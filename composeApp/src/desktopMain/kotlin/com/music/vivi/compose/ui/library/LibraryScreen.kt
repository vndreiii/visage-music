package com.music.vivi.compose.ui.library

import com.music.vivi.compose.ui.component.smoothWheelScroll

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
import com.music.vivi.database.DatabaseFactory
import com.music.vivi.database.OpusCoverExtractor
import com.music.vivi.database.LocalMusicScanner
import com.music.vivi.compose.ui.component.PageLoadingState
import com.music.vivi.compose.ui.component.PageEmptyState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.withLock
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
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
import com.music.vivi.compose.ui.component.VisageLoadingIndicator
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

/** Lets the top bar tell the Library to return to its home when its tab is tapped again. */
object LibraryNavigator {
    private val _resets = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val resets: kotlinx.coroutines.flow.SharedFlow<Unit> = _resets
    fun reset() {
        _resets.tryEmit(Unit)
    }
}

/** What the Library is currently drilled into (shown as a track list with Play all / Shuffle). */
internal sealed interface OpenCollection {
    val title: String
    data class Album(val id: String, override val title: String) : OpenCollection
    data class Artist(val id: String, override val title: String) : OpenCollection
    data class Genre(val name: String) : OpenCollection { override val title get() = name }
    data class Year(val year: Long) : OpenCollection { override val title get() = "$year" }
    data class Composer(val name: String) : OpenCollection { override val title get() = name }
    data class Mix(override val title: String, val tracks: List<Local_track>) : OpenCollection
}

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

data class YtLibraryData(
    val playlists: List<PlaylistItem> = emptyList(),
    val songs: List<SongItem> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
    val artists: List<ArtistItem> = emptyList(),
    val recent: List<YTItem> = emptyList(),
    val error: String? = null,
)

