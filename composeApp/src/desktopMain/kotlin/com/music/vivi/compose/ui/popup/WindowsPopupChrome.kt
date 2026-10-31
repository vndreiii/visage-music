package com.music.vivi.compose.ui.popup

import com.music.vivi.nativelib.HostOs
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary
import java.awt.Window

/**
 * Win32 touches for the Now Playing popup (JNA, same approach as the rest of the app's native code).
 *
 * NOT TESTED ON WINDOWS (the author had no Windows host). Every entry point is a no-op off Windows and swallows
 * failures: a missing nicety must never take the popup down. See docs/popup-player.md.
 */
object WindowsPopupChrome {
    private interface User32 : StdCallLibrary {
        fun GetWindowLongPtrW(hwnd: Pointer, index: Int): Long
        fun SetWindowLongPtrW(hwnd: Pointer, index: Int, value: Long): Long
        fun SetWindowPos(hwnd: Pointer, after: Pointer?, x: Int, y: Int, cx: Int, cy: Int, flags: Int): Boolean
        fun ReleaseCapture(): Boolean
        fun SendMessageW(hwnd: Pointer, msg: Int, wParam: Long, lParam: Long): Long
    }

    private interface Dwmapi : StdCallLibrary {
        fun DwmSetWindowAttribute(hwnd: Pointer, attr: Int, value: com.sun.jna.ptr.IntByReference, size: Int): Int
    }

    private const val GWL_EXSTYLE = -20
    private const val SWP_NOSIZE = 0x0001
    private const val SWP_NOMOVE = 0x0002
    private const val SWP_NOZORDER = 0x0004
    private const val SWP_NOACTIVATE = 0x0010
    private const val SWP_FRAMECHANGED = 0x0020
    private const val WM_NCLBUTTONDOWN = 0x00A1
    private const val HTCAPTION = 2L

    /** DWMWA_WINDOW_CORNER_PREFERENCE (Windows 11 22000+); DWMWCP_ROUND = 2. Older builds return an error: ignored. */
    private const val DWMWA_WINDOW_CORNER_PREFERENCE = 33
    private const val DWMWCP_ROUND = 2

    private val user32: User32? by lazy { if (HostOs.isWindows) runCatching { Native.load("user32", User32::class.java) }.getOrNull() else null }
    private val dwm: Dwmapi? by lazy { if (HostOs.isWindows) runCatching { Native.load("dwmapi", Dwmapi::class.java) }.getOrNull() else null }

    private fun hwnd(window: Window): Pointer? =
        runCatching { Pointer(Native.getComponentID(window)) }.getOrNull()?.takeIf { Pointer.nativeValue(it) != 0L }

    /** Ask DWM for the system's rounded corners (Windows 11); harmless where unsupported. */
    fun applyRoundedCorners(window: Window) {
        if (!HostOs.isWindows) return
        runCatching {
            val h = hwnd(window) ?: return
            dwm?.DwmSetWindowAttribute(h, DWMWA_WINDOW_CORNER_PREFERENCE, com.sun.jna.ptr.IntByReference(DWMWCP_ROUND), 4)
        }
    }

    /** Toggle WS_EX_TOOLWINDOW: tool windows get no taskbar button and no Alt+Tab entry. */
    fun setSkipTaskbar(window: Window, skip: Boolean) {
        if (!HostOs.isWindows) return
        runCatching {
            val u = user32 ?: return
            val h = hwnd(window) ?: return
            val old = u.GetWindowLongPtrW(h, GWL_EXSTYLE)
            val new = WinExStyle.withToolWindow(old, skip)
            if (new != old) {
                u.SetWindowLongPtrW(h, GWL_EXSTYLE, new)
                u.SetWindowPos(h, null, 0, 0, 0, 0, SWP_NOMOVE or SWP_NOSIZE or SWP_NOZORDER or SWP_NOACTIVATE or SWP_FRAMECHANGED)
            }
        }
    }

    /**
     * Hand the current mouse press to the system's caption-drag loop: gives Aero Snap, edge tiling and
     * multi-monitor DPI handling for free. Call from a pointer-press on the drag area. Returns false when
     * unavailable so the caller can fall back to Compose-side dragging.
     */
    fun beginNativeDrag(window: Window): Boolean {
        if (!HostOs.isWindows) return false
        return runCatching {
            val u = user32 ?: return false
            val h = hwnd(window) ?: return false
            u.ReleaseCapture()
            u.SendMessageW(h, WM_NCLBUTTONDOWN, HTCAPTION, 0L)
            true
        }.getOrDefault(false)
    }
}
