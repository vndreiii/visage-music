package com.music.vivi.compose.ui.player.background

/**
 * Decides when a canvas decoder that never produced a frame (mpv failed to open the stream, the
 * renderer could not be created, a transient network error) should be restarted, with growing waits.
 * Pure and clock-free so it is unit-testable.
 */
internal class CanvasStartWatchdog(
    private val waitsMs: List<Long> = listOf(8_000, 12_000, 20_000),
) {
    enum class Action { NONE, RESTART, GIVE_UP }

    private var restarts = 0
    private var startedAtMs = Long.MIN_VALUE

    /** Call when decoders are (re)created. */
    fun started(nowMs: Long) { startedAtMs = nowMs }

    fun check(hasFrame: Boolean, nowMs: Long): Action {
        if (hasFrame) { restarts = 0; return Action.NONE }
        if (startedAtMs == Long.MAX_VALUE) return Action.GIVE_UP
        if (startedAtMs == Long.MIN_VALUE) return Action.NONE
        if (nowMs - startedAtMs < waitsMs[restarts.coerceAtMost(waitsMs.size - 1)]) return Action.NONE
        if (restarts >= waitsMs.size) { startedAtMs = Long.MAX_VALUE; return Action.GIVE_UP }
        restarts++
        startedAtMs = nowMs
        return Action.RESTART
    }
}
