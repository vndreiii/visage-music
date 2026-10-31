package com.music.vivi.compose.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowScope
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Window
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

/**
 * Drag-the-window area for the custom (undecorated) title bar.
 *
 * Compose's own WindowDraggableArea calls setLocation on every pointer delta. Pointer positions are
 * window-relative, so as soon as the window moves the next delta is measured against the new origin; with a
 * window manager that also applies the move this feeds back and the window bounces ("jelly" drag).
 *
 * Order of preference, so the compositor / WM does the move whenever it can:
 *  1. JetBrains Runtime's window-move API (`JBR.getWindowMove()`): xdg_toplevel.move on native Wayland
 *     (WLToolkit), the WM's own move on X11. The only way to move a window on native Wayland.
 *  2. On a real X11 server, a _NET_WM_MOVERESIZE message (what a normal title bar does). Skipped under XWayland:
 *     compositors such as Hyprland accept the message and then do nothing with it.
 *  3. Moving the window ourselves from screen-absolute pointer coordinates with the offset captured once at drag
 *     start (works for floating windows under XWayland; a tiled window cannot be moved by its client).
 * Windows and macOS keep Compose's implementation.
 */
@Composable
fun WindowScope.VisageWindowDragArea(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (!com.music.vivi.nativelib.HostOs.isLinux) {
        WindowDraggableArea(modifier, content = content)
        return
    }
    Box(modifier.visageWindowDrag(window)) { content() }
}

/**
 * The drag gesture of [VisageWindowDragArea] as a modifier, for dragging a window by a whole region (the popup
 * uses it on its entire body: children that consume the gesture, such as sliders and scrollers, keep it).
 */
@Composable
fun WindowScope.visageWindowDragModifier(): Modifier {
    if (!com.music.vivi.nativelib.HostOs.isLinux) return Modifier
    return Modifier.visageWindowDrag(window)
}

/**
 * The drag gesture. It deliberately does NOT use `detectDragGestures`: since Compose 1.7 a mouse has a ~0.1 dp touch
 * slop, so a click that moves one pixel between press and release was taken as a drag by this (parent) area, which
 * consumed the pointer and cancelled the child's click: the nav chip showed its ripple and then did nothing.
 * Here nothing is consumed until the pointer has travelled [DragSlop], and a press a child already consumed (sliders,
 * buttons) is left alone.
 */
private fun Modifier.visageWindowDrag(win: Window): Modifier = composed {
    val state = remember(win) { DragState() }
    Modifier.pointerInput(win) {
        val slop = DragSlop.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
            if (down.isConsumed) return@awaitEachGesture
            var travelled = Offset.Zero
            var dragging = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                if (!dragging) {
                    if (change.isConsumed) break
                    travelled += change.positionChange()
                    if (travelled.getDistance() < slop) continue
                    dragging = true
                    state.nativeActive = WindowMover.begin(win)
                    if (!state.nativeActive) {
                        state.startPointer = pointerOnScreen()
                        state.startWindow = win.location
                    }
                }
                change.consume()
                if (state.nativeActive) continue
                val p = pointerOnScreen() ?: continue
                val sp = state.startPointer ?: continue
                val sw = state.startWindow ?: continue
                win.setLocation(sw.x + (p.x - sp.x), sw.y + (p.y - sp.y))
            }
            state.nativeActive = false
        }
    }
}

/** How far the pointer must move before a press on the drag area becomes a window drag instead of a click. */
private val DragSlop = 6.dp

private class DragState {
    var nativeActive = false
    var startPointer: Point? = null
    var startWindow: Point? = null
}

private fun pointerOnScreen(): Point? = runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull()

/** True when the process talks to Wayland natively (JBR's WLToolkit). */
internal fun isNativeWayland(): Boolean =
    runCatching { java.awt.Toolkit.getDefaultToolkit().javaClass.name.startsWith("sun.awt.wl.") }.getOrDefault(false)

