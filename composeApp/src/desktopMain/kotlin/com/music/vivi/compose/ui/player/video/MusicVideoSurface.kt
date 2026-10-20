package com.music.vivi.compose.ui.player.video

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
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
    val quality = VideoQuality.parse(prefs.getString(PrefKeys.PlayerVideoQuality, VideoQuality.AUTO.name))
    val id = track?.videoId
    val eligible = enabled && VisualChain.hasVideoCandidate(id, track?.localPath)
    // Seeded from the resolver cache so a second view of the same track (fullscreen) shows the video at once.
    val state = produceState(
        if (eligible && id != null) MusicVideoResolver.cached(id, quality) else null,
        id, eligible, quality,
    ) {
        if (!eligible || id == null) { value = null; return@produceState }
        val hit = MusicVideoResolver.cached(id, quality)
        if (hit != null) { value = hit; return@produceState }
        value = null
        value = MusicVideoResolver.resolve(id, quality)
    }
    return if (eligible) state.value else null
}

private class VideoFrameSink : FrameSink {
    var frame by mutableStateOf<CanvasFrame?>(null)
    @Volatile var positionSec: Double? = null
    @Volatile var framesRendered = 0L
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
        framesRendered++
        next = (next + 1) % ring.size
        frame = CanvasFrame(bmp.asComposeImageBitmap(), ++generation)
    }

    override fun onPosition(positionSec: Double, durationSec: Double) {
        this.positionSec = positionSec
        this.durationSec = durationSec
    }
}

/** One mpv instance and frame buffer per stream, shared by every surface showing it (cover card, fullscreen). */
private class SharedVideo(val sink: VideoFrameSink, val renderer: MpvVideoRenderer?) {
    var refs = 0
    var closeTimer: java.util.TimerTask? = null
}

private object VideoSessions {
    private val live = HashMap<String, SharedVideo>()
    private val timer = java.util.Timer("video-session-close", true)
    /** Keeps a stream alive briefly after its last surface leaves, so a view switch can pick it back up. */
    private const val GRACE_MS = 3_000L

    @Synchronized
    fun acquire(url: String, create: () -> SharedVideo): SharedVideo {
        val s = live.getOrPut(url, create)
        s.closeTimer?.cancel(); s.closeTimer = null
        s.refs++
        return s
    }

