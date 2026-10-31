package com.music.vivi.compose.ui.popup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min

/**
 * Aspect-driven arrangement of the popup: slots in, geometry out. Portrait / square windows get the compact
 * stack (art, info, controls); wide windows (see [PopupGeometry.modeFor]) put lyrics beside the player.
 * Kept free of playback state so the switch can be tested with plain stub slots.
 */
@Composable
fun PopupPlayerLayout(
    art: @Composable () -> Unit,
    info: @Composable (compact: Boolean) -> Unit,
    controls: @Composable (compact: Boolean) -> Unit,
    lyrics: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val mode = PopupGeometry.modeFor(maxWidth.value, maxHeight.value)
        // Short windows drop the optional rows (album line, volume) so the art keeps some room.
        val compact = maxHeight.value < PopupGeometry.COMPACT_BELOW_H
        when (mode) {
            PopupMode.Stack -> Column(
                Modifier.fillMaxSize().testTag("popup-stack").padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ArtSlot(Modifier.weight(1f).fillMaxWidth(), art)
                info(compact)
                controls(compact)
            }

            // Art + info on the left, lyrics on the right, one full-width transport strip underneath.
            PopupMode.Landscape -> Column(
                Modifier.fillMaxSize().testTag("popup-landscape").padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(
                        Modifier.weight(0.42f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ArtSlot(Modifier.weight(1f).fillMaxWidth(), art)
                        info(compact)
                    }
                    Box(Modifier.weight(0.58f).fillMaxHeight().testTag("popup-lyrics")) { lyrics() }
                }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.widthIn(max = 520.dp)) { controls(compact) }
                }
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
