package com.music.vivi.compose.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Desktop scrolling for horizontal shelves (LazyRow / LazyHorizontalGrid).
 *
 * - **Horizontal wheel / trackpad axis only** (`scrollDelta.x`). Vertical wheel
 *   is never remapped — it must reach the parent LazyColumn for page scroll.
 * - **Mouse drag:** after touch-slop, horizontal drag pans the shelf.
 * - **Inertia:** releasing a drag flings with exponential decay.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.horizontalWheelScroll(
    state: ScrollableState,
    onEdgeOverscroll: ((Float) -> Unit)? = null,
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val speed = with(density) { 56.dp.toPx() }
    val touchSlop = with(density) { 8.dp.toPx() }
    val flingDecay = exponentialDecay<Float>(frictionMultiplier = 1.2f)
    var flingJob by remember(state) { mutableStateOf<Job?>(null) }

    fun cancelFling() {
        flingJob?.cancel()
        flingJob = null
    }

    fun applyScroll(rawStep: Float) {
        if (abs(rawStep) < 0.01f) return
        scope.launch {
            val consumed = state.scrollBy(rawStep)
            val atEnd = rawStep > 0 && !state.canScrollForward
            val atStart = rawStep < 0 && !state.canScrollBackward
            if ((atEnd || atStart) && abs(consumed) < abs(rawStep) * 0.25f) {
                onEdgeOverscroll?.invoke((rawStep - consumed).coerceIn(-48f, 48f))
            } else {
                onEdgeOverscroll?.invoke(0f)
            }
        }
    }

    fun fling(velocityPxPerSec: Float) {
        if (abs(velocityPxPerSec) < 80f) return
        cancelFling()
        flingJob = scope.launch {
            state.scroll {
                val anim = Animatable(0f)
                var last = 0f
                anim.animateDecay(initialVelocity = velocityPxPerSec, animationSpec = flingDecay) {
                    val delta = value - last
                    last = value
                    if (abs(delta) < 0.01f) return@animateDecay
                    val consumed = scrollBy(delta)
                    if (abs(consumed - delta) > 0.5f) throw kotlinx.coroutines.CancellationException("Reached shelf edge")
                }
            }
        }
    }

    this
        .pointerInput(state, touchSlop) {
            awaitPointerEventScope {
                while (true) {
                    val down = awaitPointerEvent(PointerEventPass.Initial)
                        .changes
                        .firstOrNull { it.pressed && it.previousPressed.not() }
                        ?: continue
                    cancelFling()
                    val pointerId = down.id
                    var dragging = false
                    var accumulated = Offset.Zero
                    var lastTime = System.nanoTime()
                    var velocityX = 0f
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (!change.pressed) {
                            if (dragging) { change.consume(); fling(velocityX) }
                            break
                        }
                        val delta = change.positionChange()
                        accumulated += delta
                        val now = System.nanoTime()
                        val dtSec = ((now - lastTime) / 1_000_000_000.0).toFloat().coerceAtLeast(1e-4f)
                        lastTime = now
                        val instant = -delta.x / dtSec
                        velocityX = velocityX * 0.65f + instant * 0.35f
                        if (!dragging) {
                            if (abs(accumulated.x) < touchSlop && abs(accumulated.y) < touchSlop) {
                                continue
                            }
                            // Vertical page drag — don't steal.
                            if (abs(accumulated.y) > abs(accumulated.x) * 1.15f) break
                            dragging = true
                        }
                        if (dragging && abs(delta.x) > 0.01f) {
                            change.consume()
                            applyScroll(-delta.x)
                        }
                    }
                }
            }
        }
        .onPointerEvent(PointerEventType.Scroll, pass = PointerEventPass.Main) { event ->
            val change = event.changes.firstOrNull() ?: return@onPointerEvent
            if (change.isConsumed) return@onPointerEvent
            val dx = change.scrollDelta.x
            val dy = change.scrollDelta.y
            // ONLY real horizontal axis (wl_pointer axis horizontal / trackpad sideways).
            // Never remap vertical wheel → shelf — that steals page scroll.
            if (abs(dx) < 0.01f) return@onPointerEvent
            if (abs(dy) > abs(dx)) return@onPointerEvent
            cancelFling()
            val step = when {
                abs(dx) < 0.5f -> dx * speed
                abs(dx) < 4f -> dx * (speed / 3f)
                else -> dx * 2.5f
            }
            change.consume()
            applyScroll(step)
        }
}

/** Alias matching the UI-UX guide name — same behavior as [horizontalWheelScroll]. */
fun Modifier.dragToScroll(
    state: ScrollableState,
    onEdgeOverscroll: ((Float) -> Unit)? = null,
): Modifier = horizontalWheelScroll(state, onEdgeOverscroll)
