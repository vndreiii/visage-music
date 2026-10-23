package com.music.vivi.compose.ui.player.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals

class LyricsDelayTest {
    @Test fun formatsSignedSeconds() {
        assertEquals("+0.3s", formatLyricsDelay(300))
        assertEquals("-1.2s", formatLyricsDelay(-1200))
        assertEquals("0.0s", formatLyricsDelay(0))
        assertEquals("0.0s", formatLyricsDelay(40))
    }

    @Test fun stepsAndClamps() {
        assertEquals(100, stepLyricsDelay(0, 1))
        assertEquals(-200, stepLyricsDelay(-100, -1))
        assertEquals(LYRICS_DELAY_MAX_MS, stepLyricsDelay(LYRICS_DELAY_MAX_MS, 1))
    }
}
