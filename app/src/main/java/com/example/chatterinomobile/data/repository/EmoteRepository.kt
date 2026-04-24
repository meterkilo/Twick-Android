package com.example.chatterinomobile.data.repository

import com.example.chatterinomobile.data.model.Emote

interface EmoteRepository {
    suspend fun loadEmotesForChannel(channelId: String?)
    fun findEmote(name: String, channelId: String? = null): Emote?
    fun clearCache(channelId: String? = null)
}
