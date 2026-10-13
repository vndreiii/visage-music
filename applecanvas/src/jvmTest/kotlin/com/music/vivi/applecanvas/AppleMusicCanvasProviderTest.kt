package com.music.vivi.applecanvas

import kotlinx.coroutines.runBlocking
import org.junit.Test

class AppleMusicCanvasProviderTest {
    @Test
    fun testCanvas() = runBlocking {
        val songs = listOf(
            Triple("Blinding Lights", "The Weeknd", "After Hours"),
            Triple("Anti-Hero", "Taylor Swift", "Midnights"),
            Triple("BIRDS OF A FEATHER", "Billie Eilish", "HIT ME HARD AND SOFT"),
            Triple("Espresso", "Sabrina Carpenter", "Short n' Sweet")
        )

        for ((song, artist, album) in songs) {
            println("Testing: \$song by \$artist...")
            val result = AppleMusicCanvasProvider.getBySongArtist(song, artist, album)
            println("Result for \$song: \$result")
            assert(result != null) { "Failed to fetch canvas for \$song" }
            assert(result?.animated != null || result?.animatedTall != null) { "Missing animation URL for \$song" }
        }
    }
}
