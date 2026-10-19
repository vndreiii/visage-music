package com.music.vivi.compose.prefs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FullscreenSettingsTest {
    private class MapPrefs(val m: MutableMap<String, String> = mutableMapOf()) : PrefReader {
        override fun getString(key: String, default: String) = m[key] ?: default
        override fun getBoolean(key: String, default: Boolean) = m[key]?.toBooleanStrictOrNull() ?: default
        override fun getInt(key: String, default: Int) = m[key]?.toIntOrNull() ?: default
    }

    @Test
    fun followsNowPlayingByDefault() {
        val s = FullscreenSettings.resolve(MapPrefs(mutableMapOf(PrefKeys.PlayerBackgroundStyle to "BLUR")))
        assertEquals(PlayerBackgroundStyle.BLUR, s.backgroundStyle)
        assertTrue(s.canvasEnabled)
        assertEquals(FullscreenLayout.COVER_LEFT, s.layout)
    }

    @Test
    fun overridesApplyAndInvalidValuesFallBack() {
        val p = MapPrefs(mutableMapOf(
            PrefKeys.PlayerBackgroundStyle to "BLUR",
            PrefKeys.EnableCanvas to "true",
            FullscreenKeys.Background to "WAVE",
            FullscreenKeys.CanvasMode to "OFF",
            FullscreenKeys.Layout to "nonsense",
        ))
        val s = FullscreenSettings.resolve(p)
        assertEquals(PlayerBackgroundStyle.WAVE, s.backgroundStyle)
        assertFalse(s.canvasEnabled)
        assertEquals(FullscreenLayout.COVER_LEFT, s.layout)
        p.m[FullscreenKeys.Background] = "garbage"
        assertEquals(PlayerBackgroundStyle.BLUR, FullscreenSettings.resolve(p).backgroundStyle)
    }

    @Test
    fun canvasOnOverridesDisabledNowPlaying() {
        assertTrue(FullscreenSettings.resolveCanvas(FullscreenCanvasMode.ON, false))
        assertFalse(FullscreenSettings.resolveCanvas(FullscreenCanvasMode.FOLLOW, false))
    }

    @Test
    fun numbersAreClamped() {
        val s = FullscreenSettings.resolve(MapPrefs(mutableMapOf(
            FullscreenKeys.CoverScale to "10",
            FullscreenKeys.AutoHideSeconds to "999",
            PrefKeys.PlayPauseHeight to "10",
        )))
        assertEquals(0.5f, s.coverScale)
        assertEquals(30, s.autoHideSeconds)
        assertEquals(48, s.playPauseHeightDp)
    }

    @Test
    fun defaultsMapMatchesUnsetResolution() {
        val fromDefaults = FullscreenSettings.resolve(MapPrefs(FullscreenSettings.defaults.toMutableMap()))
        assertEquals(FullscreenSettings.resolve(MapPrefs()), fromDefaults)
    }
}
