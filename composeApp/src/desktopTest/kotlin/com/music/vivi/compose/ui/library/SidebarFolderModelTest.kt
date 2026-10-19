package com.music.vivi.compose.ui.library

import com.music.vivi.compose.ui.library.sidebar.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SidebarFolderModelTest {
    private fun e(id: String, title: String, kind: EntryKind = EntryKind.YT_PLAYLIST, rank: Int = 0) =
        LibraryEntry("$kind:$id", id, kind, title, "", null, null, rank)

    private val liked = e("liked", "Liked Songs", EntryKind.LIKED)
    private val a = e("a", "Alpha", rank = 1)
    private val b = e("b", "Bravo", EntryKind.ALBUM, rank = 2)
    private val c = e("c", "Charlie", EntryKind.ARTIST, rank = 3)
    private val d = e("d", "Delta", EntryKind.LOCAL_PLAYLIST, rank = 0)
    private val all = listOf(liked, a, b, c, d)

    private fun folder(id: String, title: String, vararg kids: LibraryEntry, expanded: Boolean = true, pos: Int = 0, sort: FolderSort = FolderSort.MANUAL) =
        LibraryFolderModel(id, title, expanded = expanded, position = pos, sort = sort, children = kids.map { it.key })

    private fun rows(
        folders: List<LibraryFolderModel>, hidden: Set<String> = emptySet(), showHidden: Boolean = false,
        filter: LibraryFilter = LibraryFilter.ALL, sort: LibrarySort = LibrarySort.RECENT, q: String = "", first: Boolean = true,
    ) = buildSidebarRows(all, folders, hidden, showHidden, filter, sort, q, first)

    private fun SidebarRow.label() = when (this) {
        is SidebarRow.Folder -> "[${folder.title}]"
        is SidebarRow.Item -> (if (folderId != null) "  " else "") + entry.title + (if (hidden) "*" else "")
    }

    @Test fun `folders pin below pinned entries with children inline and removed from the main list`() {
        val r = rows(listOf(folder("f", "Mix", b, a)))
        assertEquals(listOf("Liked Songs", "[Mix]", "  Bravo", "  Alpha", "Delta", "Charlie"), r.map { it.label() })
    }

    @Test fun `collapsed folder hides children but reports the count`() {
        val r = rows(listOf(folder("f", "Mix", a, b, expanded = false)))
        val f = r.filterIsInstance<SidebarRow.Folder>().single()
        assertEquals(2, f.count); assertTrue(!f.open)
        assertEquals(listOf("Liked Songs", "[Mix]", "Delta", "Charlie"), r.map { it.label() })
    }

    @Test fun `hidden items vanish unless show hidden, and then are flagged`() {
        val fs = listOf(folder("f", "Mix", a))
        assertEquals(listOf("Liked Songs", "[Mix]", "Delta", "Charlie"), rows(fs, hidden = setOf(a.key, b.key)).map { it.label() })
        assertEquals(listOf("Liked Songs", "[Mix]", "  Alpha*", "Delta", "Bravo*", "Charlie"), rows(fs, hidden = setOf(a.key, b.key), showHidden = true).map { it.label() })
    }

    @Test fun `search looks inside folders and force opens them`() {
        val r = rows(listOf(folder("f", "Mix", a, b, expanded = false)), q = "brav")
        assertEquals(listOf("[Mix]", "  Bravo"), r.map { it.label() })
    }

    @Test fun `matching folder title keeps all children and a non matching folder disappears`() {
        val fs = listOf(folder("f", "Mix", a, b), folder("g", "Other", c))
        assertEquals(listOf("[Mix]", "  Alpha", "  Bravo"), rows(fs, q = "mix").map { it.label() })
        assertEquals(listOf("[Other]", "  Charlie"), rows(fs, q = "charl").map { it.label() })
    }

    @Test fun `chip filters children and drops folders with no match`() {
        val fs = listOf(folder("f", "Mix", a, b), folder("g", "Other", c))
        assertEquals(listOf("[Mix]", "  Bravo"), rows(fs, filter = LibraryFilter.ALBUMS).map { it.label() })
    }

    @Test fun `folder child sort and mixed placement`() {
        val fs = listOf(folder("f", "Zed", b, a, sort = FolderSort.ALPHABETICAL))
        assertEquals(listOf("[Zed]", "  Alpha", "  Bravo"), rows(fs, q = "").filterNot { it.key == liked.key }.take(3).map { it.label() })
        val mixed = rows(fs, sort = LibrarySort.ALPHABETICAL, first = false).map { it.label() }
        assertEquals(listOf("Liked Songs", "Charlie", "Delta", "[Zed]", "  Alpha", "  Bravo"), mixed)
    }

    @Test fun `pinned entries ignore folder membership and empty folders show only unfiltered`() {
        val fs = listOf(folder("f", "Mix", liked), folder("e", "Empty"))
        val r = rows(fs)
        assertEquals("Liked Songs", r.first().label())
        assertTrue(r.any { it is SidebarRow.Folder && it.folder.id == "e" })
        assertTrue(rows(fs, filter = LibraryFilter.ALBUMS).none { it is SidebarRow.Folder })
    }
}
