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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import com.music.vivi.compose.ui.player.background.CanvasVideo
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
    private val assetIdentities = ConcurrentHashMap<String, String>()

    /** Shared lookup layer: single-flight, TTL cache, retries and the UI-facing state machine. */
    val repository: CanvasRepository by lazy { CanvasRepository(ProviderCanvasFetcher) }

    fun playbackCacheKey(url: String): String = assetIdentities[url] ?: url

    private fun register(art: CanvasArtwork?) {
        val identity = art?.canvasKey ?: return
        listOfNotNull(art.animated, art.animatedTall, art.videoUrl).forEach { url ->
            // Signed query parameters can rotate without changing the canvas.
            assetIdentities[url] = "$identity|${url.substringBefore('?')}"
        }
    }

    /** Same contract as before (null = nothing to show), now backed by [repository]. */
    suspend fun resolve(
        title: String,
        artist: String,
        album: String? = null,
        source: CanvasSource = CanvasSource.AUTO,
    ): CanvasArtwork? = repository.artwork(CanvasQuery(title, artist, album, source))

    /** Square motion artwork — fits the cover slot. */
    fun squareUrl(art: CanvasArtwork?): String? =
        art.also { register(it) }?.animated?.takeIf { it.isNotBlank() }
            ?: art?.videoUrl?.takeIf { it.isNotBlank() }
            ?: art?.animatedTall?.takeIf { it.isNotBlank() }

    /** Portrait motion artwork — fits tall panes and full-bleed backgrounds. */
    fun tallUrl(art: CanvasArtwork?): String? =
        art.also { register(it) }?.animatedTall?.takeIf { it.isNotBlank() } ?: squareUrl(art)

    fun videoUrl(art: CanvasArtwork?): String? =
        art.also { register(it) }?.animatedTall?.takeIf { it.isNotBlank() }
            ?: art?.animated?.takeIf { it.isNotBlank() }
            ?: art?.videoUrl?.takeIf { it.isNotBlank() }
            ?: art?.preferredAnimationUrl
}

/** The real lookup: the Rust helper when present, else the Kotlin providers. Failures surface as retryable. */
internal object ProviderCanvasFetcher : CanvasFetcher {
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun fetch(query: CanvasQuery): CanvasFetch {
        val storefront = Locale.getDefault().country.lowercase(Locale.ROOT)
            .takeIf { it.length == 2 } ?: "us"
        val art = try {
            if (NativeCanvas.available) nativeLookup(query, storefront) else kotlinLookup(query, storefront)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            System.err.println("CanvasResolver: ${e.javaClass.simpleName}: ${e.message}")
            return CanvasFetch.Transient(e)
        }
        return if (art != null) CanvasFetch.Found(art) else CanvasFetch.NotFound
    }

    private suspend fun nativeLookup(q: CanvasQuery, storefront: String): CanvasArtwork? {
        // The helper cannot say "failed" apart from "nothing found", but it only caches clean misses on
        // disk, so asking again is free for a real miss and a genuine retry after a network failure.
        // Run on a detached scope: the blocking call cannot be interrupted, only abandoned on timeout.
        repeat(2) { n ->
            val found = io.async { callNative(q, storefront) }.await()
            if (found != null) return found
            if (n == 0) delay(700)
        }
        return null
    }

    private fun callNative(q: CanvasQuery, storefront: String): CanvasArtwork? = NativeCanvas.resolve(
        title = q.title,
        artist = q.artist,
        album = q.album,
        source = nativeSource(q.source),
        storefront = storefront,
    )

    private suspend fun kotlinLookup(q: CanvasQuery, storefront: String): CanvasArtwork? {
        val albumTitle = q.album.orEmpty()
        val cleanTitle = q.title.replace(Regex("(?i)\\s*[\\[\\(].*?[\\]\\)]"), "").split(" - ").first().trim()
        val artist = q.artist
        fun CanvasArtwork?.usable() = this?.takeIf { !CanvasResolver.videoUrl(it).isNullOrBlank() }
        return when (q.source) {
            CanvasSource.AUTO ->
                AppleMusicCanvasProvider.getBySongArtist(cleanTitle, artist, albumTitle, storefront).usable()
                    ?: TidalCanvasProvider.getBySongArtist(cleanTitle, artist, albumTitle).usable()
                    ?: ViviMusicCanvasProvider.getBySongArtist(cleanTitle, artist, albumTitle).usable()
            CanvasSource.APPLE_MUSIC ->
                AppleMusicCanvasProvider.getBySongArtist(cleanTitle, artist, albumTitle, storefront).usable()
            CanvasSource.VIVIMUSIC -> ViviMusicCanvasProvider.getBySongArtist(cleanTitle, artist, albumTitle).usable()
            CanvasSource.TIDAL -> TidalCanvasProvider.getBySongArtist(cleanTitle, artist, albumTitle).usable()
        }
    }
}

internal fun nativeSource(source: CanvasSource): String = when (source) {
    CanvasSource.AUTO -> "auto"
    CanvasSource.APPLE_MUSIC -> "apple"
    CanvasSource.VIVIMUSIC -> "vivi"
    CanvasSource.TIDAL -> "tidal"
}

/**
 * Resolves canvases ahead of time, independent of which screen is showing: the current track at once and
 * the next [LOOKAHEAD] queue tracks right behind it (at most [PREFETCH_CONCURRENCY] lookups/decoders
 * at a time). Each result is handed to the frame-cache warmer as soon as it exists.
 */
object CanvasPrefetcher {
    const val LOOKAHEAD = 2
    const val PREFETCH_CONCURRENCY = 2

    suspend fun run(controller: PlaybackController, prefs: DesktopPrefs) {
        combine(controller.nowPlaying, controller.queueTracks, controller.queueIndex) { np, queue, index ->
            listOfNotNull(np?.let { Triple(it.videoId, it.title to it.artist, it.album) }) +
                queue.drop(index + 1).take(LOOKAHEAD).map { Triple(it.videoId, it.title to it.artist, it.album) }
        }
            .distinctUntilChangedBy { list -> list.map { it.first } }
            .collectLatest { tracks ->
                if (!prefs.getBoolean(PrefKeys.EnableCanvas, true)) return@collectLatest
                val source = runCatching {
                    CanvasSource.valueOf(prefs.getString(PrefKeys.CanvasSource, CanvasSource.AUTO.name))
                }.getOrDefault(CanvasSource.AUTO)
                val useGpu = prefs.getBoolean(PrefKeys.CanvasUseGpu, false)
                val pingPong = prefs.getBoolean(PrefKeys.CanvasPingPong, false)
                val gate = Semaphore(PREFETCH_CONCURRENCY)
                coroutineScope {
                    tracks.filter { it.second.first.isNotBlank() && it.second.second.isNotBlank() }.forEach { t ->
                        launch {
                            val art = gate.withPermit { CanvasResolver.resolve(t.second.first, t.second.second, t.third, source) }
                                ?: return@launch
                            listOfNotNull(CanvasResolver.squareUrl(art), CanvasResolver.tallUrl(art)).distinct().forEach { url ->
                                launch { gate.withPermit { CanvasVideo.warm(url, useGpu, pingPong) } }
                            }
                        }
                    }
                }
            }
    }
}
