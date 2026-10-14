package com.music.vivi.compose.ui.player.background

import com.music.vivi.canvas.CanvasArtwork
import com.music.vivi.compose.ui.player.canvas.CanvasResolver
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CanvasCacheIdentityTest {
    @Test
    fun signedUrlsReuseTheSameAlbumAssetAndKeepSongsSeparate() {
        val old = "https://canvas.example/cover.mp4?signature=old"
        val fresh = "https://canvas.example/cover.mp4?signature=fresh"
        CanvasResolver.squareUrl(CanvasArtwork(canvasKey = "apple:album:123", animated = old))
        CanvasResolver.squareUrl(CanvasArtwork(canvasKey = "apple:album:123", animated = fresh))
        assertEquals(CanvasResolver.playbackCacheKey(old), CanvasResolver.playbackCacheKey(fresh))
        val song = "https://canvas.example/song.mp4"
        CanvasResolver.squareUrl(CanvasArtwork(canvasKey = "vivi:song:artist:album", animated = song))
        assertNotEquals(CanvasResolver.playbackCacheKey(fresh), CanvasResolver.playbackCacheKey(song))
    }
}
