package com.example.chatterinomobile.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class BttvEmoteDto(
    val id: String,
    val code: String,
    val imageType: String,
    val animated: Boolean = false
)

@Serializable
data class BttvUserEmotesDto(
    val channelEmotes: List<BttvEmoteDto> = emptyList(),
    val sharedEmotes: List<BttvEmoteDto> = emptyList()
)
