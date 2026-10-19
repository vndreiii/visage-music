package com.music.vivi.compose.ui.library

import com.music.vivi.compose.ui.library.sidebar.EntryKind
import com.music.vivi.compose.ui.library.sidebar.LibraryEntry
import com.music.vivi.compose.ui.library.sidebar.LibraryFilter
import com.music.vivi.compose.ui.library.sidebar.LibrarySort
import com.music.vivi.compose.ui.library.sidebar.filterAndSort
import kotlin.test.Test
import kotlin.test.assertEquals

class SidebarModelTest {
    private fun e(id: String, title: String, kind: EntryKind, creator: String = "", rank: Int = 0, n: Int? = null) =
        LibraryEntry("$kind:$id", id, kind, title, creator, n, null, rank)

    private val all = listOf(
        e("liked", "Liked Songs", EntryKind.LIKED, "Playlist", n = 10),
        e("dl", "Downloaded", EntryKind.DOWNLOADS, n = 3),
        e("l1", "Road trip", EntryKind.LOCAL_PLAYLIST, "You", rank = 0),
        e("l2", "Étude", EntryKind.LOCAL_PLAYLIST, "You", rank = 1),
        e("y1", "Chill Mix", EntryKind.YT_PLAYLIST, "YouTube Music", rank = 1_000),
        e("y2", "Bangers", EntryKind.YT_PLAYLIST, "Alice", rank = 1_001),
        e("a1", "Abbey Road", EntryKind.ALBUM, "The Beatles", rank = 2_000),
        e("r1", "Daft Punk", EntryKind.ARTIST, "Daft Punk", rank = 3_000),
    )

    private fun ids(l: List<LibraryEntry>) = l.map { it.id }

    @Test fun `all keeps pinned first then recency order`() {
        assertEquals(listOf("liked", "dl", "l1", "l2", "y1", "y2", "a1", "r1"), ids(filterAndSort(all, LibraryFilter.ALL, LibrarySort.RECENT, "")))
    }

    @Test fun `chips select by source`() {
        assertEquals(listOf("liked", "y1", "y2"), ids(filterAndSort(all, LibraryFilter.YOUTUBE, LibrarySort.RECENT, "")))
        assertEquals(listOf("l1", "l2"), ids(filterAndSort(all, LibraryFilter.MINE, LibrarySort.RECENT, "")))
        assertEquals(listOf("a1"), ids(filterAndSort(all, LibraryFilter.ALBUMS, LibrarySort.RECENT, "")))
        assertEquals(listOf("r1"), ids(filterAndSort(all, LibraryFilter.ARTISTS, LibrarySort.RECENT, "")))
        assertEquals(listOf("dl"), ids(filterAndSort(all, LibraryFilter.DOWNLOADED, LibrarySort.RECENT, "")))
    }

    @Test fun `alphabetical ignores case and accents and keeps pinned on top`() {
        val r = ids(filterAndSort(all, LibraryFilter.ALL, LibrarySort.ALPHABETICAL, ""))
        assertEquals(listOf("liked", "dl", "a1", "y2", "y1", "r1", "l2", "l1"), r)
    }

    @Test fun `creator sort groups by creator then title with blank creators last`() {
        val extra = all + e("x", "Zed", EntryKind.YT_PLAYLIST, "", rank = 5)
        val r = ids(filterAndSort(extra, LibraryFilter.YOUTUBE, LibrarySort.CREATOR, ""))
        assertEquals(listOf("liked", "y2", "y1", "x"), r)
    }

    @Test fun `search matches title or creator accent-insensitively`() {
        assertEquals(listOf("l2"), ids(filterAndSort(all, LibraryFilter.ALL, LibrarySort.RECENT, "etude")))
        assertEquals(listOf("y2"), ids(filterAndSort(all, LibraryFilter.ALL, LibrarySort.RECENT, "  ALICE ")))
        assertEquals(listOf("a1"), ids(filterAndSort(all, LibraryFilter.ALL, LibrarySort.RECENT, "beatles")))
        assertEquals(emptyList(), ids(filterAndSort(all, LibraryFilter.ALL, LibrarySort.RECENT, "zzz")))
    }

    @Test fun `subtitle reads author and track count`() {
        assertEquals("Alice", all.first { it.id == "y2" }.subtitle)
        assertEquals("Playlist · 10 tracks", all.first { it.id == "liked" }.subtitle)
        assertEquals("Album", all.first { it.id == "a1" }.subtitle)
        assertEquals("Artist", all.first { it.id == "r1" }.subtitle)
    }
}
