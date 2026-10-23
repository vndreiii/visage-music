package com.music.vivi.database

/** Lyrics already fetched for a track (or just its sync offset when [lyrics] is null). */
data class CachedLyrics(
    val provider: String?,
    val lyrics: String?,
    /** Per-song lyrics delay in ms; positive shows the lyrics later. */
    val offsetMs: Int,
)

/**
 * Per-track lyrics cache tied to the per-song sync offset. [putLyrics] replaces the stored
 * provider + text (the user's last choice) and never touches the offset.
 */
class LyricsCacheRepository(private val db: VisageDatabase) {
    private val q get() = db.lyricsCacheQueries

    fun get(key: String): CachedLyrics? = q.selectByKey(key).executeAsOneOrNull()?.let {
        CachedLyrics(it.provider, it.lyrics?.takeIf { text -> text.isNotBlank() }, it.offset_ms.toInt())
    }

    fun putLyrics(key: String, provider: String, lyrics: String, nowMs: Long = System.currentTimeMillis()) {
        q.transaction {
            q.insertIgnore(key, nowMs)
            q.updateLyrics(provider, lyrics, nowMs, key)
        }
    }

    fun setOffset(key: String, offsetMs: Int, nowMs: Long = System.currentTimeMillis()) {
        q.transaction {
            q.insertIgnore(key, nowMs)
            q.updateOffset(offsetMs.toLong(), nowMs, key)
        }
    }

    /** Drops every cached lyrics text but keeps the per-song offsets. */
    fun purgeLyrics() {
        q.transaction {
            q.purgeLyrics()
            q.deleteEmpty()
        }
    }

    fun cachedCount(): Long = q.countLyrics().executeAsOne()
}
