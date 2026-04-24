package com.example.chatterinomobile.data.remote.irc

import com.example.chatterinomobile.data.model.ModerationEvent

/**
 * Parses the subset of Twitch IRC frames that *retroactively* change the
 * chat log rather than appending to it.
 *
 * Commands handled:
 *  - **CLEARCHAT** with no user param → [ModerationEvent.ChatCleared]
 *  - **CLEARCHAT** with a user param and no `ban-duration` → [ModerationEvent.UserBanned]
 *  - **CLEARCHAT** with a user param and `ban-duration` → [ModerationEvent.UserTimedOut]
 *  - **CLEARMSG** → [ModerationEvent.MessageDeleted]
 *
 * Returns null for anything else so the caller can filterNotNull the flow.
 */
class ModerationEventMapper {

    fun map(raw: IrcMessage): ModerationEvent? = when (raw.command) {
        "CLEARCHAT" -> mapClearChat(raw)
        "CLEARMSG" -> mapClearMsg(raw)
        else -> null
    }

    /**
     * CLEARCHAT shapes we see from Twitch:
     *   `:tmi.twitch.tv CLEARCHAT #dallas`                  → whole chat wiped
     *   `@ban-duration=30 ... CLEARCHAT #dallas :ronni`     → 30s timeout
     *   `... CLEARCHAT #dallas :ronni`                      → permanent ban
     *
     * The target user's login is the trailing param; their Twitch user ID
     * lives in the `target-user-id` tag.
     */
    private fun mapClearChat(raw: IrcMessage): ModerationEvent? {
        val channelLogin = raw.channel ?: return null
        // IrcMessage.trailing = params.lastOrNull(), which is the channel
        // itself when there's no target — so we can't just null-check trailing.
        // A real CLEARCHAT with a target always has params.size >= 2.
        val targetLogin = if (raw.params.size > 1) raw.trailing else null

        if (targetLogin.isNullOrBlank()) {
            return ModerationEvent.ChatCleared(channelLogin)
        }

        // target-user-id isn't always present on very old ban events, but the
        // modern Twitch IRC server does send it. If we can't key off a stable
        // ID we can't reliably strike the user's prior messages — skip.
        val targetUserId = raw.tags["target-user-id"] ?: return null
        val duration = raw.tags["ban-duration"]?.toIntOrNull()

        return if (duration != null) {
            ModerationEvent.UserTimedOut(
                channelLogin = channelLogin,
                targetUserId = targetUserId,
                targetLogin = targetLogin,
                durationSeconds = duration
            )
        } else {
            ModerationEvent.UserBanned(
                channelLogin = channelLogin,
                targetUserId = targetUserId,
                targetLogin = targetLogin
            )
        }
    }

    /**
     * CLEARMSG example:
     *   `@login=ronni;target-msg-id=abc-123 :tmi.twitch.tv CLEARMSG #dallas :HeyGuys`
     *
     * We only need `target-msg-id` + `login` — the trailing param is the
     * original message text, which we already have buffered in the UI.
     */
    private fun mapClearMsg(raw: IrcMessage): ModerationEvent? {
        val channelLogin = raw.channel ?: return null
        val targetMessageId = raw.tags["target-msg-id"]?.takeIf { it.isNotBlank() } ?: return null
        val targetLogin = raw.tags["login"]?.takeIf { it.isNotBlank() } ?: return null
        return ModerationEvent.MessageDeleted(
            channelLogin = channelLogin,
            targetMessageId = targetMessageId,
            targetLogin = targetLogin
        )
    }
}
