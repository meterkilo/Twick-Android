package com.example.chatterinomobile.data.repository

import com.example.chatterinomobile.data.model.Badge

/**
 * Caches Twitch + 7TV badges and exposes fast synchronous lookups for the
 * message renderer.
 *
 * Twitch badges are looked up by `(set_id, version)` which is what arrives in
 * IRC tags. Third-party badges are keyed by the user's Twitch ID because
 * they're assigned per-user, not per-message.
 */
interface BadgeRepository {

    /** Fetch global Twitch badges + 7TV cosmetics badges. Safe to call repeatedly. */
    suspend fun loadGlobalBadges()

    /** Fetch this channel's subscriber / bits / moderator-specific badge variants. */
    suspend fun loadChannelBadges(channelId: String)

    /**
     * Look up a Twitch badge by IRC tag values. Checks channel-scoped badges
     * first (they override globals), then global badges. Returns null if we've
     * never heard of that set.
     */
    fun findTwitchBadge(setId: String, version: String, channelId: String? = null): Badge?

    /** Third-party badges (currently 7TV only) for the given Twitch user ID. */
    fun findThirdPartyBadges(twitchUserId: String): List<Badge>

    /** Clears global + channel badge metadata, or only a single channel when provided. */
    fun clearCache(channelId: String? = null)
}
