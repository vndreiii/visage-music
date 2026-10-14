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
            PlayerBackgroundStyle.DEFAULT -> Box(Modifier.fillMaxSize().background(themeSurface))
            PlayerBackgroundStyle.GRADIENT -> ShaderBackground(BackgroundShader.AURORA, palette, artworkUrl)
            PlayerBackgroundStyle.BLUR -> ShaderBackground(BackgroundShader.BLUR, palette, artworkUrl)
            PlayerBackgroundStyle.GLOW_ANIMATED -> ShaderBackground(BackgroundShader.GLOW, palette, artworkUrl)
            PlayerBackgroundStyle.FLUID -> GlowCoverBg(palette, artworkUrl, FLUID_SHADER_SKSL)
            PlayerBackgroundStyle.WAVE -> ShaderBackground(BackgroundShader.WAVE, palette, artworkUrl)
            PlayerBackgroundStyle.XMB -> ShaderBackground(BackgroundShader.XMB, palette, artworkUrl)
            PlayerBackgroundStyle.DISTORT -> ShaderBackground(BackgroundShader.DISTORT, palette, artworkUrl)
            PlayerBackgroundStyle.LIVE_MESH -> ShaderBackground(BackgroundShader.MESH, palette, artworkUrl)
            PlayerBackgroundStyle.APPLE_MUSIC -> AppleMusicBg(
                artworkUrl = artworkUrl,
                canvasVideoUrl = canvasVideoUrl,
                canvasPlaying = canvasPlaying,
                canvasBlurred = canvasBlurred,
                showClearArtwork = showClearArtwork,
            )
        }
    }
}










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
        infiniteRepeatable(com.music.vivi.compose.ui.motion.ViviMotion.linear(40_000), RepeatMode.Restart),
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
        transitionSpec = { fadeIn(com.music.vivi.compose.ui.motion.ViviMotion.ambient()) togetherWith fadeOut(com.music.vivi.compose.ui.motion.ViviMotion.ambient()) },
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
            PlayerBackgroundStyle.DEFAULT -> Box(Modifier.fillMaxSize().background(themeSurface))
            PlayerBackgroundStyle.GRADIENT -> ShaderBackground(BackgroundShader.AURORA, palette, artworkUrl)
            PlayerBackgroundStyle.BLUR -> ShaderBackground(BackgroundShader.BLUR, palette, artworkUrl)
            PlayerBackgroundStyle.GLOW_ANIMATED -> ShaderBackground(BackgroundShader.GLOW, palette, artworkUrl)
            PlayerBackgroundStyle.FLUID -> MiniGlowCoverBg(palette, artworkUrl, FLUID_SHADER_SKSL)
            PlayerBackgroundStyle.WAVE -> ShaderBackground(BackgroundShader.WAVE, palette, artworkUrl)
            PlayerBackgroundStyle.XMB -> ShaderBackground(BackgroundShader.XMB, palette, artworkUrl)
            PlayerBackgroundStyle.DISTORT -> ShaderBackground(BackgroundShader.DISTORT, palette, artworkUrl)
            PlayerBackgroundStyle.LIVE_MESH -> ShaderBackground(BackgroundShader.MESH, palette, artworkUrl)
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
        infiniteRepeatable(com.music.vivi.compose.ui.motion.ViviMotion.linear(36_000), RepeatMode.Restart),
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

