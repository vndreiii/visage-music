package com.music.vivi.compose.ui.player

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FullscreenLyricsLayoutTest {
    private val L = FullscreenLyricsLayout

    @Test fun `wide window keeps the stack natural and slides it left only as far as needed`() {
        val s = L.split(available = 1800.dp, natural = 1100.dp)
        assertEquals(1100.dp, s.stackWidth)
        assertEquals(L.PreferredLyricsWidth, s.lyricsWidth)
        assertEquals(1800.dp, s.lyricsLeft + s.lyricsWidth)
        assertEquals(s.stackLeft + 1100.dp + L.Gap, s.lyricsLeft)
        assertTrue(s.stackLeft < L.centeredLeft(1800.dp, 1100.dp))
    }

    @Test fun `plenty of room leaves the stack centred`() {
        val s = L.split(available = 3000.dp, natural = 600.dp)
        assertEquals(L.centeredLeft(3000.dp, 600.dp), s.stackLeft)
        assertEquals(L.PreferredLyricsWidth, s.lyricsWidth)
    }

    @Test fun `tight window shrinks the lyrics column before the stack`() {
        val s = L.split(available = 1200.dp, natural = 700.dp)
        assertEquals(700.dp, s.stackWidth)
        assertEquals(0.dp, s.stackLeft)
        assertEquals(1200.dp - 700.dp - L.Gap, s.lyricsWidth)
        assertTrue(s.lyricsWidth >= L.MinLyricsWidth)
    }

    @Test fun `narrow window shrinks the stack to keep a usable lyrics column`() {
        val s = L.split(available = 1000.dp, natural = 900.dp)
        assertEquals(0.dp, s.stackLeft)
        assertEquals(1000.dp - L.Gap - L.MinLyricsWidth, s.stackWidth)
        assertEquals(L.MinLyricsWidth, s.lyricsWidth)
    }

    @Test fun `very narrow window never overlaps or goes negative`() {
        val s = L.split(available = 500.dp, natural = 900.dp)
        assertEquals(L.MinStackWidth, s.stackWidth)
        assertTrue(s.lyricsLeft >= s.stackLeft + s.stackWidth)
        assertTrue(s.lyricsWidth >= 0.dp)
    }

    @Test fun `fit leaves a side by side stack alone when it already fits`() {
        assertEquals(StackFit(600.dp, 520.dp), L.fit(600.dp, 520.dp, 48.dp, false, 1200.dp))
    }

    @Test fun `fit takes width from the controls first then the cover`() {
        val a = L.fit(600.dp, 520.dp, 48.dp, false, 1100.dp)
        assertEquals(StackFit(600.dp, 452.dp), a)
        val b = L.fit(600.dp, 520.dp, 48.dp, false, 900.dp)
        assertEquals(StackFit(532.dp, 320.dp), b)
    }

    @Test fun `fit clamps a vertical stack to the target`() {
        assertEquals(StackFit(400.dp, 400.dp), L.fit(520.dp, 520.dp, 32.dp, true, 400.dp))
        assertEquals(StackFit(300.dp, 340.dp), L.fit(300.dp, 520.dp, 32.dp, true, 340.dp))
    }
}
