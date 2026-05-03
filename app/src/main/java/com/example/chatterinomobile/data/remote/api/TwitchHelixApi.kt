package com.example.chatterinomobile.data.remote.api

import com.example.chatterinomobile.data.remote.dto.HelixBadgeSetDto
import com.example.chatterinomobile.data.remote.dto.HelixFollowedChannelDto
import com.example.chatterinomobile.data.remote.dto.HelixListResponse
import com.example.chatterinomobile.data.remote.dto.HelixListResponseWithPagination
import com.example.chatterinomobile.data.remote.dto.HelixStreamDto
import com.example.chatterinomobile.data.remote.dto.HelixUserDto
import com.example.chatterinomobile.data.repository.AuthRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter

class TwitchHelixApi(
    private val httpClient: HttpClient,
    private val authRepository: AuthRepository
) {

    suspend fun getUsersByLogin(logins: List<String>): List<HelixUserDto> {
        if (logins.isEmpty()) return emptyList()
        val token = authRepository.getAccessToken()
        val clientId = authRepository.getClientId()
        val response: HelixListResponse<HelixUserDto> = httpClient.get("$BASE_URL/users") {
            applyAuth(clientId, token)
            logins.forEach { parameter("login", it) }
        }.body()
        return response.data
    }

    suspend fun getStreamsByLogin(logins: List<String>): List<HelixStreamDto> {
        if (logins.isEmpty()) return emptyList()
        val token = authRepository.getAccessToken()
        val clientId = authRepository.getClientId()
        val response: HelixListResponse<HelixStreamDto> = httpClient.get("$BASE_URL/streams") {
            applyAuth(clientId, token)
            logins.forEach { parameter("user_login", it) }
        }.body()
        return response.data
    }

    suspend fun getGlobalBadges(): List<HelixBadgeSetDto> {
        val token = authRepository.getAccessToken()
        val clientId = authRepository.getClientId()
        val response: HelixListResponse<HelixBadgeSetDto> =
            httpClient.get("$BASE_URL/chat/badges/global") {
                applyAuth(clientId, token)
            }.body()
        return response.data
    }


    suspend fun getFollowedChannels(userId: String, limit: Int = 100): List<HelixFollowedChannelDto> {
        val token = authRepository.getAccessToken()
        val clientId = authRepository.getClientId()
        val response: HelixListResponseWithPagination<HelixFollowedChannelDto> =
            httpClient.get("$BASE_URL/channels/followed") {
                applyAuth(clientId, token)
                parameter("user_id", userId)
                parameter("first", limit.coerceIn(1, 100))
            }.body()
        return response.data
    }


    suspend fun getTopStreams(limit: Int = 20): List<HelixStreamDto> {
        val token = authRepository.getAccessToken()
        val clientId = authRepository.getClientId()
        val response: HelixListResponseWithPagination<HelixStreamDto> =
            httpClient.get("$BASE_URL/streams") {
                applyAuth(clientId, token)
                parameter("first", limit.coerceIn(1, 100))
            }.body()
        return response.data
    }

    suspend fun getChannelBadges(broadcasterId: String): List<HelixBadgeSetDto> {
        val token = authRepository.getAccessToken()
        val clientId = authRepository.getClientId()
        val response: HelixListResponse<HelixBadgeSetDto> =
            httpClient.get("$BASE_URL/chat/badges") {
                applyAuth(clientId, token)
                parameter("broadcaster_id", broadcasterId)
            }.body()
        return response.data
    }

    private fun HttpRequestBuilder.applyAuth(clientId: String, token: String?) {
        header("Client-Id", clientId)
        if (token != null) header("Authorization", "Bearer $token")
    }

    companion object {
        private const val BASE_URL = "https://api.twitch.tv/helix"
    }
}
