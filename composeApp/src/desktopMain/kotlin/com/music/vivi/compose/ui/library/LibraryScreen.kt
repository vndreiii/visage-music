package com.music.vivi.compose.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.music.vivi.compose.ui.component.ViviLoadingIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.pages.LibraryPage
import com.music.vivi.compose.LocalPlaybackController
import com.music.vivi.compose.navigation.Routes
import com.music.vivi.compose.ui.component.ChipsRow
import com.music.vivi.compose.ui.component.YouTubeGridItem
import com.music.vivi.compose.ui.component.YouTubeListItem
import com.music.vivi.compose.ui.component.rememberNowPlayingIds
import com.music.vivi.core.youtube.YouTubeMusicService
import com.music.vivi.database.LibraryRepository
import com.music.vivi.player.toQueueTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

private val LikedBlue = Color(0xFF3E9BFF)
private val DownloadTeal = Color(0xFF4DB6AC)
private val AlbumPurple = Color(0xFFBA68C8)
private val ArtistOrange = Color(0xFFFFB74D)
private val PlaylistPink = Color(0xFFEC6E8C)
private val RecentCyan = Color(0xFF4DD0E1)

private enum class LibraryCategory(val label: String) {
    Liked("Liked"),
    Downloaded("Offline"),
    Albums("Albums"),
    Artists("Artists"),
    Playlists("Playlists"),
    Recent("Recent"),
}

private data class CategorySpec(
    val category: LibraryCategory,
    val icon: ImageVector,
    val tint: Color,
)

private data class LibraryData(
    val playlists: List<PlaylistItem> = emptyList(),
    val songs: List<SongItem> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
    val artists: List<ArtistItem> = emptyList(),
    val recent: List<YTItem> = emptyList(),
    val error: String? = null,
)

private fun Result<LibraryPage>.itemsOrEmpty(onFail: (Throwable) -> Unit): List<YTItem> =
    fold(
        onSuccess = { it.items },
        onFailure = {
            onFail(it)
            emptyList()
        },
    )

private suspend fun fetchLibrary(
    yt: YouTubeMusicService,
    libraryRepo: LibraryRepository,
): LibraryData = coroutineScope {
    var error: String? = null
    fun note(label: String, t: Throwable) {
        if (error == null) error = "$label: ${t.message}"
    }

    val p = async { yt.libraryLikedPlaylists() }
    val s = async { yt.libraryLikedSongs() }
    val a = async { yt.libraryLikedAlbums() }
    val ar = async { yt.libraryArtists() }
    val r = async { yt.libraryRecentActivity() }

    val playlists = p.await().itemsOrEmpty { note("Playlists", it) }.filterIsInstance<PlaylistItem>()
    val songs = s.await().itemsOrEmpty { note("Songs", it) }.filterIsInstance<SongItem>()
    val albums = a.await().itemsOrEmpty { note("Albums", it) }.filterIsInstance<AlbumItem>()
    val artists = ar.await().itemsOrEmpty { note("Artists", it) }.filterIsInstance<ArtistItem>()
    val recent = r.await().itemsOrEmpty { /* soft-fail */ }

    if (songs.isNotEmpty()) {
        val liked = PlaylistItem(
            id = "LM",
            title = "Liked Music",
            author = Artist(name = "You", id = null),
            songCountText = "${songs.size} songs",
            thumbnail = songs.first().thumbnail,
            playEndpoint = null,
            shuffleEndpoint = null,
            radioEndpoint = null,
        )
        runCatching { libraryRepo.upsertPlaylistWithSongs(liked, songs) }
    }

    LibraryData(playlists, songs, albums, artists, recent, error)
}

private fun categorySubtitle(category: LibraryCategory, data: LibraryData): String? = when (category) {
    LibraryCategory.Downloaded -> null
    LibraryCategory.Albums -> if (data.albums.isEmpty()) null else "${data.albums.size} albums"
    LibraryCategory.Artists -> if (data.artists.isEmpty()) null else "${data.artists.size} artists"
    LibraryCategory.Playlists -> if (data.playlists.isEmpty()) null else "${data.playlists.size} playlists"
    LibraryCategory.Recent -> if (data.recent.isEmpty()) null else "${data.recent.size} items"
    LibraryCategory.Liked -> null
}

/**
 * Tablet-style Library: large title, Liked hero row, and a grid of category cards.
 * Keeps the YouTube fetch + [LibraryRepository] cache for Liked Music.
 */
