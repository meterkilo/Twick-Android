package com.example.chatterinomobile.data.remote.irc

import com.example.chatterinomobile.data.model.RoomState

class RoomStateMapper {

    fun map(raw: IrcMessage): RoomState? {
        if (raw.command != "ROOMSTATE") return null
        val channelLogin = raw.channel ?: return null
        val channelId = raw.tags["room-id"] ?: channelLogin

        return RoomState(
            channelLogin = channelLogin,
            channelId = channelId,
            emoteOnly = raw.tags["emote-only"] == "1",
            followersOnlyMinutes = raw.tags["followers-only"]
                ?.toIntOrNull()
                ?.takeIf { it >= 0 },
            r9k = raw.tags["r9k"] == "1",
            slowModeSeconds = raw.tags["slow"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            subscribersOnly = raw.tags["subs-only"] == "1"
        )
    }
}
