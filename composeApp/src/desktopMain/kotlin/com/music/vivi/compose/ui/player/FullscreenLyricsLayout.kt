package com.music.vivi.compose.ui.player

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.vivi.compose.prefs.FullscreenCoverSide

/**
 * Rectangles (relative to the fullscreen content area) for the big cover / video and for the column that
 * holds lyrics on top of the controls. [stacked] is the compact narrow-window fallback: cover above column.
 */
internal data class FullscreenSplit(
    val coverX: Dp,
    val coverY: Dp,
    /** Side of the square cover slot (0 when the cover is hidden). */
    val coverSize: Dp,
    val columnX: Dp,
    val columnY: Dp,
    val columnWidth: Dp,
    val columnHeight: Dp,
    val stacked: Boolean,
)

internal object FullscreenLyricsLayout {
    val Gap = 48.dp
    val StackedGap = 24.dp
    /** Narrowest lyrics / controls column worth showing next to the cover. */
    val MinColumn = 360.dp
    val MaxColumn = 720.dp
    val MinCover = 220.dp
    /** The square never takes more than this share of the width. */
    const val MaxCoverFraction = 0.6f
    /** Smallest cover in the stacked fallback. */
    val MinStackedCover = 140.dp
    /** Share of the height the stacked fallback gives the cover. */
    const val StackedCoverFraction = 0.36f

    /**
     * Cover square as tall as the whole area ([height]) but at most [MaxCoverFraction] of [width], scaled by
     * [scale]. It shrinks (never below [MinCover]) before it crowds the column below [MinColumn]; when even
     * that does not fit the layout [FullscreenSplit.stacked] — cover above, column below.
     */
    fun split(
        width: Dp,
        height: Dp,
        side: FullscreenCoverSide,
        coverShown: Boolean = true,
        scale: Float = 1f,
    ): FullscreenSplit {
        if (!coverShown) {
            val col = minOf(width, MaxColumn)
            return FullscreenSplit(0.dp, 0.dp, 0.dp, (width - col) / 2f, 0.dp, col, height, false)
        }
        val desired = minOf(height, width * MaxCoverFraction) * scale
        val room = width - Gap - MinColumn
        // Too narrow for cover + column, or a portrait screen (tablet upright): stack them.
        if (room < MinCover || height > width * 1.2f) {
            val cover = minOf(width, maxOf(MinStackedCover, height * StackedCoverFraction * scale))
            val col = minOf(width, MaxColumn)
            val colY = cover + StackedGap
            return FullscreenSplit(
                (width - cover) / 2f, 0.dp, cover,
                (width - col) / 2f, colY, col, (height - colY).coerceAtLeast(0.dp), true,
            )
        }
        val cover = minOf(maxOf(desired, MinCover), room)
        val col = minOf(width - cover - Gap, MaxColumn)
        val start = (width - (cover + Gap + col)) / 2f
        val coverY = ((height - cover) / 2f).coerceAtLeast(0.dp)
        return if (side == FullscreenCoverSide.LEFT) {
            FullscreenSplit(start, coverY, cover, start + cover + Gap, 0.dp, col, height, false)
        } else {
            FullscreenSplit(start + col + Gap, coverY, cover, start, 0.dp, col, height, false)
        }
    }
}
