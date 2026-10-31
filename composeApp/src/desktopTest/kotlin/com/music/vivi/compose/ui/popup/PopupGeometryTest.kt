package com.music.vivi.compose.ui.popup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PopupGeometryTest {
    @Test fun modeSwitchesOnAspectAndSize() {
        assertEquals(PopupMode.Stack, PopupGeometry.modeFor(340f, 480f))
        assertEquals(PopupMode.Stack, PopupGeometry.modeFor(400f, 400f))
        assertEquals(PopupMode.Stack, PopupGeometry.modeFor(500f, 340f)) // wide but not big enough
        assertEquals(PopupMode.Stack, PopupGeometry.modeFor(700f, 600f)) // big but under 1.3x
        assertEquals(PopupMode.Landscape, PopupGeometry.modeFor(780f, 600f))
        assertEquals(PopupMode.Landscape, PopupGeometry.modeFor(560f, 340f))
        assertEquals(PopupMode.Stack, PopupGeometry.modeFor(560f, 0f))
    }

    @Test fun sizeClamped() {
        assertEquals(280 to 340, PopupGeometry.clampSize(10, 10))
        assertEquals(PopupGeometry.MAX_W to PopupGeometry.MAX_H, PopupGeometry.clampSize(9999, 9999))
    }

    @Test fun boundsRoundTripAndGarbage() {
        val b = PopupBounds(-120, 40, 400, 500)
        assertEquals(b, PopupBounds.decode(b.encode()))
        assertEquals(PopupBounds(null, null, 400, 500), PopupBounds.decode(PopupBounds(null, null, 400, 500).encode()))
        assertEquals(PopupBounds.Default, PopupBounds.decode("nonsense"))
        assertEquals(PopupBounds.Default, PopupBounds.decode(null))
        assertEquals(PopupBounds(1, 2, 280, 340), PopupBounds.decode("1,2,5,5"))
    }

    @Test fun placementDropsOffscreenAndNudgesPartial() {
        val screens = listOf(ScreenRect(0, 0, 1920, 1080), ScreenRect(1920, 0, 1280, 1024))
        assertNull(PopupPlacement.restore(PopupBounds(5000, 100, 340, 480), screens))
        assertNull(PopupPlacement.restore(PopupBounds(null, null, 340, 480), screens))
        assertEquals(100 to 100, PopupPlacement.restore(PopupBounds(100, 100, 340, 480), screens))
        assertEquals(100 to 1080 - 480, PopupPlacement.restore(PopupBounds(100, 900, 340, 480), screens))
        assertEquals(2000 to 50, PopupPlacement.restore(PopupBounds(2000, 50, 340, 480), screens))
        assertNull(PopupPlacement.restore(PopupBounds(10, 10, 340, 480), emptyList()))
    }

    @Test fun displayDetection() {
        fun env(vararg p: Pair<String, String>) = p.toMap().let { m -> { k: String -> m[k] } }
        assertEquals(PopupDisplay.Windows, PopupDisplayDetect.detect("Windows 11", env()))
        assertEquals(PopupDisplay.WaylandSession, PopupDisplayDetect.detect("Linux", env("WAYLAND_DISPLAY" to "wayland-1", "DISPLAY" to ":0")))
        assertEquals(PopupDisplay.X11, PopupDisplayDetect.detect("Linux", env("DISPLAY" to ":0")))
        assertEquals(PopupDisplay.Other, PopupDisplayDetect.detect("Mac OS X", env("DISPLAY" to ":0")))
    }

    @Test fun toolWindowStyleBits() {
        val base = 0x100L or WinExStyle.WS_EX_APPWINDOW
        val tool = WinExStyle.withToolWindow(base, true)
        assertEquals(WinExStyle.WS_EX_TOOLWINDOW, tool and WinExStyle.WS_EX_TOOLWINDOW)
        assertEquals(0L, tool and WinExStyle.WS_EX_APPWINDOW)
        assertEquals(base, WinExStyle.withToolWindow(tool, false))
    }
}
