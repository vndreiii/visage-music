package com.music.vivi.compose.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.vivi.compose.ui.artist.ArtistDescription
import com.music.vivi.compose.ui.artist.ArtistStatChips
import com.music.vivi.compose.ui.glass.GlassSurface

/**
 * The header card shared by artist and album pages. One glass card (frosted over the page background with the blur
 * setting on, a plain card otherwise) holding: a [banner] that fades to transparent, the [artwork] (round portrait
 * or square cover, [artworkSize] of the card width) overlapping the banner's bottom edge, the title, an optional
 * [subtitle] (artist links), stat chips from [meta], a collapsible [description], and the Play / Shuffle row with
 * any [actions] after it.
 */
@Composable
fun MediaPageHeader(
    title: String,
    meta: String,
    description: String?,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    banner: @Composable () -> Unit,
    artwork: @Composable () -> Unit,
    artworkShape: Shape,
    artworkSize: (cardWidth: Dp) -> Dp,
    modifier: Modifier = Modifier,
    playLabel: String = "Play",
    subtitle: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    GlassSurface(
        shape = RoundedCornerShape(28.dp),
        fallbackColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cardWidth = maxWidth
            val size = artworkSize(cardWidth)
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 3:1 banner, never shorter than the artwork itself so a big cover is not squeezed on a narrow window.
                Box(Modifier.fillMaxWidth().height(maxOf(minOf(cardWidth / 3f, 420.dp), size))) {
                    // The banner fades to transparent (not to an opaque colour), so whatever the page has behind it,
                    // including the tinted blur background, shows through with no visible edge.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colorStops = arrayOf(
                                            0f to Color.Black,
                                            0.45f to Color.Black,
                                            0.7f to Color.Black.copy(alpha = 0.6f),
                                            0.88f to Color.Black.copy(alpha = 0.18f),
                                            1f to Color.Transparent,
                                        ),
                                    ),
                                    blendMode = BlendMode.DstIn,
                                )
                            },
                    ) { banner() }
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = size / 2)
                            .size(size)
                            .shadow(12.dp, artworkShape)
                            .clip(artworkShape),
                    ) { artwork() }
                }
                Spacer(Modifier.height(size / 2 + 12.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                subtitle?.let { Box(Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp)) { it() } }
                if (meta.isNotBlank()) {
                    ArtistStatChips(meta, Modifier.padding(top = 10.dp, start = 24.dp, end = 24.dp))
                }
                description?.takeIf { it.isNotBlank() }?.let { ArtistDescription(it) }
                Spacer(Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                ) {
                    Surface(
                        onClick = onPlay,
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.height(48.dp).weight(1.5f),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(playLabel, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                    Surface(
                        onClick = onShuffle,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Shuffle, contentDescription = "Shuffle", modifier = Modifier.size(22.dp))
                        }
                    }
                    actions()
                }
            }
        }
    }
}

/** A round 48 dp icon button in the style of the header's shuffle button, for [MediaPageHeader] actions. */
@Composable
fun MediaHeaderAction(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(48.dp)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(22.dp))
        }
    }
}
