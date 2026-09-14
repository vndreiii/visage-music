package com.music.vivi.compose.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.vivi.compose.prefs.MiniPlayerStyle
import com.music.vivi.compose.prefs.PlayerBackgroundStyle
import com.music.vivi.compose.prefs.PlayerButtonsStyle
import com.music.vivi.compose.prefs.SliderStyle
import com.music.vivi.compose.prefs.TopNavStyle
import com.music.vivi.compose.theme.IdleArtworkPalette
import com.music.vivi.compose.theme.LocalArtworkPalette
import com.music.vivi.compose.ui.MiniPlayerBar
import com.music.vivi.compose.ui.player.background.PlayerBackgroundLayer
import kotlin.math.PI
import kotlin.math.sin

/** Rounded preview frame used next to every visual setting option. */
@Composable
fun MockupFrame(
    modifier: Modifier = Modifier,
    width: Dp = 112.dp,
    height: Dp = 64.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier
            .size(width = width, height = height)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(14.dp),
            ),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 1.dp,
    ) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)), content = { content() })
    }
}

@Composable
fun PlayerBackgroundMockup(
    style: PlayerBackgroundStyle,
    artworkUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    MockupFrame(modifier = modifier, width = 128.dp, height = 72.dp) {
        CompositionLocalProvider(LocalArtworkPalette provides IdleArtworkPalette) {
            Box(Modifier.fillMaxSize()) {
                PlayerBackgroundLayer(
                    style = style,
                    artworkUrl = artworkUrl,
                    canvasVideoUrl = null,
                    canvasPlaying = false,
                    showClearArtwork = true,
                    modifier = Modifier.fillMaxSize(),
                )
                // Tiny NP chrome so it reads as a player, not a wallpaper swatch
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.35f)),
                    )
                    Column(Modifier.weight(1f)) {
                        Box(
                            Modifier
                                .fillMaxWidth(0.7f)
                                .height(5.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.85f)),
                        )
                        Spacer(Modifier.height(3.dp))
                        Box(
                            Modifier
                                .fillMaxWidth(0.45f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.45f)),
                        )
                    }
                    Box(
                        Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * One large live mini-player card for settings. Selection changes crossfade + scale
 * via [AnimatedContent] so tiny unreadable row thumbnails aren't needed.
 */
@Composable
fun LiveMiniPlayerHeroPreview(
    layout: MiniPlayerStyle,
    background: PlayerBackgroundStyle,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 2.dp,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(118.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = layout to background,
                transitionSpec = {
                    (fadeIn(tween(280)) + scaleIn(initialScale = 0.94f, animationSpec = tween(280))) togetherWith
                        (fadeOut(tween(200)) + scaleOut(targetScale = 0.96f, animationSpec = tween(200)))
                },
                label = "miniHeroPreview",
                modifier = Modifier.fillMaxSize(),
            ) { (previewLayout, previewBg) ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    // Lay out at near-real width, then scale to fit the card so chrome stays readable.
                    val layoutWidth = when (previewLayout) {
                        MiniPlayerStyle.FULL_WIDTH_BAR -> 640.dp
                        MiniPlayerStyle.FLOATING_PILL -> 560.dp
                        MiniPlayerStyle.COMPACT_EDGE -> 340.dp
                    }
                    val scale = when (previewLayout) {
                        MiniPlayerStyle.FULL_WIDTH_BAR -> 0.72f
                        MiniPlayerStyle.FLOATING_PILL -> 0.78f
                        MiniPlayerStyle.COMPACT_EDGE -> 0.9f
                    }
                    Box(
                        Modifier
                            .requiredWidth(layoutWidth)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            },
                    ) {
                        MiniPlayerBar(
                            onExpand = null,
                            styleOverride = previewLayout,
                            backgroundStyleOverride = previewBg,
                            interactive = false,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Live mini-player widget (scaled) — kept for any remaining small callers.
 */
@Composable
fun MiniPlayerBackgroundMockup(
    style: PlayerBackgroundStyle,
    artworkUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    LiveMiniPlayerHeroPreview(
        layout = MiniPlayerStyle.FLOATING_PILL,
        background = style,
        modifier = modifier,
    )
}

/** Live architecture preview — delegates to the hero card. */
@Composable
fun MiniPlayerStyleMockup(
    style: MiniPlayerStyle,
    modifier: Modifier = Modifier,
) {
    LiveMiniPlayerHeroPreview(
        layout = style,
        background = PlayerBackgroundStyle.GRADIENT,
        modifier = modifier,
    )
}

@Composable
fun SliderStyleMockup(
    style: SliderStyle,
    squiggly: Boolean = false,
    progress: Float = 0.42f,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
    val infinite = rememberInfiniteTransition(label = "sliderMock")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wavePhase",
    )

    MockupFrame(modifier = modifier, width = 120.dp, height = 48.dp) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxWidth().height(28.dp)) {
                val p = progress.coerceIn(0.05f, 0.95f)
                val midY = size.height / 2f
                val wavy = squiggly || style == SliderStyle.WAVY
                val slim = style == SliderStyle.SLIM
                val expressive = style == SliderStyle.EXPRESSIVE
                val stroke = when {
                    slim -> 2.5.dp.toPx()
                    expressive -> 7.dp.toPx()
                    else -> 4.5.dp.toPx()
                }
                val amp = when {
                    wavy && expressive -> 5.dp.toPx()
                    wavy -> 3.5.dp.toPx()
                    else -> 0f
                }

                fun yAt(x: Float): Float {
                    if (amp == 0f) return midY
                    val t = x / size.width + phase
                    return midY + sin(t * 2f * PI.toFloat() * 3f) * amp
                }

                if (wavy) {
                    val inactive = Path()
                    val active = Path()
                    val steps = 48
                    for (i in 0..steps) {
                        val x = size.width * i / steps
                        val y = yAt(x)
                        if (i == 0) {
                            inactive.moveTo(x, y)
                            active.moveTo(x, y)
                        } else {
                            inactive.lineTo(x, y)
                            if (x <= size.width * p) active.lineTo(x, y)
                        }
                    }
                    drawPath(inactive, track, style = Stroke(width = stroke, cap = StrokeCap.Round))
                    drawPath(active, primary, style = Stroke(width = stroke, cap = StrokeCap.Round))
                } else {
                    val trackH = stroke
                    drawRoundRect(
                        color = track,
                        topLeft = Offset(0f, midY - trackH / 2f),
                        size = Size(size.width, trackH),
                        cornerRadius = CornerRadius(trackH),
                    )
                    drawRoundRect(
                        color = primary,
                        topLeft = Offset(0f, midY - trackH / 2f),
                        size = Size(size.width * p, trackH),
                        cornerRadius = CornerRadius(trackH),
                    )
                }

                val thumbR = when {
                    expressive -> 8.dp.toPx()
                    slim -> 5.dp.toPx()
                    else -> 6.5.dp.toPx()
                }
                val tx = size.width * p
                val ty = yAt(tx)
                drawCircle(color = primary, radius = thumbR, center = Offset(tx, ty))
                if (expressive) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.9f),
                        radius = thumbR * 0.35f,
                        center = Offset(tx, ty),
                    )
                }
            }
        }
    }
}

