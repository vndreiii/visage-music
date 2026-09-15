package com.music.vivi.player

/**
 * Soft volume-ramp prefs. Crossfade uses two libmpv instances overlapping for
 * [crossfadeSeconds]; play/pause fade still uses the active player only.
 */
data class PlaybackFadeSettings(
    val crossfadeEnabled: Boolean = false,
    /** Overlap length for dual-stream crossfade. Clamped 1–10. */
    val crossfadeSeconds: Int = 3,
    val playPauseFadeEnabled: Boolean = true,
    /** Play/pause ramp length in seconds. Clamped 0.2–3. */
    val playPauseFadeSeconds: Float = 0.5f,
) {
    /** Full overlap window in ms (both players audible together). */
    val crossfadeMs: Long
        get() = crossfadeSeconds.coerceIn(1, 10) * 1000L

    @Deprecated("Use crossfadeMs — dual crossfade overlaps the full duration")
    val crossfadeHalfMs: Long
        get() = crossfadeMs / 2

    val playPauseFadeMs: Long
        get() = (playPauseFadeSeconds.coerceIn(0.2f, 3f) * 1000f).toLong()
}

fun interface PlaybackFadeSettingsProvider {
    fun current(): PlaybackFadeSettings
}
