package com.music.vivi.compose.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.ui.motion.VisageMotion
import kotlin.math.roundToInt

/**
 * One piece of background work shown by [BackgroundTaskFab]: library scanning, tag writing, auto-tagging, downloads...
 * [total] <= 0 means indeterminate. [attention] marks finished work that waits for the user (matches to review).
 */
class BackgroundTask(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val done: Int = 0,
    val total: Int = 0,
    val attention: Boolean = false,
    val onOpen: () -> Unit,
)

/** Anything can report work here; [BackgroundTaskFab] shows it. Remove it with [finish] when the work ends. */
object BackgroundTasks {
    private val reported = mutableStateMapOf<String, BackgroundTask>()
    val all: List<BackgroundTask> get() = reported.values.toList()
    fun report(task: BackgroundTask) { reported[task.id] = task }
    fun finish(id: String) { reported.remove(id) }
}

private const val FabDx = "backgroundFabDx"
private const val FabDy = "backgroundFabDy"

/**
 * The floating progress button for background operations, shared by scanning, tagging and anything reported through
 * [BackgroundTasks]. Hold and drag it anywhere (so it can be moved off whatever it covers); it remembers where it was
 * left. [tasks] are the work to show; with several running the first is shown with a "+N" count. Click opens it.
 * Penpot: component `BackgroundTaskFab` (56 dp ring + icon + label, r20, primaryContainer / tertiaryContainer).
 */
@Composable
fun BackgroundTaskFab(
    tasks: List<BackgroundTask>,
    prefs: DesktopPrefs,
    startInset: androidx.compose.ui.unit.Dp,
    bottomInset: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val all = tasks + BackgroundTasks.all
    val shown = all.firstOrNull { !it.attention } ?: all.firstOrNull()
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val maxWpx = with(density) { maxWidth.toPx() }
        val maxHpx = with(density) { maxHeight.toPx() }
        val startPx = with(density) { startInset.toPx() }
        val bottomPx = with(density) { bottomInset.toPx() }
        var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
        // Offset from the default bottom-start spot, in dp so it survives a change of display scale.
        var dx by remember { mutableFloatStateOf(prefs.getFloat(FabDx, 0f)) }
        var dy by remember { mutableFloatStateOf(prefs.getFloat(FabDy, 0f)) }
        val dxPx = dx * density.density
        val dyPx = dy * density.density
        // Keep it on screen whatever the window did since it was placed.
        val minX = -startPx
        val maxX = (maxWpx - startPx - size.width).coerceAtLeast(minX)
        val maxY = bottomPx
        val minY = -(maxHpx - bottomPx - size.height).coerceAtLeast(0f)
        val x = dxPx.coerceIn(minX, maxX)
        val y = dyPx.coerceIn(minY, maxY)

        AnimatedVisibility(
            visible = shown != null,
            enter = scaleIn(VisageMotion.spatialDefault(), initialScale = 0.6f) + fadeIn(),
            exit = scaleOut(VisageMotion.spatialDefault(), targetScale = 0.6f) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = startInset, bottom = bottomInset)
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .onSizeChanged { size = it }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = { prefs.setFloat(FabDx, dx); prefs.setFloat(FabDy, dy) },
                        onDragCancel = { prefs.setFloat(FabDx, dx); prefs.setFloat(FabDy, dy) },
                    ) { change, drag ->
                        change.consume()
                        dx = ((x + drag.x).coerceIn(minX, maxX)) / density.density
                        dy = ((y + drag.y).coerceIn(minY, maxY)) / density.density
                    }
                },
        ) {
            val task = shown ?: return@AnimatedVisibility
            val running = !task.attention
            val motion = rememberInfiniteTransition(label = "bgFab")
            val pulse by motion.animateFloat(1f, 1.12f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
            Surface(
                onClick = task.onOpen,
                shape = RoundedCornerShape(20.dp),
                color = if (task.attention) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                contentColor = if (task.attention) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
            ) {
                Row(Modifier.padding(start = 6.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                        if (running) {
                            if (task.total > 0) CircularProgressIndicator(progress = { (task.done.toFloat() / task.total).coerceIn(0f, 1f) }, Modifier.size(44.dp), strokeWidth = 3.dp)
                            else CircularProgressIndicator(Modifier.size(44.dp), strokeWidth = 3.dp)
                            Icon(task.icon, task.label, Modifier.size(22.dp).scale(pulse))
                        } else {
                            Icon(Icons.Outlined.TaskAlt, task.label, Modifier.size(26.dp))
                        }
                    }
                    val more = all.size - 1
                    Text(
                        task.label + if (more > 0) "  +$more" else "",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(end = 14.dp),
                    )
                }
            }
        }
    }
}
