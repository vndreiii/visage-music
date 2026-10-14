package com.music.vivi.compose.ui.player.background

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CanvasFrameTimelineTest {
    @Test
    fun pingPongDoesNotRepeatTheTurnaroundFrames() {
        val timeline = CanvasFrameTimeline(4, 1.0 / 24, true)
        assertEquals(listOf(0, 1, 2, 3, 2, 1, 0, 1), (0..7).map { timeline.sample(it.toDouble()).first })
        assertEquals(3, timeline.sample(2.5).second)
        assertEquals(2, timeline.sample(3.5).second)
        assertEquals(0.5f, timeline.sample(3.5).blend)
    }

    @Test
    fun forwardLoopBlendsTheCachedTailIntoTheCachedHead() {
        val timeline = CanvasFrameTimeline(24, 1.0 / 24, false)
        val nearEnd = timeline.sample(timeline.period - 0.01)
        val start = timeline.sample(0.0)
        assertTrue(nearEnd.blend > 0.99f)
        assertEquals(nearEnd.second + 1, start.first)
        assertTrue(start.second == start.first + 1)
    }
}
