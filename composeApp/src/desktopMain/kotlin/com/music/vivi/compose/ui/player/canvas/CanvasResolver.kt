package com.music.vivi.compose.ui.player.canvas

import com.music.vivi.applecanvas.AppleMusicCanvasProvider
import com.music.vivi.canvas.CanvasArtwork
import com.music.vivi.canvas.TidalCanvasProvider
import com.music.vivi.vivimusiccanvas.ViviMusicCanvasProvider
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

enum class CanvasSource {
    AUTO,
    APPLE_MUSIC,
    VIVIMUSIC,
    TIDAL,
}

/**
 * Resolve Apple/Tidal/Vivi canvas animation URLs for APPLE_MUSIC player background.
 */
object CanvasResolver {
    private val cache = ConcurrentHashMap<String, CanvasArtwork>()
    private val misses = ConcurrentHashMap.newKeySet<String>()

    suspend fun resolve(
        title: String,
        artist: String,
        album: String? = null,
        source: CanvasSource = CanvasSource.AUTO,
    ): CanvasArtwork? = runCatching {
        val key = "${source.name}|$title|$artist|${album.orEmpty()}"
        cache[key]?.let { return@runCatching it }
        if (key in misses) return@runCatching null
        val storefront = Locale.getDefault().country.lowercase(Locale.ROOT)
            .takeIf { it.length == 2 } ?: "us"
        val albumTitle = album.orEmpty()
        val fetched = when (source) {
            CanvasSource.AUTO -> {
                AppleMusicCanvasProvider.getBySongArtist(title, artist, albumTitle, storefront)
                    ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() && matches(it, title, artist, albumTitle) }
                    ?: TidalCanvasProvider.getBySongArtist(title, artist, albumTitle)
                        ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() && matches(it, title, artist, albumTitle) }
                    ?: ViviMusicCanvasProvider.getBySongArtist(title, artist, albumTitle)
                        ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() && matches(it, title, artist, albumTitle) }
            }
            CanvasSource.APPLE_MUSIC ->
                AppleMusicCanvasProvider.getBySongArtist(title, artist, albumTitle, storefront)
                    ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
            CanvasSource.VIVIMUSIC ->
                ViviMusicCanvasProvider.getBySongArtist(title, artist, albumTitle)
                    ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
            CanvasSource.TIDAL ->
                TidalCanvasProvider.getBySongArtist(title, artist, albumTitle)
                    ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
        }
        if (fetched != null) cache[key] = fetched else misses.add(key)
        fetched
    }.onFailure { e ->
        System.err.println("CanvasResolver: ${e.message}")
    }.getOrNull()


    fun videoUrl(art: CanvasArtwork?): String? =
        art?.animatedTall?.takeIf { it.isNotBlank() }
            ?: art?.animated?.takeIf { it.isNotBlank() }
            ?: art?.videoUrl?.takeIf { it.isNotBlank() }
            ?: art?.preferredAnimationUrl

    private fun matches(
        art: CanvasArtwork,
        title: String,
        artist: String,
        album: String,
    ): Boolean {
        val aOk = art.artist.isNullOrBlank() ||
            art.artist!!.trim().equals(artist.trim(), ignoreCase = true) ||
            artist.contains(art.artist!!.trim(), ignoreCase = true) ||
            art.artist!!.contains(artist.trim(), ignoreCase = true)
        val tOk = art.name.isNullOrBlank() ||
            art.name!!.trim().equals(title.trim(), ignoreCase = true) ||
            title.contains(art.name!!.trim(), ignoreCase = true)
        val alOk = album.isBlank() || art.albumName.isNullOrBlank() ||
            art.albumName!!.trim().equals(album.trim(), ignoreCase = true)
        return aOk && tOk && alOk
    }
}
