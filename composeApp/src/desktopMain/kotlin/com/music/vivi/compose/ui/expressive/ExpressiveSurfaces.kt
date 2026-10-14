package com.music.vivi.compose.ui.expressive

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.vivi.compose.ui.motion.ViviMotion

/**
 * Clickable card whose corners tighten while pressed and spring back (the Expressive shape morph),
 * with a small press scale. Use for covers, tiles and any tappable container.
 */
@Composable
fun ExpressiveCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    restRadius: Dp = 20.dp,
    pressedRadius: Dp = 12.dp,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    border: BorderStroke? = null,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val radius by animateDpAsState(if (pressed) pressedRadius else restRadius, ViviMotion.spatialFast(), label = "cardRadius")
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, ViviMotion.spatialFast(), label = "cardScale")
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(radius),
        color = color,
        contentColor = contentColor,
        border = border,
        interactionSource = interaction,
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        content = content,
    )
}
