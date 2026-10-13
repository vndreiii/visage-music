package com.music.vivi.compose.ui.component

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp

/**
 * Browser-like vertical mouse-wheel scrolling for a list/grid/scroll column.
 *
 * Compose's Linux default moves ~sqrt(viewport) px per notch on a linear tween, which
 * reads as small, stepped jumps. Here each notch adds a fixed distance to a pending
 * target and one frame loop eases toward it exponentially: fast spins accumulate, and
 * every scroll glides to a stop. Trackpad / precise deltas are applied the same way
 * with a short time constant so they stay 1:1 and responsive.
 *
 * Only the vertical axis is taken; horizontal wheel still reaches shelves.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.smoothWheelScroll(state: ScrollableState): Modifier = composed {
    val scope = rememberCoroutineScope()
    val notchPx = with(LocalDensity.current) { 110.dp.toPx() }
    var pending by remember(state) { mutableStateOf(0f) }
    var job by remember(state) { mutableStateOf<Job?>(null) }
    var tauMs by remember(state) { mutableStateOf(95f) }

    fun ensureRunning() {
        if (job?.isActive == true) return
        job = scope.launch {
            state.scroll(MutatePriority.UserInput) {
                var lastNanos = withFrameNanos { it }
                while (abs(pending) > 0.5f) {
                    val now = withFrameNanos { it }
                    val dtMs = ((now - lastNanos) / 1_000_000f).coerceIn(1f, 50f)
                    lastNanos = now
                    val step = pending * (1f - exp(-dtMs / tauMs))
                    val consumed = scrollBy(step)
                    pending -= step
                    // Hit the top/bottom: drop the rest instead of pushing against the edge.
                    if (abs(consumed) < abs(step) * 0.5f && abs(step) > 0.5f) pending = 0f
                }
                pending = 0f
            }
        }
    }

    onPointerEvent(PointerEventType.Scroll, pass = PointerEventPass.Initial) { event ->
        val change = event.changes.firstOrNull() ?: return@onPointerEvent
        val dy = change.scrollDelta.y
        val dx = change.scrollDelta.x
        if (abs(dy) < 0.001f || abs(dx) > abs(dy)) return@onPointerEvent
        // Mouse notches arrive as whole numbers (±1, ±3); trackpads send small fractions.
        val precise = abs(dy) < 1f || abs(dy - dy.toInt()) > 0.001f
        val delta = if (precise) dy * notchPx * 0.35f else dy * notchPx
        // Reversing direction cancels leftover momentum immediately.
        if (pending != 0f && (pending > 0f) != (delta > 0f)) pending = 0f
        tauMs = if (precise) 45f else 95f
        pending = (pending + delta).coerceIn(-notchPx * 30f, notchPx * 30f)
        if ((delta > 0f && state.canScrollForward) || (delta < 0f && state.canScrollBackward)) {
            change.consume()
            ensureRunning()
        } else {
            pending = 0f
        }
    }
}
