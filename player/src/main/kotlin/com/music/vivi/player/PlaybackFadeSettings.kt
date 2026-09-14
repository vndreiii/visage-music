package com.music.vivi.player

/**
 * Soft volume-ramp prefs for the single libmpv player (not dual-stream crossfade).
 */
data class PlaybackFadeSettings(
    val crossfadeEnabled: Boolean = false,
    /** Total skip transition length; half is fade-out, half fade-in. Clamped 1–10. */
    val crossfadeSeconds: Int = 3,
    val playPauseFadeEnabled: Boolean = true,
    /** Play/pause ramp length in seconds. Clamped 0.2–3. */
    val playPauseFadeSeconds: Float = 0.5f,
) {
    val crossfadeHalfMs: Long
        get() = (crossfadeSeconds.coerceIn(1, 10) * 1000L) / 2

    val playPauseFadeMs: Long
        get() = (playPauseFadeSeconds.coerceIn(0.2f, 3f) * 1000f).toLong()
}

fun interface PlaybackFadeSettingsProvider {
    fun current(): PlaybackFadeSettings
}
