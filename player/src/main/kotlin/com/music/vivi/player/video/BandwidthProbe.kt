package com.music.vivi.player.video

import java.net.HttpURLConnection
import java.net.URI

/** Measures download speed with one small ranged request, remembered briefly so track changes stay cheap. */
object BandwidthProbe {
    private const val PROBE_BYTES = 600_000
    private const val TTL_MS = 3 * 60_000L

    @Volatile private var cachedMbps: Double? = null
    @Volatile private var cachedAt = 0L

    /** Mbit/s measured against [url], or null when the probe failed (callers then pick a middle quality). */
    fun measureMbps(url: String, headers: Map<String, String>): Double? {
        val fresh = cachedMbps
        if (fresh != null && System.currentTimeMillis() - cachedAt < TTL_MS) return fresh
        return runCatching {
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.connectTimeout = 4_000
            conn.readTimeout = 4_000
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.setRequestProperty("Range", "bytes=0-${PROBE_BYTES - 1}")
            val started = System.nanoTime()
            var total = 0
            conn.inputStream.use { input ->
                val buf = ByteArray(32 * 1024)
                while (total < PROBE_BYTES) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                }
            }
            conn.disconnect()
            val seconds = (System.nanoTime() - started) / 1e9
            if (total < 100_000 || seconds <= 0.0) null else total * 8 / seconds / 1e6
        }.getOrNull()?.also {
            cachedMbps = it
            cachedAt = System.currentTimeMillis()
        }
    }
}
