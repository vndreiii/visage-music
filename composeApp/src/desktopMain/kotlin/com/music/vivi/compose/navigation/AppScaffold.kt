package com.music.vivi.compose.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.music.vivi.compose.prefs.LibrarySidebarState
import com.music.vivi.compose.prefs.SidebarConfig
import com.music.vivi.compose.prefs.SidebarKeys
import com.music.vivi.compose.prefs.SidebarSide
import com.music.vivi.compose.prefs.Sidebars
import com.music.vivi.compose.prefs.reader
import com.music.vivi.compose.ui.library.sidebar.AnimatedLibrarySidebar
import com.music.vivi.compose.ui.library.sidebar.SidebarToggleButton
import com.music.vivi.compose.ui.motion.VisageMotion
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.read
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.MiniPlayerStyle
import com.music.vivi.compose.prefs.PrefKeys
import com.music.vivi.compose.prefs.QueuePanelMode
import com.music.vivi.compose.ui.LoginGateScreen
import com.music.vivi.compose.ui.MiniPlayerBar
import com.music.vivi.compose.ui.PlaceholderScreen
import com.music.vivi.compose.ui.SmokePlaybackScreen
import com.music.vivi.compose.ui.VisageTopBar
import com.music.vivi.compose.ui.resolveMiniPlayerStyle
import com.music.vivi.compose.ui.album.OnlineAlbumScreen
import com.music.vivi.compose.ui.artist.ArtistScreen
import com.music.vivi.compose.ui.artist.openArtistByName
import com.music.vivi.compose.ui.home.HomeScreen
import com.music.vivi.compose.ui.library.LibraryScreen
import com.music.vivi.compose.ui.player.NowPlayingFullscreenScreen
import com.music.vivi.compose.ui.player.NowPlayingScreen
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import com.music.vivi.compose.ui.glass.glassSource
import com.music.vivi.compose.ui.player.queue.AnimatedQueueBottomTray
import com.music.vivi.compose.ui.player.queue.AnimatedQueuePinnedRail
import com.music.vivi.compose.ui.playlist.OnlinePlaylistScreen
import com.music.vivi.compose.ui.search.SearchScreen
import com.music.vivi.compose.ui.settings.AccountSettingsScreen
import com.music.vivi.compose.ui.settings.AppearanceSettingsScreen
import com.music.vivi.compose.ui.settings.DownloadsSettingsScreen
import com.music.vivi.compose.ui.settings.LocalOfflineSettingsScreen
import com.music.vivi.compose.ui.settings.NowPlayingSettingsScreen
import com.music.vivi.compose.ui.settings.PlayerSettingsScreen
import com.music.vivi.compose.ui.settings.SettingsHubScreen
import com.music.vivi.compose.ui.settings.ThemeSettingsScreen
import com.music.vivi.compose.ui.component.trackMainPane
import com.music.vivi.core.session.SessionManager
import org.koin.compose.koinInject

