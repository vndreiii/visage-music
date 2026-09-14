package com.music.vivi.compose.ui.component

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Desktop scrolling for horizontal shelves (LazyRow / LazyHorizontalGrid).
 *
 * - **Wheel / trackpad:** maps both horizontal *and* vertical wheel deltas to
 *   shelf scroll while the pointer is over the row (vertical page scroll resumes
 *   when the pointer leaves). Raw Linux steps are amplified.
 * - **Mouse drag:** after touch-slop, horizontal drag scrolls the shelf even when
 *   children are clickable (intercept on [PointerEventPass.Initial]).
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

    this
        .pointerInput(state, touchSlop) {
            awaitPointerEventScope {
                while (true) {
                    val down = awaitPointerEvent(PointerEventPass.Initial)
                        .changes
                        .firstOrNull { it.pressed && it.previousPressed.not() }
                        ?: continue
                    val pointerId = down.id
                    var dragging = false
                    var accumulated = Offset.Zero
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (!change.pressed) break
                        val delta = change.positionChange()
                        accumulated += delta
                        if (!dragging) {
                            if (abs(accumulated.x) < touchSlop && abs(accumulated.y) < touchSlop) {
                                continue
                            }
                            // Prefer horizontal; bail if clearly vertical page intent.
                            if (abs(accumulated.y) > abs(accumulated.x) * 1.25f) break
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
            val dx = change.scrollDelta.x
            val dy = change.scrollDelta.y
            // Prefer true horizontal deltas; otherwise map vertical wheel → shelf
            // (desktop expectation when hovering a carousel).
            val dominant = when {
                abs(dx) > abs(dy) && abs(dx) >= 0.01f -> dx
                abs(dy) >= 0.01f -> dy
                else -> return@onPointerEvent
            }
            val step = when {
                abs(dominant) < 0.5f -> dominant * speed
                abs(dominant) < 4f -> dominant * (speed / 3f)
                else -> dominant * 2.5f
            }
            change.consume()
            applyScroll(step)
        }
}
