package com.music.vivi.compose.ui.player.background

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import coil3.compose.AsyncImage
import com.music.vivi.compose.prefs.PlayerBackgroundStyle
import com.music.vivi.compose.theme.ArtworkPalette
import com.music.vivi.compose.theme.LocalArtworkPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Full Now Playing / player background layers
 * (DEFAULT / GRADIENT / BLUR / GLOW_ANIMATED / APPLE_MUSIC / LIVE_MESH).
 */
@Composable
fun PlayerBackgroundLayer(
    style: PlayerBackgroundStyle,
    artworkUrl: String?,
    canvasVideoUrl: String? = null,
    canvasPlaying: Boolean = true,
    showClearArtwork: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val palette = LocalArtworkPalette.current
    val themeSurface = MaterialTheme.colorScheme.surface
    Box(modifier.fillMaxSize().clipToBounds()) {
        when (style) {
            PlayerBackgroundStyle.DEFAULT -> {
                Box(Modifier.fillMaxSize().background(themeSurface))
            }
            PlayerBackgroundStyle.GRADIENT -> RotatingCoverGradientBg(palette, artworkUrl)
            PlayerBackgroundStyle.BLUR -> BlurBg(artworkUrl)
            PlayerBackgroundStyle.GLOW_ANIMATED -> GlowCoverBg(palette, artworkUrl)
            PlayerBackgroundStyle.APPLE_MUSIC -> AppleMusicBg(
                artworkUrl = artworkUrl,
                canvasVideoUrl = canvasVideoUrl,
                canvasPlaying = canvasPlaying,
                showClearArtwork = showClearArtwork,
            )
            PlayerBackgroundStyle.LIVE_MESH -> LiveMeshShapesBg(palette)
            PlayerBackgroundStyle.LEGACY -> LegacyHorizontalGradientBg(palette)
        }
    }
}

@Composable
private fun RotatingCoverGradientBg(palette: ArtworkPalette?, artworkUrl: String?) {
    val colors = palette?.gradient?.takeIf { it.size >= 2 }
        ?: listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.surface)
    val infinite = rememberInfiniteTransition(label = "gradRot")
    val angle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(28_000, easing = LinearEasing), RepeatMode.Restart),
        label = "gradAngle",
    )
    Box(Modifier.fillMaxSize()) {
        // Soft cover underlay so the gradient is grounded in artwork.
        if (artworkUrl != null) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = angle * 0.25f; scaleX = 1.35f; scaleY = 1.35f }
                    .blur(90.dp)
                    .alpha(0.45f),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val rad = angle * (PI.toFloat() / 180f)
                    val c = cos(rad)
                    val s = sin(rad)
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val len = max(size.width, size.height)
                    val brush = Brush.linearGradient(
                        colors = colors,
                        start = Offset(cx - c * len, cy - s * len),
                        end = Offset(cx + c * len, cy + s * len),
                    )
                    onDrawBehind {
                        drawRect(brush)
                        drawRect(Color.Black.copy(alpha = 0.22f))
                    }
                },
        )
    }
}

@Composable
private fun LegacyHorizontalGradientBg(palette: ArtworkPalette?) {
    val colors = palette?.gradient?.takeIf { it.isNotEmpty() }
        ?: listOf(MaterialTheme.colorScheme.primary, Color.Black)
    val stops = buildList {
        add(colors.first())
        if (colors.size >= 2) addAll(colors.drop(1).take(2))
        add(Color.Black)
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.horizontalGradient(stops)),
    )
}

@Composable
private fun BlurBg(artworkUrl: String?) {
    AnimatedContent(
        targetState = artworkUrl,
        transitionSpec = { fadeIn(tween(800)) togetherWith fadeOut(tween(800)) },
        label = "blurBackground",
    ) { url ->
        if (url != null) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(120.dp),
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.30f)))
            }
        }
    }
}

