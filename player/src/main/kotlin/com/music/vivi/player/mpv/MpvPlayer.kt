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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** One output device as mpv reports it (`audio-device-list`). [name] is what `audio-device` accepts. */
data class MpvAudioDevice(val name: String, val description: String)

/**
 * Thin audio-only libmpv wrapper. Position/duration polled from a dedicated thread.
 */
class MpvPlayer(
    private val onState: (State) -> Unit,
    private val onPosition: (positionSec: Double, durationSec: Double) -> Unit,
    private val onError: (String) -> Unit = {},
) : PlaybackEngine {
    enum class State { Idle, Playing, Paused, Ended, Error }

    companion object {
        private val live = CopyOnWriteArrayList<MpvPlayer>()

        /** mpv `audio-device` value applied to every engine (new ones read it at creation). */
        @Volatile
        var audioDevice: String = "auto"
            private set

        /** Lists the outputs mpv can use (`wasapi/{guid}` on Windows). Empty when no engine is alive. */
        fun listAudioDevices(): List<MpvAudioDevice> =
            live.firstOrNull()?.let { engine -> runCatching { engine.readAudioDevices() }.getOrNull() }.orEmpty()

        /** Routes all engines to [name] ("auto" for mpv's own choice). Returns false if no engine is alive. */
        fun selectAudioDevice(name: String): Boolean {
            audioDevice = name
            if (live.isEmpty()) return false
            live.forEach { it.setAudioDevice(name) }
            return true
        }

        /** What the playing engine decodes and what it hands to the audio device; null when nothing is loaded. */
        fun streamInfo(): AudioStreamInfo? {
            val engines = live.toList()
            val playing = engines.firstOrNull { !it.lastPaused } ?: engines.firstOrNull()
            return playing?.let { runCatching { it.readStreamInfo() }.getOrNull() }
        }

        /** Sets the device a not-yet-created engine starts with. Call before the players are built. */
        fun presetAudioDevice(name: String) {
            audioDevice = name.ifBlank { "auto" }
        }
    }

    private val lib = MpvLib.INSTANCE
    private val handle: Pointer = lib.mpv_create()
        ?: error("mpv_create failed — is libmpv installed?")
    private val running = AtomicBoolean(true)
    private val lastEndError = AtomicReference<String?>(null)
    @Volatile private var ready = CompletableDeferred<Unit>()
    private val eventThread: Thread
    private val levelThread: Thread

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
        if (audioDevice != "auto") runCatching { lib.mpv_set_option_string(handle, "audio-device", audioDevice) }
        // Linux playback goes through PipeWire first, then PulseAudio, then plain ALSA. An explicit "alsa/..." device
        // already names its own output driver, so the order only applies to the automatic choice.
        if (System.getProperty("os.name").orEmpty().startsWith("Linux") && !audioDevice.contains('/')) {
            runCatching { lib.mpv_set_option_string(handle, "ao", "pipewire,pulse,alsa") }
        }
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
        // Measures the loudness of what is playing without changing it; read by [levelThread] for the visuals.
        runCatching { lib.mpv_set_property_string(handle, "af", analysisGraph()) }
        lib.mpv_observe_property(handle, 0, "pause", MpvLib.MPV_FORMAT_FLAG)
        lib.mpv_observe_property(handle, 0, "time-pos", MpvLib.MPV_FORMAT_DOUBLE)
        lib.mpv_observe_property(handle, 0, "duration", MpvLib.MPV_FORMAT_DOUBLE)

        live.add(this)

        levelThread = thread(name = "mpv-levels", isDaemon = true) {
            while (running.get()) {
                try {
                    if (isPaused()) {
                        com.music.vivi.player.AudioLevels.silence(this)
                        Thread.sleep(120)
                        continue
                    }
                    val rms = readLevel("lavfi.astats.Overall.RMS_level")
                    val peak = readLevel("lavfi.astats.Overall.Peak_level")
                    val tails = bandTails
                    var bands: FloatArray? = null
                    if (tails != null) {
                        tails.forEachIndexed { i, t -> t.latest()?.let { lastBands[i] = it } }
                        bands = lastBands.copyOf()
                    }
                    if (rms != null) com.music.vivi.player.AudioLevels.report(this, rms, peak ?: rms, bands)
                    Thread.sleep(16)
                } catch (_: InterruptedException) {
                    break
                } catch (_: Throwable) {
                    runCatching { Thread.sleep(200) }
                }
            }
            com.music.vivi.player.AudioLevels.silence(this)
        }

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
        // Re-applying the filter restarts the band files, so they stay small however long the app runs.
        runCatching { lib.mpv_set_property_string(handle, "af", analysisGraph()) }
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

    // --- Audio analysis (for the visualizers) -------------------------------------------------------------------
    // The playing audio is split: the main path only has an `astats` meter on it (sound unchanged); three side branches
    // are band-limited, measured, and written to small text files by `ametadata`, then dropped. The level thread tails
    // those files. (Metadata cannot be carried back out of a merged branch, hence the files.)
    private val bandsDir: java.io.File? by lazy {
        if (System.getProperty("os.name").orEmpty().startsWith("Windows")) null
        else runCatching {
            java.io.File(System.getProperty("java.io.tmpdir"), "visage-bands-${ProcessHandle.current().pid()}-${System.identityHashCode(this)}")
                .also { it.mkdirs() }
        }.getOrNull()
    }

    private fun analysisGraph(): String {
        val dir = bandsDir
        val meter = "astats=metadata=1:reset=1:length=0.03"
        if (dir == null || dir.path.any { it in ":'\\ []," }) return "@vis:lavfi=[$meter]"
        fun band(name: String, filter: String) =
            "$filter,$meter,ametadata=mode=print:key=lavfi.astats.Overall.RMS_level:file=${dir.path}/$name.txt:direct=1,anullsink"
        return "@vis:lavfi=[asplit=4[a][b][c][d];" +
            "[b]${band("bass", "lowpass=f=150")};" +
            "[c]${band("mid", "highpass=f=150,lowpass=f=2500")};" +
            "[d]${band("high", "highpass=f=2500")};" +
            "[a]$meter]"
    }

    private class BandTail(private val file: java.io.File) {
        private var pos = 0L
        /** The newest dBFS value written since the last call, or null. */
        fun latest(): Float? {
            if (!file.exists()) return null
            val len = file.length()
            if (len < pos) pos = 0L
            if (len == pos) return null
            val from = maxOf(pos, len - 2048L)
            val bytes = ByteArray((len - from).toInt())
            java.io.RandomAccessFile(file, "r").use { it.seek(from); it.readFully(bytes) }
            pos = len
            val m = Regex("RMS_level=(-?[0-9.]+|-inf)").findAll(String(bytes, Charsets.US_ASCII)).lastOrNull() ?: return null
            val v = m.groupValues[1]
            return if (v.contains("inf")) -120f else v.toFloatOrNull()
        }
    }
    private val bandTails: List<BandTail>? by lazy { bandsDir?.let { d -> listOf("bass", "mid", "high").map { BandTail(java.io.File(d, "$it.txt")) } } }
    private val lastBands = floatArrayOf(-120f, -120f, -120f)

    private fun readText(key: String): String? {
        val ptr = lib.mpv_get_property_string(handle, key) ?: return null
        return try { ptr.getString(0).takeIf { it.isNotBlank() } } finally { lib.mpv_free(ptr) }
    }

    private fun readStreamInfo(): AudioStreamInfo? {
        val srcRate = readText("audio-params/samplerate")?.toIntOrNull() ?: return null
        return AudioStreamInfo(
            codec = readText("audio-codec-name"),
            bitrateKbps = readText("audio-bitrate")?.toDoubleOrNull()?.let { (it / 1000).toInt() }?.takeIf { it > 0 },
            sourceRate = srcRate,
            sourceFormat = readText("audio-params/format"),
            sourceChannels = readText("audio-params/hr-channels"),
            outRate = readText("audio-out-params/samplerate")?.toIntOrNull(),
            outFormat = readText("audio-out-params/format"),
            outChannels = readText("audio-out-params/hr-channels"),
        )
    }

    /** One `astats` reading in dB, or null when the filter has not produced one yet; silence ("-inf") is -120. */
    private fun readLevel(key: String): Float? {
        val ptr = lib.mpv_get_property_string(handle, "af-metadata/vis/$key") ?: return null
        return try {
            val text = ptr.getString(0)
            if (text.contains("inf")) -120f else text.toFloatOrNull()
        } finally {
            lib.mpv_free(ptr)
        }
    }

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

    /** Runs on the command thread. Reads mpv's `audio-device-list` node: an array of {name, description} maps. */
    private fun readAudioDevices(): List<MpvAudioDevice> = await("audio-device-list", 2_000) {
        val out = com.sun.jna.Memory(16)
        try {
            if (lib.mpv_get_property(handle, "audio-device-list", MpvLib.MPV_FORMAT_NODE, out) != 0) return@await emptyList()
            parseNodeDevices(out)
        } finally {
            lib.mpv_free_node_contents(out)
        }
    }

    private fun parseNodeDevices(root: Pointer): List<MpvAudioDevice> {
        // mpv_node: union (8 bytes) at 0, format at 8, size 16. mpv_node_list: num at 0, values at 8, keys at 16.
        if (root.getInt(8) != MpvLib.MPV_FORMAT_NODE_ARRAY) return emptyList()
        val list = root.getPointer(0)
        val count = list.getInt(0)
        val items = list.getPointer(8)
        return (0 until count).mapNotNull { i ->
            val item = items.share(i * 16L)
            if (item.getInt(8) != MpvLib.MPV_FORMAT_NODE_MAP) return@mapNotNull null
            val map = item.getPointer(0)
            val n = map.getInt(0)
            val values = map.getPointer(8)
            val keys = map.getPointer(16)
            var name: String? = null
            var description: String? = null
            for (j in 0 until n) {
                val key = keys.getPointer(j * 8L).getString(0)
                val value = values.share(j * 16L)
                if (value.getInt(8) != 1 /* MPV_FORMAT_STRING */) continue
                val text = value.getPointer(0).getString(0)
                when (key) {
                    "name" -> name = text
                    "description" -> description = text
                }
            }
            name?.let { MpvAudioDevice(it, description ?: it) }
        }
    }

    private fun setAudioDevice(name: String) {
        fire("audio-device") { lib.mpv_set_property_string(handle, "audio-device", name) }
    }

    override fun close() {
        live.remove(this)
        running.set(false)
        ready.cancel()
        commands.shutdown()
        runCatching { commands.awaitTermination(2, TimeUnit.SECONDS) }
        // The poller must finish using the native handle before it is destroyed.
        if (Thread.currentThread() != eventThread) eventThread.join()
        runCatching { levelThread.join(500) }
        runCatching { bandsDir?.deleteRecursively() }
        runCatching { lib.mpv_terminate_destroy(handle) }
    }
}
