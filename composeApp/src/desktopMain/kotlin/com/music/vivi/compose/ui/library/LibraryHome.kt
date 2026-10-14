package com.music.vivi.compose.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.vivi.compose.ui.component.YouTubeGridItem
import com.music.vivi.compose.ui.component.horizontalWheelScroll
import com.music.vivi.compose.ui.component.smoothWheelScroll
import com.music.vivi.database.LocalMusicRepository
import com.music.vivi.database.Local_album
import com.music.vivi.database.Local_artist
import com.music.vivi.database.Local_track
import com.music.vivi.compose.ui.motion.ViviMotion
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically

/**
 * The Library home: a second Home for what's on this computer — generated mixes, genres, decades,
 * composers, artists, albums — with everything from YouTube Music kept in its own labelled
 * "Online" container so it never reads as part of the local library.
 */
@Composable
internal fun LibraryHomeFeed(
    yt: YtLibraryData,
    local: LocalFeed,
    offlineCount: Int,
    playingId: String?,
    isPlaying: Boolean,
    onLikedClick: () -> Unit,
    onOfflineClick: () -> Unit,
    onPlayLocal: (List<Local_track>, Int) -> Unit,
    onOpen: (OpenCollection) -> Unit,
    onManageFolders: () -> Unit,
    onYtItem: (YTItem, List<SongItem>) -> Unit,
    error: String?,
    refreshing: Boolean,
    onRetry: () -> Unit,
    /** Bumped when the Library tab is tapped again: scroll back to the top. */
    resetTick: Int = 0,
) {
    val state = rememberLazyListState()
    androidx.compose.runtime.LaunchedEffect(resetTick) {
        if (resetTick > 0) state.animateScrollToItem(0)
    }
    val mixes = remember(local.tracks, local.rediscover, local.genres) {
        buildMixes(local.tracks, local.rediscover, local.genres)
    }
    val artistCovers = remember(local.albums) {
        local.albums.filter { it.artist_id != null && it.cover_path != null }
            .groupBy { it.artist_id!! }
            .mapValues { it.value.first().cover_path }
    }
    val hasLocal = local.tracks.isNotEmpty()

    LazyColumn(
        state = state,
        contentPadding = PaddingValues(bottom = 128.dp),
        modifier = Modifier.fillMaxSize().smoothWheelScroll(state),
    ) {
        item(key = "local-header") {
            SectionContainer(
                icon = Icons.Outlined.Folder,
                title = "On this device",
                badge = if (hasLocal) "${local.tracks.size} tracks" else null,
                action = { TextButton(onClick = onManageFolders) { Text("Manage folders") } },
            ) {
                if (!hasLocal) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(8.dp)) {
                        Text("Make yourself at home", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Add your music folders to find albums, genres, mixes and your most-played tracks here.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = onManageFolders) { Text("Add music folders") }
                    }
                } else {
                    Text(
                        "${local.albums.size} albums · ${local.artists.size} artists" +
                            (if (local.genres.isNotEmpty()) " · ${local.genres.size} genres" else ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }

        if (mixes.isNotEmpty()) item(key = "mixes") {
            Shelf("Made for you") {
                items(mixes, key = { it.id }) { mix ->
                    MixCard(mix) { onOpen(OpenCollection.Mix(mix.title, mix.tracks)) }
                }
            }
        }
        if (local.recentTracks.isNotEmpty()) item(key = "recent-tracks") {
            LocalTrackShelf("Recently added", local.recentTracks, playingId, isPlaying, onPlayLocal)
        }
        if (local.genres.isNotEmpty()) item(key = "genres") {
            Shelf("Genres") {
                items(local.genres.take(24), key = { it.value }) { g ->
                    LabelCard(g.value, "${g.trackCount} tracks", tintFor(g.value)) { onOpen(OpenCollection.Genre(g.value)) }
                }
            }
        }
        if (local.recentAlbums.isNotEmpty()) item(key = "albums") {
            LocalAlbumShelf("New albums", local.recentAlbums) { onOpen(OpenCollection.Album(it.id, it.title)) }
        }
        if (local.artists.isNotEmpty()) item(key = "artists") {
            Shelf("Artists") {
                items(local.artists.take(30), key = { it.id }) { artist ->
                    ArtistAvatar(artist, artistCovers[artist.id]) { onOpen(OpenCollection.Artist(artist.id, artist.name)) }
                }
            }
        }
        if (local.years.size >= 2) item(key = "years") {
            Shelf("Years") {
                items(local.years.take(30), key = { it.value }) { y ->
                    LabelCard("${y.value}", "${y.trackCount} tracks", tintFor("${y.value}", 0.5f)) { onOpen(OpenCollection.Year(y.value)) }
                }
            }
        }
        if (local.composers.isNotEmpty()) item(key = "composers") {
            Shelf("Composers") {
                items(local.composers.take(24), key = { it.value }) { c ->
                    LabelCard(c.value, "${c.trackCount} tracks", tintFor(c.value, 0.5f), icon = Icons.Outlined.Edit) {
                        onOpen(OpenCollection.Composer(c.value))
                    }
                }
            }
        }
        if (local.heavy.isNotEmpty()) item(key = "heavy") {
            LocalTrackShelf("Heavy rotation", local.heavy, playingId, isPlaying, onPlayLocal)
        }
        if (local.rediscover.isNotEmpty()) item(key = "rediscover") {
            LocalTrackShelf("Rediscover", local.rediscover, playingId, isPlaying, onPlayLocal)
        }

        item(key = "online") {
            SectionContainer(
                icon = Icons.Outlined.CloudQueue,
                title = "YouTube Music",
                badge = "Online",
                accent = true,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    LikedTile(songCount = yt.songs.size, onClick = onLikedClick, modifier = Modifier.weight(1f))
                    OfflineTile(count = offlineCount, onClick = onOfflineClick, modifier = Modifier.weight(1f))
                }
                if (error != null) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Online library unavailable", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = onRetry, enabled = !refreshing) { Text("Retry") }
                    }
                }
                if (yt.playlists.isNotEmpty()) {
                    Text("Your playlists", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val rowState = rememberLazyListState()
                    LazyRow(
                        state = rowState,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.horizontalWheelScroll(rowState),
                    ) {
                        items(yt.playlists.distinctBy { it.id }, key = { it.id }) { playlist ->
                            YouTubeGridItem(item = playlist, onClick = { onYtItem(playlist, emptyList()) }, modifier = Modifier.width(172.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Rounded container that groups a section and labels where its content comes from. */
@Composable
private fun SectionContainer(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    badge: String?,
    accent: Boolean = false,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val container = if (accent) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainer
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = container,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .then(if (accent) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(28.dp)) else Modifier),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(
                        if (accent) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon, null, Modifier.size(20.dp),
                        tint = if (accent) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (badge != null) {
                    AssistChip(onClick = {}, label = { Text(badge, style = MaterialTheme.typography.labelMedium) })
                }
                Spacer(Modifier.weight(1f))
                action?.invoke()
            }
            content()
        }
    }
}

@Composable
private fun Shelf(title: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column(Modifier.padding(top = 12.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        val rowState = rememberLazyListState()
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().horizontalWheelScroll(rowState),
            content = content,
        )
    }
}

/** Generated mix: a 2x2 collage of its covers under a tinted scrim with the title on top. */
@Composable
private fun MixCard(mix: LibraryMix, onClick: () -> Unit) {
    val tint = tintFor(mix.title, 0.45f)
    val covers = remember(mix.tracks) {
        mix.tracks.mapNotNull { it.cover_path }.distinct().take(4)
    }
    Surface(onClick = onClick, shape = RoundedCornerShape(24.dp), modifier = Modifier.size(176.dp)) {
        Box(Modifier.fillMaxSize().background(tint)) {
            if (covers.isNotEmpty()) {
                val cells = if (covers.size >= 4) covers else List(4) { covers[it % covers.size] }
                Column(Modifier.fillMaxSize()) {
                    for (row in 0..1) {
                        Row(Modifier.weight(1f)) {
                            for (col in 0..1) {
                                Box(Modifier.weight(1f).fillMaxSize()) {
                                    LazyCoverImage(cells[row * 2 + col], Modifier.fillMaxSize(), ContentScale.Crop)
                                }
                            }
                        }
                    }
                }
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.25f to Color.Transparent, 1f to tint.copy(alpha = 0.95f)),
                ),
            )
            Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                Text(mix.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(mix.subtitle, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
            }
            Icon(
                Icons.Outlined.QueueMusic, null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(20.dp),
            )
        }
    }
}

/** Colourful label card for genres, decades/years and composers. */
@Composable
private fun LabelCard(
    title: String,
    subtitle: String,
    tint: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Outlined.MusicNote,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, shape = RoundedCornerShape(22.dp), modifier = Modifier.width(168.dp).height(92.dp)) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(listOf(tint, tint.copy(red = tint.red * 0.55f, green = tint.green * 0.55f, blue = tint.blue * 0.7f))),
            ),
        ) {
            Icon(icon, null, tint = Color.White.copy(alpha = 0.22f), modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(44.dp))
            Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun ArtistAvatar(artist: Local_artist, coverPath: String?, onClick: () -> Unit) {
    Column(
        Modifier.width(104.dp).clip(RoundedCornerShape(16.dp)).clickableNoRipple(onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (coverPath != null) LazyCoverImage(coverPath, Modifier.fillMaxSize(), ContentScale.Crop)
            else Icon(Icons.Outlined.MusicNote, null)
        }
        Spacer(Modifier.height(8.dp))
        Text(artist.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))
