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
import com.music.vivi.compose.ui.dnd.carrySong
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.vivi.compose.LocalPlaybackController
import com.music.vivi.player.PlaybackState

/**
 * Set by a screen (Home) to make artist names in its item subtitles open the artist page.
 * Null elsewhere, so names stay plain text there.
 */
val LocalArtistClick = compositionLocalOf<((String) -> Unit)?> { null }

/** Same, for names YouTube gave no id for (opens a search for the name instead). */
val LocalArtistNameClick = compositionLocalOf<((String) -> Unit)?> { null }

/** One piece of a subtitle line: plain text, or artist names (linked when they carry an id). */
internal sealed interface SubtitlePart {
    data class Plain(val text: String?) : SubtitlePart
    /** [linkNames]: a credited name with no artist id may fall back to a search; playlist owners ("Auto playlist", "YouTube Music") must not. */
    data class Artists(val artists: List<Artist>?, val linkNames: Boolean = true) : SubtitlePart
}

/** Subtitle pieces for a grid/list/hero item, in display order. */
internal fun subtitlePartsOf(item: YTItem): List<SubtitlePart> = when (item) {
    is SongItem -> listOf(SubtitlePart.Artists(item.artists), SubtitlePart.Plain(item.album?.name))
    is AlbumItem -> listOf(SubtitlePart.Plain("Album"), SubtitlePart.Artists(item.artists), SubtitlePart.Plain(item.year?.toString()))
    is ArtistItem -> listOf(SubtitlePart.Plain(item.subtext))
    is PlaylistItem -> listOf(SubtitlePart.Artists(listOfNotNull(item.author), linkNames = false), SubtitlePart.Plain(item.songCountText))
}

private fun buildSubtitle(
    parts: List<SubtitlePart>,
    onArtistClick: ((String) -> Unit)?,
    onNameClick: ((String) -> Unit)?,
): AnnotatedString =
    buildAnnotatedString {
        var wrote = false
        fun separate() {
            if (wrote) append(" • ")
            wrote = true
        }
        parts.forEach { part ->
            when (part) {
                is SubtitlePart.Plain -> if (!part.text.isNullOrBlank()) {
                    separate()
                    append(part.text)
                }
                is SubtitlePart.Artists -> {
                    val named = part.artists.orEmpty().filter { it.name.isNotBlank() }
                    if (named.isNotEmpty()) {
                        separate()
                        named.forEachIndexed { i, artist ->
                            if (i > 0) append(", ")
                            val id = artist.id
                            // Every credited name is a link: the artist page when there is an id, else a search.
                            val action: (() -> Unit)? = when {
                                id != null && onArtistClick != null -> ({ onArtistClick(id) })
                                onNameClick != null && part.linkNames -> ({ onNameClick(artist.name) })
                                else -> null
                            }
                            if (action != null) {
                                pushLink(
                                    LinkAnnotation.Clickable(
                                        tag = id ?: artist.name,
                                        linkInteractionListener = { action() },
                                    ),
                                )
                                append(artist.name)
                                pop()
                            } else {
                                append(artist.name)
                            }
                        }
                    }
                }
            }
        }
    }

/** Subtitle line whose artist names are clickable when [LocalArtistClick] is provided. */
@Composable
internal fun SubtitleLine(
    parts: List<SubtitlePart>,
    style: TextStyle,
    color: Color,
    maxLines: Int,
    overflow: TextOverflow,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
) {
    val onArtistClick = LocalArtistClick.current
    val onNameClick = LocalArtistNameClick.current
    val text = remember(parts, onArtistClick, onNameClick) { buildSubtitle(parts, onArtistClick, onNameClick) }
    if (text.isEmpty()) return
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
        modifier = modifier,
    )
}

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
    val menu = rememberItemMenuState()
    var heightPx by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(InteractionCornerRadius),
        color = Color.Transparent,
        // 16:9 covers keep the shelf's cover height, so they are wider instead of a tiny letterbox in a square cell.
        modifier = (if (fillWidth) modifier.fillMaxWidth() else modifier.width(GridThumbnailHeight * thumbnailRatio.coerceAtLeast(1f) + 16.dp))
            .onSizeChanged { heightPx = it.height }
            .itemSecondaryClick(menu)
            .carrySong(item),
    ) {
        ItemContextMenu(item, menu, heightPx, onClick)
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
                overflow = TextOverflow.Clip,
                textAlign = if (item is ArtistItem) TextAlign.Center else TextAlign.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .visageMarquee(),
            )
            SubtitleLine(
                parts = subtitlePartsOf(item),
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
    val menu = rememberItemMenuState()
    var heightPx by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
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
            .height(ListItemHeight)
            .onSizeChanged { heightPx = it.height }
            .itemSecondaryClick(menu)
            .carrySong(item),
    ) {
        ItemContextMenu(item, menu, heightPx, onClick)
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
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.visageMarquee(),
                )
                SubtitleLine(
                    parts = subtitlePartsOf(item),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.visageMarquee(),
                )
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
