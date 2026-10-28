package com.music.vivi.compose.ui.settings.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.music.vivi.compose.navigation.Routes
import com.music.vivi.compose.navigation.SidebarShell
import com.music.vivi.compose.prefs.LibrarySidebarState
import com.music.vivi.compose.prefs.MiniPlayerStyle
import com.music.vivi.compose.prefs.SidebarConfig
import com.music.vivi.compose.prefs.SidebarKeys
import com.music.vivi.compose.prefs.Sidebars
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.reader
import com.music.vivi.compose.ui.MiniPlayerBar
import com.music.vivi.compose.ui.component.CoverImage
import com.music.vivi.compose.ui.glass.PageBackground
import com.music.vivi.compose.ui.glass.ProvideGlass
import com.music.vivi.compose.ui.glass.glassSource
import com.music.vivi.compose.ui.glass.rememberGlassEnabled
import com.music.vivi.compose.ui.library.sidebar.CoverKind
import com.music.vivi.compose.ui.library.sidebar.EntryActions
import com.music.vivi.compose.ui.library.sidebar.EntryPlayingHolder
import com.music.vivi.compose.ui.library.sidebar.FolderActions
import com.music.vivi.compose.ui.library.sidebar.LibraryFilter
import com.music.vivi.compose.ui.library.sidebar.LibraryPanelBody
import com.music.vivi.compose.ui.library.sidebar.LibrarySidebarFrame
import com.music.vivi.compose.ui.library.sidebar.LibrarySort
import com.music.vivi.compose.ui.library.sidebar.SidebarToggleButton
import com.music.vivi.compose.ui.library.sidebar.buildSidebarRows
import com.music.vivi.compose.prefs.LibraryViewMode
import com.music.vivi.compose.ui.player.queue.AnimatedQueuePinnedRail
import com.music.vivi.compose.ui.resolveMiniPlayerStyle
import com.music.vivi.compose.ui.settings.rememberPrefsVersion
import dev.chrisbanes.haze.HazeState
import org.koin.compose.koinInject

/** Which page the sidebars preview pretends to be on (the library sidebar can be limited to Home). */
enum class PreviewPage(val route: String, val label: String) {
    HOME(Routes.HOME, "Home"),
    LIBRARY(Routes.LIBRARY, "Other page"),
}

/**
 * A mini app window for the Sidebars settings page: the real [com.music.vivi.compose.ui.library.sidebar.AnimatedLibrarySidebar]
 * shell and panel layout, the real [AnimatedQueuePinnedRail], the real [SidebarShell] geometry (widths, insets, rounding)
 * and the real [MiniPlayerBar], around a sample page. Sidebar content is sample data, so nothing here touches the
 * database or network.
 *
 * Everything is derived from the live prefs on every change (see [rememberPrefsVersion]); [page] and [queueOpen] are
 * the preview's own toggles (the queue sidebar is opened by hand in the real app unless "Queue on Home" is on).
 */
