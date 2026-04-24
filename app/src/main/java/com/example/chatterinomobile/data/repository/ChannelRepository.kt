package com.example.chatterinomobile.data.repository

import com.example.chatterinomobile.data.model.Channel

/**
 * Looks up Twitch channel metadata (display name, live status, game, viewers)
 * by login. Backed by the Helix `/users` and `/streams` endpoints.
 *
 * Implementations are expected to tolerate auth failures and return empty
 * results rather than throwing — callers should treat a missing channel as
 * "offline or not found" either way.
 */
interface ChannelRepository {

    /** Resolve a single channel by its login (lowercase username). */
    suspend fun getChannelByLogin(login: String): Channel?

    /** Resolve many channels at once. Offline / unknown logins are omitted. */
    suspend fun getChannelsByLogins(logins: List<String>): List<Channel>
}
