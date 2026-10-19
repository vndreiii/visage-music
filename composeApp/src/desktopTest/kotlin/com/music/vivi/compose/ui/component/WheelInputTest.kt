package com.music.vivi.compose.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WheelInputTest {
    private val ms = 1_000_000L

    @Test fun isolatedIntegerClickIsNotch() {
        val c = WheelClassifier()
        val s = assertNotNull(c.classify(0f, 1f, 0))
        assertEquals(WheelKind.Notch, s.kind)
        assertEquals(WheelAxis.Vertical, s.axis)
        assertTrue(s.streamStart)
    }

    @Test fun fractionalIsPrecise() {
        val c = WheelClassifier()
        assertEquals(WheelKind.Precise, c.classify(0f, 0.2f, 0)!!.kind)
    }

    @Test fun rapidIntegerClicksBecomeStream() {
        val c = WheelClassifier()
        val kinds = (0 until 5).map { c.classify(0f, 1f, it * 8 * ms)!!.kind }
        assertEquals(WheelKind.Notch, kinds[0])
        assertEquals(WheelKind.Stream, kinds[3])
    }

    @Test fun slowMouseClicksStayNotches() {
        val c = WheelClassifier()
        val kinds = (0 until 5).map { c.classify(0f, 3f, it * 60 * ms)!!.kind }
        assertTrue(kinds.all { it == WheelKind.Notch })
    }

    @Test fun touchpadStreamKeepsAxisDespiteJitter() {
        val c = WheelClassifier()
        c.classify(-0.4f, 0.05f, 0)
        c.classify(-0.4f, 0.05f, 8 * ms)
        c.classify(-0.3f, 0.1f, 16 * ms)
        // A burst of vertical jitter must not flip the locked horizontal axis.
        val s = c.classify(-0.05f, 0.6f, 24 * ms)!!
        assertEquals(WheelAxis.Horizontal, s.axis)
    }

    @Test fun axisUnlocksAfterIdleGap() {
        val c = WheelClassifier()
        repeat(4) { c.classify(-0.4f, 0f, it * 8 * ms) }
        val s = c.classify(0f, 0.5f, 400 * ms)!!
        assertEquals(WheelAxis.Vertical, s.axis)
        assertTrue(s.streamStart)
    }

    @Test fun zeroDeltaIgnored() {
        assertEquals(null, WheelClassifier().classify(0f, 0f, 0))
    }

    @Test fun backSwipeProgressAndCommit() {
        assertEquals(0f, BackSwipe.progress(-5f, 1000f))
        assertEquals(1f, BackSwipe.progress(5000f, 1000f))
        assertEquals(0.5f, BackSwipe.progress(375f, 1000f), 1e-4f)
        assertFalse(BackSwipe.shouldCommit(0.1f, 0.2f))
        assertTrue(BackSwipe.shouldCommit(0.3f, 0f))
        assertTrue(BackSwipe.shouldCommit(0.1f, 2f))
        assertFalse(BackSwipe.shouldCommit(0.02f, 5f))
    }
}