@Composable
fun PlayerButtonsMockup(
    style: PlayerButtonsStyle,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val (pill, icon) = when (style) {
        PlayerButtonsStyle.DEFAULT -> Color.White.copy(alpha = 0.14f) to Color.White
        PlayerButtonsStyle.PRIMARY -> scheme.primary to scheme.onPrimary
        PlayerButtonsStyle.TERTIARY -> scheme.tertiary to scheme.onTertiary
    }
    val playBg = when (style) {
        PlayerButtonsStyle.DEFAULT -> Color.White
        PlayerButtonsStyle.PRIMARY -> scheme.primary
        PlayerButtonsStyle.TERTIARY -> scheme.tertiary
    }
    val playIcon = when (style) {
        PlayerButtonsStyle.DEFAULT -> Color.Black
        PlayerButtonsStyle.PRIMARY -> scheme.onPrimary
        PlayerButtonsStyle.TERTIARY -> scheme.onTertiary
    }

    MockupFrame(modifier = modifier, width = 120.dp, height = 52.dp) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF2A1F1A)),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = RoundedCornerShape(10.dp), color = pill, modifier = Modifier.size(28.dp)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.SkipPrevious, null, tint = icon, modifier = Modifier.size(16.dp))
                    }
                }
                Surface(shape = RoundedCornerShape(999.dp), color = playBg, modifier = Modifier.size(width = 44.dp, height = 28.dp)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.PlayArrow, null, tint = playIcon, modifier = Modifier.size(16.dp))
                    }
                }
                Surface(shape = RoundedCornerShape(10.dp), color = pill, modifier = Modifier.size(28.dp)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.SkipNext, null, tint = icon, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun TopNavStyleMockup(
    style: TopNavStyle,
    modifier: Modifier = Modifier,
) {
    val selected = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    MockupFrame(modifier = modifier, width = 128.dp, height = 44.dp) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TopNavChip(Icons.Filled.Home, "Home", style, selected = true, selectedColor = selected, muted = muted)
            TopNavChip(Icons.Filled.Search, "Search", style, selected = false, selectedColor = selected, muted = muted)
            TopNavChip(Icons.Filled.LibraryMusic, "Library", style, selected = false, selectedColor = selected, muted = muted)
        }
    }
}

@Composable
private fun TopNavChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    style: TopNavStyle,
    selected: Boolean,
    selectedColor: Color,
    muted: Color,
) {
    val tint = if (selected) selectedColor else muted
    when (style) {
        TopNavStyle.ICON_ONLY -> Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        TopNavStyle.TEXT_ONLY -> Text(
            label,
            color = tint,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 10.sp,
        )
        TopNavStyle.ICON_AND_TEXT -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
            Text(label, color = tint, fontSize = 8.sp, maxLines = 1)
        }
    }
}

@Composable
fun PaletteSwatchMockup(
    colors: List<Color>,
    modifier: Modifier = Modifier,
) {
    MockupFrame(modifier = modifier, width = 96.dp, height = 36.dp) {
        Row(Modifier.fillMaxSize()) {
            colors.take(5).forEach { c ->
                Box(Modifier.weight(1f).fillMaxHeight().background(c))
            }
        }
    }
}

@Composable
fun ContrastMockup(
    level: Float,
    modifier: Modifier = Modifier,
) {
    val base = MaterialTheme.colorScheme.primary
    val on = MaterialTheme.colorScheme.onPrimary
    val surface = MaterialTheme.colorScheme.surface
    val contrastBoost = (level * 0.45f).coerceIn(0f, 0.45f)
    MockupFrame(modifier = modifier, width = 96.dp, height = 40.dp) {
        Box(
            Modifier
                .fillMaxSize()
                .background(surface),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = base,
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth()
                    .height(22.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Aa",
                        color = on.copy(alpha = (0.75f + contrastBoost).coerceAtMost(1f)),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
