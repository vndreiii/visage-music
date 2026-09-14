package com.music.vivi.database

import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem

/**
 * Cache/upsert helpers for YouTube (and later local) library data.
 * Stores the metadata shown on playlist headers: title, song count, total duration.
 */
class LibraryRepository(
    private val db: ViviDatabase,
) {
    fun upsertSong(
        song: SongItem,
        liked: Boolean = false,
        inLibrary: Boolean = false,
        source: String = "youtube",
        localPath: String? = null,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        db.songQueries.upsert(
            id = song.id,
            title = song.title,
            artists = song.artists.joinToString { it.name },
            album = song.album?.name,
            duration_sec = song.duration?.toLong(),
            thumbnail = song.thumbnail,
            liked = if (liked) 1L else 0L,
            in_library = if (inLibrary) 1L else 0L,
            source = source,
            local_path = localPath,
            last_seen_ms = nowMs,
        )
    }

    /**
     * Persist a full playlist snapshot (header numbers + ordered songs).
     * Replaces [playlist_song] rows for this playlist id.
     */
    fun upsertPlaylistWithSongs(
        playlist: PlaylistItem,
        songs: List<SongItem>,
        source: String = "youtube",
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val totalDuration = songs.sumOf { (it.duration ?: 0).toLong() }
        val songCount = songs.size.takeIf { it > 0 }
            ?: playlist.songCountText?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() }

        db.transaction {
            db.playlistQueries.upsert(
                id = playlist.id,
                title = playlist.title,
                author = playlist.author?.name,
                thumbnail = playlist.thumbnail,
                description = playlist.description,
                song_count = songCount?.toLong(),
                total_duration_sec = totalDuration.takeIf { it > 0 },
                source = source,
                browse_id = playlist.id,
                updated_ms = nowMs,
            )
            db.playlistSongQueries.clearForPlaylist(playlist.id)
            songs.forEachIndexed { index, song ->
                upsertSong(song, liked = playlist.id == "LM", inLibrary = true, source = source, nowMs = nowMs)
                db.playlistSongQueries.upsert(
                    playlist_id = playlist.id,
                    song_id = song.id,
                    position = index.toLong(),
                )
            }
        }
    }

    fun getPlaylist(id: String) = db.playlistQueries.selectById(id).executeAsOneOrNull()

    fun getPlaylistSongs(id: String) = db.playlistSongQueries.songsForPlaylist(id).executeAsList()

    fun allPlaylists(source: String? = null) =
        if (source == null) db.playlistQueries.selectAll().executeAsList()
        else db.playlistQueries.selectBySource(source).executeAsList()

    fun likedSongs() = db.songQueries.selectLiked().executeAsList()

    fun isLiked(id: String): Boolean {
        val song = db.songQueries.selectById(id).executeAsOneOrNull()
        if (song?.liked == 1L) return true
        return db.playlistSongQueries.songsForPlaylist("LM").executeAsList().any { it.id == id }
    }

    fun setLiked(id: String, liked: Boolean) {
        val song = db.songQueries.selectById(id).executeAsOneOrNull()
        if (song != null) {
            db.songQueries.upsert(
                id = song.id,
                title = song.title,
                artists = song.artists,
                album = song.album,
                duration_sec = song.duration_sec,
                thumbnail = song.thumbnail,
                liked = if (liked) 1L else 0L,
                in_library = song.in_library,
                source = song.source,
                local_path = song.local_path,
                last_seen_ms = System.currentTimeMillis(),
            )
            return
        }
        if (liked) {
            db.songQueries.upsert(
                id = id,
                title = id,
                artists = "",
                album = null,
                duration_sec = null,
                thumbnail = null,
                liked = 1L,
                in_library = 0L,
                source = "youtube",
                local_path = null,
                last_seen_ms = System.currentTimeMillis(),
            )
        }
    }
}
