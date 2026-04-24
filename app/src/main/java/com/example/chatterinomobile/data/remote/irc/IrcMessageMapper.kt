package com.example.chatterinomobile.data.remote.irc

import com.example.chatterinomobile.data.model.ChatMessage
import com.example.chatterinomobile.data.model.ChatUser
import com.example.chatterinomobile.data.model.MessageFragment
import com.example.chatterinomobile.data.model.MessageType
import com.example.chatterinomobile.data.model.ReplyMetadata
import com.example.chatterinomobile.data.repository.BadgeRepository

/**
 * Turns an [IrcMessage] into a domain [ChatMessage].
 *
 * Supported commands:
 *  - **PRIVMSG** — regular user chat. Author + badges + fragments built from
 *    IRC tags. See [mapPrivmsg].
 *  - **USERNOTICE** — subs, resubs, gifts, raids, announcements. Rendered as
 *    a `MessageType.System` row; any optional user body is parsed into
 *    fragments so it renders alongside the system line. See [mapUserNotice].
 *  - **NOTICE** — server-authored informational messages ("Room is now in
 *    slow mode"). Rendered as a `MessageType.System` row. See [mapNotice].
 *
 * Non-goals (still):
 *  - Third-party emote replacement (BTTV/FFZ/7TV). That's handled by
 *    [MessageEnricher] downstream — it runs on the fragment list of the
 *    [ChatMessage] this mapper produces.
 *  - Moderation events (CLEARCHAT, CLEARMSG) — not messages; they mutate the
 *    existing log. Handled by [ModerationEventMapper].
 */
