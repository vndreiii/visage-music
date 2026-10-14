package com.music.vivi.compose.ui.player.background

import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import com.music.vivi.compose.ui.player.canvas.CanvasResolver
import com.music.vivi.canvas.CanvasArtwork
import kotlinx.coroutines.runBlocking
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress

/**
 * Real-decoder check of the gapless canvas loop. Needs ffmpeg and libmpv; skipped otherwise.
 * Generates a 6 s clip, lets it loop ~3 times and measures the longest stall between frames.
 */
class CanvasVideoLoopTest {
    @Test
    fun prewarmingCoverAndPortraitMakesBothImmediatelyDrawable() {
        val square = File.createTempFile("canvas-square-warm", ".mp4")
        val tall = File.createTempFile("canvas-tall-warm", ".mp4")
        try {
            for ((file, size) in listOf(square to "180x180", tall to "120x240")) {
                val encoder = ProcessBuilder(
                    "ffmpeg", "-y", "-loglevel", "error", "-f", "lavfi", "-i", "testsrc2=size=$size:rate=24",
                    "-t", "1", "-pix_fmt", "yuv420p", file.absolutePath,
                ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
                assertTrue(encoder.waitFor(30, TimeUnit.SECONDS) && encoder.exitValue() == 0)
            }
            val artwork = CanvasArtwork(canvasKey = "apple:album:warm-test", animated = square.absolutePath, animatedTall = tall.absolutePath)
            val urls = listOf(requireNotNull(CanvasResolver.squareUrl(artwork)), requireNotNull(CanvasResolver.tallUrl(artwork)))
            runBlocking { for (url in urls) CanvasVideo.warm(url, useGpu = false, pingPong = false) }
            for (url in urls) {
                val video = CanvasVideo.obtain(url)
                try {
                    assertTrue(video.cachedFrameCount > 0)
                    assertTrue(video.frame != null, "Both cover and lyrics variants must be drawable immediately")
                } finally { CanvasVideo.dispose(video) }
            }
        } finally { square.delete(); tall.delete() }
    }

    @Test
    fun pingPongReversesRealFramesAndReusesTheDecodedCache() {
        val clip = File.createTempFile("canvas-pingpong", ".mp4")
        try {
            val encoder = ProcessBuilder(
                "ffmpeg", "-y", "-loglevel", "error", "-f", "lavfi", "-i", "testsrc2=size=180x320:rate=24",
                "-t", "1", "-pix_fmt", "yuv420p", clip.absolutePath,
            ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
            assertTrue(encoder.waitFor(30, TimeUnit.SECONDS) && encoder.exitValue() == 0)
            val video = CanvasVideo.obtain(clip.absolutePath, pingPong = true)
            var frameCount = 0
            try {
                val readyDeadline = System.nanoTime() + 5_000_000_000L
                while (video.cachedFrameCount == 0 && System.nanoTime() < readyDeadline) Thread.sleep(5)
                frameCount = video.cachedFrameCount
                assertTrue(frameCount > 10)
                val frames = requireNotNull(CanvasFrameCache.get(CanvasResolver.playbackCacheKey(clip.absolutePath))).frames
                val fingerprints = frames.take(8).map { frame ->
                    val pixels = IntArray(frame.image.width * frame.image.height)
                    frame.image.readPixels(pixels)
                    pixels.contentHashCode()
                }
                assertTrue(fingerprints.distinct().size > 3, "Cached frames must own pixels rather than share the decoder's three buffers")
                val observed = mutableListOf<Long>()
                val deadline = System.nanoTime() + 3_000_000_000L
                while (System.nanoTime() < deadline) {
                    video.frame?.generation?.let { if (observed.lastOrNull() != it) observed += it }
                    Thread.sleep(5)
                }
                assertTrue(observed.zipWithNext().any { (a, b) -> b < a }, "Expected backward playback")
                assertTrue(observed.zipWithNext().any { (a, b) -> b > a }, "Expected forward playback")
            } finally { CanvasVideo.dispose(video) }
            val replay = CanvasVideo.obtain(clip.absolutePath, pingPong = false)
            try {
                assertEquals(frameCount, replay.cachedFrameCount, "Replay should use the already decoded clip")
                assertTrue(replay.frame != null, "A cached cover must have a frame immediately, before a timer tick")
            }
            finally { CanvasVideo.dispose(replay) }
        } finally { clip.delete() }
    }

    @Test
    fun chunkedTransportStreamLoopsAtEndOfFile() {
        val clip = File.createTempFile("canvas-stream", ".ts")
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        try {
            val encoder = ProcessBuilder(
                "ffmpeg", "-y", "-loglevel", "error", "-f", "lavfi", "-i", "testsrc2=size=180x320:rate=24",
                "-t", "1", "-c:v", "libx264", "-f", "mpegts", clip.absolutePath,
            ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
            assertTrue(encoder.waitFor(30, TimeUnit.SECONDS) && encoder.exitValue() == 0)
            server.createContext("/canvas.ts") { exchange ->
                exchange.responseHeaders.add("Content-Type", "video/mp2t")
                exchange.sendResponseHeaders(200, 0) // Chunked: no size/duration metadata.
                exchange.responseBody.use { output -> clip.inputStream().use { it.copyTo(output) } }
            }
            server.start()
            val video = CanvasVideo.obtain("http://127.0.0.1:${server.address.port}/canvas.ts")
            try {
                val deadline = System.nanoTime() + 8_000_000_000L
                while (video.completedLoops < 3 && System.nanoTime() < deadline) Thread.sleep(10)
                assertTrue(video.completedLoops >= 3, "A stream reaching EOF must continue looping")
            } finally { CanvasVideo.dispose(video) }
        } finally { server.stop(0); clip.delete() }
    }

    @Test
    fun shortCanvasLoopsAndPauseFreezesTheDissolve() {
        val clip = File.createTempFile("canvas-short", ".mp4")
        try {
            val encoder = ProcessBuilder(
                "ffmpeg", "-y", "-loglevel", "error", "-f", "lavfi", "-i", "testsrc2=size=180x320:rate=24",
                "-t", "1", "-pix_fmt", "yuv420p", clip.absolutePath,
            ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
            assertTrue(encoder.waitFor(30, TimeUnit.SECONDS) && encoder.exitValue() == 0)
            val video = CanvasVideo.obtain(clip.absolutePath)
            try {
                val deadline = System.nanoTime() + 5_000_000_000L
                while (video.incoming == null && System.nanoTime() < deadline) Thread.sleep(5)
                assertTrue(video.incoming != null, "Short clips must enter a dissolve")
                video.votePaused(true, +1)
                val loops = video.completedLoops
                val blend = video.blend
                Thread.sleep(4000)
                assertEquals(loops, video.completedLoops, "A pause must not hand off decoders")
                assertEquals(blend, video.blend, "The dissolve must pause with playback")
                video.votePaused(true, -1)
                val resumedDeadline = System.nanoTime() + 5_000_000_000L
                while (video.completedLoops < loops + 3 && System.nanoTime() < resumedDeadline) Thread.sleep(10)
                assertTrue(video.completedLoops >= loops + 3, "Expected repeated handoffs after resuming")
            } finally { CanvasVideo.dispose(video) }
        } finally { clip.delete() }
    }

    @Test
    fun tenBitHevcCanvasDecodesWithoutHardwareProbing() {
        val clip = File.createTempFile("canvas-hevc10", ".mp4")
        try {
            val encoder = ProcessBuilder(
                "ffmpeg", "-y", "-loglevel", "error", "-f", "lavfi", "-i", "testsrc2=size=180x320:rate=24",
                "-t", "2", "-c:v", "libx265", "-pix_fmt", "yuv420p10le", "-threads", "2",
                "-x265-params", "pools=1:frame-threads=1:log-level=error", clip.absolutePath,
            ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
            assertTrue(encoder.waitFor(30, TimeUnit.SECONDS), "HEVC fixture encoding timed out")
            assertTrue(encoder.exitValue() == 0 && clip.length() > 0, "Could not encode the 10-bit HEVC fixture")
            val video = CanvasVideo.obtain(clip.absolutePath)
            try {
                var last: CanvasFrame? = null
                var frames = 0
                val start = System.nanoTime()
                while (System.nanoTime() - start < 6_000_000_000L) {
                    val frame = video.frame
                    if (frame != null && frame !== last) { last = frame; frames++ }
                    Thread.sleep(5)
                }
                println("10-bit HEVC canvas: frames=$frames")
                assertTrue(frames > 60, "Expected HEVC canvas playback across loops, got $frames frames")
                assertTrue(video.completedLoops >= 2, "HEVC must complete multiple decoder handoffs")
            } finally {
                CanvasVideo.dispose(video)
            }
        } finally {
            clip.delete()
        }
    }

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
                assertTrue(video.completedLoops >= 3, "Expected at least three complete loop transitions")
            } finally {
                CanvasVideo.dispose(video)
            }
        } finally {
            clip.delete()
        }
    }
}
