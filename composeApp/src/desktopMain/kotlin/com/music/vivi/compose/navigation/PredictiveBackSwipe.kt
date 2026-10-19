package com.music.vivi.compose.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.findDefaultNavigationEventDispatcherOwner
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import com.music.vivi.compose.ui.component.WheelAxis
import com.music.vivi.compose.ui.component.WheelClassifier
import com.music.vivi.compose.ui.component.WheelDriver
import com.music.vivi.compose.ui.component.WheelKind
import com.music.vivi.compose.ui.motion.VisageMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent as AwtKeyEvent
import kotlin.math.abs

/** Pages that never take the back swipe: tab roots and the player (which owns its gestures). */
internal val backSwipeExcluded = setOf(
    Routes.HOME, Routes.SEARCH, Routes.LIBRARY, Routes.SETTINGS,
    Routes.NOW_PLAYING, Routes.NOW_PLAYING_FULLSCREEN,
)

/**
 * Desktop back navigation for a [androidx.navigation.compose.NavHost] container:
 *
 *  - **Touchpad two-finger swipe right** on a non-root page drives NavHost's predictive back
 *    (the seekable pop transition: page follows the fingers, the previous page is revealed under
 *    it). Release past [com.music.vivi.compose.ui.component.BackSwipe.CommitProgress] (or with a
 *    flick) springs the rest of the way and pops; otherwise it springs back and cancels.
 *  - Only starts when the stream's first events were *not* consumed by a scroller, so swiping
 *    inside a shelf scrolls the shelf, and a shelf reaching its start edge never hands the same
 *    swipe to back.
 *  - **Mouse back side button** and **Alt+Left** pop the back stack.
 *
 * Must wrap the NavHost so it sees scroll events in the Final pass, after children had their say.
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeApi::class)
fun Modifier.predictiveBackSwipe(navController: NavHostController): Modifier = composed {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val notchPx = with(density) { 110.dp.toPx() }
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val canPop = entry != null && navController.previousBackStackEntry != null
    val swipeEnabled by rememberUpdatedState(canPop && route !in backSwipeExcluded)
    val canPopState by rememberUpdatedState(canPop)

    val dispatcher = findDefaultNavigationEventDispatcherOwner()?.navigationEventDispatcher
    val input = remember { DirectNavigationEventInput() }
    DisposableEffect(dispatcher, input) {
        dispatcher?.addInput(input)
        onDispose { dispatcher?.removeInput(input) }
    }

    // Alt+Left, at the AWT level so it works without a focused Compose node.
    DisposableEffect(navController) {
        val manager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        val dispatcherAwt = java.awt.KeyEventDispatcher { e ->
            if (e.id == AwtKeyEvent.KEY_PRESSED && e.keyCode == AwtKeyEvent.VK_LEFT &&
                e.isAltDown && !e.isControlDown && !e.isShiftDown && canPopState
            ) {
                navController.popBackStack()
                true
            } else {
                false
            }
        }
        manager.addKeyEventDispatcher(dispatcherAwt)
        onDispose { manager.removeKeyEventDispatcher(dispatcherAwt) }
    }

    // Mouse buttons at the AWT level. Java's X11 layer only knows X buttons 4/5 as wheel clicks; the
    // tilt wheel (X6/X7) arrives as AWT buttons 4/5 and the side buttons (X8/X9) as 6/7. Tilt becomes
    // a horizontal wheel event, button 6 (the real "back" side button) goes back.
    DisposableEffect(navController) {
        val listener = java.awt.event.AWTEventListener { ev ->
            val e = ev as? java.awt.event.MouseEvent ?: return@AWTEventListener
            if (e is java.awt.event.MouseWheelEvent) return@AWTEventListener
            when (e.button) {
                4, 5 -> {
                    if (e.id == java.awt.event.MouseEvent.MOUSE_PRESSED) {
                        val dir = if (e.button == 4) -1 else 1
                        val src = e.source as? java.awt.Component
                        if (src != null) {
                            java.awt.Toolkit.getDefaultToolkit().systemEventQueue.postEvent(
                                java.awt.event.MouseWheelEvent(
                                    src, java.awt.event.MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(),
                                    java.awt.event.InputEvent.SHIFT_DOWN_MASK, e.x, e.y, 0, false,
                                    java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, dir,
                                ),
                            )
                        }
                    }
                    e.consume()
                }
                6 -> {
                    if (e.id == java.awt.event.MouseEvent.MOUSE_PRESSED && canPopState) {
                        scope.launch { navController.popBackStack() }
                    }
                    e.consume()
                }
                7 -> e.consume()
            }
        }
        java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(listener, java.awt.AWTEvent.MOUSE_EVENT_MASK)
        onDispose { java.awt.Toolkit.getDefaultToolkit().removeAWTEventListener(listener) }
    }

    val state = remember { BackSwipeState() }
    var widthPx by remember { mutableStateOf(0f) }
    val classifier = remember { com.music.vivi.compose.ui.component.WheelClassifier() }

    fun dispatch(progress: Float, x: Float, y: Float) {
        input.backProgressed(NavigationEvent(touchX = x, touchY = y, progress = progress.coerceIn(0f, 1f), swipeEdge = NavigationEvent.EDGE_LEFT))
    }

    fun finish(commit: Boolean) {
        val from = state.progress
        state.finishing = true
        scope.launch {
            val a = Animatable(from)
            try {
                a.animateTo(if (commit) 1f else 0f, VisageMotion.settle()) {
                    dispatch(value, state.x, state.y)
                }
            } finally {
                if (commit) input.backCompleted() else input.backCancelled()
                state.reset()
            }
        }
    }

    fun scheduleIdle() {
        state.idleJob?.cancel()
        state.idleJob = scope.launch {
            delay(110)
            if (state.active && !state.finishing) {
                val commit = com.music.vivi.compose.ui.component.BackSwipe.shouldCommit(
                    state.progress, state.velocity(),
                )
                finish(commit)
            } else if (!state.active) {
                state.acc = 0f
            }
        }
    }

    this
        .onSizeChanged { widthPx = it.width.toFloat() }
        .onPointerEvent(PointerEventType.Scroll, pass = PointerEventPass.Final) { event ->
            val change = event.changes.firstOrNull() ?: return@onPointerEvent
            val now = System.nanoTime()
            val sample = classifier.classify(change.scrollDelta.x, change.scrollDelta.y, now)
                ?: return@onPointerEvent
            if (sample.streamStart) {
                // A scroller that took the opening of the stream owns all of it.
                state.owned = change.isConsumed
                state.events = 0
                if (!state.active && !state.finishing) state.acc = 0f
            }
            state.events++
            if (!swipeEnabled || state.finishing) return@onPointerEvent
            if (sample.axis != WheelAxis.Horizontal || sample.kind == WheelKind.Notch && !state.active) {
                return@onPointerEvent
            }
            if (state.owned && !state.active) return@onPointerEvent
            val gain = when (sample.kind) {
                WheelKind.Precise -> WheelDriver.PreciseGain
                else -> WheelDriver.StreamGain
            }
            // Negative delta = content dragged to the right = swipe right = back.
            val px = -sample.delta * notchPx * gain
            state.acc = (state.acc + px).coerceAtLeast(0f)
            state.x = change.position.x
            state.y = change.position.y
            if (!state.active) {
                if (state.events >= 3 && state.acc >= 14f && px > 0f) {
                    state.active = true
                    input.backStarted(NavigationEvent(touchX = state.x, touchY = state.y, progress = 0f, swipeEdge = NavigationEvent.EDGE_LEFT))
                } else {
                    scheduleIdle()
                    return@onPointerEvent
                }
            }
            state.progress = com.music.vivi.compose.ui.component.BackSwipe.progress(state.acc, widthPx)
            state.record(now)
            dispatch(state.progress, state.x, state.y)
            change.consume()
            scheduleIdle()
        }
}

internal class BackSwipeState {
    var active = false
    var finishing = false
    var owned = false
    var events = 0
    var acc = 0f
    var progress = 0f
    var x = 0f
    var y = 0f
    var idleJob: Job? = null
    private val times = LongArray(8)
    private val values = FloatArray(8)
    private var n = 0

    fun record(nanos: Long) {
        if (n == times.size) {
            System.arraycopy(times, 1, times, 0, n - 1)
            System.arraycopy(values, 1, values, 0, n - 1)
            n--
        }
        times[n] = nanos; values[n] = progress; n++
    }

    /** Progress units per second over the last ~100 ms of the swipe. */
    fun velocity(): Float {
        if (n < 2) return 0f
        val last = n - 1
        var i = last
        while (i > 0 && times[last] - times[i - 1] <= 100_000_000L) i--
        val dt = (times[last] - times[i]) / 1e9f
        return if (dt <= 0f) 0f else (values[last] - values[i]) / dt
    }

    fun reset() {
        active = false; finishing = false; owned = false; events = 0
        acc = 0f; progress = 0f; n = 0
    }
}
