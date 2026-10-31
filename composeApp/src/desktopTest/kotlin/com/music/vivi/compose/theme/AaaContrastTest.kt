package com.music.vivi.compose.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertTrue

class AaaContrastTest {
    private val seeds = listOf(0xFF6750A4, 0xFF4DD0E1, 0xFFED5564, 0xFFF2B8D2, 0xFF3DDC84, 0xFFFFB84D).map { Color(it) }

    @Test fun `text roles reach 7 to 1 on every surface in light and dark`() {
        for (dark in listOf(false, true)) for (seed in seeds) {
            val s = visageColorScheme(dark, pureBlack = false, seed = seed).withAaaContrast(dark)
            val surfaces = listOf(s.surface, s.background, s.surfaceContainer, s.surfaceContainerHigh, s.surfaceContainerHighest)
            for (fg in listOf(s.onSurface, s.onSurfaceVariant, s.primary, s.secondary, s.tertiary)) {
                for (bg in surfaces) {
                    val r = contrastRatio(fg, bg)
                    assertTrue(r >= 7f, "dark=$dark seed=$seed $fg on $bg is $r")
                }
            }
            if (!dark) for (fg in listOf(s.onSurface, s.onSurfaceVariant, s.primary, s.secondary)) {
                val r = contrastRatio(fg, Color(0xFFCCCCCC))
                assertTrue(r >= 7f, "seed=$seed $fg on the light page backdrop is $r")
            }
            listOf(
                s.onPrimary to s.primary, s.onSecondary to s.secondary,
                s.onPrimaryContainer to s.primaryContainer, s.onSecondaryContainer to s.secondaryContainer,
            ).forEach { (fg, bg) -> assertTrue(contrastRatio(fg, bg) >= 7f, "dark=$dark seed=$seed pair $fg/$bg") }
        }
    }
}
