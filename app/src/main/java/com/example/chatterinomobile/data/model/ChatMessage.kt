package com.example.chatterinomobile.data.model

/**
 * A single chat message, fully parsed and ready to render.
 **/


data class ChatMessage(
    val id: String,
    val channelId: String,
    val author : ChatUser,
    val reply: ReplyMetadata? = null,
    val fragment: List<MessageFragment>,
    val badges: List<Badge> = emptyList(),
    val timestamp: Long,
    val Type: MessageType = MessageType.Regular
)

/**
 * Distinguishes rendering variants of a message without duplicating the parser.
 */
sealed class MessageType {
    /** Normal message. */
    data object Regular : MessageType()

    /** /me action — rendered italic, author color applied to body. */
    data object Action: MessageType()

    /** Channel-point highlighted message — rendered with accent background. */
    data object Highlighted: MessageType()

    /** Reply to another message. */
    data class Reply(val parentId : String) : MessageType()

    /** System notice ("User has been banned", etc). */
    data class System(val text: String) : MessageType()
}
