package com.music.vivi.player.mpv

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.StringArray
import com.sun.jna.ptr.DoubleByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.ptr.IntByReference
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A native pixel buffer the renderer writes one frame into (RGBX, 4 bytes/px).
 */
class PixelTarget(val address: Long, val width: Int, val height: Int, val stride: Int)

/** Consumer side of [MpvVideoRenderer]: hands out buffers and receives finished frames. */
interface FrameSink {
    /** Buffer of exactly [width]x[height] to render into, or null to drop this frame. */
    fun lock(width: Int, height: Int): PixelTarget?

    /** Called after [lock] on the render thread; [rendered] is false if mpv failed. */
    fun unlock(target: PixelTarget, rendered: Boolean)

    /**
     * Playback position after each rendered frame (render thread). The sink drives
     * looping by cross-fading into a second renderer, so this one never seeks itself.
     */
    fun onPosition(positionSec: Double, durationSec: Double) {}
    fun onPlaybackEnd() {}
}

/**
 * Muted video decoded by libmpv and rendered in software (render API "sw")
 * into caller-owned memory — no native window, so the frames can be drawn anywhere
 * in the Compose tree (clipped, blurred, faded) with correct Z-order on X11 and Wayland.
 */
class MpvVideoRenderer(
    private val url: String,
    private val sink: FrameSink,
    /** Longest edge of the rendered frame; the video's own aspect ratio is kept. */
    private val maxEdge: Int = 1280,
    httpHeaders: Map<String, String> = emptyMap(),
    /** Start parked on the first frame until [setHeld] releases it (standby loop slot). */
    startHeld: Boolean = false,
    /** Opt-in GPU decoding; software is the default to avoid native driver crashes. */
    useGpu: Boolean = false,
    /** Initial playback position (music-video sync starts mid-track). */
    private val startSec: Double = 0.0,
) : AutoCloseable {
    private val lib = MpvLib.INSTANCE
    private val render = MpvRenderLib.INSTANCE
    private val handle: Pointer = lib.mpv_create() ?: error("mpv_create failed")
    private val running = AtomicBoolean(true)
    private val wake = Semaphore(0)
    @Volatile private var paused = false
    @Volatile private var held = startHeld
    @Volatile private var seekToStart = false
    @Volatile private var pendingSeek = Double.NaN

    // Held as a field so JNA's callback trampoline is never garbage collected.
    private val updateCallback = object : MpvRenderLib.UpdateCallback {
        override fun invoke(ctx: Pointer?) {
            wake.release()
        }
    }

    private val renderThread: Thread

    init {
        opt("vo", "libmpv")
        opt("ao", "null")
        opt("aid", "no")
        opt("sid", "no")
        opt("terminal", "no")
        opt("msg-level", "all=error")
        opt("ytdl", "no")
        opt("loop-file", "no")
        opt("pause", if (startHeld) "yes" else "no")
        opt("keep-open", "yes")
        // Canvas uses mpv's software render API. Automatic hardware decoding
        // probes Vulkan for HEVC clips and can crash the whole JVM in the driver;
        // Java exceptions cannot catch that native SIGSEGV. Keep decoding on CPU.
        check(lib.mpv_set_option_string(handle, "hwdec", if (useGpu) "auto-copy-safe" else "no") == 0) {
            "Could not configure canvas decoding"
        }
        opt("cache", "yes")
        opt("demuxer-max-bytes", "48MiB")
        // Keep the whole clip behind the playhead so the loop seek is served from memory
        // (seamless) instead of re-fetching HLS segments and stalling at the seam.
        opt("demuxer-max-back-bytes", "96MiB")
        opt("demuxer-seekable-cache", "yes")
        opt("cache-secs", "120")
        opt("hr-seek", "yes")
        if (startSec > 0.0) opt("start", startSec.toString())
        httpHeaders["Referer"]?.let { opt("referrer", it) }
        // Canvas HLS ladders go up to 4K HEVC; cap so software scaling stays cheap.
        opt("hls-bitrate", "8000000")
        opt("vd-lavc-threads", "2")
        opt("sws-scaler", "fast-bilinear")
        httpHeaders.entries.firstOrNull { it.key.equals("User-Agent", true) }?.let { opt("user-agent", it.value) }
        check(lib.mpv_initialize(handle) == 0) { "mpv_initialize failed" }

        renderThread = thread(name = "mpv-canvas-render", isDaemon = true) { renderLoop() }
    }

    private fun opt(name: String, value: String) {
        runCatching { lib.mpv_set_option_string(handle, name, value) }
    }

    fun setPaused(value: Boolean) {
        paused = value
        applyPause()
    }

    /** A held renderer stays parked regardless of [setPaused]. */
    fun setHeld(value: Boolean) {
        held = value
        applyPause()
    }

    /** Seek (absolute, exact) to [sec]; applied on the render thread. */
    fun seekTo(sec: Double) {
        pendingSeek = sec
        wake.release()
    }

    /** Rewind to the first frame; call while held so the cache-served seek is invisible. */
    fun rewind() {
        seekToStart = true
        wake.release()
    }

    private fun applyPause() {
        val want = if (paused || held) "yes" else "no"
        runCatching { lib.mpv_set_property_string(handle, "pause", want) }
    }

    private fun renderLoop() {
        val ctxRef = PointerByReference()
        val apiType = Memory(3).apply { setString(0, "sw", "ASCII") }
        val createParams = params(MPV_RENDER_PARAM_API_TYPE to apiType)
        if (render.mpv_render_context_create(ctxRef, handle, createParams) < 0) {
            System.err.println("MpvVideoRenderer: sw render context unavailable")
            lib.mpv_terminate_destroy(handle)
            return
        }
        val ctx = ctxRef.value
        render.mpv_render_context_set_update_callback(ctx, updateCallback, null)
        lib.mpv_command(handle, StringArray(arrayOf("loadfile", url, "replace")))

        val sizeMem = Memory(8)
        val strideMem = Memory(8)
        val formatMem = Memory(5).apply { setString(0, "rgb0", "ASCII") }
        val dw = DoubleByReference()
        val dh = DoubleByReference()
        val tp = DoubleByReference()
        val dur = DoubleByReference()
        val eof = IntByReference()
        var endReported = false
        try {
            while (running.get()) {
                wake.tryAcquire(250, TimeUnit.MILLISECONDS)
                if (!running.get()) break
                wake.drainPermits()
                val ps = pendingSeek
                if (!ps.isNaN()) {
                    pendingSeek = Double.NaN
                    endReported = false
                    lib.mpv_command(handle, StringArray(arrayOf("seek", ps.toString(), "absolute", "exact")))
                }
                if (seekToStart) {
                    seekToStart = false
                    endReported = false
                    if (lib.mpv_command(handle, StringArray(arrayOf("seek", "0", "absolute", "exact"))) < 0) {
                        lib.mpv_command(handle, StringArray(arrayOf("loadfile", url, "replace")))
                    }
                }
                // EOF produces no more render callbacks. Poll it even when there
                // is no new frame, so short clips / unknown-duration HLS still loop.
                if (!held && !paused && !endReported &&
                    lib.mpv_get_property(handle, "eof-reached", MpvLib.MPV_FORMAT_FLAG, eof) >= 0 && eof.value != 0
                ) {
                    endReported = true
                    sink.onPlaybackEnd()
                }
                val flags = render.mpv_render_context_update(ctx)
                if (flags and MPV_RENDER_UPDATE_FRAME == 0L) continue

                val vw = if (lib.mpv_get_property(handle, "dwidth", MpvLib.MPV_FORMAT_DOUBLE, dw) >= 0) dw.value else 0.0
                val vh = if (lib.mpv_get_property(handle, "dheight", MpvLib.MPV_FORMAT_DOUBLE, dh) >= 0) dh.value else 0.0
                if (vw < 2 || vh < 2) continue
                val scale = minOf(1.0, maxEdge / max(vw, vh))
                val w = (vw * scale).roundToInt().coerceAtLeast(2)
                val h = (vh * scale).roundToInt().coerceAtLeast(2)

                val target = sink.lock(w, h) ?: continue
                sizeMem.setInt(0, target.width)
                sizeMem.setInt(4, target.height)
                strideMem.setLong(0, target.stride.toLong())
                val renderParams = params(
                    MPV_RENDER_PARAM_SW_SIZE to sizeMem,
                    MPV_RENDER_PARAM_SW_FORMAT to formatMem,
                    MPV_RENDER_PARAM_SW_STRIDE to strideMem,
                    MPV_RENDER_PARAM_SW_POINTER to Pointer(target.address),
                )
                val rc = render.mpv_render_context_render(ctx, renderParams)
                sink.unlock(target, rc >= 0)

                if (rc >= 0 && lib.mpv_get_property(handle, "time-pos", MpvLib.MPV_FORMAT_DOUBLE, tp) >= 0) {
                    val duration = if (lib.mpv_get_property(handle, "duration", MpvLib.MPV_FORMAT_DOUBLE, dur) >= 0) dur.value else 0.0
                    sink.onPosition(tp.value, duration)
                }
            }
        } catch (t: Throwable) {
            System.err.println("MpvVideoRenderer: ${t.javaClass.simpleName}: ${t.message}")
        } finally {
            render.mpv_render_context_set_update_callback(ctx, null, null)
            render.mpv_render_context_free(ctx)
            lib.mpv_terminate_destroy(handle)
        }
    }

    override fun close() {
        if (!running.compareAndSet(true, false)) return
        wake.release()
    }

    private fun params(vararg entries: Pair<Int, Pointer>): Memory {
        // struct mpv_render_param { int type; void *data; } — 16 bytes on 64-bit, zero-terminated.
        val entrySize = 8 + Native.POINTER_SIZE
        val mem = Memory(((entries.size + 1) * entrySize).toLong())
        mem.clear()
        entries.forEachIndexed { i, (type, data) ->
            mem.setInt((i * entrySize).toLong(), type)
            mem.setPointer((i * entrySize + 8).toLong(), data)
        }
        return mem
    }

    private companion object {
        const val MPV_RENDER_PARAM_API_TYPE = 1
        const val MPV_RENDER_PARAM_SW_SIZE = 17
        const val MPV_RENDER_PARAM_SW_FORMAT = 18
        const val MPV_RENDER_PARAM_SW_STRIDE = 19
        const val MPV_RENDER_PARAM_SW_POINTER = 20
        const val MPV_RENDER_UPDATE_FRAME = 1L
    }
}

@Suppress("FunctionName")
internal interface MpvRenderLib : Library {
    fun interface UpdateCallback : Callback {
        fun invoke(ctx: Pointer?)
    }

    fun mpv_render_context_create(res: PointerByReference, mpv: Pointer, params: Pointer): Int
    fun mpv_render_context_set_update_callback(ctx: Pointer, callback: UpdateCallback?, userdata: Pointer?)
    fun mpv_render_context_update(ctx: Pointer): Long
    fun mpv_render_context_render(ctx: Pointer, params: Pointer): Int
    fun mpv_render_context_free(ctx: Pointer)

    companion object {
        val INSTANCE: MpvRenderLib by lazy {
            MpvLib.INSTANCE // forces LC_NUMERIC=C and proves libmpv is loadable
            val candidates = libraryCandidates()
            var last: Throwable? = null
            for (name in candidates) {
                try {
                    return@lazy Native.load(name, MpvRenderLib::class.java)
                } catch (t: Throwable) {
                    last = t
                }
            }
            throw IllegalStateException("libmpv render API unavailable", last)
        }
    }
}
