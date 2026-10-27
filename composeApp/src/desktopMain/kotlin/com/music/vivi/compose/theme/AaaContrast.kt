package com.music.vivi.compose.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** WCAG 2.x level AAA contrast for normal text. */
const val AAA_CONTRAST = 7f

/**
 * Nudges [fg] toward [target] just far enough that it reaches [min] against every colour in [backgrounds], keeping
 * its hue as long as possible. If even [target] cannot reach it (a mid-tone background), [target] is returned.
 */
private fun tune(fg: Color, backgrounds: List<Color>, target: Color, min: Float = AAA_CONTRAST): Color {
    fun ok(c: Color) = backgrounds.all { contrastRatio(c, it) >= min }
    if (ok(fg)) return fg
    if (!ok(target)) return target
    var lo = 0f
    var hi = 1f
    repeat(14) {
        val mid = (lo + hi) / 2f
        if (ok(lerp(fg, target, mid))) hi = mid else lo = mid
    }
    return lerp(fg, target, hi)
}

/** Text colour on a single container: pushed toward whichever of black/white contrasts more. */
private fun onPair(fg: Color, container: Color): Color =
    tune(fg, listOf(container), if (container.luminance() > 0.179f) Color.Black else Color.White)

/**
 * Guarantees WCAG AAA (7:1) for the scheme's text roles, on both light and dark themes:
 *  - on-surface / variant and the accent roles used as link or label text (primary, secondary, tertiary, error)
 *    against every surface and accent container they commonly sit on;
 *  - every on-X colour against its X.
 * Colours that already comply are returned untouched, so a high-contrast palette is not changed.
 */
fun ColorScheme.withAaaContrast(dark: Boolean): ColorScheme {
    val toward = if (dark) Color.White else Color.Black
    val surfaces = listOf(
        surface, background, surfaceContainerLowest, surfaceContainerLow, surfaceContainer,
        surfaceContainerHigh, surfaceContainerHighest,
    )
    // Light themes also put text straight on the washed, artwork-tinted page (not on a surface), whose lightness is
    // pinned at LIGHT_BACKDROP_LUMINANCE by lightWashFor; tune against a grey of that lightness.
    val pageBackdrop = if (dark) emptyList() else listOf(Color(0xFFCCCCCC))
    val withAccents = surfaces + pageBackdrop + listOf(primaryContainer, secondaryContainer, tertiaryContainer)

    val onSurface2 = tune(onSurface, withAccents, toward)
    val onSurfaceVariant2 = tune(onSurfaceVariant, withAccents, toward)
    val primary2 = tune(primary, withAccents, toward)
    val secondary2 = tune(secondary, withAccents, toward)
    val tertiary2 = tune(tertiary, withAccents, toward)
    val error2 = tune(error, surfaces + pageBackdrop, toward)

    return copy(
        onSurface = onSurface2,
        onBackground = tune(onBackground, surfaces, toward),
        onSurfaceVariant = onSurfaceVariant2,
        primary = primary2,
        secondary = secondary2,
        tertiary = tertiary2,
        error = error2,
        onPrimary = onPair(onPrimary, primary2),
        onSecondary = onPair(onSecondary, secondary2),
        onTertiary = onPair(onTertiary, tertiary2),
        onError = onPair(onError, error2),
        onPrimaryContainer = onPair(onPrimaryContainer, primaryContainer),
        onSecondaryContainer = onPair(onSecondaryContainer, secondaryContainer),
        onTertiaryContainer = onPair(onTertiaryContainer, tertiaryContainer),
        onErrorContainer = onPair(onErrorContainer, errorContainer),
        inverseOnSurface = onPair(inverseOnSurface, inverseSurface),
        inversePrimary = tune(inversePrimary, listOf(inverseSurface), if (dark) Color.Black else Color.White),
    )
}
