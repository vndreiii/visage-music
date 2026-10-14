package com.music.vivi.compose.ui.expressive

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.vivi.compose.ui.motion.ViviMotion

/**
 * Material 3 **Expressive** buttons. Compose Multiplatform doesn't expose the official Expressive
 * components on desktop yet (they're internal/absent), so these follow the spec directly:
 * five sizes, round/square shapes, shape morph on press and when toggled, and the expressive
 * spatial spring ([ViviMotion.spatialFast]) driving every change.
 */
enum class ExpressiveSize(val height: Dp, val horizontal: Dp, val icon: Dp, val iconGap: Dp, val squareRadius: Dp) {
    XSmall(32.dp, 12.dp, 20.dp, 4.dp, 10.dp),
    Small(40.dp, 16.dp, 20.dp, 8.dp, 12.dp),
    Medium(56.dp, 24.dp, 24.dp, 8.dp, 16.dp),
    Large(96.dp, 48.dp, 32.dp, 12.dp, 28.dp),
    XLarge(136.dp, 64.dp, 40.dp, 16.dp, 28.dp);

    val textStyle: @Composable () -> TextStyle
        get() = {
            when (this) {
                XSmall, Small -> MaterialTheme.typography.labelLarge
                Medium -> MaterialTheme.typography.titleMedium
                Large -> MaterialTheme.typography.headlineSmall
                XLarge -> MaterialTheme.typography.headlineLarge
            }
        }
}

enum class ExpressiveStyle { Filled, Tonal, Outlined, Elevated, Text }

private data class StyleColors(val container: Color, val content: Color, val border: BorderStroke?, val elevation: Dp)

@Composable
private fun colorsFor(style: ExpressiveStyle, selected: Boolean?, enabled: Boolean): StyleColors {
    val c = MaterialTheme.colorScheme
    val base = when (style) {
        ExpressiveStyle.Filled -> StyleColors(c.primary, c.onPrimary, null, 0.dp)
        ExpressiveStyle.Tonal -> StyleColors(c.secondaryContainer, c.onSecondaryContainer, null, 0.dp)
        ExpressiveStyle.Outlined -> StyleColors(Color.Transparent, c.onSurfaceVariant, BorderStroke(1.dp, c.outlineVariant), 0.dp)
        ExpressiveStyle.Elevated -> StyleColors(c.surfaceContainerLow, c.primary, null, 1.dp)
        ExpressiveStyle.Text -> StyleColors(Color.Transparent, c.primary, null, 0.dp)
    }
    // A toggled-on button takes the emphasised colour of its family.
    val colors = if (selected == true) {
        when (style) {
            ExpressiveStyle.Filled -> StyleColors(c.primary, c.onPrimary, null, 0.dp)
            ExpressiveStyle.Tonal -> StyleColors(c.secondary, c.onSecondary, null, 0.dp)
            ExpressiveStyle.Outlined -> StyleColors(c.inverseSurface, c.inverseOnSurface, null, 0.dp)
            ExpressiveStyle.Elevated -> StyleColors(c.primary, c.onPrimary, null, 0.dp)
            ExpressiveStyle.Text -> StyleColors(c.secondaryContainer, c.onSecondaryContainer, null, 0.dp)
        }
    } else if (selected == false && style == ExpressiveStyle.Filled) {
        StyleColors(c.surfaceContainer, c.onSurfaceVariant, null, 0.dp) // unselected toggle sits back
    } else base
    return if (enabled) colors else colors.copy(
        container = if (colors.container == Color.Transparent) Color.Transparent else c.onSurface.copy(alpha = 0.12f),
        content = c.onSurface.copy(alpha = 0.38f),
    )
}

/**
 * Corner radius that morphs: fully round at rest, squarer while pressed; toggles flip between
 * round (off) and square (on). Spring-driven, so it overshoots a little like the spec's demos.
 */
@Composable
internal fun morphingRadius(
    size: ExpressiveSize,
    pressed: Boolean,
    selected: Boolean?,
    square: Boolean = false,
): Dp {
    val round = size.height / 2
    val rest = when {
        selected == true -> size.squareRadius
        square -> size.squareRadius
        else -> round
    }
    val target = if (pressed) (size.squareRadius * 0.6f).coerceAtLeast(6.dp) else rest
    return animateDpAsState(target, ViviMotion.spatialFast(), label = "expressiveRadius").value
}

