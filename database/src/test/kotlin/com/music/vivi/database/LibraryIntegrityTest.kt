package com.music.vivi.database

import com.music.innertube.models.Artist
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.test.*

class LibraryIntegrityTest {
    private fun database(block: (VisageDatabase) -> Unit) {
        val directory = Files.createTempDirectory("vivi-db-test").toFile()
        val driver = DatabaseFactory.createDriver(directory.resolve("library.db"))
        try { block(VisageDatabase(driver)) } finally { driver.close(); directory.deleteRecursively() }
    }

    private fun LocalMusicRepository.track(id: String, album: String = "album", count: Long = 0) {
        upsertTrack(
            id, "Track $id", album, "artist", "Artist", "Album", 60,
            "/music/$id.flac", 1, null, null, null, 1000, 44100, 16, true, 100,
            playCount = count,
        )
    }

    @Test fun `rescanning an artist and album preserves every track relationship`() = database { db ->
        val repo = LocalMusicRepository(db)
        repeat(3) { index ->
            repo.inTransaction {
                upsertArtist("artist", "Artist")
                upsertAlbum("album", "Album", "artist", "Artist", 2020, "/cover.jpg", 100)
                track(index.toString())
            }
        }
        assertEquals(3, repo.tracksForAlbum("album").size)
        assertEquals(3, repo.tracksForArtist("artist").size)
        repo.upsertAlbum("album", "New title", "artist", "Artist", 2020, null, 999)
        assertEquals(3, repo.tracksForAlbum("album").size)
        assertEquals("/cover.jpg", repo.allAlbums().single().cover_path)
        assertEquals(100L, repo.allAlbums().single().added_at_ms)
    }

    @Test fun `rescan with stale counters cannot erase a concurrent play`() = database { db ->
        val repo = LocalMusicRepository(db)
        repo.upsertArtist("artist", "Artist")
        repo.upsertAlbum("album", "Album", "artist", "Artist", null, null, 100)
        repo.track("a")
        repo.recordPlay("a", 200)
        repo.track("a", count = 0)
        assertEquals(1L, repo.trackById("a")?.play_count)
        assertEquals(200L, repo.trackById("a")?.last_played_ms)
        assertEquals("a", repo.recentlyPlayedTracks().single().id)
    }

    private fun song(id: String) = SongItem(id, "Song $id", listOf(Artist("Artist", null)), duration = 60, thumbnail = "https://example.test/art.jpg")
    private fun playlist(id: String) = PlaylistItem(id, "Playlist $id", null, "1 song", "https://example.test/art.jpg", null, null, null)

    @Test fun `caching a song in another playlist keeps both memberships and its like`() = database { db ->
        val repo = LibraryRepository(db)
        repo.upsertPlaylistWithSongs(playlist("LM"), listOf(song("one")))
        repo.upsertPlaylistWithSongs(playlist("other"), listOf(song("one")))
        assertEquals(listOf("one"), repo.getPlaylistSongs("LM").map { it.id })
        assertEquals(listOf("one"), repo.getPlaylistSongs("other").map { it.id })
        assertTrue(repo.isLiked("one"))
        repo.setLiked("one", false)
        assertFalse(repo.isLiked("one"))
        assertTrue(repo.getPlaylistSongs("LM").isEmpty())
        assertEquals(1, repo.getPlaylistSongs("other").size)
    }

    @Test fun `removing tracks prunes empty albums and artists only`() = database { db ->
        val repo = LocalMusicRepository(db)
        repo.upsertArtist("artist", "Artist")
        repo.upsertAlbum("album", "Album", "artist", "Artist", null, null, 100)
        repo.track("a"); repo.track("b")
        repo.removeTracks(listOf("a"))
        assertEquals(1, repo.allAlbums().size)
        repo.removeTracks(listOf("b"))
        assertTrue(repo.allAlbums().isEmpty())
        assertTrue(repo.allArtists().isEmpty())
    }
}

class LocalPlaylistTest {
    private fun database(block: (VisageDatabase) -> Unit) {
        val directory = Files.createTempDirectory("vivi-db-test").toFile()
        val driver = DatabaseFactory.createDriver(directory.resolve("library.db"))
        try { block(VisageDatabase(driver)) } finally { driver.close(); directory.deleteRecursively() }
    }

    @Test fun `local playlists create add reorder remove and delete`() = database { db ->
        val repo = LibraryRepository(db)
        val id = repo.createLocalPlaylist("Road trip")
        assertEquals(listOf("Road trip"), repo.localPlaylists().map { it.title })
        assertTrue(repo.addToLocalPlaylist(id, "a", "A", "x", null, 60))
        assertTrue(repo.addToLocalPlaylist(id, "b", "B", "x", null, 30))
        assertFalse(repo.addToLocalPlaylist(id, "a", "A", "x", null, 60))
        assertEquals(listOf("a", "b"), repo.getPlaylistSongs(id).map { it.id })
        assertEquals(2L, repo.getPlaylist(id)?.song_count)
        repo.moveInLocalPlaylist(id, 0, 1)
        assertEquals(listOf("b", "a"), repo.getPlaylistSongs(id).map { it.id })
        repo.removeFromLocalPlaylist(id, "b")
        assertEquals(listOf("a"), repo.getPlaylistSongs(id).map { it.id })
        repo.renameLocalPlaylist(id, "Trip")
        assertEquals("Trip", repo.getPlaylist(id)?.title)
        repo.deleteLocalPlaylist(id)
        assertTrue(repo.localPlaylists().isEmpty())
    }

    @Test fun `reorder handles bad indices`() {
        assertEquals(listOf(2, 3, 1), LibraryRepository.reorder(listOf(1, 2, 3), 0, 2))
        assertNull(LibraryRepository.reorder(listOf(1, 2, 3), 0, 0))
        assertNull(LibraryRepository.reorder(listOf(1, 2, 3), 5, 0))
    }
}
