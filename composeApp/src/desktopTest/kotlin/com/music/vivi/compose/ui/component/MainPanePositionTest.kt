package com.music.vivi.compose.ui.component

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals

class MainPanePositionTest {
    private val window = IntSize(1200, 800)
    private val anchor = IntRect(0, 0, 1200, 800)

    @Test fun centresOnPaneNotWindow() {
        val pane = Rect(300f, 50f, 900f, 750f) // sidebars eat 300px each side
        val p = MainPanePopupPositionProvider(pane, bottomGapPx = 100)
            .calculatePosition(anchor, window, LayoutDirection.Ltr, IntSize(200, 40))
        assertEquals(IntOffset(500, 750 - 100 - 40), p)
    }

    @Test fun fallsBackToAnchorAndClamps() {
        val p = MainPanePositionTest().let {
            MainPanePopupPositionProvider(null, belowAnchor = true, gapPx = 12)
                .calculatePosition(IntRect(0, 700, 100, 790), window, LayoutDirection.Ltr, IntSize(300, 40))
        }
        assertEquals(0, p.x)
        assertEquals(760, p.y)
    }
}
