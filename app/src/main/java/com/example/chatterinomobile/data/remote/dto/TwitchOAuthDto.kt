package com.example.chatterinomobile.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TwitchDeviceCodeDto(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("expires_in") val expiresIn: Int,
    val interval: Int,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String
)

@Serializable
data class TwitchTokenDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Int,
    val scope: List<String> = emptyList(),
    @SerialName("token_type") val tokenType: String
)

@Serializable
data class TwitchValidateTokenDto(
    @SerialName("client_id") val clientId: String,
    val login: String? = null,
    val scopes: List<String> = emptyList(),
    @SerialName("user_id") val userId: String? = null,
    @SerialName("expires_in") val expiresIn: Int
)

@Serializable
data class TwitchOAuthErrorDto(
    val status: Int? = null,
    val message: String? = null,
    val error: String? = null
)
