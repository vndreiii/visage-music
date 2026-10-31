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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.isSecondaryPressed
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
import com.music.vivi.player.toQueueTrack
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** What the carried card shows, plus what it stands for (a [QueueTrack], a library entry, ...). */
class DragCard(val title: String, val subtitle: String, val art: String?, val payload: Any)

/** Somewhere a carried card can be dropped, e.g. a playlist or folder in the library sidebar. */
class TrackDropTarget(
    val key: String,
    val coords: LayoutCoordinates,
    /** Targets that don't handle this kind of payload are ignored entirely (no hover, no red). */
    val handles: (Any) -> Boolean,
    /** Whether this target can take it (e.g. a local file cannot go into a YouTube playlist); otherwise it turns red. */
    val accepts: (Any) -> Boolean,
    val accept: suspend (Any) -> String?,
)

/**
 * App-wide "carrying a track" state. The queue starts it when a row is dragged out sideways; [TrackDragOverlay]
 * draws the carried card (dangling from the pointer) above everything; drop targets register with [trackDropTarget].
 */
object TrackDrag {
    var card by mutableStateOf<DragCard?>(null)
        private set
    val track: QueueTrack? get() = card?.payload as? QueueTrack
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

    /**
     * Set while the pointer is down on something that can be carried: when it went down (uptime ms) and how long it
     * must be held. A shelf that pans on mouse drag checks it, so holding a song picks it up and does not pan.
     */
    var pressDownAt = -1L
    var pressHoldMs = 0L
    /** The press that is going on is a hold that will pick the item up rather than pan whatever scrolls under it. */
    fun holdWinsAt(uptimeMs: Long): Boolean = pressDownAt >= 0 && uptimeMs - pressDownAt >= pressHoldMs

    private val targets = HashMap<String, TrackDropTarget>()

    fun register(t: TrackDropTarget) { targets[t.key] = t }
    fun unregister(key: String) { targets.remove(key) }

    val carrying get() = card != null && !releasing

    fun begin(t: QueueTrack, at: Offset) = begin(DragCard(t.title.ifBlank { t.videoId }, t.artist.ifBlank { "Queued track" }, t.artworkUrl, t), at)

    fun begin(c: DragCard, at: Offset) {
        card = c; pointer = at; releasing = false; landingAt = null; hoverKey = null; hoverRejected = false
    }

    fun moveTo(p: Offset) {
        val c = card ?: return
        if (releasing) return
        pointer = p
        val h = hit(p, c.payload)
        hoverKey = h?.key
        hoverRejected = h != null && !h.accepts(c.payload)
    }

    private fun hit(p: Offset, payload: Any): TrackDropTarget? =
        targets.values.firstOrNull { it.handles(payload) && it.coords.isAttached && it.coords.boundsInWindow().contains(p) }

    /** Gives the card back (pointer re-entered the queue). */
    fun cancel() { card = null; hoverKey = null; hoverRejected = false; releasing = false; landingAt = null }

    /** Lets go: drops on the hovered target if any, otherwise the card just fades. Returns when the card is gone. */
    suspend fun release() {
        val c = card ?: return
        val target = hit(pointer, c.payload)
        releasing = true
        if (target != null) {
            // A refused drop does not fly in; the toast says why.
            if (target.accepts(c.payload)) landingAt = target.coords.boundsInWindow().center
            message = runCatching { target.accept(c.payload) }.getOrElse { it.message ?: "Couldn't add" }
        }
        // The overlay animates itself out; it clears the state through [finishRelease].
    }

    fun finishRelease() { card = null; hoverKey = null; hoverRejected = false; releasing = false; landingAt = null }
}

/** Marks this layout as a drop target for carried payloads of type [T]. [accept] returns the toast text. */
@Composable
inline fun <reified T : Any> Modifier.dropTarget(
    key: String,
    noinline accepts: (T) -> Boolean = { true },
    noinline accept: suspend (T) -> String?,
): Modifier = dropTargetFor(key, { it is T }, { (it as? T)?.let(accepts) ?: false }, { (it as? T)?.let { v -> accept(v) } })

