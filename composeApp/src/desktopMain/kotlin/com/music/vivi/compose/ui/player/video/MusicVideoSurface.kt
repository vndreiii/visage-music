package com.music.vivi.compose.ui.player.video

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import com.music.vivi.compose.LocalPlaybackController
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.PrefKeys
import com.music.vivi.compose.ui.motion.VisageMotion
import com.music.vivi.compose.ui.player.background.CanvasFrame
import com.music.vivi.compose.ui.player.background.drawCropped
import com.music.vivi.compose.ui.settings.rememberPrefsVersion
import com.music.vivi.player.NowPlaying
import com.music.vivi.player.PlaybackState
import com.music.vivi.player.mpv.FrameSink
import com.music.vivi.player.mpv.MpvVideoRenderer
import com.music.vivi.player.mpv.PixelTarget
import com.music.vivi.player.video.MusicVideoResolver
import com.music.vivi.player.video.ResolvedVideo
import com.music.vivi.player.video.SyncAction
import com.music.vivi.player.video.VideoQuality
import com.music.vivi.player.video.VideoSync
import com.music.vivi.player.video.VisualChain
import kotlinx.coroutines.delay
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.koin.compose.koinInject

/**
 * The resolved music video for [track] when the "Play music videos" setting is on and the
 * track has one; null otherwise (setting off, local file, no video counterpart, still resolving).
 * Callers use a non-null result to suppress the canvas and show [MusicVideoSurface] instead.
 */
@Composable
fun rememberTrackVideo(track: NowPlaying?): ResolvedVideo? {
    val prefs = koinInject<DesktopPrefs>()
    rememberPrefsVersion(prefs)
    val enabled = prefs.getBoolean(PrefKeys.PlayerVideoEnabled, false)
    val quality = VideoQuality.parse(prefs.getString(PrefKeys.PlayerVideoQuality, VideoQuality.P720.name))
    val id = track?.videoId
    val eligible = enabled && VisualChain.hasVideoCandidate(id, track?.localPath)
    val state = produceState<ResolvedVideo?>(null, id, eligible, quality) {
        value = null
        if (!eligible || id == null) return@produceState
        value = MusicVideoResolver.resolve(id, quality)
    }
    return if (eligible) state.value else null
}

private class VideoFrameSink : FrameSink {
    var frame by mutableStateOf<CanvasFrame?>(null)
    @Volatile var positionSec: Double? = null
    @Volatile var durationSec: Double = 0.0
    private val ring = arrayOfNulls<Bitmap>(3)
    private var w = 0
    private var h = 0
    val aspect: Float get() = VideoFraming.aspectOf(w, h)
    private var next = 0
    private var generation = 0L

    override fun lock(width: Int, height: Int): PixelTarget? {
        if (width != w || height != h) {
            for (i in ring.indices) {
                ring[i] = Bitmap().apply {
                    allocPixels(ImageInfo(width, height, ColorType.RGB_888X, ColorAlphaType.OPAQUE))
                }
            }
            w = width; h = height
        }
        val px = ring[next]?.peekPixels() ?: return null
        return PixelTarget(px.addr, width, height, px.rowBytes)
    }

    override fun unlock(target: PixelTarget, rendered: Boolean) {
        if (!rendered) return
        val bmp = ring[next] ?: return
        bmp.notifyPixelsChanged()
        next = (next + 1) % ring.size
        frame = CanvasFrame(bmp.asComposeImageBitmap(), ++generation)
    }

    override fun onPosition(positionSec: Double, durationSec: Double) {
        this.positionSec = positionSec
        this.durationSec = durationSec
    }
}

/**
 * Silent mpv video slaved to the main player's clock: paused/resumed with it, re-seeked when it
 * drifts past [VideoSync.DRIFT_SEC]. Audio and volume stay entirely with the main player.
 * Fades in after the first frame; Fill crops to the slot, Fit letterboxes on black; Original has a slot of the video's own aspect, so Crop is exact.
 */
@Composable
fun MusicVideoSurface(
    video: ResolvedVideo,
    modifier: Modifier = Modifier,
    onFirstFrame: ((aspect: Float) -> Unit)? = null,
) {
    val prefs = koinInject<DesktopPrefs>()
    rememberPrefsVersion(prefs)
    val framing = rememberVideoFraming(prefs)
    val controller = LocalPlaybackController.current
    val state by controller.state.collectAsState()
    val playing = state == PlaybackState.Playing
    val sink = remember(video.url) { VideoFrameSink() }
    var renderer by remember(video.url) { mutableStateOf<MpvVideoRenderer?>(null) }

    DisposableEffect(video.url) {
        val r = runCatching {
            MpvVideoRenderer(
                url = video.url,
                sink = sink,
                maxEdge = (video.height * 16 / 9).coerceIn(640, 1920),
                httpHeaders = video.httpHeaders,
                startHeld = !playing,
                startSec = controller.positionMs.value / 1000.0,
            )
        }.onFailure { System.err.println("MusicVideo: ${it.message}") }.getOrNull()
        renderer = r
        onDispose { r?.close(); renderer = null }
    }

    LaunchedEffect(renderer) {
        val r = renderer ?: return@LaunchedEffect
        r.setHeld(false)
        var lastSeekAt = 0L
        while (true) {
            val now = System.nanoTime()
            val decision = VideoSync.decide(
                audioSec = controller.positionMs.value / 1000.0,
                videoSec = sink.positionSec,
                audioPlaying = controller.state.value == PlaybackState.Playing,
                durationSec = sink.durationSec,
            )
            r.setPaused(decision.videoPaused)
            val action = decision.action
            // After a seek the renderer reports stale positions until a new frame lands.
            if (action is SyncAction.Seek && now - lastSeekAt > 700_000_000L) {
                lastSeekAt = now
                sink.positionSec = null
                r.seekTo(action.toSec)
            }
            delay(200)
        }
    }

    val hasFrame = sink.frame != null
    LaunchedEffect(hasFrame) { if (hasFrame) onFirstFrame?.invoke(sink.aspect) }
    val alpha by animateFloatAsState(if (hasFrame) 1f else 0f, VisageMotion.fade(500), label = "musicVideoFade")
    Box(
        modifier
            .graphicsLayer { this.alpha = alpha }
            .background(Color.Black)
            .drawBehind {
                val f = sink.frame ?: return@drawBehind
                drawCropped(f.image, if (framing == VideoFraming.Fit) ContentScale.Fit else ContentScale.Crop, 1f)
            },
    )
}
