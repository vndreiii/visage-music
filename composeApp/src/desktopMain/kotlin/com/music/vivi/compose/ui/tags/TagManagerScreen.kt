package com.music.vivi.compose.ui.tags

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.PrefKeys
import com.music.vivi.compose.ui.component.ChipsRow
import com.music.vivi.compose.ui.component.CoverImage
import com.music.vivi.compose.ui.component.DetailPageHeader
import com.music.vivi.compose.ui.component.PageEmptyState
import com.music.vivi.compose.ui.component.PageLoadingState
import com.music.vivi.compose.ui.component.smoothWheelScroll
import com.music.vivi.compose.ui.expressive.ConnectedButtonGroup
import com.music.vivi.compose.ui.expressive.ExpressiveButton
import com.music.vivi.compose.ui.expressive.ExpressiveSize
import com.music.vivi.compose.ui.expressive.ExpressiveStyle
import com.music.vivi.compose.ui.library.LibraryCache
import com.music.vivi.compose.ui.motion.VisageMotion
import com.music.vivi.database.DatabaseFactory
import com.music.vivi.database.LocalMusicRepository
import com.music.vivi.nativelib.AppPaths
import org.koin.compose.koinInject
import org.koin.core.context.GlobalContext
import java.io.File

private val PanelWidth = 420.dp

@Composable
fun TagManagerScreen(navController: NavController) {
    val repo = koinInject<LocalMusicRepository>()
    val prefs = koinInject<DesktopPrefs>()
    val scope = rememberCoroutineScope()
    val nativeWriter = remember { NativeTagWriter { DatabaseFactory.coversDir() } }
    val state = remember {
        TagManagerState(
            scope = scope,
            repo = repo,
            prefs = prefs,
            writer = nativeWriter,
            lookup = NativeTagLookup {
                prefs.getString(PrefKeys.TagAcoustidKey, "").ifBlank { System.getenv("VISAGE_ACOUSTID_KEY").orEmpty() }.ifBlank { null }
            },
            backupRoot = File(AppPaths.data(), "tag-backups"),
            refreshLibrary = {
                runCatching { GlobalContext.get().get<LibraryCache>().loadLocal() }
            },
        )
    }
    LaunchedEffect(Unit) { state.load() }

    Column(Modifier.fillMaxSize()) {
        DetailPageHeader(
            title = "Tag Manager",
            onBack = { navController.navigateUp() },
            subtitle = when {
                state.loading -> "Reading your library"
                else -> "${state.tracks.size} local tracks" + state.missingCount.takeIf { it > 0 }?.let { " · $it need attention" }.orEmpty()
            },
        )
        AnimatedVisibility(!nativeWriter.available, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Banner("The native tag writer isn't available, so tags can't be read or written. Build native/visage-native and restart.", error = true)
        }
        AnimatedContent(
            targetState = state.proposals != null,
            transitionSpec = { fadeIn(VisageMotion.fadeInSpec()) togetherWith fadeOut(VisageMotion.fadeOutSpec()) },
            label = "tagMode",
            modifier = Modifier.weight(1f),
        ) { reviewing ->
            if (reviewing) TagReviewScreen(state)
            else Column(Modifier.fillMaxSize()) {
                Toolbar(state)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        state.loading -> PageLoadingState("Reading your library")
                        state.tracks.isEmpty() -> PageEmptyState(
                            "No local tracks", "Add a music folder in Settings, then scan it. The Tag Manager works on those files.",
                            actionLabel = "Manage folders",
                            onAction = { navController.navigate(com.music.vivi.compose.navigation.Routes.SETTINGS_LOCAL_OFFLINE) },
                        )
                        else -> Row(Modifier.fillMaxSize()) {
                            TrackBrowser(state, Modifier.weight(1f).fillMaxHeight())
                            val open = state.selection.ids.isNotEmpty()
                            val w by animateDpAsState(if (open) PanelWidth else 0.dp, VisageMotion.rail(), label = "tagPanelWidth")
                            Box(Modifier.width(w).fillMaxHeight().clipToBounds()) {
                                Box(Modifier.requiredWidth(PanelWidth).fillMaxHeight().padding(end = 16.dp, bottom = 8.dp)) { SidePanel(state) }
                            }
                        }
                    }
                }
            }
        }
        StatusBar(state)
    }
}

