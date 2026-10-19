package com.music.vivi.compose.keys

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.navigation.NavHostController
import com.music.vivi.compose.LocalPlaybackController
import com.music.vivi.compose.WindowFullscreen
import com.music.vivi.compose.navigation.Routes
import com.music.vivi.compose.navigation.navigateMain
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.PrefKeys
import com.music.vivi.player.PlaybackController
import com.music.vivi.player.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

fun DesktopPrefs.asKeyValueStore(): KeyValueStore = object : KeyValueStore {
    override fun get(key: String): String? = getString(key, "").ifEmpty { null }
    override fun set(key: String, value: String) = setString(key, value)
}

fun DesktopPrefs.keymap(): Keymap = Keymap(asKeyValueStore())

/** Shortcut effects that only a screen can carry out (it owns the state). Screens collect [events]. */
enum class ShortcutEvent { ToggleLyrics, ToggleQueue, ToggleLibrarySidebar, ShowTrackActions, FocusSearch }

object ShortcutBus {
    private val flow = MutableSharedFlow<ShortcutEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<ShortcutEvent> = flow.asSharedFlow()
    fun emit(e: ShortcutEvent) { flow.tryEmit(e) }
}

/** Collects [ShortcutBus] events while in composition. */
@Composable
fun ShortcutEventEffect(onEvent: (ShortcutEvent) -> Unit) {
    val latest by rememberUpdatedState(onEvent)
    LaunchedEffect(Unit) { ShortcutBus.events.collect { latest(it) } }
}

/** Overlay state: the cheat-sheet and the transient "Volume 40%" style readout. */
object ShortcutUi {
    var cheatSheetVisible by mutableStateOf(false)
    var hudText by mutableStateOf<String?>(null)
    var hudSerial by mutableIntStateOf(0)
    /** Navigates to Settings → Keyboard shortcuts; set by [KeyboardShortcutHost]. */
    var openSettings: (() -> Unit)? = null

    fun hud(text: String) { hudText = text; hudSerial++ }
}

/** Lets the settings page grab the next key press for rebinding (null sink = normal dispatch). */
object ShortcutCapture {
    @Volatile var engine: ShortcutEngine? = null
    fun begin(sink: (KeyInput) -> Unit) { engine?.captureSink = sink }
    fun end() { engine?.captureSink = null }
}

/**
 * Window-level keyboard shortcuts. Installs an AWT dispatcher (works whichever node has focus, even
 * a focused button) and runs the matching [ShortcutAction]. Emits no layout.
 */
@Composable
fun KeyboardShortcutHost(navController: NavHostController, windowState: WindowState) {
    val prefs = koinInject<DesktopPrefs>()
    val controller = LocalPlaybackController.current
    val scope = rememberCoroutineScope()
    val keymap = remember(prefs) { prefs.keymap() }
    val runner = remember(prefs, controller, navController, windowState) {
        ShortcutRunner(prefs, controller, navController, windowState, scope)
    }
    DisposableEffect(keymap, runner) {
        val engine = ShortcutEngine(keymap, ShortcutDispatcher::isTextInputFocused, runner::perform)
        ShortcutCapture.engine = engine
        ShortcutDispatcher.install(engine)
        ShortcutUi.openSettings = { navController.navigate(Routes.SETTINGS_KEYBINDS) { launchSingleTop = true } }
        onDispose {
            ShortcutUi.openSettings = null
            ShortcutDispatcher.uninstall()
            if (ShortcutCapture.engine === engine) ShortcutCapture.engine = null
        }
    }
}

