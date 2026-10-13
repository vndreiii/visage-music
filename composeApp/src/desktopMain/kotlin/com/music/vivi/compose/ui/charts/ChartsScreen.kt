package com.music.vivi.compose.ui.charts

import com.music.vivi.compose.ui.component.smoothWheelScroll

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.pages.ChartsPage
import com.music.vivi.compose.LocalPlaybackController
import com.music.vivi.compose.navigation.Routes
import com.music.vivi.compose.ui.component.NavigationTitle
import com.music.vivi.compose.ui.component.PageLoadingState
import com.music.vivi.compose.ui.component.YouTubeGridItem
import com.music.vivi.compose.ui.component.rememberNowPlayingIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ChartsScreen(
    navController: NavHostController,
) {
    val controller = LocalPlaybackController.current
    val scope = rememberCoroutineScope()
    val (playingId, isPlaying) = rememberNowPlayingIds()

    var chartsPage by remember { mutableStateOf<ChartsPage?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            loading = true
            error = null
            try {
                val result = withContext(Dispatchers.IO) { YouTube.getChartsPage().getOrThrow() }
                chartsPage = result
            } catch (e: Exception) {
                error = e.message ?: "Failed to load charts"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        load()
    }

    fun onItemClick(item: YTItem) {
        when (item) {
            is SongItem -> scope.launch(Dispatchers.IO) {
                if (item.id == playingId) {
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
        loading && chartsPage == null -> {
            PageLoadingState("Loading charts", detail = true)
        }
        error != null && chartsPage == null -> {
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
                
                val smoothScroll1 = androidx.compose.foundation.lazy.rememberLazyListState()
                LazyColumn(
                    state = smoothScroll1,
                    contentPadding = PaddingValues(bottom = 120.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f)
                        .smoothWheelScroll(smoothScroll1)
                ) {
                    chartsPage?.sections?.forEach { section ->
                        item {
                            NavigationTitle(title = section.title)
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(section.items, key = { it.id }) { item ->
                                    YouTubeGridItem(
                                        item = item,
                                        onClick = { onItemClick(item) },
                                        isActive = when (item) {
                                            is SongItem -> playingId == item.id
                                            is AlbumItem -> playingId == item.id
                                            else -> false
                                        },
                                        isPlaying = isPlaying,
                                        modifier = Modifier.fillMaxWidth()
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
