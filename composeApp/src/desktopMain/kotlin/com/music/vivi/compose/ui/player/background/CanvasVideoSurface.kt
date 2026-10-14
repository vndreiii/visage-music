package com.music.vivi.compose.ui.player.background

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.music.vivi.player.mpv.FrameSink
import com.music.vivi.player.mpv.MpvVideoRenderer
import com.music.vivi.player.mpv.PixelTarget
import com.music.vivi.compose.ui.player.canvas.CanvasResolver
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.PrefKeys
import com.music.vivi.compose.ui.settings.rememberPrefsVersion
import org.koin.compose.koinInject
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.swing.Swing
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.ScheduledFuture
import kotlin.math.roundToInt

/** Latest decoded canvas frame; [generation] changes on every new frame so draws invalidate. */
@Stable
class CanvasFrame(val image: ImageBitmap, val generation: Long)

/** A coherent frame pair: the draw pass never sees a half-finished decoder handoff. */
@Stable
class CanvasPresentation(val frame: CanvasFrame?, val incoming: CanvasFrame?, val blend: Float)

/** Shared dual-decoder canvas loop. The next clip is primed before a short dissolve. */
class CanvasVideo private constructor(val url: String, private val useGpu: Boolean, private val pingPong: Boolean) {
    var presentation by mutableStateOf(CanvasPresentation(null, null, 0f))
        private set
    val frame: CanvasFrame? get() = presentation.frame
    val incoming: CanvasFrame? get() = presentation.incoming
    val blend: Float get() = presentation.blend

    private val lock = Any()
    private var active = 0
    private var refs = 0
    private var pauseVotes = 0
    private var paused = false
    private var crossing = false
    private var crossElapsed = 0.0
    private var crossDuration = CROSSFADE_SEC
    private var lastTickNs = 0L
    private var ticker: ScheduledFuture<*>? = null
    private val slots = arrayOf(Slot(0), Slot(1))
    @Volatile internal var completedLoops = 0
        private set
    internal val cachedFrameCount: Int get() = synchronized(lock) { cached?.frames?.size ?: 0 }

    private val cacheKey = CanvasResolver.playbackCacheKey(url)
    private var cached = CanvasFrameCache.get(cacheKey)
    private var timeline: CanvasFrameTimeline? = cached?.let { CanvasFrameTimeline(it.frames.size, it.interval, pingPong) }
    private var cachePosition = 0.0
    private val recorded = ArrayList<CanvasFrame>()
    private val positions = ArrayList<Double>()
    private var recordedBytes = 0L
    private var cacheTooLarge = false

    init {
        // A cache hit must have a drawable frame during the first composition,
        // rather than waiting for the ticker and starting a 450 ms fade-in.
        cached?.let { clip ->
            val sample = requireNotNull(timeline).sample(0.0)
            presentation = CanvasPresentation(clip.frames[sample.first], clip.frames[sample.second], sample.blend)
        }
    }

    private inner class Slot(val index: Int) : FrameSink {
        var frame: CanvasFrame? = null
        var ended = false
        var latestBitmap: Bitmap? = null
        private val ring = arrayOfNulls<Bitmap>(3)
        private var ringW = 0
        private var ringH = 0
        private var writeIndex = 0
        private var generation = 0L
        var renderer: MpvVideoRenderer? = null

        override fun lock(width: Int, height: Int): PixelTarget? {
            if (width != ringW || height != ringH) {
                // Old bitmaps may still back the displayed ImageBitmap; let GC close them.
                for (i in ring.indices) {
                    ring[i] = Bitmap().apply {
                        // RGBX: the 4th byte is padding, never read as alpha (mpv's "rgb0" leaves it 0).
                        allocPixels(ImageInfo(width, height, ColorType.RGB_888X, ColorAlphaType.OPAQUE))
                    }
                }
                ringW = width
                ringH = height
            }
            val bmp = ring[writeIndex] ?: return null
            val pixmap = bmp.peekPixels() ?: return null
            return PixelTarget(pixmap.addr, width, height, pixmap.rowBytes)
        }

        override fun unlock(target: PixelTarget, rendered: Boolean) {
            if (!rendered) return
            val bmp = ring[writeIndex] ?: return
            bmp.notifyPixelsChanged()
            writeIndex = (writeIndex + 1) % ring.size
            synchronized(this@CanvasVideo.lock) {
                latestBitmap = bmp
                frame = CanvasFrame(bmp.asComposeImageBitmap(), ++generation)
                if (cached == null) publish()
            }
        }

        override fun onPosition(positionSec: Double, durationSec: Double) =
            onSlotFrame(index, positionSec, durationSec)

        override fun onPlaybackEnd() = synchronized(this@CanvasVideo.lock) {
            ended = true
            if (index == active && !crossing) startCrossfade(CROSSFADE_SEC)
        }
    }

