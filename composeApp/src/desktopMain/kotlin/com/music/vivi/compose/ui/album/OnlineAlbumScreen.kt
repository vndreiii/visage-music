package com.music.vivi.compose.ui.album

import com.music.vivi.compose.ui.component.MiniPlayerClearance
import com.music.vivi.compose.ui.component.PageHeaderTopPadding
import com.music.vivi.compose.ui.component.PageInset
import com.music.vivi.compose.ui.component.PageHeaderBottomPadding
import com.music.vivi.compose.ui.component.CoverImage
import com.music.vivi.compose.ui.component.WheelLazyRow
import com.music.vivi.compose.ui.component.smoothWheelScroll
import com.music.vivi.compose.ui.component.pageScrollEdge
import com.music.vivi.compose.ui.component.edgeProgress

import androidx.compose.foundation.background
import androidx.compose.material.icons.outlined.Link
import androidx.compose.ui.draw.blur
import com.music.vivi.compose.ui.artist.ArtistBackBar
import com.music.vivi.compose.ui.artist.ArtistLinks
import com.music.vivi.compose.ui.artist.openArtistByName
import com.music.vivi.compose.ui.component.MediaHeaderAction
import com.music.vivi.compose.ui.component.MediaPageHeader
import com.music.vivi.compose.ui.component.SystemClipboard
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
import com.music.vivi.compose.ui.component.PageSkeleton
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
            PageLoadingState("Loading album", PageSkeleton.MediaSquare)
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
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
                    .pageScrollEdge { listState.edgeProgress() }.smoothWheelScroll(listState),
                contentPadding = PaddingValues(top = 12.dp, bottom = MiniPlayerClearance),
            ) {
                item(key = "toolbar") { ArtistBackBar { navController.navigateUp() } }

                item(key = "header") {
                    OnlineAlbumHeader(
                        page = page,
                        songs = vm.songs,
                        playingId = playingId,
                        isPlaying = isPlaying,
                        onPlay = { playFrom(0) },
                        onShuffle = { playFrom(0, shuffled = true) },
                        navController = navController,
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
                        WheelLazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
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
                        WheelLazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
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

/** YouTube cover URLs end in `=w544-h544`-style size suffixes; ask for a size that stays sharp at the header's big cover. */
internal fun bigCover(url: String): String = url.replace(Regex("=w\\d+-h\\d+[^/]*$"), "=w1000-h1000")

internal fun albumDurationText(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours} hr ${minutes} min" else "$minutes min"
}

/** The album page's top: the artist page's header card with the album cover shown large instead of a portrait. */
@Composable
private fun OnlineAlbumHeader(
    page: AlbumPage,
    songs: List<SongItem>,
    playingId: String?,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    navController: NavController,
) {
    val album = page.album
    val anyPlayingHere = songs.any { it.id == playingId }
    val artists = album.artists.orEmpty()
    val cover = remember(album.thumbnail) { bigCover(album.thumbnail) }
    val totalDuration = songs.sumOf { it.duration ?: 0 }
    val meta = listOfNotNull(
        album.year?.toString(),
        "${songs.size} tracks".takeIf { songs.isNotEmpty() },
        albumDurationText(totalDuration).takeIf { totalDuration > 0 },
    ).joinToString(" • ")

    MediaPageHeader(
        title = album.title,
        meta = meta,
        description = page.description,
        onPlay = onPlay,
        onShuffle = onShuffle,
        playLabel = if (isPlaying && anyPlayingHere) "Pause" else "Play",
        banner = {
            AsyncImage(
                model = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(36.dp).graphicsLayer { scaleX = 1.2f; scaleY = 1.2f },
            )
        },
        artwork = { CoverImage(cover, album.id, Modifier.fillMaxSize(), animated = true) },
        artworkShape = RoundedCornerShape(24.dp),
        artworkSize = { (it * 0.6f).coerceIn(180.dp, 340.dp) },
        subtitle = if (artists.isEmpty()) null else {
            {
                ArtistLinks(
                    credits = artists.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    hoverColor = MaterialTheme.colorScheme.onSurface,
                    onOpen = { name ->
                        val id = artists.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id
                        if (id != null) navController.navigate(Routes.artist(id)) else navController.openArtistByName(name)
                    },
                    maxLines = 2,
                )
            }
        },
        actions = {
            MediaHeaderAction(Icons.Outlined.Link, "Copy link") {
                SystemClipboard.copy("https://music.youtube.com/playlist?list=${page.album.playlistId}")
            }
        },
    )
}
