package com.example.chatterinomobile.data.remote.irc

/**
 * A single parsed IRC frame (IRCv3-with-tags, as spoken by Twitch).
 *
 * Example raw line:
 * ```
 * @badges=broadcaster/1;color=#FF69B4;display-name=xQc;id=abc;user-id=71092938;tmi-sent-ts=1611000000000 :xqc!xqc@xqc.tmi.twitch.tv PRIVMSG #xqc :Hello KEKW
 * ```
 *
 * [tags]     → parsed IRCv3 `@key=val;key2=val2` preamble
 * [prefix]   → raw `:nick!user@host` (whole string, no further splitting here)
 * [command]  → e.g. "PRIVMSG", "JOIN", "PING", "001"
 * [params]   → command parameters, with the trailing `:param` already joined
 *              back into a single string (common case: last param is the
 *              message body).
 */
data class IrcMessage(
    val tags: Map<String, String>,
    val prefix: String?,
    val command: String,
    val params: List<String>
) {
    /** Convenience: the nickname portion of [prefix] if present. */
    val nick: String?
        get() {
            val p = prefix ?: return null
            val bang = p.indexOf('!')
            return if (bang > 0) p.substring(0, bang) else null
        }

    /** First param (usually the channel for PRIVMSG/JOIN/PART). Null if none. */
    val channel: String?
        get() = params.firstOrNull()?.takeIf { it.startsWith("#") }?.removePrefix("#")

    /** Last param, which is conventionally the "message body" for PRIVMSG/NOTICE. */
    val trailing: String?
        get() = params.lastOrNull()
}