    @Synchronized
    fun release(url: String) {
        val s = live[url] ?: return
        if (--s.refs > 0) return
        s.closeTimer = object : java.util.TimerTask() {
            override fun run() {
                synchronized(this@VideoSessions) {
                    if (s.refs == 0 && live[url] === s) {
                        live.remove(url)
                        s.renderer?.close()
                    }
                }
            }
        }
        timer.schedule(s.closeTimer, GRACE_MS)
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
    var shared by remember(video.url) { mutableStateOf<SharedVideo?>(null) }

    DisposableEffect(video.url) {
        shared = VideoSessions.acquire(video.url) {
            val sink = VideoFrameSink()
            val r = runCatching {
                MpvVideoRenderer(
                    url = video.url,
                    sink = sink,
                    maxEdge = 960,
                    httpHeaders = video.httpHeaders,
                    useGpu = prefs.getBoolean(PrefKeys.CanvasUseGpu, false),
                    startHeld = !playing,
                    startSec = controller.positionMs.value / 1000.0,
                    backCacheMiB = 24,
                )
            }.onFailure { System.err.println("MusicVideo: ${it.message}") }.getOrNull()
            SharedVideo(sink, r)
        }
        onDispose { VideoSessions.release(video.url); shared = null }
    }
    val sink = shared?.sink
    val renderer = shared?.renderer

    var slot by remember { mutableStateOf(IntSize.Zero) }
    // Render at roughly the size it is shown: uploading full 1080p frames to a small card every frame is
    // what makes the rest of the UI lag.
    val hasSlot = slot.width > 0
    LaunchedEffect(renderer, slot) {
        val r = renderer ?: return@LaunchedEffect
        if (!hasSlot) return@LaunchedEffect
        val edge = maxOf(slot.width, slot.height)
        r.maxEdge = (((edge + 159) / 160) * 160).coerceIn(480, (video.height * 16 / 9).coerceIn(640, 1920))
    }

    LaunchedEffect(renderer) {
        val r = renderer ?: return@LaunchedEffect
        val sink = shared?.sink ?: return@LaunchedEffect
        r.setHeld(false)
        var lastSeekAt = 0L
        // VISAGE_VIDEO_DEBUG=1: print seeks, drift and frame rate to stderr to diagnose stutter.
        val debug = System.getenv("VISAGE_VIDEO_DEBUG") == "1"
        var seeks = 0
        var consecutiveSeeks = 0
        var lastFrames = sink.framesRendered
        var lastFrameAt = System.nanoTime()
        var statAt = System.nanoTime()
        var statFrames = sink.framesRendered
        while (true) {
            val now = System.nanoTime()
            if (debug && now - statAt > 5_000_000_000L) {
                val fps = (sink.framesRendered - statFrames) * 1e9 / (now - statAt)
                val v = sink.positionSec
                val driftMs = v?.let { ((controller.positionMs.value / 1000.0 - it) * 1000).toInt() }
                System.err.println("MusicVideo[debug] ${video.height}p fps=${"%.1f".format(fps)} seeks=$seeks driftMs=$driftMs")
                statAt = now; statFrames = sink.framesRendered; seeks = 0
            }
            val decision = VideoSync.decide(
                audioSec = controller.positionMs.value / 1000.0,
                videoSec = sink.positionSec,
                audioPlaying = controller.state.value == PlaybackState.Playing,
                durationSec = sink.durationSec,
            )
            r.setPaused(decision.videoPaused)
            val action = decision.action
            val frames = sink.framesRendered
            if (frames != lastFrames) { lastFrames = frames; lastFrameAt = now }
            val stalledNs = now - lastFrameAt
            // Seeking a stream that is still buffering restarts the download and makes the lag worse (stutter
            // loop), so seek only while frames are flowing, and back off exponentially when seeks keep recurring.
            val cooldownNs = minOf(10_000_000_000L, 700_000_000L shl consecutiveSeeks.coerceAtMost(4))
            val buffering = !decision.videoPaused && stalledNs > 1_000_000_000L && stalledNs < 8_000_000_000L
            if (action is SyncAction.None && now - lastSeekAt > 3_000_000_000L) consecutiveSeeks = 0
            // After a seek the renderer reports stale positions until a new frame lands.
            if (action is SyncAction.Seek && !buffering && now - lastSeekAt > cooldownNs) {
                lastSeekAt = now
                seeks++
                consecutiveSeeks++
                sink.positionSec = null
                r.seekTo(action.toSec)
            }
            delay(200)
        }
    }

    val hasFrame = sink?.frame != null
    LaunchedEffect(hasFrame) { if (hasFrame) sink?.let { onFirstFrame?.invoke(it.aspect) } }
    val alpha by animateFloatAsState(if (hasFrame) 1f else 0f, VisageMotion.fade(500), label = "musicVideoFade")
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier.hoverable(hoverSource).onSizeChanged { slot = it }) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { this.alpha = alpha }
                .background(Color.Black)
                .drawBehind {
                    val f = sink?.frame ?: return@drawBehind
                    drawCropped(f.image, if (framing == VideoFraming.Fit) ContentScale.Fit else ContentScale.Crop, 1f)
                },
        )
        AnimatedVisibility(
            // Opening the menu moves the pointer onto its popup, which ends the hover; keep the chip (and the
            // menu it hosts) alive while the menu is open.
            visible = (hovered && hasFrame) || menuOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            // The cover is clipped with a large corner radius, so keep the chip clear of the curve.
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 20.dp, end = 20.dp),
        ) {
            VideoQualityChip(prefs, playingHeight = video.height, open = menuOpen, onOpenChange = { menuOpen = it })
        }
    }
}

/** Quality picker shown over the playing video; writes the same preference as Settings. */
@Composable
private fun VideoQualityChip(prefs: DesktopPrefs, playingHeight: Int, open: Boolean, onOpenChange: (Boolean) -> Unit) {
    val selected = VideoQuality.parse(prefs.getString(PrefKeys.PlayerVideoQuality, VideoQuality.AUTO.name))
    Box {
        Surface(
            onClick = { onOpenChange(true) },
            shape = RoundedCornerShape(50),
            color = Color.Black.copy(alpha = 0.6f),
            contentColor = Color.White,
        ) {
            Text(
                if (selected == VideoQuality.AUTO) "Auto \u00b7 ${playingHeight}p" else selected.label,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { onOpenChange(false) }) {
            VideoQuality.entries.forEach { q ->
                DropdownMenuItem(
                    text = { Text(if (q == selected) "${q.label}  \u2713" else q.label) },
                    onClick = {
                        prefs.setString(PrefKeys.PlayerVideoQuality, q.name)
                        onOpenChange(false)
                    },
                )
            }
        }
    }
}
