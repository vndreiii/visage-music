package com.music.vivi.compose.ui.library.sidebar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowRight
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.music.vivi.compose.LocalPlaybackController
import com.music.vivi.compose.navigation.Routes
import com.music.vivi.compose.navigation.navigateMain
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.LibrarySidebarState
import com.music.vivi.compose.prefs.LibraryViewMode
import com.music.vivi.compose.prefs.SidebarConfig
import com.music.vivi.compose.prefs.SidebarKeys
import com.music.vivi.compose.prefs.SidebarSide
import com.music.vivi.compose.ui.component.CoverImage
import com.music.vivi.compose.ui.component.VisageToast
import com.music.vivi.compose.ui.component.WheelLazyRow
import com.music.vivi.compose.ui.component.smoothWheelScroll
import com.music.vivi.compose.ui.library.LibraryCache
import com.music.vivi.compose.ui.library.libraryModule
import com.music.vivi.compose.ui.motion.VisageMotion
import com.music.vivi.compose.local.toQueueTrack as offlineToQueueTrack
import com.music.vivi.core.youtube.YouTubeMusicService
import com.music.vivi.database.LibraryRepository
import com.music.vivi.player.PlaybackState
import com.music.vivi.player.QueueTrack
import com.music.vivi.player.toQueueTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules

/** Remembers which sidebar entry the user last started so the row can show the equalizer. */
object LibraryPlayback {
    var playingKey by mutableStateOf<String?>(null)
}

internal val RowCover = 48.dp
private val ContentPad = 14.dp

/**
 * Spotify-style "Your Library" panel. Same motion language as the pinned queue rail: slides and
 * expands in/out with [VisageMotion.rail] springs inside a rounded inset surface. Between
 * [LibrarySidebarState.EXPANDED] and [LibrarySidebarState.RAIL] the width springs while one shared
 * list keeps the covers in place and fades the text/header away, so covers morph, not swap.
 */
@Composable
fun AnimatedLibrarySidebar(
    state: LibrarySidebarState,
    side: SidebarSide,
    expandedWidth: Dp,
    navController: NavController,
    onStateChange: (LibrarySidebarState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fromRight = side == SidebarSide.RIGHT
    val offset = VisageMotion.rail<IntOffset>()
    val size = VisageMotion.rail<IntSize>()
    AnimatedVisibility(
        visible = state != LibrarySidebarState.HIDDEN,
        enter = slideInHorizontally(animationSpec = offset) { if (fromRight) it else -it } +
            expandHorizontally(animationSpec = size, expandFrom = if (fromRight) Alignment.End else Alignment.Start) +
            fadeIn(VisageMotion.fadeInSpec()),
        exit = slideOutHorizontally(animationSpec = offset) { if (fromRight) it else -it } +
            shrinkHorizontally(animationSpec = size, shrinkTowards = if (fromRight) Alignment.End else Alignment.Start) +
            fadeOut(VisageMotion.fadeOutSpec()),
        modifier = modifier,
    ) {
        val railW = SidebarConfig.RailWidth.dp
        val target = if (state == LibrarySidebarState.RAIL) railW else expandedWidth
        val width by animateDpAsState(target, VisageMotion.rail(), label = "libWidth")
        val span = (expandedWidth - railW).value.coerceAtLeast(1f)
        // 1 = fully expanded, 0 = rail. Drives text/header alpha and the filter block height.
        val f = ((width - railW).value / span).coerceIn(0f, 1f)
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .padding(
                    start = if (fromRight) 4.dp else 8.dp, end = if (fromRight) 8.dp else 4.dp,
                    top = 8.dp, bottom = 8.dp,
                )
                .width(width),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
            tonalElevation = 3.dp,
        ) {
            Box(Modifier.fillMaxSize()) {
                // Fixed-width content anchored to the start edge: the surface only reveals/clips it.
                LibraryPanel(
                    f = f,
                    expanded = state == LibrarySidebarState.EXPANDED,
                    side = side,
                    navController = navController,
                    onStateChange = onStateChange,
                    // Root-cause fix for the "empty rail": requiredWidth centres an oversized child inside a
                    // narrower parent, which pushed every cover out of the clipped surface. Measure at the
                    // full width but always place at the start edge so the surface merely reveals it.
                    modifier = Modifier.fillMaxHeight().layout { m, c ->
                        val w = expandedWidth.roundToPx()
                        val p = m.measure(androidx.compose.ui.unit.Constraints.fixed(w, c.maxHeight))
                        layout(c.maxWidth, c.maxHeight) { p.place(0, 0) }
                    },
                )
            }
        }
    }
}

