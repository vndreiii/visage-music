package com.music.vivi.database

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalLibraryDbTest {
    @TempDir
    lateinit var dir: File

    private fun repo(): LocalMusicRepository {
        val driver = DatabaseFactory.createDriver(File(dir, "lib.db"))
        return LocalMusicRepository(ViviDatabase(driver), driver)
    }

    private fun LocalMusicRepository.add(id: String, title: String, artist: String, album: String, genre: String?, year: Long?) =
        inTransaction {
            upsertArtist("a-$artist", artist)
            upsertAlbum("al-$album", album, "a-$artist", artist, year, null, 1)
            upsertTrack(
                id, title, "al-$album", "a-$artist", artist, album, 200, "/music/$id.flac", 1, null, null, null,
                null, null, null, true, 1,
            )
            setTrackExtra(id, artist, genre, null, year, 1, 1000, 2000)
            setTrackArtists(id, listOf("a-$artist" to artist))
        }

    @Test
    fun ftsSearchGenresYearsAndKnownFiles() {
        val repo = repo()
        repo.add("t1", "Magnetic", "ILLIT", "SUPER REAL ME", "K-Pop", 2024)
        repo.add("t2", "Capable of love", "PinkPantheress", "Fancy That", "Electronic", 2023)
        repo.add("t3", "True romance", "PinkPantheress", "Fancy That", "Electronic", 2023)

        assertEquals(listOf("t1"), repo.search("magn").map { it.id })
        assertEquals(setOf("t2", "t3"), repo.search("pink").map { it.id }.toSet())
        assertEquals("t3", repo.search("pink true").single().id)
        assertEquals(listOf("Electronic" to 2L, "K-Pop" to 1L), repo.genres().map { it.value to it.trackCount })
        assertEquals(listOf(2024L, 2023L), repo.years().map { it.value })
        assertEquals(2, repo.tracksByGenre("electronic").size)
        assertEquals(1000L, repo.knownFiles().first().size)

        // The index follows updates and deletes through the triggers.
        repo.removeTracks(listOf("t1"))
        assertTrue(repo.search("magn").isEmpty())
    }

    private fun wav(file: File) {
        val data = ByteArray(16_000) // 1 s of 8 kHz mono 16-bit silence
        val buf = ByteBuffer.allocate(44 + data.size).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray()).putInt(36 + data.size).put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(8000).putInt(16000).putShort(2).putShort(16)
        buf.put("data".toByteArray()).putInt(data.size).put(data)
        file.writeBytes(buf.array())
    }

    @Test
    fun nativeScanSkipsUnchangedFiles() {
        assumeTrue(NativeHelpers.available, "libvivi_native.so not built")
        val music = File(dir, "Artist/Album").apply { mkdirs() }
        wav(File(music, "song.wav"))
        val covers = File(dir, "covers")

        val first = NativeHelpers.scan(listOf(dir), covers, emptyList())!!
        val track = first.tracks.single()
        assertTrue(!track.unreadable && !track.unchanged)
        assertEquals(1L, track.duration_sec)

        val known = listOf(KnownFileEntry(track.path, track.size, track.mtime_ms, track.cover_path))
        val second = NativeHelpers.scan(listOf(dir), covers, known)!!
        assertTrue(second.tracks.single().unchanged)
        assertEquals(1, second.stats.cached)
    }
}
