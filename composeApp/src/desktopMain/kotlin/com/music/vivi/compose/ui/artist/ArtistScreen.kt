package com.music.vivi.compose.ui.artist

import androidx.compose.foundation.lazy.grid.items
import com.music.vivi.compose.ui.component.horizontalWheelScroll
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.foundation.layout.width
import com.music.vivi.compose.ui.component.MiniPlayerClearance
import com.music.vivi.compose.ui.component.PageHeaderTopPadding
import com.music.vivi.compose.ui.component.PageInset
import com.music.vivi.compose.ui.component.PageHeaderBottomPadding
import com.music.vivi.compose.ui.component.WheelLazyRow
import com.music.vivi.compose.ui.component.smoothWheelScroll
import com.music.vivi.compose.ui.component.pageScrollEdge
import com.music.vivi.compose.ui.component.edgeProgress

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.sp
import com.music.vivi.compose.ui.motion.VisageMotion
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import com.music.vivi.compose.ui.component.PageLoadingState
import com.music.vivi.compose.ui.component.VisageLoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.pages.ArtistPage
import com.music.vivi.compose.LocalPlaybackController
import com.music.vivi.compose.navigation.Routes
import com.music.vivi.compose.ui.component.NavigationTitle
import com.music.vivi.compose.ui.component.YouTubeGridItem
import com.music.vivi.compose.ui.component.YouTubeListItem
import com.music.vivi.compose.ui.component.rememberNowPlayingIds
import com.music.vivi.core.youtube.YouTubeMusicService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Desktop port of upstream ArtistScreen — header + YouTube artist sections.
 */
