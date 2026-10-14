package com.music.vivi.compose.ui.player.background

import kotlin.math.floor

/** A cached clip's playback clock, without seeks or repeated turnaround frames. */
internal class CanvasFrameTimeline(val count: Int, val secondsPerFrame: Double, val pingPong: Boolean) {
    private val fadeFrames = if (pingPong) 0 else minOf(count / 4, (0.35 / secondsPerFrame).toInt())
    val period: Int = if (pingPong) 2 * (count - 1) else count - fadeFrames
    data class Sample(val first: Int, val second: Int, val blend: Float)

    fun sample(position: Double): Sample {
        val phase = ((position % period) + period) % period
        val base = floor(phase).toInt()
        val fraction = (phase - base).toFloat()
        if (pingPong) {
            fun index(step: Int): Int {
                val folded = step % period
                return if (folded < count) folded else period - folded
            }
            return Sample(index(base), index(base + 1), fraction)
        }
        if (fadeFrames == 0) return Sample(base, (base + 1) % count, fraction)
        val fadeStart = count - 2 * fadeFrames
        if (phase >= fadeStart) {
            val t = ((phase - fadeStart) / fadeFrames).toFloat()
            return Sample(base + fadeFrames, base - fadeStart, t * t * (3f - 2f * t))
        }
        return Sample(base + fadeFrames, base + fadeFrames + 1, fraction)
    }
}

internal data class CachedCanvasFrames(val frames: List<CanvasFrame>, val interval: Double, val bytes: Long)

/** Decoded pixels shared by album/song identity and asset URL; bounded to 512 MiB. */
internal object CanvasFrameCache {
    private const val MAX_BYTES = 512L * 1024 * 1024
    private val entries = LinkedHashMap<String, CachedCanvasFrames>(16, 0.75f, true)
    private var bytes = 0L
    @Synchronized fun get(key: String): CachedCanvasFrames? = entries[key]
    @Synchronized fun put(key: String, value: CachedCanvasFrames) {
        bytes -= entries.remove(key)?.bytes ?: 0L
        entries[key] = value
        bytes += value.bytes
        while (bytes > MAX_BYTES && entries.isNotEmpty()) {
            val first = entries.entries.iterator().next()
            bytes -= first.value.bytes
            entries.remove(first.key)
        }
    }
}
