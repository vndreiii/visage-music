package com.music.vivi.compose.ui.history

import com.music.vivi.compose.ui.component.MiniPlayerClearance
import com.music.vivi.compose.ui.component.edgeProgress
import com.music.vivi.compose.ui.component.pageScrollEdge
import com.music.vivi.compose.ui.component.PageInset
import com.music.vivi.compose.ui.component.smoothWheelScroll

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.music.vivi.compose.LocalPlaybackController
import com.music.vivi.compose.ui.component.NavigationTitle
import com.music.vivi.compose.ui.component.rememberNowPlayingIds
import com.music.innertube.models.SongItem
import com.music.innertube.models.Artist
import com.music.vivi.compose.ui.component.YouTubeListItem
import com.music.vivi.database.Play_event
import com.music.vivi.database.PlayHistoryRepository
import com.music.vivi.player.toQueueTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@Composable
fun HistoryScreen(
    navController: NavHostController,
) {
    val historyRepo = koinInject<PlayHistoryRepository>()
    val controller = LocalPlaybackController.current
    val scope = rememberCoroutineScope()
    val (playingId, isPlaying) = rememberNowPlayingIds()

    var historyItems by remember { mutableStateOf<List<Play_event>>(emptyList()) }
    var query by remember { mutableStateOf("") }

    // More than the page shows by default, so a search can reach further back.
    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            historyItems = historyRepo.recentDistinct(1000)
        }
    }
    val visible = remember(historyItems, query) {
        val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (terms.isEmpty()) historyItems.take(100)
        else historyItems.filter { e ->
            val hay = (e.title + " " + (e.artists ?: "")).lowercase()
            terms.all { it in hay }
        }
    }

    fun onItemClick(event: Play_event) {
        scope.launch(Dispatchers.IO) {
            if (event.song_id == playingId) {
                controller.toggleFromList()
            } else {
                controller.playVideo(event.song_id)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        com.music.vivi.compose.ui.component.DetailPageHeader(
            title = "Listening history",
            onBack = { navController.navigateUp() },
            trailing = {
                com.music.vivi.compose.ui.component.PageSearchField(
                    value = query, onValueChange = { query = it }, placeholder = "Search your history",
                    modifier = Modifier.width(320.dp),
                )
            },
        )

        val smoothScroll1 = androidx.compose.foundation.lazy.rememberLazyListState()
        LazyColumn(
            state = smoothScroll1,
            contentPadding = PaddingValues(start = PageInset, end = PageInset, bottom = MiniPlayerClearance),
            modifier = Modifier.fillMaxWidth().weight(1f)
                .pageScrollEdge { smoothScroll1.edgeProgress() }
                .smoothWheelScroll(smoothScroll1)
        ) {
            if (visible.isEmpty() && query.isNotBlank()) {
                item {
                    Text(
                        "Nothing in your history matches “${query.trim()}”.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, top = 16.dp),
                    )
                }
            }
            items(visible, key = { it.id }) { event ->
                val song = SongItem(
                    id = event.song_id,
                    title = event.title,
                    artists = listOf(Artist(name = event.artists ?: "", id = null)),
                    album = null,
                    duration = null, // string duration if needed
                    thumbnail = event.thumbnail ?: "",
                    explicit = false,
                    endpoint = null
                )
                YouTubeListItem(
                    item = song,
                    isActive = event.song_id == playingId,
                    isPlaying = isPlaying,
                    onClick = { onItemClick(event) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
