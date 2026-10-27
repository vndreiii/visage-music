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

/**
 * Windows only: leaving OS fullscreen re-creates the native peer, which drops the transparent
 * background of the undecorated window. The Compose-clipped rounded corners then show a white
 * fill instead of the desktop. Re-apply the transparent background to the window and its root
 * components once the placement has settled.
 */
object WindowTransparency {
    fun reapply(window: java.awt.Window) {
        if (!com.music.vivi.nativelib.HostOs.isWindows) return
        runCatching {
            val clear = java.awt.Color(0, 0, 0, 0)
            window.background = clear
            if (window is javax.swing.RootPaneContainer) {
                (window as? javax.swing.JComponent)?.isOpaque = false
                (window.rootPane as? javax.swing.JComponent)?.isOpaque = false
                (window.contentPane as? javax.swing.JComponent)?.apply {
                    isOpaque = false
                    background = clear
                }
            }
            window.repaint()
        }
    }
}
