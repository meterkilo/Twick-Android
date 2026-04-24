package com.example.chatterinomobile.data.model

/**
 * Events that mutate the chat log *after the fact* — deletions and retroactive
 * strikethroughs that can't be represented as a new [ChatMessage] appended to
 * the list.
 *
 * These are emitted on a separate flow from [ChatMessage] because the UI has
 * to reach back into already-rendered messages and strike or remove them.
 * Twitch sends them on the same IRC connection (CLEARCHAT / CLEARMSG), so the
 * chat layer demultiplexes into two streams.
 */
sealed class ModerationEvent {

    /** Login of the channel (no `#`) the event applies to. */
    abstract val channelLogin: String

    /**
     * `CLEARCHAT` with no target user — the entire chat was wiped by a mod
     * (`/clear`). UI should clear all messages for [channelLogin].
     */
    data class ChatCleared(
        override val channelLogin: String
    ) : ModerationEvent()

    /**
     * `CLEARCHAT` with a target user and no `ban-duration` tag → permanent ban.
     * Every prior message by [targetUserId] in the channel should be struck.
     */
    data class UserBanned(
        override val channelLogin: String,
        val targetUserId: String,
        val targetLogin: String
    ) : ModerationEvent()

    /**
     * `CLEARCHAT` with a target user and a `ban-duration` tag. Same visual
     * treatment as [UserBanned] for now — the duration is carried for UIs
     * that want to show "timed out for Ns".
     */
    data class UserTimedOut(
        override val channelLogin: String,
        val targetUserId: String,
        val targetLogin: String,
        val durationSeconds: Int
    ) : ModerationEvent()

    /**
     * `CLEARMSG` — a single message was deleted. The UI looks up
     * [targetMessageId] in its message list and strikes only that one.
     *
     * The target login is included because Twitch provides it in the tag
     * and it's useful for mod-log style UIs; the message itself may already
     * have scrolled off-screen.
     */
    data class MessageDeleted(
        override val channelLogin: String,
        val targetMessageId: String,
        val targetLogin: String
    ) : ModerationEvent()
}
