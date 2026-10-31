package com.music.vivi.compose.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp

/**
 * Foreground ink for player text and icons.
 *
 * Artwork-driven backdrops are dark, so the ink is white and translucent variants are fine.
 * A solid (theme-surface) backdrop uses an opaque ink picked to reach 7:1 contrast against the
 * actual backdrop, so alpha is dropped for text: translucent text would lose the contrast.
 */
data class PlayerInk(val primary: Color, val solid: Boolean) {
    /** Text / icon colour. Opaque on solid backdrops; [alpha] only applies to artwork backdrops. */
    fun text(alpha: Float = 1f): Color = if (solid) primary else primary.copy(alpha = alpha)

    /** Translucent fill or container tinted with the ink (keeps its alpha on every backdrop). */
    fun fill(alpha: Float): Color = primary.copy(alpha = alpha)

    /** Opposite ink, for content drawn on top of a filled [primary] shape (e.g. an active button). */
    val inverse: Color get() = if (primary.luminance() > 0.5f) Color.Black else Color.White
}

val LocalPlayerInk = compositionLocalOf { PlayerInk(Color.White, solid = false) }

/**
 * Provides [LocalPlayerInk] for a player surface. [backdrop] is the opaque colour behind the text
 * (before the darkness overlay); [darkness] is the 0..1 overlay (black on dark themes, white on light ones) drawn on top of it.
 */
@Composable
fun ProvidePlayerInk(
    solid: Boolean,
    backdrop: Color,
    darkness: Float,
    /** Controls sit in the dark framed card (see playerControlsSurface), so their ink is white even on a light theme. */
    framedControls: Boolean = false,
    content: @Composable () -> Unit,
) {
    val light = isLightTheme()
    val wash = if (light) Color.White else Color.Black
    val ink = if (framedControls && light) {
        PlayerInk(Color.White, solid = false)
    } else if (!solid) {
        // Artwork backdrops are dark, so white ink, until a light theme washes them toward white.
        PlayerInk(if (light && darkness > 0.45f) Color.Black else Color.White, solid = false)
    } else {
        val effective = lerp(backdrop, wash, darkness.coerceIn(0f, 1f))
        PlayerInk(bestInkOn(effective, MaterialTheme.colorScheme.onSurface), solid = true)
    }
    CompositionLocalProvider(LocalPlayerInk provides ink, content = content)
}
