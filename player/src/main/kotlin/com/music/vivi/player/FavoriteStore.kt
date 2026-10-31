package com.music.vivi.player

/**
 * Optional local source of truth for whether a video is liked.
 * Wired from [com.music.vivi.database.LibraryRepository] in the app module.
 */
fun interface FavoriteStore {
    fun isLiked(videoId: String): Boolean

    fun setLiked(videoId: String, liked: Boolean) {}

    /** Emits when any like changes (here or by a library sync), so the player can re-read the current track's heart. */
    val changes: kotlinx.coroutines.flow.Flow<Any?>? get() = null
}
