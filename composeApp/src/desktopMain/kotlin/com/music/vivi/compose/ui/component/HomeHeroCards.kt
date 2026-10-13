package com.music.vivi.compose.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem

val HeroCardWidth = 300.dp
val HeroCardHeight = 200.dp
val HeroCardWideWidth = 360.dp

/** Full-bleed artwork card — daily-discover / mix / playlist style. */
@Composable
fun YouTubeHeroCard(
    item: YTItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = HeroCardWidth,
    height: Dp = HeroCardHeight,
    caption: String? = null,
    /** Parent list scroll offset for subtle parallax on the artwork. */
    parallaxPx: () -> Float = { 0f },
) {
    val subtitle = when (item) {
        is SongItem -> item.artists.joinToString { it.name }
        is AlbumItem -> item.artists?.joinToString { it.name }.orEmpty()
        is ArtistItem -> item.subtext.orEmpty()
        is PlaylistItem -> listOfNotNull(item.author?.name, item.songCountText)
            .joinToString(" • ")
    }
    val bottomCaption = caption ?: when (item) {
        is PlaylistItem -> item.description?.takeIf { it.isNotBlank() }
            ?: item.author?.name?.let { "Playlist • $it" }
        is AlbumItem -> "Album"
        is SongItem -> "Mix"
        else -> null
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(32.dp),
        modifier = modifier
            .width(width)
            .height(height)
            .interactiveScale(),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = item.thumbnail,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = parallaxPx() * 0.35f
                        scaleX = 1.08f
                        scaleY = 1.08f
                    },
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.15f),
                            0.45f to Color.Transparent,
                            0.75f to Color.Black.copy(alpha = 0.55f),
                            1f to Color.Black.copy(alpha = 0.88f),
                        ),
                    ),
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(18.dp),
            ) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!bottomCaption.isNullOrBlank() && bottomCaption != subtitle) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        bottomCaption,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Compact community-style playlist tile: collage thumbnail + title on a tonal surface.
 * Used when a shelf is mostly playlists but we don't have nested track lists yet.
 */
@Composable
fun PlaylistShelfCard(
    item: PlaylistItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .width(280.dp)
            .height(320.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp)),
            ) {
                AsyncImage(
                    model = item.thumbnail,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.5f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.45f),
                            ),
                        ),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = listOfNotNull(item.author?.name, item.songCountText).joinToString(" • ")
            if (sub.isNotBlank()) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
