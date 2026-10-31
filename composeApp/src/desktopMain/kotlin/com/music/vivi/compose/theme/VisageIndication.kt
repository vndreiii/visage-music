package com.music.vivi.compose.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlin.math.hypot
import kotlinx.coroutines.launch

/**
 * App-wide hover / focus / press feedback: a flat state layer over the component's whole area (8 % hover, 10 % focus)
 * and the press ripple, a circle growing from the click point and fading on release. It replaces the default ripple,
 * whose desktop hover highlight showed up as a thin strip behind the label. Both are drawn inside the bounds the
 * component draws in, so they stay inside the button's own shape wherever the component clips to one.
 */
class VisageIndication(private val tint: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = LayerNode(interactionSource, tint)
    override fun equals(other: Any?) = other is VisageIndication && other.tint == tint
    override fun hashCode() = tint.hashCode()

    private class LayerNode(private val source: InteractionSource, private val tint: Color) :
        Modifier.Node(), DrawModifierNode {
        private var hovers = 0
        private var focuses = 0
        private var rippleCenter = Offset.Zero
        private val rippleProgress = Animatable(1f)
        private val rippleAlpha = Animatable(0f)

        override fun onAttach() {
            coroutineScope.launch {
                source.interactions.collect { i ->
                    when (i) {
                        is HoverInteraction.Enter -> hovers++
                        is HoverInteraction.Exit -> hovers = (hovers - 1).coerceAtLeast(0)
                        is FocusInteraction.Focus -> focuses++
                        is FocusInteraction.Unfocus -> focuses = (focuses - 1).coerceAtLeast(0)
                        is PressInteraction.Press -> {
                            rippleCenter = i.pressPosition
                            launch {
                                rippleProgress.snapTo(0f)
                                rippleProgress.animateTo(1f, tween(RIPPLE_GROW_MS, easing = FastOutSlowInEasing))
                            }
                            launch { rippleAlpha.snapTo(RIPPLE_ALPHA) }
                        }
                        is PressInteraction.Release, is PressInteraction.Cancel ->
                            launch { rippleAlpha.animateTo(0f, tween(RIPPLE_FADE_MS)) }
                    }
                    invalidateDraw()
                }
            }
        }

        override fun ContentDrawScope.draw() {
            // State layer and ripple go BEHIND the content, so the label is never drawn through a translucent
            // overlay (text anti-aliasing under an overlay showed up as a lighter strip behind the label).
            val state = when {
                focuses > 0 -> 0.10f
                hovers > 0 -> 0.08f
                else -> 0f
            }
            // Everything below is drawn inside the component's own bounds (and so inside its clipped shape).
            if (state > 0f) drawRect(tint.copy(alpha = state))
            val a = rippleAlpha.value
            if (a > 0f) {
                val c = rippleCenter
                val far = maxOf(
                    hypot(c.x, c.y), hypot(size.width - c.x, c.y),
                    hypot(c.x, size.height - c.y), hypot(size.width - c.x, size.height - c.y),
                )
                clipRect {
                    drawCircle(tint.copy(alpha = a), radius = far * rippleProgress.value, center = c)
                }
            }
            drawContent()
        }
    }

    private companion object {
        const val RIPPLE_ALPHA = 0.14f
        const val RIPPLE_GROW_MS = 320
        const val RIPPLE_FADE_MS = 260
    }
}
