package com.music.vivi.compose.ui.player.canvas

import com.music.vivi.applecanvas.AppleMusicCanvasProvider
import com.music.vivi.canvas.CanvasArtwork
import com.music.vivi.canvas.NativeCanvas
import com.music.vivi.canvas.TidalCanvasProvider
import com.music.vivi.vivimusiccanvas.ViviMusicCanvasProvider
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.PrefKeys
import com.music.vivi.player.PlaybackController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

enum class CanvasSource {
    AUTO,
    APPLE_MUSIC,
    VIVIMUSIC,
    TIDAL,
}

/**
 * Resolve Apple/Tidal/Visage canvas animation URLs (cover slot, lyrics pane and the Apple Music background).
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
        if (NativeCanvas.available) {
            // Rust path: sources run concurrently, results cached on disk across sessions.
            val native = NativeCanvas.resolve(
                title = title,
                artist = artist,
                album = album,
                source = when (source) {
                    CanvasSource.AUTO -> "auto"
                    CanvasSource.APPLE_MUSIC -> "apple"
                    CanvasSource.VIVIMUSIC -> "vivi"
                    CanvasSource.TIDAL -> "tidal"
                },
                storefront = storefront,
            )
            // Misses are cached by the helper (clean misses only), so network failures get retried.
            if (native != null) cache[key] = native
            return@runCatching native
        }
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

/**
 * Resolves canvases ahead of time, independent of which screen is showing: the current track as
 * soon as it starts and, a moment later, the next one in the queue. The Now Playing screen then
 * finds the result already cached (memory + the helper's disk cache) instead of fetching on open.
 */
object CanvasPrefetcher {
    suspend fun run(controller: PlaybackController, prefs: DesktopPrefs) {
        combine(controller.nowPlaying, controller.queueTracks, controller.queueIndex) { np, queue, index ->
            np to queue.getOrNull(index + 1)
        }
            .distinctUntilChangedBy { (np, next) -> np?.videoId to next?.videoId }
            .collectLatest { (np, next) ->
                if (!prefs.getBoolean(PrefKeys.EnableCanvas, true)) return@collectLatest
                val source = runCatching {
                    CanvasSource.valueOf(prefs.getString(PrefKeys.CanvasSource, CanvasSource.AUTO.name))
                }.getOrDefault(CanvasSource.AUTO)
                withContext(Dispatchers.IO) {
                    if (np != null && np.title.isNotBlank()) {
                        CanvasResolver.resolve(np.title, np.artist, np.album, source)
                    }
                    delay(1_500) // the playing track first, then warm the next one quietly
                    if (next != null && next.title.isNotBlank() && next.artist.isNotBlank()) {
                        CanvasResolver.resolve(next.title, next.artist, next.album, source)
                    }
                }
            }
    }
}