class IrcMessageMapper(
    private val badgeRepository: BadgeRepository
) {

    /**
     * Dispatches on [IrcMessage.command]. Returns null for commands this
     * mapper doesn't produce a visible message for (PING, JOIN, ROOMSTATE,
     * CLEARCHAT, CLEARMSG, ...).
     */
    fun map(raw: IrcMessage): ChatMessage? = when (raw.command) {
        "PRIVMSG" -> mapPrivmsg(raw)
        "USERNOTICE" -> mapUserNotice(raw)
        "NOTICE" -> mapNotice(raw)
        else -> null
    }

    fun mapPrivmsg(raw: IrcMessage): ChatMessage? {
        if (raw.command != "PRIVMSG") return null
        val channelLogin = raw.channel ?: return null
        val body = raw.trailing ?: return null

        // ---- Author ------------------------------------------------------
        val userId = raw.tags["user-id"] ?: return null
        val login = raw.nick ?: raw.tags["login"] ?: return null
        val displayName = raw.tags["display-name"]?.takeUnless { it.isBlank() } ?: login
        val color = raw.tags["color"]?.takeUnless { it.isBlank() }
        val author = ChatUser(
            id = userId,
            login = login,
            displayName = displayName,
            color = color,
            paint = null // enriched by a higher-level decorator that knows about PaintRepository
        )

        // ---- Badges ------------------------------------------------------
        val channelId = raw.tags["room-id"]
        val badges = parseBadgeTag(raw.tags["badges"])
            .mapNotNull { (setId, version) ->
                badgeRepository.findTwitchBadge(setId, version, channelId)
            } + badgeRepository.findThirdPartyBadges(userId)

        // ---- Detect ACTION (/me) + reply metadata ------------------------
        val reply = raw.tags["reply-parent-msg-id"]?.takeIf { it.isNotBlank() }?.let {
            ReplyMetadata(
                parentMessageId = it,
                parentUserId = raw.tags["reply-parent-user-id"],
                parentUserLogin = raw.tags["reply-parent-user-login"],
                parentDisplayName = raw.tags["reply-parent-display-name"],
                parentBody = raw.tags["reply-parent-msg-body"]
            )
        }

        val cleanedBody = stripReplyPrefix(body, reply)
        val isAction = cleanedBody.startsWith(ACTION_PREFIX) && cleanedBody.endsWith(ACTION_SUFFIX)
        val displayBody = if (isAction) {
            cleanedBody.substring(ACTION_PREFIX.length, cleanedBody.length - ACTION_SUFFIX.length)
        } else {
            cleanedBody
        }

        val messageType: MessageType = when {
            raw.tags["msg-id"] == "highlighted-message" -> MessageType.Highlighted
            isAction -> MessageType.Action
            else -> MessageType.Regular
        }

        // ---- Fragments ---------------------------------------------------
        val fragments = buildFragments(displayBody, raw.tags["emotes"])

        val tmiTs = raw.tags["tmi-sent-ts"]?.toLongOrNull() ?: System.currentTimeMillis()
        val id = raw.tags["id"] ?: "${channelLogin}-${tmiTs}-${System.nanoTime()}"

        return ChatMessage(
            id = id,
            channelId = channelId ?: channelLogin,
            author = author,
            reply = reply,
            fragment = fragments,
            badges = badges,
            timestamp = tmiTs,
            Type = messageType
        )
    }

    /** Parses `badges=broadcaster/1,subscriber/12` → list of (setId, version). */
    private fun parseBadgeTag(tag: String?): List<Pair<String, String>> {
        if (tag.isNullOrBlank()) return emptyList()
        return tag.split(',').mapNotNull { part ->
            val slash = part.indexOf('/')
            if (slash <= 0 || slash == part.length - 1) null
            else part.substring(0, slash) to part.substring(slash + 1)
        }
    }

    /**
     * Splits message text into fragments using the Twitch `emotes` tag:
     * `emotes=25:0-4,6-10/1902:12-16`
     *  → emote id 25 at indices [0..4], [6..10]
     *  → emote id 1902 at indices [12..16]
     *
     * Indices are inclusive and count code points — for ASCII chat (the common
     * case) this matches String indices. For messages containing astral plane
     * characters we'd need a code-point aware slicer; deferred until we see
     * a real-world breakage.
     *
     * Non-emote text is further scanned for `@mention` tokens.
     */
    private fun buildFragments(body: String, emotesTag: String?): List<MessageFragment> {
        val emoteRanges = parseEmoteRanges(emotesTag)
        if (emoteRanges.isEmpty()) {
            return splitMentions(body)
        }
        val sorted = emoteRanges.sortedBy { it.start }
        val out = mutableListOf<MessageFragment>()
        var cursor = 0
        for (range in sorted) {
            if (range.start > cursor) {
                val leading = body.substring(cursor, range.start.coerceAtMost(body.length))
                out.addAll(splitMentions(leading))
            }
            val endExclusive = (range.endInclusive + 1).coerceAtMost(body.length)
            if (range.start < body.length) {
                val name = body.substring(range.start, endExclusive)
                out.add(
                    MessageFragment.Emote(
                        id = range.emoteId,
                        name = name,
                        url = twitchEmoteUrl(range.emoteId)
                    )
                )
            }
            cursor = endExclusive
        }
        if (cursor < body.length) {
            out.addAll(splitMentions(body.substring(cursor)))
        }
        return out
    }

    private data class EmoteRange(
        val emoteId: String,
        val start: Int,
        val endInclusive: Int
    )

    private fun parseEmoteRanges(tag: String?): List<EmoteRange> {
        if (tag.isNullOrBlank()) return emptyList()
        val out = mutableListOf<EmoteRange>()
        for (entry in tag.split('/')) {
            val colon = entry.indexOf(':')
            if (colon <= 0) continue
            val emoteId = entry.substring(0, colon)
            val rangesPart = entry.substring(colon + 1)
            for (range in rangesPart.split(',')) {
                val dash = range.indexOf('-')
                if (dash <= 0) continue
                val start = range.substring(0, dash).toIntOrNull() ?: continue
                val end = range.substring(dash + 1).toIntOrNull() ?: continue
                out.add(EmoteRange(emoteId, start, end))
            }
        }
        return out
    }

    private fun twitchEmoteUrl(id: String): String =
        "https://static-cdn.jtvnw.net/emoticons/v2/$id/default/dark/3.0"

    /** Cheap mention splitter: tokens starting with `@`, up to the next non-name char. */
    private fun splitMentions(text: String): List<MessageFragment> {
        if (text.isEmpty()) return emptyList()
        if (!text.contains('@')) return listOf(MessageFragment.Text(text))

        val out = mutableListOf<MessageFragment>()
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '@') {
                // Flush any accumulated text.
                if (sb.isNotEmpty()) {
                    out.add(MessageFragment.Text(sb.toString()))
                    sb.clear()
                }
                // Read the mention token.
                var j = i + 1
                while (j < text.length && text[j].isUsernameChar()) j++
                val name = text.substring(i + 1, j)
                if (name.isEmpty()) {
                    sb.append(c)
                    i++
                } else {
                    out.add(MessageFragment.Mention(name))
                    i = j
                }
            } else {
                sb.append(c)
                i++
            }
        }
        if (sb.isNotEmpty()) out.add(MessageFragment.Text(sb.toString()))
        return out
    }

    private fun Char.isUsernameChar(): Boolean =
        this.isLetterOrDigit() || this == '_'

    /**
     * Twitch prefixes reply bodies with `@parentAuthor ` in the visible text.
     * The parent metadata is already present in tags, so the renderer should
     * not have to show or parse that synthetic prefix again.
     */
    private fun stripReplyPrefix(body: String, reply: ReplyMetadata?): String {
        if (reply == null) return body

        val candidates = buildList {
            reply.parentDisplayName?.takeIf { it.isNotBlank() }?.let { add("@$it ") }
            reply.parentUserLogin?.takeIf { it.isNotBlank() }?.let { add("@$it ") }
        }

        for (candidate in candidates.distinct()) {
            if (body.startsWith(candidate)) {
                return body.removePrefix(candidate)
            }
        }
        return body
    }

    // ---------------------------------------------------------------------
    // USERNOTICE (subs, resubs, gifts, raids, announcements, rituals, …)
    // ---------------------------------------------------------------------

    /**
     * Builds a [MessageType.System] [ChatMessage] from a USERNOTICE frame.
     *
     * The human-readable event description lives in the `system-msg` tag
     * (e.g. "Ronni has subscribed for 6 months!"). A USERNOTICE may *also*
     * carry a user-authored body in the trailing param (e.g. the resub
     * message text); when present we parse it with the normal emote-tag
     * pipeline so third-party emote enrichment and mention detection still
     * work on it downstream.
     *
     * If the frame has neither a system-msg nor a user body, we drop it —
     * there's nothing to render.
     */
    private fun mapUserNotice(raw: IrcMessage): ChatMessage? {
        if (raw.command != "USERNOTICE") return null
        val channelLogin = raw.channel ?: return null

        val systemText = raw.tags["system-msg"]?.takeIf { it.isNotBlank() }
            ?: raw.tags["msg-id"]?.takeIf { it.isNotBlank() }
            ?: return null

        // `trailing` on IrcMessage returns the last param unconditionally, so
        // on a USERNOTICE with no body (params == ["#channel"]) it would hand
        // us the channel back. Guard by param count so we never treat that
        // as a message body.
        val body = if (raw.params.size > 1) raw.trailing else null
        val fragments: List<MessageFragment> = if (!body.isNullOrEmpty()) {
            buildFragments(body, raw.tags["emotes"])
        } else {
            emptyList()
        }

        val tmiTs = raw.tags["tmi-sent-ts"]?.toLongOrNull() ?: System.currentTimeMillis()
        val id = raw.tags["id"] ?: "${channelLogin}-usernotice-${tmiTs}-${System.nanoTime()}"
        val channelId = raw.tags["room-id"] ?: channelLogin

        // USERNOTICE has its own author in tags (the sub-sender / raider).
        // We still surface them so UIs that want to render "ronni subscribed"
        // with a clickable name have the data — but the default renderer
        // should key off MessageType.System for the visual treatment.
        val userId = raw.tags["user-id"] ?: SYSTEM_USER_ID
        val login = raw.tags["login"] ?: raw.nick ?: SYSTEM_USER_LOGIN
        val displayName = raw.tags["display-name"]?.takeUnless { it.isBlank() } ?: login
        val author = ChatUser(
            id = userId,
            login = login,
            displayName = displayName,
            color = raw.tags["color"]?.takeUnless { it.isBlank() },
            paint = null
        )

        return ChatMessage(
            id = id,
            channelId = channelId,
            author = author,
            reply = null,
            fragment = fragments,
            badges = emptyList(),
            timestamp = tmiTs,
            Type = MessageType.System(systemText)
        )
    }

    // ---------------------------------------------------------------------
    // NOTICE (server-authored informational messages)
    // ---------------------------------------------------------------------

    /**
     * Server notices arrive with no author tags and the text in the trailing
     * param, e.g. `@msg-id=slow_on :tmi.twitch.tv NOTICE #x :This room is in
     * slow mode.` We flatten them to a System message attributed to a
     * synthetic system author so the rest of the pipeline doesn't need to
     * branch on nullable authors.
     */
    private fun mapNotice(raw: IrcMessage): ChatMessage? {
        if (raw.command != "NOTICE") return null
        val channelLogin = raw.channel ?: return null
        // Guard against "NOTICE #channel" with no trailing — IrcMessage.trailing
        // would otherwise hand back the channel name.
        val text = (if (raw.params.size > 1) raw.trailing else null)
            ?.takeIf { it.isNotBlank() } ?: return null

        val tmiTs = System.currentTimeMillis() // NOTICE has no tmi-sent-ts
        val id = "${channelLogin}-notice-${tmiTs}-${System.nanoTime()}"

        return ChatMessage(
            id = id,
            channelId = channelLogin,
            author = SYSTEM_AUTHOR,
            reply = null,
            fragment = emptyList(),
            badges = emptyList(),
            timestamp = tmiTs,
            Type = MessageType.System(text)
        )
    }

    companion object {
        /** Twitch wraps /me actions in `\u0001ACTION …\u0001` on the wire. */
        private const val ACTION_PREFIX = "\u0001ACTION "
        private const val ACTION_SUFFIX = "\u0001"

        /** Sentinel user attached to [MessageType.System] rows that have no real author. */
        private const val SYSTEM_USER_ID = "system"
        private const val SYSTEM_USER_LOGIN = "tmi.twitch.tv"
        private val SYSTEM_AUTHOR = ChatUser(
            id = SYSTEM_USER_ID,
            login = SYSTEM_USER_LOGIN,
            displayName = SYSTEM_USER_LOGIN,
            color = null,
            paint = null
        )
    }
}
