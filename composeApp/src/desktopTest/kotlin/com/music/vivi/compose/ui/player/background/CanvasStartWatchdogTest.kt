package com.music.vivi.compose.ui.player.background

import com.music.vivi.compose.ui.player.canvas.CanvasSource
import com.music.vivi.compose.ui.player.canvas.nativeSource
import kotlin.test.Test
import kotlin.test.assertEquals

class CanvasStartWatchdogTest {
    @Test
    fun restartsWithGrowingWaitsThenGivesUp() {
        val w = CanvasStartWatchdog(listOf(100, 200))
        w.started(0)
        assertEquals(CanvasStartWatchdog.Action.NONE, w.check(false, 50))
        assertEquals(CanvasStartWatchdog.Action.RESTART, w.check(false, 100))
        assertEquals(CanvasStartWatchdog.Action.NONE, w.check(false, 250))
        assertEquals(CanvasStartWatchdog.Action.RESTART, w.check(false, 300))
        assertEquals(CanvasStartWatchdog.Action.NONE, w.check(false, 400))
        assertEquals(CanvasStartWatchdog.Action.GIVE_UP, w.check(false, 600))
    }

    @Test
    fun aFrameResetsTheWatchdog() {
        val w = CanvasStartWatchdog(listOf(100))
        w.started(0)
        assertEquals(CanvasStartWatchdog.Action.NONE, w.check(true, 1_000))
    }

    @Test
    fun pingPongTimelineFoldsAndRepeatsWithoutDuplicatingEnds() {
        val t = CanvasFrameTimeline(3, 0.04, pingPong = true)
        assertEquals(4, t.period)
        assertEquals(listOf(0, 1, 2, 1, 0, 1), (0..5).map { t.sample(it.toDouble()).first })
    }

    @Test
    fun nativeSourceNames() {
        assertEquals("auto", nativeSource(CanvasSource.AUTO))
        assertEquals("apple", nativeSource(CanvasSource.APPLE_MUSIC))
    }
}
