package com.music.vivi.compose.ui.player.swap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** The three visual layers a swap moves independently (parallax). */
enum class SwapLayer { Background, Cover, Controls }

/**
 * Drives the song-swap animation for any scene made of [SwapLayer]s.
 *
 * One signed progress value [x] runs the whole thing: 0 = at rest, 0 -> 1 = the old song leaving,
 * the content is then swapped (via the `commit` callback of [run]) and x jumps to -1 and eases
 * back to 0 = the new song arriving. Layers only read the animatables inside `graphicsLayer {}`,
 * so nothing recomposes while it runs.
 */
@Stable
class SongSwapState {
    var config by mutableStateOf(SongSwapConfig())
    var direction by mutableStateOf(SwapDirection.NEXT)
        private set

    /** True while a swap (including a loading hover) is in flight. */
    var busy by mutableStateOf(false)
        private set

    internal val x = Animatable(0f)
    internal val gray = Animatable(0f)
    internal val bob = Animatable(0f)
    internal val loading = Animatable(0f)
    internal var viewportW = 0f
    internal var viewportH = 0f

    /**
     * Plays one swap. [commit] is called once, while the old content is fully out of view, and
     * must switch whatever the layers show to the new song. [isReady] is polled; if it is still
     * false after ~350 ms the camera-pan mode hovers (grayscale, bobbing) until it turns true.
     * [isFailed] ends the wait and settles back. Cancelling is safe (a newer swap takes over).
     */
    suspend fun run(
        dir: SwapDirection,
        animate: Boolean = config.mode.driven,
        isReady: () -> Boolean = { true },
        isFailed: () -> Boolean = { false },
        commit: () -> Unit,
    ) {
        direction = dir
        val cfg = config
        if (!animate) {
            x.snapTo(0f); gray.snapTo(0f); bob.snapTo(0f); loading.snapTo(0f)
            commit()
            return
        }
        busy = true
        try {
            coroutineScope {
                val s = cfg.speed
                val exitSpec = tween<Float>((300 / s).toInt().coerceAtLeast(80), easing = ExitEasing)
                var failed = false
                if (cfg.mode == SongSwapMode.CAMERA_PAN) {
                    val approach = launch { x.animateTo(HoverX, spring(0.9f, 260f * s * s, 0.0005f)) }
                    val quick = withTimeoutOrNull((HoverAfterMs / s).toLong().coerceAtLeast(150L)) {
                        delay(80)
                        awaitCondition { isReady() || isFailed() }
                    }
                    if (quick == null) {
                        val hover = launch { hoverLoop(cfg) }
                        val outcome = withTimeoutOrNull(MaxWaitMs) {
                            awaitCondition { isReady() || isFailed() }
                            !isFailed()
                        }
                        hover.cancel()
                        failed = outcome != true
                    } else {
                        failed = isFailed() && !isReady()
                    }
                    approach.cancel()
                } else {
                    x.animateTo(1f, exitSpec)
                    val quick = withTimeoutOrNull((HoverAfterMs / s).toLong().coerceAtLeast(150L)) {
                        awaitCondition { isReady() || isFailed() }
                    }
                    if (quick == null) {
                        val indicator = launch { loading.animateTo(1f, tween(300)) }
                        val outcome = withTimeoutOrNull(MaxWaitMs) {
                            awaitCondition { isReady() || isFailed() }
                            !isFailed()
                        }
                        indicator.cancel()
                        failed = outcome != true
                    } else {
                        failed = isFailed() && !isReady()
                    }
                }
                recover()
                if (failed) {
                    x.animateTo(0f, spring(0.55f, 380f * s * s, 0.0005f))
                    commit()
                } else {
                    if (cfg.mode == SongSwapMode.CAMERA_PAN) x.animateTo(1f, exitSpec)
                    commit()
                    x.snapTo(-1f)
                    x.animateTo(0f, spring(0.8f, 300f * s * s, 0.0005f))
                }
            }
        } finally {
            busy = false
        }
    }

    private suspend fun hoverLoop(cfg: SongSwapConfig) = coroutineScope {
        if (cfg.grayscaleWhileLoading) launch { gray.animateTo(1f, tween(500)) }
        launch { loading.animateTo(1f, tween(350, delayMillis = 150)) }
        while (true) {
            bob.animateTo(1f, tween(1500, easing = FastOutSlowInEasing))
            bob.animateTo(-1f, tween(1500, easing = FastOutSlowInEasing))
        }
    }

