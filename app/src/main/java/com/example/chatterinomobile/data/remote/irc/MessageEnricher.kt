package com.example.chatterinomobile.data.remote.irc

import com.example.chatterinomobile.data.model.ChatMessage
import com.example.chatterinomobile.data.model.MessageFragment
import com.example.chatterinomobile.data.repository.EmoteRepository
import com.example.chatterinomobile.data.repository.PaintRepository

/**
 * Decorator applied to each [ChatMessage] after [IrcMessageMapper] has produced
 * it from the raw IRC frame.
 *
 * Two passes:
 *  1. **Paint:** look up the author's active 7TV paint by Twitch user ID.
 *  2. **Third-party emotes:** scan every [MessageFragment.Text] for whole-word
 *     matches in [EmoteRepository]; each hit is split into surrounding Text
 *     fragments and a new [MessageFragment.Emote].
 *
 * Both repositories are lookup-only at this layer — they must already have
 * been warmed (global sets on startup, channel sets on join). Lookups are
 * read-mostly and happen on every inbound message, so they stay synchronous
 * and allocation-light: we only allocate a new fragment list when a Text
 * fragment actually contains an emote.
 *
 * Twitch first-party emotes are *not* re-processed here — [IrcMessageMapper]
 * already produced them from the `emotes` IRC tag, and they aren't Text
 * fragments by the time we see them.
 */
class MessageEnricher(
    private val emoteRepository: EmoteRepository,
    private val paintRepository: PaintRepository
) {

    fun enrich(message: ChatMessage): ChatMessage {
        val paint = paintRepository.findPaintForUser(message.author.id)
        val swapped = swapThirdPartyEmotes(
            fragments = message.fragment,
            channelId = message.channelId
        )

        // Fast path: nothing changed → avoid copy allocation.
        if (paint == null && swapped === message.fragment) return message

        val author = if (paint != null) message.author.copy(paint = paint) else message.author
        return message.copy(
            author = author,
            fragment = swapped
        )
    }

    /**
     * Walks each [MessageFragment.Text] and splits it on whitespace. Any token
     * that resolves to an [com.example.chatterinomobile.data.model.Emote] via
     * [EmoteRepository.findEmote] is emitted as a [MessageFragment.Emote];
     * runs of non-matching tokens stay as [MessageFragment.Text] with their
     * original spacing preserved.
     *
     * If no Text fragment contains a known emote, the original list is
     * returned by reference so the caller can skip allocating a new message.
     */
    private fun swapThirdPartyEmotes(
        fragments: List<MessageFragment>,
        channelId: String
    ): List<MessageFragment> {
        // Peek first — if nothing to do, don't allocate.
        if (fragments.none { it is MessageFragment.Text && it.content.containsEmoteCandidate() }) {
            return fragments
        }

        val out = ArrayList<MessageFragment>(fragments.size)
        for (fragment in fragments) {
            if (fragment !is MessageFragment.Text) {
                out.add(fragment)
                continue
            }
            expandTextFragment(fragment.content, channelId, out)
        }
        return out
    }

    /**
     * Splits [text] into words separated by runs of whitespace. Each word is
     * checked against [EmoteRepository]; matches become [MessageFragment.Emote],
     * misses are accumulated and flushed as [MessageFragment.Text] (preserving
     * the original whitespace between them).
     *
     * Rationale for the "flush buffer" approach: we want emotes to become
     * distinct fragments (the renderer draws them as images), but we don't
     * want to shred plain text into dozens of per-word Text fragments — that
     * would blow up the fragment count on long messages for no gain.
     */
    private fun expandTextFragment(
        text: String,
        channelId: String,
        out: MutableList<MessageFragment>
    ) {
        if (text.isEmpty()) return
        val buffer = StringBuilder()
        var i = 0
        val n = text.length
        while (i < n) {
            // Consume any whitespace into the buffer verbatim.
            val wsStart = i
            while (i < n && text[i].isWhitespace()) i++
            if (i > wsStart) buffer.append(text, wsStart, i)
            if (i >= n) break

            // Consume a "word" — run of non-whitespace characters.
            val wordStart = i
            while (i < n && !text[i].isWhitespace()) i++
            val word = text.substring(wordStart, i)

            val emote = emoteRepository.findEmote(word, channelId)
            if (emote != null) {
                if (buffer.isNotEmpty()) {
                    out.add(MessageFragment.Text(buffer.toString()))
                    buffer.clear()
                }
                out.add(
                    MessageFragment.Emote(
                        id = emote.id,
                        name = emote.name,
                        url = emote.urls.x3
                    )
                )
            } else {
                buffer.append(word)
            }
        }
        if (buffer.isNotEmpty()) {
            out.add(MessageFragment.Text(buffer.toString()))
        }
    }

    /**
     * Cheap guard: a Text fragment can only contain an emote if it has at
     * least one non-whitespace character. This lets us skip the StringBuilder
     * allocation path for empty/whitespace-only leaders produced by the emote
     * tag slicing.
     */
    private fun String.containsEmoteCandidate(): Boolean {
        for (c in this) if (!c.isWhitespace()) return true
        return false
    }
}
