package com.music.vivi.compose.ui.player.lyrics

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.vivi.compose.prefs.PlayerBackgroundStyle
import com.music.vivi.compose.theme.ArtworkPalette
import com.music.vivi.compose.theme.IdleArtworkPalette
import com.music.vivi.compose.ui.player.background.PlayerBackgroundLayer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Optional panel background behind the lyrics. Mode and intensity changes cross-fade; "None"
 * leaves the player backdrop untouched.
 */
@Composable
internal fun LyricsBackground(
    mode: LyricsBackgroundMode,
    intensity: Float,
    artworkUrl: String?,
    palette: ArtworkPalette?,
    playerBackgroundStyle: PlayerBackgroundStyle,
    modifier: Modifier = Modifier,
    corner: Int = 28,
) {
    val k = animateFloatAsState(intensity.coerceIn(0f, 1f), spring(1f, 300f), label = "lyricBgK")
    Crossfade(targetState = mode, animationSpec = androidx.compose.animation.core.tween(450), label = "lyricBg", modifier = modifier.fillMaxSize()) { m ->
        val shape = RoundedCornerShape(corner.dp)
        when (m) {
            LyricsBackgroundMode.NONE -> Box(Modifier.fillMaxSize())
            LyricsBackgroundMode.BLURRED_COVER -> Box(Modifier.fillMaxSize().clip(shape)) {
                Crossfade(artworkUrl, animationSpec = androidx.compose.animation.core.tween(600), label = "lyricBgCover") { url ->
                    if (url != null) {
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = k.value }.blur(56.dp),
                        )
                    }
                }
                Box(Modifier.fillMaxSize().drawBehind { drawRect(Color.Black.copy(alpha = 0.35f * k.value)) })
            }
            LyricsBackgroundMode.TINTED_SCRIM -> {
                val seed = (palette ?: IdleArtworkPalette).seed
                Box(
                    Modifier.fillMaxSize().clip(shape).drawBehind {
                        val a = k.value
                        drawRect(Color.Black.copy(alpha = 0.55f * a))
                        drawRect(
                            Brush.verticalGradient(
                                listOf(seed.copy(alpha = 0.32f * a), Color.Transparent, seed.copy(alpha = 0.2f * a)),
                            ),
                        )
                    },
                )
            }
            LyricsBackgroundMode.ANIMATED_GRADIENT -> {
                val colors = (palette ?: IdleArtworkPalette).let { it.glowColors.ifEmpty { it.gradient } }
                val time = remember { mutableFloatStateOf(0f) }
                LaunchedEffect(Unit) {
                    val start = withFrameNanos { it }
                    while (true) withFrameNanos { time.floatValue = (it - start) / 1_000_000_000f }
                }
                Box(
                    Modifier.fillMaxSize().clip(shape).drawBehind {
                        val a = k.value
                        drawRect(Color.Black.copy(alpha = 0.45f * a))
                        val t = time.floatValue * 0.18f
                        val r = size.maxDimension * 0.7f
                        for (i in 0 until 3) {
                            val c = colors[i % colors.size]
                            val ph = t + i * (2f * PI.toFloat() / 3f)
                            val center = Offset(
                                size.width * (0.5f + 0.38f * cos(ph * 1.1f + i)),
                                size.height * (0.5f + 0.38f * sin(ph * 0.9f + i * 2)),
                            )
                            drawCircle(
                                Brush.radialGradient(listOf(c.copy(alpha = 0.7f * a), Color.Transparent), center = center, radius = r),
                                radius = r, center = center,
                            )
                        }
                    },
                )
            }
            LyricsBackgroundMode.FOLLOW_PLAYER -> Box(Modifier.fillMaxSize().clip(shape).alpha(k.value)) {
                PlayerBackgroundLayer(style = playerBackgroundStyle, artworkUrl = artworkUrl)
            }
        }
    }
}
