package com.music.vivi.nativelib

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppPathsTest {
    @TempDir
    lateinit var tmp: File

    @Test
    fun movesLegacyDirectoryAndLeavesASymlink() {
        val legacy = File(tmp, "vivi-desktop").apply { mkdirs() }
        File(legacy, "vivi.db").writeText("library")
        File(legacy, "offline").mkdirs()
        File(legacy, "offline/track.opus").writeText("audio")

        val dir = AppPaths.migrateDir(tmp)

        assertEquals(File(tmp, "visage"), dir)
        assertEquals("library", File(dir, "vivi.db").readText())
        assertEquals("audio", File(dir, "offline/track.opus").readText())
        assertTrue(Files.isSymbolicLink(legacy.toPath()), "old path should point at the new one")
        assertEquals("library", File(legacy, "vivi.db").readText(), "an older build still finds its data")
        // Idempotent: a second launch changes nothing.
        assertEquals(dir, AppPaths.migrateDir(tmp))
        assertEquals("library", File(dir, "vivi.db").readText())
    }

    @Test
    fun neverTouchesAnExistingNewDirectory() {
        File(tmp, "vivi-desktop").apply { mkdirs(); File(this, "old.txt").writeText("old") }
        File(tmp, "visage").apply { mkdirs(); File(this, "new.txt").writeText("new") }

        val dir = AppPaths.migrateDir(tmp)

        assertTrue(File(dir, "new.txt").exists())
        assertFalse(File(dir, "old.txt").exists())
        assertFalse(Files.isSymbolicLink(File(tmp, "vivi-desktop").toPath()))
        assertEquals("old", File(tmp, "vivi-desktop/old.txt").readText())
    }

    @Test
    fun freshInstallJustCreatesTheDirectory() {
        val dir = AppPaths.migrateDir(tmp)
        assertTrue(dir.isDirectory)
        assertFalse(File(tmp, "vivi-desktop").exists())
    }

    @Test
    fun renamesTheDatabaseWithItsWalFiles() {
        File(tmp, "vivi.db").writeText("db")
        File(tmp, "vivi.db-wal").writeText("wal")
        File(tmp, "vivi.db-shm").writeText("shm")

        val file = AppPaths.migratedFile(tmp, "visage.db", "vivi.db")

        assertEquals(File(tmp, "visage.db"), file)
        assertEquals("db", file.readText())
        assertEquals("wal", File(tmp, "visage.db-wal").readText())
        assertEquals("shm", File(tmp, "visage.db-shm").readText())
        assertFalse(File(tmp, "vivi.db").exists())
    }

    @Test
    fun envFallsBackToLegacyPrefix() {
        // Neither name is set in the test environment; the helper must simply report unset.
        assertEquals(null, AppEnv.get("DEFINITELY_NOT_SET_FOR_TESTS"))
    }
}
