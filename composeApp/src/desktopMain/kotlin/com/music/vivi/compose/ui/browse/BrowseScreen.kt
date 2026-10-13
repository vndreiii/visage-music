package com.music.vivi.compose.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.pages.BrowseResult
import com.music.vivi.compose.LocalPlaybackController
import com.music.vivi.compose.navigation.Routes
import com.music.vivi.compose.ui.component.DetailPageHeader
import com.music.vivi.compose.ui.component.smoothWheelScroll
import com.music.vivi.compose.ui.component.NavigationTitle
import com.music.vivi.compose.ui.component.PageEmptyState
import com.music.vivi.compose.ui.component.PageErrorState
import com.music.vivi.compose.ui.component.PageLoadingState
import com.music.vivi.compose.ui.component.YouTubeGridItem
import com.music.vivi.compose.ui.component.rememberNowPlayingIds
import com.music.vivi.player.toQueueTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Generic YouTube Music browse page (mood/genre categories, "more" shelves). */
@Composable
fun BrowseScreen(
    browseId: String,
    navController: NavHostController,
    params: String? = null,
) {
    val controller = LocalPlaybackController.current
    val scope = rememberCoroutineScope()
    val (playingId, isPlaying) = rememberNowPlayingIds()

    var title by remember { mutableStateOf<String?>(null) }
    var sections by remember { mutableStateOf<List<BrowseResult.Item>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            loading = true
            error = null
            try {
                val result = withContext(Dispatchers.IO) { YouTube.browse(browseId, params).getOrThrow() }
                title = result.title
                sections = result.items.filter { it.items.isNotEmpty() }
            } catch (e: Exception) {
                error = e.message ?: "Failed to load"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(browseId, params) { load() }

    fun onItemClick(item: YTItem, context: List<YTItem>) {
        when (item) {
            is SongItem -> scope.launch(Dispatchers.IO) {
                if (item.id == playingId) {
                    if (isPlaying) controller.pause() else controller.play()
                } else {
                    val songs = context.filterIsInstance<SongItem>()
                    val idx = songs.indexOfFirst { it.id == item.id }
                    if (idx >= 0 && songs.size > 1) {
                        controller.playQueue(songs.map { it.toQueueTrack() }, idx)
                    } else {
                        controller.playVideo(item.id)
                    }
                }
            }
            is AlbumItem -> navController.navigate(Routes.album(item.id))
            is ArtistItem -> navController.navigate(Routes.artist(item.id))
            is PlaylistItem -> navController.navigate(Routes.onlinePlaylist(item.id))
        }
    }

    Column(Modifier.fillMaxSize()) {
        DetailPageHeader(title = title ?: if (loading) "" else "Browse", onBack = { navController.navigateUp() })
        val loaded = sections
        val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
        when {
            loading && loaded == null -> PageLoadingState("Loading", detail = true)
            error != null && loaded == null -> PageErrorState(error.orEmpty()) { load() }
            loaded.isNullOrEmpty() -> PageEmptyState("Nothing here", "This page has no items.")
            else -> LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 172.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().smoothWheelScroll(gridState),
            ) {
                loaded.forEachIndexed { sectionIndex, section ->
                    val sectionTitle = section.title
                    if (!sectionTitle.isNullOrBlank() && loaded.size > 1) {
                        item(key = "header_$sectionIndex", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                            NavigationTitle(title = sectionTitle, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                    items(section.items, key = { "${sectionIndex}_${it.id}" }, contentType = { it::class }) { item ->
                        YouTubeGridItem(
                            item = item,
                            onClick = { onItemClick(item, section.items) },
                            isActive = (item is SongItem || item is AlbumItem) && playingId == item.id,
                            isPlaying = isPlaying,
                            modifier = Modifier.fillMaxWidth().animateItem(),
                        )
                    }
                }
            }
        }
    }
}
