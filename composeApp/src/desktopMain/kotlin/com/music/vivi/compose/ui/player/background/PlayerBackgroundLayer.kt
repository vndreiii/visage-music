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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ShaderBrush
import java.nio.ByteBuffer
import java.nio.ByteOrder
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.asComposeRenderEffect
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
    /** Blur the canvas into an ambient wash (true) or show it full-bleed and sharp (false). */
    canvasBlurred: Boolean = true,
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
            PlayerBackgroundStyle.GLOW_ANIMATED, PlayerBackgroundStyle.FLUID -> GlowCoverBg(palette, artworkUrl, FLUID_SHADER_SKSL)
            PlayerBackgroundStyle.WAVE -> GlowCoverBg(palette, artworkUrl, WAVE_SHADER_SKSL)
            PlayerBackgroundStyle.XMB -> GlowCoverBg(palette, artworkUrl, XMB_SHADER_SKSL)
            PlayerBackgroundStyle.DISTORT -> DistortCoverBg(artworkUrl)
            PlayerBackgroundStyle.APPLE_MUSIC -> AppleMusicBg(
                artworkUrl = artworkUrl,
                canvasVideoUrl = canvasVideoUrl,
                canvasPlaying = canvasPlaying,
                canvasBlurred = canvasBlurred,
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




private const val WAVE_SHADER_SKSL = """
uniform float2 iResolution;
uniform float iTime;
uniform vec3 color1;
uniform vec3 color2;
uniform vec3 color3;

const float TWO_PI = 6.28318530718;

half4 main(vec2 fragCoord) {
    vec2 uv = (fragCoord - iResolution.xy * 0.5) / min(iResolution.x, iResolution.y);
    float time_factor = iTime * TWO_PI / 10.0;

    float wave1 = sin(uv.x * 3.0 + time_factor * 1.0) * 0.1;
    float wave2 = cos(uv.y * 4.0 - time_factor * 2.0) * 0.12; 
    float wave3 = sin((uv.x + uv.y) * 5.0 + time_factor * 3.0) * 0.08;
    float large_wave = sin(uv.x * 1.0 - time_factor * 1.0) * 0.25; 
    float combined_waves = wave1 + wave2 + wave3 + large_wave;

    vec3 dark_color = color1 * 0.1;
    vec3 mid_color = color2 * 0.2;
    float intensity = smoothstep(-0.2, 0.2, combined_waves);
    vec3 base_color = mix(dark_color, mid_color, intensity);

    float gradient = abs(sin(combined_waves * 10.0));
    float outline = smoothstep(0.8, 1.0, gradient);
    vec3 outline_color = color3;

    vec3 final_color = mix(base_color, outline_color, outline);
    return half4(final_color, 1.0);
}
"""

private const val XMB_SHADER_SKSL = """
uniform float2 iResolution;
uniform float iTime;
uniform vec3 color1;
uniform vec3 color2;
uniform vec3 color3;

half4 main(vec2 fragCoord) {
    vec2 uv = (fragCoord - iResolution.xy * 0.5) / min(iResolution.x, iResolution.y);
    
    float t = iTime * 0.3;
    vec3 col = vec3(0.0);
    
    for(int i = 1; i <= 4; i++) {
        float fi = float(i);
        float wave = sin(uv.x * (1.0 + fi * 0.3) + t * (0.8 + fi * 0.2)) * 0.2 
                   * cos(uv.x * (0.5 + fi * 0.1) - t * 0.4) 
                   + sin(uv.x * 2.5 - t * 0.3 + fi) * 0.1;
                   
        float dist = abs(uv.y - wave);
        
        float thickness = 0.4;
        float fill = smoothstep(thickness, 0.0, dist);
        float core = smoothstep(0.02, 0.0, dist); 
        
        vec3 color = mix(color1, color2, 0.5 + 0.5 * sin(t + fi));
        
        col += color * fill * 0.5;
        col += color3 * core;
    }
    
    return half4(col, 1.0);
}
"""

private const val DISTORT_SHADER_SKSL = """
uniform float2 iResolution;
uniform float iTime;
uniform shader image;

half4 main(vec2 fragCoord) {
    vec2 uv = fragCoord / iResolution;
    
    float waveX = sin(uv.y * 5.0 + iTime * 0.5) * 0.05 + cos(uv.y * 3.0 - iTime * 0.3) * 0.03;
    float waveY = cos(uv.x * 5.0 + iTime * 0.5) * 0.05 + sin(uv.x * 3.0 - iTime * 0.3) * 0.03;
    
    vec2 distorted = uv + vec2(waveX, waveY);
    return image.eval(distorted * iResolution);
}
"""

private const val FLUID_SHADER_SKSL = """
uniform float2 iResolution;
uniform float iTime;
uniform vec3 color1;
uniform vec3 color2;
uniform vec3 color3;

half4 main(vec2 fragCoord) {
    vec2 uv = (fragCoord - iResolution.xy * 0.5) / min(iResolution.x, iResolution.y);
    
    vec2 p = uv;
    float t = iTime * 0.2;
    vec3 col = vec3(0.0);
    
    for(int i = 1; i < 8; i++) {
        float fi = float(i);
        float angle = t * 0.2 / fi;
        float s = sin(angle);
        float c = cos(angle);
        p = vec2(p.x * c - p.y * s, p.x * s + p.y * c);
        
        p.x += sin(t * 0.8 + p.y * 1.5 * fi) * 0.2;
        p.y += cos(t * 0.9 + p.x * 1.5 * fi) * 0.2;
        
        float len = length(p);
        float stroke = smoothstep(0.8, 0.0, len * (0.5 + 0.2 * fi));
        
        float mix1 = sin(iTime * 0.2 + p.x * 2.0 + fi) * 0.5 + 0.5;
        float mix2 = cos(iTime * 0.2 + p.y * 2.0 + fi) * 0.5 + 0.5;
        vec3 strokeColor = mix(mix(color1, color2, mix1), color3, mix2);
        
        col += strokeColor * stroke * 0.3;
    }
    
    return half4(col, 1.0);
}
"""

@Composable
private fun GlowCoverBg(palette: ArtworkPalette?, artworkUrl: String?, sksl: String = FLUID_SHADER_SKSL) {
    val glow = palette?.glowColors?.takeIf { it.isNotEmpty() }
        ?: palette?.gradient
        ?: listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary)
    val c1 = glow.getOrElse(0) { MaterialTheme.colorScheme.primary }
    val c2 = glow.getOrElse(1) { c1 }
    val c3 = glow.getOrElse(2) { c2 }
    
    val infinite = rememberInfiniteTransition(label = "glowCover")
    val rot by infinite.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(40_000, easing = LinearEasing), RepeatMode.Restart),
        label = "glowRot",
    )
    
    var time by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        while(true) {
            androidx.compose.runtime.withFrameNanos { time = (System.currentTimeMillis() - start) / 1000f }
        }
    }
    val effect = remember(sksl) { org.jetbrains.skia.RuntimeEffect.makeForShader(sksl) }

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
                    
                    val buffer = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN)
                    buffer.putFloat(w)
                    buffer.putFloat(h)
                    buffer.putFloat(time)
                    buffer.putFloat(c1.red).putFloat(c1.green).putFloat(c1.blue)
                    buffer.putFloat(c2.red).putFloat(c2.green).putFloat(c2.blue)
                    buffer.putFloat(c3.red).putFloat(c3.green).putFloat(c3.blue)
                    
                    val data = org.jetbrains.skia.Data.makeFromBytes(buffer.array())
                    val shader = effect.makeShader(data, null, null)
                    val brush = ShaderBrush(shader)
                    
                    onDrawBehind { drawRect(brush) }
                }
                .blur(12.dp),
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
    }
}

