package com.music.vivi.compose.ui.charts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChartsLogicTest {
    @Test fun parsesMoves() {
        assertEquals(ChartMove.Up(3), parseChartMove("+3"))
        assertEquals(ChartMove.Down(2), parseChartMove("▼2"))
        assertEquals(ChartMove.New, parseChartMove("NEW"))
        assertEquals(ChartMove.Same, parseChartMove("="))
        assertNull(parseChartMove(null))
        assertNull(parseChartMove("  "))
    }

    @Test fun barAndDuration() {
        assertEquals(1f, rankBar(1, 20))
        assertTrue(rankBar(20, 20) < rankBar(2, 20))
        assertEquals("3:05", formatDuration(185))
        assertEquals("", formatDuration(null))
    }
}
