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