/** True when the X display is XWayland: a Wayland session is running but the app uses the X11 toolkit. */
internal fun isXWayland(): Boolean =
    !isNativeWayland() && (!System.getenv("WAYLAND_DISPLAY").isNullOrBlank() || System.getenv("XDG_SESSION_TYPE").equals("wayland", true))

/** Starts a compositor / WM driven window move. Returns true when one was started (it then owns the pointer). */
internal object WindowMover {
    fun begin(window: Window): Boolean {
        // Under XWayland both compositor-driven paths (JBR's X11 move service, _NET_WM_MOVERESIZE) are silently ignored by
        // Hyprland, so the move is done manually there. Native Wayland (WLToolkit) can only use JBR.
        val started = !isXWayland() && (jbrMove(window) || X11WindowMove.begin(window))
        // The compositor now owns the pointer and will not send us the button release; tell Compose it happened.
        if (started) SwingUtilities.invokeLater { releaseButton(window) }
        return started
    }

    private fun jbrMove(window: Window): Boolean = try {
        val mover = com.jetbrains.JBR.getWindowMove()
        if (mover == null) false else { mover.startMovingTogetherWithMouse(window, MouseEvent.BUTTON1); true }
    } catch (_: Throwable) { false } // not on the JetBrains Runtime, or the API is unsupported there

    private fun releaseButton(window: Window) {
        runCatching {
            val p = MouseInfo.getPointerInfo().location
            val origin = runCatching { window.locationOnScreen }.getOrNull()
            val lx = if (origin != null) p.x - origin.x else window.width / 2
            val ly = if (origin != null) p.y - origin.y else 18
            val target = SwingUtilities.getDeepestComponentAt(window, lx, ly) ?: window
            val t = SwingUtilities.convertPoint(window, Point(lx, ly), target)
            target.dispatchEvent(
                MouseEvent(target, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, t.x, t.y, 1, false, MouseEvent.BUTTON1),
            )
        }
    }
}

private interface Xlib : Library {
    fun XUngrabPointer(display: Pointer, time: NativeLong): Int
    fun XDefaultRootWindow(display: Pointer): NativeLong
    fun XInternAtom(display: Pointer, name: String, onlyIfExists: Int): NativeLong
    fun XSendEvent(display: Pointer, w: NativeLong, propagate: Int, mask: NativeLong, event: Pointer): Int
    fun XQueryPointer(
        display: Pointer, w: NativeLong, rootRet: Pointer, childRet: Pointer,
        rootX: Pointer, rootY: Pointer, winX: Pointer, winY: Pointer, mask: Pointer,
    ): Int
    fun XFlush(display: Pointer): Int
    fun XGetWindowProperty(
        display: Pointer, w: NativeLong, property: NativeLong, offset: NativeLong, length: NativeLong, delete: Int,
        reqType: NativeLong, actualType: Pointer, actualFormat: Pointer, nitems: Pointer, bytesAfter: Pointer, prop: Pointer,
    ): Int
    fun XFree(data: Pointer): Int
}

private object X11WindowMove {
    private const val MOVE = 8L // _NET_WM_MOVERESIZE_MOVE
    private const val SUBSTRUCTURE_MASK = (1L shl 19) or (1L shl 20) // SubstructureNotify | SubstructureRedirect
    private const val CLIENT_MESSAGE = 33

    private val xlib: Xlib? by lazy {
        runCatching { Native.load("X11", Xlib::class.java) }.getOrNull()
    }
    private var failed = false
    private var wmSupports: Boolean? = null

