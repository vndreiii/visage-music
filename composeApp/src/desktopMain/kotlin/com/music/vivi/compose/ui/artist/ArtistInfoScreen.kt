package com.music.vivi.compose.ui.artist

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

/** Local tracks credited to [name] (split credits, so "A, B" appears on both A's and B's pages). */
private fun tracksFor(repo: LocalMusicRepository, name: String): List<Local_track> {
    val byId = LinkedHashMap<String, Local_track>()
    val key = ArtistNames.normalize(name)
    repo.allArtists().filter { ArtistNames.normalize(it.name) == key }.forEach { a ->
        (repo.tracksCreditedTo(a.id).ifEmpty { repo.tracksForArtist(a.id) }).forEach { byId.putIfAbsent(it.id, it) }
    }
    repo.allTracks().filter { ArtistNames.credited(it.artist_name, name) }.forEach { byId.putIfAbsent(it.id, it) }
    return byId.values.sortedWith(compareByDescending<Local_track> { it.play_count }.thenBy { it.title.lowercase() })
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
    LaunchedEffect(name) { tracks = withContext(Dispatchers.IO) { tracksFor(repo, name) } }
    val list = tracks.orEmpty()
    val ytId = remember(name) { ArtistIdRegistry.idFor(name) }

    fun play(index: Int) {
        scope.launch(Dispatchers.IO) { controller.playLocalQueue(list.map { it.toQueueTrack() }, index) }
    }

    val state = rememberLazyListState()
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize().smoothWheelScroll(state),
        contentPadding = PaddingValues(bottom = 128.dp),
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
        items(list, key = { it.id }) { track ->
            val active = playingId == "local:${track.id}"
            Surface(
                onClick = { play(list.indexOfFirst { it.id == track.id }.coerceAtLeast(0)) },
                shape = RoundedCornerShape(12.dp),
                color = if (active && isPlaying) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    LazyCoverImage(coverPath = track.cover_path, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(track.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(track.artist_name.takeIf { it.isNotBlank() }, track.album_title).joinToString(" · "),
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

private fun sourceLabel(s: String) = when (s) {
    "deezer" -> "Deezer"
    "theaudiodb" -> "TheAudioDB"
    "wikipedia" -> "Wikipedia"
    else -> s
}