/** Expressive button; pass [selected] to make it a toggle button. */
@Composable
fun ExpressiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: ExpressiveSize = ExpressiveSize.Small,
    style: ExpressiveStyle = ExpressiveStyle.Filled,
    selected: Boolean? = null,
    enabled: Boolean = true,
    square: Boolean = false,
    leadingIcon: ImageVector? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val radius = morphingRadius(size, pressed, selected, square)
    val colors = colorsFor(style, selected, enabled)
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, ViviMotion.spatialFast(), label = "expressivePress")

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(radius),
        color = colors.container,
        contentColor = colors.content,
        border = colors.border,
        shadowElevation = colors.elevation,
        interactionSource = interaction,
        modifier = modifier
            .defaultMinSize(minWidth = size.height)
            .height(size.height)
            .graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        Box(
            Modifier.padding(horizontal = size.horizontal),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (leadingIcon != null) {
                    Icon(leadingIcon, null, Modifier.size(size.icon))
                    Spacer(Modifier.width(size.iconGap))
                }
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalTextStyle provides size.textStyle(),
                ) { content() }
            }
        }
    }
}

/** Convenience for the common text-only case. */
@Composable
fun ExpressiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: ExpressiveSize = ExpressiveSize.Small,
    style: ExpressiveStyle = ExpressiveStyle.Filled,
    selected: Boolean? = null,
    enabled: Boolean = true,
    square: Boolean = false,
    leadingIcon: ImageVector? = null,
) = ExpressiveButton(onClick, modifier, size, style, selected, enabled, square, leadingIcon) {
    Text(text, maxLines = 1)
}

/**
 * Connected button group (the expressive replacement for a segmented button): outer corners stay
 * round, inner corners are tight, and the selected item morphs into a fully round pill.
 */
@Composable
fun <T> ConnectedButtonGroup(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    size: ExpressiveSize = ExpressiveSize.Small,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEachIndexed { index, (value, label) ->
            val isSelected = value == selected
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val round = size.height / 2
            val inner = if (pressed) 4.dp else 6.dp
            val first = index == 0
            val last = index == options.lastIndex
            val spring = ViviMotion.spatialFast<Dp>()
            val tl by animateDpAsState(if (isSelected) round else if (first) round else inner, spring, label = "tl")
            val bl by animateDpAsState(if (isSelected) round else if (first) round else inner, spring, label = "bl")
            val tr by animateDpAsState(if (isSelected) round else if (last) round else inner, spring, label = "tr")
            val br by animateDpAsState(if (isSelected) round else if (last) round else inner, spring, label = "br")
            val c = MaterialTheme.colorScheme
            Surface(
                onClick = { onSelect(value) },
                shape = RoundedCornerShape(topStart = tl, topEnd = tr, bottomEnd = br, bottomStart = bl),
                color = if (isSelected) c.primary else c.surfaceContainerHighest,
                contentColor = if (isSelected) c.onPrimary else c.onSurfaceVariant,
                interactionSource = interaction,
                modifier = Modifier.weight(1f, fill = true).height(size.height),
            ) {
                Box(Modifier.padding(horizontal = size.horizontal), contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        style = size.textStyle(),
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        color = LocalContentColor.current,
                    )
                }
            }
        }
    }
}

/** Split button: a primary action plus a trailing chevron that opens related actions. */
@Composable
fun ExpressiveSplitButton(
    text: String,
    onClick: () -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    size: ExpressiveSize = ExpressiveSize.Small,
    style: ExpressiveStyle = ExpressiveStyle.Filled,
    leadingIcon: ImageVector? = null,
) {
    val colors = colorsFor(style, null, true)
    val round = size.height / 2
    val inner = 4.dp
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, ViviMotion.spatialDefault(), label = "chevron")
    val trailingRadius by animateDpAsState(if (expanded) round else inner, ViviMotion.spatialFast(), label = "trailing")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(topStart = round, bottomStart = round, topEnd = inner, bottomEnd = inner),
            color = colors.container,
            contentColor = colors.content,
            border = colors.border,
            modifier = Modifier.height(size.height),
        ) {
            Row(
                Modifier.padding(horizontal = size.horizontal),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leadingIcon != null) {
                    Icon(leadingIcon, null, Modifier.size(size.icon))
                    Spacer(Modifier.width(size.iconGap))
                }
                Text(text, style = size.textStyle(), maxLines = 1)
            }
        }
        Surface(
            onClick = { onExpandedChange(!expanded) },
            shape = RoundedCornerShape(topStart = trailingRadius, bottomStart = trailingRadius, topEnd = round, bottomEnd = round),
            color = colors.container,
            contentColor = colors.content,
            border = colors.border,
            modifier = Modifier.height(size.height).width(size.height + 4.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(size.icon + 4.dp).rotate(rotation))
            }
        }
    }
}
