package com.music.vivi.compose.ui.newrelease

import com.music.vivi.compose.ui.component.smoothWheelScroll

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
fun NewReleaseScreen(
    navController: NavHostController,
) {
    val controller = LocalPlaybackController.current
    val scope = rememberCoroutineScope()
    val (playingId, isPlaying) = rememberNowPlayingIds()

    var albums by remember { mutableStateOf<List<AlbumItem>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            loading = true
            error = null
            try {
                val result = withContext(Dispatchers.IO) { YouTube.newReleaseAlbums().getOrThrow() }
                albums = result
            } catch (e: Exception) {
                error = e.message ?: "Failed to load new releases"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        load()
    }

    when {
        loading && albums == null -> {
            PageLoadingState("Loading new releases", detail = true)
        }
        error != null && albums == null -> {
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
                com.music.vivi.compose.ui.component.VisageBackButton(onClick = { navController.navigateUp() }, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
                
                NavigationTitle(title = "New Releases", modifier = Modifier.padding(bottom = 8.dp))

                val smoothScroll1 = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
                LazyVerticalGrid(
                    state = smoothScroll1,
                    columns = GridCells.Adaptive(minSize = 180.dp),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f)
                        .smoothWheelScroll(smoothScroll1)
                ) {
                    albums.orEmpty().let { itemList ->
                        items(itemList, key = { it.id }) { item ->
                            YouTubeGridItem(
                                item = item,
                                onClick = { navController.navigate(Routes.album(item.id)) },
                                isActive = playingId == item.id,
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
