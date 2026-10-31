package com.music.vivi.compose.navigation

import androidx.compose.runtime.collectAsState
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
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
import kotlinx.coroutines.launch
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
    // Dev aid: VISAGE_DEV_NAVFILE=<file>; writing a route into it navigates there (responsive-layout screenshots).
    com.music.vivi.nativelib.AppEnv.get("DEV_NAVFILE")?.let { path ->
        LaunchedEffect(path) {
            var last = ""
            while (true) {
                val route = runCatching { java.io.File(path).readText().trim() }.getOrDefault("")
                if (route.isNotEmpty() && route != last) { last = route; runCatching { navController.navigate(route) } }
                kotlinx.coroutines.delay(500)
            }
        }
    }
    val current = backStack?.destination?.route
    val onNowPlaying = current == Routes.NOW_PLAYING || current == Routes.NOW_PLAYING_FULLSCREEN
    // Window class decides how sidebars dock: beside the page on Desktop+, as overlays/trays below it.
    val windowLayout = com.music.vivi.compose.ui.responsive.windowLayout()
    val compact = windowLayout.isCompactWidth
    var libraryOverlayOpen by remember { mutableStateOf(false) }
    LaunchedEffect(current, compact) { libraryOverlayOpen = false }
    LaunchedEffect(Unit) { com.music.vivi.compose.ui.search.ContentRegion.apply(prefs) }
    var pinnedQueue by remember { mutableStateOf(com.music.vivi.nativelib.AppEnv.flag("DEV_QUEUE")) } // DEV_QUEUE: dev aid, start with the queue open
    var fullscreenQueue by remember { mutableStateOf(false) }
    var prefsTick by remember { mutableIntStateOf(0) }
    DisposableEffect(prefs) {
        val listener = { prefsTick += 1 }
        prefs.addListener(listener)
        onDispose { prefs.removeListener(listener) }
    }
    @Suppress("UNUSED_VARIABLE")
    val observePrefs = prefsTick
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
    val libraryStateRaw = if (libraryAllowed) sidebarCfg.libraryState else LibrarySidebarState.HIDDEN
    // On compact windows the in-row sidebar is always hidden; the library shows as an overlay instead.
    val libraryState = if (compact) LibrarySidebarState.HIDDEN else libraryStateRaw
    val queueAuto = Sidebars.queueAutoOpenOnHome(current, sidebarCfg, queueDismissedOnHome)
    // Compact windows have no room for a 360dp rail: an explicitly opened "pinned" queue is the bottom tray instead.
    val queueRailOn = (pinnedQueue || queueAuto) && !fullscreenQueue && !compact
    val queueTrayOn = fullscreenQueue || (pinnedQueue && compact)
    // The rail that opened itself on Home stays when you navigate (Search, Library, ...): it only goes when closed.
    // (Never on compact windows: the auto-open queue must not pop a modal tray.)
    LaunchedEffect(queueAuto, compact) { if (queueAuto && !compact) pinnedQueue = true }
    fun closePinnedQueue() {
        pinnedQueue = false
        if (sidebarCfg.queueOnHome) queueDismissedOnHome = true
    }
    fun setLibraryState(state: LibrarySidebarState) = prefs.setString(SidebarKeys.LibraryState, state.name)
    fun toggleLibrary() {
        if (libraryAllowed) {
            if (compact) libraryOverlayOpen = !libraryOverlayOpen
            else setLibraryState(Sidebars.toggled(sidebarCfg.libraryState, sidebarCfg.libraryDefaultState))
        }
    }
    fun toggleQueueSidebar() {
        if (queueRailOn || queueTrayOn) { closePinnedQueue(); fullscreenQueue = false } else { queueDismissedOnHome = false; openPinnedQueue() }
    }

    // Onboarding tour: drives the real UI (navigation, queue and library sidebars) step by step.
    LaunchedEffect(Unit) {
        com.music.vivi.compose.ui.onboarding.TourBus.commands.collect { cmd ->
            when (cmd) {
                is com.music.vivi.compose.ui.onboarding.TourCommand.Navigate -> navigateMain(navController, cmd.route)
                is com.music.vivi.compose.ui.onboarding.TourCommand.Queue -> {
                    // Not through openPinnedQueue(): the tour must not overwrite the saved queue mode.
                    if (cmd.open) { fullscreenQueue = false; queueDismissedOnHome = false; pinnedQueue = true }
                    else { pinnedQueue = false; queueDismissedOnHome = true }
                }
                is com.music.vivi.compose.ui.onboarding.TourCommand.Library -> setLibraryState(cmd.state)
            }
        }
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
    // Sourced from the page background only, for cards inside the page content (see LocalPageHazeState).
    val pageGlassState = remember { dev.chrisbanes.haze.HazeState() }
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
                            description = if (queueRailOn || queueTrayOn) "Hide queue sidebar" else "Show queue sidebar",
                            active = queueRailOn || queueTrayOn,
                            onClick = { toggleQueueSidebar() },
                        )
                    }
                    if (Sidebars.libraryAllowedOn(current, sidebarCfg)) {
                        SidebarToggleButton(
                            icon = Icons.Outlined.LibraryMusic,
                            description = if (libraryState == LibrarySidebarState.HIDDEN && !libraryOverlayOpen) "Show library (Ctrl+Shift+L)" else "Hide library (Ctrl+Shift+L)",
                            active = libraryState != LibrarySidebarState.HIDDEN || libraryOverlayOpen,
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
    val bandColor = MaterialTheme.colorScheme.background
    val miniStyle = resolveMiniPlayerStyle(prefs)
    val nowPlayingForSplit by playbackController.nowPlaying.collectAsState()
    androidx.compose.runtime.SideEffect {
        com.music.vivi.compose.ui.component.MainPane.splitAroundPill =
            nowPlayingForSplit != null && !onNowPlaying && !miniAtRoot &&
                miniStyle == MiniPlayerStyle.FLOATING_PILL
    }
    var miniBarHeightPx by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val miniBarInset = if (miniAtRoot) with(androidx.compose.ui.platform.LocalDensity.current) { miniBarHeightPx.toDp() } else 0.dp
    com.music.vivi.compose.ui.glass.ProvideGlass(glassState, scroll = backdropScroll, pageState = pageGlassState) {
    Box(
        Modifier.fillMaxSize().onPreviewKeyEvent { e ->
            if (e.type == KeyEventType.KeyDown && e.key == Key.L && e.isCtrlPressed && e.isShiftPressed && libraryAllowed) {
                toggleLibrary(); true
            } else false
        },
    ) {
      if (glassOn) {
          // One tinted background behind everything (bar, sidebars, pages): the glass surfaces blur it.
          Box(Modifier.fillMaxSize().glassSource(glassState).glassSource(pageGlassState)) {
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
            // Artist names in any item subtitle (playlists, albums, queue, lists) open the artist page. Navigate
            // after the text's own press gesture has finished, not from inside it.
            val linkScope = androidx.compose.runtime.rememberCoroutineScope()
            // Remembered so a settings write (which recomposes this scope) does not hand every subtitle a new lambda.
            val artistClick = androidx.compose.runtime.remember(navController, linkScope) {
                { artistId: String ->
                    linkScope.launch { kotlinx.coroutines.yield(); runCatching { navController.navigate(Routes.artist(artistId)) } }
                    Unit
                }
            }
            val artistNameClick = androidx.compose.runtime.remember(navController, linkScope) {
                { name: String ->
                    linkScope.launch { kotlinx.coroutines.yield(); runCatching { navController.navigate(Routes.searchResult(name)) } }
                    Unit
                }
            }
            val navigateRoute = androidx.compose.runtime.remember(navController, linkScope) {
                { route: String ->
                    linkScope.launch { kotlinx.coroutines.yield(); runCatching { navController.navigate(route) } }
                    Unit
                }
            }
            // On the fullscreen player the pinned queue floats over the page (its blurred backdrop absorbs it)
            // instead of narrowing it, so the big cover never reflows.
            val queueOverlay = current == Routes.NOW_PLAYING_FULLSCREEN
            androidx.compose.runtime.CompositionLocalProvider(
                com.music.vivi.compose.ui.component.LocalArtistClick provides artistClick,
                com.music.vivi.compose.ui.component.LocalArtistNameClick provides artistNameClick,
                com.music.vivi.compose.ui.component.LocalNavigate provides navigateRoute,
            ) {
            SidebarShell(
                cfg = sidebarCfg,
                libraryState = libraryState,
                queueLeft = queueRailOn && sidebarCfg.queueSide == SidebarSide.LEFT && !queueOverlay,
                queueRight = queueRailOn && sidebarCfg.queueSide == SidebarSide.RIGHT && !queueOverlay,
                queueWidthForLibrary = if (queueRailOn) SidebarConfig.QueueWidth else 0,
                rowModifier = (if (overlayTopBar) Modifier.padding(top = com.music.vivi.compose.ui.glass.TopBarInset) else Modifier)
                    .padding(bottom = miniBarInset),
                librarySidebar = { side, state, width ->
                    AnimatedLibrarySidebar(
                        state = state,
                        side = side,
                        expandedWidth = width,
                        navController = navController,
                        onStateChange = ::setLibraryState,
                    )
                },
                queueRail = { visible, fromStart ->
                    AnimatedQueuePinnedRail(
                        visible = visible,
                        onDismiss = { closePinnedQueue() },
                        onExpandFullscreen = { openFullscreenQueue() },
                        fromStart = fromStart,
                    )
                },
                overlay = {
                    if (queueOverlay) {
                        val overlayLeft = sidebarCfg.queueSide == SidebarSide.LEFT
                        AnimatedQueuePinnedRail(
                            visible = queueRailOn,
                            onDismiss = { closePinnedQueue() },
                            onExpandFullscreen = { openFullscreenQueue() },
                            modifier = Modifier
                                .align(if (overlayLeft) Alignment.CenterStart else Alignment.CenterEnd)
                                .fillMaxHeight()
                                .padding(8.dp),
                            fromStart = overlayLeft,
                        )
                    }
                    if (compact && libraryAllowed) {
                        // Compact windows: the library is a modal overlay on its configured side, over a scrim.
                        androidx.compose.animation.AnimatedVisibility(
                            visible = libraryOverlayOpen,
                            enter = androidx.compose.animation.fadeIn(),
                            exit = androidx.compose.animation.fadeOut(),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Box(
                                Modifier.fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.4f))
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    ) { libraryOverlayOpen = false },
                            )
                        }
                        val fromRight = sidebarCfg.librarySide == SidebarSide.RIGHT
                        val overlayLibW = minOf(sidebarCfg.libraryWidthDp.dp, (windowLayout.width.value * 0.88f).dp)
                        Box(
                            Modifier
                                .align(if (fromRight) Alignment.CenterEnd else Alignment.CenterStart)
                                .fillMaxHeight()
                                .then(if (overlayTopBar) Modifier.padding(top = com.music.vivi.compose.ui.glass.TopBarInset) else Modifier),
                        ) {
                            AnimatedLibrarySidebar(
                                state = if (libraryOverlayOpen) LibrarySidebarState.EXPANDED else LibrarySidebarState.HIDDEN,
                                side = sidebarCfg.librarySide,
                                expandedWidth = overlayLibW,
                                navController = navController,
                                onStateChange = { st -> if (st == LibrarySidebarState.HIDDEN) libraryOverlayOpen = false },
                            )
                        }
                    }
                },
            ) {
                Box(Modifier.weight(1f).fillMaxSize().trackMainPane()) {
                    VisageNavHost(
                        navController = navController,
                        startDestination = com.music.vivi.nativelib.AppEnv.get("DEV_ROUTE") ?: if (sessionManager.isLoggedIn()) Routes.HOME else Routes.LIBRARY,
                        onOpenPinnedQueue = { openPinnedQueue() },
                        onOpenFullscreenQueue = { openFullscreenQueue() },
                        queueOpen = queueRailOn || queueTrayOn,
                        onCloseQueue = { closePinnedQueue(); fullscreenQueue = false },
                        modifier = Modifier.fillMaxSize().nestedScroll(backdropNested).glassSource(glassState),
                    )
                }
            }
            }
            if (!onNowPlaying && !miniAtRoot) {
                // At window level (not inside the inset, rounded pane) so the pill sits flush on the bottom edge,
                // but laid out over the main pane's horizontal bounds.
                Box(
                    Modifier.fillMaxSize().layout { m, c ->
                        val r = com.music.vivi.compose.ui.component.MainPane.bounds
                        val w = r?.width?.roundToInt()?.coerceIn(0, c.maxWidth) ?: c.maxWidth
                        val p = m.measure(androidx.compose.ui.unit.Constraints.fixed(w, c.maxHeight))
                        layout(c.maxWidth, c.maxHeight) { p.place(r?.left?.roundToInt() ?: 0, 0) }
                    },
                ) {
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
                visible = queueTrayOn,
                onDismiss = { fullscreenQueue = false; closePinnedQueue() },
                onPin = { openPinnedQueue() },
            )
        }
      }
      if (overlayTopBar) Box(Modifier.align(Alignment.TopCenter).fillMaxWidth()) { topBar() }
      // Tag Manager work (auto-tag / writing) goes on in the background; this takes you back to it.
      if (current != Routes.TAG_MANAGER) {
          com.music.vivi.compose.ui.tags.TagWorkFab(
              onOpen = { navController.navigate(Routes.TAG_MANAGER) { launchSingleTop = true } },
              modifier = Modifier.align(Alignment.BottomStart).padding(start = 32.dp, bottom = 32.dp + miniBarInset),
          )
      }
      // A queue row dragged out of the queue hangs from the pointer above everything, to be dropped on a playlist.
      com.music.vivi.compose.ui.dnd.TrackDragOverlay()
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
            NowPlayingFullscreenScreen(
                navController = navController,
                onOpenPinnedQueue = onOpenPinnedQueue,
                onOpenFullscreenQueue = onOpenFullscreenQueue,
                queueOpen = queueOpen,
                onCloseQueue = onCloseQueue,
            )
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
