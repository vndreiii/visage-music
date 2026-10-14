package com.music.vivi.compose.ui.player

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdwaitaClampTest {
    @Test fun fitsNarrowPanesAndApproachesTheLimitMonotonically() {
        for (maximum in listOf(400, 500, 600)) {
            var previous = 0f
            for (available in 0..2000) {
                val width = adwaitaClampWidth(available.dp, maximum.dp).value
                assertTrue(width <= available + 0.001f)
                assertTrue(width <= maximum + 0.001f)
                assertTrue(width >= previous)
                if (available <= maximum * 0.75f) assertEquals(available.toFloat(), width)
                previous = width
            }
            assertEquals(maximum.dp, adwaitaClampWidth(2000.dp, maximum.dp))
        }
    }
}
