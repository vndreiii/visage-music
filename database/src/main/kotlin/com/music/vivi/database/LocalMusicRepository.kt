package com.music.vivi.database

/**
 * Indexed local music library (user-chosen folders).
 */
class LocalMusicRepository(
    private val db: ViviDatabase,
    /** Needed for the FTS5 index (not expressible in .sq files); search degrades to LIKE without it. */
    private val driver: app.cash.sqldelight.db.SqlDriver? = null,
) {
    data class KnownFile(val path: String, val size: Long, val mtimeMs: Long, val coverPath: String?)
    data class Counted<T>(val value: T, val trackCount: Long)

    /** Files already indexed with their size/mtime, so a rescan can skip unchanged ones. */
    fun knownFiles(): List<KnownFile> =
        db.localTrackQueries.selectKnownFiles().executeAsList()
            .map { KnownFile(it.file_path, it.file_size ?: 0, it.file_mtime_ms ?: 0, it.cover_path) }

    fun setTrackExtra(
        id: String,
        albumArtist: String?,
        genre: String?,
        composer: String?,
        year: Long?,
        discNumber: Long?,
        fileSize: Long?,
        fileMtimeMs: Long?,
    ) = db.localTrackQueries.updateExtra(albumArtist, genre, composer, year, discNumber, fileSize, fileMtimeMs, id)

    /** Replace all artist credits of a track (ordered as in the tags). */
    fun setTrackArtists(trackId: String, artists: List<Pair<String, String>>) {
        db.localTrackArtistQueries.deleteForTrack(trackId)
        artists.forEachIndexed { i, (artistId, name) ->
            upsertArtist(artistId, name)
            db.localTrackArtistQueries.insert(trackId, artistId, i.toLong())
        }
    }

    fun artistsForTrack(trackId: String) = db.localTrackArtistQueries.selectArtistsForTrack(trackId).executeAsList()
    fun tracksCreditedTo(artistId: String) = db.localTrackArtistQueries.selectTracksForArtist(artistId).executeAsList()

    fun genres(): List<Counted<String>> =
        db.localTrackQueries.selectGenres().executeAsList().map { Counted(it.genre.orEmpty(), it.track_count) }

    fun tracksByGenre(genre: String) = db.localTrackQueries.selectByGenre(genre).executeAsList()

    fun years(): List<Counted<Long>> =
        db.localTrackQueries.selectYears().executeAsList().mapNotNull { r -> r.year?.let { Counted(it, r.track_count) } }

    fun tracksByYear(year: Long) = db.localTrackQueries.selectByYear(year).executeAsList()

    /**
     * Ranked full-text search over title/artist/album/genre (prefix match per word).
     * Falls back to LIKE when FTS5 isn't available.
     */
    fun search(query: String, limit: Int = 30): List<Local_track> {
        val words = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        val ids = driver?.let { d ->
            runCatching {
                val match = words.joinToString(" ") { "\"${it.replace("\"", "")}\"*" }
                d.executeQuery(
                    null,
                    """
                    SELECT local_track.id FROM local_track_fts
                    JOIN local_track ON local_track.rowid = local_track_fts.rowid
                    WHERE local_track_fts MATCH ?
                    ORDER BY bm25(local_track_fts, 8.0, 4.0, 2.0, 1.0)
                    LIMIT ?
                    """.trimIndent(),
                    { cursor ->
                        val out = ArrayList<String>()
                        while (cursor.next().value) cursor.getString(0)?.let(out::add)
                        app.cash.sqldelight.db.QueryResult.Value(out as List<String>)
                    },
                    2,
                ) {
                    bindString(0, match)
                    bindLong(1, limit.toLong())
                }.value
            }.getOrNull()
        }
        if (ids != null) return ids.mapNotNull { trackById(it) }
        return allTracks().filter { t ->
            val hay = "${t.title} ${t.artist_name} ${t.album_title} ${t.genre.orEmpty()}".lowercase()
            words.all { it in hay }
        }.take(limit)
    }

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
