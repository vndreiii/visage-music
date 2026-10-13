package com.music.vivi.database

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Skipped unless `cargo build --release --manifest-path native/Cargo.toml` has been run. */
class NativeHelpersTest {
    @Test
    fun fuzzySearchRanksTypos() {
        assumeTrue(NativeHelpers.available, "libvivi_native.so not built")
        val index = assertNotNull(
            NativeHelpers.searchIndex(
                listOf(
                    "a" to "Magnetic ILLIT",
                    "b" to "Capable of love PinkPantheress",
                    "c" to "Where You Are Willow",
                ),
            ),
        )
        index.use {
            assertEquals("a", it.query("magnt").first())
            assertEquals(listOf("b"), it.query("pink love"))
        }
    }

    @Test
    fun scanFindsAudioAndSkipsJunk() {
        assumeTrue(NativeHelpers.available, "libvivi_native.so not built")
        val dir = kotlin.io.path.createTempDirectory("vivi-native").toFile()
        try {
            File(dir, "Artist/Album").mkdirs()
            File(dir, "venv").mkdirs()
            File(dir, "Artist/Album/broken.mp3").writeBytes(ByteArray(9000))
            File(dir, "venv/skip.mp3").writeBytes(ByteArray(9000))
            val result = assertNotNull(NativeHelpers.scan(listOf(dir), File(dir, "covers"), emptyList()))
            assertEquals(1, result.tracks.size)
            assertTrue(result.tracks.single().unreadable) // garbage bytes → caller falls back to Jaudiotagger
        } finally {
            dir.deleteRecursively()
        }
    }
}
