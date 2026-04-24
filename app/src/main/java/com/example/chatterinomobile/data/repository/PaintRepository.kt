package com.example.chatterinomobile.data.repository

import com.example.chatterinomobile.data.model.Paint

/**
 * Caches 7TV paints and maps Twitch user IDs to the paint they're wearing.
 *
 * Lookups are called from the render path on every message author, so they
 * need to be synchronous and allocation-free when possible.
 */
interface PaintRepository {

    /** Fetches the global 7TV paint set and user entitlements. */
    suspend fun loadPaints()

    /** The paint the given Twitch user is wearing, or null if none. */
    fun findPaintForUser(twitchUserId: String): Paint?
}