    private fun CoroutineScope.recover() {
        launch { gray.animateTo(0f, tween(450)) }
        launch { loading.animateTo(0f, tween(200)) }
        launch { bob.animateTo(0f, spring(0.8f, 200f)) }
    }

    private suspend fun awaitCondition(cond: () -> Boolean) {
        while (!cond()) delay(30)
    }

    companion object {
        private const val HoverX = 0.42f
        private const val HoverAfterMs = 350
        private const val MaxWaitMs = 20_000L
        private val ExitEasing = CubicBezierEasing(0.4f, 0f, 1f, 1f)
    }
}

/** Records the viewport size that the camera-pan distances are relative to. */
fun Modifier.songSwapViewport(state: SongSwapState): Modifier = onSizeChanged {
    state.viewportW = it.width.toFloat()
    state.viewportH = it.height.toFloat()
}

/** Applies the swap transform of [layer]. Pure graphics layer: no recomposition while animating. */
fun Modifier.songSwap(state: SongSwapState, layer: SwapLayer): Modifier =
    graphicsLayer { state.applyTo(this, layer) }

/** A subtle spinner shown while the swap hovers on a slow load. Overlay it above the layers. */
@Composable
fun SongSwapLoadingIndicator(state: SongSwapState, modifier: Modifier = Modifier) {
    val visible by remember(state) { derivedStateOf { state.loading.value > 0.01f } }
    if (!visible) return
    Box(modifier.graphicsLayer { alpha = state.loading.value * 0.85f }, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            color = Color.White,
            strokeWidth = 2.5.dp,
        )
    }
}

