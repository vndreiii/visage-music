package com.music.vivi.compose.ui.playlist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.models.filterVideoSongs
import com.music.vivi.core.youtube.YouTubeMusicService
import com.music.vivi.database.LibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Desktop port of upstream OnlinePlaylistViewModel.
 * Caches playlist header + songs into SQLDelight for offline / local mode.
 */
class OnlinePlaylistViewModel(
    private val playlistId: String,
    private val yt: YouTubeMusicService,
    private val library: LibraryRepository,
    private val scope: CoroutineScope,
) {
    var playlist by mutableStateOf<PlaylistItem?>(null)
        private set
    var songs by mutableStateOf<List<SongItem>>(emptyList())
        private set
    var relatedItems by mutableStateOf<List<YTItem>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var isLoadingMore by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var continuation by mutableStateOf<String?>(null)
        private set

    private var proactiveJob: Job? = null

    init {
        // Show cached header numbers immediately when available
        scope.launch(Dispatchers.IO) {
            library.getPlaylist(playlistId)?.let { cached ->
                // Keep network as source of truth for PlaylistItem shape; cache is for counts/local
            }
        }
        load()
    }

    private fun cacheCurrent() {
        val pl = playlist ?: return
        runCatching {
            library.upsertPlaylistWithSongs(pl, songs)
        }
    }

    fun load() {
        scope.launch {
            isLoading = true
            error = null
            continuation = null
            proactiveJob?.cancel()
            withContext(Dispatchers.IO) {
                yt.playlist(playlistId)
                    .onSuccess { page ->
                        playlist = page.playlist
                        songs = page.songs.distinctBy { it.id }.filterVideoSongs(false)
                        relatedItems = page.related.orEmpty()
                        continuation = page.songsContinuation
                        cacheCurrent()
                    }
                    .onFailure {
                        error = it.message ?: it.toString()
                    }
            }
            isLoading = false
            if (continuation != null) startProactiveLoad()
        }
    }

    fun retry() = load()

    fun loadMore() {
        val token = continuation ?: return
        if (isLoadingMore) return
        proactiveJob?.cancel()
        scope.launch {
            isLoadingMore = true
            withContext(Dispatchers.IO) {
                yt.playlistContinuation(token)
                    .onSuccess { page ->
                        songs = (songs + page.songs).distinctBy { it.id }.filterVideoSongs(false)
                        continuation = page.continuation
                        cacheCurrent()
                    }
            }
            isLoadingMore = false
            if (continuation != null) startProactiveLoad()
        }
    }

    private fun startProactiveLoad() {
        proactiveJob?.cancel()
        proactiveJob = scope.launch(Dispatchers.IO) {
            var token = continuation
            while (token != null && isActive) {
                if (isLoadingMore) break
                yt.playlistContinuation(token)
                    .onSuccess { page ->
                        songs = (songs + page.songs).distinctBy { it.id }.filterVideoSongs(false)
                        token = page.continuation
                        continuation = token
                        cacheCurrent()
                    }
                    .onFailure {
                        token = null
                    }
            }
        }
    }
}
