package com.music.vivi.compose.ui.playlist

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
import com.music.vivi.compose.ui.component.ViviLoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import com.music.vivi.compose.LocalPlaybackController
import com.music.vivi.compose.navigation.Routes
import com.music.vivi.compose.ui.component.NavigationTitle
import com.music.vivi.compose.ui.component.YouTubeGridItem
import com.music.vivi.compose.ui.component.YouTubeListItem
import com.music.vivi.compose.ui.component.rememberNowPlayingIds
import com.music.vivi.core.youtube.YouTubeMusicService
import com.music.vivi.database.LibraryRepository
import com.music.vivi.player.toQueueTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Desktop port of upstream OnlinePlaylistScreen — header, play/shuffle, in-page search,
 * song list, related carousels, continuation loading.
 */
@Composable
fun OnlinePlaylistScreen(
    playlistId: String,
    navController: NavController,
) {
    val yt = koinInject<YouTubeMusicService>()
    val library = koinInject<LibraryRepository>()
    val controller = LocalPlaybackController.current
    val scope = rememberCoroutineScope()
    val vm = remember(playlistId) { OnlinePlaylistViewModel(playlistId, yt, library, scope) }

    val listState = rememberLazyListState()
    LaunchedEffect(listState, vm.continuation) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                val total = listState.layoutInfo.totalItemsCount
                if (last != null && total > 0 && last >= total - 4) {
                    vm.loadMore()
                }
            }
    }

    var searchQuery by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    val filteredSongs by remember {
        derivedStateOf {
            val q = searchQuery.trim()
            if (q.isEmpty()) vm.songs
            else vm.songs.filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.artists.any { a -> a.name.contains(q, ignoreCase = true) }
            }
        }
    }

    val (playingId, isPlaying) = rememberNowPlayingIds()

    fun playFrom(index: Int, shuffled: Boolean = false) {
        val list = if (shuffled) filteredSongs.shuffled() else filteredSongs
        val song = list.getOrNull(if (shuffled) 0 else index) ?: return
        scope.launch(Dispatchers.IO) {
            if (song.id == playingId) {
                if (isPlaying) controller.pause() else controller.play()
            } else {
                val start = if (shuffled) 0 else index
                controller.playQueue(list.map { it.toQueueTrack() }, start)
            }
        }
    }

    when {
        vm.isLoading && vm.playlist == null -> {
            PageLoadingState("Loading playlist", detail = true)
        }
        vm.error != null && vm.playlist == null -> {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Playlist failed: ${vm.error}")
                Button(onClick = { vm.retry() }, Modifier.padding(top = 12.dp)) {
                    Text("Retry")
                }
            }
        }
        else -> {
            val playlist = vm.playlist
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                // Mini player floats over the list — keep last tracks reachable.
                contentPadding = PaddingValues(bottom = 120.dp),
            ) {
                item(key = "toolbar") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { navController.navigateUp() }) {
                            Text("← Back")
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { searching = !searching }) {
                            Text(if (searching) "Close search" else "Search")
                        }
                    }
                }

                if (searching) {
                    item(key = "search") {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            singleLine = true,
                            label = { Text("Search in playlist") },
                        )
                    }
                }

                if (playlist != null && !searching) {
                    item(key = "header") {
                        OnlinePlaylistHeader(
                            playlist = playlist,
                            songs = vm.songs,
                            playingId = playingId,
                            isPlaying = isPlaying,
                            onPlay = { playFrom(0) },
                            onShuffle = { playFrom(0, shuffled = true) },
                            onAuthorClick = {
                                playlist.author?.id?.let { id ->
                                    navController.navigate(Routes.artist(id))
                                }
                            },
                        )
                    }
                }

                itemsIndexed(
                    items = filteredSongs,
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

                if (vm.isLoadingMore) {
                    item(key = "more") {
                        Box(
                            Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            ViviLoadingIndicator()
                        }
                    }
                }

                if (vm.relatedItems.isNotEmpty() && !searching) {
                    item(key = "related_title") {
                        NavigationTitle(title = "You might also like")
                    }
                    item(key = "related") {
                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
                            items(vm.relatedItems, key = { it.id }) { item ->
                                YouTubeGridItem(
                                    item = item,
                                    onClick = {
                                        when (item) {
                                            is SongItem -> scope.launch(Dispatchers.IO) {
                                                controller.playVideo(item.id)
                                            }
                                            is AlbumItem -> navController.navigate(Routes.album(item.id))
                                            is ArtistItem -> navController.navigate(Routes.artist(item.id))
                                            is PlaylistItem -> navController.navigate(
                                                Routes.onlinePlaylist(item.id),
                                            )
                                        }
                                    },
                                    modifier = Modifier.width(160.dp),
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

@Composable
private fun OnlinePlaylistHeader(
    playlist: PlaylistItem,
    songs: List<SongItem>,
    playingId: String?,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onAuthorClick: () -> Unit,
) {
    val anyPlayingHere = songs.any { it.id == playingId }

    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(168.dp),
        ) {
            AsyncImage(
                model = playlist.thumbnail,
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
                model = playlist.thumbnail,
                contentDescription = playlist.title,
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
            text = playlist.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )

        playlist.author?.name?.let { author ->
            Text(
                text = author,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clickable(onClick = onAuthorClick),
            )
        }

        val totalDuration = songs.sumOf { it.duration ?: 0 }
        Text(
            text = buildString {
                append("${songs.size} songs")
                if (totalDuration > 0) {
                    append(" • ")
                    val hours = totalDuration / 3600
                    val minutes = (totalDuration % 3600) / 60
                    if (hours > 0) append("${hours}h ${minutes}m") else append("${minutes}m")
                }
                playlist.songCountText?.let { if (songs.isEmpty()) append(it) }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, start = 32.dp, end = 32.dp),
        )

        playlist.description?.takeIf { it.isNotBlank() }?.let { desc ->
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
