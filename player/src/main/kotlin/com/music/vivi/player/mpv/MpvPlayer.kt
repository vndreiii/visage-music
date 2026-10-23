package com.music.vivi.player.mpv

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import com.sun.jna.Pointer
import com.sun.jna.StringArray
import com.sun.jna.ptr.DoubleByReference
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * Thin audio-only libmpv wrapper. Position/duration polled from a dedicated thread.
 */
class MpvPlayer(
    private val onState: (State) -> Unit,
    private val onPosition: (positionSec: Double, durationSec: Double) -> Unit,
    private val onError: (String) -> Unit = {},
) : PlaybackEngine {
    enum class State { Idle, Playing, Paused, Ended, Error }

    private val lib = MpvLib.INSTANCE
    private val handle: Pointer = lib.mpv_create()
        ?: error("mpv_create failed — is libmpv installed?")
    private val running = AtomicBoolean(true)
    private val lastEndError = AtomicReference<String?>(null)
    @Volatile private var ready = CompletableDeferred<Unit>()
    private val eventThread: Thread

    /**
     * Every libmpv command is synchronous: it waits for mpv's core thread, which can be wedged for a
     * long time (stalled CDN read, audio device stuck). Callers (often the UI thread) must never wait
     * on that, so commands run here, in order, and callers either fire-and-forget or wait with a bound.
     */
    private val commands = Executors.newSingleThreadExecutor { r ->
        Thread(r, "mpv-cmd-${System.identityHashCode(this)}").apply { isDaemon = true }
    }
    @Volatile private var lastPaused = false

    private fun fire(what: String, block: () -> Unit) {
        runCatching {
            commands.execute {
                try {
                    block()
                } catch (t: Throwable) {
                    System.err.println("mpv $what failed: ${t.javaClass.simpleName}: ${t.message}")
                }
            }
        }
    }

    private fun <T> await(what: String, timeoutMs: Long, block: () -> T): T {
        val f = commands.submit<T> { block() }
        try {
            return f.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            println("[play-trace ${java.time.LocalTime.now()}] mpv $what timed out after ${timeoutMs}ms — core is not responding")
            throw IllegalStateException("mpv $what timed out", e)
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }

    init {
        check(lib.mpv_set_option_string(handle, "vid", "no") == 0) { "set vid=no failed" }
        check(lib.mpv_set_option_string(handle, "terminal", "no") == 0)
        check(lib.mpv_set_option_string(handle, "msg-level", "ffmpeg=error,ao=error,cplayer=error") == 0)
        // Direct CDN URLs — don't let mpv's ytdl hook re-fetch and 403.
        runCatching { lib.mpv_set_option_string(handle, "ytdl", "no") }
        runCatching { lib.mpv_set_option_string(handle, "demuxer-max-bytes", "64MiB") }
        runCatching { lib.mpv_set_option_string(handle, "cache", "yes") }
        // Default is 60s; a stalled CDN connection would otherwise wedge the core (and every command) that long.
        runCatching { lib.mpv_set_option_string(handle, "network-timeout", "15") }
        check(lib.mpv_initialize(handle) == 0) {
            "mpv_initialize failed: ${lib.mpv_error_string(-1)}"
        }
        lib.mpv_observe_property(handle, 0, "pause", MpvLib.MPV_FORMAT_FLAG)
        lib.mpv_observe_property(handle, 0, "time-pos", MpvLib.MPV_FORMAT_DOUBLE)
        lib.mpv_observe_property(handle, 0, "duration", MpvLib.MPV_FORMAT_DOUBLE)

        eventThread = thread(name = "mpv-events", isDaemon = true) {
            while (running.get()) {
                try {
                    val ptr = lib.mpv_wait_event(handle, 0.2) ?: continue
                    val event = MpvEvent(ptr).also { it.read() }
                    when (event.event_id) {
                        MpvLib.MPV_EVENT_SHUTDOWN -> break
                        MpvLib.MPV_EVENT_END_FILE -> {
                            // loadfile replace emits END_FILE with reason=STOP — that must not
                            // look like a finished track or next/prev will double-fire.
                            val end = event.data?.let { MpvEventEndFile(it).also { e -> e.read() } }
                            val reason = end?.reason ?: MpvLib.MPV_END_FILE_REASON_EOF
                            println("[play-trace ${java.time.LocalTime.now()}] mpv END_FILE reason=$reason error=${end?.error}")
                            val endErr = end?.error ?: event.error
                            when (reason) {
                                MpvLib.MPV_END_FILE_REASON_EOF -> onState(State.Ended)
                                MpvLib.MPV_END_FILE_REASON_ERROR -> {
                                    val msg = lib.mpv_error_string(endErr) ?: "endfile error $endErr"
                                    ready.completeExceptionally(IllegalStateException(msg))
                                    lastEndError.set(msg)
                                    onError(msg)
                                    onState(State.Error)
                                }
                                else -> {
                                    // STOP / QUIT / REDIRECT — intentional unload; ignore for queue
                                }
                            }
                        }
                        MpvLib.MPV_EVENT_FILE_LOADED, MpvLib.MPV_EVENT_PLAYBACK_RESTART -> {
                            println("[play-trace ${java.time.LocalTime.now()}] mpv ${if (event.event_id == MpvLib.MPV_EVENT_FILE_LOADED) "FILE_LOADED" else "PLAYBACK_RESTART"} paused=${isPaused()}")
                            ready.complete(Unit)
                            lastEndError.set(null)
                            onState(State.Playing)
                        }
                    }
                    pollTime()
                } catch (t: Throwable) {
                    // Never let a callback crash kill the event loop — that freezes
                    // auto-advance and leaves Next/EOF dead for the rest of the session.
                    System.err.println(
                        "mpv-events handler error: ${t.javaClass.simpleName}: ${t.message}",
                    )
                    t.printStackTrace(System.err)
                }
            }
        }
    }

    override fun load(url: String, httpHeaders: Map<String, String>) {
        ready.cancel()
        ready = CompletableDeferred()
        lastEndError.set(null)
        await("load", 10_000) { loadBlocking(url, httpHeaders) }
    }

    private fun loadBlocking(url: String, httpHeaders: Map<String, String>) {
        if (httpHeaders.isNotEmpty()) {
            val ua = httpHeaders.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value
            if (ua != null) lib.mpv_set_property_string(handle, "user-agent", ua)
            
            val ref = httpHeaders.entries.firstOrNull { it.key.equals("Referer", ignoreCase = true) }?.value
            if (ref != null) lib.mpv_set_property_string(handle, "referrer", ref)

            val others = httpHeaders.filterKeys { 
                !it.equals("User-Agent", ignoreCase = true) && !it.equals("Referer", ignoreCase = true) 
            }
            if (others.isNotEmpty()) {
                val headerField = others.entries.joinToString(",") { (k, v) -> 
                    val escapedV = v.replace(",", "\\,")
                    "$k: $escapedV" 
                }
                val code = lib.mpv_set_property_string(handle, "http-header-fields", headerField)
                if (code < 0) {
                    onError("http-header-fields: ${lib.mpv_error_string(code)}")
                }
            } else {
                lib.mpv_set_property_string(handle, "http-header-fields", "")
            }
        } else {
            lib.mpv_set_property_string(handle, "http-header-fields", "")
        }
        // Keep startup warming and normal loads silent until the controller is ready.
        command("set", "pause", "yes")
        command("loadfile", url, "replace")
    }

    override suspend fun awaitReady() {
        withTimeout(15_000) { ready.await() }
    }

    override fun play() {
        fire("play") { command("set", "pause", "no") }
        onState(State.Playing)
    }

    private fun who(): String = Throwable().stackTrace.asSequence().drop(2)
        .filter { it.className.startsWith("com.music") }.take(4)
        .joinToString(" <- ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }

    override fun pause() {
        println("[play-trace ${java.time.LocalTime.now()}] mpv.pause() from ${who()}")
        fire("pause") { command("set", "pause", "yes") }
        onState(State.Paused)
    }

    override fun stop() {
        fire("stop") { command("stop") }
        onState(State.Idle)
    }

    /** Hard-cut audio for an immediate track change (search/playlist pick / skip). */
    override fun halt() {
        println("[play-trace ${java.time.LocalTime.now()}] mpv.halt() from ${who()}")
        ready.cancel()
        fire("halt") {
            runCatching { lib.mpv_command(handle, StringArray(arrayOf("stop"))) }
            runCatching { lib.mpv_set_property_string(handle, "pause", "yes") }
        }
        // Keep user volume — do not reset
    }

    /** mpv volume is 0–100 (can go higher; we clamp in the controller). */
    override fun setVolume(percent: Int) {
        val v = percent.coerceIn(0, 150)
        fire("volume") { lib.mpv_set_property_string(handle, "volume", v.toString()) }
    }

    override fun seek(positionSec: Double) {
        fire("seek") { command("seek", positionSec.toString(), "absolute") }
    }

    override fun isPaused(): Boolean = try {
        await("isPaused", 500) {
            val flag = com.sun.jna.ptr.IntByReference()
            (lib.mpv_get_property(handle, "pause", MpvLib.MPV_FORMAT_FLAG, flag) == 0 && flag.value != 0)
                .also { lastPaused = it }
        }
    } catch (_: IllegalStateException) {
        lastPaused
    }

    fun lastErrorMessage(): String? = lastEndError.get()

    private fun pollTime() {
        val pos = DoubleByReference()
        val dur = DoubleByReference()
        val posOk = lib.mpv_get_property(handle, "time-pos", MpvLib.MPV_FORMAT_DOUBLE, pos) == 0
        val durOk = lib.mpv_get_property(handle, "duration", MpvLib.MPV_FORMAT_DOUBLE, dur) == 0
        if (posOk || durOk) {
            onPosition(
                if (posOk) pos.value else 0.0,
                if (durOk) dur.value else 0.0,
            )
        }
    }

    private fun command(vararg args: String) {
        val code = lib.mpv_command(handle, StringArray(args))
        if (code < 0) {
            val msg = "mpv_command ${args.firstOrNull()} failed: ${lib.mpv_error_string(code)}"
            onError(msg)
            onState(State.Error)
            error(msg)
        }
    }

    override fun close() {
        running.set(false)
        ready.cancel()
        commands.shutdown()
        runCatching { commands.awaitTermination(2, TimeUnit.SECONDS) }
        // The poller must finish using the native handle before it is destroyed.
        if (Thread.currentThread() != eventThread) eventThread.join()
        runCatching { lib.mpv_terminate_destroy(handle) }
    }
}
