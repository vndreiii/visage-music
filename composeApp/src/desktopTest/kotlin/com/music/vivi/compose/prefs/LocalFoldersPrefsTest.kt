package com.music.vivi.compose.prefs

import com.music.vivi.compose.local.LocalFoldersPrefs
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalFoldersPrefsTest {
    private val dir: File = Files.createTempDirectory("visage-folders").toFile().also { it.deleteOnExit() }

    @Test
    fun severalFoldersNestingAndDuplicates() {
        val prefs = DesktopPrefs(File(dir, "s.properties"))
        val a = File(dir, "a").apply { mkdirs() }
        val b = File(dir, "b").apply { mkdirs() }
        val inner = File(a, "inner").apply { mkdirs() }

        val first = LocalFoldersPrefs.addFolders(prefs, listOf(a, b))
        assertEquals(2, first.added.size)

        val second = LocalFoldersPrefs.addFolders(prefs, listOf(inner, File(dir, "missing")))
        assertEquals(listOf(inner.absolutePath), second.alreadyCovered)
        assertEquals(1, second.notFolders.size)

        // Adding a parent swallows the folders it contains.
        val parent = LocalFoldersPrefs.addFolders(prefs, listOf(dir))
        assertEquals(listOf(dir.absolutePath), parent.added)
        assertEquals(listOf(dir.absolutePath), LocalFoldersPrefs.folders(prefs))
    }
}
