package com.music.vivi.compose.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import coil3.compose.AsyncImage
import com.music.vivi.player.PlaybackController
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlin.math.abs

/** Card gestures operate on the real queue, without interrupting playback for previews. */
@Composable
internal fun AlbumGestureCard(
    controller: PlaybackController,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val queue by controller.queueTracks.collectAsState()
    val index by controller.queueIndex.collectAsState()
    val scope = rememberCoroutineScope()
    var displacement by remember { mutableStateOf(Offset.Zero) }
    var grabbed by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf(false) }
    var previous by remember { mutableStateOf(false) }
    var count by remember { mutableIntStateOf(3) }
    var options by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }
    var cardHeight by remember { mutableIntStateOf(0) }
    val toastGap = with(LocalDensity.current) { 12.dp.roundToPx() }
    var toast by remember { mutableStateOf<String?>(null) }
    var lastToast by remember { mutableStateOf("") }
    val toastProgress by animateFloatAsState(if (toast != null) 1f else 0f, tween(200), label = "cardToast")
    var rotationTarget by remember { mutableFloatStateOf(0f) }
    val rotation by animateFloatAsState(rotationTarget, spring(dampingRatio = 0.55f, stiffness = 180f), label = "cardRotation")
    val x by animateFloatAsState(displacement.x, if (grabbed) spring(stiffness = 10000f) else spring(stiffness = 300f))
    val y by animateFloatAsState(displacement.y, if (grabbed) spring(stiffness = 10000f) else spring(stiffness = 300f))
    Box(modifier.onSizeChanged { cardHeight = it.height }) {
        Box(Modifier.fillMaxSize().graphicsLayer { translationX = x; translationY = y; rotationZ = rotation }
            .shadow(28.dp, RoundedCornerShape(28.dp), clip = false)
            .pointerInput(controller) {
                val limit = 100.dp.toPx()
                val commit = 72.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var total = Offset.Zero
                    var lastTurn = down.uptimeMillis
                    var extreme = 0f
                    var direction = 0
                    var reversals = 0
                    var shaken = false
                    var moved = false
                    grabbed = true
                    var holdJob: Job? = scope.launch {
                        delay(550)
                        if (!moved && grabbed) { preview = true; options = true }
                    }
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val held = change.uptimeMillis - down.uptimeMillis
                            if (!change.pressed) {
                                when (cardReleaseAction(held, total.x, commit, moved, shaken)) {
                                    CardReleaseAction.PREVIEW -> preview = true
                                    CardReleaseAction.NEXT -> controller.next()
                                    CardReleaseAction.PREVIOUS -> controller.previous()
                                    CardReleaseAction.NONE -> Unit
                                }
                                break
                            }
                            val delta = change.position - change.previousPosition
                            total += delta
                            if (total.getDistance() > viewConfiguration.touchSlop) {
                                moved = true
                                holdJob?.cancel()
                                holdJob = null
                            }
                            if (moved) change.consume()
                            displacement = Offset(total.x.coerceIn(-limit, limit), total.y.coerceIn(-limit, limit))
                            rotationTarget = (displacement.x / limit * 9f + delta.x * 0.12f).coerceIn(-16f, 16f)
                            val nextDirection = if (delta.x > 0) 1 else if (delta.x < 0) -1 else direction
                            if (nextDirection != direction && abs(total.x - extreme) >= 24.dp.toPx()) {
                                reversals = if (change.uptimeMillis - lastTurn <= 300) reversals + 1 else 0
                                lastTurn = change.uptimeMillis
                                extreme = total.x
                                direction = nextDirection
                            }
                            if (!shaken && reversals >= 4) {
                                controller.toggleShuffle()
                                shaken = true
                                toast = if (controller.shuffleEnabled.value) "Playlist shuffled · Shuffle on" else "Shuffle off"
                            }
                            hint = when {
                                shaken -> null
                                total.x <= -commit -> "Release for next: ${controller.queueTracks.value.getOrNull(controller.queueIndex.value + 1)?.title ?: "end of queue"}"
                                total.x >= commit -> "Release for previous: ${controller.queueTracks.value.getOrNull(controller.queueIndex.value - 1)?.title ?: "start of queue"}"
                                else -> null
                            }
                        }
                    } finally {
                        holdJob?.cancel()
                        grabbed = false
                        rotationTarget = 0f
                        displacement = Offset.Zero
                        hint = null
                    }
                }
            }) { content() }
    if (toast != null || toastProgress > 0.001f) {
        Popup(alignment = Alignment.TopCenter, offset = IntOffset(0, cardHeight + toastGap), properties = PopupProperties(focusable = false)) {
            Surface(modifier = Modifier.graphicsLayer {
                alpha = toastProgress
                translationY = (1f - toastProgress) * -toastGap
                scaleX = 0.96f + 0.04f * toastProgress
                scaleY = scaleX
            }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.inverseSurface, shadowElevation = 6.dp) {
                Text(toast ?: lastToast, Modifier.padding(horizontal = 16.dp, vertical = 10.dp), color = MaterialTheme.colorScheme.inverseOnSurface)
            }
        }
    }
        hint?.let {
            Surface(Modifier.align(Alignment.BottomCenter).padding(16.dp), shape = MaterialTheme.shapes.medium) {
                Text(it, Modifier.padding(12.dp))
            }
        }
    }
    LaunchedEffect(toast) {
        if (toast != null) { lastToast = toast.orEmpty(); delay(2200); toast = null }
    }
    if (preview) {
        AlertDialog(
            onDismissRequest = { preview = false },
            title = { Text("Queue preview") },
            text = {
                Column {
                    Box {
                        TextButton(onClick = { options = true }) { Text("${if (previous) "Previous" else "Next"} $count songs ▾") }
                        DropdownMenu(expanded = options, onDismissRequest = { options = false }) {
                            for (back in listOf(true, false)) for (n in listOf(3, 5)) {
                                DropdownMenuItem(text = { Text("${if (back) "Previous" else "Next"} $n songs") }, onClick = { previous = back; count = n; options = false })
                            }
                        }
                    }
                    val indices = queuePreviewIndices(index, queue.size, previous, count)
                    if (indices.isEmpty()) Text("No ${if (previous) "previous" else "upcoming"} tracks")
                    for (i in indices) {
                        TextButton(onClick = { preview = false; scope.launch { controller.playQueue(queue, i) } }) {
                            AsyncImage(queue[i].artworkUrl, null, Modifier.size(40.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) { Text(queue[i].title); Text(queue[i].artist, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { preview = false }) { Text("Close") } },
        )
    }
}

internal enum class CardReleaseAction { NONE, PREVIEW, NEXT, PREVIOUS }
internal fun cardReleaseAction(heldMs: Long, x: Float, threshold: Float, moved: Boolean, shaken: Boolean): CardReleaseAction = when {
    shaken -> CardReleaseAction.NONE
    !moved && heldMs >= 450 -> CardReleaseAction.PREVIEW
    moved && x <= -threshold -> CardReleaseAction.NEXT
    moved && x >= threshold -> CardReleaseAction.PREVIOUS
    else -> CardReleaseAction.NONE
}
internal fun queuePreviewIndices(index: Int, size: Int, previous: Boolean, count: Int): List<Int> {
    if (index !in 0 until size) return emptyList()
    return (1..count).map { if (previous) index - it else index + it }.filter { it in 0 until size }
}
