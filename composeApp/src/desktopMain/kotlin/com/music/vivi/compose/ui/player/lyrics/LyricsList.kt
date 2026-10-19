package com.music.vivi.compose.ui.player.lyrics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.lyricsync.LinePhase
import com.music.lyricsync.LyricsRow
import com.music.lyricsync.LyricsTimeline
import com.music.vivi.compose.ui.component.smoothWheelScroll
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * The scrolling lyrics list shared by the real panel and the settings preview.
 *
 * [clock] is the smoothed playback position in ms (per-frame); it is only read inside
 * [derivedStateOf] blocks and draw lambdas.
 */
@Composable
internal fun LyricsList(
    timeline: LyricsTimeline,
    cfg: LyricsConfig,
    clock: State<Float>,
    accent: Color,
    onSeek: ((Long) -> Unit)?,
    modifier: Modifier = Modifier,
    sizeScale: Float = 1f,
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var following by remember { mutableStateOf(true) }
    var lastUserNanos by remember { mutableLongStateOf(0L) }
    var snapNext by remember(timeline) { mutableStateOf(true) }

    val plain = !timeline.isSynced
    val activeRow by remember(timeline) { derivedStateOf { timeline.activeRowIndex(clock.value.toLong()) } }
    val primary by remember(timeline) { derivedStateOf { timeline.primaryIndex(clock.value.toLong()) } }
    val inGap by remember(timeline) { derivedStateOf { timeline.rows.getOrNull(activeRow) is LyricsRow.Gap } }
    val manualScroll = plain || cfg.visibility == LyricsVisibility.FULL
    val edge = animateFloatAsState(if (cfg.fadeEdges) 1f else 0f, spring(1f, 200f), label = "lyricEdge")

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .pointerInput(manualScroll) {
                if (!manualScroll) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        val user = e.type == PointerEventType.Scroll ||
                            (e.type == PointerEventType.Move && e.changes.any { it.pressed && it.positionChanged() })
                        if (user && !plain) {
                            following = false
                            lastUserNanos = System.nanoTime()
                        }
                    }
                }
            },
    ) {
        val heightPx = with(density) { maxHeight.toPx() }
        val anchorPx = heightPx * cfg.anchor
        val topPad = with(density) { anchorPx.toDp() }
        val bottomPad = with(density) { (heightPx - anchorPx).toDp() }
        val hPad = (28 * sizeScale).dp

        // Auto-follow: spring the active row to the anchor; never jumps except for the first placement / far seeks.
        LaunchedEffect(activeRow, following, anchorPx, timeline) {
            if (!following || activeRow < 0 || plain) return@LaunchedEffect
            scrollRowToAnchor(listState, activeRow, anchorPx, animate = !snapNext)
            snapNext = false
        }
        LaunchedEffect(following, lastUserNanos, cfg.autoResume) {
            if (!following && cfg.autoResume) {
                delay(5_000)
                following = true
            }
        }

        LazyColumn(
            state = listState,
            userScrollEnabled = manualScroll,
            contentPadding = PaddingValues(start = hPad, end = hPad, top = topPad.coerceAtLeast(0.dp), bottom = bottomPad.coerceAtLeast(0.dp)),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val e = edge.value * 0.14f
                    if (e > 0.001f) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Transparent, e to Color.Black, (1f - e) to Color.Black, 1f to Color.Transparent,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                }
                .smoothWheelScroll(listState),
        ) {
            itemsIndexed(timeline.rows, key = { i, row ->
                when (row) {
                    is LyricsRow.Line -> "L${row.index}"
                    is LyricsRow.Gap -> "G${row.startMs}-$i"
                }
            }) { rowIndex, row ->
                val dist = if (activeRow < 0) rowIndex + 1 else abs(rowIndex - activeRow)
                val visible = plain || dist <= cfg.visibility.radius
                when (row) {
                    is LyricsRow.Line -> {
                        val line = timeline.lines[row.index]
                        // derivedStateOf: recompose only when this line's phase flips, never per frame
                        val phase by remember(timeline, row.index, plain) {
                            derivedStateOf {
                                if (plain) LinePhase.Past else {
                                    val ph = timeline.phase(row.index, clock.value.toLong(), primary)
                                    // during an instrumental break the sung line settles back instead of glowing on
                                    if (inGap && ph == LinePhase.Active) LinePhase.Past else ph
                                }
                            }
                        }
                        val seek = line.timeMs
                        LyricLineView(
                            line = line,
                            phase = phase,
                            distance = dist,
                            visible = visible,
                            plain = plain,
                            cfg = cfg,
                            accent = accent,
                            clock = clock,
                            sizeScale = sizeScale,
                            onClick = if (cfg.tapToSeek && seek != null && onSeek != null) {
                                { following = true; onSeek(seek) }
                            } else null,
                            modifier = Modifier.fillMaxWidth().padding(vertical = (cfg.lineSpacingDp * sizeScale / 2f).dp),
                        )
                    }
                    is LyricsRow.Gap -> LyricsGapRow(
                        gap = row,
                        active = rowIndex == activeRow,
                        style = cfg.gapStyle,
                        align = cfg.align,
                        accent = accent,
                        clock = clock,
                        sizeScale = sizeScale,
                        modifier = Modifier.padding(horizontal = (8 * sizeScale).dp),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = !following && !plain,
            enter = fadeIn(spring(1f, 400f)) + scaleIn(spring(0.55f, 420f), initialScale = 0.6f) + slideInVertically(spring(0.7f, 400f)) { it / 2 },
            exit = fadeOut(spring(1f, 500f)) + scaleOut(spring(1f, 500f), targetScale = 0.7f) + slideOutVertically(spring(1f, 500f)) { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = (18 * sizeScale).dp),
        ) {
            Surface(
                onClick = { following = true },
                shape = RoundedCornerShape(percent = 50),
                color = Color.Black.copy(alpha = 0.55f),
                contentColor = Color.White,
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Outlined.MyLocation, contentDescription = null, modifier = Modifier.padding(0.dp))
                    Text("Resume", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private suspend fun scrollRowToAnchor(state: LazyListState, index: Int, anchorPx: Float, animate: Boolean) {
    fun delta(): Float? {
        val info = state.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return null
        return item.offset + item.size / 2f - (info.viewportStartOffset + anchorPx)
    }
    val d0 = if (animate) delta() else null
    if (d0 == null) {
        state.scrollToItem(index)
        val d = delta() ?: return
        if (animate) state.animateScrollBy(d, spring(0.9f, 140f)) else state.scrollBy(d)
        return
    }
    if (abs(d0) > 0.5f) state.animateScrollBy(d0, spring(0.9f, 140f))
}
