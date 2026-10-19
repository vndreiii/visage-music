package com.music.vivi.compose.ui.player.swap

import com.music.vivi.compose.prefs.PrefReader

/** Pref keys for the "Song swappings" feature (stored as plain strings / ints / booleans). */
object SongSwapKeys {
    const val Mode = "songSwapMode"
    /** Animation speed in percent (50..200). */
    const val Speed = "songSwapSpeed"
    /** Motion intensity in percent (25..150). */
    const val Intensity = "songSwapIntensity"
    const val AnimateBackground = "songSwapAnimateBg"
    const val AnimateControls = "songSwapAnimateControls"
    const val GrayscaleWhileLoading = "songSwapGrayscale"
}

enum class SongSwapMode(val label: String, val description: String) {
    CAMERA_PAN("Camera pan", "The whole view travels down to the next song; lyrics stay put"),
    SLIDE("Slide", "Cover slides sideways, the background stays still"),
    CROSSFADE("Crossfade", "Cover, background and controls dissolve"),
    ZOOM_THROUGH("Zoom through", "Fly through the old cover into the next"),
    FLIP("Flip", "3D card flip of the cover"),
    STACK("Stack", "Old cover drops to the back, the next lands on top"),
    VINYL("Vinyl swap", "Pulled out like a record, the next drops in"),
    WIPE("Wipe", "Cookie-shaped reveal from the play button"),
    BLUR_MORPH("Blur morph", "Blur out, blur in, colours morph"),
    NONE("None", "Instant swap, no animation");

    /** True when [SongSwapState] animates this mode (everything except None). */
    val driven: Boolean get() = this != NONE

    /**
     * True when the Now Playing screens hand the animation to [SongSwapState]. Slide keeps the
     * legacy in-place AnimatedContent behaviour there (the engine version of it is preview-only).
     */
    val engineInPlayer: Boolean get() = this != SLIDE && this != NONE

    companion object {
        val Default = SLIDE
        fun parse(raw: String?): SongSwapMode =
            entries.firstOrNull { it.name == raw } ?: Default
    }
}

/** Resolved swap settings; [speed] and [intensity] are multipliers (1f = default). */
data class SongSwapConfig(
    val mode: SongSwapMode = SongSwapMode.Default,
    val speed: Float = 1f,
    val intensity: Float = 1f,
    val animateBackground: Boolean = true,
    val animateControls: Boolean = true,
    val grayscaleWhileLoading: Boolean = true,
) {
    companion object {
        fun resolve(p: PrefReader): SongSwapConfig = SongSwapConfig(
            mode = SongSwapMode.parse(p.getString(SongSwapKeys.Mode, SongSwapMode.Default.name)),
            speed = p.getInt(SongSwapKeys.Speed, 100).coerceIn(50, 200) / 100f,
            intensity = p.getInt(SongSwapKeys.Intensity, 100).coerceIn(25, 150) / 100f,
            animateBackground = p.getBoolean(SongSwapKeys.AnimateBackground, true),
            animateControls = p.getBoolean(SongSwapKeys.AnimateControls, true),
            grayscaleWhileLoading = p.getBoolean(SongSwapKeys.GrayscaleWhileLoading, true),
        )
    }
}

/** Which way the queue moved. [forward] decides the travel direction of directional modes. */
enum class SwapDirection(val forward: Boolean, val isJump: Boolean) {
    NEXT(true, false),
    PREVIOUS(false, false),
    JUMP_FORWARD(true, true),
    JUMP_BACK(false, true);

    companion object {
        /**
         * Compares the queue before and after a track change.
         * Returns null when the track did not change (nothing to animate).
         */
        fun detect(
            prevId: String?,
            prevIds: List<String>,
            prevIndex: Int,
            newId: String?,
            newIds: List<String>,
            newIndex: Int,
        ): SwapDirection? {
            if (newId == null || newId == prevId) return null
            if (prevId == null) return NEXT
            val sameQueue = prevIds == newIds
            val from = if (prevIndex in prevIds.indices && prevIds[prevIndex] == prevId) prevIndex
            else prevIds.indexOf(prevId)
            val to = if (newIndex in newIds.indices && newIds[newIndex] == newId) newIndex
            else newIds.indexOf(newId)
            if (to < 0 || from < 0) return NEXT
            if (sameQueue) {
                return when {
                    to == from + 1 -> NEXT
                    to == from - 1 -> PREVIOUS
                    to > from -> JUMP_FORWARD
                    else -> JUMP_BACK
                }
            }
            // Queue was rebuilt (e.g. radio / new list): the new track's old position, if any.
            val old = prevIds.indexOf(newId)
            return when {
                old < 0 -> NEXT
                old == from + 1 -> NEXT
                old == from - 1 -> PREVIOUS
                old > from -> JUMP_FORWARD
                else -> JUMP_BACK
            }
        }
    }
}
