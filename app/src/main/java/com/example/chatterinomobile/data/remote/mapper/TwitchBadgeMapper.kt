package com.example.chatterinomobile.data.remote.mapper

import com.example.chatterinomobile.data.model.Badge
import com.example.chatterinomobile.data.model.BadgeProvider
import com.example.chatterinomobile.data.remote.dto.HelixBadgeVersionDto

/**
 * Maps a Helix badge version to our domain [Badge].
 *
 * Twitch identifies a badge by (set_id, version); we flatten that into a
 * single slash-joined string for [Badge.id] so the repository can key a
 * `Map<String, Badge>` off of it directly.
 *
 * We prefer [imageUrl4x] for crispness — the UI will downscale as needed.
 */
fun HelixBadgeVersionDto.toDomain(setId: String): Badge = Badge(
    id = "$setId/$id",
    imageURL = imageUrl4x,
    description = description.ifBlank { title },
    provider = BadgeProvider.TWITCH
)

/** Canonical lookup key used by [com.example.chatterinomobile.data.repository.BadgeRepository]. */
fun twitchBadgeKey(setId: String, version: String): String = "$setId/$version"
