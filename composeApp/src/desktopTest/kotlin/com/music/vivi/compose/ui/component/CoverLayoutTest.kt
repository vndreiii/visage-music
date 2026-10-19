package com.music.vivi.compose.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoverLayoutTest {
    private val aspects = listOf(0.5f, 0.8f, 1f, 1.74f, 2.5f, 3.5f)

    @Test
    fun motifsNeverOverlapAndStayInsideTheirCell() {
        for (aspect in aspects) for (i in 0 until 400) for (text in listOf(false, true)) {
            val cells = generateCoverLayout("seed-$i", aspect, text)
            val region = CRect(0f, 0f, aspect, 1f)
            assertTrue(cells.size in 3..5, "cell count ${cells.size}")
            for (c in cells) {
                assertTrue(region.contains(c.rect), "cell outside region seed-$i")
                assertTrue(c.rect.contains(c.ink), "ink outside cell seed-$i")
                assertTrue(c.ink.w > 0.03f && c.ink.h > 0.03f, "degenerate ink seed-$i")
            }
            for (a in cells.indices) for (b in a + 1 until cells.size) {
                assertFalse(cells[a].rect.intersects(cells[b].rect), "cells overlap seed-$i")
                assertFalse(cells[a].ink.intersects(cells[b].ink), "ink overlaps seed-$i")
            }
            assertEquals(text, cells.any { it.motif == Motif.TEXT })
        }
    }

    @Test
    fun deterministic() {
        assertEquals(generateCoverLayout("Rock", 1.3f).map { it.rect to it.motif }, generateCoverLayout("rock", 1.3f).map { it.rect to it.motif })
    }
}
