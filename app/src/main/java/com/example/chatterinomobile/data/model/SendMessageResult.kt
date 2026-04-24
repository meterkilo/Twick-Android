package com.example.chatterinomobile.data.model

sealed interface SendMessageResult {
    data object Sent : SendMessageResult
    data object EmptyMessage : SendMessageResult
    data object Anonymous : SendMessageResult
    data object Disconnected : SendMessageResult
    data class Failed(val message: String) : SendMessageResult
}
