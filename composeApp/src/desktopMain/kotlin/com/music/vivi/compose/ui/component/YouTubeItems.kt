package com.music.vivi.compose.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.vivi.compose.LocalPlaybackController
import com.music.vivi.player.PlaybackState

private fun joinByBullet(vararg parts: String?): String? =
    parts.filterNot { it.isNullOrBlank() }.joinToString(" • ").ifBlank { null }

@Composable
fun YouTubeGridItem(
    item: YTItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    thumbnailRatio: Float = if (item is SongItem) 16f / 9f else 1f,
    /** Fill the parent's width (grid cells) instead of the fixed shelf width. */
    fillWidth: Boolean = false,
) {
    val thumbShape =
        if (item is ArtistItem) CircleShape else RoundedCornerShape(ThumbnailCornerRadius)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(InteractionCornerRadius),
        color = Color.Transparent,
        modifier = if (fillWidth) modifier.fillMaxWidth() else modifier.width(GridThumbnailHeight + 16.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalAlignment = if (item is ArtistItem) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(thumbnailRatio)
                    .clip(thumbShape),
            ) {
                CoverImage(item.thumbnail, item.id, Modifier.matchParentSize(), label = item.title)
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (isPlaying) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                } else if (item is SongItem) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (item is ArtistItem) TextAlign.Center else TextAlign.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee(),
            )
            val subtitle = when (item) {
                is SongItem -> joinByBullet(item.artists.joinToString { it.name }, item.album?.name)
                is AlbumItem -> joinByBullet("Album", item.artists?.joinToString { it.name }, item.year?.toString())
                is ArtistItem -> item.subtext
                is PlaylistItem -> joinByBullet(item.author?.name, item.songCountText)
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = if (item is ArtistItem) TextAlign.Center else TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun YouTubeListItem(
    item: YTItem,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
) {
    val shape =
        if (item is ArtistItem) CircleShape else RoundedCornerShape(ThumbnailCornerRadius)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        } else {
            Color.Transparent
        },
        onClick = onClick,
        modifier = modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
            .height(ListItemHeight),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(ListThumbnailSize)
                    .clip(shape),
            ) {
                CoverImage(item.thumbnail, item.id, Modifier.matchParentSize())
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (isPlaying) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = when (item) {
                    is SongItem -> joinByBullet(item.artists.joinToString { it.name }, item.album?.name)
                    is AlbumItem -> joinByBullet("Album", item.artists?.joinToString { it.name }, item.year?.toString())
                    is ArtistItem -> item.subtext
                    is PlaylistItem -> joinByBullet(item.author?.name, item.songCountText)
                }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun rememberNowPlayingIds(): Pair<String?, Boolean> {
    val controller = LocalPlaybackController.current
    val nowPlaying by controller.nowPlaying.collectAsState()
    val state by controller.state.collectAsState()
    return nowPlaying?.videoId to (state == PlaybackState.Playing)
}
