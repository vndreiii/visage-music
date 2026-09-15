package com.music.vivi.compose.ui.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import com.music.vivi.database.LocalMusicScanner
import com.music.vivi.compose.ui.component.PageLoadingState
import com.music.vivi.compose.ui.component.PageEmptyState
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.pages.LibraryPage
import com.music.vivi.compose.LocalPlaybackController
import com.music.vivi.compose.download.TrackDownloadService
import com.music.vivi.compose.navigation.Routes
import com.music.vivi.compose.ui.component.ChipsRow
import com.music.vivi.compose.ui.component.ViviLoadingIndicator
import com.music.vivi.compose.ui.component.YouTubeGridItem
import com.music.vivi.compose.ui.component.YouTubeListItem
import com.music.vivi.compose.ui.component.horizontalWheelScroll
import com.music.vivi.compose.ui.component.rememberNowPlayingIds
import com.music.vivi.core.youtube.YouTubeMusicService
import com.music.vivi.database.Downloaded_track
import com.music.vivi.database.LibraryRepository
import com.music.vivi.database.LocalMusicRepository
import com.music.vivi.database.Local_album
import com.music.vivi.database.Local_artist
import com.music.vivi.database.Local_track
import com.music.vivi.player.QueueTrack
import com.music.vivi.player.toQueueTrack
import com.music.vivi.compose.local.toQueueTrack
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

private val LikedBlue = Color(0xFF3E9BFF)

private enum class LibraryTab(val label: String) {
    Home("Home"),
    Songs("Songs"),
    Playlists("Playlists"),
    Albums("Albums"),
    Artists("Artists"),
    Local("Local"),
    Offline("Offline"),
    Liked("Liked"),
    Recent("Recent"),
}

private data class YtLibraryData(
    val playlists: List<PlaylistItem> = emptyList(),
    val songs: List<SongItem> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
    val artists: List<ArtistItem> = emptyList(),
    val recent: List<YTItem> = emptyList(),
    val error: String? = null,
)

private data class LocalFeed(
    val recentTracks: List<Local_track> = emptyList(),
    val recentAlbums: List<Local_album> = emptyList(),
    val heavy: List<Local_track> = emptyList(),
    val rediscover: List<Local_track> = emptyList(),
    val artists: List<Local_artist> = emptyList(),
    val albums: List<Local_album> = emptyList(),
    val tracks: List<Local_track> = emptyList(),
    val recentPlayed: List<Local_track> = emptyList(),
    val liked: List<Local_track> = emptyList(),
)

private fun Result<LibraryPage>.itemsOrEmpty(onFail: (Throwable) -> Unit): List<YTItem> =
    fold(
        onSuccess = { it.items },
        onFailure = {
            onFail(it)
            emptyList()
        },
    )

private suspend fun fetchYtLibrary(
    yt: YouTubeMusicService,
    libraryRepo: LibraryRepository,
): YtLibraryData = coroutineScope {
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
            author = com.music.innertube.models.Artist(name = "You", id = null),
            songCountText = "${songs.size} songs",
            thumbnail = songs.first().thumbnail,
            playEndpoint = null,
            shuffleEndpoint = null,
            radioEndpoint = null,
        )
        runCatching { libraryRepo.upsertPlaylistWithSongs(liked, songs) }
    }

    YtLibraryData(playlists, songs, albums, artists, recent, error)
}

/**
 * Library as a "Second Home": chips for sections, default feed from local index,
 * Offline from the download registry (not ~/Downloads).
 */
