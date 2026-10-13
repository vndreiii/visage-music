package com.music.vivi.database

/**
 * Indexed local music library (user-chosen folders).
 */
class LocalMusicRepository(
    private val db: ViviDatabase,
) {
    fun upsertArtist(id: String, name: String) {
        db.localArtistQueries.upsert(id, name)
        db.localArtistQueries.updateName(name, id)
    }

    fun upsertAlbum(
        id: String,
        title: String,
        artistId: String?,
        artistName: String,
        releaseYear: Long?,
        coverPath: String?,
        addedAtMs: Long,
    ) {
        db.localAlbumQueries.upsert(
            id = id,
            title = title,
            artist_id = artistId,
            artist_name = artistName,
            release_year = releaseYear,
            cover_path = coverPath,
            added_at_ms = addedAtMs,
        )
        db.localAlbumQueries.updateMetadata(title, artistId, artistName, releaseYear, coverPath, id)
    }

    fun upsertTrack(
        id: String,
        title: String,
        albumId: String?,
        artistId: String?,
        artistName: String,
        albumTitle: String?,
        durationSec: Long?,
        filePath: String,
        trackNumber: Long?,
        coverPath: String?,
        lyricsPath: String?,
        embeddedLyrics: String?,
        bitrate: Long?,
        sampleRate: Long?,
        bitDepth: Long?,
        isLossless: Boolean,
        addedAtMs: Long,
        lastPlayedMs: Long? = null,
        playCount: Long = 0,
    ) {
        db.localTrackQueries.upsert(
            id = id,
            title = title,
            album_id = albumId,
            artist_id = artistId,
            artist_name = artistName,
            album_title = albumTitle,
            duration_sec = durationSec,
            file_path = filePath,
            track_number = trackNumber,
            cover_path = coverPath,
            lyrics_path = lyricsPath,
            embedded_lyrics = embeddedLyrics,
            bitrate = bitrate,
            sample_rate = sampleRate,
            bit_depth = bitDepth,
            is_lossless = if (isLossless) 1L else 0L,
            added_at_ms = addedAtMs,
            last_played_ms = lastPlayedMs,
            play_count = playCount,
        )
        db.localTrackQueries.updateMetadata(
            title, albumId, artistId, artistName, albumTitle, durationSec, filePath, trackNumber,
            coverPath, lyricsPath, embeddedLyrics, bitrate, sampleRate, bitDepth, if (isLossless) 1L else 0L, id,
        )
    }

    fun recentlyPlayedTracks(limit: Long = 50) = db.localTrackQueries.selectRecentlyPlayed(limit).executeAsList()

    fun allTracks(): List<Local_track> = db.localTrackQueries.selectAll().executeAsList()
    fun allAlbums(): List<Local_album> = db.localAlbumQueries.selectAll().executeAsList()
    fun allArtists(): List<Local_artist> = db.localArtistQueries.selectAll().executeAsList()

    fun trackById(id: String) = db.localTrackQueries.selectById(id).executeAsOneOrNull()
    fun trackByPath(path: String) = db.localTrackQueries.selectByPath(path).executeAsOneOrNull()

    fun recentlyAddedTracks(limit: Long = 24) =
        runCatching { db.localTrackQueries.selectRecentlyAdded(limit).executeAsList() }
            .getOrElse { db.localTrackQueries.selectAll().executeAsList().take(limit.toInt()) }

    fun recentlyAddedAlbums(limit: Long = 24) =
        runCatching { db.localAlbumQueries.selectRecentlyAdded(limit).executeAsList() }
            .getOrElse { db.localAlbumQueries.selectAll().executeAsList().take(limit.toInt()) }

    fun heavyRotation(limit: Long = 24) =
        runCatching { db.localTrackQueries.selectHeavyRotation(limit).executeAsList() }
            .getOrElse { emptyList() }

    fun rediscoverTracks(
        olderThanMs: Long = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000,
        limit: Long = 24,
    ) = runCatching {
        db.localTrackQueries.selectRediscover(olderThanMs, olderThanMs, limit).executeAsList()
    }.getOrElse { emptyList() }

    fun rediscoverAlbums(
        olderThanMs: Long = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000,
        limit: Long = 24,
    ) = runCatching {
        db.localAlbumQueries.selectRediscover(olderThanMs, limit).executeAsList()
    }.getOrElse { emptyList() }

    fun tracksForAlbum(albumId: String) =
        db.localTrackQueries.selectByAlbum(albumId).executeAsList()

    fun tracksForArtist(artistId: String) =
        db.localTrackQueries.selectByArtist(artistId).executeAsList()

    fun recordPlay(trackId: String, nowMs: Long = System.currentTimeMillis()) {
        withSqliteBusyRetry {
            db.localTrackQueries.recordPlay(nowMs, trackId)
        }
    }

    fun trackCount() = db.localTrackQueries.count().executeAsOne()

    /** One short write transaction (e.g. artist+album+track upsert for a scanned file). */
    fun <T> inTransaction(block: LocalMusicRepository.() -> T): T =
        withSqliteBusyRetry {
            db.transactionWithResult {
                block()
            }
        }

    fun removeTracks(ids: List<String>) {
        inTransaction {
            ids.forEach { db.localTrackQueries.deleteById(it) }
            db.localAlbumQueries.deleteEmpty()
            db.localArtistQueries.deleteEmpty()
        }
    }

    fun clearAll() {
        withSqliteBusyRetry {
            db.transaction {
                db.localTrackQueries.deleteAll()
                db.localAlbumQueries.deleteAll()
                db.localArtistQueries.deleteAll()
            }
        }
    }

    fun updateCoverPathForOpusLazy(oldPath: String, newPath: String) {
        inTransaction {
            db.localTrackQueries.updateCoverPathByOld(newPath, oldPath)
            db.localAlbumQueries.updateCoverPathByOld(newPath, oldPath)
        }
    }
}
