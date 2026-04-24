package com.example.chatterinomobile.data.model

/**
 * Immutable snapshot of a started Twitch device-code flow.
 *
 * We persist the scopes here because Twitch expects them again when polling the
 * token endpoint for a public client. Keeping them attached to the state object
 * avoids a fragile "remember to pass the same scopes twice" contract in the UI.
 */
data class TwitchDeviceFlowState(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val scopes: List<String>,
    val expiresAtEpochMillis: Long,
    val pollIntervalSeconds: Int
) {
    fun isExpired(nowEpochMillis: Long): Boolean = nowEpochMillis >= expiresAtEpochMillis
}

/**
 * Terminal outcomes of waiting for a Twitch device-code authorization.
 *
 * The UI only needs to care whether the flow completed, expired, was denied,
 * or failed for some other reason. Intermediate retry states stay inside the
 * repository so callers don't have to re-implement Twitch's polling contract.
 */
sealed interface TwitchDeviceAuthorization {

    data class Authorized(
        val userId: String?,
        val login: String?,
        val accessToken: String,
        val scopes: List<String>,
        val expiresAtEpochMillis: Long
    ) : TwitchDeviceAuthorization

    data object Expired : TwitchDeviceAuthorization

    data object Denied : TwitchDeviceAuthorization

    data class Failed(val message: String) : TwitchDeviceAuthorization
}
