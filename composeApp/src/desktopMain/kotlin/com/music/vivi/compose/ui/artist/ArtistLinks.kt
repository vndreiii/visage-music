package com.music.vivi.compose.ui.artist

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.music.vivi.compose.ui.motion.VisageMotion

/**
 * A credit string ("DJ llz, XSKAVI1, DJ SMG, & Dj v2be") rendered as one link per artist. Hover
 * shifts the colour to [hoverColor], draws an underline that grows in, and shows the hand cursor.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArtistLinks(
    credits: String,
    style: TextStyle,
    color: Color,
    hoverColor: Color,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
) {
    val names = remember(credits) { ArtistNames.split(credits).ifEmpty { listOf(credits) } }
    FlowRow(modifier, horizontalArrangement = Arrangement.Start, maxLines = maxLines) {
        names.forEachIndexed { i, name ->
            ArtistLink(name, style, color, hoverColor) { onOpen(name) }
            if (i < names.lastIndex) Text(if (i == names.lastIndex - 1 && names.size > 2) " & " else ", ", style = style, color = color)
        }
    }
}

@Composable
fun ArtistLink(name: String, style: TextStyle, color: Color, hoverColor: Color, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val tint by animateColorAsState(if (hovered) hoverColor else color, VisageMotion.effectsFast(), label = "artistLinkColor")
    val underline by animateFloatAsState(if (hovered) 1f else 0f, VisageMotion.fade(200), label = "artistLinkUnderline")
    Text(
        name,
        style = style,
        color = tint,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .hoverable(source)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .drawBehind {
                if (underline > 0.01f) {
                    val y = size.height - 1.5f
                    drawLine(tint, Offset(0f, y), Offset(size.width * underline, y), strokeWidth = 1.5f)
                }
            },
    )
}
