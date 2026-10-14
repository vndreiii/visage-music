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
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** Latest decoded canvas frame; [generation] changes on every new frame so draws invalidate. */
@Stable
class CanvasFrame(val image: ImageBitmap, val generation: Long)

/**
 * One muted looping canvas video, shared by every composable showing the same URL
 * (cover slot, lyrics pane and blurred background decode once).
 *
 * Looping is gapless: two decoders alternate. [CROSSFADE_SEC] before the end of the active clip
 * the standby one (parked on frame 0) starts and is dissolved in over it, so the picture never
 * freezes or waits on a seek. The fade is driven by elapsed time, not by the clip reporting its
 * exact end (clips often stop a few frames short), and a watchdog guarantees the swap happens.
 */
class CanvasVideo private constructor(val url: String) {
    /** Frame currently on screen (the outgoing clip during a crossfade). */
    val frame: CanvasFrame? get() = slots[active].frame

    /** Incoming clip while crossfading, drawn over [frame] at [blend]; null otherwise. */
    val incoming: CanvasFrame? get() = if (crossing) slots[1 - active].frame else null

    var blend by mutableFloatStateOf(0f)
        private set
    private var crossing by mutableStateOf(false)

    private val lock = Any()
    private var active = 0
    private var refs = 0
    private var pauseVotes = 0
    private val slots = arrayOf(Slot(0), Slot(1))

    private var crossEpoch = 0
    private var crossElapsed = 0.0
    private var crossDuration = 0.0
    private var lastTickNs = 0L

    private inner class Slot(val index: Int) : FrameSink {
        var frame by mutableStateOf<CanvasFrame?>(null)
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
            frame = CanvasFrame(bmp.asComposeImageBitmap(), ++generation)
        }

        override fun onPosition(positionSec: Double, durationSec: Double) =
            onSlotFrame(index, positionSec, durationSec)
    }

    private fun onSlotFrame(slot: Int, pos: Double, length: Double) = synchronized(lock) {
        if (!crossing) {
            if (slot != active || length < 1.5) return@synchronized
            val fade = minOf(CROSSFADE_SEC, length / 3)
            if (pos < length - fade) return@synchronized
            // Fade for exactly the time the outgoing clip has left.
            crossDuration = maxOf(0.4, length - pos)
            crossElapsed = 0.0
            lastTickNs = System.nanoTime()
            val epoch = ++crossEpoch
            crossing = true
            slots[1 - active].renderer?.setHeld(false)
            watchdog.schedule(
                { synchronized(lock) { if (crossing && crossEpoch == epoch) finishCrossfade() } },
                (crossDuration * 1000).toLong() + WATCHDOG_GRACE_MS,
                TimeUnit.MILLISECONDS,
            )
            return@synchronized
        }
        // Both decoders tick us; wall time between ticks (capped, so pauses and stalls don't eat
        // the fade) drives the dissolve.
        val now = System.nanoTime()
        crossElapsed += minOf((now - lastTickNs) / 1e9, 0.1)
        lastTickNs = now
        val t = (crossElapsed / crossDuration).coerceIn(0.0, 1.0).toFloat()
        blend = t * t * (3f - 2f * t)
        if (t >= 1f) finishCrossfade()
    }

    /** Caller holds [lock]. Swaps roles, or, if the standby never produced a frame, restarts in place. */
    private fun finishCrossfade() {
        val old = slots[active]
        val next = slots[1 - active]
        crossing = false
        blend = 0f
        if (next.frame == null) {
            old.renderer?.apply { rewind(); setHeld(false) }
            next.renderer?.setHeld(true)
            return
        }
        active = 1 - active
        old.renderer?.apply { setHeld(true); rewind() }
    }

    private fun acquire() {
        refs++
        if (slots[0].renderer == null) {
            for (slot in slots) {
                slot.renderer = runCatching { MpvVideoRenderer(url, slot, startHeld = slot.index != active) }
                    .onFailure { System.err.println("CanvasVideo: ${it.message}") }
                    .getOrNull()
            }
        }
    }

    private fun release() {
        refs--
        if (refs <= 0) {
            for (slot in slots) { slot.renderer?.close(); slot.renderer = null }
            registry.remove(url)
        }
    }

    internal fun votePaused(paused: Boolean, delta: Int) {
        if (paused) pauseVotes += delta
        val pause = pauseVotes > 0 && pauseVotes >= refs
        for (slot in slots) slot.renderer?.setPaused(pause)
    }

    companion object {
        /** How long before the end the next loop starts fading in. */
        private const val CROSSFADE_SEC = 3.0
        private const val WATCHDOG_GRACE_MS = 3_000L
        private val watchdog = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "canvas-loop-watchdog").apply { isDaemon = true }
        }
        private val registry = HashMap<String, CanvasVideo>()

        internal fun obtain(url: String): CanvasVideo =
            registry.getOrPut(url) { CanvasVideo(url) }.also { it.acquire() }

        internal fun dispose(video: CanvasVideo) = video.release()
    }
}

/** Subscribe to the shared canvas decoder for [url] for as long as this is composed. */
@Composable
fun rememberCanvasVideo(url: String, playing: Boolean): CanvasVideo {
    val video = remember(url) { CanvasVideo.obtain(url) }
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
    val alpha by animateFloatAsState(if (hasFrame) 1f else 0f, tween(fadeInMillis), label = "canvasFade")
    if (onFirstFrame != null) {
        LaunchedEffect(hasFrame) { if (hasFrame) onFirstFrame() }
    }
    Box(
        modifier
            .graphicsLayer { this.alpha = alpha }
            .drawBehind {
                val f = video.frame ?: return@drawBehind
                drawCropped(f.image, contentScale, 1f)
                val next = video.incoming
                if (next != null && video.blend > 0.001f) drawCropped(next.image, contentScale, video.blend)
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
