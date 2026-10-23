package com.music.vivi.database

import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.test.*

class LyricsCacheTest {
    private fun repo(block: (LyricsCacheRepository) -> Unit) {
        val directory = Files.createTempDirectory("vivi-lyrics-test").toFile()
        val driver = DatabaseFactory.createDriver(directory.resolve("l.db"))
        try { block(LyricsCacheRepository(VisageDatabase(driver))) } finally { driver.close(); directory.deleteRecursively() }
    }

    @Test fun `lyrics and offset are stored together and survive each other`() = repo { r ->
        assertNull(r.get("a"))
        r.setOffset("a", 300)
        assertEquals(CachedLyrics(null, null, 300), r.get("a"))
        r.putLyrics("a", "LRCLIB", "[00:01.00]hi")
        assertEquals(CachedLyrics("LRCLIB", "[00:01.00]hi", 300), r.get("a"))
        r.putLyrics("a", "KUGOU", "[00:02.00]yo")
        r.setOffset("a", -100)
        assertEquals(CachedLyrics("KUGOU", "[00:02.00]yo", -100), r.get("a"))
    }

    @Test fun `purge removes lyrics but keeps offsets`() = repo { r ->
        r.putLyrics("a", "LRCLIB", "x"); r.putLyrics("b", "LRCLIB", "y"); r.setOffset("b", 200)
        assertEquals(2, r.cachedCount())
        r.purgeLyrics()
        assertEquals(0, r.cachedCount())
        assertNull(r.get("a"))
        assertEquals(CachedLyrics(null, null, 200), r.get("b"))
    }
}
