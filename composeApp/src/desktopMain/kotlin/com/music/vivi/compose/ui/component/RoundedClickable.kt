package com.music.vivi.compose.ui.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Default pill / card interaction radius used across desktop chrome. */
val InteractionCornerRadius = 12.dp

/**
 * Clickable surface whose hover/press indication is clipped to [shape].
 *
 * Prefer this (or `Surface(onClick=…, shape=…)`) over bare `Modifier.clickable` —
 * unshaped clickable draws a hard rectangle that ignores rounded artwork/cards.
 */
@Composable
fun RoundedClickable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(InteractionCornerRadius),
    color: Color = Color.Transparent,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = color,
        modifier = modifier,
    ) {
        content()
    }
}
