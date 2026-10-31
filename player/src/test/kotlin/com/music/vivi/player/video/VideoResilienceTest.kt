package com.music.vivi.player.video

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VideoResilienceTest {
    private val mb = 1024L * 1024

    @Test fun firstChunkIsSmallThenFullSize() {
        val layout = ChunkLayout(100 * mb)
        assertEquals(0L, layout.start(0))
        assertEquals(mb - 1, layout.end(0))
        assertEquals(mb, layout.start(1))
        assertEquals(5 * mb - 1, layout.end(1))
        assertEquals(0, layout.indexOf(0))
        assertEquals(0, layout.indexOf(mb - 1))
        assertEquals(1, layout.indexOf(mb))
        assertEquals(1, layout.indexOf(5 * mb - 1))
        assertEquals(2, layout.indexOf(5 * mb))
    }

    @Test fun chunkCountCoversEveryByte() {
        assertEquals(1, ChunkLayout(1).count)
        assertEquals(1, ChunkLayout(mb).count)
        assertEquals(2, ChunkLayout(mb + 1).count)
        assertEquals(2, ChunkLayout(5 * mb).count)
        assertEquals(3, ChunkLayout(5 * mb + 1).count)
        // The last chunk ends exactly at the stream length.
        val layout = ChunkLayout(10 * mb + 7)
        assertEquals(10 * mb + 6, layout.end(layout.count - 1))
        for (pos in listOf(0L, mb - 1, mb, 9 * mb + 3, 10 * mb + 6)) {
            val idx = layout.indexOf(pos)
            assertTrue(pos in layout.start(idx)..layout.end(idx), "pos $pos in chunk $idx")
        }
    }

    @Test fun backoffDoublesAndCaps() {
        assertEquals(250L, RetryBackoff.delayMs(0))
        assertEquals(500L, RetryBackoff.delayMs(1))
        assertEquals(1_000L, RetryBackoff.delayMs(2))
        assertEquals(4_000L, RetryBackoff.delayMs(4))
        assertEquals(4_000L, RetryBackoff.delayMs(50))
    }

    @Test fun onlyTransientFailuresRetry() {
        assertTrue(RetryBackoff.isRetryable(0))
        assertTrue(RetryBackoff.isRetryable(429))
        assertTrue(RetryBackoff.isRetryable(503))
        assertFalse(RetryBackoff.isRetryable(404))
        assertFalse(RetryBackoff.isRetryable(403))
    }

    @Test fun expiryFromUrl() {
        val url = "https://rr1.googlevideo.com/videoplayback?id=abc&expire=1000&clen=4096&mime=video%2Fmp4"
        assertEquals(1000L, UrlExpiry.expiresAtSec(url))
        assertEquals(4096L, UrlExpiry.contentLength(url))
        assertTrue(UrlExpiry.needsRefresh(url, nowSec = 800))
        assertFalse(UrlExpiry.needsRefresh(url, nowSec = 100))
        assertFalse(UrlExpiry.needsRefresh("https://example.com/x?clen=1", nowSec = 10_000_000))
        assertNull(UrlExpiry.contentLength("https://example.com/x"))
    }

    @Test fun stepDownLadder() {
        assertEquals(VideoQuality.P720, QualityStepDown.lowerThan(1080))
        assertEquals(VideoQuality.P480, QualityStepDown.lowerThan(720))
        assertEquals(VideoQuality.P360, QualityStepDown.lowerThan(480))
        assertNull(QualityStepDown.lowerThan(360))
        // A 540p stream drops to the next fixed level below it.
        assertEquals(VideoQuality.P480, QualityStepDown.lowerThan(540))
    }

    @Test fun stepsDownOnlyOnRepeatedRecentStalls() {
        val window = QualityStepDown.WINDOW_MS
        assertFalse(QualityStepDown.shouldStepDown(listOf(1_000L), nowMs = 2_000L))
        assertTrue(QualityStepDown.shouldStepDown(listOf(1_000L, 5_000L), nowMs = 6_000L))
        // The first stall has aged out of the window, so only one counts.
        assertFalse(QualityStepDown.shouldStepDown(listOf(0L, window + 10_000L), nowMs = window + 10_000L))
    }
}