/** Dimmed rotating cover + palette glow orbs + slight blur. */
@Composable
private fun GlowCoverBg(palette: ArtworkPalette?, artworkUrl: String?) {
    val glow = palette?.glowColors?.takeIf { it.isNotEmpty() }
        ?: palette?.gradient
        ?: listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
    val infinite = rememberInfiniteTransition(label = "glowCover")
    val rot by infinite.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(40_000, easing = LinearEasing), RepeatMode.Restart),
        label = "glowRot",
    )
    val pulse by infinite.animateFloat(
        0.55f, 0.9f,
        infiniteRepeatable(tween(6_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "glowPulse",
    )
    val dimMatrix = remember {
        ColorMatrix(
            floatArrayOf(
                0.55f, 0f, 0f, 0f, 0f,
                0f, 0.55f, 0f, 0f, 0f,
                0f, 0f, 0.55f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
        if (artworkUrl != null) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = ColorFilter.colorMatrix(dimMatrix),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = rot * 0.35f
                        scaleX = 1.25f
                        scaleY = 1.25f
                    }
                    .blur(28.dp)
                    .alpha(0.85f),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    val brushes = glow.take(4).mapIndexed { i, color ->
                        val t = (rot / 360f + i * 0.22f) % 1f
                        val cx = 0.2f + 0.6f * ((sin(t * 2f * PI.toFloat() + i) + 1f) * 0.5f)
                        val cy = 0.2f + 0.6f * ((cos(t * 2f * PI.toFloat() + i * 1.3f) + 1f) * 0.5f)
                        Brush.radialGradient(
                            colors = listOf(
                                color.copy(alpha = 0.55f * pulse),
                                color.copy(alpha = 0.18f * pulse),
                                Color.Transparent,
                            ),
                            center = Offset(w * cx, h * cy),
                            radius = w * (0.45f + i * 0.08f),
                        )
                    }
                    onDrawBehind { brushes.forEach { drawRect(brush = it) } }
                }
                .blur(18.dp),
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
    }
}

@Composable
private fun AppleMusicBg(
    artworkUrl: String?,
    canvasVideoUrl: String?,
    canvasPlaying: Boolean,
    showClearArtwork: Boolean,
) {
    AnimatedContent(
        targetState = artworkUrl,
        transitionSpec = { fadeIn(tween(1200)) togetherWith fadeOut(tween(1200)) },
        label = "appleMusicBackground",
    ) { url ->
        if (url == null) return@AnimatedContent
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(150.dp),
            )
            if (showClearArtwork) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.65f)
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0f to Color.Black,
                                        0.75f to Color.Black,
                                        0.92f to Color.Black.copy(alpha = 0.4f),
                                        1f to Color.Transparent,
                                    ),
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        },
                ) {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (!canvasVideoUrl.isNullOrBlank()) {
                        CanvasVideoSurface(
                            videoUrl = canvasVideoUrl,
                            playing = canvasPlaying,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.4f)),
                        ),
                    ),
            )
        }
    }
}

