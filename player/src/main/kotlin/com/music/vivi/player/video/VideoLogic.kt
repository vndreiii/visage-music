package com.music.vivi.player.video

import com.music.innertube.VideoStreamCandidate
import kotlin.math.abs

/** Highest video height the user allows (data/CPU cap). [AUTO] picks the cap from measured download speed. */
enum class VideoQuality(val height: Int, val label: String) {
    AUTO(1080, "Auto"), P360(360, "360p"), P480(480, "480p"), P720(720, "720p"), P1080(1080, "1080p");

    companion object {
        fun parse(name: String?): VideoQuality = entries.firstOrNull { it.name == name } ?: AUTO

        /** Highest fixed quality a connection of [mbps] can stream with headroom. */
        fun forMbps(mbps: Double): VideoQuality = when {
            mbps < 2.0 -> P360
            mbps < 4.5 -> P480
            mbps < 9.0 -> P720
            else -> P1080
        }
    }
}

/** What fills the cover slot: music video, else animated canvas, else the plain cover. */
enum class VisualSource { VIDEO, CANVAS, COVER }

object VisualChain {
    fun resolve(videoEnabled: Boolean, videoAvailable: Boolean, canvasAvailable: Boolean): VisualSource = when {
        videoEnabled && videoAvailable -> VisualSource.VIDEO
        canvasAvailable -> VisualSource.CANVAS
        else -> VisualSource.COVER
    }

    /** Local/offline tracks have no YouTube video. */
    fun hasVideoCandidate(videoId: String?, localPath: String?): Boolean =
        videoId != null && localPath == null && !videoId.startsWith("local:") && !videoId.startsWith("offline:")
}

/** Pick the stream to hand mpv: best height under the cap, h264/VP9 over AV1, smaller if nothing fits. */
fun pickVideoCandidate(candidates: List<VideoStreamCandidate>, cap: VideoQuality): VideoStreamCandidate? {
    if (candidates.isEmpty()) return null
    fun rank(c: VideoStreamCandidate): Int {
        val codec = c.codec.orEmpty().lowercase()
        return when {
            codec.startsWith("avc") -> 0
            codec.startsWith("vp9") || codec.startsWith("vp09") -> 1
            codec.startsWith("av01") -> 3
            else -> 2
        }
    }
    val fitting = candidates.filter { it.height <= cap.height }
    val pool = fitting.ifEmpty { listOf(candidates.minBy { it.height }) }
    val bestHeight = pool.maxOf { it.height }
    return pool.filter { it.height == bestHeight }.minBy { rank(it) }
}

/** Corrective action for the muted video renderer relative to the audio clock. */
sealed interface SyncAction {
    data object None : SyncAction
    data class Seek(val toSec: Double) : SyncAction
}

data class SyncDecision(val action: SyncAction, val videoPaused: Boolean)

object VideoSync {
    const val DRIFT_SEC = 0.25
    /** While paused, only correct larger gaps so a scrub shows the right frame. */
    const val PAUSED_DRIFT_SEC = 0.5

    fun decide(audioSec: Double, videoSec: Double?, audioPlaying: Boolean, durationSec: Double = 0.0): SyncDecision {
        val paused = !audioPlaying
        if (videoSec == null) return SyncDecision(SyncAction.None, paused)
        val limit = if (audioPlaying) DRIFT_SEC else PAUSED_DRIFT_SEC
        var target = audioSec.coerceAtLeast(0.0)
        if (durationSec > 0.5) target = target.coerceAtMost(durationSec - 0.1)
        val action = if (abs(videoSec - audioSec) > limit) SyncAction.Seek(target) else SyncAction.None
        return SyncDecision(action, paused)
    }
}