data class LocalFeed(
    val recentTracks: List<Local_track> = emptyList(),
    val recentAlbums: List<Local_album> = emptyList(),
    val heavy: List<Local_track> = emptyList(),
    val rediscover: List<Local_track> = emptyList(),
    val artists: List<Local_artist> = emptyList(),
    val albums: List<Local_album> = emptyList(),
    val tracks: List<Local_track> = emptyList(),
    val recentPlayed: List<Local_track> = emptyList(),
    val liked: List<Local_track> = emptyList(),
    val genres: List<LocalMusicRepository.Counted<String>> = emptyList(),
    val years: List<LocalMusicRepository.Counted<Long>> = emptyList(),
    val composers: List<LocalMusicRepository.Counted<String>> = emptyList(),
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

@Composable
fun LazyCoverImage(
    coverPath: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    /** Stable seed for the generated fallback cover (title/id); defaults to the cover path. */
    seed: String? = null,
) {
    var finalPath by remember(coverPath) { mutableStateOf(if (coverPath?.startsWith("opus-lazy://") == true) null else coverPath) }
    
    LaunchedEffect(coverPath) {
        if (coverPath != null && coverPath.startsWith("opus-lazy://")) {
            val audioPath = coverPath.substring("opus-lazy://".length)
            val extracted = OpusCoverExtractor.extractCover(audioPath, DatabaseFactory.coversDir())
            if (extracted != null) {
                finalPath = extracted
                try {
                    val localRepo = GlobalContext.get().get<LocalMusicRepository>()
                    localRepo.updateCoverPathForOpusLazy(coverPath, extracted)
                } catch (e: Exception) {}
            }
        }
    }
    
    com.music.vivi.compose.ui.component.CoverImage(
        finalPath?.let { File(it) },
        seed = seed ?: coverPath ?: "none",
        modifier = modifier,
        contentScale = contentScale,
    )
}

class LibraryCache(
    private val yt: YouTubeMusicService,
    private val libraryRepo: LibraryRepository,
    private val localRepo: LocalMusicRepository,
    private val downloads: TrackDownloadService
) {
    val local = MutableStateFlow(LocalFeed())
    val offline = MutableStateFlow<List<Downloaded_track>>(emptyList())
    val ytData = MutableStateFlow(YtLibraryData())
    
    val loading = MutableStateFlow(true)
    val refreshing = MutableStateFlow(false)
    val localError = MutableStateFlow<String?>(null)

    private var localLoadedAt = 0L
    val hasLoadedLocal get() = localLoadedAt != 0L
    var lastDownloadStatus: Any? = null
    private var onlineLoadedAt = 0L
    private val localMutex = kotlinx.coroutines.sync.Mutex()
    private val onlineMutex = kotlinx.coroutines.sync.Mutex()

    private fun stale(at: Long) = at == 0L || System.currentTimeMillis() - at > TTL_MS

    /** Stale-while-revalidate: cached data stays visible; reload only if never loaded or past TTL. */
    suspend fun refreshIfStale() {
        if (stale(localLoadedAt)) loadLocal()
        if (stale(onlineLoadedAt)) loadOnline()
    }

    /** Cheap refresh of just the liked-local list (one query) after a like/unlike. */
    suspend fun refreshLiked() {
        if (localLoadedAt == 0L) return
        try {
            val likedIds = withContext(Dispatchers.IO) { libraryRepo.likedSongs().map { it.id }.toHashSet() }
            local.update { f -> f.copy(liked = f.tracks.filter { "local:${it.id}" in likedIds }) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    suspend fun loadLocal() {
        localMutex.withLock { loadLocalLocked() }
    }

    private suspend fun loadLocalLocked() {
        try {
            val nextLocal = withContext(Dispatchers.IO) {
                val allTracks = localRepo.allTracks()
                val likedIds = libraryRepo.likedSongs().map { it.id }.toHashSet()
                LocalFeed(
                    recentTracks = localRepo.recentlyAddedTracks(36),
                    recentAlbums = localRepo.recentlyAddedAlbums(24),
                    heavy = localRepo.heavyRotation(36),
                    rediscover = localRepo.rediscoverTracks(limit = 24),
                    artists = localRepo.allArtists(),
                    albums = localRepo.allAlbums(),
                    tracks = allTracks,
                    recentPlayed = localRepo.recentlyPlayedTracks(50),
                    liked = allTracks.filter { "local:${it.id}" in likedIds },
                    genres = localRepo.genres(),
                    years = localRepo.years(),
                    composers = localRepo.composers(),
                )
            }
            val nextOffline = withContext(Dispatchers.IO) { downloads.listOfflineTracks() }
            local.value = nextLocal
            offline.value = nextOffline
            localError.value = null
            localLoadedAt = System.currentTimeMillis()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            localError.value = "Couldn't read your library: ${e.message}"
        } finally {
            loading.value = false
        }
    }

    suspend fun loadOnline() {
        onlineMutex.withLock { loadOnlineLocked() }
    }

    private suspend fun loadOnlineLocked() {
        refreshing.value = true
        try {
            val fresh = withContext(Dispatchers.IO) { fetchYtLibrary(yt, libraryRepo) }
            // Keep previously good data if a refresh fails outright (offline) instead of blanking the lists.
            val prev = ytData.value
            ytData.value = if (fresh.error != null && prev.error == null &&
                fresh.songs.isEmpty() && fresh.playlists.isEmpty() && fresh.albums.isEmpty() && fresh.artists.isEmpty() &&
                (prev.songs.isNotEmpty() || prev.playlists.isNotEmpty() || prev.albums.isNotEmpty() || prev.artists.isNotEmpty())
            ) prev.copy(error = fresh.error) else fresh
            if (fresh.error == null) onlineLoadedAt = System.currentTimeMillis()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ytData.update { it.copy(error = e.message ?: "Online library unavailable") }
        } finally {
            refreshing.value = false
        }
    }
}

private const val TTL_MS = 5 * 60 * 1000L

val libraryModule = module {
    single { LibraryCache(get(), get(), get(), get()) }
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
    val cache = remember {
        try {
            GlobalContext.get().get<LibraryCache>()
        } catch (e: Exception) {
            loadKoinModules(libraryModule)
            GlobalContext.get().get<LibraryCache>()
        }
    }
    
    val controller = LocalPlaybackController.current
    val scope = rememberCoroutineScope()
    val (playingId, isPlaying) = rememberNowPlayingIds()

    val scanner = koinInject<LocalMusicScanner>()
    val scanProgress by scanner.progress.collectAsState()
    val downloadStatus by downloads.status.collectAsState()
    
    var tab by rememberSaveable { mutableStateOf(LibraryTab.Home) }
    var openCollection by remember { mutableStateOf<OpenCollection?>(null) }
    var collectionTracks by remember { mutableStateOf(emptyList<Local_track>()) }
    var collectionLoading by remember { mutableStateOf(false) }
    
    val tabStates = rememberSaveableStateHolder()
    
    val loading by cache.loading.collectAsState()
    val refreshing by cache.refreshing.collectAsState()
    val ytData by cache.ytData.collectAsState()
    val local by cache.local.collectAsState()
    val offline by cache.offline.collectAsState()
    val localError by cache.localError.collectAsState()

    var homeResetTick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        LibraryNavigator.resets.collect {
            tab = LibraryTab.Home
            openCollection = null
            homeResetTick++
        }
    }

    // Cached data renders immediately; these only revalidate in the background.
    LaunchedEffect(Unit) { cache.refreshIfStale() }
    // Scan finished or a download finished/failed: local index changed, force a reload.
    var firstMutationPass by remember { mutableStateOf(true) }
    LaunchedEffect(scanProgress.running, downloadStatus) {
        firstMutationPass = false
        // Download status is remembered in the cache so a change made while Library was closed is caught.
        val changed = cache.lastDownloadStatus != downloadStatus
        cache.lastDownloadStatus = downloadStatus
        if (changed && !scanProgress.running && cache.hasLoadedLocal) cache.loadLocal()
    }
    val likeMutations by libraryRepo.mutations.collectAsState()
    LaunchedEffect(likeMutations) { if (likeMutations != 0L) cache.refreshLiked() }
    
    LaunchedEffect(openCollection, local) {
        val open = openCollection ?: return@LaunchedEffect
        collectionLoading = true
        try {
            collectionTracks = withContext(Dispatchers.IO) {
                when (open) {
                    is OpenCollection.Album -> localRepo.tracksForAlbum(open.id)
                    is OpenCollection.Artist -> localRepo.tracksCreditedTo(open.id).ifEmpty { localRepo.tracksForArtist(open.id) }
                    is OpenCollection.Genre -> localRepo.tracksByGenre(open.name)
                    is OpenCollection.Year -> localRepo.tracksByYear(open.year)
                    is OpenCollection.Composer -> localRepo.tracksByComposer(open.name)
                    is OpenCollection.Mix -> open.tracks
                }
            }
        } finally {
            collectionLoading = false
        }
    }
    fun openAlbum(album: Local_album) { openCollection = OpenCollection.Album(album.id, album.title) }
    fun openArtist(artist: Local_artist) { navController.navigate(Routes.artistInfo(artist.name)) }

    val onYtItem: (YTItem, List<SongItem>) -> Unit = { item, context ->
        when (item) {
            is SongItem -> scope.launch(Dispatchers.IO) {
                if (context.isNotEmpty()) {
                    val idx = context.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
                    controller.playQueue(context.map { it.toQueueTrack() }, idx)
                } else if (item.id == playingId) {
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

    val playLocalTracks: (List<Local_track>, Int) -> Unit = { tracks, index ->
        scope.launch(Dispatchers.IO) {
            controller.playLocalQueue(tracks.map { it.toQueueTrack() }, index)
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Tabs and actions share one row, vertically centred, with room below so content never touches the sticky header.
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                ChipsRow(
                    chips = LibraryTab.entries.map { it to it.label },
                    currentValue = tab,
                    onValueUpdate = { next -> if (next != null) { tab = next; openCollection = null } },
                )
            }
            com.music.vivi.compose.ui.expressive.ExpressiveButton(
                text = "Tag Manager",
                onClick = { navController.navigate(Routes.TAG_MANAGER) },
                style = com.music.vivi.compose.ui.expressive.ExpressiveStyle.Text,
            )
            com.music.vivi.compose.ui.expressive.ExpressiveButton(
                text = "Refresh",
                onClick = { scope.launch { cache.loadLocal(); cache.loadOnline() } },
                enabled = !refreshing && !loading,
                style = com.music.vivi.compose.ui.expressive.ExpressiveStyle.Text,
            )
        }
        if (refreshing || scanProgress.running) {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
        }
        localError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        if (ytData.error != null && tab != LibraryTab.Home) {
            Text("Online library unavailable · Your local music is still available", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        val open = openCollection
        if (open != null) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                com.music.vivi.compose.ui.component.VisageBackButton(onClick = { openCollection = null })
                Column(Modifier.weight(1f)) {
                    Text(open.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${collectionTracks.size} tracks", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                com.music.vivi.compose.ui.expressive.ExpressiveButton(
                    text = "Play all",
                    onClick = { playLocalTracks(collectionTracks, 0) },
                    enabled = collectionTracks.isNotEmpty(),
                    leadingIcon = Icons.Filled.PlayArrow,
                )
                com.music.vivi.compose.ui.expressive.ExpressiveButton(
                    text = "Shuffle",
                    onClick = { playLocalTracks(collectionTracks.shuffled(), 0) },
                    enabled = collectionTracks.isNotEmpty(),
                    style = com.music.vivi.compose.ui.expressive.ExpressiveStyle.Tonal,
                    leadingIcon = Icons.Filled.Shuffle,
                )
            }
            if (collectionLoading) PageLoadingState("Loading tracks", detail = true)
            else LocalTrackList(collectionTracks, playingId, isPlaying, playLocalTracks)
            return@Column
        }
        val missingTagCount = remember(local.tracks) { com.music.vivi.compose.ui.tags.countTracksMissingTags(local.tracks) }
        com.music.vivi.compose.ui.tags.TagFixCta(
            count = if (tab == LibraryTab.Songs || tab == LibraryTab.Local) missingTagCount else 0,
            onClick = { navController.navigate(Routes.TAG_MANAGER) },
        )
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn(com.music.vivi.compose.ui.motion.VisageMotion.fadeInSpec()) togetherWith fadeOut(com.music.vivi.compose.ui.motion.VisageMotion.fadeOutSpec()) },
            label = "libraryTab",
            modifier = Modifier.weight(1f),
        ) { selectedTab ->
        tabStates.SaveableStateProvider(selectedTab) {
        when {
            loading -> PageLoadingState("Opening your library")
            selectedTab == LibraryTab.Home -> LibraryHomeFeed(
                yt = ytData,
                local = local,
                offlineCount = offline.size,
                playingId = playingId,
                isPlaying = isPlaying,
                onLikedClick = { tab = LibraryTab.Liked },
                onOfflineClick = { tab = LibraryTab.Offline },
                onPlayLocal = playLocalTracks,
                onOpen = { openCollection = it },
                resetTick = homeResetTick,
                onManageFolders = { navController.navigate(Routes.SETTINGS_LOCAL_OFFLINE) },
                onYtItem = onYtItem,
                error = ytData.error,
                refreshing = refreshing,
                onRetry = {
                    scope.launch {
                        cache.loadLocal()
                        cache.loadOnline()
                    }
                },
            )
            selectedTab == LibraryTab.Liked -> LikedSplit(
                songs = ytData.songs,
                localLiked = local.liked,
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
internal fun LikedTile(
    songCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    com.music.vivi.compose.ui.expressive.ExpressiveCard(
        onClick = onClick,
        restRadius = 20.dp,
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
internal fun OfflineTile(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    com.music.vivi.compose.ui.expressive.ExpressiveCard(
        onClick = onClick,
        restRadius = 20.dp,
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
internal fun LocalTrackShelf(
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
internal fun LocalAlbumShelf(
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
internal fun LocalCoverCard(
    title: String,
    subtitle: String,
    coverPath: String?,
    badge: String?,
    active: Boolean = false,
    modifier: Modifier = Modifier.width(148.dp),
    onClick: () -> Unit,
) {
    com.music.vivi.compose.ui.expressive.ExpressiveCard(
        onClick = onClick,
        restRadius = 16.dp,
        pressedRadius = 10.dp,
        color = if (active) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        modifier = modifier,
    ) {
        Column(Modifier.padding(8.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LazyCoverImage(
                        coverPath = coverPath,
                        modifier = Modifier.fillMaxSize(),
                        seed = title,
                    )
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        badge ?: "Local",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
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
private fun LikedSplit(
    songs: List<SongItem>,
    localLiked: List<Local_track>,
    playingId: String?,
    isPlaying: Boolean,
    onYtItem: (YTItem, List<SongItem>) -> Unit,
    onPlayLocal: (List<Local_track>, Int) -> Unit,
    emptyMessage: String,
) {
    if (songs.isEmpty() && localLiked.isEmpty()) {
        EmptyContent(emptyMessage)
        return
    }
    Row(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            LikedColumnHeader("YouTube", songs.size)
            if (songs.isEmpty()) EmptyContent("No liked YouTube songs") else {
                val state = androidx.compose.foundation.lazy.rememberLazyListState()
                LazyColumn(state = state, contentPadding = PaddingValues(bottom = 128.dp), modifier = Modifier.smoothWheelScroll(state)) {
                    items(songs, key = { "yt-${it.id}" }) { song ->
                        YouTubeListItem(
                            item = song,
                            isActive = song.id == playingId,
                            isPlaying = isPlaying,
                            onClick = { onYtItem(song, songs) },
                        )
                    }
                }
            }
        }
        androidx.compose.material3.VerticalDivider(Modifier.padding(vertical = 12.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            LikedColumnHeader("Local", localLiked.size)
            if (localLiked.isEmpty()) EmptyContent("No liked local songs") else {
                val state = androidx.compose.foundation.lazy.rememberLazyListState()
                LazyColumn(state = state, contentPadding = PaddingValues(bottom = 128.dp), modifier = Modifier.smoothWheelScroll(state)) {
                    items(localLiked, key = { "ll-${it.id}" }) { track ->
                        LocalTrackRow(track, active = playingId == "local:${track.id}") {
                            onPlayLocal(localLiked, localLiked.indexOfFirst { it.id == track.id }.coerceAtLeast(0))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LikedColumnHeader(title: String, count: Int) {
    Text(
        "$title · $count",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

@Composable
private fun LocalTrackRow(track: Local_track, active: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val container = if (active) scheme.secondaryContainer else Color.Transparent
    // Readable on whatever tint the theme gives the active row.
    val primaryText = if (active) com.music.vivi.compose.theme.ensureContrastingOn(scheme.secondaryContainer, scheme.onSecondaryContainer, 7f) else scheme.onSurface
    val secondaryText = if (active) primaryText.copy(alpha = 0.78f) else scheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = container,
        contentColor = primaryText,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            LazyCoverImage(
                coverPath = track.cover_path,
                seed = track.album_title?.takeIf { it.isNotBlank() } ?: track.title,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(track.title, color = primaryText, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist_name, color = secondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
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
        val smoothScroll2 = androidx.compose.foundation.lazy.rememberLazyListState()
        LazyColumn(
            state = smoothScroll2,contentPadding = PaddingValues(bottom = 128.dp),
            modifier = Modifier.smoothWheelScroll(smoothScroll2),
        ) {
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
    val smoothScroll3 = androidx.compose.foundation.lazy.rememberLazyListState()
    LazyColumn(
        state = smoothScroll3,contentPadding = PaddingValues(bottom = 128.dp),
        modifier = Modifier.smoothWheelScroll(smoothScroll3),
    ) {
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
                    LazyCoverImage(
                        coverPath = track.cover_path,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        seed = track.album_title ?: track.title,
                    )
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
    val smoothScroll4 = androidx.compose.foundation.lazy.rememberLazyListState()
    LazyColumn(
        state = smoothScroll4,contentPadding = PaddingValues(bottom = 128.dp),
        modifier = Modifier.smoothWheelScroll(smoothScroll4),
    ) {
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
                LocalTrackRow(track, active = playingId == "local:${track.id}") {
                    onPlayLocal(localRecent, localRecent.indexOfFirst { it.id == track.id }.coerceAtLeast(0))
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
            val smoothScroll5 = androidx.compose.foundation.lazy.rememberLazyListState()
            LazyColumn(
                state = smoothScroll5,contentPadding = PaddingValues(bottom = 128.dp),
                modifier = Modifier.smoothWheelScroll(smoothScroll5),
            ) {
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
    val smoothScroll6 = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    LazyVerticalGrid(
        state = smoothScroll6,
        columns = GridCells.Adaptive(minSize = 164.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 128.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
            .smoothWheelScroll(smoothScroll6),
    ) {
        items(albums, key = { "l-${it.id}" }) { album ->
            LocalCoverCard(
                title = album.title,
                subtitle = album.artist_name,
                coverPath = album.cover_path,
                badge = null,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onLocalAlbum(album) },
            )
        }
        items(ytAlbums, key = { "y-${it.id}" }) { album ->
            YouTubeGridItem(
                item = album,
                onClick = { onYtAlbum(album) },
                modifier = Modifier.fillMaxWidth(),
                fillWidth = true,
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
    val smoothScroll7 = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    LazyVerticalGrid(
        state = smoothScroll7,
        columns = GridCells.Adaptive(minSize = 164.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 128.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
            .smoothWheelScroll(smoothScroll7),
    ) {
        items(artists, key = { "l-${it.id}" }) { artist ->
            com.music.vivi.compose.ui.artist.ArtistTile(
                name = artist.name,
                onClick = { onLocalArtist(artist) },
            )
        }
        items(ytArtists, key = { "y-${it.id}" }) { artist ->
            androidx.compose.runtime.LaunchedEffect(artist.id) {
                com.music.vivi.player.ArtistIdRegistry.remember(artist.title, artist.id)
            }
            com.music.vivi.compose.ui.artist.ArtistTile(
                name = artist.title,
                subtitle = "YouTube Music",
                imageUrl = artist.thumbnail,
                onClick = { onYtArtist(artist) },
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
    val smoothScroll8 = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    // YouTube playlists sit in their own tinted container so their origin is obvious.
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Text(
                "YouTube",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp),
            )
            LazyVerticalGrid(
                state = smoothScroll8,
                columns = GridCells.Adaptive(minSize = 164.dp),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 128.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().smoothWheelScroll(smoothScroll8),
            ) {
                items(playlists, key = { it.id }) { pl ->
                    YouTubeGridItem(
                        item = pl,
                        onClick = { onItem(pl) },
                        modifier = Modifier.fillMaxWidth(),
                        fillWidth = true,
                    )
                }
            }
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
