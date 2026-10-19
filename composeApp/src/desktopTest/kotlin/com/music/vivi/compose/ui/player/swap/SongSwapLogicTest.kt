package com.music.vivi.compose.ui.player.swap

import com.music.vivi.compose.prefs.PrefReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SongSwapLogicTest {
    private class MapPrefs(val m: MutableMap<String, String> = mutableMapOf()) : PrefReader {
        override fun getString(key: String, default: String) = m[key] ?: default
        override fun getBoolean(key: String, default: Boolean) = m[key]?.toBooleanStrictOrNull() ?: default
        override fun getInt(key: String, default: Int) = m[key]?.toIntOrNull() ?: default
    }

    private val q = listOf("a", "b", "c", "d", "e")

    @Test
    fun nextAndPrevious() {
        assertEquals(SwapDirection.NEXT, SwapDirection.detect("b", q, 1, "c", q, 2))
        assertEquals(SwapDirection.PREVIOUS, SwapDirection.detect("b", q, 1, "a", q, 0))
    }

    @Test
    fun jumpsKeepTheirDirection() {
        assertEquals(SwapDirection.JUMP_FORWARD, SwapDirection.detect("a", q, 0, "d", q, 3))
        assertEquals(SwapDirection.JUMP_BACK, SwapDirection.detect("e", q, 4, "b", q, 1))
        assertTrue(SwapDirection.JUMP_BACK.isJump && !SwapDirection.JUMP_BACK.forward)
    }

    @Test
    fun sameTrackOrNoTrackIsNotASwap() {
        assertNull(SwapDirection.detect("a", q, 0, "a", q, 0))
        assertNull(SwapDirection.detect("a", q, 0, null, emptyList(), -1))
    }

    @Test
    fun firstTrackAndRebuiltQueueGoForward() {
        assertEquals(SwapDirection.NEXT, SwapDirection.detect(null, emptyList(), -1, "a", q, 0))
        assertEquals(SwapDirection.NEXT, SwapDirection.detect("a", q, 0, "x", listOf("x", "y"), 0))
    }

    @Test
    fun staleIndexFallsBackToIdLookup() {
        // queueIndex lagging behind nowPlaying: ids still decide.
        assertEquals(SwapDirection.NEXT, SwapDirection.detect("b", q, 1, "c", q, 1))
        assertEquals(SwapDirection.PREVIOUS, SwapDirection.detect("c", q, 2, "b", q, 2))
    }

    @Test
    fun rebuiltQueueUsesOldPositionOfNewTrack() {
        val radio = listOf("c", "b", "z")
        assertEquals(SwapDirection.NEXT, SwapDirection.detect("b", q, 1, "c", radio, 0))
        assertEquals(SwapDirection.PREVIOUS, SwapDirection.detect("c", q, 2, "b", radio, 1))
    }

    @Test
    fun defaultsKeepLegacySlide() {
        val c = SongSwapConfig.resolve(MapPrefs())
        assertEquals(SongSwapMode.SLIDE, c.mode)
        assertEquals(1f, c.speed)
        assertEquals(1f, c.intensity)
        assertTrue(c.animateBackground && c.animateControls && c.grayscaleWhileLoading)
        assertFalse(c.mode.engineInPlayer)
    }

    @Test
    fun prefsResolveClampAndFallBack() {
        val p = MapPrefs(mutableMapOf(
            SongSwapKeys.Mode to "CAMERA_PAN",
            SongSwapKeys.Speed to "900",
            SongSwapKeys.Intensity to "1",
            SongSwapKeys.AnimateBackground to "false",
        ))
        val c = SongSwapConfig.resolve(p)
        assertEquals(SongSwapMode.CAMERA_PAN, c.mode)
        assertEquals(2f, c.speed)
        assertEquals(0.25f, c.intensity)
        assertFalse(c.animateBackground)
        assertTrue(c.mode.engineInPlayer)
        p.m[SongSwapKeys.Mode] = "garbage"
        assertEquals(SongSwapMode.SLIDE, SongSwapConfig.resolve(p).mode)
        p.m[SongSwapKeys.Mode] = "NONE"
        assertFalse(SongSwapConfig.resolve(p).mode.driven)
    }
}
