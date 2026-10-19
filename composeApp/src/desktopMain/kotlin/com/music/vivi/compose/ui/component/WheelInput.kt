package com.music.vivi.compose.ui.component

import kotlin.math.abs

/** Axis a wheel / touchpad scroll stream is acting on. */
internal enum class WheelAxis { Horizontal, Vertical }

/** What produced a scroll event, as far as we can tell from the numbers alone. */
internal enum class WheelKind {
    /** A discrete mouse-wheel click (integer delta, isolated): glide a fixed distance. */
    Notch,

    /**
     * A stream of integer clicks arriving at touchpad rate (XWayland/X11 emulates touchpad
     * scrolling as rapid wheel buttons): small steps with light smoothing to hide the quantisation.
     */
    Stream,

    /** Fractional deltas (native precise scrolling): applied 1:1, no animation. */
    Precise,
}

internal class WheelSample(
    val axis: WheelAxis,
    /** Signed delta along [axis], in wheel units (positive = towards the end / down / right). */
    val delta: Float,
    val kind: WheelKind,
    /** True for the first event after an idle gap. */
    val streamStart: Boolean,
)

/**
 * Pure classifier for pointer scroll events. Every scroll modifier on the same pointer path owns
 * one and feeds it the same events, so they all agree which axis a gesture belongs to (a
 * horizontal shelf inside a vertical page: the dominant axis of the gesture wins, and a touchpad
 * stream keeps its axis until the fingers pause, so diagonal jitter cannot flip it).
 */
internal class WheelClassifier(
    private val streamGapNanos: Long = 120_000_000L,
    private val fastGapNanos: Long = 24_000_000L,
) {
    private var lastNanos = Long.MIN_VALUE
    private var fastCount = 0
    private var locked: WheelAxis? = null
    private var accX = 0f
    private var accY = 0f
    private var events = 0

    fun classify(dx: Float, dy: Float, nanos: Long): WheelSample? {
        if (abs(dx) < 1e-4f && abs(dy) < 1e-4f) return null
        val gap = if (lastNanos == Long.MIN_VALUE) Long.MAX_VALUE else nanos - lastNanos
        lastNanos = nanos
        val start = gap > streamGapNanos
        if (start) {
            locked = null; accX = 0f; accY = 0f; fastCount = 0; events = 0
        }
        fastCount = if (!start && gap < fastGapNanos) fastCount + 1 else 0
        events++

        val fractional = isFractional(dx) || isFractional(dy)
        val kind = when {
            fractional -> WheelKind.Precise
            fastCount >= 2 -> WheelKind.Stream
            else -> WheelKind.Notch
        }

        val axis: WheelAxis
        if (kind == WheelKind.Notch) {
            axis = if (abs(dx) > abs(dy)) WheelAxis.Horizontal else WheelAxis.Vertical
        } else {
            accX += dx; accY += dy
            val dominant = if (abs(accX) > abs(accY)) WheelAxis.Horizontal else WheelAxis.Vertical
            if (locked == null && (events >= 3 || maxOf(abs(accX), abs(accY)) >= 0.5f)) locked = dominant
            axis = locked ?: dominant
        }
        val delta = if (axis == WheelAxis.Horizontal) dx else dy
        return WheelSample(axis, delta, kind, start)
    }

    private fun isFractional(v: Float) = abs(v - Math.round(v)) > 0.001f
}

/** Edge-swipe back gesture decisions, pure so they can be unit-tested. */
internal object BackSwipe {
    /** Fraction of the page width a swipe has to cover to reach progress 1. */
    const val TravelFraction = 0.75f

    /** Progress at/after which releasing commits the back navigation. */
    const val CommitProgress = 0.28f

    /** Progress velocity (per second) over which a flick commits even below [CommitProgress]. */
    const val FlickVelocity = 1.6f

    fun progress(accumulatedPx: Float, widthPx: Float): Float =
        if (widthPx <= 0f) 0f else (accumulatedPx / (widthPx * TravelFraction)).coerceIn(0f, 1f)

    fun shouldCommit(progress: Float, velocityPerSec: Float): Boolean =
        progress >= CommitProgress || (progress > 0.06f && velocityPerSec >= FlickVelocity)
}
