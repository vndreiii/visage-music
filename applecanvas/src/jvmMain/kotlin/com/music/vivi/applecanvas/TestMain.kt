package com.music.vivi.applecanvas

import kotlinx.coroutines.runBlocking

object TestMain {
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
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
        }
    }
}