@Composable
private fun Banner(text: String, error: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ── toolbar ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun Toolbar(state: TagManagerState) {
    val counts = remember(state.tracks, state.modified) { TagBrowser.counts(state.tracks, state.modified) }
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SearchField(state.query, { state.query = it }, Modifier.weight(1f))
            ConnectedButtonGroup(
                options = TagGrouping.entries.map { it to it.label },
                selected = state.grouping,
                onSelect = { state.grouping = it },
                size = ExpressiveSize.XSmall,
                modifier = Modifier.width(280.dp),
            )
        }
        ChipsRow(
            chips = TagFilter.entries.map { it to if (it == TagFilter.All) "All  ${counts[it]}" else "${it.label}  ${counts[it]}" },
            currentValue = state.filter,
            onValueUpdate = { state.filter = it ?: TagFilter.All },
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val visible = remember(state.tracks, state.filter, state.query, state.modified) {
                TagBrowser.filter(state.tracks, state.filter, state.query, state.modified)
            }
            val allSelected = visible.isNotEmpty() && visible.all { it.id in state.selection.ids }
            ExpressiveButton(
                text = if (allSelected) "Clear selection" else "Select all (${visible.size})",
                onClick = { if (allSelected) state.select(state.selection.clear()) else state.select(state.selection.selectAll(visible.map { it.id })) },
                style = ExpressiveStyle.Tonal, size = ExpressiveSize.XSmall, enabled = visible.isNotEmpty(),
            )
            AnimatedVisibility(state.selection.ids.isNotEmpty(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                ExpressiveButton(
                    text = "Auto-tag ${state.selection.ids.size}",
                    onClick = { state.startAutoTag() }, size = ExpressiveSize.XSmall, enabled = !state.autoRunning && !state.busy,
                )
            }
            val needy = remember(visible) { visible.filter { TagHealth.of(it).any } }
            AnimatedVisibility(state.selection.ids.isEmpty() && needy.isNotEmpty(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                ExpressiveButton(
                    text = "Auto-tag ${needy.size} missing",
                    onClick = { state.startAutoTag(needy) }, size = ExpressiveSize.XSmall, style = ExpressiveStyle.Tonal, enabled = !state.autoRunning && !state.busy,
                )
            }
            Spacer(Modifier.weight(1f))
            val note = state.autoNote
            AnimatedVisibility(note != null, enter = fadeIn(), exit = fadeOut()) {
                Text(note.orEmpty(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${state.selection.ids.size} selected", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focused = remember { mutableStateOf(false) }
    val bg by animateColorAsState(
        if (focused.value) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh,
        VisageMotion.effectsDefault(), label = "searchBg",
    )
    Row(
        modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(bg).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.Search, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("Search title, artist, album, file name…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BasicTextField(
                value, onChange, singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false); focused.value = true } },
            )
        }
        AnimatedVisibility(value.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
            IconButton(onClick = { onChange("") }, Modifier.size(28.dp)) { Icon(Icons.Outlined.Close, "Clear", Modifier.size(18.dp)) }
        }
    }
}

// ── track list ───────────────────────────────────────────────────────────────────────────

private class Col(val label: String, val weight: Float, val minWidth: Dp, val text: (TagTrack) -> String, val missing: ((TagHealth) -> Boolean)? = null)

private val allColumns = listOf(
    Col("Title", 2.2f, 0.dp, { it.tags.value(TagField.Title) }) { it.missingTitle },
    Col("Artist", 1.6f, 0.dp, { it.tags.value(TagField.Artist) }) { it.missingArtist },
    Col("Album", 1.6f, 0.dp, { it.tags.value(TagField.Album) }) { it.missingAlbum },
    Col("Album artist", 1.2f, 1000.dp, { it.tags.value(TagField.AlbumArtist) }),
    Col("Year", 0.55f, 760.dp, { it.tags.value(TagField.Year) }),
    Col("Genre", 1f, 1150.dp, { it.tags.value(TagField.Genre) }),
    Col("#", 0.4f, 760.dp, { it.tags.value(TagField.TrackNo) }),
    Col("Composer", 1f, 1300.dp, { it.tags.value(TagField.Composer) }),
)

@Composable
private fun TrackBrowser(state: TagManagerState, modifier: Modifier) {
    val visible by remember { derivedStateOf { TagBrowser.filter(state.tracks, state.filter, state.query, state.modified) } }
    val rows by remember { derivedStateOf { TagBrowser.rows(visible, state.grouping) } }
    val order by remember { derivedStateOf { rows.filterIsInstance<TagBrowser.Row.Item>().map { it.track.id } } }
    val listState = rememberLazyListState()
    BoxWithConstraints(modifier.padding(start = 12.dp, end = 8.dp)) {
        val cols = allColumns.filter { maxWidth - (if (state.selection.ids.isEmpty()) 0.dp else 0.dp) >= it.minWidth }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(48.dp + 52.dp))
                cols.forEach { Text(it.label, Modifier.weight(it.weight).padding(end = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                Text("Cover", Modifier.width(48.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedContent(visible.isEmpty(), transitionSpec = { fadeIn(VisageMotion.fadeInSpec()) togetherWith fadeOut(VisageMotion.fadeOutSpec()) }, label = "tagEmpty") { empty ->
                if (empty) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (state.filter == TagFilter.All && state.query.isBlank()) "Nothing here" else "No tracks match this filter",
                            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxSize().smoothWheelScroll(listState),
                ) {
                    items(rows, key = { it.key }) { row ->
                        when (row) {
                            is TagBrowser.Row.Header -> GroupHeader(row, Modifier.animateItem())
                            is TagBrowser.Row.Item -> TrackRow(
                                track = row.track, cols = cols,
                                selected = row.track.id in state.selection.ids,
                                modified = row.track.id in state.modified,
                                onClick = { shift, ctrl -> state.select(state.selection.click(row.track.id, order, shift, ctrl)) },
                                onToggle = { state.select(state.selection.toggle(row.track.id)) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(h: TagBrowser.Row.Header, modifier: Modifier) {
    Row(modifier.fillMaxWidth().padding(start = 12.dp, top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(h.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Text("${h.count} · ${h.subtitle}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TrackRow(
    track: TagTrack,
    cols: List<Col>,
    selected: Boolean,
    modified: Boolean,
    onClick: (shift: Boolean, ctrl: Boolean) -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val health = remember(track) { TagHealth.of(track) }
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        VisageMotion.effectsDefault(), label = "rowBg",
    )
    val radius by animateDpAsState(if (selected) 18.dp else 12.dp, VisageMotion.spatialFast(), label = "rowRadius")
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius))
            .background(bg)
            .pointerInput(track.id) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val up = waitForUpOrCancellation()
                    if (up != null) {
                        val m = currentEvent.keyboardModifiers
                        onClick(m.isShiftPressed, m.isCtrlPressed || m.isMetaPressed)
                    }
                }
            }
            .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(selected, { onToggle() }, Modifier.size(40.dp))
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))) {
            CoverImage(track.coverPath?.takeIf { !it.startsWith("opus-lazy://") && it != "embedded" }?.let { File(it) }, seed = track.id, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(12.dp))
        cols.forEach { c ->
            val text = c.text(track)
            val miss = text.isBlank() || c.missing?.invoke(health) == true
            val color by animateColorAsState(
                if (miss && c.missing != null) MaterialTheme.colorScheme.error.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurface,
                VisageMotion.effectsDefault(), label = "cellColor",
            )
            Text(
                text.ifBlank { "—" }, Modifier.weight(c.weight).padding(end = 8.dp),
                style = if (c.label == "Title") MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium) else MaterialTheme.typography.bodyMedium,
                color = if (text.isBlank() && c.missing == null) MaterialTheme.colorScheme.onSurfaceVariant else color,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.width(48.dp), contentAlignment = Alignment.CenterStart) {
            val tint by animateColorAsState(if (health.missingCover) MaterialTheme.colorScheme.error.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary, VisageMotion.effectsDefault(), label = "coverTint")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (health.missingCover) Icons.Outlined.Image else Icons.Outlined.Check, if (health.missingCover) "No cover" else "Has cover", Modifier.size(18.dp), tint = tint)
                if (modified) Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
                if (track.readonly) Icon(Icons.Outlined.ErrorOutline, "Read-only", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ── status / progress / undo ─────────────────────────────────────────────────────────────

@Composable
private fun StatusBar(state: TagManagerState) {
    val last by state.lastBatch.collectAsState()
    val show = state.busy || state.autoRunning || state.report != null || last != null
    AnimatedVisibility(
        show,
        enter = slideInVertically(VisageMotion.spatialDefault()) { it } + fadeIn(),
        exit = slideOutVertically(VisageMotion.spatialDefault()) { it } + fadeOut(),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                AnimatedContent(
                    targetState = when {
                        state.busy -> 0
                        state.autoRunning -> 1
                        state.report != null -> 2
                        else -> 3
                    },
                    transitionSpec = { fadeIn(VisageMotion.fadeInSpec()) togetherWith fadeOut(VisageMotion.fadeOutSpec()) },
                    label = "tagStatus",
                ) { mode ->
                    when (mode) {
                        0 -> ProgressLine("Writing tags… ${state.progressDone}/${state.progressTotal}", state.progressDone, state.progressTotal, null)
                        1 -> ProgressLine("Looking up matches… ${state.autoDone}/${state.autoTotal}", state.autoDone, state.autoTotal) {
                            ExpressiveButton("Stop", { state.cancelAutoTag() }, size = ExpressiveSize.XSmall, style = ExpressiveStyle.Tonal)
                        }
                        2 -> ReportLine(state)
                        else -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Last change: ${last?.entries?.size ?: 0} file(s) can be restored.", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            ExpressiveButton("Undo", { state.undo() }, size = ExpressiveSize.XSmall, style = ExpressiveStyle.Tonal, leadingIcon = Icons.Outlined.Undo)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressLine(text: String, done: Int, total: Int, trailing: (@Composable RowScope.() -> Unit)?) {
    val p by animateFloatAsState(if (total == 0) 0f else done.toFloat() / total, VisageMotion.glide(), label = "tagProgress")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            trailing?.invoke(this)
        }
        LinearProgressIndicator({ p }, Modifier.fillMaxWidth().height(4.dp).clip(CircleShape))
    }
}

@Composable
private fun ReportLine(state: TagManagerState) {
    val r = state.report ?: return
    var expanded by remember(r) { mutableStateOf(false) }
    val last by state.lastBatch.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val head = when {
                r.undo -> "Restored ${r.succeeded} file(s)" + if (r.failed.isNotEmpty()) " · ${r.failed.size} failed" else ""
                r.results.isEmpty() -> "Nothing to change"
                else -> "Updated ${r.succeeded} file(s)" + if (r.failed.isNotEmpty()) " · ${r.failed.size} failed" else ""
            }
            Text(head, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (r.failed.isNotEmpty()) ExpressiveButton(if (expanded) "Hide details" else "Details", { expanded = !expanded }, size = ExpressiveSize.XSmall, style = ExpressiveStyle.Text)
            if (!r.undo && last != null) ExpressiveButton("Undo", { state.undo() }, size = ExpressiveSize.XSmall, style = ExpressiveStyle.Tonal, leadingIcon = Icons.Outlined.Undo)
            IconButton(onClick = { state.dismissReport() }, Modifier.size(32.dp)) { Icon(Icons.Outlined.Close, "Dismiss", Modifier.size(18.dp)) }
        }
        AnimatedVisibility(expanded, enter = expandVertically(VisageMotion.spatialDefault()) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.fillMaxWidth().height(120.dp).let { it }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                r.failed.take(6).forEach {
                    Text("${File(it.item.path).name}: ${it.error ?: "failed"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (r.failed.size > 6) Text("…and ${r.failed.size - 6} more", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
