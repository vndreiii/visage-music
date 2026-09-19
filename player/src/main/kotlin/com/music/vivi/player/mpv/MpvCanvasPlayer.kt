package com.music.vivi.player.mpv

import com.sun.jna.Pointer
import com.sun.jna.StringArray
import java.awt.Canvas
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Second libmpv instance for silent looping canvas video, embedded via `wid=` into an AWT [Canvas].
 * Best-effort on X11 / XWayland; fails quietly on pure Wayland without a native window id.
 */
class MpvCanvasPlayer : AutoCloseable {
    private val lib = MpvLib.INSTANCE
    private var handle: Pointer? = null
    private val running = AtomicBoolean(true)
    private var eventThread: Thread? = null

    fun attachAndPlay(canvas: Canvas, videoUrl: String): Boolean {
        close()
        running.set(true)
        val wid = nativeWindowId(canvas) ?: return false
        val h = lib.mpv_create() ?: return false
        handle = h
        lib.mpv_set_option_string(h, "audio", "no")
        lib.mpv_set_option_string(h, "vid", "yes")
        lib.mpv_set_option_string(h, "loop-file", "inf")
        lib.mpv_set_option_string(h, "terminal", "no")
        lib.mpv_set_option_string(h, "osc", "no")
        lib.mpv_set_option_string(h, "input-default-bindings", "no")
        lib.mpv_set_option_string(h, "keep-open", "yes")
        lib.mpv_set_option_string(h, "wid", wid.toString())
        // Let mpv auto-detect the video output for the embedded window.
        // vo=x11 fails on pure Wayland; omit to let mpv try gpu/x11/etc.
        runCatching { lib.mpv_set_option_string(h, "vo", "gpu") }
        runCatching { lib.mpv_set_option_string(h, "gpu-context", "x11egl") }
        runCatching { lib.mpv_set_option_string(h, "hwdec", "auto") }
        if (lib.mpv_initialize(h) != 0) {
            lib.mpv_terminate_destroy(h)
            handle = null
            return false
        }
        lib.mpv_command(h, StringArray(arrayOf("loadfile", videoUrl, "replace")))
        eventThread = thread(name = "canvas-mpv-events", isDaemon = true) {
            while (running.get()) {
                lib.mpv_wait_event(h, 0.25) ?: continue
            }
        }
        return true
    }

    fun setPaused(paused: Boolean) {
        val h = handle ?: return
        runCatching {
            lib.mpv_set_property_string(h, "pause", if (paused) "yes" else "no")
        }
    }

    override fun close() {
        running.set(false)
        eventThread?.interrupt()
        eventThread = null
        handle?.let { h ->
            runCatching {
                lib.mpv_command(h, StringArray(arrayOf("stop")))
                lib.mpv_terminate_destroy(h)
            }
        }
        handle = null
    }

    companion object {
        fun nativeWindowId(canvas: Canvas): Long? = runCatching {
            val getPeer = java.awt.Component::class.java.getDeclaredMethod("getPeer")
            getPeer.isAccessible = true
            val peer = getPeer.invoke(canvas) ?: return null
            val m: Method = peer.javaClass.methods.firstOrNull {
                it.name == "getWindow" && it.parameterCount == 0
            } ?: peer.javaClass.methods.firstOrNull {
                it.name == "getContentWindow" && it.parameterCount == 0
            } ?: return null
            m.isAccessible = true
            when (val v = m.invoke(peer)) {
                is Long -> v
                is Number -> v.toLong()
                else -> null
            }
        }.getOrNull()
    }
}
