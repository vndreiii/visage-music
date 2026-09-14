package com.music.vivi.compose.ui.player.lyrics

import com.music.kugou.KuGou
import com.music.lrclib.LrcLib
import com.music.musixmatch.Musixmatch
import com.music.paxsenix.Paxsenix
import com.music.unison.Unison
import com.music.vivi.betterlyrics.BetterLyrics
import com.music.vivi.compose.prefs.LyricsProvider
import com.music.youlyplus.YouLyPlus

/**
 * Dispatches title/artist lyrics fetch to the selected [LyricsProvider].
 * Duration uses seconds when known; `-1` means “don’t enforce duration match”.
 */
suspend fun fetchLyrics(
    provider: LyricsProvider,
    title: String,
    artist: String,
    durationMs: Long,
    album: String? = null,
    videoId: String? = null,
): Result<String> {
    val durationSec = if (durationMs > 0L) (durationMs / 1000L).toInt() else -1
    return when (provider) {
        LyricsProvider.LRCLIB -> LrcLib.getLyrics(title, artist, durationSec, album)
        LyricsProvider.KUGOU -> KuGou.getLyrics(title, artist, durationSec, album)
        LyricsProvider.MUSIXMATCH -> Musixmatch.getLyrics(title, artist, durationSec, album)
        LyricsProvider.BETTER_LYRICS -> BetterLyrics.getLyrics(title, artist, durationSec, album)
        LyricsProvider.YOULY_PLUS -> YouLyPlus.getLyrics(title, artist, durationSec, album, id = videoId)
        LyricsProvider.UNISON -> Unison.getLyrics(title, artist, durationSec, album, videoId = videoId)
        LyricsProvider.PAXSENIX -> Paxsenix.getLyrics(title, artist, durationSec, album)
    }
}
