package com.music.vivi.compose.ui.component

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp

/**
 * Applies wheel / touchpad samples to a [ScrollableState] along one axis.
 *
 *  - Notch (mouse wheel click): adds a fixed distance to a pending target that a frame loop eases
 *    towards, so a click glides and fast spins accumulate.
 *  - Stream (rapid integer clicks, i.e. touchpad through X11/XWayland): small steps, short time
 *    constant -- responsive but without visible quantisation.
 *  - Precise (fractional deltas): dispatched straight to the state, 1:1, no animation lag at all.
 */
internal class WheelDriver(
    private val state: ScrollableState,
    private val scope: CoroutineScope,
    private val notchPx: Float,
    /** Called with the signed distance (px) actually scrolled; used to drift the page background. */
    private val onScrolled: ((Float) -> Unit)? = null,
) {
    private var pending = 0f
    private var job: Job? = null
    private var tauMs = NotchTauMs

    /** Returns true when the sample moved (or will move) the scroller and should be consumed. */
    fun apply(sample: WheelSample): Boolean {
        val dir = sample.delta
        val canMove = (dir > 0f && state.canScrollForward) || (dir < 0f && state.canScrollBackward)
        if (!canMove) {
            pending = 0f
            return false
        }
        // Reversing direction drops leftover glide immediately.
        if (pending != 0f && (pending > 0f) != (dir > 0f)) pending = 0f
        when (sample.kind) {
            WheelKind.Precise -> {
                pending = 0f
                onScrolled?.invoke(state.dispatchRawDelta(sample.delta * notchPx * PreciseGain))
            }
            WheelKind.Stream -> {
                tauMs = StreamTauMs
                add(sample.delta * notchPx * StreamGain)
            }
            WheelKind.Notch -> {
                tauMs = NotchTauMs
                add(sample.delta * notchPx)
            }
        }
        return true
    }

    private fun add(px: Float) {
        pending = (pending + px).coerceIn(-notchPx * 30f, notchPx * 30f)
        if (job?.isActive == true) return
        job = scope.launch {
            state.scroll(MutatePriority.UserInput) {
                var last = withFrameNanos { it }
                while (abs(pending) > 0.5f) {
                    val now = withFrameNanos { it }
                    val dtMs = ((now - last) / 1_000_000f).coerceIn(1f, 50f)
                    last = now
                    val step = pending * (1f - exp(-dtMs / tauMs))
                    val consumed = scrollBy(step)
                    onScrolled?.invoke(consumed)
                    pending -= step
                    if (abs(consumed) < abs(step) * 0.5f && abs(step) > 0.5f) pending = 0f
                }
                pending = 0f
            }
        }
    }

    companion object {
        const val NotchTauMs = 95f
        const val StreamTauMs = 32f
        const val PreciseGain = 0.8f
        const val StreamGain = 0.45f
    }
}

/**
 * Shared implementation behind [smoothWheelScroll] and [horizontalWheelScroll]'s wheel handling.
 * [onUnconsumed] is called with the signed leftover when the scroller is already at that edge.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.wheelScroll(
    state: ScrollableState,
    axis: WheelAxis,
    pass: PointerEventPass,
    onUnconsumed: ((Float) -> Unit)? = null,
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val notchPx = with(LocalDensity.current) { 110.dp.toPx() }
    val classifier = remember(state) { WheelClassifier() }
    val backdrop = com.music.vivi.compose.ui.glass.LocalBackdropScroll.current
    val driver = remember(state, notchPx, backdrop) {
        WheelDriver(state, scope, notchPx, backdrop?.let { b -> { d: Float -> b.y = (b.y + d).coerceAtLeast(0f) } })
    }
    onPointerEvent(PointerEventType.Scroll, pass = pass) { event ->
        val change = event.changes.firstOrNull() ?: return@onPointerEvent
        val sample = classifier.classify(
            change.scrollDelta.x,
            change.scrollDelta.y,
            System.nanoTime(),
        ) ?: return@onPointerEvent
        if (sample.axis != axis || change.isConsumed) return@onPointerEvent
        if (driver.apply(sample)) change.consume() else onUnconsumed?.invoke(sample.delta)
    }
}

/**
 * Mouse-wheel + touchpad vertical scrolling for a list/grid/scroll column.
 *
 * Compose's Linux default moves ~sqrt(viewport) px per unit on a linear tween, which reads as
 * small stepped jumps and makes touchpads feel dead. See [WheelDriver] for how each kind of input
 * is handled. Only the vertical axis is taken; horizontal gestures reach shelves / the back swipe.
 */
fun Modifier.smoothWheelScroll(state: ScrollableState): Modifier =
    wheelScroll(state, WheelAxis.Vertical, PointerEventPass.Initial)
