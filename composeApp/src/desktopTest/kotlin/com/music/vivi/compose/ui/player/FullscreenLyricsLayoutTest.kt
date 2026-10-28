package com.music.vivi.compose.ui.player

import androidx.compose.ui.unit.dp
import com.music.vivi.compose.prefs.FullscreenCoverSide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FullscreenLyricsLayoutTest {
    private val L = FullscreenLayoutAlias
    private val left = FullscreenCoverSide.LEFT
    private val right = FullscreenCoverSide.RIGHT

    @Test fun `cover is as tall as the area when the width allows`() {
        val s = L.split(1800.dp, 900.dp, left)
        assertEquals(900.dp, s.coverSize)
        assertEquals(0.dp, s.coverY)
        assertEquals(900.dp, s.columnHeight)
        assertFalse(s.stacked)
    }

    @Test fun `cover never exceeds 60 percent of the width`() {
        val s = L.split(1300.dp, 900.dp, left)
        assertEquals(780f, s.coverSize.value, 0.01f)
        assertEquals(60f, s.coverY.value, 0.01f)
    }

    @Test fun `column takes the rest capped and group is centred`() {
        val s = L.split(1800.dp, 900.dp, left)
        assertEquals(L.MaxColumn, s.columnWidth)
        assertEquals(s.coverX + s.coverSize + L.Gap, s.columnX)
        val start = s.coverX
        assertEquals(1800.dp - (s.columnX + s.columnWidth), start)
    }

    @Test fun `flipping the side mirrors cover and column`() {
        val a = L.split(1800.dp, 900.dp, left)
        val b = L.split(1800.dp, 900.dp, right)
        assertEquals(a.coverSize, b.coverSize)
        assertEquals(a.columnWidth, b.columnWidth)
        assertEquals(b.columnX + b.columnWidth + L.Gap, b.coverX)
        assertEquals(a.coverX, 1800.dp - (b.coverX + b.coverSize))
    }

    @Test fun `cover shrinks to leave a usable column`() {
        val s = L.split(800.dp, 900.dp, left)
        assertEquals(800.dp - L.Gap - L.MinColumn, s.coverSize)
        assertEquals(L.MinColumn, s.columnWidth)
        assertTrue(s.columnX >= s.coverX + s.coverSize)
        assertFalse(s.stacked)
    }

    @Test fun `very narrow window stacks cover above the column`() {
        val s = L.split(500.dp, 900.dp, left)
        assertTrue(s.stacked)
        assertTrue(s.coverSize >= L.MinStackedCover && s.coverSize <= 500.dp)
        assertEquals(s.coverSize + L.StackedGap, s.columnY)
        assertEquals(900.dp - s.columnY, s.columnHeight)
        assertTrue(s.columnWidth <= 500.dp)
    }

    @Test fun `hidden cover gives the column the page`() {
        val s = L.split(1800.dp, 900.dp, left, coverShown = false)
        assertEquals(0.dp, s.coverSize)
        assertEquals(L.MaxColumn, s.columnWidth)
        assertEquals((1800.dp - L.MaxColumn) / 2f, s.columnX)
    }

    @Test fun `scale shrinks the cover but not below the floor`() {
        assertEquals(450.dp, L.split(1800.dp, 900.dp, left, scale = 0.5f).coverSize)
        assertEquals(L.MinCover, L.split(1800.dp, 300.dp, left, scale = 0.5f).coverSize)
    }
}

private val FullscreenLayoutAlias = FullscreenLyricsLayout
