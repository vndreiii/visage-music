package com.music.vivi.compose.ui.library

import androidx.compose.ui.graphics.Color
import com.music.vivi.database.LocalMusicRepository
import com.music.vivi.database.Local_track
import java.time.LocalDate
import java.util.Random

/** A generated "playlist" built from what the library knows about the user's listening. */
internal data class LibraryMix(
    val id: String,
    val title: String,
    val subtitle: String,
    val tracks: List<Local_track>,
)

/** Stable, pleasant colour for a label: the same genre/decade always gets the same hue. */
internal fun tintFor(label: String, value: Float = 0.55f): Color {
    val hue = ((label.lowercase().hashCode() and 0x7fffffff) % 360).toFloat()
    return Color.hsv(hue, 0.55f, value)
}

/**
 * Mixes derived from tracked data: play counts, when things were added, lossless files, genres and
 * decades. Day-seeded shuffles change daily but stay stable within a day.
 */
internal fun buildMixes(
    tracks: List<Local_track>,
    rediscover: List<Local_track>,
    genres: List<LocalMusicRepository.Counted<String>>,
    today: LocalDate = LocalDate.now(),
): List<LibraryMix> {
    if (tracks.size < 8) return emptyList()
    val daySeed = today.toEpochDay()
    fun seeded(list: List<Local_track>, salt: Long, n: Int) = list.shuffled(Random(daySeed * 31 + salt)).take(n)

    val mixes = ArrayList<LibraryMix>()
    mixes += LibraryMix("daily", "Daily shuffle", "Fresh every day", seeded(tracks, 1, 50))

    val onRepeat = tracks.filter { it.play_count > 0 }.sortedByDescending { it.play_count }.take(40)
    if (onRepeat.size >= 5) mixes += LibraryMix("repeat", "On repeat", "Your most played", onRepeat)

    val fresh = tracks.sortedByDescending { it.added_at_ms }.take(40)
    mixes += LibraryMix("fresh", "Fresh finds", "Recently added", fresh)

    if (rediscover.size >= 5) mixes += LibraryMix("rediscover", "Rediscover", "Haven't heard in a while", rediscover)

    val lossless = tracks.filter { it.is_lossless != 0L }
    if (lossless.size >= 10) mixes += LibraryMix("lossless", "Lossless picks", "Best quality, shuffled", seeded(lossless, 2, 40))

    genres.filter { it.trackCount >= 12 }.take(3).forEachIndexed { i, g ->
        val pool = tracks.filter { it.genre.equals(g.value, ignoreCase = true) }
        mixes += LibraryMix("genre-${g.value}", "${g.value} mix", "${pool.size} tracks", seeded(pool, 10L + i, 40))
    }

    tracks.filter { (it.year ?: 0L) > 0L }
        .groupBy { (it.year!! / 10) * 10 }
        .filter { it.value.size >= 12 }
        .toSortedMap(compareByDescending { it })
        .entries.take(3)
        .forEach { (decade, list) ->
            mixes += LibraryMix("decade-$decade", "${decade}s", "${list.size} tracks", seeded(list, decade, 40))
        }
    return mixes
}
