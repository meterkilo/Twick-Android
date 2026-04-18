package com.example.chatterinomobile.data.model

sealed class MessageFragment {
    data class Text(val content: String) : MessageFragment()
    data class Emote(val id: String, val name: String, val url: String) : MessageFragment()
    data class Mention(val username: String) : MessageFragment()
}