/** Blurred M3 expressive shapes, rotating in a soft kaleidoscope. */
@Composable
private fun LiveMeshShapesBg(palette: ArtworkPalette?) {
    val colors = palette?.gradient?.takeIf { it.isNotEmpty() }
        ?: listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.primaryContainer,
        )
    val shapes = remember { meshPolygons() }
    val paths = remember(shapes) { shapes.map { it.toPath() } }
    val infinite = rememberInfiniteTransition(label = "meshKaleido")
    val rotA by infinite.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(32_000, easing = LinearEasing), RepeatMode.Restart),
        label = "meshA",
    )
    val rotB by infinite.animateFloat(
        0f, -360f,
        infiniteRepeatable(tween(48_000, easing = LinearEasing), RepeatMode.Restart),
        label = "meshB",
    )
    val morph by infinite.animateFloat(
        0.85f, 1.15f,
        infiniteRepeatable(tween(9_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "meshScale",
    )
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .blur(42.dp)
                .alpha(0.9f),
        ) {
            val base = min(size.width, size.height) * 0.42f * morph
            val center = size.center
            // Kaleidoscope: 6 wedges of the same shape set.
            repeat(6) { wedge ->
                rotate(degrees = wedge * 60f + rotA * 0.2f, pivot = center) {
                    paths.forEachIndexed { i, path ->
                        val color = colors[i % colors.size].copy(alpha = 0.55f)
                        translate(center.x, center.y) {
                            rotate(degrees = rotA * (0.4f + i * 0.15f) + i * 40f) {
                                scale(base / 2f, base / 2f, pivot = Offset.Zero) {
                                    translate(-0.5f, -0.5f) {
                                        drawPath(path, color = color, style = Fill)
                                    }
                                }
                            }
                        }
                        // Counter-rotating ghost
                        translate(center.x, center.y) {
                            rotate(degrees = rotB * (0.3f + i * 0.1f) - i * 25f) {
                                scale(base / 2.6f, base / 2.6f, pivot = Offset.Zero) {
                                    translate(0.15f, -0.35f) {
                                        drawPath(
                                            path,
                                            color = colors[(i + 1) % colors.size].copy(alpha = 0.35f),
                                            style = Fill,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
    }
}

private fun meshPolygons(): List<RoundedPolygon> {
    val round = CornerRounding(radius = 0.28f)
    return listOf(
        RoundedPolygon.circle(numVertices = 8),
        RoundedPolygon.star(numVerticesPerRadius = 6, radius = 1f, innerRadius = 0.55f, rounding = round),
        RoundedPolygon(
            numVertices = 7,
            rounding = CornerRounding(0.35f),
        ),
        RoundedPolygon.star(numVerticesPerRadius = 8, radius = 1f, innerRadius = 0.42f, rounding = CornerRounding(0.2f)),
    )
}

private fun RoundedPolygon.toPath(): Path {
    val path = Path()
    val cubics = normalized().cubics
    var first = true
    for (cubic in cubics) {
        if (first) {
            path.moveTo(cubic.anchor0X, cubic.anchor0Y)
            first = false
        }
        path.cubicTo(
            cubic.control0X, cubic.control0Y,
            cubic.control1X, cubic.control1Y,
            cubic.anchor1X, cubic.anchor1Y,
        )
    }
    path.close()
    val bounds = path.getBounds()
    val m = Matrix().apply {
        translate(-bounds.left, -bounds.top)
        val s = 1f / max(bounds.width, bounds.height).coerceAtLeast(0.001f)
        scale(s, s)
    }
    path.transform(m)
    return path
}

/** Compact background for the mini-player pill (same enum, lighter). */
@Composable
fun MiniPlayerBackgroundLayer(
    style: PlayerBackgroundStyle,
    artworkUrl: String?,
    modifier: Modifier = Modifier,
) {
    val palette = LocalArtworkPalette.current
    val themeSurface = MaterialTheme.colorScheme.surfaceContainerHigh
    Box(modifier.clipToBounds()) {
        when (style) {
            PlayerBackgroundStyle.DEFAULT -> {
                Box(Modifier.fillMaxSize().background(themeSurface))
            }
            PlayerBackgroundStyle.GRADIENT -> {
                val colors = palette?.gradient?.takeIf { it.size >= 2 }
                    ?: listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.tertiary,
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                val infinite = rememberInfiniteTransition(label = "miniGrad")
                val angle by infinite.animateFloat(
                    0f, 360f,
                    infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart),
                    label = "miniGradAngle",
                )
                Box(Modifier.fillMaxSize()) {
                    if (artworkUrl != null) {
                        AsyncImage(
                            model = artworkUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { rotationZ = angle * 0.3f; scaleX = 1.5f; scaleY = 1.5f }
                                .blur(22.dp)
                                .alpha(0.4f),
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .drawWithCache {
                                val rad = angle * (PI.toFloat() / 180f)
                                val c = cos(rad)
                                val s = sin(rad)
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                val len = max(size.width, size.height)
                                val brush = Brush.linearGradient(
                                    colors = colors,
                                    start = Offset(cx - c * len, cy - s * len),
                                    end = Offset(cx + c * len, cy + s * len),
                                )
                                onDrawBehind {
                                    drawRect(brush)
                                    drawRect(Color.Black.copy(alpha = 0.18f))
                                }
                            },
                    )
                }
            }
            PlayerBackgroundStyle.BLUR -> {
                if (artworkUrl != null) {
                    AsyncImage(
                        model = artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().blur(30.dp),
                    )
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
                } else {
                    Box(Modifier.fillMaxSize().background(themeSurface))
                }
            }
            PlayerBackgroundStyle.GLOW_ANIMATED -> {
                val glow = palette?.glowColors?.takeIf { it.isNotEmpty() }
                    ?: palette?.gradient
                    ?: listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                val infinite = rememberInfiniteTransition(label = "miniGlow")
                val rot by infinite.animateFloat(
                    0f, 360f,
                    infiniteRepeatable(tween(36_000, easing = LinearEasing), RepeatMode.Restart),
                    label = "miniGlowRot",
                )
                val dimMatrix = remember {
                    ColorMatrix(
                        floatArrayOf(
                            0.5f, 0f, 0f, 0f, 0f,
                            0f, 0.5f, 0f, 0f, 0f,
                            0f, 0f, 0.5f, 0f, 0f,
                            0f, 0f, 0f, 1f, 0f,
                        ),
                    )
                }
                Box(Modifier.fillMaxSize().background(Color(0xFF101010))) {
                    if (artworkUrl != null) {
                        AsyncImage(
                            model = artworkUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            colorFilter = ColorFilter.colorMatrix(dimMatrix),
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    rotationZ = rot * 0.4f
                                    scaleX = 1.4f
                                    scaleY = 1.4f
                                }
                                .blur(16.dp)
                                .alpha(0.9f),
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .drawWithCache {
                                val w = size.width
                                val h = size.height
                                val brushes = glow.take(3).mapIndexed { i, color ->
                                    val t = (rot / 360f + i * 0.28f) % 1f
                                    val cx = 0.25f + 0.5f * ((sin(t * 2f * PI.toFloat() + i) + 1f) * 0.5f)
                                    val cy = 0.3f + 0.4f * ((cos(t * 2f * PI.toFloat() + i) + 1f) * 0.5f)
                                    Brush.radialGradient(
                                        colors = listOf(
                                            color.copy(alpha = 0.65f),
                                            color.copy(alpha = 0.2f),
                                            Color.Transparent,
                                        ),
                                        center = Offset(w * cx, h * cy),
                                        radius = w * (0.55f + i * 0.1f),
                                    )
                                }
                                onDrawBehind { brushes.forEach { drawRect(brush = it) } }
                            }
                            .blur(10.dp),
                    )
                }
            }
            PlayerBackgroundStyle.LIVE_MESH -> {
                LiveMeshShapesBg(palette)
            }
            PlayerBackgroundStyle.LEGACY -> {
                LegacyHorizontalGradientBg(palette)
            }
            PlayerBackgroundStyle.APPLE_MUSIC -> {
                if (artworkUrl != null) {
                    AsyncImage(
                        model = artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().blur(40.dp),
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(themeSurface))
                }
            }
        }
    }
}
