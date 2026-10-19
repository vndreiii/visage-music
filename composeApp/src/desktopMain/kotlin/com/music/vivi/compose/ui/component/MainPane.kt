package com.music.vivi.compose.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import kotlin.math.roundToInt

/**
 * Bounds (window coordinates) of the main content pane: the area between the sidebars, below the
 * top bar, including the mini player. Toasts and transient overlays centre on this rather than the
 * whole window so they stay centred on the page when the queue / library sidebars are open.
 */
object MainPane {
    var bounds by mutableStateOf<Rect?>(null)
        private set

    fun update(rect: Rect) { if (bounds != rect) bounds = rect }
}

/** Records this layout's window bounds as the main pane. Apply once, in AppScaffold. */
fun Modifier.trackMainPane(): Modifier = onGloballyPositioned { MainPane.update(it.boundsInWindow()) }

/**
 * Lays an overlay out at the main pane's bounds (falls back to the incoming constraints before the
 * pane is known). Use inside a window-sized, origin-anchored Box.
 */
fun Modifier.overMainPane(): Modifier = layout { measurable, constraints ->
    val r = MainPane.bounds
    if (r == null) {
        val p = measurable.measure(constraints)
        layout(p.width, p.height) { p.place(0, 0) }
    } else {
        val w = r.width.roundToInt().coerceIn(constraints.minWidth, constraints.maxWidth)
        val h = r.height.roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)
        val p = measurable.measure(Constraints.fixed(w, h))
        layout(constraints.maxWidth, constraints.maxHeight) { p.place(r.left.roundToInt(), r.top.roundToInt()) }
    }
}

/**
 * Popup placement for toasts: horizontally centred on the main pane, either [bottomGapPx] above the
 * pane's bottom edge, or (when [belowAnchor]) [gapPx] under the popup's anchor, clamped to the window.
 */
class MainPanePopupPositionProvider(
    private val pane: Rect?,
    private val bottomGapPx: Int = 0,
    private val belowAnchor: Boolean = false,
    private val gapPx: Int = 0,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val paneCenter = pane?.center?.x ?: anchorBounds.center.x.toFloat()
        val x = (paneCenter - popupContentSize.width / 2f).roundToInt()
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val y = if (belowAnchor) {
            anchorBounds.bottom + gapPx
        } else {
            val bottom = pane?.bottom?.roundToInt() ?: windowSize.height
            bottom - bottomGapPx - popupContentSize.height
        }.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0))
        return IntOffset(x, y)
    }
}

/** Remembers a [MainPanePopupPositionProvider] that follows the current pane bounds. */
@Composable
fun rememberToastPositionProvider(bottomGapPx: Int = 0, belowAnchor: Boolean = false, gapPx: Int = 0): PopupPositionProvider {
    val pane = MainPane.bounds
    return remember(pane, bottomGapPx, belowAnchor, gapPx) {
        MainPanePopupPositionProvider(pane, bottomGapPx, belowAnchor, gapPx)
    }
}