@Composable
fun LibraryScreen(navController: NavController) {
    val yt = koinInject<YouTubeMusicService>()
    val libraryRepo = koinInject<LibraryRepository>()
    val controller = LocalPlaybackController.current
    val scope = rememberCoroutineScope()
    val (playingId, isPlaying) = rememberNowPlayingIds()

    var selected by remember { mutableStateOf<LibraryCategory?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var data by remember { mutableStateOf(LibraryData()) }

    LaunchedEffect(Unit) {
        loading = true
        data = fetchLibrary(yt, libraryRepo)
        loading = false
    }

    val categories = listOf(
        CategorySpec(LibraryCategory.Downloaded, Icons.Outlined.Download, DownloadTeal),
        CategorySpec(LibraryCategory.Albums, Icons.Outlined.Album, AlbumPurple),
        CategorySpec(LibraryCategory.Artists, Icons.Outlined.Person, ArtistOrange),
        CategorySpec(LibraryCategory.Playlists, Icons.AutoMirrored.Outlined.QueueMusic, PlaylistPink),
        CategorySpec(LibraryCategory.Recent, Icons.Outlined.History, RecentCyan),
    )

    val onItemClick: (YTItem, List<SongItem>) -> Unit = { item, context ->
        when (item) {
            is SongItem -> scope.launch(Dispatchers.IO) {
                if (context.isNotEmpty()) {
                    val idx = context.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
                    controller.playQueue(context.map { it.toQueueTrack() }, idx)
                } else if (item.id == playingId) {
                    if (isPlaying) controller.pause() else controller.play()
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
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ViviLoadingIndicator()
        }
        selected == null -> LibraryOverview(
            data = data,
            categories = categories,
            onLikedClick = { selected = LibraryCategory.Liked },
            onCategoryClick = { selected = it },
            error = data.error,
            refreshing = refreshing,
            onRetry = {
                scope.launch {
                    refreshing = true
                    data = fetchLibrary(yt, libraryRepo)
                    refreshing = false
                }
            },
        )
        else -> selected?.let { category ->
            CategoryContent(
                category = category,
                data = data,
                playingId = playingId,
                isPlaying = isPlaying,
                onSelect = { selected = it },
                onItemClick = onItemClick,
            )
        }
    }
}

@Composable
private fun LibraryOverview(
    data: LibraryData,
    categories: List<CategorySpec>,
    onLikedClick: () -> Unit,
    onCategoryClick: (LibraryCategory) -> Unit,
    error: String?,
    refreshing: Boolean,
    onRetry: () -> Unit,
) {
    val allEmpty = data.playlists.isEmpty() &&
        data.songs.isEmpty() &&
        data.albums.isEmpty() &&
        data.artists.isEmpty() &&
        data.recent.isEmpty()

    if (allEmpty && error != null) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(error, style = MaterialTheme.typography.bodyLarge)
            Button(
                onClick = onRetry,
                modifier = Modifier.padding(top = 12.dp),
                enabled = !refreshing,
            ) { Text(if (refreshing) "Refreshing…" else "Retry") }
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = "Library",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            LikedCard(songCount = data.songs.size, onClick = onLikedClick)
        }
        items(categories, key = { it.category }) { spec ->
            CategoryCard(
                label = spec.category.label,
                subtitle = categorySubtitle(spec.category, data),
                icon = spec.icon,
                tint = spec.tint,
                onClick = { onCategoryClick(spec.category) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LikedCard(
    songCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(LikedBlue),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = "Liked",
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Liked",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (songCount == 0) "Songs you've liked" else "$songCount songs",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        }
    }
}

@Composable
private fun CategoryCard(
    label: String,
    subtitle: String?,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(tint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        }
    }
}

@Composable
private fun CategoryContent(
    category: LibraryCategory,
    data: LibraryData,
    playingId: String?,
    isPlaying: Boolean,
    onSelect: (LibraryCategory?) -> Unit,
    onItemClick: (YTItem, List<SongItem>) -> Unit,
) {
    val downloads = koinInject<com.music.vivi.compose.download.TrackDownloadService>()
    var localFiles by remember { mutableStateOf(emptyList<java.io.File>()) }
    LaunchedEffect(category) {
        if (category == LibraryCategory.Downloaded) {
            localFiles = withContext(Dispatchers.IO) { downloads.listDownloadedAudio() }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
        ) {
            Text(
                text = category.label,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            if (category == LibraryCategory.Downloaded) {
                Text(
                    downloads.resolveFolder().absolutePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        ChipsRow(
            chips = LibraryCategory.entries.map { it to it.label },
            currentValue = category,
            onValueUpdate = onSelect,
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val gridItems: List<YTItem> = when (category) {
                LibraryCategory.Liked -> emptyList()
                LibraryCategory.Downloaded -> emptyList()
                LibraryCategory.Albums -> data.albums
                LibraryCategory.Artists -> data.artists
                LibraryCategory.Playlists -> data.playlists
                LibraryCategory.Recent -> data.recent
            }
            when (category) {
                LibraryCategory.Liked -> if (data.songs.isEmpty()) {
                    EmptyContent("No liked songs yet")
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                        items(data.songs, key = { it.id }) { song ->
                            YouTubeListItem(
                                item = song,
                                isActive = song.id == playingId,
                                isPlaying = isPlaying,
                                onClick = { onItemClick(song, data.songs) },
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }
                    }
                }
                LibraryCategory.Downloaded -> if (localFiles.isEmpty()) {
                    EmptyContent("Nothing available offline yet")
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                        items(localFiles, key = { it.absolutePath }) { file ->
                            Surface(
                                onClick = {
                                    runCatching {
                                        java.awt.Desktop.getDesktop().open(file)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Outlined.Download,
                                        contentDescription = null,
                                        tint = DownloadTeal,
                                        modifier = Modifier.size(28.dp),
                                    )
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            file.nameWithoutExtension,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            "${file.extension.uppercase()} · ${formatFileSize(file.length())}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                else -> if (gridItems.isEmpty()) {
                    EmptyContent("No ${category.label.lowercase()} yet")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 140.dp),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(gridItems, key = { it.id }) { item ->
                            YouTubeGridItem(
                                item = item,
                                onClick = { onItemClick(item, emptyList()) },
                                modifier = Modifier.fillMaxWidth(),
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

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

@Composable
private fun EmptyContent(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}