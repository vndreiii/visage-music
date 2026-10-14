package com.music.vivi.compose.ui.expressive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Living reference for the Expressive kit: every component, size and style in one place.
 * Render it with `ExpressiveGalleryTest` (set `VIVI_RENDER_OUT=<dir>` to keep the PNG) after changing
 * a component, and add any new component or variant here so it stays visible.
 */
@Composable
fun ExpressiveGallery() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Expressive buttons", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ExpressiveButton("Filled", {}, style = ExpressiveStyle.Filled, leadingIcon = Icons.Filled.PlayArrow)
                    ExpressiveButton("Tonal", {}, style = ExpressiveStyle.Tonal, leadingIcon = Icons.Filled.Shuffle)
                    ExpressiveButton("Outlined", {}, style = ExpressiveStyle.Outlined)
                    ExpressiveButton("Elevated", {}, style = ExpressiveStyle.Elevated)
                    ExpressiveButton("Text", {}, style = ExpressiveStyle.Text)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ExpressiveButton("XS", {}, size = ExpressiveSize.XSmall)
                    ExpressiveButton("Small", {}, size = ExpressiveSize.Small)
                    ExpressiveButton("Medium", {}, size = ExpressiveSize.Medium)
                    ExpressiveButton("Large", {}, size = ExpressiveSize.Large, leadingIcon = Icons.Filled.PlayArrow)
                }
                Text("Toggle (off round → on square) and square shape", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ExpressiveButton("Off", {}, selected = false, size = ExpressiveSize.Medium)
                    ExpressiveButton("On", {}, selected = true, size = ExpressiveSize.Medium)
                    ExpressiveButton("Square", {}, square = true, size = ExpressiveSize.Medium, style = ExpressiveStyle.Tonal)
                }
                Text("Connected group (\"Side\" selected)", style = MaterialTheme.typography.titleMedium)
                ConnectedButtonGroup(
                    options = listOf("sheet" to "Sheet", "side" to "Side", "inline" to "Inline"),
                    selected = "side",
                    onSelect = {},
                    modifier = Modifier.width(360.dp),
                )
                Text("Split button", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ExpressiveSplitButton("Play all", {}, expanded = false, onExpandedChange = {}, leadingIcon = Icons.Filled.PlayArrow)
                    ExpressiveSplitButton("Open", {}, expanded = true, onExpandedChange = {}, style = ExpressiveStyle.Tonal, size = ExpressiveSize.Medium)
                }
            }
        }
    }
}

