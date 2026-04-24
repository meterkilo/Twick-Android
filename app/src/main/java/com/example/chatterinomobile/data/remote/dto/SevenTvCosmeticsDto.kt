package com.example.chatterinomobile.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response shape for 7TV's v2 cosmetics endpoint:
 * `https://api.7tv.app/v2/cosmetics?user_identifier=twitch_id`
 *
 * The endpoint returns *all* paints and badges along with the Twitch IDs of
 * the users who own each one. We prefer this shape over 7TV v3's per-user
 * lookup because it lets us cache the full cosmetics table client-side with
 * a single network round-trip.
 */
@Serializable
data class SevenTvCosmeticsResponseDto(
    val paints: List<SevenTvPaintDto> = emptyList(),
    val badges: List<SevenTvBadgeDto> = emptyList()
)

@Serializable
data class SevenTvPaintDto(
    val id: String,
    val name: String,
    /** Twitch IDs of users entitled to this paint. */
    val users: List<String> = emptyList(),
    /** One of "LINEAR_GRADIENT", "RADIAL_GRADIENT", "URL". */
    val function: String,
    /** Solid fallback color (ARGB int); 0 for gradients/images. */
    val color: Long? = null,
    val stops: List<SevenTvColorStopDto> = emptyList(),
    val repeat: Boolean = false,
    val angle: Int = 0,
    @SerialName("image_url") val imageUrl: String? = null,
    val shadows: List<SevenTvShadowDto> = emptyList()
)

@Serializable
data class SevenTvColorStopDto(
    val at: Float,
    val color: Long
)

@Serializable
data class SevenTvShadowDto(
    @SerialName("x_offset") val xOffset: Float,
    @SerialName("y_offset") val yOffset: Float,
    val radius: Float,
    val color: Long
)

@Serializable
data class SevenTvBadgeDto(
    val id: String,
    val name: String,
    val tooltip: String,
    val tag: String = "",
    /** Twitch IDs of users entitled to this badge. */
    val users: List<String> = emptyList(),
    /** List of [sizeTag, url] pairs: [["1", "..."], ["2", "..."], ["3", "..."]]. */
    val urls: List<List<String>> = emptyList()
)
