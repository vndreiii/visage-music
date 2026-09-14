package com.music.vivi.compose

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState

val LocalWindowState = staticCompositionLocalOf<WindowState> {
    error("WindowState not provided")
}

/**
 * Enter/exit OS-level window fullscreen while remembering the prior placement
 * (Floating / Maximized) so Exit can restore it.
 */
object WindowFullscreen {
    @Volatile
    private var savedPlacement: WindowPlacement? = null

    fun enter(windowState: WindowState) {
        if (windowState.placement == WindowPlacement.Fullscreen) return
        savedPlacement = windowState.placement
        windowState.placement = WindowPlacement.Fullscreen
    }

    fun exit(windowState: WindowState) {
        if (windowState.placement != WindowPlacement.Fullscreen) {
            savedPlacement = null
            return
        }
        windowState.placement = savedPlacement ?: WindowPlacement.Floating
        savedPlacement = null
    }

    fun toggle(windowState: WindowState) {
        if (windowState.placement == WindowPlacement.Fullscreen) exit(windowState)
        else enter(windowState)
    }
}
