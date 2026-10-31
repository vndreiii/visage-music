package com.music.vivi.player.video

/**
 * Byte layout of a stream split into chunks: a small first chunk so playback can start quickly, then
 * [CHUNK_SIZE]-sized chunks. Chunk `idx` covers `start(idx)..end(idx)` (inclusive end).
 */
class ChunkLayout(val length: Long, private val first: Long = FIRST_CHUNK, private val chunk: Long = CHUNK_SIZE) {
    val count: Int = if (length <= first) 1 else (1 + (length - first + chunk - 1) / chunk).toInt()

    fun start(idx: Int): Long = if (idx <= 0) 0L else first + (idx - 1).toLong() * chunk

    fun end(idx: Int): Long = (if (idx + 1 >= count) length else start(idx + 1)) - 1

    fun indexOf(pos: Long): Int = if (pos < first) 0 else (1 + (pos - first) / chunk).toInt()

    companion object {
        const val FIRST_CHUNK = 1L * 1024 * 1024
        const val CHUNK_SIZE = 4L * 1024 * 1024
    }
}

/** Exponential backoff for failed range requests; only failures that may succeed later are retried. */
object RetryBackoff {
    const val MAX_ATTEMPTS = 4

    /** Delay before retry number [retry] (0-based): 250 ms, 500 ms, 1 s, ... capped at 4 s. */
    fun delayMs(retry: Int): Long = minOf(250L shl retry.coerceIn(0, 6), 4_000L)

    /** [status] is the HTTP status, or 0 for a transport error or a short body. */
    fun isRetryable(status: Int): Boolean = status == 0 || status == 408 || status == 429 || status >= 500
}

/** googlevideo URLs carry `expire=` (unix seconds) and `clen=` (byte length) query parameters. */
object UrlExpiry {
    fun expiresAtSec(url: String): Long? = queryLong(url, "expire")

    fun contentLength(url: String): Long? = queryLong(url, "clen")

    /** True when the URL expires within [marginSec] of [nowSec]. URLs without `expire=` never need a refresh. */
    fun needsRefresh(url: String, nowSec: Long, marginSec: Long = 300): Boolean =
        expiresAtSec(url)?.let { it - nowSec < marginSec } ?: false

    private fun queryLong(url: String, name: String): Long? =
        Regex("[?&]$name=(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull()
}

/**
 * Coarse quality step-down under sustained stalling, the nearest thing to FreeTube's adaptive bitrate: a
 * stream that stalls twice within [WINDOW_MS] drops one fixed level (1080 -> 720 -> 480 -> 360).
 */
object QualityStepDown {
    const val WINDOW_MS = 30_000L
    const val MIN_STALLS = 2

    /** The next lower fixed quality than [height], or null when already at the bottom. */
    fun lowerThan(height: Int): VideoQuality? =
        VideoQuality.entries.filter { it != VideoQuality.AUTO && it.height < height }.maxByOrNull { it.height }

    /** [stallsAtMs] are the times (ms) of stalls that began; only those inside the window count. */
    fun shouldStepDown(stallsAtMs: List<Long>, nowMs: Long): Boolean =
        stallsAtMs.count { nowMs - it in 0..WINDOW_MS } >= MIN_STALLS
}

/**
 * Stall detection for the music video: frames stopping while the video should be playing. Buffering that follows a
 * seek (the stream is fetched again from the new position) is not a stall, so nothing is counted until
 * [seekGraceMs] has passed since the last seek. A stall is reported once, when it begins.
 */
class VideoStallDetector(
    private val thresholdMs: Long = 1_000L,
    private val seekGraceMs: Long = 10_000L,
) {
    private var lastFrameMs = 0L
    private var lastSeekMs: Long? = null
    private var stallOpen = false

    /** A seek was issued at [nowMs]: the clock restarts and the current stall, if any, is over. */
    fun noteSeek(nowMs: Long) {
        lastSeekMs = nowMs
        lastFrameMs = nowMs
        stallOpen = false
    }

    /**
     * One sample per sync tick. [framesChanged]: a new frame arrived since the previous sample. [expected]: the video
     * should be showing motion now (playing, and a frame is on screen). Returns true when a stall begins on this sample.
     */
    fun observe(nowMs: Long, framesChanged: Boolean, expected: Boolean): Boolean {
        if (framesChanged || !expected) {
            lastFrameMs = nowMs
            stallOpen = false
            return false
        }
        if (stallOpen) return false
        val seeking = lastSeekMs?.let { nowMs - it < seekGraceMs } ?: false
        if (seeking || nowMs - lastFrameMs <= thresholdMs) return false
        stallOpen = true
        return true
    }
}

/**
 * Debounces the music video's seeks. Repeated skips move the audio clock several times a second; the video should
 * jump once, to where the clock is after the last jump, once the clock has been still for [quietMs]. A jump is a
 * change of more than [jumpSec] between two samples, which normal playback never makes.
 */
class VideoSeekCoalescer(
    private val quietMs: Long = 250L,
    private val jumpSec: Double = 1.5,
) {
    private var lastAudioSec: Double? = null
    private var lastJumpMs = 0L
    private var seekPending = false

    /** True while a seek is waiting for the clock to settle. */
    val pending: Boolean get() = seekPending

    /** One sample of the audio clock at [nowMs]. */
    fun observe(audioSec: Double, nowMs: Long) {
        val last = lastAudioSec
        lastAudioSec = audioSec
        if (last != null && kotlin.math.abs(audioSec - last) > jumpSec) {
            seekPending = true
            lastJumpMs = nowMs
        }
    }

    /**
     * The clock position [audioSec] to seek the video to, once a pending seek has been quiet for [quietMs]; null
     * otherwise. Taking the target clears the pending seek.
     */
    fun takeSettled(nowMs: Long, audioSec: Double): Double? {
        if (!seekPending || nowMs - lastJumpMs < quietMs) return null
        seekPending = false
        return audioSec
    }
}
