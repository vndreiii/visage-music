package com.music.vivi.compose.ui.player.lyrics

import com.music.kugou.KuGou
import com.music.lrclib.LrcLib
import com.music.musixmatch.Musixmatch
import com.music.paxsenix.Paxsenix
import com.music.unison.Unison
import com.music.vivi.betterlyrics.BetterLyrics
import com.music.vivi.compose.prefs.LyricsProvider
import com.music.youlyplus.YouLyPlus
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

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
): Result<String> = fetchLyricsWithSource(provider, title, artist, durationMs, album, videoId).map { it.text }

/** Lyrics text plus the provider that actually answered (may differ from the requested one). */
data class FetchedLyrics(val provider: LyricsProvider, val text: String)

suspend fun fetchLyricsWithSource(
    provider: LyricsProvider,
    title: String,
    artist: String,
    durationMs: Long,
    album: String? = null,
    videoId: String? = null,
): Result<FetchedLyrics> {
    var last: Result<String>? = null
    // The chosen provider first, then the others. A provider that is down (HTTP 503) or has no
    // match must not leave the player without lyrics while another one has them.
    for (candidate in listOf(provider) + FALLBACK_ORDER.filter { it != provider }) {
        val result = try {
            withTimeout(PROVIDER_TIMEOUT_MS) { fetchFrom(candidate, title, artist, durationMs, album, videoId) }
        } catch (e: TimeoutCancellationException) {
            Result.failure(e)
        }
        result.getOrNull()?.takeIf { it.isNotBlank() }?.let { return Result.success(FetchedLyrics(candidate, it)) }
        last = result
    }
    return last?.map { FetchedLyrics(provider, it) } ?: Result.failure(IllegalStateException("no lyrics provider available"))
}

private const val PROVIDER_TIMEOUT_MS = 8_000L

/** Fallback order: the ones that answer fastest and match best come first. */
private val FALLBACK_ORDER = listOf(
    LyricsProvider.LRCLIB,
    LyricsProvider.BETTER_LYRICS,
    LyricsProvider.KUGOU,
    LyricsProvider.YOULY_PLUS,
    LyricsProvider.PAXSENIX,
    LyricsProvider.MUSIXMATCH,
    LyricsProvider.UNISON,
)

private suspend fun fetchFrom(
    provider: LyricsProvider,
    title: String,
    artist: String,
    durationMs: Long,
    album: String?,
    videoId: String?,
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
