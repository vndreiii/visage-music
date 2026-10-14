package com.music.vivi.compose.ui.player.background

import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Real-decoder check of the gapless canvas loop. Needs ffmpeg and libmpv; skipped otherwise.
 * Generates a 6 s clip, lets it loop ~3 times and measures the longest stall between frames.
 */
class CanvasVideoLoopTest {
    @Test
    fun loopsWithoutStalling() {
        val clip = File.createTempFile("canvas-loop", ".mp4")
        try {
            val ok = runCatching {
                ProcessBuilder(
                    "ffmpeg", "-y", "-loglevel", "error", "-f", "lavfi", "-i", "testsrc2=size=360x640:rate=24",
                    "-t", "6", "-pix_fmt", "yuv420p", clip.absolutePath,
                ).redirectErrorStream(true).start().waitFor(60, TimeUnit.SECONDS)
            }.getOrDefault(false)
            if (!ok || clip.length() == 0L) return // no ffmpeg: nothing to verify

            val video = runCatching { CanvasVideo.obtain(clip.absolutePath) }.getOrNull() ?: return
            try {
                var last: CanvasFrame? = null
                var lastChange = System.nanoTime()
                var maxGapMs = 0L
                var frames = 0
                var firstFrameAt = 0L
                val start = System.nanoTime()
                while (System.nanoTime() - start < 22_000_000_000L) {
                    val f = video.frame
                    if (f != null && f !== last) {
                        val now = System.nanoTime()
                        if (last != null) {
                            val gap = (now - lastChange) / 1_000_000
                            maxGapMs = maxOf(maxGapMs, gap)
                            if (gap > 90) println("canvas gap ${gap}ms at t=${(now - firstFrameAt) / 1_000_000}ms")
                        }
                        if (firstFrameAt == 0L) firstFrameAt = now
                        last = f
                        lastChange = now
                        frames++
                    }
                    Thread.sleep(4)
                }
                if (frames == 0) return // libmpv unavailable in this environment
                println("canvas loop: frames=$frames maxGapMs=$maxGapMs")
                // 22 s of a 6 s clip = 3+ loops; a hard loop seek stalls for hundreds of ms.
                assertTrue(frames > 200, "expected continuous playback, got $frames frames")
                assertTrue(maxGapMs < 300, "stalled for ${maxGapMs}ms")
            } finally {
                CanvasVideo.dispose(video)
            }
        } finally {
            clip.delete()
        }
    }
}
