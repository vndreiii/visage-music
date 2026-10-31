package com.music.vivi.compose.ui.player.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LyricsRomanizerTest {
    private fun r(s: String) = LyricsRomanizer.romanize(s)

    @Test fun japanese() {
        assertEquals("sakura", r("さくら"))
        assertEquals("kyou ha ii tenki", r("きょう は いい てんき"))
        assertEquals("gakkou", r("がっこう"))
        assertEquals("shashin", r("しゃしん"))
        assertEquals("konnichiwa", r("こんにちわ"))
        assertEquals("raamen", r("ラーメン"))
        assertEquals("paati", r("パーティ"))
    }

    @Test fun korean() {
        assertEquals("saranghae", r("사랑해"))
        assertEquals("isseo", r("있어"))
        assertEquals("hangugeo", r("한국어"))
    }

    @Test fun cyrillicAndLatin() {
        assertEquals("privet", r("привет"))
        assertNull(r("hello world"))
        assertNull(r("漢字"))
    }
}
