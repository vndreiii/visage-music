package com.music.vivi.compose.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.SidebarKeys
import com.music.vivi.compose.ui.component.CoverImage
import com.music.vivi.compose.ui.component.ListThumbnailSize
import com.music.vivi.compose.ui.expressive.ExpressiveButton
import com.music.vivi.compose.ui.expressive.ExpressiveSize
import com.music.vivi.compose.ui.expressive.ExpressiveStyle
import com.music.vivi.compose.ui.library.YtLibraryData
import com.music.vivi.compose.ui.library.sidebar.EntryKind
import com.music.vivi.compose.ui.library.sidebar.HiddenItemRow
import com.music.vivi.compose.ui.library.sidebar.LibraryEntry
import com.music.vivi.compose.ui.library.sidebar.hiddenItemRows
import com.music.vivi.compose.ui.library.sidebar.libraryEntries
import com.music.vivi.compose.ui.library.sidebar.rememberLibraryCache
import com.music.vivi.compose.ui.motion.VisageMotion
import com.music.vivi.database.Downloaded_track
import com.music.vivi.database.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

private val PlaylistTypes = setOf(EntryKind.YT_PLAYLIST.name, EntryKind.LOCAL_PLAYLIST.name)

/**
 * Hidden library-sidebar items as a list. Each row restores just that item; "Restore all" brings back everything.
 * Rows that can no longer be resolved keep their raw id so they can still be restored.
 */
@Composable
internal fun HiddenSidebarItemsList(prefs: DesktopPrefs) {
    val repo = koinInject<LibraryRepository>()
    val cache = rememberLibraryCache()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { cache.refreshIfStale() }
    val ytData by cache.ytData.collectAsState()
    val offline by cache.offline.collectAsState()
    val folderMutations by repo.folderMutations.collectAsState()
    val playlistMutations by repo.playlistMutations.collectAsState()
    // Reloads on every hidden/unhidden change, from here or from the sidebar.
    val rows by produceState<List<HiddenItemRow>?>(null, folderMutations, playlistMutations, ytData, offline) {
        value = withContext(Dispatchers.IO) {
            runCatching { loadHiddenRows(repo, ytData, offline) }.getOrDefault(emptyList())
        }
    }
    // Keys of rows animating out. The database is only touched once the exit animation has finished.
    var leaving by remember { mutableStateOf(emptySet<String>()) }
    val current = rows
    LaunchedEffect(current) {
        if (current != null) leaving = leaving.filterTo(HashSet()) { key -> current.any { it.key == key } }
    }

    if (current == null) return
    if (current.isEmpty()) {
        Text(
            "Nothing is hidden",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        )
        return
    }

    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (current.size == 1) "1 hidden" else "${current.size} hidden",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        ExpressiveButton(
            "Restore all",
            onClick = {
                leaving = current.mapTo(HashSet()) { it.key }
                prefs.setBoolean(SidebarKeys.LibraryShowHidden, false)
            },
            size = ExpressiveSize.XSmall,
            style = ExpressiveStyle.Text,
        )
    }
    Column(Modifier.fillMaxWidth()) {
        current.forEach { row ->
            key(row.key) {
                HiddenItemRowAnimated(
                    row = row,
                    leaving = row.key in leaving,
                    onRestore = { leaving = leaving + row.key },
                    onGone = { scope.launch(Dispatchers.IO) { runCatching { repo.unhideItem(row.type, row.id) } } },
                )
            }
        }
    }
}

/** Fades and collapses the row out before [onGone] runs, so a restored row never pops out of the list. */
@Composable
private fun HiddenItemRowAnimated(
    row: HiddenItemRow,
    leaving: Boolean,
    onRestore: () -> Unit,
    onGone: () -> Unit,
) {
    val state = remember { MutableTransitionState(true) }
    state.targetState = !leaving
    LaunchedEffect(state.isIdle, state.currentState) {
        if (state.isIdle && !state.currentState) onGone()
    }
    AnimatedVisibility(
        visibleState = state,
        enter = fadeIn(VisageMotion.fadeInSpec()) + expandVertically(VisageMotion.glide()),
        exit = fadeOut(VisageMotion.fadeOutSpec()) + shrinkVertically(VisageMotion.glide()),
    ) {
        HiddenItemRowView(row, onRestore)
    }
}

@Composable
private fun HiddenItemRowView(row: HiddenItemRow, onRestore: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HiddenItemCover(row, Modifier.size(ListThumbnailSize))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(row.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                row.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onRestore, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Outlined.Visibility, contentDescription = "Restore ${row.title}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HiddenItemCover(row: HiddenItemRow, modifier: Modifier) {
    val shape = RoundedCornerShape(10.dp)
    val iconTint = Color.White
    when {
        row.kind == EntryKind.LIKED -> Box(
            modifier.clip(shape).background(Brush.linearGradient(listOf(Color(0xFF6C4DFF), Color(0xFFB9A6FF)))),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Favorite, null, tint = iconTint, modifier = Modifier.size(24.dp)) }
        row.kind == EntryKind.DOWNLOADS -> Box(
            modifier.clip(shape).background(Brush.linearGradient(listOf(Color(0xFF1F9D74), Color(0xFF8FE3C2)))),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Download, null, tint = iconTint, modifier = Modifier.size(24.dp)) }
        row.kind == null -> Box(
            modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Outlined.HelpOutline, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp)) }
        else -> CoverImage(
            row.thumbnail,
            seed = row.id,
            modifier = modifier.clip(shape),
            label = row.title.takeIf { row.thumbnail == null },
        )
    }
}

/** Resolves the persisted hidden set against the loaded library, falling back to the playlist table for stray playlists. */
private fun loadHiddenRows(repo: LibraryRepository, yt: YtLibraryData, offline: List<Downloaded_track>): List<HiddenItemRow> {
    val hidden = repo.hiddenItems()
    if (hidden.isEmpty()) return emptyList()
    val entries = libraryEntries(yt, offline, repo.localPlaylists())
    val known = entries.mapTo(HashSet()) { it.kind.name to it.id }
    val extra: List<LibraryEntry> = hidden.filter { it !in known && it.first in PlaylistTypes }.mapNotNull { (type, id) ->
        repo.getPlaylist(id)?.let { p ->
            LibraryEntry(
                key = "$type:$id", id = id, kind = EntryKind.valueOf(type), title = p.title,
                creator = p.author.orEmpty(), trackCount = p.song_count?.toInt(), thumbnail = p.thumbnail,
            )
        }
    }
    return hiddenItemRows(hidden, entries + extra)
}
