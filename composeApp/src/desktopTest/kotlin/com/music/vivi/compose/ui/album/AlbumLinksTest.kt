package com.music.vivi.compose.ui.album

import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlbumLinksTest {
    private fun album(id: String, title: String, vararg artists: String) = AlbumItem(
        browseId = id, playlistId = "OLAK$id", title = title,
        artists = artists.map { Artist(it, null) }, thumbnail = "",
    )

    @Test fun picksExactTitleIgnoringCaseAndPunctuation() {
        val hits = listOf(album("a", "Random Access Memories (Deluxe)", "Daft Punk"), album("b", "Random Access Memories", "Daft Punk"))
        assertEquals("b", AlbumLinks.pick(hits, "random access memories!", "Daft Punk")?.id)
    }

    @Test fun prefersMatchingArtistAmongSameTitles() {
        val hits = listOf(album("a", "Greatest Hits", "Queen"), album("b", "Greatest Hits", "ABBA"))
        assertEquals("b", AlbumLinks.pick(hits, "Greatest Hits", "ABBA & Friends")?.id)
    }

    @Test fun noTitleMatchGivesNull() {
        assertNull(AlbumLinks.pick(listOf(album("a", "Other", "X")), "Wanted", "X"))
    }

    @Test fun openableRules() {
        assertTrue(AlbumLinks.isOpenable("A", "MPREb1", isLocalFile = true))
        assertTrue(AlbumLinks.isOpenable("A", null, isLocalFile = false))
        assertFalse(AlbumLinks.isOpenable("A", null, isLocalFile = true))
        assertFalse(AlbumLinks.isOpenable("", "MPREb1", isLocalFile = false))
    }
}
