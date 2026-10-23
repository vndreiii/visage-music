package com.music.vivi.compose.ui.player.lyrics

import kotlin.math.abs

/** Per-song lyrics delay: positive shows the lyrics later, negative earlier. */
internal const val LYRICS_DELAY_STEP_MS = 100
internal const val LYRICS_DELAY_MAX_MS = 5_000

internal fun stepLyricsDelay(currentMs: Int, steps: Int): Int =
    (currentMs + steps * LYRICS_DELAY_STEP_MS).coerceIn(-LYRICS_DELAY_MAX_MS, LYRICS_DELAY_MAX_MS)

/** "+0.3s", "-1.2s", "0.0s". */
internal fun formatLyricsDelay(ms: Int): String {
    val tenths = Math.round(abs(ms) / 100.0).toInt()
    val body = "${tenths / 10}.${tenths % 10}s"
    return when {
        tenths == 0 -> body
        ms > 0 -> "+$body"
        else -> "-$body"
    }
}
