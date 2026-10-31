package com.music.vivi.compose.ui.popup

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.music.vivi.compose.LocalPlaybackController
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.theme.VisageTheme
import com.music.vivi.compose.theme.rememberArtworkPalette
import com.music.vivi.nativelib.HostOs
import com.music.vivi.player.PlaybackController
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Toolkit

/** Open / close state of the popup. The window itself is composed by [PopupPlayerHost] in the app's `application {}`. */
object PopupPlayer {
    /** VIVI_POPUP_AT_START=1 opens it at launch (developer aid for window-manager testing). */
    var visible by mutableStateOf(com.music.vivi.nativelib.AppEnv.flag("POPUP_AT_START"))
        private set

    fun open() { visible = true }
    fun close() { visible = false }
    fun toggle() { visible = !visible }
}

internal object PopupPrefKeys {
    const val Bounds = "popupPlayerBounds"
    const val Pinned = "popupPlayerPinned"
    const val SkipTaskbar = "popupPlayerSkipTaskbar"
}

internal const val POPUP_TITLE = "VIsage Mini Player"

/** The X11 / XWayland class (Hyprland `class:`) the popup uses, so window rules can target it alone. */
internal const val POPUP_WM_CLASS = "visage-popup"

/**
 * Hosts the popup [Window] while [PopupPlayer.visible]. Call once inside `application {}`; closing the popup only
 * hides it, and the main window's exit takes it down with the application.
 */
@Composable
fun PopupPlayerHost(controller: PlaybackController, prefs: DesktopPrefs, icon: Painter?) {
    if (!PopupPlayer.visible) return
    // XToolkit stamps WM_CLASS from one toolkit-wide field when the native window is created: swap it for the
    // popup's window only. (No-op under other toolkits.)
    remember { PopupWmClass.beginPopup(); true }
    LaunchedEffect(Unit) { delay(800); PopupWmClass.endPopup() }

    val display = remember { PopupDisplayDetect.detect(System.getProperty("os.name"), System::getenv) }
    val saved = remember { PopupBounds.decode(prefs.getString(PopupPrefKeys.Bounds, "")) }
    val position = remember {
        val screens = runCatching {
            GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map {
                it.defaultConfiguration.bounds.let { b -> ScreenRect(b.x, b.y, b.width, b.height) }
            }
        }.getOrDefault(emptyList())
        if (PopupDisplayDetect.canPosition(display)) PopupPlacement.restore(saved, screens) else null
    }
    val state = rememberWindowState(
        size = DpSize(saved.width.dp, saved.height.dp),
        position = position?.let { WindowPosition(it.first.dp, it.second.dp) } ?: WindowPosition.PlatformDefault,
    )
    var pinned by remember { mutableStateOf(prefs.getBoolean(PopupPrefKeys.Pinned, true)) }
    val skipTaskbar = remember { prefs.getBoolean(PopupPrefKeys.SkipTaskbar, false) }

    Window(
        onCloseRequest = PopupPlayer::close,
        state = state,
        title = POPUP_TITLE,
        icon = icon,
        undecorated = true,
        // Windows has no compositor rounding for undecorated windows; same trick as the main window.
        transparent = true,
        resizable = true,
        alwaysOnTop = pinned,
    ) {
        PopupWindowEffects(state, prefs, skipTaskbar)
        CompositionLocalProvider(LocalPlaybackController provides controller) {
            val nowPlaying by controller.nowPlaying.collectAsState()
            VisageTheme(prefs = prefs, artworkPalette = rememberArtworkPalette(nowPlaying?.artworkUrl)) {
                val shape = RoundedCornerShape(16.dp)
                Surface(
                    Modifier.fillMaxSize().clip(shape),
                    shape = shape,
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize()) {
                            PopupHeader(
                                pinned = pinned,
                                onTogglePin = {
                                    pinned = !pinned
                                    prefs.setBoolean(PopupPrefKeys.Pinned, pinned)
                                },
                                onClose = PopupPlayer::close,
                            )
                            PopupPlayerContent(Modifier.weight(1f))
                        }
                        ResizeGrip(Modifier.align(Alignment.BottomEnd))
                    }
                }
            }
        }
    }
}

