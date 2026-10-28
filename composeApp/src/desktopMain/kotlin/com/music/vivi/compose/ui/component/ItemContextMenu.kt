package com.music.vivi.compose.ui.component

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.vivi.compose.LocalPlaybackController
import com.music.vivi.compose.navigation.Routes
import com.music.vivi.player.toQueueTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/** Opens an app route (album, artist, ...); provided by the app scaffold. */
val LocalNavigate = compositionLocalOf<((String) -> Unit)?> { null }

/** Where a right-click landed, relative to the item (px), or null when the menu is closed. */
class ItemMenuState {
    var at by mutableStateOf<Offset?>(null)
}

@Composable
fun rememberItemMenuState() = remember { ItemMenuState() }

/** Right mouse button opens [state]; the press is consumed so the item's own click does not fire. */
fun Modifier.itemSecondaryClick(state: ItemMenuState): Modifier = pointerInput(state) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            if (e.type == PointerEventType.Press && e.buttons.isSecondaryPressed) {
                state.at = e.changes.first().position
                e.changes.forEach { it.consume() }
            }
        }
    }
}

/**
 * The right-click menu for any YouTube item: queue actions, radio, offline, playlists and navigation for songs;
 * open and copy-link for albums, playlists and artists. [onOpen] is the item's normal click.
 */
@Composable
fun ItemContextMenu(item: YTItem, state: ItemMenuState, itemHeightPx: Int, onOpen: () -> Unit) {
    val at = state.at ?: return
    val density = LocalDensity.current
    val controller = LocalPlaybackController.current
    val navigate = LocalNavigate.current
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }
    fun close() { state.at = null }

    DropdownMenu(
        expanded = true,
        onDismissRequest = { close() },
        offset = with(density) { DpOffset(at.x.toDp(), (at.y - itemHeightPx).toDp()) },
    ) {
        if (item is SongItem && picking) {
            val repo = koinInject<com.music.vivi.database.LibraryRepository>()
            var playlists by remember { mutableStateOf<List<com.music.vivi.database.Playlist>?>(null) }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                playlists = withContext(Dispatchers.IO) { runCatching { repo.localPlaylists() }.getOrDefault(emptyList()) }
            }
            val list = playlists
            if (list == null) DropdownMenuItem(text = { Text("Loading…") }, onClick = {}, enabled = false)
            else if (list.isEmpty()) DropdownMenuItem(text = { Text("No local playlists yet") }, onClick = {}, enabled = false)
            androidx.compose.foundation.layout.Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                list.orEmpty().forEach { p ->
                    DropdownMenuItem(text = { Text(p.title) }, onClick = {
                        close()
                        val q = item.toQueueTrack()
                        scope.launch(Dispatchers.IO) {
                            runCatching {
                                repo.addToLocalPlaylist(p.id, q.videoId, q.title, q.artist, q.artworkUrl, (q.durationMs / 1000).takeIf { it > 0 }, q.localPath)
                            }
                        }
                    })
                }
            }
            return@DropdownMenu
        }
        when (item) {
            is SongItem -> {
                val downloads = koinInject<com.music.vivi.compose.download.TrackDownloadService>()
                DropdownMenuItem(text = { Text("Play") }, onClick = { close(); onOpen() })
                DropdownMenuItem(text = { Text("Play next") }, onClick = { close(); controller.playNext(item.toQueueTrack()) })
                DropdownMenuItem(text = { Text("Add to queue") }, onClick = { close(); controller.addToQueue(item.toQueueTrack()) })
                DropdownMenuItem(text = { Text("Start radio") }, onClick = {
                    close()
                    scope.launch(Dispatchers.IO) { runCatching { controller.startRadio(item.id) } }
                })
                HorizontalDivider()
                DropdownMenuItem(text = { Text("Save to playlist…") }, onClick = { picking = true })
                DropdownMenuItem(text = { Text("Make available offline") }, onClick = {
                    close()
                    downloads.enqueue(item.id, item.title, item.artists.joinToString { it.name })
                })
                val artist = item.artists.firstOrNull { it.id != null }
                val album = item.album?.takeIf { it.id.isNotBlank() }
                if ((artist != null || album != null) && navigate != null) HorizontalDivider()
                if (artist != null && navigate != null) {
                    DropdownMenuItem(text = { Text("Go to artist") }, onClick = { close(); navigate(Routes.artist(artist.id!!)) })
                }
                if (album != null && navigate != null) {
                    DropdownMenuItem(text = { Text("Go to album") }, onClick = { close(); navigate(Routes.album(album.id)) })
                }
                DropdownMenuItem(text = { Text("Copy link") }, onClick = {
                    close(); SystemClipboard.copy("https://music.youtube.com/watch?v=${item.id}")
                })
            }
            else -> {
                DropdownMenuItem(text = { Text("Open") }, onClick = { close(); onOpen() })
                val link = when (item) {
                    is AlbumItem -> "https://music.youtube.com/browse/${item.browseId}"
                    is PlaylistItem -> "https://music.youtube.com/playlist?list=${item.id}"
                    is ArtistItem -> "https://music.youtube.com/channel/${item.id}"
                    else -> null
                }
                if (link != null) DropdownMenuItem(text = { Text("Copy link") }, onClick = { close(); SystemClipboard.copy(link) })
            }
        }
    }
}
