package com.music.vivi.compose.ui.playlist

import com.music.vivi.compose.ui.component.smoothWheelScroll

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.music.innertube.models.Artist
import com.music.innertube.models.SongItem
import com.music.vivi.compose.LocalPlaybackController
import com.music.vivi.compose.ui.component.NavigationTitle
import com.music.vivi.compose.ui.component.PageLoadingState
import com.music.vivi.compose.ui.component.YouTubeListItem
import com.music.vivi.compose.ui.component.rememberNowPlayingIds
import com.music.vivi.database.LibraryRepository
import com.music.vivi.database.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun AutoPlaylistScreen(
    type: String,
    navController: NavHostController,
) {
    val libraryRepo = koinInject<LibraryRepository>()
    val controller = LocalPlaybackController.current
    val scope = rememberCoroutineScope()
    val (playingId, isPlaying) = rememberNowPlayingIds()

    var songs by remember { mutableStateOf<List<Song>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch(Dispatchers.IO) {
            loading = true
            error = null
            try {
                if (type == "liked") {
                    songs = libraryRepo.likedSongs()
                } else {
                    error = "Unknown auto playlist type: $type"
                }
            } catch (e: Exception) {
                error = e.message ?: "Failed to load auto playlist"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(type) {
        load()
    }

    fun onItemClick(songId: String) {
        scope.launch(Dispatchers.IO) {
            if (songId == playingId) {
                controller.toggleFromList()
            } else {
                controller.playVideo(songId)
            }
        }
    }

    when {
        loading && songs == null -> {
            PageLoadingState("Loading $type playlist", detail = true)
        }
        error != null && songs == null -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Error: $error", style = MaterialTheme.typography.bodyLarge)
                Button(onClick = { load() }, Modifier.padding(top = 12.dp)) {
                    Text("Retry")
                }
            }
        }
        else -> {
            Column(modifier = Modifier.fillMaxSize()) {
                TextButton(
                    onClick = { navController.navigateUp() },
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp)
                ) {
                    Text("← Back")
                }
                
                NavigationTitle(
                    title = if (type == "liked") "Liked Songs" else "Auto Playlist",
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val smoothScroll1 = androidx.compose.foundation.lazy.rememberLazyListState()
                LazyColumn(
                    state = smoothScroll1,
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 120.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f)
                        .smoothWheelScroll(smoothScroll1)
                ) {
                    items(songs.orEmpty(), key = { it.id }) { song ->
                        val ytSong = SongItem(
                            id = song.id,
                            title = song.title,
                            artists = listOf(Artist(name = song.artists ?: "", id = null)),
                            album = null,
                            duration = null,
                            thumbnail = song.thumbnail ?: "",
                            explicit = false,
                            endpoint = null
                        )
                        YouTubeListItem(
                            item = ytSong,
                            isActive = song.id == playingId,
                            isPlaying = isPlaying,
                            onClick = { onItemClick(song.id) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
