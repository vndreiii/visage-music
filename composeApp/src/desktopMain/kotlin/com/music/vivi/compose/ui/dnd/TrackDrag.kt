package com.music.vivi.compose.ui.dnd

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.music.vivi.compose.ui.component.CoverImage
import com.music.vivi.compose.ui.component.VisageToast
import com.music.vivi.player.QueueTrack
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Somewhere a carried track can be dropped, e.g. a playlist row in the library sidebar. */
class TrackDropTarget(
    val key: String,
    val coords: LayoutCoordinates,
    /** Whether this target can take the track at all (e.g. a local file cannot go into a YouTube playlist). */
    val accepts: (QueueTrack) -> Boolean,
    val accept: suspend (QueueTrack) -> String?,
)

/**
 * App-wide "carrying a track" state. The queue starts it when a row is dragged out sideways; [TrackDragOverlay]
 * draws the carried card (dangling from the pointer) above everything; drop targets register with [trackDropTarget].
 */
object TrackDrag {
    var track by mutableStateOf<QueueTrack?>(null)
        private set
    /** Pointer in window coordinates. */
    var pointer by mutableStateOf(Offset.Zero)
    var hoverKey by mutableStateOf<String?>(null)
        private set
    /** The hovered target would refuse this track: shown as a red tint with no growth, and the drop explains why. */
    var hoverRejected by mutableStateOf(false)
        private set
    /** Set once released: the card flies to this point (a target's centre) and shrinks away. */
    var landingAt by mutableStateOf<Offset?>(null)
        private set
    var releasing by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    private val targets = HashMap<String, TrackDropTarget>()

    fun register(t: TrackDropTarget) { targets[t.key] = t }
    fun unregister(key: String) { targets.remove(key) }

    val carrying get() = track != null && !releasing

    fun begin(t: QueueTrack, at: Offset) {
        track = t; pointer = at; releasing = false; landingAt = null; hoverKey = null
    }

    fun moveTo(p: Offset) {
        if (track == null || releasing) return
        pointer = p
        val h = hit(p)
        hoverKey = h?.key
        hoverRejected = h != null && !h.accepts(track!!)
    }

    private fun hit(p: Offset): TrackDropTarget? =
        targets.values.firstOrNull { it.coords.isAttached && it.coords.boundsInWindow().contains(p) }

    /** Gives the card back (pointer re-entered the queue). */
    fun cancel() { track = null; hoverKey = null; hoverRejected = false; releasing = false; landingAt = null }

    /** Lets go: drops on the hovered target if any, otherwise the card just fades. Returns when the card is gone. */
    suspend fun release() {
        val t = track ?: return
        val target = hit(pointer)
        releasing = true
        if (target != null) {
            // A refused drop does not fly in; the toast says why.
            if (target.accepts(t)) landingAt = target.coords.boundsInWindow().center
            message = runCatching { target.accept(t) }.getOrElse { it.message ?: "Couldn't add" }
        }
        // The overlay animates itself out; it clears the state through [finishRelease].
    }

    fun finishRelease() { track = null; hoverKey = null; hoverRejected = false; releasing = false; landingAt = null }
}

/** Marks this layout as a drop target for carried tracks. [accept] returns the toast text (null = nothing to say). */
@Composable
fun Modifier.trackDropTarget(key: String, accepts: (QueueTrack) -> Boolean = { true }, accept: suspend (QueueTrack) -> String?): Modifier {
    DisposableEffect(key) { onDispose { TrackDrag.unregister(key) } }
    val latest by androidx.compose.runtime.rememberUpdatedState(accept)
    val latestAccepts by androidx.compose.runtime.rememberUpdatedState(accepts)
    return onGloballyPositioned { TrackDrag.register(TrackDropTarget(key, it, { t -> latestAccepts(t) }) { t -> latest(t) }) }
}

/**
 * Draws the carried card in the window. It hangs from the pointer like on a string: position follows through a
 * spring (so it lags and overshoots), and it swings by the sideways velocity.
 */
@Composable
fun TrackDragOverlay(modifier: Modifier = Modifier) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        val track = TrackDrag.track
        if (track != null) {
            var pos by remember { mutableStateOf(TrackDrag.pointer) }
            var vel by remember { mutableStateOf(Offset.Zero) }
            var angle by remember { mutableFloatStateOf(0f) }
            var angleVel by remember { mutableFloatStateOf(0f) }
            val fade = remember { Animatable(1f) }
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            LaunchedEffect(Unit) {
                var last = 0L
                while (true) {
                    val now = androidx.compose.runtime.withFrameNanos { it }
                    if (last == 0L) { last = now; continue }
                    val dt = ((now - last) / 1e9f).coerceIn(0.001f, 0.032f)
                    last = now
                    val target = TrackDrag.landingAt ?: TrackDrag.pointer
                    // Underdamped spring toward the pointer.
                    val k = if (TrackDrag.landingAt != null) 700f else 420f
                    val c = 2f * sqrt(k) * (if (TrackDrag.landingAt != null) 0.9f else 0.52f)
                    val a = (target - pos) * k - vel * c
                    vel += a * dt
                    pos += vel * dt
                    // Pendulum: pushed against its sideways velocity, pulled back by a spring with some damping.
                    val push = -vel.x * 0.035f
                    val aa = (push - angle) * 140f - angleVel * 9f
                    angleVel += aa * dt
                    angle = (angle + angleVel * dt).coerceIn(-28f, 28f)
                }
            }
            LaunchedEffect(TrackDrag.releasing) {
                if (!TrackDrag.releasing) return@LaunchedEffect
                // Give the fly-to-target a moment, then shrink away.
                fade.animateTo(0f, androidx.compose.animation.core.tween(if (TrackDrag.landingAt != null) 260 else 180, delayMillis = if (TrackDrag.landingAt != null) 120 else 0))
                TrackDrag.finishRelease()
            }
            val over = TrackDrag.hoverKey != null
            val shrink by androidx.compose.animation.core.animateFloatAsState(if (over) 0.82f else 1f, label = "dragOver")
            Box(
                Modifier
                    .offset { IntOffset((pos.x - origin.x).roundToInt(), (pos.y - origin.y).roundToInt()) }
                    .width(260.dp)
                    .graphicsLayer {
                        // Hangs from the pointer: pivot at the top centre, centred horizontally on the pointer.
                        translationX = -size.width / 2f
                        translationY = 6.dp.toPx()
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        rotationZ = angle
                        val s = shrink * (0.2f + 0.8f * fade.value)
                        scaleX = s; scaleY = s
                        alpha = 0.96f * fade.value
                        shadowElevation = 18.dp.toPx()
                        shape = RoundedCornerShape(14.dp)
                        clip = false
                    }
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(14.dp)),
            ) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))) {
                        CoverImage(track.artworkUrl, track.title, modifier = Modifier.fillMaxSize())
                    }
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(track.title.ifBlank { track.videoId }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(track.artist.ifBlank { "Queued track" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        VisageToast(TrackDrag.message, onDismiss = { TrackDrag.message = null })
    }
}
