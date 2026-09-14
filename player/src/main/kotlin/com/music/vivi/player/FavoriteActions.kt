package com.music.vivi.player

import com.music.innertube.YouTube
import com.music.vivi.player.session.SessionSecretStore

object FavoriteActions {
    /**
     * Applies the keyring session (if present) and likes/unlikes [videoId] via Innertube.
     */
    suspend fun setLiked(videoId: String, like: Boolean) {
        SessionSecretStore.loadSession()?.applyToYouTube()
            ?: error("No keyring session — log in via :desktop first")
        YouTube.likeVideo(videoId, like).getOrThrow()
    }
}
