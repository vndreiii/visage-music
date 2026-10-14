package com.music.vivi.compose.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Desktop tactility: press scale 0.98, hover brightness via slight scale-up.
 */
fun Modifier.interactiveScale(
    pressedScale: Float = 0.98f,
    hoveredScale: Float = 1.02f,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> pressedScale
            hovered -> hoveredScale
            else -> 1f
        },
        animationSpec = if (pressed) com.music.vivi.compose.ui.motion.ViviMotion.pressDown() else com.music.vivi.compose.ui.motion.ViviMotion.press(),
        label = "interactiveScale",
    )
    this
        .hoverable(interaction)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
}
