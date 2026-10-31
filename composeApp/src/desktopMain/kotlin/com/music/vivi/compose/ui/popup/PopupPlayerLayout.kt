package com.music.vivi.compose.ui.popup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min

/**
 * Wraps the art + info block so the host window can make it the drag surface (e.g. `WindowDraggableArea`).
 * The default is a plain [Box], so tests and previews need nothing.
 */
typealias PopupDragArea = @Composable (modifier: Modifier, content: @Composable () -> Unit) -> Unit

val NoPopupDragArea: PopupDragArea = { modifier, content -> Box(modifier) { content() } }

/**
 * Aspect-driven arrangement of the popup: slots in, geometry out.
 *
 * Portrait / square windows get the stack (art, info, controls). Wide windows (see [PopupGeometry.modeFor]) get a
 * fixed-width player column on the left (art, info, seek, transport, volume stacked) and the lyrics filling the whole
 * right side, top to bottom. Kept free of playback state so the switch can be tested with plain stub slots.
 */
@Composable
fun PopupPlayerLayout(
    art: @Composable () -> Unit,
    info: @Composable (compact: Boolean) -> Unit,
    controls: @Composable (compact: Boolean) -> Unit,
    lyrics: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dragArea: PopupDragArea = NoPopupDragArea,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val totalW = maxWidth
        val mode = PopupGeometry.modeFor(maxWidth.value, maxHeight.value)
        // Short windows drop the optional rows (album line, volume) so the art keeps some room.
        val compact = maxHeight.value < PopupGeometry.COMPACT_BELOW_H
        val pad = if (compact) 20.dp else 28.dp
        when (mode) {
            // One even inset on all four sides (the pin/close row floats inside the top-right of it).
            PopupMode.Stack -> Column(
                Modifier.fillMaxSize().testTag("popup-stack").padding(pad),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                dragArea(Modifier.weight(1f).fillMaxWidth()) {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        ArtSlot(Modifier.weight(1f).fillMaxWidth(), art)
                        Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
                        info(compact)
                    }
                }
                Spacer(Modifier.height(if (compact) 6.dp else 12.dp))
                controls(compact)
            }

            // Fixed-width player column on the left, lyrics own the entire right side.
            PopupMode.Landscape -> Row(Modifier.fillMaxSize().testTag("popup-landscape")) {
                val leftW = PopupGeometry.landscapeLeftWidth(totalW.value).dp
                Column(
                    Modifier.width(leftW).fillMaxHeight().padding(pad),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    dragArea(Modifier.weight(1f).fillMaxWidth()) {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                            ArtSlot(Modifier.weight(1f).fillMaxWidth(), art)
                            Spacer(Modifier.height(if (compact) 6.dp else 12.dp))
                            info(compact)
                        }
                    }
                    Spacer(Modifier.height(if (compact) 4.dp else 10.dp))
                    controls(compact)
                }
                // Same inset as the player column, so the lyrics toolbar (4dp top / 8dp end inside the panel)
                // lands exactly `pad` from the top and right edges.
                Box(
                    Modifier.weight(1f).fillMaxHeight().testTag("popup-lyrics")
                        .padding(top = pad - 4.dp, end = pad - 8.dp, bottom = pad),
                ) { lyrics() }
            }
        }
    }
}

/** Square art, as large as the slot allows, centred. */
@Composable
private fun ArtSlot(modifier: Modifier, art: @Composable () -> Unit) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        Box(Modifier.size(min(maxWidth, maxHeight))) { art() }
    }
}
