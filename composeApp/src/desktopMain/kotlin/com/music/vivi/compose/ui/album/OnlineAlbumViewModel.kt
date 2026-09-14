package com.music.vivi.compose.ui.album

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.filterVideoSongs
import com.music.innertube.pages.AlbumPage
import com.music.vivi.core.youtube.YouTubeMusicService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Desktop port of upstream AlbumViewModel's online fetch path.
 * There is no album table in the desktop DB yet, so this is network-only.
 */
class OnlineAlbumViewModel(
    private val albumId: String,
    private val yt: YouTubeMusicService,
    private val scope: CoroutineScope,
) {
    var page by mutableStateOf<AlbumPage?>(null)
        private set
    var isLoading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val album: AlbumItem?
        get() = page?.album
    val songs: List<SongItem>
        get() = page?.songs?.distinctBy { it.id }?.filterVideoSongs(false).orEmpty()
    val otherVersions: List<AlbumItem>
        get() = page?.otherVersions.orEmpty()
    val releasesForYou: List<AlbumItem>
        get() = page?.releasesForYou.orEmpty()

    init {
        load()
    }

    fun load() {
        scope.launch {
            isLoading = true
            error = null
            withContext(Dispatchers.IO) {
                yt.album(albumId)
                    .onSuccess { page = it }
                    .onFailure { error = it.message ?: it.toString() }
            }
            isLoading = false
        }
    }

    fun retry() = load()
}