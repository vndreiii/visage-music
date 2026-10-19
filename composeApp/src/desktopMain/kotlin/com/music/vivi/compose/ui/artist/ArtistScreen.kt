package com.music.vivi.compose.ui.artist

import com.music.vivi.compose.ui.component.WheelLazyRow
import com.music.vivi.compose.ui.component.smoothWheelScroll

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
                    .smoothWheelScroll(smoothScroll1),
                contentPadding = PaddingValues(bottom = 24.dp),
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
                    item(key = "section_title_$index") {
                        NavigationTitle(
                            title = section.title.ifBlank { "More" },
                            onClick = section.moreEndpoint?.let { ep ->
                                { navController.navigate(Routes.forBrowse(ep.browseId, ep.params, artistId)) }
                            },
                        )
                    }
                    val songsOnly = section.items.isNotEmpty() && section.items.all { it is SongItem }
                    if (songsOnly) {
                        items(
                            items = section.items.filterIsInstance<SongItem>(),
                            key = { "${index}_${it.id}" },
                        ) { song ->
                            YouTubeListItem(
                                item = song,
                                isActive = song.id == playingId,
                                isPlaying = isPlaying,
                                onClick = { onItem(song) },
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }
                    } else {
                        item(key = "section_row_$index") {
                            WheelLazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
                                items(section.items, key = { it.id }) { item ->
                                    YouTubeGridItem(
                                        item = item,
                                        onClick = { onItem(item) },
                                        isActive = item.id == playingId,
                                        isPlaying = isPlaying,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ArtistBackBar(onBack: () -> Unit) {
    Box(Modifier.padding(start = 16.dp, top = 8.dp)) { com.music.vivi.compose.ui.component.VisageBackButton(onClick = onBack) }
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
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(2.4f)) {
            Box(Modifier.fillMaxSize()) { banner() }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, MaterialTheme.colorScheme.surface),
                        ),
                    ),
            )
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
            Text(
                meta,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp),
            )
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