@Composable
fun Modifier.dropTargetFor(
    key: String,
    handles: (Any) -> Boolean,
    accepts: (Any) -> Boolean,
    accept: suspend (Any) -> String?,
): Modifier {
    DisposableEffect(key) { onDispose { TrackDrag.unregister(key) } }
    val latest by androidx.compose.runtime.rememberUpdatedState(accept)
    val latestAccepts by androidx.compose.runtime.rememberUpdatedState(accepts)
    val latestHandles by androidx.compose.runtime.rememberUpdatedState(handles)
    return onGloballyPositioned {
        TrackDrag.register(TrackDropTarget(key, it, { p -> latestHandles(p) }, { p -> latestAccepts(p) }) { p -> latest(p) })
    }
}

/** Marks this layout as a drop target for carried queue tracks. */
@Composable
fun Modifier.trackDropTarget(key: String, accepts: (QueueTrack) -> Boolean = { true }, accept: suspend (QueueTrack) -> String?): Modifier =
    dropTarget<QueueTrack>(key, accepts, accept)

/**
 * Lets this element be carried: once the pointer moves a few dp with the button down, [card] is picked up and hangs
 * from the pointer until release. Return null from [card] to opt out (e.g. pinned entries).
 */
@Composable
fun Modifier.carryOnDrag(holdMs: Long = 0L, card: () -> DragCard?): Modifier {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val latest by androidx.compose.runtime.rememberUpdatedState(card)
    return onGloballyPositioned { origin = it.positionInWindow() }.pointerInput(Unit) {
        val slop = 8.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            if (currentEvent.buttons.isSecondaryPressed || TrackDrag.card != null) return@awaitEachGesture
            val c = latest() ?: return@awaitEachGesture
            TrackDrag.pressDownAt = down.uptimeMillis; TrackDrag.pressHoldMs = holdMs
            while (true) {
                val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: run { TrackDrag.pressDownAt = -1L; return@awaitEachGesture }
                if (!ch.pressed) { TrackDrag.pressDownAt = -1L; return@awaitEachGesture }
                if ((ch.position - down.position).getDistance() > slop) {
                    // A quick swipe or a jittery click is not a pickup: the item must be held first.
                    if (ch.uptimeMillis - down.uptimeMillis < holdMs) { TrackDrag.pressDownAt = -1L; return@awaitEachGesture }
                    break
                }
            }
            TrackDrag.pressDownAt = -1L
            TrackDrag.begin(c, origin + down.position)
            while (true) {
                val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                ch.consume()
                if (!ch.pressed) break
                TrackDrag.moveTo(origin + ch.position)
            }
            scope.launch { TrackDrag.release() }
        }
    }
}

/**
 * Draws the carried card in the window. It hangs from the pointer like on a string: position follows through a
 * spring (so it lags and overshoots), and it swings by the sideways velocity.
 */
@Composable
fun TrackDragOverlay(modifier: Modifier = Modifier) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        val card = TrackDrag.card
        if (card != null) {
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
                    .background(if (TrackDrag.hoverRejected) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(14.dp)),
            ) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))) {
                        CoverImage(card.art, card.title, modifier = Modifier.fillMaxSize())
                    }
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(card.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(card.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        VisageToast(TrackDrag.message, onDismiss = { TrackDrag.message = null })
    }
}


/** What carrying a song from a page card or row shows; its payload is a [QueueTrack], so every track drop target takes it. */
fun com.music.innertube.models.YTItem.dragCard(): DragCard? {
    val song = this as? com.music.innertube.models.SongItem ?: return null
    val q = song.toQueueTrack()
    return DragCard(q.title.ifBlank { q.videoId }, q.artist, q.artworkUrl, q)
}

/** Hold a song for a moment, then drag it out to a drop target (the library sidebar, Liked Songs, Downloaded, ...). */
@Composable
fun Modifier.carrySong(item: com.music.innertube.models.YTItem): Modifier =
    if (item is com.music.innertube.models.SongItem) carryOnDrag(holdMs = 220L) { item.dragCard() } else this
