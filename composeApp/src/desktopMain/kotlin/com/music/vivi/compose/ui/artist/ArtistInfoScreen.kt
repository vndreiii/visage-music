package com.music.vivi.compose.ui.artist

import com.music.vivi.compose.ui.component.MiniPlayerClearance
import com.music.vivi.compose.ui.component.visageMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.layout.height
import com.music.vivi.compose.ui.component.horizontalWheelScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.music.vivi.compose.LocalPlaybackController
import com.music.vivi.compose.local.toQueueTrack
import com.music.vivi.compose.navigation.Routes
import com.music.vivi.compose.ui.component.NavigationTitle
import com.music.vivi.compose.ui.component.rememberNowPlayingIds
import com.music.vivi.compose.ui.component.smoothWheelScroll
import com.music.vivi.compose.ui.component.pageScrollEdge
import com.music.vivi.compose.ui.component.edgeProgress
import com.music.vivi.compose.ui.library.LazyCoverImage
import com.music.vivi.compose.ui.motion.VisageMotion
import com.music.vivi.database.LocalMusicRepository
import com.music.vivi.database.Local_track
import com.music.vivi.player.ArtistIdRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * Navigates to an artist by display name: the YouTube artist page when a channel id has been seen
 * for that name, otherwise the helper-backed [ArtistInfoScreen].
 */
fun NavController.openArtistByName(name: String) {
    val id = ArtistIdRegistry.idFor(name)
    navigate(if (id != null) Routes.artist(id) else Routes.artistInfo(name))
}

/** Local tracks whose artist tag is [name] (ignoring case and accents). A collaboration is its own artist and is not included. */
private fun tracksFor(repo: LocalMusicRepository, name: String): List<Local_track> {
    val key = ArtistNames.normalize(name)
    return repo.allTracks()
        .filter { ArtistNames.normalize(it.artist_name) == key }
        .sortedWith(compareByDescending<Local_track> { it.play_count }.thenBy { it.title.lowercase() })
}

/**
 * Artist page for artists without a YouTube channel (or only known by name): same layout as
 * [ArtistScreen], with imagery and bio from the Rust helper and the tracks from the local library.
 */
@Composable
fun ArtistInfoScreen(name: String, navController: NavController) {
    val repo = koinInject<LocalMusicRepository>()
    val controller = LocalPlaybackController.current
    val scope = rememberCoroutineScope()
    val (playingId, isPlaying) = rememberNowPlayingIds()
    val lookup by rememberArtistLookup(name)
    val info = lookup.info
    var tracks by remember(name) { mutableStateOf<List<Local_track>?>(null) }
    LaunchedEffect(name) {
        val local = withContext(Dispatchers.IO) { tracksFor(repo, name) }
        tracks = local
        // No local files by this name: it is a YouTube Music artist we only know by name, so find and open the real
        // artist page (with their songs, albums and videos) instead of an empty library page.
        if (local.isEmpty()) {
            val hit = withContext(Dispatchers.IO) {
                com.music.innertube.YouTube.search(name, com.music.innertube.YouTube.SearchFilter.FILTER_ARTIST)
                    .getOrNull()?.items.orEmpty()
                    .filterIsInstance<com.music.innertube.models.ArtistItem>()
                    .let { artists -> artists.firstOrNull { it.title.equals(name, ignoreCase = true) } ?: artists.firstOrNull() }
            }
            if (hit != null) {
                ArtistIdRegistry.remember(name, hit.id)
                runCatching {
                    navController.navigate(Routes.artist(hit.id)) {
                        popUpTo(Routes.ARTIST_INFO) { inclusive = true }
                    }
                }
            }
        }
    }
    val list = tracks.orEmpty()
    val ytId = remember(name) { ArtistIdRegistry.idFor(name) }

    fun play(index: Int) {
        scope.launch(Dispatchers.IO) { controller.playLocalQueue(list.map { it.toQueueTrack() }, index) }
    }

    val state = rememberLazyListState()
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize().pageScrollEdge { state.edgeProgress() }.smoothWheelScroll(state),
        contentPadding = PaddingValues(bottom = MiniPlayerClearance),
    ) {
        item(key = "toolbar") { ArtistBackBar { navController.navigateUp() } }
        item(key = "header") {
            val meta = buildList {
                tracks?.let { add(if (it.size == 1) "1 song" else "${it.size} songs") }
                info?.sources?.takeIf { it.isNotEmpty() }?.let { add("via " + it.joinToString(" · ") { s -> sourceLabel(s) }) }
            }.joinToString(" • ")
            ArtistPageHeader(
                title = name,
                meta = meta,
                description = info?.bio,
                onPlay = { if (list.isNotEmpty()) play(0) },
                onShuffle = { if (list.isNotEmpty()) play(list.indices.random()) },
                banner = {
                    ArtistFallback(name, Modifier.fillMaxSize())
                    val banner = info?.bannerUrl
                    if (banner != null) FadingImage(banner, Modifier.fillMaxSize())
                    else FadingImage(info?.imageUrl, Modifier.fillMaxSize(), blurDp = 28)
                },
                portrait = { ArtistImage(name, Modifier.fillMaxSize()) },
            )
        }
        if (ytId != null) {
            item(key = "yt") {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                    Surface(
                        onClick = { navController.navigate(Routes.artist(ytId)) },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            "Open on YouTube Music",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
        item(key = "title") { NavigationTitle(title = "Top songs") }
        if (tracks != null && list.isEmpty()) {
            item(key = "empty") {
                Text(
                    "No songs by this artist in your library yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        }
        if (list.isNotEmpty()) {
            item(key = "songs") {
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                    // Same shelf as Home's Quick picks: up to 4 rows, extra songs scroll sideways in whole columns.
                    val gap = 8.dp
                    val sidePad = 8.dp
                    val usable = maxWidth - sidePad * 2
                    val fitCols = ((usable + gap) / (280.dp + gap)).toInt().coerceAtLeast(1)
                    val rows = ((list.size + fitCols - 1) / fitCols).coerceIn(1, 4)
                    val colWidth = (usable - gap * (fitCols - 1)) / fitCols
                    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
                    androidx.compose.foundation.lazy.grid.LazyHorizontalGrid(
                        rows = androidx.compose.foundation.lazy.grid.GridCells.Fixed(rows),
                        state = gridState,
                        contentPadding = PaddingValues(horizontal = sidePad),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(com.music.vivi.compose.ui.component.ListItemHeight * rows)
                            .horizontalWheelScroll(gridState),
                    ) {
                        itemsIndexed(items = list, key = { _, t -> t.id }) { i, track ->
                            val active = playingId == "local:${track.id}"
                            Surface(
                                onClick = { play(i) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (active && isPlaying) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                modifier = Modifier.width(colWidth).height(com.music.vivi.compose.ui.component.ListItemHeight),
                            ) {
                                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    LazyCoverImage(coverPath = track.cover_path, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(track.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.visageMarquee())
                                        Text(
                                            listOfNotNull(track.artist_name.takeIf { it.isNotBlank() }, track.album_title).joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Clip,
                                            modifier = Modifier.visageMarquee(),
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
}

private fun sourceLabel(s: String) = when (s) {
    "deezer" -> "Deezer"
    "theaudiodb" -> "TheAudioDB"
    "wikipedia" -> "Wikipedia"
    else -> s
}