@Composable
fun LibraryScreen(navController: NavController) {
    val yt = koinInject<YouTubeMusicService>()
    val libraryRepo = koinInject<LibraryRepository>()
    val localRepo = koinInject<LocalMusicRepository>()
    val downloads = koinInject<TrackDownloadService>()
    val controller = LocalPlaybackController.current
    val scope = rememberCoroutineScope()
    val (playingId, isPlaying) = rememberNowPlayingIds()

    val scanner = koinInject<LocalMusicScanner>()
    val scanProgress by scanner.progress.collectAsState()
    val downloadStatus by downloads.status.collectAsState()
    var tab by rememberSaveable { mutableStateOf(LibraryTab.Home) }
    var collectionId by rememberSaveable { mutableStateOf<String?>(null) }
    var collectionTitle by rememberSaveable { mutableStateOf("") }
    var collectionArtist by rememberSaveable { mutableStateOf(false) }
    var collectionTracks by remember { mutableStateOf(emptyList<Local_track>()) }
    var collectionLoading by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    val tabStates = rememberSaveableStateHolder()
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var ytData by remember { mutableStateOf(YtLibraryData()) }
    var local by remember { mutableStateOf(LocalFeed()) }
    var offline by remember { mutableStateOf(emptyList<Downloaded_track>()) }

    suspend fun loadLocal() {
        try {
            local = withContext(Dispatchers.IO) {
                LocalFeed(
                    recentTracks = localRepo.recentlyAddedTracks(36),
                    recentAlbums = localRepo.recentlyAddedAlbums(24),
                    heavy = localRepo.heavyRotation(36),
                    rediscover = localRepo.rediscoverTracks(limit = 24),
                    artists = localRepo.allArtists(),
                    albums = localRepo.allAlbums(),
                    tracks = localRepo.allTracks(),
                    recentPlayed = localRepo.recentlyPlayedTracks(50),
                    liked = localRepo.allTracks().filter { libraryRepo.isLiked("local:${it.id}") },
                )
            }
            offline = withContext(Dispatchers.IO) { downloads.listOfflineTracks() }
            localError = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            localError = "Couldn't read your library: ${e.message}"
        } finally {
            loading = false
        }
    }

    suspend fun loadOnline() {
        refreshing = true
        try {
            ytData = withContext(Dispatchers.IO) { fetchYtLibrary(yt, libraryRepo) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ytData = ytData.copy(error = e.message ?: "Online library unavailable")
        } finally {
            refreshing = false
        }
    }

    LaunchedEffect(scanProgress.running, downloadStatus) {
        if (!scanProgress.running) loadLocal()
    }
    LaunchedEffect(Unit) { loadOnline() }
    LaunchedEffect(collectionId, collectionArtist, local) {
        val id = collectionId ?: return@LaunchedEffect
        collectionLoading = true
        try {
            collectionTracks = withContext(Dispatchers.IO) {
                if (collectionArtist) localRepo.tracksForArtist(id) else localRepo.tracksForAlbum(id)
            }
        } finally {
            collectionLoading = false
        }
    }
    fun openAlbum(album: Local_album) {
        collectionTitle = album.title
        collectionArtist = false
        collectionId = album.id
    }
    fun openArtist(artist: Local_artist) {
        collectionTitle = artist.name
        collectionArtist = true
        collectionId = artist.id
    }

    val onYtItem: (YTItem, List<SongItem>) -> Unit = { item, context ->
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

    val playLocalTracks: (List<Local_track>, Int) -> Unit = { tracks, index ->
        scope.launch(Dispatchers.IO) {
            controller.playLocalQueue(tracks.map { it.toQueueTrack() }, index)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Library", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { navController.navigate(Routes.SETTINGS_LOCAL_OFFLINE) }) { Text("Manage folders") }
            TextButton(enabled = !refreshing && !loading, onClick = { scope.launch { loadLocal(); loadOnline() } }) { Text("Refresh") }
        }
        ChipsRow(
            chips = LibraryTab.entries.map { it to it.label },
            currentValue = tab,
            onValueUpdate = { next -> if (next != null) { tab = next; collectionId = null } },
        )
        if (refreshing || scanProgress.running) {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
        }
        localError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        if (ytData.error != null && tab != LibraryTab.Home) {
            Text("Online library unavailable · Your local music is still available", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        if (collectionId != null) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { collectionId = null }) { Text("Back") }
                Column(Modifier.weight(1f)) {
                    Text(collectionTitle, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${collectionTracks.size} tracks", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(enabled = collectionTracks.isNotEmpty(), onClick = { playLocalTracks(collectionTracks, 0) }) { Text("Play all") }
                TextButton(enabled = collectionTracks.isNotEmpty(), onClick = { playLocalTracks(collectionTracks.shuffled(), 0) }) { Text("Shuffle") }
            }
            if (collectionLoading) PageLoadingState("Loading tracks", detail = true)
            else LocalTrackList(collectionTracks, playingId, isPlaying, playLocalTracks)
            return@Column
        }
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "libraryTab",
            modifier = Modifier.weight(1f),
        ) { selectedTab ->
        tabStates.SaveableStateProvider(selectedTab) {
        when {
            loading -> PageLoadingState("Opening your library")
            selectedTab == LibraryTab.Home -> SecondHomeFeed(
                yt = ytData,
                local = local,
                offlineCount = offline.size,
                playingId = playingId,
                isPlaying = isPlaying,
                onLikedClick = { tab = LibraryTab.Liked },
                onOfflineClick = { tab = LibraryTab.Offline },
                onPlayLocal = playLocalTracks,
                onPlayAlbum = ::openAlbum,
                onOpenArtist = ::openArtist,
                onManageFolders = { navController.navigate(Routes.SETTINGS_LOCAL_OFFLINE) },
                onYtItem = onYtItem,
                error = ytData.error,
                refreshing = refreshing,
                onRetry = {
                    scope.launch {
                        loadLocal()
                        loadOnline()
                    }
                },
            )
            selectedTab == LibraryTab.Liked -> RecentMixedList(
                recent = ytData.songs,
                localRecent = local.liked,
                playingId = playingId,
                isPlaying = isPlaying,
                onYtItem = onYtItem,
                onPlayLocal = playLocalTracks,
                emptyMessage = "Your liked songs will appear here",
            )
            selectedTab == LibraryTab.Offline -> OfflineList(
                tracks = offline,
                playingId = playingId,
                isPlaying = isPlaying,
                folder = downloads.resolveFolder().absolutePath,
                onPlay = { track, all ->
                    scope.launch(Dispatchers.IO) {
                        val q = all.map { it.toQueueTrack() }
                        val idx = all.indexOfFirst { it.video_id == track.video_id }.coerceAtLeast(0)
                        controller.playLocalQueue(q, idx)
                    }
                },
            )
            selectedTab == LibraryTab.Albums -> LocalAlbumsGrid(
                albums = if (local.albums.isNotEmpty()) local.albums else emptyList(),
                ytAlbums = ytData.albums,
                onLocalAlbum = ::openAlbum,
                onYtAlbum = { onYtItem(it, emptyList()) },
            )
            selectedTab == LibraryTab.Artists -> LocalArtistsGrid(
                artists = local.artists,
                ytArtists = ytData.artists,
                onLocalArtist = ::openArtist,
                onYtArtist = { onYtItem(it, emptyList()) },
            )
            selectedTab == LibraryTab.Playlists -> PlaylistsGrid(
                playlists = ytData.playlists,
                onItem = { onYtItem(it, emptyList()) },
            )
            selectedTab == LibraryTab.Songs -> LikedList(
                songs = ytData.songs,
                playingId = playingId,
                isPlaying = isPlaying,
                onItemClick = onYtItem,
                emptyMessage = "No songs in library yet",
            )
            selectedTab == LibraryTab.Local -> LocalTrackList(
                tracks = local.tracks,
                playingId = playingId,
                isPlaying = isPlaying,
                onPlay = playLocalTracks,
            )
            selectedTab == LibraryTab.Recent -> RecentMixedList(
                recent = ytData.recent,
                localRecent = local.recentPlayed,
                playingId = playingId,
                isPlaying = isPlaying,
                onYtItem = onYtItem,
                onPlayLocal = playLocalTracks,
            )
        }
    }
    }
    }
}

@Composable
private fun SecondHomeFeed(
    yt: YtLibraryData,
    local: LocalFeed,
    offlineCount: Int,
    playingId: String?,
    isPlaying: Boolean,
    onLikedClick: () -> Unit,
    onOfflineClick: () -> Unit,
    onPlayLocal: (List<Local_track>, Int) -> Unit,
    onPlayAlbum: (Local_album) -> Unit,
    onOpenArtist: (Local_artist) -> Unit,
    onManageFolders: () -> Unit,
    onYtItem: (YTItem, List<SongItem>) -> Unit,
    error: String?,
    refreshing: Boolean,
    onRetry: () -> Unit,
) {
    val emptyLocal = local.tracks.isEmpty() && local.albums.isEmpty()
    LazyColumn(
        contentPadding = PaddingValues(bottom = 128.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LikedTile(
                    songCount = yt.songs.size,
                    onClick = onLikedClick,
                    modifier = Modifier.weight(1f),
                )
                OfflineTile(
                    count = offlineCount,
                    onClick = onOfflineClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (error != null) {
            item {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Online library unavailable", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onRetry, enabled = !refreshing) { Text("Retry") }
                }
            }
        }
        if (emptyLocal) {
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Make yourself at home", style = MaterialTheme.typography.headlineSmall)
                        Text("Add your music folders to find albums, recent additions and your most-played tracks here.")
                        Button(onClick = onManageFolders) { Text("Add music folders") }
                    }
                }
            }
        }
        if (local.recentTracks.isNotEmpty()) {
            item {
                LocalTrackShelf(
                    title = "Recently added",
                    tracks = local.recentTracks,
                    playingId = playingId,
                    isPlaying = isPlaying,
                    onPlay = onPlayLocal,
                )
            }
        }
        if (yt.playlists.isNotEmpty()) {
            item {
                Text("Your playlists", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                val rowState = rememberLazyListState()
                LazyRow(state = rowState, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.horizontalWheelScroll(rowState)) {
                    items(yt.playlists.distinctBy { it.id }, key = { it.id }) { playlist ->
                        YouTubeGridItem(item = playlist, onClick = { onYtItem(playlist, emptyList()) }, modifier = Modifier.width(172.dp))
                    }
                }
            }
        }
        if (local.recentAlbums.isNotEmpty()) {
            item {
                LocalAlbumShelf(
                    title = "New local albums",
                    albums = local.recentAlbums,
                    onClick = onPlayAlbum,
                )
            }
        }
        if (local.heavy.isNotEmpty()) {
            item {
                LocalTrackShelf(
                    title = "Heavy rotation",
                    tracks = local.heavy,
                    playingId = playingId,
                    isPlaying = isPlaying,
                    onPlay = onPlayLocal,
                )
            }
        }
        if (local.rediscover.isNotEmpty()) {
            item {
                LocalTrackShelf(
                    title = "Rediscover",
                    tracks = local.rediscover,
                    playingId = playingId,
                    isPlaying = isPlaying,
                    onPlay = onPlayLocal,
                )
            }
        }
        if (local.artists.isNotEmpty()) {
            item {
                LocalArtistShelf(title = "Local artists", artists = local.artists.take(24), onClick = onOpenArtist)
            }
        }

    }
}

@Composable
private fun LikedTile(
    songCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(LikedBlue),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Liked", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (songCount == 0) "Songs you've liked" else "$songCount songs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OfflineTile(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF4DB6AC)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Download, contentDescription = null, tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Offline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (count == 0) "App downloads" else "$count tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LocalTrackShelf(
    title: String,
    tracks: List<Local_track>,
    playingId: String?,
    isPlaying: Boolean,
    onPlay: (List<Local_track>, Int) -> Unit,
) {
    Column(Modifier.padding(top = 12.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        val rowState = rememberLazyListState()
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalWheelScroll(rowState),
        ) {
            items(tracks, key = { it.id }) { track ->
                LocalTrackCard(
                    track = track,
                    active = playingId == "local:${track.id}",
                    isPlaying = isPlaying,
                    onClick = {
                        val idx = tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                        onPlay(tracks, idx)
                    },
                )
            }
        }
    }
}

@Composable
private fun LocalAlbumShelf(
    title: String,
    albums: List<Local_album>,
    onClick: (Local_album) -> Unit,
) {
    Column(Modifier.padding(top = 12.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        val rowState = rememberLazyListState()
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalWheelScroll(rowState),
        ) {
            items(albums, key = { it.id }) { album ->
                LocalCoverCard(
                    title = album.title,
                    subtitle = album.artist_name,
                    coverPath = album.cover_path,
                    badge = null,
                    onClick = { onClick(album) },
                )
            }
        }
    }
}

@Composable
private fun LocalArtistShelf(
    title: String,
    artists: List<Local_artist>,
    onClick: (Local_artist) -> Unit,
) {
    Column(Modifier.padding(top = 12.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        val rowState = rememberLazyListState()
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalWheelScroll(rowState),
        ) {
            items(artists, key = { it.id }) { artist ->
                Surface(
                    onClick = { onClick(artist) },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.width(140.dp),
                ) {
                    Column(
                        Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(48.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.Person, contentDescription = null)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            artist.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalTrackCard(
    track: Local_track,
    active: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    LocalCoverCard(
        title = track.title,
        subtitle = track.artist_name,
        coverPath = track.cover_path,
        badge = if (track.is_lossless != 0L) "Lossless" else null,
        active = active,
        onClick = onClick,
    )
}

@Composable
private fun LocalCoverCard(
    title: String,
    subtitle: String,
    coverPath: String?,
    badge: String?,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (active) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        modifier = Modifier.width(148.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Box(
                Modifier
                    .size(128.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (coverPath != null) {
                    AsyncImage(
                        model = File(coverPath),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        Icons.Outlined.Album,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.Center).size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (badge != null) {
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LikedList(
    songs: List<SongItem>,
    playingId: String?,
    isPlaying: Boolean,
    onItemClick: (YTItem, List<SongItem>) -> Unit,
    emptyMessage: String = "No liked songs yet",
) {
    if (songs.isEmpty()) {
        EmptyContent(emptyMessage)
    } else {
        LazyColumn(contentPadding = PaddingValues(bottom = 128.dp)) {
            items(songs, key = { it.id }) { song ->
                YouTubeListItem(
                    item = song,
                    isActive = song.id == playingId,
                    isPlaying = isPlaying,
                    onClick = { onItemClick(song, songs) },
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun LocalTrackList(
    tracks: List<Local_track>,
    playingId: String?,
    isPlaying: Boolean,
    onPlay: (List<Local_track>, Int) -> Unit,
) {
    if (tracks.isEmpty()) {
        EmptyContent("No local tracks — add folders in Settings → Local / Offline")
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 128.dp)) {
        items(tracks, key = { it.id }) { track ->
            val id = "local:${track.id}"
            Surface(
                onClick = {
                    val idx = tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                    onPlay(tracks, idx)
                },
                shape = RoundedCornerShape(12.dp),
                color = if (playingId == id && isPlaying) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    Color.Transparent
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (track.cover_path != null) {
                        AsyncImage(
                            model = File(track.cover_path),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                    } else {
                        Icon(
                            Icons.Outlined.LibraryMusic,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            track.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            buildString {
                                append(track.artist_name)
                                if (track.is_lossless != 0L) append(" · Lossless")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentMixedList(
    recent: List<YTItem>,
    localRecent: List<Local_track>,
    playingId: String?,
    isPlaying: Boolean,
    onYtItem: (YTItem, List<SongItem>) -> Unit,
    onPlayLocal: (List<Local_track>, Int) -> Unit,
    emptyMessage: String = "Nothing recent yet",
) {
    if (recent.isEmpty() && localRecent.isEmpty()) {
        EmptyContent(emptyMessage)
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 128.dp)) {
        if (localRecent.isNotEmpty()) {
            item {
                Text(
                    "Local",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(localRecent, key = { "lr-${it.id}" }) { track ->
                val id = "local:${track.id}"
                Surface(
                    onClick = {
                        val idx = localRecent.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                        onPlayLocal(localRecent, idx)
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (playingId == id) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        Color.Transparent
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.LibraryMusic, null, Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(track.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                track.artist_name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        if (recent.isNotEmpty()) {
            item {
                Text(
                    "YouTube",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(recent, key = { "yt-${it.id}" }) { item ->
                when (item) {
                    is SongItem -> YouTubeListItem(
                        item = item,
                        isActive = item.id == playingId,
                        isPlaying = isPlaying,
                        onClick = { onYtItem(item, emptyList()) },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    else -> YouTubeListItem(
                        item = item,
                        isActive = item.id == playingId,
                        isPlaying = isPlaying,
                        onClick = { onYtItem(item, emptyList()) },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun OfflineList(
    tracks: List<Downloaded_track>,
    playingId: String?,
    isPlaying: Boolean,
    folder: String,
    onPlay: (Downloaded_track, List<Downloaded_track>) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Text(
            folder,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        if (tracks.isEmpty()) {
            EmptyContent("Nothing available offline yet")
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 128.dp)) {
                items(tracks, key = { it.video_id }) { track ->
                    val id = "offline:${track.video_id}"
                    Surface(
                        onClick = { onPlay(track, tracks) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (playingId == id) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            Color.Transparent
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (track.cover_path != null) {
                                AsyncImage(
                                    model = File(track.cover_path),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                )
                            } else {
                                Icon(
                                    Icons.Outlined.Download,
                                    contentDescription = null,
                                    tint = Color(0xFF4DB6AC),
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    track.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    track.artist.ifBlank { File(track.file_path).extension.uppercase() },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun LocalAlbumsGrid(
    albums: List<Local_album>,
    ytAlbums: List<AlbumItem>,
    onLocalAlbum: (Local_album) -> Unit,
    onYtAlbum: (AlbumItem) -> Unit,
) {
    if (albums.isEmpty() && ytAlbums.isEmpty()) {
        EmptyContent("No albums yet — scan local folders or like albums on YouTube")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 164.dp),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 128.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(albums, key = { "l-${it.id}" }) { album ->
            LocalCoverCard(
                title = album.title,
                subtitle = album.artist_name,
                coverPath = album.cover_path,
                badge = null,
                onClick = { onLocalAlbum(album) },
            )
        }
        items(ytAlbums, key = { "y-${it.id}" }) { album ->
            YouTubeGridItem(
                item = album,
                onClick = { onYtAlbum(album) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LocalArtistsGrid(
    artists: List<Local_artist>,
    ytArtists: List<ArtistItem>,
    onLocalArtist: (Local_artist) -> Unit,
    onYtArtist: (ArtistItem) -> Unit,
) {
    if (artists.isEmpty() && ytArtists.isEmpty()) {
        EmptyContent("No artists yet")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 164.dp),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 128.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(artists, key = { "l-${it.id}" }) { artist ->
            Surface(
                onClick = { onLocalArtist(artist) },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        artist.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        items(ytArtists, key = { "y-${it.id}" }) { artist ->
            YouTubeGridItem(
                item = artist,
                onClick = { onYtArtist(artist) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PlaylistsGrid(
    playlists: List<PlaylistItem>,
    onItem: (PlaylistItem) -> Unit,
) {
    if (playlists.isEmpty()) {
        EmptyContent("No playlists yet")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 164.dp),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 128.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(playlists, key = { it.id }) { pl ->
            YouTubeGridItem(
                item = pl,
                onClick = { onItem(pl) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
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
