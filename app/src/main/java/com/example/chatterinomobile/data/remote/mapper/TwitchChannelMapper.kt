package com.example.chatterinomobile.data.remote.mapper

import com.example.chatterinomobile.data.model.Channel
import com.example.chatterinomobile.data.remote.dto.HelixStreamDto
import com.example.chatterinomobile.data.remote.dto.HelixUserDto

/**
 * Fold a [HelixUserDto] and its matching [HelixStreamDto] (if live) into a
 * single [Channel]. Streams are keyed by `user_id`, and a null [stream] means
 * the channel is offline.
 *
 * The stream thumbnail URL uses `{width}x{height}` placeholders that callers
 * must replace — we do that here once so the UI never has to.
 */
fun HelixUserDto.toChannel(stream: HelixStreamDto? = null): Channel = Channel(
    id = id,
    login = login,
    displayName = displayName,
    isLive = stream != null,
    viewerCount = stream?.viewerCount ?: 0,
    gameName = stream?.gameName,
    title = stream?.title,
    thumbnailUrl = stream?.thumbnailUrl?.replace("{width}", "1280")?.replace("{height}", "720")
)