/** Min/max size, Windows chrome, and remembering size/position. */
@OptIn(FlowPreview::class)
@Composable
private fun FrameWindowScope.PopupWindowEffects(
    state: androidx.compose.ui.window.WindowState,
    prefs: DesktopPrefs,
    skipTaskbar: Boolean,
) {
    DisposableEffect(window) {
        // xdg_toplevel min/max size on native Wayland; WM_NORMAL_HINTS on X11 / XWayland; MINMAXINFO on Windows.
        window.minimumSize = Dimension(PopupGeometry.MIN_W, PopupGeometry.MIN_H)
        window.maximumSize = Dimension(PopupGeometry.MAX_W, PopupGeometry.MAX_H)
        WindowsPopupChrome.applyRoundedCorners(window)
        WindowsPopupChrome.setSkipTaskbar(window, skipTaskbar)
        // X11 / XWayland peers only forward the minimum to the WM, so the maximum is also enforced here.
        val clamp = object : java.awt.event.ComponentAdapter() {
            override fun componentResized(e: java.awt.event.ComponentEvent) {
                val (w, h) = PopupGeometry.clampSize(window.width, window.height)
                if (w != window.width || h != window.height) window.setSize(w, h)
            }
        }
        window.addComponentListener(clamp)
        onDispose { window.removeComponentListener(clamp) }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.size to state.position }
            .debounce(400)
            .collect { (size, pos) ->
                val x = pos.takeIf { it.isSpecified }?.x?.value?.toInt()
                val y = pos.takeIf { it.isSpecified }?.y?.value?.toInt()
                val (w, h) = PopupGeometry.clampSize(size.width.value.toInt(), size.height.value.toInt())
                val onWayland = !PopupDisplayDetect.canPosition(
                    PopupDisplayDetect.detect(System.getProperty("os.name"), System::getenv),
                )
                prefs.setString(PopupPrefKeys.Bounds, PopupBounds(if (onWayland) null else x, if (onWayland) null else y, w, h).encode())
            }
    }
}

@Composable
private fun FrameWindowScope.PopupHeader(pinned: Boolean, onTogglePin: () -> Unit, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(36.dp).padding(start = 4.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val dragModifier = Modifier.weight(1f).fillMaxSize()
        val dragContent: @Composable () -> Unit = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.DragHandle, "Drag to move",
                    Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                )
            }
        }
        if (HostOs.isWindows) {
            // Windows: let the OS run the caption drag (Aero Snap, per-monitor DPI), falling back to Compose dragging.
            Box(dragModifier.pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent()
                        if (ev.changes.any { it.pressed && !it.previousPressed }) WindowsPopupChrome.beginNativeDrag(window)
                    }
                }
            }) { dragContent() }
        } else {
            WindowDraggableArea(dragModifier) { dragContent() }
        }
        IconButton(onClick = onTogglePin, modifier = Modifier.size(32.dp)) {
            Icon(
                if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                if (pinned) "Unpin (stop keeping on top)" else "Keep on top",
                Modifier.size(18.dp),
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Close, "Close mini player", Modifier.size(18.dp))
        }
    }
}

/**
 * Bottom-right resize grip. The window is undecorated, so there is no frame to grab on most platforms; this works
 * the same everywhere and stays inside [PopupGeometry]'s bounds. (Compositor-driven resizing, e.g. Hyprland's
 * `Super + right-drag`, still works as well.)
 */
@Composable
private fun FrameWindowScope.ResizeGrip(modifier: Modifier) {
    val density = LocalDensity.current.density
    Box(
        modifier
            .size(22.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    val (w, h) = PopupGeometry.clampSize(
                        window.width + (drag.x / density).toInt(),
                        window.height + (drag.y / density).toInt(),
                    )
                    if (w != window.width || h != window.height) window.setSize(w, h)
                }
            },
        contentAlignment = Alignment.BottomEnd,
    ) {
        Box(
            Modifier.padding(5.dp).size(10.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f), RoundedCornerShape(bottomEnd = 6.dp)),
        )
    }
}

/** Per-window WM_CLASS under XToolkit (X11 and XWayland); Wayland-native toolkits have one app_id per process. */
internal object PopupWmClass {
    private var previous: String? = null

    private fun field(): Pair<Any, java.lang.reflect.Field>? = runCatching {
        val t = Toolkit.getDefaultToolkit()
        if (t.javaClass.name != "sun.awt.X11.XToolkit") return null
        t to t.javaClass.getDeclaredField("awtAppClassName").apply { isAccessible = true }
    }.getOrNull()

    fun beginPopup() {
        val (t, f) = field() ?: return
        runCatching { previous = f.get(t) as? String; f.set(t, POPUP_WM_CLASS) }
    }

    fun endPopup() {
        val (t, f) = field() ?: return
        runCatching { previous?.let { f.set(t, it) } }
        previous = null
    }
}
