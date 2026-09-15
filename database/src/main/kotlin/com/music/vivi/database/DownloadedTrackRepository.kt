package com.music.vivi.database

/**
 * App-managed offline downloads (yt-dlp saves into the managed offline dir).
 */
class DownloadedTrackRepository(
    private val db: ViviDatabase,
) {
    fun upsert(
        videoId: String,
        title: String,
        artist: String = "",
        album: String? = null,
        durationSec: Long? = null,
        filePath: String,
        coverPath: String? = null,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        db.downloadedTrackQueries.upsert(
            video_id = videoId,
            title = title,
            artist = artist,
            album = album,
            duration_sec = durationSec,
            file_path = filePath,
            cover_path = coverPath,
            downloaded_at_ms = nowMs,
        )
    }

    fun all() = db.downloadedTrackQueries.selectAll().executeAsList()

    fun get(videoId: String) = db.downloadedTrackQueries.selectByVideoId(videoId).executeAsOneOrNull()

    fun remove(videoId: String) = db.downloadedTrackQueries.deleteByVideoId(videoId)

    fun clear() = db.downloadedTrackQueries.deleteAll()

    fun count() = db.downloadedTrackQueries.count().executeAsOne()
}
