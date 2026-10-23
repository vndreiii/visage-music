package com.music.vivi.compose

import androidx.compose.ui.window.WindowState
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.Window
import kotlin.math.abs

/**
 * Maximize for the undecorated window on Windows. AWT's own maximize on an
 * undecorated frame covers the whole monitor (over the taskbar) and stays
 * "maximized" after the window is dragged, so the next click only un-maximizes
 * invisibly. Here "maximized" simply means "bounds fill the usable screen area",
 * which dragging breaks naturally.
 */
object WindowMaximize {
    @Volatile
    private var restoreBounds: Rectangle? = null

    private fun usableBounds(window: Window): Rectangle {
        val gc = window.graphicsConfiguration ?: return Rectangle(Toolkit.getDefaultToolkit().screenSize)
        val b = gc.bounds
        val i = Toolkit.getDefaultToolkit().getScreenInsets(gc)
        return Rectangle(b.x + i.left, b.y + i.top, b.width - i.left - i.right, b.height - i.top - i.bottom)
    }

    fun isMaximized(window: Window): Boolean {
        val u = usableBounds(window)
        val w = window.bounds
        return abs(w.x - u.x) <= 2 && abs(w.y - u.y) <= 2 && abs(w.width - u.width) <= 2 && abs(w.height - u.height) <= 2
    }

    fun toggle(window: Window, windowState: WindowState) {
        if (windowState.placement != androidx.compose.ui.window.WindowPlacement.Floating) {
            windowState.placement = androidx.compose.ui.window.WindowPlacement.Floating
        }
        if (isMaximized(window)) {
            val u = usableBounds(window)
            window.bounds = restoreBounds?.takeUnless { it.width >= u.width && it.height >= u.height }
                ?: Rectangle(u.x + u.width / 10, u.y + u.height / 10, u.width * 4 / 5, u.height * 4 / 5)
        } else {
            restoreBounds = window.bounds
            window.bounds = usableBounds(window)
        }
    }
}