    private fun publish() {
        if (cached != null) return
        val t = (crossElapsed / crossDuration).coerceIn(0.0, 1.0).toFloat()
        presentation = CanvasPresentation(
            slots[active].frame,
            if (crossing) slots[1 - active].frame else null,
            if (crossing) t * t * (3f - 2f * t) else 0f,
        )
    }

    private fun onSlotFrame(slot: Int, pos: Double, length: Double) = synchronized(lock) {
        if (cached != null) return@synchronized
        if (slot == 0 && completedLoops == 0 && !cacheTooLarge &&
            (positions.isEmpty() || pos > positions.last() + 0.001)
        ) {
            slots[slot].latestBitmap?.let { bitmap ->
                val size = bitmap.rowBytes.toLong() * bitmap.height
                if (recordedBytes + size <= 256L * 1024 * 1024) {
                    val copy = Bitmap().apply {
                        check(installPixels(bitmap.imageInfo, requireNotNull(bitmap.readPixels()), bitmap.rowBytes))
                        setImmutable()
                    }
                    recorded += CanvasFrame(copy.asComposeImageBitmap(), recorded.size.toLong())
                    positions += pos
                    recordedBytes += size
                } else {
                    cacheTooLarge = true
                    recorded.clear()
                    positions.clear()
                    recordedBytes = 0
                }
            }
        }
        if (!crossing && slot == active && length.isFinite() && length > 0) {
            val fade = minOf(CROSSFADE_SEC, length / 4)
            if (pos >= length - fade) startCrossfade(fade)
        }
    }

    private fun startCrossfade(duration: Double) {
        if (paused || refs <= 0 || slots[1 - active].frame == null) return
        crossing = true
        crossDuration = duration.coerceAtLeast(0.08)
        crossElapsed = 0.0
        lastTickNs = System.nanoTime()
        slots[1 - active].renderer?.setHeld(false)
        publish()
    }

    private fun tick() = synchronized(lock) {
        val now = System.nanoTime()
        if (!paused && refs > 0) {
            val clip = cached
            val clock = timeline
            if (clip != null && clock != null) {
                val before = kotlin.math.floor(cachePosition / clock.period).toInt()
                cachePosition += ((now - lastTickNs) / 1e9).coerceIn(0.0, 0.1) / clip.interval
                completedLoops += kotlin.math.floor(cachePosition / clock.period).toInt() - before
                val sample = clock.sample(cachePosition)
                presentation = CanvasPresentation(clip.frames[sample.first], clip.frames[sample.second], sample.blend)
                lastTickNs = now
                return@synchronized
            }
            if (!crossing && slots[active].ended) startCrossfade(CROSSFADE_SEC)
            if (crossing) {
                crossElapsed += ((now - lastTickNs) / 1e9).coerceIn(0.0, 0.1)
                if (crossElapsed >= crossDuration) finishCrossfade() else publish()
            }
        }
        lastTickNs = now
    }

    private fun finishCrossfade() {
        val old = slots[active]
        active = 1 - active
        crossing = false
        crossElapsed = 0.0
        completedLoops++
        // Publish the fully visible incoming frame before preparing the retired
        // decoder. Its old last frame must never be used for the next dissolve.
        publish()
        if (!cacheTooLarge && recorded.size >= 2) {
            val intervals = positions.zipWithNext { a, b -> b - a }.filter { it > 0.001 }.sorted()
            val interval = (intervals.getOrNull(intervals.size / 2) ?: (1.0 / 24)).coerceIn(1.0 / 120, 0.1)
            val clip = CachedCanvasFrames(recorded.toList(), interval, recordedBytes)
            CanvasFrameCache.put(cacheKey, clip)
            cached = clip
            timeline = CanvasFrameTimeline(clip.frames.size, clip.interval, pingPong)
            cachePosition = if (pingPong) minOf(clip.frames.lastIndex.toDouble(), CROSSFADE_SEC / interval) else 0.0
            for (slot in slots) { slot.renderer?.close(); slot.renderer = null }
            recorded.clear()
            positions.clear()
            return
        }
        old.frame = null
        old.ended = false
        old.renderer?.apply { setHeld(true); rewind() }
    }

    private fun acquire() = synchronized(lock) {
        refs++
        if (ticker == null) {
            if (cached == null) for (slot in slots) {
                slot.renderer = runCatching { MpvVideoRenderer(url, slot, maxEdge = 512, startHeld = slot.index != active, useGpu = useGpu) }
                    .onFailure { System.err.println("CanvasVideo: ${it.message}") }
                    .getOrNull()
            }
            lastTickNs = System.nanoTime()
            ticker = watchdog.scheduleAtFixedRate({ tick() }, 0, 16, TimeUnit.MILLISECONDS)
        }
    }

    private fun release() = synchronized(lock) {
        refs--
        if (refs <= 0) {
            ticker?.cancel(false)
            ticker = null
            for (slot in slots) { slot.renderer?.close(); slot.renderer = null }
            registry.remove(DecoderKey(cacheKey, useGpu, pingPong))
        }
    }

