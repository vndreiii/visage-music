package com.music.vivi.player.video

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VideoSeekTest {
    /** Frames every 200 ms until [until]; returns the time of the last frame. */
    private fun VideoStallDetector.playFrames(from: Long, until: Long): Long {
        var t = from
        while (t <= until) {
            observe(t, framesChanged = true, expected = true)
            t += 200
        }
        return t - 200
    }

    @Test fun steadyFramesNeverStall() {
        val d = VideoStallDetector()
        var stalled = false
        var t = 0L
        while (t <= 60_000L) {
            stalled = stalled || d.observe(t, framesChanged = true, expected = true)
            t += 200
        }
        assertFalse(stalled)
    }

    @Test fun freezeWithoutSeekIsAStallOnce() {
        val d = VideoStallDetector()
        val last = d.playFrames(0, 5_000)
        val reports = (last + 200..last + 6_000 step 200).count { d.observe(it, framesChanged = false, expected = true) }
        assertEquals(1, reports)
    }

    @Test fun bufferingAfterSeekIsNotAStall() {
        val d = VideoStallDetector()
        d.playFrames(0, 5_000)
        d.noteSeek(5_000)
        // The stream refetches from the new position: no frames for 9 s is still seek buffering.
        val reports = (5_200..14_000 step 200).count { d.observe(it.toLong(), framesChanged = false, expected = true) }
        assertEquals(0, reports)
    }

    @Test fun stallAfterSeekGraceIsCounted() {
        val d = VideoStallDetector()
        d.playFrames(0, 5_000)
        d.noteSeek(5_000)
        // Still no frames 12 s after the seek: genuine stall.
        val reports = (5_200..17_000 step 200).count { d.observe(it.toLong(), framesChanged = false, expected = true) }
        assertEquals(1, reports)
    }

    @Test fun pausedVideoDoesNotStall() {
        val d = VideoStallDetector()
        d.playFrames(0, 2_000)
        val reports = (2_200..20_000 step 200).count { d.observe(it.toLong(), framesChanged = false, expected = false) }
        assertEquals(0, reports)
        // Resuming starts the clock again: a short gap after resume is fine.
        assertFalse(d.observe(20_200, framesChanged = false, expected = true))
        assertFalse(d.observe(20_600, framesChanged = false, expected = true))
    }

    @Test fun newStallAfterFramesResume() {
        val d = VideoStallDetector()
        d.playFrames(0, 5_000)
        assertTrue((5_200..8_000 step 200).any { d.observe(it.toLong(), framesChanged = false, expected = true) })
        d.observe(8_200, framesChanged = true, expected = true)
        assertTrue((8_400..12_000 step 200).any { d.observe(it.toLong(), framesChanged = false, expected = true) })
    }

    @Test fun coalescerAppliesOnlyTheLastTargetAfterQuiet() {
        val c = VideoSeekCoalescer(quietMs = 250)
        c.observe(100.0, 0)
        // Five Shift+Right presses, 15 s each, 100 ms apart.
        c.observe(115.0, 100)
        c.observe(130.0, 200)
        c.observe(145.0, 300)
        c.observe(160.0, 400)
        c.observe(175.0, 500)
        assertTrue(c.pending)
        assertNull(c.takeSettled(600, 175.2))   // only 100 ms quiet
        assertNull(c.takeSettled(700, 175.4))   // 200 ms quiet
        assertEquals(175.6, c.takeSettled(760, 175.6))  // 260 ms quiet: one seek, to where the clock is now
        assertFalse(c.pending)
        assertNull(c.takeSettled(2_000, 175.8))  // nothing left to apply
    }

    @Test fun naturalPlaybackIsNotASeek() {
        val c = VideoSeekCoalescer()
        var clock = 0.0
        for (t in 0..100L) {
            c.observe(clock, t * 200L)
            clock += 0.2
        }
        assertFalse(c.pending)
        assertNull(c.takeSettled(100_000, clock))
    }

    @Test fun singleSeekIsSettledAfterQuiet() {
        val c = VideoSeekCoalescer(quietMs = 250)
        c.observe(10.0, 0)
        c.observe(40.0, 200)
        assertNull(c.takeSettled(300, 40.0))
        assertEquals(40.0, c.takeSettled(450, 40.0))
    }
}
