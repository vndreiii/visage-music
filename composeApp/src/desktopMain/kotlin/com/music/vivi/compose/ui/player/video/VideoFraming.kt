package com.music.vivi.compose.ui.player.video

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.PrefKeys
import com.music.vivi.compose.prefs.PrefReader
import com.music.vivi.compose.ui.motion.VisageMotion
import com.music.vivi.compose.ui.settings.rememberPrefsVersion
import kotlin.math.roundToInt

/** How a music video is shown in the cover slot. */
enum class VideoFraming {
    /** Square slot, letterboxed on black. */
    Fit,
    /** Square slot, cropped to fill. */
    Fill,
    /** The slot morphs to the video's own aspect ratio; nothing is cropped or letterboxed. */
    Original;

    companion object {
        const val DefaultAspect = 16f / 9f
        private const val MinAspect = 1f
        private const val MaxAspect = 2.4f

        /**
         * [stored] is the new three-way pref; [legacyFill] the old boolean `playerVideoFill`
         * ("true"/"false"/absent) used when the new key has never been written.
         */
        fun resolve(stored: String?, legacyFill: String?): VideoFraming {
            entries.firstOrNull { it.name == stored }?.let { return it }
            return if (legacyFill?.toBooleanStrictOrNull() == true) Fill else Fit
        }

        fun resolve(reader: PrefReader): VideoFraming = resolve(
            reader.getString(PrefKeys.PlayerVideoFraming, "").ifEmpty { null },
            reader.getString(PrefKeys.PlayerVideoFill, "").ifEmpty { null },
        )

        /** Aspect (w/h) of a rendered frame; [DefaultAspect] when unknown, clamped to a sane range. */
        fun aspectOf(width: Int, height: Int): Float =
            if (width <= 0 || height <= 0) DefaultAspect
            else (width.toFloat() / height).coerceIn(MinAspect, MaxAspect)

        /** Target cover aspect: the video's when [framing] is Original and a video is on screen, else square. */
        fun coverAspect(framing: VideoFraming, videoAspect: Float?): Float =
            if (framing == Original && videoAspect != null) videoAspect.coerceIn(MinAspect, MaxAspect) else 1f
    }
}

@Composable
fun rememberVideoFraming(prefs: DesktopPrefs): VideoFraming {
    rememberPrefsVersion(prefs)
    return VideoFraming.resolve(
        prefs.getString(PrefKeys.PlayerVideoFraming, "").ifEmpty { null },
        prefs.getString(PrefKeys.PlayerVideoFill, "").ifEmpty { null },
    )
}

/**
 * Animated cover morph. The video surface reports the frame aspect on its first frame
 * ([report]); the cover aspect then springs 1 -> video aspect, and back to 1 when the video
 * goes away (song change, video ends, setting off).
 */
@Stable
class CoverMorph internal constructor() {
    private var reportedFor by mutableStateOf<Any?>(null)
    private var reportedAspect by mutableStateOf(VideoFraming.DefaultAspect)
    internal var animated: State<Float> = mutableStateOf(1f)

    fun report(video: Any, aspect: Float) {
        reportedAspect = aspect
        reportedFor = video
    }

    internal fun videoAspect(video: Any?): Float? = if (video != null && video == reportedFor) reportedAspect else null

    /** Current animated aspect ratio (w/h). Read in layout/draw phases to avoid recomposition. */
    val aspect: Float get() = animated.value

    /** 0 = square, 1 = fully 16:9-ish. */
    val progress: Float
        get() = ((animated.value - 1f) / (VideoFraming.DefaultAspect - 1f)).coerceIn(0f, 1.2f)
}

@Composable
fun rememberCoverMorph(framing: VideoFraming, video: Any?): CoverMorph {
    val morph = remember { CoverMorph() }
    val target = VideoFraming.coverAspect(framing, morph.videoAspect(video))
    morph.animated = animateFloatAsState(target, VisageMotion.glide(), label = "coverMorphAspect")
    return morph
}

/**
 * Sizes the slot to full available width with height = width / aspect (aspect read lazily in the
 * layout phase, so the spring doesn't recompose). Centred in the parent by the caller.
 */
fun Modifier.morphAspect(aspect: () -> Float): Modifier = layout { measurable, c ->
    val a = aspect().coerceAtLeast(0.2f)
    val w = if (c.hasBoundedWidth) c.maxWidth else c.minWidth
    var h = (w / a).roundToInt()
    var ww = w
    if (c.hasBoundedHeight && h > c.maxHeight) { h = c.maxHeight; ww = (h * a).roundToInt() }
    val p = measurable.measure(Constraints.fixed(ww, h))
    layout(ww, h) { p.place(0, 0) }
}

/** Rounded clip whose radius is animated by [radius] (read in the draw phase). */
fun Modifier.morphClip(radius: () -> androidx.compose.ui.unit.Dp): Modifier = graphicsLayer {
    shape = androidx.compose.foundation.shape.RoundedCornerShape(radius())
    clip = true
}