    internal fun votePaused(paused: Boolean, delta: Int) = synchronized(lock) {
        if (paused) pauseVotes += delta
        this.paused = pauseVotes > 0 && pauseVotes >= refs
        lastTickNs = System.nanoTime()
        for (slot in slots) slot.renderer?.setPaused(this.paused)
    }

    companion object {
        /** How long before the end the next loop starts fading in. */
        private const val CROSSFADE_SEC = 0.35
        private val watchdog = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "canvas-loop-watchdog").apply { isDaemon = true }
        }
        private data class DecoderKey(val identity: String, val useGpu: Boolean, val pingPong: Boolean)
        private val registry = HashMap<DecoderKey, CanvasVideo>()

        internal fun obtain(url: String, useGpu: Boolean = false, pingPong: Boolean = false): CanvasVideo =
            registry.getOrPut(DecoderKey(CanvasResolver.playbackCacheKey(url), useGpu, pingPong)) { CanvasVideo(url, useGpu, pingPong) }.also { it.acquire() }

        internal fun dispose(video: CanvasVideo) = video.release()

        /** Predecode a pass into the same bounded cache used by visible surfaces. */
        internal suspend fun warm(url: String, useGpu: Boolean, pingPong: Boolean) {
            if (CanvasFrameCache.get(CanvasResolver.playbackCacheKey(url)) != null) return
            val video = withContext(Dispatchers.Swing) { obtain(url, useGpu, pingPong) }
            try {
                withTimeoutOrNull(30_000) {
                    while (video.cachedFrameCount == 0 && !video.cacheTooLarge) delay(50)
                }
            } finally {
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Swing) { dispose(video) }
            }
        }
    }
}

/** Subscribe to the shared canvas decoder for [url] for as long as this is composed. */
@Composable
fun rememberCanvasVideo(url: String, playing: Boolean): CanvasVideo {
    val prefs = koinInject<DesktopPrefs>()
    rememberPrefsVersion(prefs)
    val useGpu = prefs.getBoolean(PrefKeys.CanvasUseGpu, false)
    val pingPong = prefs.getBoolean(PrefKeys.CanvasPingPong, false)
    val video = remember(url, useGpu, pingPong) { CanvasVideo.obtain(url, useGpu, pingPong) }
    DisposableEffect(video) { onDispose { CanvasVideo.dispose(video) } }
    DisposableEffect(video, playing) {
        video.votePaused(!playing, +1)
        onDispose { video.votePaused(!playing, -1) }
    }
    return video
}

/**
 * Draws a canvas video into its layout bounds like an Image (crop-to-fill by default).
 * It is an ordinary composable, so clip/blur/alpha/graphicsLayer modifiers all apply;
 * it fades in once the first frame arrives.
 */
@Composable
fun CanvasVideoSurface(
    videoUrl: String,
    playing: Boolean,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fadeInMillis: Int = 450,
    onFirstFrame: (() -> Unit)? = null,
) {
    val video = rememberCanvasVideo(videoUrl, playing)
    val hasFrame = video.frame != null
    val alpha by animateFloatAsState(if (hasFrame) 1f else 0f, com.music.vivi.compose.ui.motion.VisageMotion.fade(fadeInMillis), label = "canvasFade")
    if (onFirstFrame != null) {
        LaunchedEffect(video, hasFrame) { if (hasFrame) onFirstFrame() }
    }
    Box(
        modifier
            .graphicsLayer { this.alpha = alpha }
            .drawBehind {
                val display = video.presentation
                val f = display.frame ?: return@drawBehind
                drawCropped(f.image, contentScale, 1f)
                val next = display.incoming
                if (next != null && display.blend > 0.001f) drawCropped(next.image, contentScale, display.blend)
            },
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCropped(
    image: ImageBitmap,
    contentScale: ContentScale,
    alpha: Float,
) {
    val src = Size(image.width.toFloat(), image.height.toFloat())
    val scale = contentScale.computeScaleFactor(src, size)
    val dw = src.width * scale.scaleX
    val dh = src.height * scale.scaleY
    // Map the visible destination rect back to a source rect (centered crop).
    val visW = minOf(dw, size.width)
    val visH = minOf(dh, size.height)
    val srcW = (visW / scale.scaleX).roundToInt().coerceIn(1, image.width)
    val srcH = (visH / scale.scaleY).roundToInt().coerceIn(1, image.height)
    drawImage(
        image = image,
        srcOffset = IntOffset((image.width - srcW) / 2, (image.height - srcH) / 2),
        srcSize = IntSize(srcW, srcH),
        dstOffset = IntOffset(((size.width - visW) / 2).roundToInt(), ((size.height - visH) / 2).roundToInt()),
        dstSize = IntSize(visW.roundToInt(), visH.roundToInt()),
        alpha = alpha,
        filterQuality = FilterQuality.Low,
    )
}