internal class ShortcutRunner(
    private val prefs: DesktopPrefs,
    private val controller: PlaybackController,
    private val nav: NavHostController,
    private val window: WindowState,
    private val scope: CoroutineScope,
) {
    private var volumeBeforeMute: Float? = null

    private val route get() = nav.currentDestination?.route
    private val hasTrack get() = controller.nowPlaying.value != null

    fun perform(a: ShortcutAction): Boolean {
        a.seekTenths?.let { return seekFraction(it / 10f) }
        return when (a) {
            ShortcutAction.PLAY_PAUSE -> {
                val st = controller.state.value
                if (st == PlaybackState.Playing || st == PlaybackState.Loading) controller.pause() else controller.play()
                true
            }
            ShortcutAction.NEXT -> { controller.next(); hud("Next track"); true }
            ShortcutAction.PREVIOUS -> { controller.previous(); hud("Previous track"); true }
            ShortcutAction.SEEK_FORWARD -> seekBy(5_000)
            ShortcutAction.SEEK_BACKWARD -> seekBy(-5_000)
            ShortcutAction.SEEK_FORWARD_LONG -> seekBy(15_000)
            ShortcutAction.SEEK_BACKWARD_LONG -> seekBy(-15_000)
            ShortcutAction.VOLUME_UP -> volumeBy(0.05f)
            ShortcutAction.VOLUME_DOWN -> volumeBy(-0.05f)
            ShortcutAction.MUTE -> toggleMute()
            ShortcutAction.SHUFFLE -> {
                val on = !controller.shuffleEnabled.value
                controller.toggleShuffle()
                hud(if (on) "Shuffle on" else "Shuffle off")
                true
            }
            ShortcutAction.REPEAT -> {
                controller.cycleRepeatMode()
                scope.launch { delay(80); hud("Repeat: ${controller.repeatMode.value.name.lowercase()}") }
                true
            }
            ShortcutAction.LIKE -> {
                if (!hasTrack) return false
                val liking = !controller.isFavorite.value
                scope.launch { controller.toggleFavorite() }
                hud(if (liking) "Added to favorites" else "Removed from favorites")
                true
            }
            ShortcutAction.START_RADIO -> {
                val id = controller.nowPlaying.value?.videoId ?: return false
                scope.launch { controller.startRadio(id) }
                hud("Starting radio")
                true
            }
            ShortcutAction.TOGGLE_LYRICS -> onPlayer(ShortcutEvent.ToggleLyrics)
            ShortcutAction.TRACK_ACTIONS -> onPlayer(ShortcutEvent.ShowTrackActions)
            ShortcutAction.TOGGLE_QUEUE -> { ShortcutBus.emit(ShortcutEvent.ToggleQueue); true }
            ShortcutAction.TOGGLE_LIBRARY_SIDEBAR -> { ShortcutBus.emit(ShortcutEvent.ToggleLibrarySidebar); true }
            ShortcutAction.TOGGLE_FULLSCREEN -> toggleFullscreenPlayer()
            ShortcutAction.OPEN_PLAYER -> {
                if (!hasTrack) return false
                if (route != Routes.NOW_PLAYING && route != Routes.NOW_PLAYING_FULLSCREEN) {
                    nav.navigate(Routes.NOW_PLAYING) { launchSingleTop = true }
                }
                true
            }
            ShortcutAction.LEAVE_FULLSCREEN -> leave()
            ShortcutAction.BACK -> {
                if (nav.previousBackStackEntry == null) false else { nav.popBackStack(); true }
            }
            ShortcutAction.FOCUS_SEARCH -> {
                if (route != Routes.SEARCH) navigateMain(nav, Routes.SEARCH)
                scope.launch { delay(120); ShortcutBus.emit(ShortcutEvent.FocusSearch) }
                true
            }
            ShortcutAction.GO_HOME -> { navigateMain(nav, Routes.HOME); true }
            ShortcutAction.GO_SEARCH -> { navigateMain(nav, Routes.SEARCH); true }
            ShortcutAction.GO_LIBRARY -> { navigateMain(nav, Routes.LIBRARY); true }
            ShortcutAction.GO_SETTINGS -> { navigateMain(nav, Routes.SETTINGS); true }
            ShortcutAction.SHOW_SHORTCUTS -> { ShortcutUi.cheatSheetVisible = !ShortcutUi.cheatSheetVisible; true }
            else -> false
        }
    }

    private fun hud(text: String) = ShortcutUi.hud(text)

    private fun seekBy(deltaMs: Long): Boolean {
        if (!hasTrack) return false
        val dur = controller.durationMs.value
        val target = (controller.positionMs.value + deltaMs).coerceAtLeast(0L).let { if (dur > 0) it.coerceAtMost(dur) else it }
        controller.seekTo(target)
        hud(if (deltaMs > 0) "+${deltaMs / 1000} s" else "${deltaMs / 1000} s")
        return true
    }

    private fun seekFraction(f: Float): Boolean {
        val dur = controller.durationMs.value
        if (!hasTrack || dur <= 0) return false
        controller.seekTo((dur * f).toLong())
        hud("${(f * 100).toInt()}%")
        return true
    }

    private fun volumeBy(delta: Float): Boolean {
        val v = (controller.volume.value + delta).coerceIn(0f, 1f)
        volumeBeforeMute = null
        setVolume(v)
        return true
    }

    private fun setVolume(v: Float) {
        controller.setVolume(v)
        prefs.setFloat(PrefKeys.PlaybackVolume, v)
        hud(if (v <= 0f) "Muted" else "Volume ${(v * 100).toInt()}%")
    }

    private fun toggleMute(): Boolean {
        val current = controller.volume.value
        val restore = volumeBeforeMute
        if (current > 0f) {
            volumeBeforeMute = current
            setVolume(0f)
        } else {
            volumeBeforeMute = null
            setVolume(restore ?: 0.5f)
        }
        return true
    }

    /** Lyrics / track actions live in the player screens: bring the player up first when elsewhere. */
    private fun onPlayer(event: ShortcutEvent): Boolean {
        if (!hasTrack) return false
        if (route == Routes.NOW_PLAYING || route == Routes.NOW_PLAYING_FULLSCREEN) {
            ShortcutBus.emit(event)
        } else {
            nav.navigate(Routes.NOW_PLAYING) { launchSingleTop = true }
            scope.launch { delay(450); ShortcutBus.emit(event) }
        }
        return true
    }

    private fun toggleFullscreenPlayer(): Boolean {
        if (route == Routes.NOW_PLAYING_FULLSCREEN) {
            WindowFullscreen.exit(window)
            nav.popBackStack()
            return true
        }
        if (!hasTrack) return false
        WindowFullscreen.enter(window)
        nav.navigate(Routes.NOW_PLAYING_FULLSCREEN) { launchSingleTop = true }
        return true
    }

    private fun leave(): Boolean {
        if (ShortcutUi.cheatSheetVisible) { ShortcutUi.cheatSheetVisible = false; return true }
        if (route == Routes.NOW_PLAYING_FULLSCREEN) {
            WindowFullscreen.exit(window)
            nav.popBackStack()
            return true
        }
        if (window.placement == WindowPlacement.Fullscreen) { WindowFullscreen.exit(window); return true }
        return false
    }
}
