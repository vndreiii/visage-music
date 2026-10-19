package com.music.vivi.compose.ui.artist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArtistInfoTest {
    @Test
    fun parsesHelperJson() {
        val info = ArtistInfo.parse(
            """{"name":"Basshunter, Alien Cut","imageUrl":"https://x/a.jpg","bannerUrl":null,"bio":"Swedish DJ.","sources":["deezer","wikipedia"],"extra":1}""",
        )!!
        assertEquals("Basshunter, Alien Cut", info.name)
        assertEquals("https://x/a.jpg", info.imageUrl)
        assertNull(info.bannerUrl)
        assertEquals(listOf("deezer", "wikipedia"), info.sources)
    }

    @Test
    fun nullAndGarbageGiveNull() {
        assertNull(ArtistInfo.parse("null"))
        assertNull(ArtistInfo.parse(""))
        assertNull(ArtistInfo.parse("not json"))
        assertNull(ArtistInfo.parse("[]"))
    }

    @Test
    fun splitsEachCreditedArtist() {
        assertEquals(
            listOf("DJ llz", "XSKAVI1", "DJ SMG", "Dj v2be"),
            ArtistNames.split("DJ llz, XSKAVI1, DJ SMG, & Dj v2be"),
        )
        assertEquals(listOf("Drake", "Future"), ArtistNames.split("Drake feat. Future"))
        assertEquals(listOf("AC/DC"), ArtistNames.split("AC/DC"))
    }

    @Test
    fun creditMatching() {
        assertTrue(ArtistNames.credited("Basshunter, Alien Cut", "alien cut"))
        assertTrue(ArtistNames.credited("Sigur Rós", "sigur ros"))
        assertFalse(ArtistNames.credited("Basshunter", "Alien Cut"))
        assertFalse(ArtistNames.credited("Basshunter", ""))
    }

    @Test
    fun initialsAndHueAreStable() {
        assertEquals("DS", ArtistNames.initials("DJ SMG"))
        assertEquals("BA", ArtistNames.initials("Basshunter"))
        assertEquals(ArtistNames.hue("Zara Larsson"), ArtistNames.hue("  zara   LARSSON "))
        assertTrue(ArtistNames.hue("x") in 0f..360f)
    }
}