private fun smooth(edge0: Float, edge1: Float, v: Float): Float {
    val t = ((v - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}

private fun SongSwapState.applyTo(scope: GraphicsLayerScope, layer: SwapLayer) = with(scope) {
    val cfg = config
    val x = x.value
    val sat = 1f - gray.value
    val bob = bob.value
    val enabled = when (layer) {
        SwapLayer.Background -> cfg.animateBackground
        SwapLayer.Controls -> cfg.animateControls
        SwapLayer.Cover -> true
    }
    var blur = 0f
    if (enabled && cfg.mode.driven && (x != 0f || bob != 0f)) {
        val k = cfg.intensity
        val d = if (direction.forward) 1f else -1f
        val jump = if (direction.isJump) 1.25f else 1f
        val a = min(abs(x), 1f)
        val w = size.width
        val h = size.height
        val vh = if (viewportH > 0f) viewportH else h
        val dens = density
        when (cfg.mode) {
            SongSwapMode.CAMERA_PAN -> {
                val f = when (layer) { SwapLayer.Background -> 0.10f; SwapLayer.Cover -> 0.9f; SwapLayer.Controls -> 1.25f }
                val dist = vh * 0.6f * k * jump
                translationY = -d * x * dist * f + bob * 5f * dens * (0.5f + f)
                if (layer == SwapLayer.Background) {
                    val s = 1f + 2f * abs(translationY) / max(h, 1f) + 0.004f
                    scaleX = s; scaleY = s
                    alpha = 1f - 0.5f * a
                } else {
                    val s = 1f - 0.05f * a * k + abs(bob) * 0.006f
                    scaleX = s; scaleY = s
                    alpha = (1f - a.pow(1.6f)).coerceIn(0f, 1f)
                    blur = a * 14f * k * dens
                }
            }
            SongSwapMode.SLIDE -> if (layer == SwapLayer.Cover) {
                translationX = -d * x * w * 0.33f * k
                val s = 1f - 0.08f * a
                scaleX = s; scaleY = s
                alpha = 1f - a
            }
            SongSwapMode.CROSSFADE -> {
                alpha = if (layer == SwapLayer.Background) 1f - 0.6f * a else 1f - a
                if (layer == SwapLayer.Cover) {
                    val s = 1f - 0.04f * a * k
                    scaleX = s; scaleY = s
                }
            }
            SongSwapMode.ZOOM_THROUGH -> {
                val amt = when (layer) { SwapLayer.Cover -> 0.6f; SwapLayer.Controls -> 0.15f; SwapLayer.Background -> 0.12f } * k
                val s = if (layer == SwapLayer.Background) 1f + amt * a else max(0.05f, 1f + amt * d * x)
                scaleX = s; scaleY = s
                alpha = if (layer == SwapLayer.Background) 1f - 0.5f * a else 1f - a.pow(1.5f)
            }
            SongSwapMode.FLIP -> when (layer) {
                SwapLayer.Cover -> {
                    rotationY = -d * x * 90f * min(k, 1.2f)
                    cameraDistance = 14f * dens
                    alpha = 1f - smooth(0.88f, 1f, a)
                }
                SwapLayer.Controls -> {
                    alpha = 1f - a
                    translationY = x * 14f * dens * k
                }
                SwapLayer.Background -> alpha = 1f - 0.5f * a
            }
            SongSwapMode.STACK -> when (layer) {
                SwapLayer.Cover -> if (x >= 0f) {
                    val s = 1f - 0.2f * k * x
                    scaleX = s; scaleY = s
                    translationY = -0.04f * h * x
                    alpha = 1f - x
                } else {
                    translationX = -d * x * w * 0.5f * k
                    rotationZ = d * x * 6f * k
                    alpha = 1f - a * a
                }
                SwapLayer.Controls -> {
                    alpha = 1f - a
                    translationY = x * 10f * dens * k
                }
                SwapLayer.Background -> alpha = 1f - 0.4f * a
            }
            SongSwapMode.VINYL -> when (layer) {
                SwapLayer.Cover -> if (x >= 0f) {
                    translationX = -d * x * w * 0.65f * k
                    rotationZ = -d * x * 12f
                    alpha = 1f - x * x
                } else {
                    translationY = x * h * 0.35f * k
                    rotationZ = d * x * 10f
                    val s = 1f + (-x) * 0.08f * k
                    scaleX = s; scaleY = s
                    alpha = 1f - a * a
                }
                SwapLayer.Controls -> {
                    alpha = 1f - a
                    translationY = x * 14f * dens * k
                }
                SwapLayer.Background -> alpha = 1f - 0.4f * a
            }
            SongSwapMode.WIPE -> when (layer) {
                SwapLayer.Cover -> {
                    val maxR = hypot(w, h) / 2f * 1.3f
                    val r = (1f - a) * maxR
                    if (r < maxR * 0.995f) {
                        clip = true
                        shape = CookieShape(r, x * 90f, w / 2f, h / 2f)
                    }
                    alpha = if (r < 1f) 0f else 1f
                }
                SwapLayer.Controls -> alpha = 1f - a
                SwapLayer.Background -> alpha = 1f - 0.4f * a
            }
            SongSwapMode.BLUR_MORPH -> {
                blur = a * (if (layer == SwapLayer.Background) 24f else 40f) * k * dens
                alpha = if (layer == SwapLayer.Background) 1f - 0.4f * a else 1f - a * a
                val s = 1f + 0.04f * a * k
                scaleX = s; scaleY = s
            }
            SongSwapMode.NONE -> Unit
        }
    }
    var fx: RenderEffect? = null
    if (sat < 0.999f) {
        // Skia filter chain: blur (if any) then saturation.
        val cm = ColorMatrix().apply { setToSaturation(sat.coerceAtLeast(0f)) }
        val skCf = org.jetbrains.skia.ColorFilter.makeMatrix(org.jetbrains.skia.ColorMatrix(*cm.values))
        val blurred = if (blur > 0.5f) {
            val (bx, by) = if (cfg.mode == SongSwapMode.CAMERA_PAN) 0f to blur else blur to blur
            org.jetbrains.skia.ImageFilter.makeBlur(bx, by, org.jetbrains.skia.FilterTileMode.DECAL)
        } else null
        fx = org.jetbrains.skia.ImageFilter.makeColorFilter(skCf, blurred, null).asComposeRenderEffect()
    } else if (blur > 0.5f) {
        fx = if (cfg.mode == SongSwapMode.CAMERA_PAN) BlurEffect(0f, blur, TileMode.Decal)
        else BlurEffect(blur, blur, TileMode.Decal)
    }
    renderEffect = fx
}

private val cookiePath: Path by lazy {
    val poly = RoundedPolygon.star(9, 1f, 0.82f, CornerRounding(0.5f))
    val p = Path()
    var first = true
    for (c in poly.cubics) {
        if (first) { p.moveTo(c.anchor0X, c.anchor0Y); first = false }
        p.cubicTo(c.control0X, c.control0Y, c.control1X, c.control1Y, c.anchor1X, c.anchor1Y)
    }
    p.close()
    p
}

/** Unit cookie (9-sided Material-expressive style star) scaled to [radius] around ([cx], [cy]). */
private class CookieShape(
    val radius: Float,
    val rotationDeg: Float,
    val cx: Float,
    val cy: Float,
) : Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val m = Matrix()
        m.translate(cx, cy)
        m.rotateZ(rotationDeg)
        m.scale(radius, radius)
        val p = Path()
        p.addPath(cookiePath)
        p.transform(m)
        return Outline.Generic(p)
    }
}
