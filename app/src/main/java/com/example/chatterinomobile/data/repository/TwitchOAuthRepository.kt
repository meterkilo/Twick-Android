package com.example.chatterinomobile.data.repository

import com.example.chatterinomobile.BuildConfig
import com.example.chatterinomobile.data.local.TokenStore
import com.example.chatterinomobile.data.model.TwitchDeviceAuthorization
import com.example.chatterinomobile.data.model.TwitchDeviceFlowState
import com.example.chatterinomobile.data.remote.api.TwitchOAuthApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Real Twitch auth implementation for a public mobile client.
 *
 * Responsibilities:
 * - start + poll the device-code flow
 * - persist access/refresh tokens securely
 * - refresh access tokens when they expire
 * - validate tokens on startup and roughly hourly thereafter, per Twitch's rules
 *
 * A single [sessionMutex] serializes refresh / validate / login completion so
 * multiple callers don't burn one-time-use refresh tokens concurrently.
 */
class TwitchOAuthRepository(
    private val oauthApi: TwitchOAuthApi,
    private val tokenStore: TokenStore
) : AuthRepository {

    private val sessionMutex = Mutex()

    override suspend fun getAccessToken(): String? =
        sessionMutex.withLock {
            val current = tokenStore.read() ?: return null
            val now = System.currentTimeMillis()

            val refreshed = if (current.expiresAtEpochMillis <= now + EXPIRY_SKEW_MILLIS) {
                refreshLocked(current) ?: return null
            } else {
                current
            }

            val maybeValidated =
                if (refreshed.lastValidatedAtEpochMillis <= now - VALIDATE_INTERVAL_MILLIS) {
                    validateLocked(refreshed) ?: return null
                } else {
                    refreshed
                }

            maybeValidated.accessToken
        }

    override suspend fun getUserId(): String? =
        sessionMutex.withLock { tokenStore.read()?.userId }

    override suspend fun getLogin(): String? =
        sessionMutex.withLock { tokenStore.read()?.login }

    override fun getClientId(): String = BuildConfig.TWITCH_CLIENT_ID

    override suspend fun startDeviceFlow(scopes: List<String>): TwitchDeviceFlowState? {
        val clientId = getClientId().trim()
        if (clientId.isBlank()) return null

        val response = oauthApi.startDeviceFlow(clientId, scopes)
        val now = System.currentTimeMillis()
        return TwitchDeviceFlowState(
            deviceCode = response.deviceCode,
            userCode = response.userCode,
            verificationUri = response.verificationUri,
            scopes = scopes,
            expiresAtEpochMillis = now + (response.expiresIn * 1000L),
            pollIntervalSeconds = response.interval
        )
    }

    override suspend fun awaitDeviceAuthorization(
        state: TwitchDeviceFlowState
    ): TwitchDeviceAuthorization {
        val clientId = getClientId().trim()
        if (clientId.isBlank()) {
            return TwitchDeviceAuthorization.Failed("Twitch client ID is not configured")
        }

        var intervalSeconds = state.pollIntervalSeconds.coerceAtLeast(1)
        while (true) {
            val now = System.currentTimeMillis()
            if (state.isExpired(now)) return TwitchDeviceAuthorization.Expired

            when (
                val result = oauthApi.exchangeDeviceCode(
                    clientId = clientId,
                    scopes = state.scopes,
                    deviceCode = state.deviceCode
                )
            ) {
                is TwitchOAuthApi.DeviceCodeExchangeResult.Authorized -> {
                    val validated = when (
                        val validation = oauthApi.validateAccessToken(result.token.accessToken)
                    ) {
                        is TwitchOAuthApi.ValidateTokenResult.Valid -> validation.token
                        is TwitchOAuthApi.ValidateTokenResult.Invalid -> {
                            return TwitchDeviceAuthorization.Failed(
                                "Twitch returned an invalid access token"
                            )
                        }
                        is TwitchOAuthApi.ValidateTokenResult.Failed -> {
                            return TwitchDeviceAuthorization.Failed(validation.message)
                        }
                    }

                    val stored = TokenStore.StoredTwitchSession(
                        accessToken = result.token.accessToken,
                        refreshToken = result.token.refreshToken,
                        userId = validated.userId,
                        login = validated.login,
                        expiresAtEpochMillis = now + (result.token.expiresIn * 1000L),
                        scopes = if (validated.scopes.isNotEmpty()) {
                            validated.scopes
                        } else {
                            result.token.scope
                        },
                        lastValidatedAtEpochMillis = now
                    )

                    sessionMutex.withLock {
                        tokenStore.write(stored)
                    }

                    return TwitchDeviceAuthorization.Authorized(
                        userId = stored.userId,
                        login = stored.login,
                        accessToken = stored.accessToken,
                        scopes = stored.scopes,
                        expiresAtEpochMillis = stored.expiresAtEpochMillis
                    )
                }
                TwitchOAuthApi.DeviceCodeExchangeResult.AuthorizationPending -> {
                    delay(intervalSeconds * 1000L)
                }
                TwitchOAuthApi.DeviceCodeExchangeResult.SlowDown -> {
                    intervalSeconds += 5
                    delay(intervalSeconds * 1000L)
                }
                TwitchOAuthApi.DeviceCodeExchangeResult.ExpiredToken -> {
                    return TwitchDeviceAuthorization.Expired
                }
                TwitchOAuthApi.DeviceCodeExchangeResult.AccessDenied -> {
                    return TwitchDeviceAuthorization.Denied
                }
                is TwitchOAuthApi.DeviceCodeExchangeResult.Failed -> {
                    return TwitchDeviceAuthorization.Failed(result.message)
                }
            }
        }
    }

    override suspend fun clearSession() {
        sessionMutex.withLock {
            tokenStore.clear()
        }
    }

    private suspend fun refreshLocked(
        current: TokenStore.StoredTwitchSession
    ): TokenStore.StoredTwitchSession? {
        val refreshToken = current.refreshToken ?: run {
            tokenStore.clear()
            return null
        }

        return when (val result = oauthApi.refreshAccessToken(getClientId(), refreshToken)) {
            is TwitchOAuthApi.RefreshTokenResult.Success -> {
                val now = System.currentTimeMillis()
                val updated = current.copy(
                    accessToken = result.token.accessToken,
                    refreshToken = result.token.refreshToken ?: refreshToken,
                    expiresAtEpochMillis = now + (result.token.expiresIn * 1000L),
                    scopes = if (result.token.scope.isNotEmpty()) {
                        result.token.scope
                    } else {
                        current.scopes
                    },
                    lastValidatedAtEpochMillis = 0L
                )
                tokenStore.write(updated)
                updated
            }
            TwitchOAuthApi.RefreshTokenResult.InvalidRefreshToken -> {
                tokenStore.clear()
                null
            }
            is TwitchOAuthApi.RefreshTokenResult.Failed -> null
        }
    }

    private suspend fun validateLocked(
        current: TokenStore.StoredTwitchSession
    ): TokenStore.StoredTwitchSession? =
        when (val result = oauthApi.validateAccessToken(current.accessToken)) {
            is TwitchOAuthApi.ValidateTokenResult.Valid -> {
                val now = System.currentTimeMillis()
                val updated = current.copy(
                    userId = result.token.userId ?: current.userId,
                    login = result.token.login ?: current.login,
                    expiresAtEpochMillis = now + (result.token.expiresIn * 1000L),
                    scopes = if (result.token.scopes.isNotEmpty()) {
                        result.token.scopes
                    } else {
                        current.scopes
                    },
                    lastValidatedAtEpochMillis = now
                )
                tokenStore.write(updated)
                updated
            }
            TwitchOAuthApi.ValidateTokenResult.Invalid -> {
                tokenStore.clear()
                null
            }
            is TwitchOAuthApi.ValidateTokenResult.Failed -> current
        }

    companion object {
        private const val EXPIRY_SKEW_MILLIS = 60_000L
        private const val VALIDATE_INTERVAL_MILLIS = 60L * 60L * 1000L
    }
}
