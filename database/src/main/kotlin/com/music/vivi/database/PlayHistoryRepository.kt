package com.music.vivi.database

/**
 * Local play history for Recently Played (queue panel + history screens).
 */
class PlayHistoryRepository(
    private val db: ViviDatabase,
) {
    private var lastSongId: String? = null
    private var lastRecordedAt: Long = 0L

    /** Record a play; ignores repeats of the same song within [debounceMs]. */
    fun recordPlay(
        songId: String,
        title: String,
        artists: String = "",
        thumbnail: String? = null,
        durationSec: Long? = null,
        nowMs: Long = System.currentTimeMillis(),
        debounceMs: Long = 30_000L,
    ) {
        if (songId == lastSongId && nowMs - lastRecordedAt < debounceMs) return
        lastSongId = songId
        lastRecordedAt = nowMs
        withSqliteBusyRetry {
            db.playEventQueries.insert(
                song_id = songId,
                title = title,
                artists = artists,
                thumbnail = thumbnail,
                duration_sec = durationSec,
                played_at_ms = nowMs,
            )
        }
    }

    fun recentPlays(limit: Long = 50) =
        db.playEventQueries.selectRecent(limit).executeAsList()

    fun recentDistinct(limit: Long = 50) =
        db.playEventQueries.selectRecentDistinct(limit).executeAsList()

    fun clear() = db.playEventQueries.clearAll()
}
