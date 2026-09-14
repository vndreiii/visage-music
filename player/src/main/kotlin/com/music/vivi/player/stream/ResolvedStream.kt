package com.music.vivi.player.stream

import com.music.vivi.player.NowPlaying

data class ResolvedStream(
    val nowPlaying: NowPlaying,
    val streamUrl: String,
    val source: String = "youtube",
    /** HTTP headers required by googlevideo / CDN (User-Agent must match the player client). */
    val httpHeaders: Map<String, String> = emptyMap(),
)
