package com.music.vivi.compose.ui.playlist

import com.music.vivi.compose.ui.component.MiniPlayerClearance
import com.music.vivi.compose.ui.component.PageHeaderTopPadding
import com.music.vivi.compose.ui.component.PageInset
import com.music.vivi.compose.ui.component.PageHeaderBottomPadding
import com.music.vivi.compose.ui.expressive.VisageTextField
import com.music.vivi.compose.ui.component.visageMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.music.vivi.compose.LocalPlaybackController
import com.music.vivi.compose.ui.component.CoverImage
import com.music.vivi.compose.ui.component.VisageBackButton
import com.music.vivi.compose.ui.component.rememberNowPlayingIds
import com.music.vivi.compose.ui.component.smoothWheelScroll
import com.music.vivi.compose.ui.component.pageScrollEdge
import com.music.vivi.compose.ui.component.edgeProgress
import com.music.vivi.compose.ui.library.sidebar.toQueueTrack
import com.music.vivi.database.LibraryRepository
import com.music.vivi.database.Playlist
import com.music.vivi.database.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/** A user-created playlist: play, shuffle, rename, reorder, remove tracks, delete. */
@Composable
fun LocalPlaylistScreen(playlistId: String, navController: NavController) {
    val repo = koinInject<LibraryRepository>()
    val controller = LocalPlaybackController.current
    val scope = rememberCoroutineScope()
    val mutations by repo.playlistMutations.collectAsState()
    val data by produceState<Pair<Playlist?, List<Song>>?>(null, playlistId, mutations) {
        value = withContext(Dispatchers.IO) { repo.getPlaylist(playlistId) to repo.getPlaylistSongs(playlistId) }
    }
    val (playingId, _) = rememberNowPlayingIds()
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    fun play(songs: List<Song>, start: Int, shuffled: Boolean = false) {
        if (songs.isEmpty()) return
        val queue = (if (shuffled) songs.shuffled() else songs).map { it.toQueueTrack() }
        scope.launch(Dispatchers.IO) {
            if (queue.all { !it.localPath.isNullOrBlank() }) controller.playLocalQueue(queue, if (shuffled) 0 else start)
            else controller.playQueue(queue, if (shuffled) 0 else start)
        }
    }

    val loaded = data
    if (loaded != null && loaded.first == null) {
        // Deleted (or never existed): leave quietly.
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("This playlist no longer exists", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { navController.navigateUp() }) { Text("Go back") }
        }
        return
    }
    val playlist = loaded?.first
    val songs = loaded?.second.orEmpty()

    BoxWithConstraints(Modifier.fillMaxSize()) {
    // Under 460 dp the up/down reorder buttons go, so the title and the remove button keep their room.
    val narrowRows = maxWidth < 460.dp
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().pageScrollEdge { listState.edgeProgress() }.smoothWheelScroll(listState),
            contentPadding = PaddingValues(bottom = MiniPlayerClearance),
        ) {
            item(key = "back") {
                Row(Modifier.fillMaxWidth().padding(start = PageInset, end = PageInset, top = PageHeaderTopPadding, bottom = PageHeaderBottomPadding)) {
                    VisageBackButton(onClick = { navController.navigateUp() })
                }
            }
            item(key = "header") {
                // Narrow pane (docked panels in a small window, phones): cover on top, everything centred, buttons wrap.
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val stacked = maxWidth < 640.dp
                    val coverSize = if (stacked) minOf(168.dp, maxWidth - 48.dp).coerceAtLeast(96.dp) else 168.dp
                    val total = songs.sumOf { it.duration_sec ?: 0L }
                    val titleBlock: @Composable (Modifier, Alignment.Horizontal, TextAlign) -> Unit = { mod, align, textAlign ->
                        Column(mod, horizontalAlignment = align) {
                            Text("Playlist", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                playlist?.title.orEmpty(),
                                style = if (stacked) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold, textAlign = textAlign,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { renaming = true },
                            )
                            Text(
                                buildString {
                                    append("You · ${songs.size} ${if (songs.size == 1) "song" else "songs"}")
                                    if (total > 0) append(" · ${total / 3600}h ${(total % 3600) / 60}m".replace("0h ", ""))
                                },
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = textAlign,
                                modifier = Modifier.padding(top = 4.dp, bottom = if (playlist?.description.isNullOrBlank()) 14.dp else 6.dp),
                            )
                            playlist?.description?.takeIf { it.isNotBlank() }?.let { desc ->
                                Text(
                                    desc, style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = textAlign,
                                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(bottom = 14.dp),
                                )
                            }
                            // FlowRow: the buttons wrap instead of squeezing "Shuffle" into a column of letters.
                            androidx.compose.foundation.layout.FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp, if (stacked) Alignment.CenterHorizontally else Alignment.Start),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                itemVerticalAlignment = Alignment.CenterVertically,
                            ) {
                                Button(onClick = { play(songs, 0) }, enabled = songs.isNotEmpty()) {
                                    Icon(Icons.Filled.PlayArrow, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text("Play", maxLines = 1, softWrap = false)
                                }
                                FilledTonalButton(onClick = { play(songs, 0, shuffled = true) }, enabled = songs.isNotEmpty()) {
                                    Icon(Icons.Outlined.Shuffle, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text("Shuffle", maxLines = 1, softWrap = false)
                                }
                                IconButton(onClick = { renaming = true }) { Icon(Icons.Outlined.Edit, "Edit details") }
                                IconButton(onClick = { deleting = true }) { Icon(Icons.Outlined.Delete, "Delete playlist") }
                            }
                        }
                    }
                    if (stacked) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CoverImage(
                                playlist?.thumbnail, seed = playlistId, label = playlist?.title,
                                modifier = Modifier.size(coverSize).clip(RoundedCornerShape(20.dp)),
                                animated = true,
                            )
                            Spacer(Modifier.height(14.dp))
                            titleBlock(Modifier.fillMaxWidth(), Alignment.CenterHorizontally, TextAlign.Center)
                        }
                    } else {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.Bottom) {
                            CoverImage(
                                playlist?.thumbnail, seed = playlistId, label = playlist?.title,
                                modifier = Modifier.size(coverSize).clip(RoundedCornerShape(20.dp)),
                                animated = true,
                            )
                            titleBlock(Modifier.weight(1f).padding(start = 20.dp), Alignment.Start, TextAlign.Start)
                        }
                    }
                }
            }
            if (loaded != null && songs.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "No songs yet. Use \"Add to playlist\" from the track menu to fill this up.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                val active = song.id == playingId
                Row(
                    Modifier
                        .animateItem()
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { play(songs, index) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoverImage(song.thumbnail, seed = song.id, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(
                            song.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1,
                            overflow = TextOverflow.Clip,
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.visageMarquee(),
                        )
                        Text(
                            song.artists, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Clip,
                            modifier = Modifier.visageMarquee(),
                        )
                    }
                    if (!narrowRows) {
                        IconButton(onClick = { scope.launch(Dispatchers.IO) { repo.moveInLocalPlaylist(playlistId, index, index - 1) } }, enabled = index > 0) {
                            Icon(Icons.Filled.KeyboardArrowUp, "Move up")
                        }
                        IconButton(onClick = { scope.launch(Dispatchers.IO) { repo.moveInLocalPlaylist(playlistId, index, index + 1) } }, enabled = index < songs.lastIndex) {
                            Icon(Icons.Filled.KeyboardArrowDown, "Move down")
                        }
                    }
                    IconButton(onClick = { scope.launch(Dispatchers.IO) { repo.removeFromLocalPlaylist(playlistId, song.id) } }) {
                        Icon(Icons.Outlined.DeleteOutline, "Remove from playlist")
                    }
                }
            }
        }
    }

    if (renaming && playlist != null) {
        // Same card as New playlist, filled in: change the name, description and photo.
        com.music.vivi.compose.ui.library.sidebar.PlaylistDialog(
            title = "Edit playlist",
            confirm = "Save",
            initialName = playlist.title,
            initialDescription = playlist.description.orEmpty(),
            initialCover = playlist.thumbnail?.takeIf { it.startsWith("/") },
            onDismiss = { renaming = false },
            onConfirm = { name, description, cover ->
                renaming = false
                scope.launch(Dispatchers.IO) {
                    // Keep the automatic cover when the user hasn't chosen a photo.
                    val thumb = cover ?: playlist.thumbnail?.takeIf { !it.startsWith("/") }
                    repo.updateLocalPlaylistDetails(playlistId, name, description, thumb)
                }
            },
        )
    }
    if (deleting && playlist != null) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete \"${playlist.title}\"?") },
            text = { Text("The playlist is removed from this device. The songs themselves are not deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    scope.launch {
                        withContext(Dispatchers.IO) { repo.deleteLocalPlaylist(playlistId) }
                        navController.navigateUp()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
        )
    }
}