@Composable
fun ArtistScreen(
    artistId: String,
    navController: NavController,
) {
    val yt = koinInject<YouTubeMusicService>()
    val controller = LocalPlaybackController.current
    val scope = rememberCoroutineScope()
    val (playingId, isPlaying) = rememberNowPlayingIds()

    var page by remember { mutableStateOf<ArtistPage?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // The artist page only carries a handful of items per shelf; shelves with a "more" link are filled in from it.
    val fullSections = remember(artistId) { androidx.compose.runtime.mutableStateMapOf<Int, List<YTItem>>() }
    LaunchedEffect(page) {
        val p = page ?: return@LaunchedEffect
        p.sections.forEachIndexed { i, sec ->
            val ep = sec.moreEndpoint ?: return@forEachIndexed
            if (sec.items.isEmpty()) return@forEachIndexed
            launch(Dispatchers.IO) {
                com.music.innertube.YouTube.artistItems(com.music.innertube.models.BrowseEndpoint(browseId = ep.browseId, params = ep.params))
                    .getOrNull()?.items?.distinctBy { it.id }?.takeIf { it.size > sec.items.size }
                    ?.let { fullSections[i] = it }
            }
        }
    }

    LaunchedEffect(artistId) {
        loading = true
        error = null
        yt.artist(artistId)
            .onSuccess { page = it }
            .onFailure { error = it.message ?: it.toString() }
        loading = false
    }

    fun onItem(item: YTItem) {
        when (item) {
            is SongItem -> scope.launch(Dispatchers.IO) {
                if (item.id == playingId) {
                    controller.toggleFromList()
                } else {
                    controller.playVideo(item.id)
                }
            }
            is AlbumItem -> navController.navigate(Routes.album(item.id))
            is ArtistItem -> navController.navigate(Routes.artist(item.id))
            is PlaylistItem -> navController.navigate(Routes.onlinePlaylist(item.id))
        }
    }

    when {
        loading && page == null -> PageLoadingState("Loading artist", detail = true)
        error != null && page == null -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Artist failed: $error")
            Button(
                onClick = {
                    scope.launch {
                        loading = true
                        yt.artist(artistId).onSuccess { page = it }.onFailure { error = it.message }
                        loading = false
                    }
                },
                Modifier.padding(top = 12.dp),
            ) { Text("Retry") }
        }
        else -> {
            val artistPage = page ?: return
            val artist = artistPage.artist
            val smoothScroll1 = androidx.compose.foundation.lazy.rememberLazyListState()
            LazyColumn(
                state = smoothScroll1,
                modifier = Modifier.fillMaxSize()
                    .pageScrollEdge { smoothScroll1.edgeProgress() }.smoothWheelScroll(smoothScroll1),
                contentPadding = PaddingValues(top = 12.dp, bottom = MiniPlayerClearance),
            ) {
                item(key = "toolbar") { ArtistBackBar { navController.navigateUp() } }
                item(key = "header") {
                    ArtistHeader(
                        artist = artist,
                        description = artistPage.description,
                        subscribers = artistPage.subscriberCountText,
                        monthlyListeners = artistPage.monthlyListenerCount,
                        onPlay = {
                            val firstSong = artistPage.sections
                                .flatMap { it.items }
                                .filterIsInstance<SongItem>()
                                .firstOrNull()
                            if (firstSong != null) onItem(firstSong)
                        },
                        onShuffle = {
                            val song = artistPage.sections
                                .flatMap { it.items }
                                .filterIsInstance<SongItem>()
                                .randomOrNull()
                            if (song != null) onItem(song)
                        },
                    )
                }

                artistPage.sections.forEachIndexed { index, section ->
                    val full = fullSections[index] ?: section.items
                    val songsOnly = full.isNotEmpty() && full.all { it is SongItem }
                    val isVideos = songsOnly && section.title.contains("video", ignoreCase = true)
                    item(key = "section_title_$index") {
                        NavigationTitle(
                            title = section.title.ifBlank { "More" },
                            // NavigationTitle pads its text by 12 dp; shift so the text starts where the covers do.
                            modifier = Modifier.padding(start = ArtistContentStart - 12.dp),
                            onClick = section.moreEndpoint?.let { ep ->
                                { navController.navigate(Routes.forBrowse(ep.browseId, ep.params, artistId)) }
                            },
                        )
                    }
                    when {
                        isVideos -> item(key = "section_videos_$index") {
                            // Videos are big 16:9 thumbnails in a grid, not a text list.
                            VideoGrid(full.filterIsInstance<SongItem>(), playingId, isPlaying) { onItem(it) }
                        }
                        songsOnly -> item(key = "section_songs_$index") {
                            ArtistSongColumns(full.filterIsInstance<SongItem>(), playingId, isPlaying) { onItem(it) }
                        }
                        else -> item(key = "section_row_$index") {
                            ArtistShelf(full, playingId, isPlaying) { onItem(it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ArtistBackBar(onBack: () -> Unit) {
    Box(Modifier.padding(start = PageInset, top = PageHeaderTopPadding, bottom = PageHeaderBottomPadding)) { com.music.vivi.compose.ui.component.VisageBackButton(onClick = onBack) }
}

@Composable
private fun ArtistHeader(
    artist: ArtistItem,
    description: String?,
    subscribers: String?,
    monthlyListeners: String?,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    ArtistPageHeader(
        title = artist.title,
        meta = listOfNotNull(subscribers, monthlyListeners).joinToString(" • "),
        description = description,
        onPlay = onPlay,
        onShuffle = onShuffle,
        banner = {
            AsyncImage(
                model = artist.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        },
        portrait = {
            AsyncImage(
                model = artist.thumbnail,
                contentDescription = artist.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        },
    )
}

/**
 * The shared artist page header (YouTube artists and helper-backed local artists): full-width
 * banner, round portrait overlapping its bottom edge, centered name, stats line, collapsible
 * description, then a pill Play button with a round shuffle button.
 */
@Composable
internal fun ArtistPageHeader(
    title: String,
    meta: String,
    description: String?,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    banner: @Composable () -> Unit,
    portrait: @Composable () -> Unit,
) {
    val portraitSize = 140.dp
    // Banner, portrait, name, description and the Play row are one card: frosted glass over the page background with
    // the blur setting on (so the banner fades into the glass, not into the bare page), a plain card otherwise.
    com.music.vivi.compose.ui.glass.GlassSurface(
        shape = RoundedCornerShape(28.dp),
        fallbackColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp),
    ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(3.0f)) {
            // The banner fades to transparent (not to an opaque colour), so whatever the page has behind it, including
            // the tinted blur background, shows through with no visible edge.
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
                    .offset(y = portraitSize / 2)
                    .size(portraitSize)
                    .shadow(12.dp, CircleShape)
                    .clip(CircleShape),
            ) { portrait() }
        }
        Spacer(Modifier.height(portraitSize / 2 + 12.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        if (meta.isNotBlank()) {
            ArtistStatChips(meta, Modifier.padding(top = 10.dp, start = 24.dp, end = 24.dp))
        }
        description?.takeIf { it.isNotBlank() }?.let { ArtistDescription(it) }
        Spacer(Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
        ) {
            Surface(
                onClick = onPlay,
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.height(48.dp).weight(1.5f),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Play",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Surface(
                onClick = onShuffle,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(48.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Shuffle,
                        contentDescription = "Shuffle",
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
    }
}

/**
 * Artist description: three lines when collapsed, in a readable centred column (max 640dp) with
 * generous line spacing; "More"/"Less" animates the height with the app's glide spring.
 */
@Composable
internal fun ArtistDescription(text: String) {
    var expanded by remember(text) { mutableStateOf(false) }
    var overflows by remember(text) { mutableStateOf(false) }
    Column(
        Modifier
            .padding(top = 12.dp, start = 32.dp, end = 32.dp)
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .animateContentSize(VisageMotion.glide()),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Clip,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        )
        if (overflows || expanded) {
            Text(
                if (expanded) "Less" else "More",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}


/** Left edge of the first cover on an artist page: the page inset plus the card's own 6 dp padding. Titles line up with it. */
private val ArtistContentStart = PageInset + 6.dp

/** Big 16:9 video thumbnails, as many columns as fit. */
@Composable
private fun VideoGrid(videos: List<SongItem>, playingId: String?, isPlaying: Boolean, onClick: (SongItem) -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 12.dp
        val usable = maxWidth - PageInset * 2
        val cols = ((usable + gap) / (300.dp + gap)).toInt().coerceIn(1, 4)
        Column(
            Modifier.padding(horizontal = PageInset),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            videos.chunked(cols).forEach { row ->
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { v ->
                        YouTubeGridItem(
                            item = v,
                            onClick = { onClick(v) },
                            isActive = v.id == playingId,
                            isPlaying = isPlaying,
                            thumbnailRatio = 16f / 9f,
                            fillWidth = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(cols - row.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * One shelf of albums, singles, playlists or artists. Cards are sized so a whole number of them fills the row edge to
 * edge (no leftover gap on the right), larger than before, and the row scrolls sideways for the rest.
 */
@Composable
private fun ArtistShelf(items: List<YTItem>, playingId: String?, isPlaying: Boolean, onClick: (YTItem) -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 14.dp
        val usable = maxWidth - PageInset * 2
        val perRow = ((usable + gap) / (200.dp + gap)).toInt().coerceAtLeast(3)
        val cardWidth = (usable - gap * (perRow - 1)) / perRow
        WheelLazyRow(
            contentPadding = PaddingValues(horizontal = PageInset),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            items(items, key = { it.id }) { item ->
                YouTubeGridItem(
                    item = item,
                    onClick = { onClick(item) },
                    isActive = item.id == playingId,
                    isPlaying = isPlaying,
                    thumbnailRatio = 1f,
                    modifier = Modifier.width(cardWidth),
                )
            }
        }
    }
}


/** "2.76M subscribers • 74.7M monthly audience" as separate pills, each with an icon that says what it counts. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ArtistStatChips(meta: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        meta.split('•', '·').map { it.trim() }.filter { it.isNotEmpty() }.forEach { stat ->
            val lower = stat.lowercase()
            val icon = when {
                "subscriber" in lower || "follower" in lower -> Icons.Outlined.Groups
                "listener" in lower || "audience" in lower || "monthly" in lower -> Icons.Outlined.Headphones
                "song" in lower || "track" in lower -> Icons.Outlined.MusicNote
                "album" in lower -> Icons.Outlined.Album
                else -> Icons.Outlined.Info
            }
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f))
                    .padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stat, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}


/** Songs as Home's Quick picks shelf: up to 4 rows, extra songs scroll sideways in whole columns. */
@Composable
private fun ArtistSongColumns(songs: List<SongItem>, playingId: String?, isPlaying: Boolean, onClick: (SongItem) -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 8.dp
        // The list row adds 8 + 12 dp of its own before the cover.
        val side = ArtistContentStart - 20.dp
        val usable = maxWidth - side * 2
        val fitCols = ((usable + gap) / (280.dp + gap)).toInt().coerceAtLeast(1)
        val shown = songs.take(60)
        val rows = ((shown.size + fitCols - 1) / fitCols).coerceIn(1, 4)
        val colWidth = (usable - gap * (fitCols - 1)) / fitCols
        val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
        androidx.compose.foundation.lazy.grid.LazyHorizontalGrid(
            rows = androidx.compose.foundation.lazy.grid.GridCells.Fixed(rows),
            state = gridState,
            contentPadding = PaddingValues(horizontal = side),
            horizontalArrangement = Arrangement.spacedBy(gap),
            modifier = Modifier
                .fillMaxWidth()
                .height(com.music.vivi.compose.ui.component.ListItemHeight * rows)
                .horizontalWheelScroll(gridState),
        ) {
            items(shown, key = { it.id }) { song ->
                YouTubeListItem(
                    item = song,
                    isActive = song.id == playingId,
                    isPlaying = isPlaying,
                    onClick = { onClick(song) },
                    modifier = Modifier.width(colWidth),
                )
            }
        }
    }
}
