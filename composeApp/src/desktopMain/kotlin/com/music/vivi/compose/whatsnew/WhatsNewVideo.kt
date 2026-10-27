package com.music.vivi.compose.whatsnew

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.music.vivi.player.mpv.FrameSink
import com.music.vivi.player.mpv.MpvVideoRenderer
import com.music.vivi.player.mpv.PixelTarget
import com.music.vivi.player.video.VideoQuality
import com.music.vivi.player.video.VideoStreamResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import java.net.HttpURLConnection
import java.net.URI

private class VideoSource(val url: String, val headers: Map<String, String>)

private sealed interface VideoState {
    data object Loading : VideoState
    data object Unavailable : VideoState
    class Playing(val frame: ImageBitmap) : VideoState
}

/** Frames decoded by mpv into a small bitmap ring; the newest one is published as Compose state. */
private class WhatsNewFrames : FrameSink {
    var frame by mutableStateOf<ImageBitmap?>(null)
        private set
    var onEnd: () -> Unit = {}
    private val ring = arrayOfNulls<Bitmap>(3)
    private var ringW = 0
    private var ringH = 0
    private var writeIndex = 0

    override fun lock(width: Int, height: Int): PixelTarget? {
        if (width != ringW || height != ringH) {
            for (i in ring.indices) {
                ring[i] = Bitmap().apply {
                    allocPixels(ImageInfo(width, height, ColorType.RGB_888X, ColorAlphaType.OPAQUE))
                }
            }
            ringW = width
            ringH = height
        }
        val pixmap = ring[writeIndex]?.peekPixels() ?: return null
        return PixelTarget(pixmap.addr, width, height, pixmap.rowBytes)
    }

    override fun unlock(target: PixelTarget, rendered: Boolean) {
        if (!rendered) return
        val bmp = ring[writeIndex] ?: return
        bmp.notifyPixelsChanged()
        writeIndex = (writeIndex + 1) % ring.size
        frame = bmp.asComposeImageBitmap()
    }

    override fun onPlaybackEnd() = onEnd()
}

/**
 * The walkthrough clip for [feature]: the CDN file first, then the YouTube upload (resolved to a plain stream and
 * rendered in-app like everything else), and a "no connection" card when neither plays. Muted, looping, and parked
 * while [active] is false so only the visible page decodes.
 */
@Composable
fun WhatsNewVideo(version: String, feature: WhatsNewFeature, active: Boolean, modifier: Modifier = Modifier) {
    val frames = remember(feature.slug) { WhatsNewFrames() }
    var failed by remember(feature.slug) { mutableStateOf(false) }
    var renderer by remember(feature.slug) { mutableStateOf<MpvVideoRenderer?>(null) }
    val activeNow by rememberUpdatedState(active)

    // Only start decoding once the page has been visible; the pager keeps neighbours composed.
    var everActive by remember(feature.slug) { mutableStateOf(active) }
    if (active) everActive = true

    LaunchedEffect(feature.slug, everActive) {
        if (!everActive) return@LaunchedEffect
        val sources = listOfNotNull(
            WhatsNewConfig.cdnUrl(version, feature.slug)?.takeIf { reachable(it) }?.let { VideoSource(it, emptyMap()) },
        )
        suspend fun tryPlay(source: VideoSource): Boolean {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    MpvVideoRenderer(
                        url = source.url,
                        sink = frames,
                        maxEdge = 960,
                        httpHeaders = source.headers,
                        backCacheMiB = 32,
                    )
                }.getOrNull()
            } ?: return false
            frames.onEnd = { r.rewind() }
            r.setPaused(!activeNow)
            renderer = r
            val first = withTimeoutOrNull(FIRST_FRAME_TIMEOUT_MS) {
                while (frames.frame == null) delay(50)
                true
            }
            if (first == true) return true
            renderer = null
            r.close()
            return false
        }
        for (s in sources) if (tryPlay(s)) return@LaunchedEffect
        val yt = feature.youtubeId
        if (yt != null) {
            val resolved = withTimeoutOrNull(20_000L) {
                runCatching { VideoStreamResolver.resolve(yt, VideoQuality.P720) }.getOrNull()
            }
            if (resolved != null && tryPlay(VideoSource(resolved.url, resolved.httpHeaders))) return@LaunchedEffect
        }
        failed = true
    }

    LaunchedEffect(active, renderer) { renderer?.setPaused(!active) }
    DisposableEffect(feature.slug) {
        onDispose {
            val r = renderer
            renderer = null
            Thread { runCatching { r?.close() } }.start()
        }
    }

    val state: VideoState = frames.frame?.let { VideoState.Playing(it) }
        ?: if (failed) VideoState.Unavailable else VideoState.Loading

    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest), contentAlignment = Alignment.Center) {
        when (state) {
            is VideoState.Playing -> Image(
                bitmap = state.frame,
                contentDescription = feature.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            VideoState.Unavailable -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.WifiOff,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Video unavailable. Read the changelog for how it works.",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            VideoState.Loading -> Unit
        }
    }
}

private const val FIRST_FRAME_TIMEOUT_MS = 10_000L

private suspend fun reachable(url: String): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        c.requestMethod = "HEAD"
        c.connectTimeout = 4_000
        c.readTimeout = 4_000
        c.instanceFollowRedirects = true
        val ok = c.responseCode in 200..299
        c.disconnect()
        ok
    }.getOrDefault(false)
}