@Composable
private fun rememberLibraryCache(): LibraryCache = remember {
    try {
        GlobalContext.get().get<LibraryCache>()
    } catch (e: Exception) {
        loadKoinModules(libraryModule)
        GlobalContext.get().get<LibraryCache>()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryPanel(
    f: Float,
    expanded: Boolean,
    side: SidebarSide,
    navController: NavController,
    onStateChange: (LibrarySidebarState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val prefs = koinInject<DesktopPrefs>()
    val yt = koinInject<YouTubeMusicService>()
    val repo = koinInject<LibraryRepository>()
    val cache = rememberLibraryCache()
    val controller = LocalPlaybackController.current
    val scope = rememberCoroutineScope()

    // Stale-while-revalidate: whatever Home/Library already cached renders immediately.
    LaunchedEffect(Unit) { cache.refreshIfStale() }
    val ytData by cache.ytData.collectAsState()
    val offline by cache.offline.collectAsState()
    val mutations by repo.playlistMutations.collectAsState()
    val locals by produceState(emptyList<com.music.vivi.database.Playlist>(), mutations) {
        value = withContext(Dispatchers.IO) { runCatching { repo.localPlaylists() }.getOrDefault(emptyList()) }
    }

    val entries = remember(ytData, offline, locals) {
        buildEntries(
            ytPlaylists = ytData.playlists.mapIndexed { i, p ->
                LibraryEntry(
                    key = "yt:${p.id}", id = p.id, kind = EntryKind.YT_PLAYLIST, title = p.title,
                    creator = p.author?.name.orEmpty(),
                    trackCount = p.songCountText?.let { Regex("""\d[\d,]*""").find(it)?.value?.replace(",", "")?.toIntOrNull() },
                    thumbnail = p.thumbnail, recentRank = 1_000 + i,
                )
            },
            localPlaylists = locals.mapIndexed { i, p ->
                LibraryEntry(
                    key = "local:${p.id}", id = p.id, kind = EntryKind.LOCAL_PLAYLIST, title = p.title,
                    creator = "You", trackCount = p.song_count?.toInt(), thumbnail = p.thumbnail, recentRank = i,
                )
            },
            albums = ytData.albums.mapIndexed { i, a ->
                LibraryEntry(
                    key = "album:${a.id}", id = a.id, kind = EntryKind.ALBUM, title = a.title,
                    creator = a.artists?.joinToString { it.name }.orEmpty(), thumbnail = a.thumbnail, recentRank = 2_000 + i,
                )
            },
            artists = ytData.artists.mapIndexed { i, a ->
                LibraryEntry(
                    key = "artist:${a.id}", id = a.id, kind = EntryKind.ARTIST, title = a.title,
                    creator = a.title, thumbnail = a.thumbnail, recentRank = 3_000 + i,
                )
            },
            liked = ytData.songs.takeIf { it.isNotEmpty() }?.let {
                LibraryEntry("liked", "LM", EntryKind.LIKED, "Liked Songs", "Playlist", it.size, it.first().thumbnail)
            },
            downloads = offline.takeIf { it.isNotEmpty() }?.let {
                LibraryEntry("downloads", "offline", EntryKind.DOWNLOADS, "Downloaded", "Available offline", it.size, null)
            },
        )
    }

    // Snapshot state, initialised from prefs and written through, so every control applies instantly.
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(runCatching { LibraryFilter.valueOf(prefs.getString(SidebarKeys.LibraryFilter, "ALL")) }.getOrDefault(LibraryFilter.ALL)) }
    var sort by remember { mutableStateOf(runCatching { LibrarySort.valueOf(prefs.getString(SidebarKeys.LibrarySort, "RECENT")) }.getOrDefault(LibrarySort.RECENT)) }
    var view by remember { mutableStateOf(runCatching { LibraryViewMode.valueOf(prefs.getString(SidebarKeys.LibraryView, "LIST")) }.getOrDefault(LibraryViewMode.LIST)) }
    var showHidden by remember { mutableStateOf(prefs.getBoolean(SidebarKeys.LibraryShowHidden, false)) }
    @Suppress("UNUSED_VARIABLE") val prefsVersion = com.music.vivi.compose.ui.settings.rememberPrefsVersion(prefs)
    val foldersFirst = prefs.getBoolean(SidebarKeys.LibraryFoldersFirst, true)

    // Folders + hidden items (sidebar-only metadata).
    val folderMutations by repo.folderMutations.collectAsState()
    val folderData by produceState(LibraryRepository.FolderState(emptyList(), emptyList(), emptySet()), folderMutations) {
        value = withContext(Dispatchers.IO) {
            runCatching { LibraryRepository.FolderState(repo.libraryFolders(), repo.folderItems(), repo.hiddenItems()) }
                .getOrDefault(LibraryRepository.FolderState(emptyList(), emptyList(), emptySet()))
        }
    }
    val byDbKey = remember(entries) { entries.associateBy { it.kind.name to it.id } }
    val folderModels = remember(folderData, byDbKey) {
        folderData.folders.map { f ->
            LibraryFolderModel(
                id = f.id, title = f.title, icon = f.icon, accent = f.accent,
                coverKind = runCatching { CoverKind.valueOf(f.cover_kind) }.getOrDefault(CoverKind.AUTO),
                coverRef = f.cover_ref,
                sort = runCatching { FolderSort.valueOf(f.sort) }.getOrDefault(FolderSort.MANUAL),
                expanded = f.expanded != 0L, position = f.position.toInt(),
                children = folderData.items.filter { it.folder_id == f.id }.sortedBy { it.position }
                    .mapNotNull { byDbKey[it.item_type to it.item_id]?.key },
            )
        }
    }
    val hiddenKeys = remember(folderData, byDbKey) { folderData.hidden.mapNotNull { byDbKey[it]?.key }.toSet() }
    val rows by remember(entries, folderModels, hiddenKeys, showHidden, filter, sort, foldersFirst) {
        derivedStateOf { buildSidebarRows(entries, folderModels, hiddenKeys, showHidden, filter, sort, query, foldersFirst) }
    }
    val thumbsByFolder = remember(folderModels, entries) {
        val by = entries.associateBy { it.key }
        folderModels.associate { f -> f.id to f.children.mapNotNull { by[it] }.map { ChildThumb(it.thumbnail, it.id) } }
    }
    var editingFolder by remember { mutableStateOf<LibraryFolderModel?>(null) }
    var creatingFolder by remember { mutableStateOf<LibraryEntry?>(null) }
    var showFolderDialog by remember { mutableStateOf(false) }
    var deletingFolder by remember { mutableStateOf<LibraryFolderModel?>(null) }

    var toast by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<LibraryEntry?>(null) }
    var deleting by remember { mutableStateOf<LibraryEntry?>(null) }

    val playback = remember(yt, repo, cache, controller) {
        EntryPlayback(yt, repo, cache, controller) { toast = it }
    }
    val actions = remember(navController, playback) {
        EntryActions(
            open = { e ->
                when (e.kind) {
                    EntryKind.LIKED -> navController.navigate(Routes.onlinePlaylist("LM")) { launchSingleTop = true }
                    EntryKind.DOWNLOADS ->
                        if (navController is androidx.navigation.NavHostController) navigateMain(navController, Routes.LIBRARY)
                        else navController.navigate(Routes.LIBRARY) { launchSingleTop = true }
                    EntryKind.YT_PLAYLIST -> navController.navigate(Routes.onlinePlaylist(e.id)) { launchSingleTop = true }
                    EntryKind.LOCAL_PLAYLIST -> navController.navigate(Routes.localPlaylist(e.id)) { launchSingleTop = true }
                    EntryKind.ALBUM -> navController.navigate(Routes.album(e.id)) { launchSingleTop = true }
                    EntryKind.ARTIST -> navController.navigate(Routes.artist(e.id)) { launchSingleTop = true }
                }
            },
            play = { e -> scope.launch { playback.play(e, shuffled = false) } },
            shuffle = { e -> scope.launch { playback.play(e, shuffled = true) } },
            enqueue = { e -> scope.launch { playback.enqueue(e) } },
            rename = { renaming = it },
            delete = { deleting = it },
            hide = { e -> scope.launch(Dispatchers.IO) { repo.hideItem(e.dbType(), e.id) } },
            unhide = { e -> scope.launch(Dispatchers.IO) { repo.unhideItem(e.dbType(), e.id) } },
            folders = folderModels,
            addToFolder = { e, f -> scope.launch(Dispatchers.IO) { repo.addToFolder(f.id, e.dbType(), e.id) }; toast = "Added to ${f.title}" },
            removeFromFolder = { e -> scope.launch(Dispatchers.IO) { repo.removeFromFolder(e.dbType(), e.id) } },
            newFolderFor = { e -> creatingFolder = e; showFolderDialog = true },
        )
    }
    val folderActions = remember(repo, scope) {
        FolderActions(
            toggle = { f -> scope.launch(Dispatchers.IO) { repo.setFolderExpanded(f.id, !f.expanded) } },
            edit = { editingFolder = it; showFolderDialog = true },
            delete = { deletingFolder = it },
            setSort = { f, s -> scope.launch(Dispatchers.IO) { repo.setFolderSort(f.id, s.name) } },
        )
    }
    fun setFilter(v: LibraryFilter) { filter = v; prefs.setString(SidebarKeys.LibraryFilter, v.name) }
    fun setSort(v: LibrarySort) { sort = v; prefs.setString(SidebarKeys.LibrarySort, v.name) }
    fun setView(v: LibraryViewMode) { view = v; prefs.setString(SidebarKeys.LibraryView, v.name) }
    fun setShowHidden(v: Boolean) { showHidden = v; prefs.setBoolean(SidebarKeys.LibraryShowHidden, v) }

    Column(modifier.padding(top = 8.dp)) {
        // Header: library icon doubles as the expand/collapse handle; the rest fades with f.
        Row(
            Modifier.fillMaxWidth().height(52.dp).padding(start = 14.dp, end = ContentPad - 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { onStateChange(if (expanded) LibrarySidebarState.RAIL else LibrarySidebarState.EXPANDED) },
                modifier = Modifier.size(RowCover),
            ) {
                Icon(Icons.Outlined.LibraryMusic, contentDescription = if (expanded) "Collapse library" else "Expand library")
            }
            Row(Modifier.weight(1f).alpha(f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Your Library",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 6.dp).weight(1f),
                )
                Box {
                    var addMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { addMenu = true }, enabled = f > 0.5f) {
                        Icon(Icons.Outlined.Add, contentDescription = "Create")
                    }
                    DropdownMenu(
                        expanded = addMenu, onDismissRequest = { addMenu = false },
                        shape = RoundedCornerShape(20.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        MenuItem("New playlist", Icons.Outlined.QueueMusic) { addMenu = false; showCreate = true }
                        MenuItem("New folder", Icons.Outlined.CreateNewFolder) {
                            addMenu = false; creatingFolder = null; editingFolder = null; showFolderDialog = true
                        }
                    }
                }
                IconButton(onClick = { onStateChange(LibrarySidebarState.RAIL) }, enabled = f > 0.5f) {
                    Icon(
                        if (side == SidebarSide.RIGHT) Icons.Outlined.KeyboardDoubleArrowRight else Icons.Outlined.KeyboardDoubleArrowLeft,
                        contentDescription = "Collapse to rail",
                    )
                }
                IconButton(onClick = { onStateChange(LibrarySidebarState.HIDDEN) }, enabled = f > 0.5f) {
                    Icon(Icons.Outlined.Close, contentDescription = "Hide library")
                }
            }
        }

        // Search + chips + sort: height and alpha follow the width animation.
        Column(
            Modifier
                .layout { m, c ->
                    val p = m.measure(c)
                    layout(p.width, (p.height * f).toInt()) { p.place(0, 0) }
                }
                .alpha(f),
        ) {
            SearchField(
                query = query,
                onQuery = { query = it },
                modifier = Modifier.padding(horizontal = ContentPad, vertical = 4.dp),
            )
            WheelLazyRow(
                contentPadding = PaddingValues(horizontal = ContentPad),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 4.dp),
            ) {
                items(LibraryFilter.entries.toList()) { fl ->
                    FilterChip(
                        selected = fl == filter,
                        onClick = { setFilter(fl) },
                        label = { Text(fl.label) },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    )
                }
            }
            SortBar(
                sort = sort,
                view = view,
                onSort = { setSort(it) },
                onView = { setView(it) },
                hiddenCount = hiddenKeys.size,
                showHidden = showHidden,
                onShowHidden = { setShowHidden(it) },
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val showGrid = view == LibraryViewMode.GRID && expanded
            AnimatedContent(
                targetState = showGrid,
                transitionSpec = { fadeIn(VisageMotion.fadeInSpec(60)) togetherWith fadeOut(VisageMotion.fadeOutSpec()) },
                label = "libViewMode",
            ) { grid ->
                when {
                    rows.isEmpty() -> EmptyState(
                        loading = ytData.playlists.isEmpty() && ytData.error == null,
                        searching = query.isNotBlank() || filter != LibraryFilter.ALL,
                        onCreate = { showCreate = true },
                        f = f,
                    )
                    grid -> {
                        val gridState = rememberLazyGridState()
                        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Adaptive(116.dp),
                            contentPadding = PaddingValues(start = ContentPad, end = ContentPad, bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize().smoothWheelScroll(gridState),
                        ) {
                            items(
                                rows, key = { it.key },
                                span = { r -> if (r is SidebarRow.Folder) androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) else androidx.compose.foundation.lazy.grid.GridItemSpan(1) },
                            ) { r ->
                                when (r) {
                                    is SidebarRow.Folder -> FolderRowView(r, thumbsByFolder[r.folder.id].orEmpty(), 1f, folderActions, Modifier.animateItem())
                                    is SidebarRow.Item -> LibraryGridCard(r, playback, actions, Modifier.animateItem())
                                }
                            }
                        }
                    }
                    else -> {
                        val listState = rememberLazyListState()
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            modifier = Modifier.fillMaxSize().smoothWheelScroll(listState),
                        ) {
                            items(rows, key = { it.key }) { r ->
                                when (r) {
                                    is SidebarRow.Folder -> FolderRowView(r, thumbsByFolder[r.folder.id].orEmpty(), f, folderActions, Modifier.animateItem())
                                    is SidebarRow.Item -> LibraryRow(r, f, playback, actions, Modifier.animateItem())
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    VisageToast(toast, onDismiss = { toast = null })

    if (showFolderDialog) {
        val editing = editingFolder
        val pending = creatingFolder
        FolderDialog(
            initial = editing,
            thumbs = editing?.let { thumbsByFolder[it.id] }.orEmpty().ifEmpty { listOfNotNull(pending?.let { ChildThumb(it.thumbnail, it.id) }) },
            onDismiss = { showFolderDialog = false; editingFolder = null; creatingFolder = null },
            onSave = { d ->
                showFolderDialog = false; editingFolder = null; creatingFolder = null
                scope.launch(Dispatchers.IO) {
                    if (editing != null) repo.updateFolder(editing.id, d.title, d.icon, d.accent, d.coverKind.name, d.coverRef)
                    else {
                        val id = repo.createFolder(d.title, d.icon, d.accent, d.coverKind.name, d.coverRef)
                        if (pending != null) repo.addToFolder(id, pending.dbType(), pending.id)
                    }
                }
            },
        )
    }
    deletingFolder?.let { f ->
        AlertDialog(
            onDismissRequest = { deletingFolder = null },
            title = { Text("Delete folder \"${f.title}\"?") },
            text = { Text("Only the folder is removed. Its ${f.children.size} item(s) return to your library list and nothing is deleted from YouTube Music.") },
            confirmButton = {
                TextButton(onClick = {
                    deletingFolder = null
                    scope.launch(Dispatchers.IO) { repo.deleteFolder(f.id); FolderCovers.discard(f.coverRef.takeIf { f.coverKind == CoverKind.IMAGE }) }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deletingFolder = null }) { Text("Cancel") } },
        )
    }

    if (showCreate) NameDialog(
        title = "New playlist", initial = "", confirm = "Create",
        onDismiss = { showCreate = false },
        onConfirm = { name ->
            showCreate = false
            scope.launch {
                val id = withContext(Dispatchers.IO) { repo.createLocalPlaylist(name) }
                navController.navigate(Routes.localPlaylist(id)) { launchSingleTop = true }
            }
        },
    )
    renaming?.let { e ->
        NameDialog(
            title = "Rename playlist", initial = e.title, confirm = "Rename",
            onDismiss = { renaming = null },
            onConfirm = { name ->
                renaming = null
                scope.launch(Dispatchers.IO) { repo.renameLocalPlaylist(e.id, name) }
            },
        )
    }
    deleting?.let { e ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${e.title}\"?") },
            text = { Text("The playlist is removed from this device. The songs themselves are not deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch(Dispatchers.IO) { repo.deleteLocalPlaylist(e.id) }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

/** Callbacks shared by rows, cards and their context menus. */
internal class EntryActions(
    val open: (LibraryEntry) -> Unit,
    val play: (LibraryEntry) -> Unit,
    val shuffle: (LibraryEntry) -> Unit,
    val enqueue: (LibraryEntry) -> Unit,
    val rename: (LibraryEntry) -> Unit,
    val delete: (LibraryEntry) -> Unit,
    val hide: (LibraryEntry) -> Unit,
    val unhide: (LibraryEntry) -> Unit,
    val folders: List<LibraryFolderModel>,
    val addToFolder: (LibraryEntry, LibraryFolderModel) -> Unit,
    val removeFromFolder: (LibraryEntry) -> Unit,
    val newFolderFor: (LibraryEntry) -> Unit,
)

/** Resolves an entry to tracks and starts / queues them. Network lookups are done on demand. */
internal class EntryPlayback(
    private val yt: YouTubeMusicService,
    private val repo: LibraryRepository,
    private val cache: LibraryCache,
    private val controller: com.music.vivi.player.PlaybackController,
    private val say: (String) -> Unit,
) {
    private suspend fun tracks(e: LibraryEntry): List<QueueTrack> = withContext(Dispatchers.IO) {
        when (e.kind) {
            EntryKind.LIKED -> cache.ytData.value.songs.map { it.toQueueTrack() }
            EntryKind.DOWNLOADS -> cache.offline.value.map { it.offlineToQueueTrack() }
            EntryKind.YT_PLAYLIST -> yt.playlist(e.id).getOrThrow().songs.map { it.toQueueTrack() }
            EntryKind.ALBUM -> yt.album(e.id).getOrThrow().songs.map { it.toQueueTrack() }
            EntryKind.LOCAL_PLAYLIST -> repo.getPlaylistSongs(e.id).map { it.toQueueTrack() }
            EntryKind.ARTIST -> emptyList()
        }
    }

    suspend fun play(e: LibraryEntry, shuffled: Boolean) {
        val now = controller.nowPlaying.value
        // Resume instead of restarting when this is the playlist the user already started.
        if (!shuffled && LibraryPlayback.playingKey == e.key && now != null) {
            if (controller.state.value == PlaybackState.Playing) controller.pause() else controller.play()
            return
        }
        val list = runCatching { tracks(e) }.getOrElse { say(it.message ?: "Couldn't load ${e.title}"); return }
        if (list.isEmpty()) { say(if (e.kind == EntryKind.ARTIST) "Open the artist to play" else "${e.title} is empty"); return }
        val queue = if (shuffled) list.shuffled() else list
        LibraryPlayback.playingKey = e.key
        withContext(Dispatchers.IO) {
            if (queue.all { !it.localPath.isNullOrBlank() }) controller.playLocalQueue(queue, 0)
            else controller.playQueue(queue, 0)
        }
    }

    suspend fun enqueue(e: LibraryEntry) {
        val list = runCatching { tracks(e) }.getOrElse { say(it.message ?: "Couldn't load ${e.title}"); return }
        if (list.isEmpty()) { say("Nothing to add"); return }
        list.forEach { controller.addToQueue(it) }
        say("Added ${list.size} to queue")
    }
}

internal fun com.music.vivi.database.Song.toQueueTrack(): QueueTrack = QueueTrack(
    videoId = id,
    title = title,
    artist = artists,
    artworkUrl = thumbnail,
    localPath = local_path,
    durationMs = (duration_sec ?: 0L) * 1000L,
    album = album,
)

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.fillMaxWidth().height(40.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Search, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text("Search your library", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val clearAlpha by animateFloatAsState(if (query.isNotEmpty()) 1f else 0f, VisageMotion.effectsFast(), label = "clear")
            Icon(
                Icons.Outlined.Close, "Clear search",
                Modifier.size(18.dp).alpha(clearAlpha).clip(CircleShape).clickable(enabled = query.isNotEmpty()) { onQuery("") },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SortBar(
    sort: LibrarySort, view: LibraryViewMode, onSort: (LibrarySort) -> Unit, onView: (LibraryViewMode) -> Unit,
    hiddenCount: Int, showHidden: Boolean, onShowHidden: (Boolean) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = ContentPad - 4.dp, end = ContentPad - 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            TextButton(onClick = { menu = true }) {
                Text(sort.label, style = MaterialTheme.typography.labelLarge)
                Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(18.dp))
            }
            DropdownMenu(
                expanded = menu,
                onDismissRequest = { menu = false },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                LibrarySort.entries.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(s.label, fontWeight = if (s == sort) FontWeight.Bold else FontWeight.Normal) },
                        onClick = { menu = false; onSort(s) },
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        AnimatedVisibility(hiddenCount > 0 || showHidden, enter = fadeIn(VisageMotion.fadeInSpec()), exit = fadeOut(VisageMotion.fadeOutSpec())) {
            IconButton(onClick = { onShowHidden(!showHidden) }) {
                Icon(
                    if (showHidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                    contentDescription = if (showHidden) "Stop showing hidden items" else "Show $hiddenCount hidden",
                    tint = if (showHidden) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = { onView(if (view == LibraryViewMode.LIST) LibraryViewMode.GRID else LibraryViewMode.LIST) }) {
            AnimatedContent(view, transitionSpec = { fadeIn(VisageMotion.fadeInSpec()) togetherWith fadeOut(VisageMotion.fadeOutSpec()) }, label = "viewIcon") {
                Icon(
                    if (it == LibraryViewMode.LIST) Icons.Outlined.GridView else Icons.Outlined.Menu,
                    contentDescription = if (it == LibraryViewMode.LIST) "Grid view" else "List view",
                )
            }
        }
    }
}

@Composable
private fun EmptyState(loading: Boolean, searching: Boolean, onCreate: () -> Unit, f: Float) {
    val pulse by rememberInfiniteTransition(label = "libSkeleton").animateFloat(
        0.35f, 0.8f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse",
    )
    Column(Modifier.fillMaxSize().padding(horizontal = ContentPad, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (loading && !searching) {
            repeat(5) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(RowCover).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = pulse)))
                    Column(Modifier.padding(start = 12.dp).alpha(f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.width(140.dp).height(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = pulse)))
                        Box(Modifier.width(90.dp).height(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = pulse * 0.7f)))
                    }
                }
            }
        } else if (f > 0.4f) {
            Column(Modifier.fillMaxWidth().alpha(f).padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (searching) "Nothing matches" else "Your playlists will show up here",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (searching) "Try another search or filter." else "Create one to start collecting songs.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                if (!searching) TextButton(onClick = onCreate) { Text("Create playlist") }
            }
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                label = { Text("Name") }, shape = RoundedCornerShape(14.dp),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim().ifBlank { "New playlist" }) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ── Rows ─────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun rememberEntryPlaying(e: LibraryEntry): Pair<Boolean, Boolean> {
    val controller = LocalPlaybackController.current
    val now by controller.nowPlaying.collectAsState()
    val st by controller.state.collectAsState()
    val current = LibraryPlayback.playingKey == e.key && now != null
    return current to (current && st == PlaybackState.Playing)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun LibraryRow(r: SidebarRow.Item, f: Float, playback: EntryPlayback, actions: EntryActions, modifier: Modifier = Modifier) {
    val e = r.entry
    val nested = r.folderId != null
    val folderAccent = actions.folders.firstOrNull { it.id == r.folderId }?.let { folderAccentColor(it, MaterialTheme.colorScheme.primary) }
    val dim by animateFloatAsState(if (r.hidden) 0.5f else 1f, VisageMotion.effectsDefault(), label = "hiddenDim")
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val (isCurrent, isPlaying) = rememberEntryPlaying(e)
    var menu by remember { mutableStateOf(false) }
    val bg by animateFloatAsState(if (hovered || isCurrent) 1f else 0f, VisageMotion.effectsDefault(), label = "rowBg")
    Box(modifier.alpha(dim).padding(start = if (nested) 14.dp * f else 0.dp, top = 1.dp, bottom = 1.dp)
        .let { m -> if (folderAccent != null) m.drawBehind {
            drawRoundRect(folderAccent.copy(alpha = 0.55f * f), Offset(-8.dp.toPx(), 6.dp.toPx()), Size(2.dp.toPx(), size.height - 12.dp.toPx()), CornerRadius(1.dp.toPx()))
        } else m }) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = bg * (if (isCurrent && !hovered) 0.6f else 1f)))
                .hoverable(interaction)
                .onPointerEvent(PointerEventType.Press) { if (it.buttons.isSecondaryPressed) menu = true }
                .clickable { actions.open(e) }
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EntryCover(e, hovered, isCurrent, isPlaying, onPlay = { actions.play(e) }, Modifier.size(RowCover), RoundedCornerShape(10.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp).alpha(f)) {
                Text(
                    e.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    e.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Box(Modifier.alpha(f)) {
                val moreAlpha by animateFloatAsState(if (hovered) 1f else 0f, VisageMotion.effectsFast(), label = "more")
                SourceIcon(e, Modifier.alpha(1f - moreAlpha))
                IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp).alpha(moreAlpha), enabled = f > 0.5f) {
                    Icon(Icons.Outlined.MoreVert, "More", Modifier.size(20.dp))
                }
            }
        }
        EntryMenu(menu, { menu = false }, e, actions, r.folderId, r.hidden)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun LibraryGridCard(r: SidebarRow.Item, playback: EntryPlayback, actions: EntryActions, modifier: Modifier = Modifier) {
    val e = r.entry
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val (isCurrent, isPlaying) = rememberEntryPlaying(e)
    var menu by remember { mutableStateOf(false) }
    Box(modifier.alpha(if (r.hidden) 0.5f else 1f)) {
        Column(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .hoverable(interaction)
                .onPointerEvent(PointerEventType.Press) { if (it.buttons.isSecondaryPressed) menu = true }
                .clickable { actions.open(e) }
                .padding(6.dp),
        ) {
            EntryCover(
                e, hovered, isCurrent, isPlaying, onPlay = { actions.play(e) },
                Modifier.fillMaxWidth().aspectRatio(1f),
                RoundedCornerShape(if (e.kind == EntryKind.ARTIST) 999.dp else 12.dp),
            )
            Text(
                e.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                e.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        EntryMenu(menu, { menu = false }, e, actions, r.folderId, r.hidden)
    }
}

@Composable
private fun EntryCover(
    e: LibraryEntry,
    hovered: Boolean,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier,
    shape: androidx.compose.ui.graphics.Shape,
) {
    val scrim by animateFloatAsState(if (hovered) 0.45f else if (isCurrent) 0.35f else 0f, VisageMotion.effectsDefault(), label = "scrim")
    val playAlpha by animateFloatAsState(if (hovered) 1f else 0f, VisageMotion.effectsFast(), label = "playBtn")
    val eqAlpha by animateFloatAsState(if (isCurrent && !hovered) 1f else 0f, VisageMotion.effectsFast(), label = "eq")
    val playScale by animateFloatAsState(if (hovered) 1f else 0.7f, VisageMotion.press(), label = "playScale")
    Box(modifier.clip(shape), contentAlignment = Alignment.Center) {
        when (e.kind) {
            EntryKind.LIKED -> GradientCover(Color(0xFF6C4DFF), Color(0xFFB9A6FF)) {
                Icon(Icons.Filled.Favorite, null, tint = Color.White, modifier = Modifier.fillMaxSize(0.45f))
            }
            EntryKind.DOWNLOADS -> GradientCover(Color(0xFF1F9D74), Color(0xFF8FE3C2)) {
                Icon(Icons.Outlined.Download, null, tint = Color.White, modifier = Modifier.fillMaxSize(0.5f))
            }
            else -> CoverImage(e.thumbnail, seed = e.id, modifier = Modifier.fillMaxSize(), label = e.title.takeIf { e.thumbnail == null })
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrim)))
        EqualizerBars(isPlaying, Color.White, Modifier.size(20.dp).alpha(eqAlpha))
        Surface(
            onClick = onPlay,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(32.dp).graphicsLayer { alpha = playAlpha; scaleX = playScale; scaleY = playScale },
            enabled = hovered,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(if (isCurrent && isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play", Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun GradientCover(a: Color, b: Color, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(a, b))), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun EntryMenu(expanded: Boolean, onDismiss: () -> Unit, e: LibraryEntry, a: EntryActions, folderId: String?, hidden: Boolean) {
    var page by remember(expanded) { mutableStateOf(0) }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        @Composable
        fun item(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, run: () -> Unit) =
            MenuItem(label, icon) { onDismiss(); run() }
        if (page == 0) {
            if (e.kind != EntryKind.ARTIST) {
                item("Play", Icons.Filled.PlayArrow) { a.play(e) }
                item("Shuffle", Icons.Outlined.Shuffle) { a.shuffle(e) }
                item("Add to queue", Icons.Outlined.QueueMusic) { a.enqueue(e) }
            }
            item("Open", Icons.Outlined.OpenInFull) { a.open(e) }
            if (e.kind == EntryKind.LOCAL_PLAYLIST) {
                item("Rename", Icons.Outlined.Edit) { a.rename(e) }
                item("Delete", Icons.Outlined.Delete) { a.delete(e) }
            }
            if (!e.pinned) {
                if (folderId != null) item("Remove from folder", Icons.Outlined.FolderOff) { a.removeFromFolder(e) }
                MenuItem("Add to folder", Icons.Outlined.CreateNewFolder) { page = 1 }
            }
            if (hidden) item("Unhide", Icons.Outlined.Visibility) { a.unhide(e) }
            else item("Hide", Icons.Outlined.VisibilityOff) { a.hide(e) }
        } else {
            MenuItem("Back", Icons.Outlined.ArrowBack) { page = 0 }
            a.folders.filter { it.id != folderId }.forEach { f ->
                item(f.title, folderIcon(f.icon)) { a.addToFolder(e, f) }
            }
            item("New folder…", Icons.Outlined.Add) { a.newFolderFor(e) }
        }
    }
}

/** Three bouncing bars; frozen low when paused. */
@Composable
fun EqualizerBars(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "eq")
    val h1 by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse), label = "b1")
    val h2 by t.animateFloat(0.4f, 1f, infiniteRepeatable(tween(430, easing = LinearEasing), RepeatMode.Reverse), label = "b2")
    val h3 by t.animateFloat(0.2f, 0.9f, infiniteRepeatable(tween(610, easing = LinearEasing), RepeatMode.Reverse), label = "b3")
    val live by animateFloatAsState(if (playing) 1f else 0f, VisageMotion.effectsDefault(), label = "eqLive")
    Canvas(modifier) {
        val gap = size.width * 0.12f
        val bar = (size.width - gap * 2) / 3f
        listOf(h1, h2, h3).forEachIndexed { i, h ->
            val height = size.height * (0.3f + (h - 0.3f) * live).coerceIn(0.2f, 1f)
            drawRoundRect(
                color,
                topLeft = Offset(i * (bar + gap), size.height - height),
                size = Size(bar, height),
                cornerRadius = CornerRadius(bar / 2f),
            )
        }
    }
}
