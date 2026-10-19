package com.music.vivi.database

import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.test.*

class LibraryFolderTest {
    private fun database(block: (VisageDatabase) -> Unit) {
        val directory = Files.createTempDirectory("vivi-folder-test").toFile()
        val driver = DatabaseFactory.createDriver(directory.resolve("library.db"))
        try { block(VisageDatabase(driver)) } finally { driver.close(); directory.deleteRecursively() }
    }

    @Test fun `create update and delete a folder returns items to the main list`() = database { db ->
        val repo = LibraryRepository(db)
        val id = repo.createFolder("  Chill ")
        assertEquals("Chill", repo.libraryFolders().single().title)
        repo.addToFolder(id, "YT_PLAYLIST", "p1")
        repo.addToFolder(id, "ALBUM", "a1")
        assertEquals(2, repo.folderItems().size)
        repo.updateFolder(id, "Focus", "star", 0xFF112233, "IMAGE", "/covers/x.png")
        val f = repo.libraryFolders().single()
        assertEquals(Triple("Focus", "star", 0xFF112233), Triple(f.title, f.icon, f.accent))
        assertEquals("IMAGE", f.cover_kind)
        repo.deleteFolder(id)
        assertTrue(repo.libraryFolders().isEmpty())
        assertTrue(repo.folderItems().isEmpty())
    }

    @Test fun `an item lives in one folder and moving it re-parents it`() = database { db ->
        val repo = LibraryRepository(db)
        val a = repo.createFolder("A"); val b = repo.createFolder("B")
        repo.addToFolder(a, "ARTIST", "x")
        repo.addToFolder(b, "ARTIST", "x")
        val items = repo.folderItems()
        assertEquals(1, items.size)
        assertEquals(b, items.single().folder_id)
        repo.removeFromFolder("ARTIST", "x")
        assertTrue(repo.folderItems().isEmpty())
    }

    @Test fun `manual order can be rearranged`() = database { db ->
        val repo = LibraryRepository(db)
        val f = repo.createFolder("F")
        listOf("1", "2", "3").forEach { repo.addToFolder(f, "YT_PLAYLIST", it) }
        repo.moveInFolder(f, "YT_PLAYLIST", "3", 0)
        assertEquals(listOf("3", "1", "2"), repo.folderItems().map { it.item_id })
    }

    @Test fun `hide unhide and reset only touch the sidebar`() = database { db ->
        val repo = LibraryRepository(db)
        repo.hideItem("YT_PLAYLIST", "p"); repo.hideItem("YT_PLAYLIST", "p"); repo.hideItem("ALBUM", "a")
        assertEquals(2, repo.hiddenItems().size)
        repo.unhideItem("ALBUM", "a")
        assertEquals(setOf("YT_PLAYLIST" to "p"), repo.hiddenItems())
        repo.resetHidden()
        assertTrue(repo.hiddenItems().isEmpty())
    }

    @Test fun `deleting a local playlist drops its folder membership and hidden flag`() = database { db ->
        val repo = LibraryRepository(db)
        val pl = repo.createLocalPlaylist("Mine")
        val f = repo.createFolder("F")
        repo.addToFolder(f, "LOCAL_PLAYLIST", pl); repo.hideItem("LOCAL_PLAYLIST", pl)
        repo.deleteLocalPlaylist(pl)
        assertTrue(repo.folderItems().isEmpty()); assertTrue(repo.hiddenItems().isEmpty())
        assertEquals(1, repo.libraryFolders().size)
    }

    @Test fun `folder mutation counter bumps and state flow reflects changes`() = database { db ->
        val repo = LibraryRepository(db)
        val before = repo.folderMutations.value
        repo.createFolder("F")
        assertTrue(repo.folderMutations.value > before)
    }

    @Test fun `migration is idempotent on an existing database`() {
        val directory = Files.createTempDirectory("vivi-folder-mig").toFile()
        try {
            DatabaseFactory.createDriver(directory.resolve("l.db")).close()
            val d = DatabaseFactory.createDriver(directory.resolve("l.db"))
            LibraryRepository(VisageDatabase(d)).createFolder("ok"); d.close()
        } finally { directory.deleteRecursively() }
    }
}