    /** Whether the running WM advertises _NET_WM_MOVERESIZE in _NET_SUPPORTED (a WM-less X server would just drop it). */
    private fun wmSupportsMoveResize(x: Xlib, display: Pointer, root: NativeLong): Boolean {
        wmSupports?.let { return it }
        val atomSupported = x.XInternAtom(display, "_NET_SUPPORTED", 1)
        val atomMove = x.XInternAtom(display, "_NET_WM_MOVERESIZE", 1).toLong()
        var found = false
        if (atomSupported.toLong() != 0L && atomMove != 0L) {
            val type = Memory(8); val fmt = Memory(8); val n = Memory(8); val after = Memory(8); val data = Memory(8)
            if (x.XGetWindowProperty(display, root, atomSupported, NativeLong(0), NativeLong(4096), 0, NativeLong(4), type, fmt, n, after, data) == 0) {
                val p = data.getPointer(0)
                if (p != null) {
                    val count = n.getLong(0).toInt()
                    for (i in 0 until count) if (p.getLong(i * 8L) == atomMove) { found = true; break }
                    x.XFree(p)
                }
            }
        }
        wmSupports = found
        return found
    }

    /** True when the window manager took over the move. */
    fun begin(window: Window): Boolean {
        if (failed) return false
        val x = xlib ?: return false
        val ok = runCatching {
            val toolkit = java.awt.Toolkit.getDefaultToolkit()
            if (toolkit.javaClass.name != "sun.awt.X11.XToolkit") return@runCatching false
            val tk = toolkit.javaClass
            val display = Pointer(tk.getMethod("getDisplay").invoke(null) as Long)
            val xid = frameXid(window) ?: return@runCatching false
            val lock = tk.getMethod("awtLock")
            val unlock = tk.getMethod("awtUnlock")
            lock.invoke(null)
            try {
                val root = x.XDefaultRootWindow(display)
                if (!wmSupportsMoveResize(x, display, root)) return@runCatching false
                val rx = Memory(8).also { it.clear() }
                val ry = Memory(8).also { it.clear() }
                x.XQueryPointer(display, root, Memory(8), Memory(8), rx, ry, Memory(8), Memory(8), Memory(8))
                val rootX = rx.getInt(0).toLong()
                val rootY = ry.getInt(0).toLong()
                // The toolkit holds an implicit pointer grab for the pressed button; release it so the WM can grab.
                x.XUngrabPointer(display, NativeLong(0))
                val ev = Memory(192).also { it.clear() }
                ev.setInt(0, CLIENT_MESSAGE)
                ev.setInt(16, 1) // send_event
                ev.setPointer(24, display)
                ev.setLong(32, xid)
                ev.setLong(40, x.XInternAtom(display, "_NET_WM_MOVERESIZE", 0).toLong())
                ev.setInt(48, 32)
                ev.setLong(56, rootX)
                ev.setLong(64, rootY)
                ev.setLong(72, MOVE)
                ev.setLong(80, 1) // button 1
                ev.setLong(88, 1) // source: normal application
                x.XSendEvent(display, root, 0, NativeLong(SUBSTRUCTURE_MASK), ev)
                x.XFlush(display)
            } finally {
                unlock.invoke(null)
            }
            true
        }.getOrDefault(false)
        if (!ok) failed = true
        return ok
    }

    private fun frameXid(window: Window): Long? = runCatching {
        val accessor = Class.forName("sun.awt.AWTAccessor").getMethod("getComponentAccessor").invoke(null)
        val getPeer = accessor.javaClass.interfaces.first { it.simpleName == "ComponentAccessor" }
            .getMethod("getPeer", java.awt.Component::class.java)
        val peer = getPeer.invoke(accessor, window) ?: return@runCatching null
        var c: Class<*>? = peer.javaClass
        while (c != null) {
            val m = runCatching { c!!.getDeclaredMethod("getShell") }.getOrNull()
            if (m != null) { m.isAccessible = true; return@runCatching m.invoke(peer) as Long }
            c = c.superclass
        }
        val m = Class.forName("sun.awt.X11.XBaseWindow").getDeclaredMethod("getWindow")
        m.isAccessible = true
        m.invoke(peer) as Long
    }.getOrNull()
}
