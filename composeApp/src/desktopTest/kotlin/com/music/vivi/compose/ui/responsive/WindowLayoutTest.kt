package com.music.vivi.compose.ui.responsive

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowLayoutTest {
    private fun cls(w: Int, h: Int) = layoutClassOf(w.dp, h.dp)

    @Test fun targetResolutionsMapToTheirClass() {
        assertEquals(LayoutClass.Mobile, cls(360, 560))
        assertEquals(LayoutClass.Mobile, cls(390, 844))
        assertEquals(LayoutClass.Tablet, cls(800, 1280))
        assertEquals(LayoutClass.Desktop, cls(1280, 800)) // tablet landscape
        assertEquals(LayoutClass.Desktop, cls(1024, 768))
        assertEquals(LayoutClass.Desktop, cls(1366, 768))
        assertEquals(LayoutClass.Desktop, cls(1920, 1080))
        assertEquals(LayoutClass.Large, cls(2560, 1440))
        assertEquals(LayoutClass.UltraWide, cls(3440, 1440))
        assertEquals(LayoutClass.UltraWide, cls(2560, 1080))
    }

    @Test fun boundaries() {
        assertEquals(LayoutClass.Mobile, cls(599, 800))
        assertEquals(LayoutClass.Tablet, cls(600, 800))
        assertEquals(LayoutClass.Tablet, cls(899, 800))
        assertEquals(LayoutClass.Desktop, cls(900, 700))
        // wide but short windows are ultrawide only when they are also wide enough
        assertEquals(LayoutClass.Desktop, cls(1500, 500))
        assertEquals(LayoutClass.UltraWide, cls(1900, 800))
    }

    @Test fun helpers() {
        val phone = windowLayoutOf(390.dp, 844.dp)
        assertTrue(phone.isCompactWidth && phone.isMobile && phone.isPortrait)
        assertFalse(phone.isDesktopUp)
        assertFalse(phone.contentMaxWidth.value.isFinite())
        val uw = windowLayoutOf(3440.dp, 1440.dp)
        assertTrue(uw.isExpanded && uw.isUltraWide)
        assertEquals(2200.dp, uw.contentMaxWidth)
        assertTrue(windowLayoutOf(360.dp, 400.dp).isShort)
    }

    @Test fun gridColumns() {
        assertEquals(1, gridColumnsFor(300.dp, 180.dp))
        assertEquals(2, gridColumnsFor(390.dp, 170.dp)) // 2*170 + 12 <= 390
        assertEquals(1, gridColumnsFor(100.dp, 180.dp))
        assertEquals(8, gridColumnsFor(1600.dp, 180.dp))
        assertEquals(1, gridColumnsFor(Float.POSITIVE_INFINITY.dp, 180.dp))
    }
}
