package com.music.vivi.compose.ui.home

import com.music.innertube.models.Artist
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.pages.HomePage
import kotlin.test.*

class HomeFeedTest {
    private fun song(id: Int) = SongItem("s$id", "Song $id", listOf(Artist("Artist", null)), duration = 60, thumbnail = "art")
    private fun playlist(id: String) = PlaylistItem(id, id, null, "1 song", "art", null, null, null)
    private fun shelf(title: String, items: List<com.music.innertube.models.YTItem>) = HomePage.Section(title, null, null, null, items)

    @Test fun quickPicksIncludeDistinctSongsFromOtherRecommendations() {
        val sections = listOf(shelf("Quick picks", (0..7).map(::song)), shelf("Recommended", (4..39).map(::song)))
        val feed = prepareHomeSections(sections, emptySet())
        assertEquals("Quick picks", feed.first().title)
        assertEquals(40, feed.first().items.size)
        assertEquals(40, feed.first().items.map { it.id }.distinct().size)
    }

    @Test fun playlistsAreNotRepeatedAcrossSavedAndRecommendedShelves() {
        val sections = listOf(shelf("Mixes", listOf(playlist("a"), playlist("b"))), shelf("Discover", listOf(playlist("b"), playlist("c"))))
        val feed = prepareHomeSections(sections, setOf("a"))
        assertEquals(listOf("b", "c"), feed.flatMap { it.items }.map { it.id })
    }
}
