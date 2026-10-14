package com.music.vivi.compose.ui.album

import com.music.vivi.compose.ui.component.smoothWheelScroll

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.music.innertube.pages.AlbumPage
import com.music.vivi.compose.LocalPlaybackController
import com.music.vivi.compose.navigation.Routes
import com.music.vivi.compose.ui.component.NavigationTitle
import com.music.vivi.compose.ui.component.YouTubeGridItem
import com.music.vivi.compose.ui.component.YouTubeListItem
import com.music.vivi.compose.ui.component.rememberNowPlayingIds
import com.music.vivi.core.youtube.YouTubeMusicService
import com.music.vivi.player.toQueueTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Desktop port of upstream AlbumScreen — header, play/shuffle, song list,
 * other versions + releases for you carousels.
 */
@Composable
fun OnlineAlbumScreen(
    albumId: String,
    navController: NavController,
) {
    val yt = koinInject<YouTubeMusicService>()
    val controller = LocalPlaybackController.current
    val scope = rememberCoroutineScope()
    val vm = remember(albumId) { OnlineAlbumViewModel(albumId, yt, scope) }

    val (playingId, isPlaying) = rememberNowPlayingIds()

    fun playFrom(index: Int, shuffled: Boolean = false) {
        val list = if (shuffled) vm.songs.shuffled() else vm.songs
        val song = list.getOrNull(if (shuffled) 0 else index) ?: return
        scope.launch(Dispatchers.IO) {
            if (song.id == playingId) {
                controller.toggleFromList()
            } else {
                val start = if (shuffled) 0 else index
                controller.playQueue(list.map { it.toQueueTrack() }, start)
            }
        }
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
        vm.isLoading && vm.page == null -> {
            PageLoadingState("Loading album", detail = true)
        }
        vm.error != null && vm.page == null -> {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Album failed: ${vm.error}")
                Button(onClick = { vm.retry() }, Modifier.padding(top = 12.dp)) {
                    Text("Retry")
                }
            }
        }
        else -> {
            val page = vm.page ?: return
            val listState = rememberLazyListState()
            val collapse = (
                listState.firstVisibleItemIndex * 320f +
                    listState.firstVisibleItemScrollOffset
                ).coerceIn(0f, 320f) / 320f
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
                    .smoothWheelScroll(listState),
                contentPadding = PaddingValues(bottom = 120.dp),
            ) {
                item(key = "toolbar") {
                    TextButton(onClick = { navController.navigateUp() }) {
                        Text("← Back")
                    }
                }

                item(key = "header") {
                    OnlineAlbumHeader(
                        page = page,
                        songs = vm.songs,
                        playingId = playingId,
                        isPlaying = isPlaying,
                        collapseFraction = collapse,
                        onPlay = { playFrom(0) },
                        onShuffle = { playFrom(0, shuffled = true) },
                        onArtistClick = { artistId ->
                            navController.navigate(Routes.artist(artistId))
                        },
                    )
                }

                itemsIndexed(
                    items = vm.songs,
                    key = { _, song -> song.id },
                ) { index, song ->
                    YouTubeListItem(
                        item = song,
                        isActive = song.id == playingId,
                        isPlaying = isPlaying,
                        onClick = { playFrom(index) },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    )
                }

                if (vm.otherVersions.isNotEmpty()) {
                    item(key = "versions_title") {
                        NavigationTitle(title = "Other versions")
                    }
                    item(key = "versions") {
                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
                            items(vm.otherVersions.distinctBy { it.id }, key = { it.id }) { album ->
                                YouTubeGridItem(
                                    item = album,
                                    onClick = { onItem(album) },
                                    isActive = album.id == playingId,
                                    isPlaying = isPlaying,
                                    modifier = Modifier.width(160.dp),
                                )
                            }
                        }
                    }
                }

                if (vm.releasesForYou.isNotEmpty()) {
                    item(key = "releases_title") {
                        NavigationTitle(title = "Releases for you")
                    }
                    item(key = "releases") {
                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
                            items(vm.releasesForYou.distinctBy { it.id }, key = { it.id }) { album ->
                                YouTubeGridItem(
                                    item = album,
                                    onClick = { onItem(album) },
                                    isActive = album.id == playingId,
                                    isPlaying = isPlaying,
                                    modifier = Modifier.width(160.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineAlbumHeader(
    page: AlbumPage,
    songs: List<SongItem>,
    playingId: String?,
    isPlaying: Boolean,
    collapseFraction: Float = 0f,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onArtistClick: (String) -> Unit,
) {
    val album = page.album
    val anyPlayingHere = songs.any { it.id == playingId }
    val artScale = 1f - collapseFraction * 0.35f
    val artAlpha = 1f - collapseFraction * 0.75f

    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(168.dp)
                .graphicsLayer {
                    alpha = artAlpha
                    scaleX = artScale
                    scaleY = artScale
                },
        ) {
            AsyncImage(
                model = album.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, MaterialTheme.colorScheme.surface),
                        ),
                    ),
            )
            AsyncImage(
                model = album.thumbnail,
                contentDescription = album.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                    .size(128.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = album.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 32.dp),
        )

        album.artists?.takeIf { it.isNotEmpty() }?.let { artists ->
            val onClick = if (artists.size == 1) {
                artists.firstOrNull()?.id?.let { id -> { onArtistClick(id) } }
            } else {
                null
            }
            Text(
                text = artists.joinToString(", ") { it.name },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 6.dp, start = 32.dp, end = 32.dp)
                    .clickable(enabled = onClick != null, onClick = { onClick?.invoke() }),
            )
        }

        val totalDuration = songs.sumOf { it.duration ?: 0 }
        Text(
            text = buildString {
                append("Album")
                album.year?.let { append(" • $it") }
                append(" • ${songs.size} Tracks")
                if (totalDuration > 0) {
                    val hours = totalDuration / 3600
                    val minutes = (totalDuration % 3600) / 60
                    append(" • ")
                    if (hours > 0) append("${hours}h ${minutes}m") else append("${minutes}m")
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, start = 32.dp, end = 32.dp),
        )

        page.description?.takeIf { it.isNotBlank() }?.let { desc ->
            Text(
                text = desc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, start = 32.dp, end = 32.dp),
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .height(52.dp)
                    .weight(1.5f)
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isPlaying && anyPlayingHere) "Pause" else "Play",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    textAlign = TextAlign.Center,
                )
            }
            Surface(
                onClick = onShuffle,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(52.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Shuffle,
                        contentDescription = "Shuffle",
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}