@Composable
fun SidebarsPreview(page: PreviewPage, queueOpen: Boolean, modifier: Modifier = Modifier) {
    val prefs = koinInject<DesktopPrefs>()
    // Observed here, not in a child: every pref read below (config, folders-first, mini-player style) must refresh.
    @Suppress("UNUSED_VARIABLE") val observe = rememberPrefsVersion(prefs)
    val cfg = SidebarConfig.resolve(prefs.reader())
    val allowed = Sidebars.libraryAllowedOn(page.route, cfg)
    val libraryState = if (allowed) cfg.libraryState else LibrarySidebarState.HIDDEN
    val queueOn = Sidebars.queueAutoOpenOnHome(page.route, cfg, dismissedOnHome = false) || queueOpen
    val miniStyle = resolveMiniPlayerStyle(prefs)
    val glassOn = rememberGlassEnabled()
    val hazeState = remember { HazeState() }
    val pageHaze = remember { HazeState() }

    ProvideGlass(hazeState, pageState = pageHaze) {
        Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (glassOn) {
                Box(Modifier.fillMaxSize().glassSource(hazeState).glassSource(pageHaze)) { PageBackground() }
            }
            Column(Modifier.fillMaxSize()) {
                PreviewTopBar(cfg, page, libraryState, queueOn)
                SidebarShell(
                    cfg = cfg,
                    libraryState = libraryState,
                    queueLeft = queueOn && cfg.queueSide == com.music.vivi.compose.prefs.SidebarSide.LEFT,
                    queueRight = queueOn && cfg.queueSide == com.music.vivi.compose.prefs.SidebarSide.RIGHT,
                    queueWidthForLibrary = if (queueOn) SidebarConfig.QueueWidth else 0,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    librarySidebar = { side, state, width ->
                        PreviewLibrarySidebar(side, state, width, foldersFirst = prefs.getBoolean(SidebarKeys.LibraryFoldersFirst, true))
                    },
                    queueRail = { visible, fromStart ->
                        AnimatedQueuePinnedRail(visible = visible, onDismiss = {}, onExpandFullscreen = {}, fromStart = fromStart)
                    },
                ) {
                    Box(Modifier.weight(1f).fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                        PreviewHomePane(page)
                        MiniPlayerBar(
                            styleOverride = miniStyle,
                            interactive = false,
                            modifier = Modifier.align(
                                if (miniStyle == MiniPlayerStyle.COMPACT_EDGE) Alignment.BottomEnd else Alignment.BottomCenter,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewTopBar(cfg: SidebarConfig, page: PreviewPage, libraryState: LibrarySidebarState, queueOn: Boolean) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("VIsage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        listOf("Home", "Search", "Library").forEach {
            Text(
                it, style = MaterialTheme.typography.labelLarge,
                color = if (it == "Home" && page == PreviewPage.HOME || it == "Library" && page == PreviewPage.LIBRARY) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        // The same toggles the real top bar shows, driven by the same rules.
        if (page == PreviewPage.HOME && cfg.queueOnHome) {
            SidebarToggleButton(Icons.Outlined.QueueMusic, "Queue sidebar", active = queueOn, onClick = {})
        }
        if (Sidebars.libraryAllowedOn(page.route, cfg)) {
            SidebarToggleButton(Icons.Outlined.LibraryMusic, "Library", active = libraryState != LibrarySidebarState.HIDDEN, onClick = {})
        }
    }
}

@Composable
private fun PreviewLibrarySidebar(
    side: com.music.vivi.compose.prefs.SidebarSide,
    state: LibrarySidebarState,
    width: androidx.compose.ui.unit.Dp,
    foldersFirst: Boolean,
) {
    val rows = remember(foldersFirst) {
        buildSidebarRows(
            PreviewSampleData.libraryEntries, PreviewSampleData.libraryFolders, emptySet(), false,
            LibraryFilter.ALL, LibrarySort.RECENT, "", foldersFirst,
        )
    }
    val thumbs = remember { emptyMap<String, List<com.music.vivi.compose.ui.library.sidebar.ChildThumb>>() }
    val playing = remember { EntryPlayingHolder() }
    val actions = remember {
        EntryActions({}, {}, {}, {}, {}, {}, {}, {}, emptyList(), { _, _ -> }, {}, {})
    }
    val folderActions = remember { FolderActions({}, {}, {}, { _, _ -> }, { _, _ -> null }) }
    LibrarySidebarFrame(state = state, side = side, expandedWidth = width) { f, expanded, panelModifier ->
        LibraryPanelBody(
            f = f, expanded = expanded, side = side, onStateChange = {},
            rows = rows, thumbsByFolder = thumbs, hiddenCount = 0,
            query = "", onQuery = {}, filter = LibraryFilter.ALL, onFilter = {},
            sort = LibrarySort.RECENT, onSort = {}, view = LibraryViewMode.LIST, onView = {},
            showHidden = false, onShowHidden = {}, loading = false,
            playback = playing, actions = actions, folderActions = folderActions,
            onNewPlaylist = {}, onNewFolder = {}, modifier = panelModifier,
        )
    }
}

/** A sample page: a couple of shelves of generated covers, enough to read as "the app". */
@Composable
private fun PreviewHomePane(page: PreviewPage) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(
            if (page == PreviewPage.HOME) "Good evening" else "Your library",
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
        )
        PreviewData.shelves.forEach { (title, items) ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items.take(4).forEach { name ->
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            CoverImage(null, name, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)))
                            Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

private val PreviewData = PreviewSampleData
