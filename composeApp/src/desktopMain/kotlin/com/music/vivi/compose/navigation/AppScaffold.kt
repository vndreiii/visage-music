package com.music.vivi.compose.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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
import com.music.vivi.compose.ui.ViviTopBar
import com.music.vivi.compose.ui.resolveMiniPlayerStyle
import com.music.vivi.compose.ui.album.OnlineAlbumScreen
import com.music.vivi.compose.ui.artist.ArtistScreen
import com.music.vivi.compose.ui.home.HomeScreen
import com.music.vivi.compose.ui.library.LibraryScreen
import com.music.vivi.compose.ui.player.NowPlayingFullscreenScreen
import com.music.vivi.compose.ui.player.NowPlayingScreen
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

    Column(Modifier.fillMaxSize()) {
        val osFullscreen = windowState.placement == WindowPlacement.Fullscreen
        // Hide chrome for immersive NP route and whenever the window is OS-fullscreen.
        val hideTopBar =
            current == Routes.NOW_PLAYING_FULLSCREEN || osFullscreen
        if (!hideTopBar) {
            ViviTopBar(
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
            )
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Row(Modifier.fillMaxSize()) {
                // Round + inset the main pane whenever the rail is open (NP and every other page).
                val besidePinnedQueue = pinnedQueue && !fullscreenQueue
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .then(
                            if (besidePinnedQueue) {
                                Modifier
                                    .padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)
                                    .clip(RoundedCornerShape(20.dp))
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    val bandColor = MaterialTheme.colorScheme.background
                    val miniStyle = resolveMiniPlayerStyle(prefs)
                    Box(Modifier.weight(1f).fillMaxSize()) {
                        ViviNavHost(
                            navController = navController,
                            startDestination = if (sessionManager.isLoggedIn()) Routes.HOME else Routes.LIBRARY,
                            onOpenPinnedQueue = { openPinnedQueue() },
                            onOpenFullscreenQueue = { openFullscreenQueue() },
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (!onNowPlaying) {
                            Box(
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
                    visible = pinnedQueue && !fullscreenQueue,
                    onDismiss = { pinnedQueue = false },
                    onExpandFullscreen = { openFullscreenQueue() },
                )
            }
            AnimatedQueueBottomTray(
                visible = fullscreenQueue,
                onDismiss = { fullscreenQueue = false },
                onPin = { openPinnedQueue() },
            )
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
fun ViviNavHost(
    navController: NavHostController,
    startDestination: String = Routes.HOME,
    modifier: Modifier = Modifier,
    onOpenPinnedQueue: (() -> Unit)? = null,
    onOpenFullscreenQueue: (() -> Unit)? = null,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = viviEnterTransition,
        exitTransition = viviExitTransition,
        popEnterTransition = viviPopEnterTransition,
        popExitTransition = viviPopExitTransition,
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
        composable(Routes.SETTINGS_DOWNLOADS) { DownloadsSettingsScreen(navController) }
        composable(Routes.SETTINGS_LOCAL_OFFLINE) { LocalOfflineSettingsScreen(navController) }
        composable(Routes.SETTINGS_DIAGNOSTICS) { com.music.vivi.compose.ui.settings.DiagnosticsScreen(navController) }
        composable(Routes.SMOKE) { SmokePlaybackScreen() }
        composable(Routes.NOW_PLAYING) {
            NowPlayingScreen(
                navController = navController,
                onOpenPinnedQueue = onOpenPinnedQueue,
                onOpenFullscreenQueue = onOpenFullscreenQueue,
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
