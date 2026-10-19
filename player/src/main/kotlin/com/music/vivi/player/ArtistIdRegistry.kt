package com.music.vivi.player

import java.util.concurrent.ConcurrentHashMap

/**
 * Remembers YouTube Music channel ids seen next to artist names (a track's `artists` carry ids, but
 * [QueueTrack]/[NowPlaying] only carry the joined display string). The UI uses this to send a click
 * on "Zara Larsson" to the real YouTube artist page when the id is known, and to the helper-backed
 * artist page otherwise.
 */
object ArtistIdRegistry {
    private val ids = ConcurrentHashMap<String, String>()

    private fun key(name: String) = name.trim().lowercase()

    fun remember(name: String?, id: String?) {
        if (name.isNullOrBlank() || id.isNullOrBlank()) return
        ids[key(name)] = id
    }

    fun remember(artists: List<com.music.innertube.models.Artist>) {
        artists.forEach { remember(it.name, it.id) }
    }

    fun idFor(name: String): String? = ids[key(name)]
}
