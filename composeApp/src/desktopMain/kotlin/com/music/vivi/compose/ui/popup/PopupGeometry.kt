package com.music.vivi.compose.ui.popup

/**
 * Pure geometry and persistence logic for the Now Playing popup window (no AWT / Compose types, so it is
 * unit-testable on any host). Sizes are in logical pixels (AWT / dp, the same thing on Compose Desktop).
 */
enum class PopupMode { Stack, Landscape }

object PopupGeometry {
    const val MIN_W = 280
    const val MIN_H = 340
    const val MAX_W = 1100
    const val MAX_H = 900
    const val DEFAULT_W = 340
    const val DEFAULT_H = 480

    /** Landscape (lyrics beside the player) needs a clearly wide window, not just "slightly wider than tall". */
    const val LANDSCAPE_ASPECT = 1.3f
    const val LANDSCAPE_MIN_W = 520

    /** Below this window height the layout drops optional rows (album, volume) and uses a one-line title. */
    const val COMPACT_BELOW_H = 430f

    /** Left player column in landscape: ~46% of the window at the narrow end, then fixed (does not stretch). */
    fun landscapeLeftWidth(windowWidth: Float): Float = (windowWidth * 0.46f).coerceIn(264f, 330f)

    fun modeFor(width: Float, height: Float): PopupMode =
        if (height > 0f && width >= LANDSCAPE_ASPECT * height && width >= LANDSCAPE_MIN_W) PopupMode.Landscape
        else PopupMode.Stack

    fun clampSize(w: Int, h: Int): Pair<Int, Int> = w.coerceIn(MIN_W, MAX_W) to h.coerceIn(MIN_H, MAX_H)
}

data class PopupBounds(val x: Int?, val y: Int?, val width: Int, val height: Int) {
    /** "x,y,w,h" with an empty x/y when the position is unknown. */
    fun encode(): String = "${x ?: ""},${y ?: ""},$width,$height"

    companion object {
        val Default = PopupBounds(null, null, PopupGeometry.DEFAULT_W, PopupGeometry.DEFAULT_H)

        /** Tolerant of garbage: anything unparsable falls back to [Default]; size is clamped to the allowed range. */
        fun decode(s: String?): PopupBounds {
            val p = s?.split(',') ?: return Default
            if (p.size != 4) return Default
            val w = p[2].trim().toIntOrNull() ?: return Default
            val h = p[3].trim().toIntOrNull() ?: return Default
            val (cw, ch) = PopupGeometry.clampSize(w, h)
            return PopupBounds(p[0].trim().toIntOrNull(), p[1].trim().toIntOrNull(), cw, ch)
        }
    }
}

data class ScreenRect(val x: Int, val y: Int, val width: Int, val height: Int) {
    val right get() = x + width
    val bottom get() = y + height
}

object PopupPlacement {
    /**
     * Keeps a remembered position usable after monitors or scaling changed: when less than [minVisible] px of
     * the window overlaps any screen, null is returned (let the system place it); otherwise the position is
     * nudged so the window stays on that screen.
     */
    fun restore(bounds: PopupBounds, screens: List<ScreenRect>, minVisible: Int = 64): Pair<Int, Int>? {
        val x = bounds.x ?: return null
        val y = bounds.y ?: return null
        val best = screens.maxByOrNull { overlap(x, y, bounds.width, bounds.height, it) } ?: return null
        if (overlap(x, y, bounds.width, bounds.height, best) < minVisible.toLong() * minVisible) return null
        val nx = x.coerceIn(best.x, maxOf(best.x, best.right - bounds.width))
        val ny = y.coerceIn(best.y, maxOf(best.y, best.bottom - bounds.height))
        return nx to ny
    }

    private fun overlap(x: Int, y: Int, w: Int, h: Int, s: ScreenRect): Long {
        val ox = minOf(x + w, s.right) - maxOf(x, s.x)
        val oy = minOf(y + h, s.bottom) - maxOf(y, s.y)
        return if (ox <= 0 || oy <= 0) 0L else ox.toLong() * oy
    }
}

/** Window-system flavour, decided from the environment; drives which tricks are applicable. */
enum class PopupDisplay { Windows, WaylandSession, X11, Other }

object PopupDisplayDetect {
    fun detect(osName: String, env: (String) -> String?): PopupDisplay = when {
        osName.startsWith("Windows", ignoreCase = true) -> PopupDisplay.Windows
        !osName.startsWith("Linux", ignoreCase = true) -> PopupDisplay.Other
        !env("WAYLAND_DISPLAY").isNullOrBlank() || env("XDG_SESSION_TYPE").equals("wayland", ignoreCase = true) -> PopupDisplay.WaylandSession
        !env("DISPLAY").isNullOrBlank() -> PopupDisplay.X11
        else -> PopupDisplay.Other
    }

    /** Wayland clients cannot position themselves, so a remembered x/y is only applied elsewhere. */
    fun canPosition(d: PopupDisplay) = d != PopupDisplay.WaylandSession
}

/** Windows extended-style math for the optional "skip the taskbar" tool-window mode. */
object WinExStyle {
    const val WS_EX_TOOLWINDOW = 0x00000080L
    const val WS_EX_APPWINDOW = 0x00040000L

    fun withToolWindow(exStyle: Long, skipTaskbar: Boolean): Long =
        if (skipTaskbar) (exStyle or WS_EX_TOOLWINDOW) and WS_EX_APPWINDOW.inv()
        else (exStyle and WS_EX_TOOLWINDOW.inv()) or WS_EX_APPWINDOW
}
