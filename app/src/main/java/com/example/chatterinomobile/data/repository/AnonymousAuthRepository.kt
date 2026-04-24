package com.example.chatterinomobile.data.repository

import com.example.chatterinomobile.data.model.TwitchDeviceAuthorization
import com.example.chatterinomobile.data.model.TwitchDeviceFlowState

/**
 * Stub [AuthRepository] that always returns "no user logged in".
 *
 * All downstream callers must tolerate this (the IRC client falls back to a
 * `justinfan*` anonymous login, Helix endpoints that require auth return empty).
 *
 * This still has one real job after OAuth lands: it remains the fallback when
 * no Twitch client ID is configured locally, which keeps CI and fresh clones
 * buildable without private setup.
 */
class AnonymousAuthRepository(
    private val clientId: String = ""
) : AuthRepository {

    override suspend fun getAccessToken(): String? = null

    override suspend fun getUserId(): String? = null

    override suspend fun getLogin(): String? = null

    override fun getClientId(): String = clientId

    override suspend fun startDeviceFlow(scopes: List<String>): TwitchDeviceFlowState? = null

    override suspend fun awaitDeviceAuthorization(
        state: TwitchDeviceFlowState
    ): TwitchDeviceAuthorization =
        TwitchDeviceAuthorization.Failed("Twitch OAuth is not configured")

    override suspend fun clearSession() = Unit
}
