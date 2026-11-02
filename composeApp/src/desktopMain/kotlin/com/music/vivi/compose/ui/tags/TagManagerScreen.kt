package com.music.vivi.compose.ui.tags

import com.music.vivi.compose.ui.component.visageMarquee
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
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.expandHorizontally
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.isSecondaryPressed
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
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
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
import com.music.vivi.compose.ui.component.PageSkeleton
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

private val PanelWidth = 460.dp

@Composable
fun TagManagerScreen(navController: NavController) {
    val repo = koinInject<LocalMusicRepository>()
    val prefs = koinInject<DesktopPrefs>()
    val scope = rememberCoroutineScope()
    // The state lives in the host, not here: auto-tagging keeps running when this page is left.
    val state = remember { TagManagerHost.get(repo, prefs) }
    val nativeWriter = TagManagerHost.nativeWriter
    LaunchedEffect(Unit) { state.reviewHidden = false; state.load() }

    Column(Modifier.fillMaxSize()) {
        DetailPageHeader(
            title = "Tag Manager",
            onBack = { navController.navigateUp() },
            subtitleInline = true,
            subtitle = when {
                state.loading -> "Reading your library"
                else -> "${state.tracks.size} local tracks" + state.missingCount.takeIf { it > 0 }?.let { " · $it need attention" }.orEmpty()
            },
        )
        AnimatedVisibility(nativeWriter?.available == false, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Banner("The native tag writer isn't available, so tags can't be read or written. Build native/visage-native and restart.", error = true)
        }
        AnimatedContent(
            targetState = state.proposals != null && !state.reviewHidden,
            transitionSpec = { fadeIn(VisageMotion.fadeInSpec()) togetherWith fadeOut(VisageMotion.fadeOutSpec()) },
            label = "tagMode",
            modifier = Modifier.weight(1f),
        ) { reviewing ->
            if (reviewing) TagReviewScreen(state)
            else Column(Modifier.fillMaxSize()) {
                Toolbar(state)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        state.loading -> PageLoadingState("Reading your library", PageSkeleton.List, header = false)
                        state.tracks.isEmpty() -> PageEmptyState(
                            "No local tracks", "Add a music folder in Settings, then scan it. The Tag Manager works on those files.",
                            actionLabel = "Manage folders",
                            onAction = { navController.navigate(com.music.vivi.compose.navigation.Routes.SETTINGS_LOCAL_OFFLINE) },
                        )
                        com.music.vivi.compose.ui.responsive.windowLayout().isCompactWidth -> Box(Modifier.fillMaxSize()) {
                            // Narrow windows: the edit panel covers the list while something is selected.
                            TrackBrowser(state, Modifier.fillMaxSize())
                            if (state.selection.ids.isNotEmpty()) {
                                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                                    Box(Modifier.fillMaxSize().padding(end = 8.dp, start = 8.dp, bottom = 8.dp)) { SidePanel(state) }
                                }
                            }
                        }
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
    val visible = remember(state.tracks, state.filter, state.query, state.modified) {
        TagBrowser.filter(state.tracks, state.filter, state.query, state.modified)
    }
    val needy = remember(visible) { visible.filter { TagHealth.of(it).any } }
    Column(Modifier.fillMaxWidth().padding(top = 0.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // The search field takes the height of the grouping selector, so the two sit flush in one row.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Too narrow for search + grouping side by side (docked panels at ~900dp windows): stack them.
        if (com.music.vivi.compose.ui.responsive.windowLayout().isCompactWidth || maxWidth < 620.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = if (maxWidth < 620.dp) 20.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SearchField(state.query, { state.query = it }, Modifier.fillMaxWidth())
                ConnectedButtonGroup(
                    options = TagGrouping.entries.map { it to it.label },
                    selected = state.grouping,
                    onSelect = { state.grouping = it },
                    size = ExpressiveSize.Small,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SearchField(state.query, { state.query = it }, Modifier.weight(1f).fillMaxHeight())
            ConnectedButtonGroup(
                options = TagGrouping.entries.map { it to it.label },
                selected = state.grouping,
                onSelect = { state.grouping = it },
                size = ExpressiveSize.Small,
                modifier = Modifier.width(340.dp),
            )
        }
        }
        val tagCompact = com.music.vivi.compose.ui.responsive.windowLayout().isCompactWidth
        val chipsRow: @Composable () -> Unit = {
            ChipsRow(
                chips = TagFilter.entries.map { it to if (it == TagFilter.All) "All  ${counts[it]}" else "${it.label}  ${counts[it]}" },
                currentValue = state.filter,
                onValueUpdate = { state.filter = it ?: TagFilter.All },
            )
        }
        val actionsRow: @Composable () -> Unit = {
            Row(Modifier.then(if (tagCompact) Modifier.padding(horizontal = 12.dp) else Modifier.padding(end = 20.dp)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val note = state.autoNote
                AnimatedVisibility(note != null, enter = fadeIn(), exit = fadeOut()) {
                    Text(note.orEmpty(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                AnimatedVisibility(state.selection.ids.isNotEmpty(), enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                    Text("${state.selection.ids.size} selected", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                val allSelected = visible.isNotEmpty() && visible.all { it.id in state.selection.ids }
                ExpressiveButton(
                    text = if (allSelected) "Clear selection" else "Select all (${visible.size})",
                    onClick = { if (allSelected) state.select(state.selection.clear()) else state.select(state.selection.selectAll(visible.map { it.id })) },
                    style = ExpressiveStyle.Tonal, size = ExpressiveSize.Small, enabled = visible.isNotEmpty(),
                )
                AnimatedVisibility(state.selection.ids.isNotEmpty(), enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                    ExpressiveButton(
                        text = "Auto-tag ${state.selection.ids.size}",
                        onClick = { state.startAutoTag() }, size = ExpressiveSize.Small, enabled = !state.autoRunning && !state.busy,
                    )
                }
                AnimatedVisibility(state.selection.ids.isEmpty() && needy.isNotEmpty(), enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                    ExpressiveButton(
                        text = "Auto-tag ${needy.size} missing",
                        onClick = { state.startAutoTag(needy) }, size = ExpressiveSize.Small, style = ExpressiveStyle.Tonal, enabled = !state.autoRunning && !state.busy,
                    )
                }
            }
        }
        if (tagCompact) {
            // Narrow: the actions sit on their own scrolling line under the filter chips.
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                chipsRow()
                Box(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState())) { actionsRow() }
            }
        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { chipsRow() }
            actionsRow()
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
        modifier.heightIn(min = 40.dp).clip(CircleShape).background(bg).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.Search, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("Search title, artist, album, file name…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            BasicTextField(
                value, onChange, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
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
    // Column widths the user dragged (by label); a column without an entry keeps its default weight.
    val weights = remember { mutableStateMapOf<String, Float>() }
    BoxWithConstraints(modifier.padding(start = 12.dp, end = 8.dp)) {
        val cols = allColumns.filter { maxWidth - (if (state.selection.ids.isEmpty()) 0.dp else 0.dp) >= it.minWidth }
        // Width left for the weighted columns: row inset 116 + end padding 12 + the cover tile.
        val areaW = maxWidth - (116.dp + 12.dp + CoverTileWidth)
        Column(Modifier.fillMaxSize()) {
            // Same insets as TrackRow (8 + checkbox 48 + cover slot 48 + gap 12), so every title sits over its column.
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(116.dp))
                cols.forEachIndexed { i, it -> Text(it.label, Modifier.weight(weights[it.label] ?: it.weight).padding(start = if (i >= 2) DividerGap else 0.dp, end = 8.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                Spacer(Modifier.width(12.dp))
                Text("Cover", Modifier.width(CoverTileWidth), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
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
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize().smoothWheelScroll(listState).dragSelect(listState, { rows }, state),
                ) {
                    items(rows, key = { it.key }) { row ->
                        when (row) {
                            is TagBrowser.Row.Header -> GroupHeader(row, Modifier.animateItem())
                            is TagBrowser.Row.Item -> TrackRow(
                                track = row.track, cols = cols, weights = weights,
                                selected = row.track.id in state.selection.ids,
                                modified = row.track.id in state.modified,
                                onClick = { shift, ctrl -> state.select(state.selection.click(row.track.id, order, shift, ctrl)) },
                                onToggle = { state.select(state.selection.toggle(row.track.id)) },
                                onContext = {
                                    if (row.track.id !in state.selection.ids) state.select(TagSelection(setOf(row.track.id), row.track.id))
                                    state.selectedTracks.ifEmpty { listOf(row.track) }
                                },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
            if (visible.isNotEmpty()) ColumnDividers(cols, weights, areaW)
            }
        }
    }
}

private val CoverTileWidth = 65.dp

/** Gap between a column divider and the text of the column to its right (14dp in the Penpot design). */
private val DividerGap = 14.dp

/** Dotted, draggable dividers in the gutter left of every column from Album on; dragging moves width between the two neighbours. */
@Composable
private fun ColumnDividers(cols: List<Col>, weights: SnapshotStateMap<String, Float>, areaW: Dp) {
    val density = LocalDensity.current
    val total = cols.sumOf { (weights[it.label] ?: it.weight).toDouble() }.toFloat()
    var x = 116.dp
    cols.forEachIndexed { i, c ->
        if (i >= 2) {
            val left = cols[i - 1]
            Box(
                Modifier.offset(x = x - 6.dp).width(12.dp).fillMaxHeight()
                    .pointerHoverIcon(PointerIcon.Hand)
                    .pointerInput(left.label, c.label, total, areaW) {
                        detectHorizontalDragGestures { change, dx ->
                            change.consume()
                            val dw = dx / density.density / areaW.value * total
                            val lw = weights[left.label] ?: left.weight
                            val rw = weights[c.label] ?: c.weight
                            if (lw + dw >= left.weight * 0.35f && rw - dw >= c.weight * 0.35f) {
                                weights[left.label] = lw + dw
                                weights[c.label] = rw - dw
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxHeight().width(1.dp)) {
                    drawLine(
                        Color.White.copy(alpha = 0.5f), Offset(0f, 0f), Offset(0f, size.height),
                        strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())),
                    )
                }
            }
        }
        x += areaW * ((weights[c.label] ?: c.weight) / total)
    }
}

/**
 * Press on a row and drag across others to select them all; the rows are added to the current selection, or removed from
 * it when the drag starts on a selected row. Near the top or
 * bottom edge the list scrolls on its own. A plain click is left to the row.
 */
private fun Modifier.dragSelect(listState: LazyListState, rows: () -> List<TagBrowser.Row>, state: TagManagerState): Modifier =
    pointerInput(listState, state) {
        val slop = viewConfiguration.touchSlop
        val edge = 56.dp.toPx()
        var active = false
        var lastY = 0f
        var edgeSpeed = 0f
        var startIndex = -1
        var base: Set<String> = emptySet()
        var anchorId: String? = null
        var removing = false

        fun indexAt(y: Float): Int? =
            listState.layoutInfo.visibleItemsInfo.firstOrNull { y >= it.offset && y < it.offset + it.size + 2 }?.index

        fun apply() {
            val list = rows()
            val y = lastY.coerceIn(0f, size.height - 1f)
            val visible = listState.layoutInfo.visibleItemsInfo
            val current = indexAt(y) ?: (if (lastY < 0f) visible.firstOrNull()?.index else visible.lastOrNull()?.index) ?: return
            val from = minOf(startIndex, current)
            val to = maxOf(startIndex, current)
            val ids = (from..to).mapNotNull { (list.getOrNull(it) as? TagBrowser.Row.Item)?.track?.id }
            state.selection = TagSelection(if (removing) base - ids.toSet() else base + ids, anchorId)
        }

        coroutineScope {
        // Keeps scrolling while the pointer rests near an edge.
        launch {
            while (true) {
                delay(16)
                if (active && edgeSpeed != 0f) {
                    listState.dispatchRawDelta(edgeSpeed)
                    apply()
                }
            }
        }

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val list = rows()
            val first = indexAt(down.position.y) ?: return@awaitEachGesture
            val item = list.getOrNull(first) as? TagBrowser.Row.Item ?: return@awaitEachGesture
            startIndex = first
            anchorId = item.track.id
            lastY = down.position.y
            active = false
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    lastY = change.position.y
                    if (!active && kotlin.math.abs(change.position.y - down.position.y) > slop) {
                        active = true
                        // Dragging always builds on what is selected: it adds the rows swept over, or, when it starts on a
                        // selected row, takes them out again. A new drag therefore never throws the selection away.
                        base = state.selection.ids
                        removing = item.track.id in base
                    }
                    if (active) {
                        change.consume()
                        edgeSpeed = when {
                            lastY < edge -> -(edge - lastY) * 0.5f
                            lastY > size.height - edge -> (lastY - (size.height - edge)) * 0.5f
                            else -> 0f
                        }.coerceIn(-48f, 48f)
                        apply()
                    }
                }
            } finally {
                edgeSpeed = 0f
                if (active) {
                    active = false
                    state.loadEditor() // read the files once the drag is over, not on every row passed
                }
            }
        }
        }
    }

@Composable
private fun GroupHeader(h: TagBrowser.Row.Header, modifier: Modifier) {
    Row(modifier.fillMaxWidth().padding(start = 12.dp, top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(h.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.weight(1f, fill = false).visageMarquee())
        Text("${h.count} · ${h.subtitle}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.visageMarquee())
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun TrackRow(
    track: TagTrack,
    cols: List<Col>,
    weights: SnapshotStateMap<String, Float>,
    selected: Boolean,
    modified: Boolean,
    onClick: (shift: Boolean, ctrl: Boolean) -> Unit,
    onToggle: () -> Unit,
    onContext: () -> List<TagTrack>,
    modifier: Modifier = Modifier,
) {
    val health = remember(track) { TagHealth.of(track) }
    var menuFor by remember { mutableStateOf<List<TagTrack>?>(null) }
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        VisageMotion.effectsDefault(), label = "rowBg",
    )
    val radius by animateDpAsState(if (selected) 18.dp else 12.dp, VisageMotion.spatialFast(), label = "rowRadius")
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .onPointerEvent(PointerEventType.Release) { e ->
                // Opens on release: a popup shown on press is dismissed by the same click's release.
                if (e.button == PointerButton.Secondary) menuFor = onContext()
            }
            .pointerInput(track.id) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (currentEvent.buttons.isSecondaryPressed || currentEvent.button == PointerButton.Secondary) {
                        waitForUpOrCancellation()
                        return@awaitEachGesture
                    }
                    val up = waitForUpOrCancellation()
                    if (up != null) {
                        val m = currentEvent.keyboardModifiers
                        onClick(m.isShiftPressed, m.isCtrlPressed || m.isMetaPressed)
                    }
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        menuFor?.let { TrackContextMenu(it) { menuFor = null } }
        Row(
            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(radius)).background(bg)
                .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(selected, { onToggle() }, Modifier.size(48.dp))
            // 48dp slot, 38dp cover (the Penpot "Tag Manager" design sizes every cover like this).
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(6.dp))) {
                    CoverImage(track.coverPath?.takeIf { !it.startsWith("opus-lazy://") && it != "embedded" }?.let { File(it) }, seed = track.id, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.width(12.dp))
            cols.forEachIndexed { ci, c ->
                val text = c.text(track)
                val miss = text.isBlank() || c.missing?.invoke(health) == true
                val color by animateColorAsState(
                    if (miss && c.missing != null) MaterialTheme.colorScheme.error.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurface,
                    VisageMotion.effectsDefault(), label = "cellColor",
                )
                Text(
                    text.ifBlank { "—" }, Modifier.weight(weights[c.label] ?: c.weight).padding(start = if (ci >= 2) DividerGap else 0.dp, end = 8.dp).visageMarquee(),
                    style = if (c.label == "Title") MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium) else MaterialTheme.typography.bodyLarge,
                    color = if (text.isBlank() && c.missing == null) MaterialTheme.colorScheme.onSurfaceVariant else color,
                    maxLines = 1, overflow = TextOverflow.Clip,
                )
            }
        }
        // Cover status tile: a separate rounded tile at the row's end, tonal when the file has a cover, error-tinted when it lacks one.
        Box(
            Modifier.width(CoverTileWidth).fillMaxHeight().clip(RoundedCornerShape(radius))
                .background((if (health.missingCover) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer).copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (health.missingCover) Icons.Outlined.Close else Icons.Outlined.Check, if (health.missingCover) "No cover" else "Has cover", Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
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
    val prefs = koinInject<DesktopPrefs>()
    val playing by com.music.vivi.compose.LocalPlaybackController.current.nowPlaying.collectAsState()
    val pillStyle = remember { com.music.vivi.compose.ui.resolveMiniPlayerStyle(prefs) }
    val mode = when {
        state.busy -> 0
        state.autoRunning -> 1
        state.report != null -> 2
        else -> 3
    }
    // The floating mini-player sits bottom-centre: split into two islands around it, or lift above it when too narrow.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val pillW = minOf(720.dp, maxWidth - 48.dp)
        val side = (maxWidth - 40.dp - pillW - 16.dp) / 2
        val avoid = playing != null && pillStyle == com.music.vivi.compose.prefs.MiniPlayerStyle.FLOATING_PILL
        val split = avoid && side >= 190.dp
        val lift = if (avoid && !split) 92.dp else 0.dp
        AnimatedVisibility(
            show,
            enter = slideInVertically(VisageMotion.spatialDefault()) { it } + fadeIn(),
            exit = slideOutVertically(VisageMotion.spatialDefault()) { it } + fadeOut(),
        ) {
            var expanded by remember(state.report) { mutableStateOf(false) }
            val rep = state.report
            val r = rep
            val head = when {
                r == null -> "Last change: ${last?.entries?.size ?: 0} file(s) can be restored."
                r.undo -> "Restored ${r.succeeded} file(s)" + if (r.failed.isNotEmpty()) " · ${r.failed.size} failed" else ""
                r.results.isEmpty() -> "Nothing to change"
                else -> "Updated ${r.succeeded} file(s)" + if (r.failed.isNotEmpty()) " · ${r.failed.size} failed" else ""
            }
            val actions: (@Composable RowScope.() -> Unit)? = when (mode) {
                1 -> ({ ExpressiveButton("Stop", { state.cancelAutoTag() }, size = ExpressiveSize.XSmall, style = ExpressiveStyle.Tonal) })
                2 -> ({
                    if (r != null && r.failed.isNotEmpty()) ExpressiveButton(if (expanded) "Hide details" else "Details", { expanded = !expanded }, size = ExpressiveSize.XSmall, style = ExpressiveStyle.Text)
                    if (r != null && !r.undo && last != null) ExpressiveButton("Undo", { state.undo() }, size = ExpressiveSize.XSmall, style = ExpressiveStyle.Tonal, leadingIcon = Icons.Outlined.Undo)
                    IconButton(onClick = { state.dismissReport() }, Modifier.size(32.dp)) { Icon(Icons.Outlined.Close, "Dismiss", Modifier.size(18.dp)) }
                })
                3 -> ({ ExpressiveButton("Undo", { state.undo() }, size = ExpressiveSize.XSmall, style = ExpressiveStyle.Tonal, leadingIcon = Icons.Outlined.Undo) })
                else -> null
            }
            val body: @Composable () -> Unit = {
                when (mode) {
                    0 -> Text("Writing tags… ${state.progressDone}/${state.progressTotal}", style = MaterialTheme.typography.bodyMedium)
                    1 -> Text("Looking up matches… ${state.autoDone}/${state.autoTotal}", style = MaterialTheme.typography.bodyMedium)
                    else -> Text(head, style = MaterialTheme.typography.bodyMedium, fontWeight = if (mode == 2) FontWeight.SemiBold else null)
                }
            }
            val progress: @Composable () -> Unit = {
                if (mode <= 1) {
                    val done = if (mode == 0) state.progressDone else state.autoDone
                    val total = if (mode == 0) state.progressTotal else state.autoTotal
                    val p by animateFloatAsState(if (total == 0) 0f else done.toFloat() / total, VisageMotion.glide(), label = "tagProgress")
                    LinearProgressIndicator({ p }, Modifier.fillMaxWidth().padding(top = 8.dp).height(4.dp).clip(CircleShape))
                }
            }
            val details: @Composable () -> Unit = {
                AnimatedVisibility(mode == 2 && expanded && r != null, enter = expandVertically(VisageMotion.spatialDefault()) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        r?.failed?.take(6)?.forEach {
                            Text("${File(it.item.path).name}: ${it.error ?: "failed"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if ((r?.failed?.size ?: 0) > 6) Text("…and ${(r?.failed?.size ?: 0) - 6} more", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (split) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.Bottom) {
                    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, tonalElevation = 3.dp, modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) { body(); progress(); details() }
                    }
                    Spacer(Modifier.width(pillW + 16.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        if (actions != null) Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, tonalElevation = 3.dp) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 10.dp + lift),
                ) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) { body() }
                            actions?.invoke(this)
                        }
                        progress(); details()
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackContextMenu(tracks: List<TagTrack>, onDismiss: () -> Unit) {
    val controller = com.music.vivi.compose.LocalPlaybackController.current
    val scope = rememberCoroutineScope()
    val many = tracks.size > 1
    fun queue(t: TagTrack) = com.music.vivi.player.QueueTrack(
        videoId = "local:${t.id}",
        title = t.tags.value(TagField.Title).ifBlank { t.stem },
        artist = t.tags.value(TagField.Artist),
        artworkUrl = t.coverPath?.takeIf { !it.startsWith("opus-lazy://") && it != "embedded" }?.let { File(it).toURI().toString() },
        localPath = t.path,
        durationMs = (t.durationSec ?: 0L) * 1000L,
        album = t.tags.value(TagField.Album).ifBlank { null },
    )
    val suffix = if (many) " (${tracks.size})" else ""
    androidx.compose.material3.DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        androidx.compose.material3.DropdownMenuItem(
            text = { Text("Play$suffix") },
            onClick = { onDismiss(); scope.launch(kotlinx.coroutines.Dispatchers.IO) { controller.playLocalQueue(tracks.map(::queue), 0) } },
        )
        androidx.compose.material3.DropdownMenuItem(
            text = { Text("Play next$suffix") },
            onClick = { onDismiss(); tracks.asReversed().forEach { controller.playNext(queue(it)) } },
        )
        androidx.compose.material3.DropdownMenuItem(
            text = { Text("Add to queue$suffix") },
            onClick = { onDismiss(); tracks.forEach { controller.addToQueue(queue(it)) } },
        )
        androidx.compose.material3.HorizontalDivider()
        androidx.compose.material3.DropdownMenuItem(
            text = { Text("Open containing folder") },
            onClick = { onDismiss(); revealInFileManager(tracks.first().path) },
        )
        androidx.compose.material3.DropdownMenuItem(
            text = { Text("Copy path") },
            onClick = {
                onDismiss()
                com.music.vivi.compose.ui.component.SystemClipboard.copyAsync(tracks.joinToString("\n") { it.path })
            },
        )
    }
}

private fun revealInFileManager(path: String) {
    Thread({ revealInFileManagerBlocking(path) }, "reveal-in-file-manager").apply { isDaemon = true }.start()
}

private fun revealInFileManagerBlocking(path: String) {
    runCatching {
        val f = File(path)
        if (com.music.vivi.nativelib.HostOs.isWindows) {
            ProcessBuilder("explorer", "/select,", f.absolutePath).start()
        } else {
            val ok = runCatching {
                ProcessBuilder(
                    "dbus-send", "--session", "--dest=org.freedesktop.FileManager1", "--type=method_call",
                    "/org/freedesktop/FileManager1", "org.freedesktop.FileManager1.ShowItems",
                    "array:string:${f.toURI()}", "string:",
                ).start().let { p ->
                    if (p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) p.exitValue() == 0 else { p.destroyForcibly(); false }
                }
            }.getOrDefault(false)
            if (!ok) ProcessBuilder("xdg-open", f.parent ?: "/").start()
        }
    }
}
