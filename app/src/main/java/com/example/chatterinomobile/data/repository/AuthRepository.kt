package com.example.chatterinomobile.data.repository

import com.example.chatterinomobile.data.model.TwitchDeviceAuthorization
import com.example.chatterinomobile.data.model.TwitchDeviceFlowState

/**
 * Supplies a Twitch OAuth access token for endpoints/WebSockets that require one.
 *
 * The key design choice here is that "logged out" is not an exceptional state.
 * The rest of the app must keep functioning in anonymous read-only mode when:
 * - the user never signed in
 * - the configured Twitch client ID is blank
 * - a stored token is no longer valid and we intentionally clear it
 *
 * OAuth-specific entry points live on this same interface because the eventual
 * UI should depend on the auth abstraction rather than a concrete repository.
 */
interface AuthRepository {

    /** Current access token, or null if the user isn't logged in. */
    suspend fun getAccessToken(): String?

    /** Twitch user ID of the logged-in user, or null if anonymous. */
    suspend fun getUserId(): String?

    /** Twitch login of the logged-in user, or null if anonymous. */
    suspend fun getLogin(): String?

    /** Twitch client ID used for Helix requests. */
    fun getClientId(): String

    /**
     * Starts Twitch's device-code flow and returns the code/URL that the UI
     * should show the user, or null when OAuth isn't configured.
     */
    suspend fun startDeviceFlow(
        scopes: List<String> = DEFAULT_TWITCH_SCOPES
    ): TwitchDeviceFlowState?

    /**
     * Polls Twitch until the current device-code flow reaches a terminal state.
     *
     * This intentionally hides `authorization_pending` / `slow_down` from the
     * UI layer. The repository owns the retry loop, backoff, token storage,
     * and post-login validation so the eventual screen can stay thin.
     */
    suspend fun awaitDeviceAuthorization(
        state: TwitchDeviceFlowState
    ): TwitchDeviceAuthorization

    /** Best-effort logout that wipes the locally stored Twitch session. */
    suspend fun clearSession()

    companion object {
        val DEFAULT_TWITCH_SCOPES = listOf(
            "chat:read",
            "chat:edit",
            "user:read:follows"
        )
    }
}
