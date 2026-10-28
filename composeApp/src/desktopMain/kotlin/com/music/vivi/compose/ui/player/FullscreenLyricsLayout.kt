package com.music.vivi.compose.ui.player

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Where the Now Playing stack and the lyrics column sit across the fullscreen page's content width. */
internal data class LyricsSplit(
    val stackLeft: Dp,
    /** Width the stack gets: its natural width unless the window is too narrow to fit the lyrics next to it. */
    val stackWidth: Dp,
    val lyricsLeft: Dp,
    val lyricsWidth: Dp,
)

/** Cover and controls widths after fitting the stack into a narrower width. */
internal data class StackFit(val cover: Dp, val controls: Dp)

internal object FullscreenLyricsLayout {
    val Gap = 48.dp
    val PreferredLyricsWidth = 560.dp
    val MinLyricsWidth = 380.dp
    val MinStackWidth = 340.dp
    private val MinControls = 320.dp
    private val MinCover = 140.dp

    /** Left edge of a [natural]-wide stack centred in [available] (the lyrics-off layout). */
    fun centeredLeft(available: Dp, natural: Dp): Dp = ((available - natural) / 2f).coerceAtLeast(0.dp)

    /**
     * Lyrics on: the stack keeps its [natural] width and slides left only as far as needed so a
     * [PreferredLyricsWidth] column fits to its right. When even a flush-left stack leaves less than
     * [MinLyricsWidth], the stack shrinks (down to [MinStackWidth]) to make room.
     */
    fun split(available: Dp, natural: Dp): LyricsSplit {
        val centered = centeredLeft(available, natural)
        val shift = (centered + natural + Gap + PreferredLyricsWidth - available).coerceAtLeast(0.dp)
        val left = (centered - shift).coerceAtLeast(0.dp)
        val roomRight = available - (left + natural + Gap)
        if (roomRight >= MinLyricsWidth) {
            return LyricsSplit(left, natural, left + natural + Gap, minOf(roomRight, PreferredLyricsWidth))
        }
        val stack = minOf(natural, maxOf(MinStackWidth, available - Gap - MinLyricsWidth))
        return LyricsSplit(0.dp, stack, stack + Gap, (available - stack - Gap).coerceAtLeast(0.dp))
    }

    /**
     * Shrinks cover and controls so the stack is at most [target] wide. A vertical stack is as wide as
     * its widest part; a side-by-side one is cover + [gap] + controls, giving up controls width first.
     */
    fun fit(cover: Dp, controls: Dp, gap: Dp, vertical: Boolean, target: Dp): StackFit {
        if (vertical) return StackFit(minOf(cover, target), minOf(controls, maxOf(target, MinControls)))
        val excess = cover + gap + controls - target
        if (excess <= 0.dp) return StackFit(cover, controls)
        val controlsGive = minOf(excess, (controls - MinControls).coerceAtLeast(0.dp))
        val coverGive = minOf(excess - controlsGive, (cover - MinCover).coerceAtLeast(0.dp))
        return StackFit(cover - coverGive, controls - controlsGive)
    }
}
