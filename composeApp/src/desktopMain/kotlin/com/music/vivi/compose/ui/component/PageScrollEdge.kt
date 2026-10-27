package com.music.vivi.compose.ui.component

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Corner radius shared by the top edge of every scrolling page area (matches the artist banner). */
val PageEdgeRadius = 24.dp

/**
 * Standard top edge for a page's scrolling area under a fixed header. Content that scrolls up no longer gets cut by a
 * hard line: the area has rounded top corners and its top [fade] fades out as content moves under it. [progress]
 * (0..1) says how far the content has scrolled, so at rest nothing is faded and the first row shows in full.
 */
fun Modifier.pageScrollEdge(fade: Dp = 36.dp, progress: () -> Float): Modifier =
    this
        .clip(RoundedCornerShape(topStart = PageEdgeRadius, topEnd = PageEdgeRadius))
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            val p = progress().coerceIn(0f, 1f)
            if (p > 0f) {
                val h = fade.toPx().coerceAtMost(size.height)
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 1f - p),
                        1f to Color.Black,
                        startY = 0f,
                        endY = h,
                    ),
                    size = androidx.compose.ui.geometry.Size(size.width, h),
                    blendMode = BlendMode.DstIn,
                )
            }
        }

private const val EdgeRampPx = 48f

/** 0 at the very top, ramping to 1 after a short scroll. */
fun LazyListState.edgeProgress(): Float =
    if (firstVisibleItemIndex > 0) 1f else (firstVisibleItemScrollOffset / EdgeRampPx).coerceIn(0f, 1f)

fun LazyGridState.edgeProgress(): Float =
    if (firstVisibleItemIndex > 0) 1f else (firstVisibleItemScrollOffset / EdgeRampPx).coerceIn(0f, 1f)
