package com.music.vivi.compose.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

/** WCAG relative-luminance contrast ratio between two opaque colors. */
fun contrastRatio(a: Color, b: Color): Float {
    val l1 = a.luminance()
    val l2 = b.luminance()
    val lighter = max(l1, l2)
    val darker = min(l1, l2)
    return (lighter + 0.05f) / (darker + 0.05f)
}

/** Black or white that maximizes contrast against [background]. */
fun contrastingOn(background: Color): Color =
    if (background.luminance() > 0.4f) Color.Black else Color.White

/**
 * Returns [preferredOn] when it already meets [minRatio] against [background];
 * otherwise falls back to black/white.
 */
fun ensureContrastingOn(
    background: Color,
    preferredOn: Color,
    minRatio: Float = 4.5f,
): Color =
    if (contrastRatio(background, preferredOn) >= minRatio) preferredOn
    else contrastingOn(background)
