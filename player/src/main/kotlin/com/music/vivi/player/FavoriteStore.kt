package com.music.vivi.player

/**
 * Optional local source of truth for whether a video is liked.
 * Wired from [com.music.vivi.database.LibraryRepository] in the app module.
 */
fun interface FavoriteStore {
    fun isLiked(videoId: String): Boolean

    fun setLiked(videoId: String, liked: Boolean) {}
}
