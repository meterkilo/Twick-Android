package com.example.chatterinomobile.data.remote.api

import com.example.chatterinomobile.data.remote.dto.SevenTvCosmeticsResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/**
 * Fetches the full 7TV paints + badges table along with the user IDs entitled
 * to each one. One call is enough to populate both the paint and badge
 * caches for every chatter we'll ever see.
 *
 * Uses the v2 endpoint because it returns the user→cosmetic mapping in the
 * same payload; v3 forces a per-user round-trip we don't want on every message.
 */
class SevenTvCosmeticsApi(private val httpClient: HttpClient) {

    /**
     * @param userIdentifier Which ID format the `users` arrays should contain.
     *   "twitch_id" is what we key off of throughout the app.
     */
    suspend fun getCosmetics(userIdentifier: String = "twitch_id"): SevenTvCosmeticsResponseDto {
        return httpClient.get("$BASE_URL/cosmetics") {
            parameter("user_identifier", userIdentifier)
        }.body()
    }

    companion object {
        private const val BASE_URL = "https://api.7tv.app/v2"
    }
}
