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
        val cleanTitle = title.replace(Regex("(?i)\\s*[\\[\\(].*?[\\]\\)]"), "").split(" - ").first().trim()
        val fetched = when (source) {
            CanvasSource.AUTO -> {
                AppleMusicCanvasProvider.getBySongArtist(cleanTitle, artist, albumTitle, storefront)
                    ?.takeIf { !videoUrl(it).isNullOrBlank() }
                    ?: TidalCanvasProvider.getBySongArtist(cleanTitle, artist, albumTitle)
                        ?.takeIf { !videoUrl(it).isNullOrBlank() }
                    ?: ViviMusicCanvasProvider.getBySongArtist(cleanTitle, artist, albumTitle)
                        ?.takeIf { !videoUrl(it).isNullOrBlank() }
            }
            CanvasSource.APPLE_MUSIC ->
                AppleMusicCanvasProvider.getBySongArtist(cleanTitle, artist, albumTitle, storefront)
                    ?.takeIf { !videoUrl(it).isNullOrBlank() }
            CanvasSource.VIVIMUSIC ->
                ViviMusicCanvasProvider.getBySongArtist(cleanTitle, artist, albumTitle)
                    ?.takeIf { !videoUrl(it).isNullOrBlank() }
            CanvasSource.TIDAL ->
                TidalCanvasProvider.getBySongArtist(cleanTitle, artist, albumTitle)
                    ?.takeIf { !videoUrl(it).isNullOrBlank() }
        }
        if (fetched != null) cache[key] = fetched else misses.add(key)
        fetched
    }.onFailure { e ->
        System.err.println("CanvasResolver: ${e.message}")
    }.getOrNull()


    /** Square motion artwork — fits the cover slot. */
    fun squareUrl(art: CanvasArtwork?): String? =
        art?.animated?.takeIf { it.isNotBlank() }
            ?: art?.videoUrl?.takeIf { it.isNotBlank() }
            ?: art?.animatedTall?.takeIf { it.isNotBlank() }

    /** Portrait motion artwork — fits tall panes and full-bleed backgrounds. */
    fun tallUrl(art: CanvasArtwork?): String? =
        art?.animatedTall?.takeIf { it.isNotBlank() } ?: squareUrl(art)

    fun videoUrl(art: CanvasArtwork?): String? =
        art?.animatedTall?.takeIf { it.isNotBlank() }
            ?: art?.animated?.takeIf { it.isNotBlank() }
            ?: art?.videoUrl?.takeIf { it.isNotBlank() }
            ?: art?.preferredAnimationUrl
}
