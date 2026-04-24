package com.example.chatterinomobile.data.model

data class ReplyMetadata(
    val parentMessageId: String,
    val parentUserId: String?,
    val parentUserLogin: String?,
    val parentDisplayName: String?,
    val parentBody: String?
)
