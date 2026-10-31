package com.music.lrclib

import com.music.lrclib.models.Track
import com.music.lrclib.models.bestMatchingForRelaxed
import com.music.lrclib.models.plausibleFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlausibleMatchTest {
    private fun track(id: Int, title: String, artist: String, duration: Double) =
        Track(id, title, artist, duration, null, "[00:01.00] x")

    private val britney = track(1, "Stronger", "Britney Spears", 203.0)
    private val fatRat = track(2, "Stronger", "TheFatRat, Slaydit & Anjulie", 206.0)

    @Test
    fun namesakeWithCloserDurationIsNotPicked() {
        val picked = listOf(britney, fatRat).plausibleFor("Stronger", "TheFatRat").bestMatchingForRelaxed(204)
        assertEquals(2, picked?.id)
    }

    @Test
    fun onlyNamesakeMeansNoLyricsRatherThanWrongOnes() {
        assertNull(listOf(britney).plausibleFor("Stronger", "TheFatRat").bestMatchingForRelaxed(204))
    }
}
