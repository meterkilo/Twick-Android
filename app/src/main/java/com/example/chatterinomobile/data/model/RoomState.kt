package com.example.chatterinomobile.data.model

data class RoomState(
    val channelLogin: String,
    val channelId: String,
    val emoteOnly: Boolean = false,
    val followersOnlyMinutes: Int? = null,
    val r9k: Boolean = false,
    val slowModeSeconds: Int = 0,
    val subscribersOnly: Boolean = false
)