@Composable
fun FrameWindowScope.AppScaffold(
    windowState: WindowState,
    onClose: () -> Unit,
) {
    val sessionManager = koinInject<SessionManager>()
    val navController = rememberNavController()
    val prefs = koinInject<DesktopPrefs>()
    val playbackController = com.music.vivi.compose.LocalPlaybackController.current
    // Fetch canvases in the background so Now Playing never waits on them when it opens.
    androidx.compose.runtime.LaunchedEffect(playbackController) {
        com.music.vivi.compose.ui.player.canvas.CanvasPrefetcher.run(playbackController, prefs)
    }
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val onNowPlaying = current == Routes.NOW_PLAYING || current == Routes.NOW_PLAYING_FULLSCREEN
    val onFullscreenPlayer = current == Routes.NOW_PLAYING_FULLSCREEN
    LaunchedEffect(Unit) { com.music.vivi.compose.ui.search.ContentRegion.apply(prefs) }
    var pinnedQueue by remember { mutableStateOf(false) }
    var fullscreenQueue by remember { mutableStateOf(false) }
    var prefsTick by remember { mutableIntStateOf(0) }
    DisposableEffect(prefs) {
        val listener = { prefsTick += 1 }
        prefs.addListener(listener)
        onDispose { prefs.removeListener(listener) }
    }
    @Suppress("UNUSED_VARIABLE")
    val observePrefs = prefsTick
    LaunchedEffect(onFullscreenPlayer) {
        if (onFullscreenPlayer) {
            pinnedQueue = false
            fullscreenQueue = false
        }
    }
    fun openPinnedQueue() {
        prefs.setString(PrefKeys.QueuePanelMode, QueuePanelMode.PINNED_RIGHT.name)
        fullscreenQueue = false
        pinnedQueue = true
    }
    fun openFullscreenQueue() {
        prefs.setString(PrefKeys.QueuePanelMode, QueuePanelMode.FULLSCREEN.name)
        pinnedQueue = false
        fullscreenQueue = true
    }

    // Sidebars: library (Your Library panel) + pinned queue, each dockable left or right.
    val sidebarCfg = SidebarConfig.resolve(prefs.reader())
    var queueDismissedOnHome by remember { mutableStateOf(false) }
    LaunchedEffect(sidebarCfg.queueOnHome) { queueDismissedOnHome = false }
    val libraryAllowed = Sidebars.libraryAllowedOn(current, sidebarCfg)
    val libraryState = if (libraryAllowed) sidebarCfg.libraryState else LibrarySidebarState.HIDDEN
    val queueAuto = Sidebars.queueAutoOpenOnHome(current, sidebarCfg, queueDismissedOnHome)
    val queueRailOn = (pinnedQueue || queueAuto) && !fullscreenQueue && !onFullscreenPlayer
    fun closePinnedQueue() {
        pinnedQueue = false
        if (queueAuto) queueDismissedOnHome = true
    }
    fun setLibraryState(state: LibrarySidebarState) = prefs.setString(SidebarKeys.LibraryState, state.name)
    fun toggleLibrary() {
        if (libraryAllowed) setLibraryState(Sidebars.toggled(sidebarCfg.libraryState, sidebarCfg.libraryDefaultState))
    }
    fun toggleQueueSidebar() {
        if (queueRailOn) closePinnedQueue() else { queueDismissedOnHome = false; openPinnedQueue() }
    }

    // Global keyboard shortcuts (AWT-level dispatcher) + the screen-owned effects they trigger.
    com.music.vivi.compose.keys.KeyboardShortcutHost(navController, windowState)
    com.music.vivi.compose.keys.ShortcutEventEffect { event ->
        when (event) {
            com.music.vivi.compose.keys.ShortcutEvent.ToggleQueue -> toggleQueueSidebar()
            com.music.vivi.compose.keys.ShortcutEvent.ToggleLibrarySidebar -> toggleLibrary()
            else -> Unit
        }
    }

    val glassState = remember { dev.chrisbanes.haze.HazeState() }
    val backdropScroll = remember { com.music.vivi.compose.ui.glass.BackdropScroll() }
    androidx.compose.runtime.LaunchedEffect(current) { backdropScroll.y = 0f }
    // Drag / touchpad scrolling reports through nested scroll; the mouse wheel reports from the shared wheel scroller.
    val backdropNested = remember(backdropScroll) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                backdropScroll.y = (backdropScroll.y - consumed.y).coerceAtLeast(0f)
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }
    val glassOn = com.music.vivi.compose.ui.glass.rememberGlassEnabled()
    val osFullscreen = windowState.placement == WindowPlacement.Fullscreen
    // Hide chrome for immersive NP route and whenever the window is OS-fullscreen.
    val hideTopBar = current == Routes.NOW_PLAYING_FULLSCREEN || osFullscreen
    val topBar: @Composable () -> Unit = {
            VisageTopBar(
                destinations = Routes.mainDestinations,
                currentRoute = current,
                onNavigate = { route -> navigateMain(navController, route) },
                onOpenSettingsRoute = { route ->
                    navController.navigate(route) {
                        launchSingleTop = true
                    }
                },
                windowState = windowState,
                onClose = onClose,
                extraActions = {
                    if (current == Routes.HOME && sidebarCfg.queueOnHome) {
                        SidebarToggleButton(
                            icon = Icons.Outlined.QueueMusic,
                            description = if (queueRailOn) "Hide queue sidebar" else "Show queue sidebar",
                            active = queueRailOn,
                            onClick = { toggleQueueSidebar() },
                        )
                    }
                    if (Sidebars.libraryAllowedOn(current, sidebarCfg)) {
                        SidebarToggleButton(
                            icon = Icons.Outlined.LibraryMusic,
                            description = if (libraryState == LibrarySidebarState.HIDDEN) "Show library (Ctrl+Shift+L)" else "Hide library (Ctrl+Shift+L)",
                            active = libraryState != LibrarySidebarState.HIDDEN,
                            onClick = { toggleLibrary() },
                        )
                    }
                },
            )
    }
    // With the blur setting on, the top bar floats over the page (so the page, and on Now Playing its shader
    // background, shows through the glass) and pages are inset by its height instead.
    val overlayTopBar = glassOn && !hideTopBar
    // Blur setting on and the full-width mini-player style: the bar spans the whole window (not just the inset,
    // rounded main pane) and the sidebars and pages end above it.
    val miniAtRoot = glassOn && !onNowPlaying && resolveMiniPlayerStyle(prefs) == MiniPlayerStyle.FULL_WIDTH_BAR
    var miniBarHeightPx by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val miniBarInset = if (miniAtRoot) with(androidx.compose.ui.platform.LocalDensity.current) { miniBarHeightPx.toDp() } else 0.dp
    com.music.vivi.compose.ui.glass.ProvideGlass(glassState, scroll = backdropScroll) {
    Box(
        Modifier.fillMaxSize().onPreviewKeyEvent { e ->
            if (e.type == KeyEventType.KeyDown && e.key == Key.L && e.isCtrlPressed && e.isShiftPressed && libraryAllowed) {
                toggleLibrary(); true
            } else false
        },
    ) {
      if (glassOn) {
          // One tinted background behind everything (bar, sidebars, pages): the glass surfaces blur it.
          Box(Modifier.fillMaxSize().glassSource(glassState)) {
              com.music.vivi.compose.ui.glass.PageBackground(nowPlayingStyle = onNowPlaying)
          }
      }
      Column(Modifier.fillMaxSize()) {
        if (!hideTopBar && !overlayTopBar) topBar()
        Box(
            Modifier
                .weight(1f)
                .fillMaxSize()
                .then(if (glassOn) Modifier else Modifier.background(MaterialTheme.colorScheme.background)),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val libVisible = libraryState != LibrarySidebarState.HIDDEN
                val libLeft = libVisible && sidebarCfg.librarySide == SidebarSide.LEFT
                val libRight = libVisible && sidebarCfg.librarySide == SidebarSide.RIGHT
                val queueLeft = queueRailOn && sidebarCfg.queueSide == SidebarSide.LEFT
                val queueRight = queueRailOn && sidebarCfg.queueSide == SidebarSide.RIGHT
                val libWidth = Sidebars.libraryWidthFor(
                    available = maxWidth.value.toInt(),
                    queueWidth = if (queueRailOn) SidebarConfig.QueueWidth else 0,
                    desired = sidebarCfg.libraryWidthDp,
                ).dp
                // Round + inset the main pane whenever a sidebar is open (NP and every other page).
                // Insets spring in and out together with the rails instead of jumping.
                val leftFrac by animateFloatAsState(if (libLeft || queueLeft) 1f else 0f, VisageMotion.rail(), label = "insetLeft")
                val rightFrac by animateFloatAsState(if (libRight || queueRight) 1f else 0f, VisageMotion.rail(), label = "insetRight")
                val inset = maxOf(leftFrac, rightFrac)
                Row(Modifier.fillMaxSize().then(if (overlayTopBar) Modifier.padding(top = com.music.vivi.compose.ui.glass.TopBarInset) else Modifier).padding(bottom = miniBarInset)) {
                    AnimatedLibrarySidebar(
                        state = if (sidebarCfg.librarySide == SidebarSide.LEFT) libraryState else LibrarySidebarState.HIDDEN,
                        side = SidebarSide.LEFT,
                        expandedWidth = libWidth,
                        navController = navController,
                        onStateChange = ::setLibraryState,
                    )
                    AnimatedQueuePinnedRail(
                        visible = queueLeft,
                        onDismiss = { closePinnedQueue() },
                        onExpandFullscreen = { openFullscreenQueue() },
                        fromStart = true,
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .then(
                                if (inset > 0.001f) {
                                    Modifier
                                        .padding(
                                            start = (8f * inset - 4f * leftFrac).dp,
                                            top = (8f * inset).dp,
                                            bottom = (8f * inset).dp,
                                            end = (8f * inset - 4f * rightFrac).dp,
                                        )
                                        .clip(RoundedCornerShape((20f * inset).dp))
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        val bandColor = MaterialTheme.colorScheme.background
                        val miniStyle = resolveMiniPlayerStyle(prefs)
                        Box(Modifier.weight(1f).fillMaxSize().trackMainPane()) {
                            VisageNavHost(
                                navController = navController,
                                startDestination = if (sessionManager.isLoggedIn()) Routes.HOME else Routes.LIBRARY,
                                onOpenPinnedQueue = { openPinnedQueue() },
                                onOpenFullscreenQueue = { openFullscreenQueue() },
                                queueOpen = queueRailOn || fullscreenQueue,
                                onCloseQueue = { closePinnedQueue(); fullscreenQueue = false },
                                modifier = Modifier.fillMaxSize().nestedScroll(backdropNested).glassSource(glassState),
                            )
                            if (!onNowPlaying && !miniAtRoot) {
                                if (!glassOn) Box(
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .height(120.dp)
                                        .background(
                                            Brush.verticalGradient(
                                                0f to Color.Transparent,
                                                0.45f to bandColor.copy(alpha = 0.35f),
                                                1f to bandColor.copy(alpha = 0.85f),
                                            ),
                                        ),
                                )
                                MiniPlayerBar(
                                    onExpand = {
                                        navController.navigate(Routes.NOW_PLAYING) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onOpenArtist = { navController.openArtistByName(it) },
                                    styleOverride = miniStyle,
                                    modifier = Modifier.align(
                                        if (miniStyle == MiniPlayerStyle.COMPACT_EDGE) {
                                            Alignment.BottomEnd
                                        } else {
                                            Alignment.BottomCenter
                                        },
                                    ),
                                )
                            }
                        }
                    }
                    AnimatedQueuePinnedRail(
                        visible = queueRight,
                        onDismiss = { closePinnedQueue() },
                        onExpandFullscreen = { openFullscreenQueue() },
                    )
                    AnimatedLibrarySidebar(
                        state = if (sidebarCfg.librarySide == SidebarSide.RIGHT) libraryState else LibrarySidebarState.HIDDEN,
                        side = SidebarSide.RIGHT,
                        expandedWidth = libWidth,
                        navController = navController,
                        onStateChange = ::setLibraryState,
                    )
                }
            }
            if (miniAtRoot) {
                MiniPlayerBar(
                    onExpand = {
                        navController.navigate(Routes.NOW_PLAYING) {
                            launchSingleTop = true
                        }
                    },
                    onOpenArtist = { navController.openArtistByName(it) },
                    styleOverride = MiniPlayerStyle.FULL_WIDTH_BAR,
                    modifier = Modifier.align(Alignment.BottomCenter).onSizeChanged { miniBarHeightPx = it.height },
                )
            }
            AnimatedQueueBottomTray(
                visible = fullscreenQueue,
                onDismiss = { fullscreenQueue = false },
                onPin = { openPinnedQueue() },
            )
        }
      }
      if (overlayTopBar) Box(Modifier.align(Alignment.TopCenter).fillMaxWidth()) { topBar() }
    }
    }
}


/**
 * Main tab / logo navigation. Tabs always land on their root page: nested detail
 * stacks are dropped instead of saved, so a tab click never "restores" a stale album
 * or playlist the user already left.
 */
fun navigateMain(navController: NavHostController, route: String) {
    val start = navController.graph.findStartDestination()
    if (navController.currentDestination?.route == route) {
        // Tapping the tab you're already on goes back to that section's home.
        if (route == Routes.LIBRARY) com.music.vivi.compose.ui.library.LibraryNavigator.reset()
        return
    }
    if (route == start.route) {
        navController.popBackStack(start.id, inclusive = false)
        return
    }
    navController.navigate(route) {
        popUpTo(start.id) { inclusive = false }
        launchSingleTop = true
    }
}

@Composable
fun VisageNavHost(
    navController: NavHostController,
    startDestination: String = Routes.HOME,
    modifier: Modifier = Modifier,
    onOpenPinnedQueue: (() -> Unit)? = null,
    onOpenFullscreenQueue: (() -> Unit)? = null,
    queueOpen: Boolean = false,
    onCloseQueue: (() -> Unit)? = null,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier.predictiveBackSwipe(navController),
        enterTransition = visageEnterTransition,
        exitTransition = visageExitTransition,
        popEnterTransition = visagePopEnterTransition,
        popExitTransition = visagePopExitTransition,
    ) {
        composable(Routes.HOME) { HomeScreen(navController) }
        composable(Routes.SEARCH) { SearchScreen(navController) }
        composable(Routes.LIBRARY) { LibraryScreen(navController) }
        composable(Routes.SETTINGS) { SettingsHubScreen(navController) }
        composable(Routes.SETTINGS_ACCOUNT) { AccountSettingsScreen(navController) }
        composable(Routes.SETTINGS_APPEARANCE) { AppearanceSettingsScreen(navController) }
        composable(Routes.SETTINGS_THEME) { ThemeSettingsScreen(navController) }
        composable(Routes.SETTINGS_PLAYER) { PlayerSettingsScreen(navController) }
        composable(Routes.SETTINGS_NOW_PLAYING) { NowPlayingSettingsScreen(navController) }
        composable(Routes.SETTINGS_LYRICS) { com.music.vivi.compose.ui.settings.LyricsSettingsScreen(navController) }
        composable(Routes.SETTINGS_SONG_SWAP) { com.music.vivi.compose.ui.settings.SongSwapSettingsScreen(navController) }
        composable(Routes.SETTINGS_FULLSCREEN_PLAYER) { com.music.vivi.compose.ui.settings.FullscreenPlayerSettingsScreen(navController) }
        composable(Routes.SETTINGS_DOWNLOADS) { DownloadsSettingsScreen(navController) }
        composable(Routes.SETTINGS_LOCAL_OFFLINE) { LocalOfflineSettingsScreen(navController) }
        composable(Routes.TAG_MANAGER) { com.music.vivi.compose.ui.tags.TagManagerScreen(navController) }
        composable(Routes.SETTINGS_KEYBINDS) { com.music.vivi.compose.ui.settings.KeybindsSettingsScreen(navController) }
        composable(Routes.SETTINGS_DIAGNOSTICS) { com.music.vivi.compose.ui.settings.DiagnosticsScreen(navController) }
        composable(Routes.SMOKE) { SmokePlaybackScreen() }
        composable(Routes.NOW_PLAYING) {
            NowPlayingScreen(
                navController = navController,
                onOpenPinnedQueue = onOpenPinnedQueue,
                onOpenFullscreenQueue = onOpenFullscreenQueue,
                queueOpen = queueOpen,
                onCloseQueue = onCloseQueue,
            )
        }
        composable(Routes.NOW_PLAYING_FULLSCREEN) {
            NowPlayingFullscreenScreen(navController = navController)
        }

        composable(
            route = Routes.ALBUM,
            arguments = listOf(navArgument("albumId") { type = NavType.StringType }),
        ) { entry ->
            val albumId = entry.arguments?.read { getStringOrNull("albumId") }
            if (albumId.isNullOrBlank()) PlaceholderScreen("Missing album id")
            else {
                OnlineAlbumScreen(albumId = albumId, navController = navController)
            }
        }
        composable(
            route = Routes.ARTIST,
            arguments = listOf(navArgument("artistId") { type = NavType.StringType }),
        ) { entry ->
            val artistId = entry.arguments?.read { getStringOrNull("artistId") }
            if (artistId.isNullOrBlank()) PlaceholderScreen("Missing artist id")
            else {
                ArtistScreen(artistId = artistId, navController = navController)
            }
        }
        composable(
            route = Routes.ARTIST_INFO,
            arguments = listOf(navArgument("name") { type = NavType.StringType }),
        ) { entry ->
            val name = entry.arguments?.read { getStringOrNull("name") }
            if (name.isNullOrBlank()) PlaceholderScreen("Missing artist")
            else com.music.vivi.compose.ui.artist.ArtistInfoScreen(name = name, navController = navController)
        }
        composable(
            route = Routes.ONLINE_PLAYLIST,
            arguments = listOf(
                navArgument("playlistId") { type = NavType.StringType },
            ),
        ) { entry ->
            val playlistId = entry.arguments?.read { getStringOrNull("playlistId") }
            if (playlistId.isNullOrBlank()) {
                PlaceholderScreen("Missing playlist id")
            } else {
                OnlinePlaylistScreen(playlistId = playlistId, navController = navController)
            }
        }
        composable(
            route = Routes.LOCAL_PLAYLIST,
            arguments = listOf(navArgument("playlistId") { type = NavType.StringType }),
        ) { entry ->
            val playlistId = entry.arguments?.read { getStringOrNull("playlistId") }
            if (playlistId.isNullOrBlank()) PlaceholderScreen("Missing playlist id")
            else com.music.vivi.compose.ui.playlist.LocalPlaylistScreen(playlistId, navController)
        }
        composable(Routes.SETTINGS_SIDEBARS) { com.music.vivi.compose.ui.settings.SidebarsSettingsScreen(navController) }
        composable(
            route = Routes.BROWSE,
            arguments = listOf(
                navArgument("browseId") { type = NavType.StringType },
                navArgument("params") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            val browseId = entry.arguments?.read { getStringOrNull("browseId") }
            if (browseId.isNullOrBlank()) {
                PlaceholderScreen("Missing browse id")
            } else {
                com.music.vivi.compose.ui.browse.BrowseScreen(
                    browseId = browseId,
                    params = entry.arguments?.read { getStringOrNull("params") },
                    navController = navController,
                )
            }
        }
        
        composable(Routes.CHARTS) { com.music.vivi.compose.ui.charts.ChartsScreen(navController = navController) }
        composable(Routes.MOOD_AND_GENRES) { com.music.vivi.compose.ui.moods.MoodAndGenresScreen(navController = navController) }
        composable(Routes.HISTORY) { com.music.vivi.compose.ui.history.HistoryScreen(navController = navController) }
        composable(Routes.NEW_RELEASE) { com.music.vivi.compose.ui.newrelease.NewReleaseScreen(navController = navController) }
        
        composable(
            route = Routes.ARTIST_ITEMS,
            arguments = listOf(
                navArgument("artistId") { type = NavType.StringType },
                navArgument("browseId") { type = NavType.StringType },
                navArgument("params") { type = NavType.StringType; nullable = true; defaultValue = null },
            )
        ) { entry ->
            val browseId = entry.arguments?.read { getStringOrNull("browseId") }
            if (browseId.isNullOrBlank()) {
                PlaceholderScreen("Missing browse id")
            } else {
                com.music.vivi.compose.ui.artist.ArtistItemsScreen(
                    browseId = browseId,
                    params = entry.arguments?.read { getStringOrNull("params") },
                    navController = navController
                )
            }
        }
    }
}