@Composable
private fun AppleMusicBg(
    artworkUrl: String?,
    canvasVideoUrl: String?,
    canvasPlaying: Boolean,
    canvasBlurred: Boolean,
    showClearArtwork: Boolean,
) {
    Box(Modifier.fillMaxSize()) {
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
                        modifier = Modifier.fillMaxSize().blur(50.dp).alpha(0.85f),
                    )
                    
                }
            }
        }
    }
    if (canvasVideoUrl != null) {
        CanvasVideoSurface(
            videoUrl = canvasVideoUrl,
            playing = canvasPlaying,
            // Slow cross-fade over the blurred artwork so the switch to video is invisible;
            // overscan before blurring so the blur never pulls transparent edges inward.
            fadeInMillis = if (canvasBlurred) 1400 else 600,
            modifier = if (canvasBlurred) {
                Modifier.fillMaxSize().graphicsLayer { scaleX = 1.35f; scaleY = 1.35f }.blur(120.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded).alpha(0.85f)
            } else {
                Modifier.fillMaxSize()
            },
        )
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    if (canvasBlurred || canvasVideoUrl == null) {
                        listOf(Color.Black.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.4f))
                    } else {
                        listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.7f))
                    },
                ),
            ),
    )
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
            PlayerBackgroundStyle.GLOW_ANIMATED, PlayerBackgroundStyle.FLUID -> MiniGlowCoverBg(palette, artworkUrl, FLUID_SHADER_SKSL)
            PlayerBackgroundStyle.WAVE -> MiniGlowCoverBg(palette, artworkUrl, WAVE_SHADER_SKSL)
            PlayerBackgroundStyle.XMB -> MiniGlowCoverBg(palette, artworkUrl, XMB_SHADER_SKSL)
            PlayerBackgroundStyle.DISTORT -> MiniDistortCoverBg(artworkUrl)
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





