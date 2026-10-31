package com.music.vivi.compose.ui.library

import com.music.vivi.compose.ui.library.sidebar.EntryKind
import com.music.vivi.compose.ui.library.sidebar.LibraryEntry
import com.music.vivi.compose.ui.library.sidebar.hiddenItemRows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HiddenItemsTest {
    private fun entry(kind: EntryKind, id: String, title: String, creator: String = "", thumb: String? = null) =
        LibraryEntry("${kind.name}:$id", id, kind, title, creator, null, thumb)

    @Test
    fun resolvesKnownItemsWithKindLabelAndCreator() {
        val album = entry(EntryKind.ALBUM, "MPREb1", "Blue", creator = "Joni", thumb = "t.jpg")
        val playlist = entry(EntryKind.YT_PLAYLIST, "PL1", "Road trip", creator = "Me")
        val rows = hiddenItemRows(setOf("ALBUM" to "MPREb1", "YT_PLAYLIST" to "PL1"), listOf(album, playlist))

        assertEquals(2, rows.size)
        assertTrue(rows.all { it.resolved })
        // Sorted by kind ordinal: YT_PLAYLIST comes before ALBUM.
        assertEquals(listOf("PL1", "MPREb1"), rows.map { it.id })
        val albumRow = rows.first { it.id == "MPREb1" }
        assertEquals("Blue", albumRow.title)
        assertEquals("Album · Joni", albumRow.subtitle)
        assertEquals("t.jpg", albumRow.thumbnail)
        assertEquals("Playlist · Me", rows.first { it.id == "PL1" }.subtitle)
        assertEquals("ALBUM:MPREb1", albumRow.key)
    }

    @Test
    fun unresolvedItemsKeepRawIdAndStayRestorable() {
        val known = entry(EntryKind.ARTIST, "UC1", "Artist A")
        val rows = hiddenItemRows(setOf("ALBUM" to "gone-id", "ARTIST" to "UC1"), listOf(known))

        val missing = rows.first { it.id == "gone-id" }
        assertFalse(missing.resolved)
        assertEquals(EntryKind.ALBUM, missing.kind)
        assertEquals("gone-id", missing.title)
        assertNull(missing.thumbnail)
        assertEquals("Album · Details unavailable", missing.subtitle)
        // Unresolved rows come after resolved ones.
        assertEquals("UC1", rows.first().id)
    }

    @Test
    fun unknownTypeIsStillListed() {
        val rows = hiddenItemRows(setOf("SOMETHING_NEW" to "x"), emptyList())
        assertEquals(1, rows.size)
        assertNull(rows.single().kind)
        assertEquals("Item · Details unavailable", rows.single().subtitle)
    }

    @Test
    fun likedAndDownloadsUseTheirOwnLabels() {
        val liked = entry(EntryKind.LIKED, "LM", "Liked Songs")
        val downloads = entry(EntryKind.DOWNLOADS, "offline", "Downloaded")
        val rows = hiddenItemRows(setOf("LIKED" to "LM", "DOWNLOADS" to "offline"), listOf(liked, downloads))
        assertEquals("Playlist", rows.first { it.type == "LIKED" }.subtitle)
        assertEquals("Downloads", rows.first { it.type == "DOWNLOADS" }.subtitle)
    }

    @Test
    fun emptyHiddenSetGivesNoRows() {
        assertTrue(hiddenItemRows(emptySet(), listOf(entry(EntryKind.ALBUM, "a", "A"))).isEmpty())
    }
}
