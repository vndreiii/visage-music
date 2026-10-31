package com.music.vivi.compose.ui.popup

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import com.music.vivi.compose.ui.visageWindowDragModifier
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
                    // Dragging anywhere on the popup (cover, header, empty space) moves it; children that use the
                    // gesture themselves (sliders, scrollers) keep it. Windows runs its own caption drag on the header.
                    val bodyDrag = if (HostOs.isWindows) Modifier else visageWindowDragModifier()
                    Box(Modifier.fillMaxSize().then(bodyDrag)) {
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
                        ResizeEdges(window)
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
        // No visible handle: the empty header (and, off Windows, the whole popup body) is the drag area.
        val dragModifier = Modifier.weight(1f).fillMaxSize()
        if (HostOs.isWindows) {
            // Windows: let the OS run the caption drag (Aero Snap, per-monitor DPI), falling back to Compose dragging.
            Box(dragModifier.pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent()
                        if (ev.changes.any { it.pressed && !it.previousPressed }) WindowsPopupChrome.beginNativeDrag(window)
                    }
                }
            })
        } else {
            Box(dragModifier)
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
 * Invisible resize strips along the right edge, bottom edge and bottom-right corner (the window is undecorated, so
 * there is no frame to grab). They stay inside [PopupGeometry]'s bounds. The top-left origin never moves, so this
 * works on native Wayland too, where a client cannot position itself. (Compositor-driven resizing, e.g. Hyprland's
 * `Super + right-drag`, still works as well.)
 */
@Composable
private fun BoxScope.ResizeEdges(window: java.awt.Window) {
    ResizeStrip(window, Modifier.align(Alignment.CenterEnd).width(6.dp).fillMaxHeight().padding(bottom = 16.dp, top = 40.dp), 1, 0, java.awt.Cursor.E_RESIZE_CURSOR)
    ResizeStrip(window, Modifier.align(Alignment.BottomCenter).height(6.dp).fillMaxWidth().padding(end = 16.dp), 0, 1, java.awt.Cursor.S_RESIZE_CURSOR)
    ResizeStrip(window, Modifier.align(Alignment.BottomEnd).size(16.dp), 1, 1, java.awt.Cursor.SE_RESIZE_CURSOR)
}

@Composable
private fun ResizeStrip(window: java.awt.Window, modifier: Modifier, dirX: Int, dirY: Int, cursor: Int) {
    val density = LocalDensity.current.density
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .pointerHoverIcon(PointerIcon(java.awt.Cursor(cursor)))
            .onGloballyPositioned { origin = it.positionInWindow() }
            .pointerInput(Unit) {
                // The pointer is tracked in window coordinates (strip origin + local position) against the size at
                // drag start; per-event deltas would be measured in a frame that moves with the window edge.
                var startPointer = Offset.Zero
                var startW = 0
                var startH = 0
                detectDragGestures(
                    onDragStart = { local ->
                        startPointer = origin + local
                        startW = window.width
                        startH = window.height
                    },
                ) { change, _ ->
                    change.consume()
                    val now = origin + change.position
                    val (w, h) = PopupGeometry.clampSize(
                        startW + if (dirX != 0) ((now.x - startPointer.x) / density).toInt() else 0,
                        startH + if (dirY != 0) ((now.y - startPointer.y) / density).toInt() else 0,
                    )
                    if (w != window.width || h != window.height) window.setSize(w, h)
                }
            },
    )
}

/** Per-window WM_CLASS under XToolkit (X11 and XWayland); Wayland-native toolkits have one app_id per process. */
internal object PopupWmClass {
    private var previous: String? = null

    private fun field(): Pair<Any, java.lang.reflect.Field>? = runCatching {
        val t = Toolkit.getDefaultToolkit()
        if (t.javaClass.name != "sun.awt.X11.XToolkit") return null
        t to t.javaClass.getDeclaredField("awtAppClassName").apply { isAccessible = true }
    }.getOrNull()

    private var previousAppId: String? = null

    fun beginPopup() {
        // Native Wayland (WLToolkit): the app_id comes from this system property when the surface is created.
        if (com.music.vivi.compose.ui.isNativeWayland()) { previousAppId = System.getProperty("awt.app.id"); System.setProperty("awt.app.id", POPUP_WM_CLASS); return }
        val (t, f) = field() ?: return
        runCatching { previous = f.get(t) as? String; f.set(t, POPUP_WM_CLASS) }
    }

    fun endPopup() {
        if (com.music.vivi.compose.ui.isNativeWayland()) { previousAppId?.let { System.setProperty("awt.app.id", it) }; previousAppId = null; return }
        val (t, f) = field() ?: return
        runCatching { previous?.let { f.set(t, it) } }
        previous = null
    }
}
