package com.music.vivi.compose.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * The background "darkness" setting washes the blurred/artwork backdrop toward a tint. On a dark theme that tint is
 * black (darker). On a light theme black would turn the page muddy under dark text, so it washes toward white
 * instead and the setting reads "Lightness". Follows the effective theme (dark/light/auto and any override).
 */
@Composable
fun isLightTheme(): Boolean = MaterialTheme.colorScheme.surface.luminance() > 0.5f

@Composable
fun backdropTint(): Color = if (isLightTheme()) Color.White else Color.Black

/** "Darkness" on dark themes, "Lightness" on light ones. */
@Composable
fun backdropLevelName(): String = if (isLightTheme()) "Lightness" else "Darkness"

/** Backdrop lightness the page text is tuned for on light themes (see [withAaaContrast]). */
const val LIGHT_BACKDROP_LUMINANCE = 0.6f

/**
 * Smallest white wash (0..1) that lifts the darkest colour of [palette] to [LIGHT_BACKDROP_LUMINANCE], so dark text on
 * the artwork-tinted page keeps AAA contrast whatever the cover looks like. The user's own lightness setting can only
 * go higher than this on light themes.
 */
fun lightWashFor(palette: ArtworkPalette?): Float {
    val colors = (palette?.glowColors.orEmpty() + listOfNotNull(palette?.seed)).ifEmpty { listOf(IdleArtworkPalette.seed) }
    val darkest = colors.minByOrNull { it.luminance() } ?: return 0.5f
    var lo = 0.35f
    var hi = 1f
    if (androidx.compose.ui.graphics.lerp(darkest, Color.White, lo).luminance() >= LIGHT_BACKDROP_LUMINANCE) return lo
    repeat(12) {
        val mid = (lo + hi) / 2f
        if (androidx.compose.ui.graphics.lerp(darkest, Color.White, mid).luminance() >= LIGHT_BACKDROP_LUMINANCE) hi = mid else lo = mid
    }
    return hi
}
