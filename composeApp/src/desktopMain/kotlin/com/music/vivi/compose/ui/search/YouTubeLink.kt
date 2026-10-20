package com.music.vivi.compose.ui.search

import java.net.URI

private val VideoId = Regex("[A-Za-z0-9_-]{11}")

/**
 * The video id in a pasted YouTube / YouTube Music link (watch, youtu.be, shorts, embed, live), or null when
 * [input] is not such a link. Playlist-only and channel links have no video id and return null.
 */
internal fun youTubeVideoId(input: String): String? {
    val text = input.trim()
    if (text.isEmpty() || text.any { it.isWhitespace() }) return null
    val uri = runCatching { URI(if ("://" in text) text else "https://$text") }.getOrNull() ?: return null
    val host = uri.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return null
    val path = uri.path.orEmpty()
    val id = when (host) {
        "music.youtube.com", "youtube.com" -> when {
            path == "/watch" -> uri.rawQuery.orEmpty().split('&').firstOrNull { it.startsWith("v=") }?.removePrefix("v=")
            path.startsWith("/shorts/") || path.startsWith("/embed/") || path.startsWith("/live/") ->
                path.split('/').getOrNull(2)
            else -> null
        }
        "youtu.be" -> path.removePrefix("/").substringBefore('/')
        else -> null
    }
    return id?.takeIf { VideoId.matches(it) }
}