@Composable
private fun DistortCoverBg(artworkUrl: String?) {
    val infinite = rememberInfiniteTransition(label = "distortTr")
    var time by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        while(true) {
            androidx.compose.runtime.withFrameNanos { time = (System.currentTimeMillis() - start) / 1000f }
        }
    }
    val waveX by infinite.animateFloat(
        -30f, 30f,
        infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "waveX",
    )
    val waveY by infinite.animateFloat(
        -30f, 30f,
        infiniteRepeatable(tween(18_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "waveY",
    )
    
    val effect = remember { org.jetbrains.skia.RuntimeEffect.makeForShader(DISTORT_SHADER_SKSL) }
    
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (artworkUrl != null) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val w = maxWidth.value
                val h = maxHeight.value
                val builder = remember(effect, time, w, h) {
                    val b = org.jetbrains.skia.RuntimeShaderBuilder(effect)
                    b.uniform("iTime", time)
                    b.uniform("iResolution", w, h)
                    b
                }
                val imageFilter = remember(builder) {
                    org.jetbrains.skia.ImageFilter.makeRuntimeShader(builder, "image", null)
                }
                
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.3f
                            scaleY = 1.3f
                            translationX = waveX
                            translationY = waveY
                            renderEffect = imageFilter.asComposeRenderEffect()
                        }
                        .blur(40.dp)
                        .alpha(0.6f)
                )
            }
        }
    }
}






@Composable
private fun MiniGlowCoverBg(palette: ArtworkPalette?, artworkUrl: String?, sksl: String) {
    val glow = palette?.glowColors?.takeIf { it.isNotEmpty() }
        ?: palette?.gradient
        ?: listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary)
    val c1 = glow.getOrElse(0) { MaterialTheme.colorScheme.primary }
    val c2 = glow.getOrElse(1) { c1 }
    val c3 = glow.getOrElse(2) { c2 }
    
    val infinite = rememberInfiniteTransition(label = "miniGlow")
    val rot by infinite.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(36_000, easing = LinearEasing), RepeatMode.Restart),
        label = "miniGlowRot",
    )
    
    var time by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        while(true) {
            androidx.compose.runtime.withFrameNanos { time = (System.currentTimeMillis() - start) / 1000f }
        }
    }
    val effect = remember(sksl) { org.jetbrains.skia.RuntimeEffect.makeForShader(sksl) }

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
                    .alpha(0.6f), // lowered opacity
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    
                    val buffer = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN)
                    buffer.putFloat(w)
                    buffer.putFloat(h)
                    buffer.putFloat(time)
                    buffer.putFloat(c1.red).putFloat(c1.green).putFloat(c1.blue)
                    buffer.putFloat(c2.red).putFloat(c2.green).putFloat(c2.blue)
                    buffer.putFloat(c3.red).putFloat(c3.green).putFloat(c3.blue)
                    
                    val data = org.jetbrains.skia.Data.makeFromBytes(buffer.array())
                    val shader = effect.makeShader(data, null, null)
                    val brush = ShaderBrush(shader)
                    
                    onDrawBehind { drawRect(brush) }
                }
                .blur(12.dp)
                .alpha(0.75f), // lower opacity for better contrast
        )
    }
}

@Composable
private fun MiniDistortCoverBg(artworkUrl: String?) {
    val infinite = rememberInfiniteTransition(label = "distortTr")
    var time by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        while(true) {
            androidx.compose.runtime.withFrameNanos { time = (System.currentTimeMillis() - start) / 1000f }
        }
    }
    val waveX by infinite.animateFloat(
        -20f, 20f,
        infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "waveX",
    )
    val waveY by infinite.animateFloat(
        -20f, 20f,
        infiniteRepeatable(tween(18_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "waveY",
    )
    
    val effect = remember { org.jetbrains.skia.RuntimeEffect.makeForShader(DISTORT_SHADER_SKSL) }
    
    Box(Modifier.fillMaxSize().background(Color(0xFF101010))) {
        if (artworkUrl != null) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val w = maxWidth.value
                val h = maxHeight.value
                val builder = remember(effect, time, w, h) {
                    val b = org.jetbrains.skia.RuntimeShaderBuilder(effect)
                    b.uniform("iTime", time)
                    b.uniform("iResolution", w, h)
                    b
                }
                val imageFilter = remember(builder) {
                    org.jetbrains.skia.ImageFilter.makeRuntimeShader(builder, "image", null)
                }
                
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.3f
                            scaleY = 1.3f
                            translationX = waveX
                            translationY = waveY
                            renderEffect = imageFilter.asComposeRenderEffect()
                        }
                        .blur(24.dp)
                        .alpha(0.5f)
                )
            }
        }
    }
}