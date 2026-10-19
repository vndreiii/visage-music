package com.music.vivi.compose.keys

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.ui.component.overMainPane
import com.music.vivi.compose.ui.motion.VisageMotion
import com.music.vivi.compose.ui.settings.rememberPrefsVersion
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

/** Window-level overlays: the transient readout (volume, seek…) and the `?` cheat-sheet. Place at the app root. */
@Composable
fun ShortcutOverlays() {
    Box(Modifier.fillMaxSize()) {
        ShortcutHud()
        ShortcutCheatSheet()
    }
}

@Composable
private fun ShortcutHud() {
    val serial = ShortcutUi.hudSerial
    LaunchedEffect(serial) {
        if (serial == 0) return@LaunchedEffect
        delay(1300)
        ShortcutUi.hudText = null
    }
    Box(Modifier.overMainPane().padding(bottom = 132.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = ShortcutUi.hudText != null,
            enter = fadeIn(VisageMotion.fadeInSpec()) + slideInVertically(VisageMotion.glide()) { it / 2 } + scaleIn(VisageMotion.bounce(), initialScale = 0.85f),
            exit = fadeOut(VisageMotion.fadeOutSpec()) + slideOutVertically(tween(160)) { it / 3 },
        ) {
            // Keep showing the last text while the exit animation runs.
            val text = remember(serial) { ShortcutUi.hudText }
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.94f),
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shadowElevation = 6.dp,
            ) {
                Text(
                    text ?: "",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun ShortcutCheatSheet() {
    val prefs = koinInject<DesktopPrefs>()
    @Suppress("UNUSED_VARIABLE")
    val observe = rememberPrefsVersion(prefs)
    val keymap = remember(prefs) { prefs.keymap() }
    val visible = ShortcutUi.cheatSheetVisible

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(VisageMotion.fadeInSpec()),
        exit = fadeOut(VisageMotion.fadeOutSpec()),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    ShortcutUi.cheatSheetVisible = false
                },
            contentAlignment = Alignment.TopStart,
        ) {
          Box(Modifier.overMainPane(), contentAlignment = Alignment.Center) {
            val sheetInteraction = remember { MutableInteractionSource() }
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .padding(32.dp)
                    .widthIn(max = 880.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(0.86f)
                    .clickable(interactionSource = sheetInteraction, indication = null) {},
            ) {
                Column(Modifier.padding(horizontal = 32.dp, vertical = 24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Keyboard shortcuts", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(
                                if (keymap.masterEnabled) "Always active, except while typing in a text field."
                                else "Shortcuts are turned off in Settings.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = {
                            ShortcutUi.cheatSheetVisible = false
                            ShortcutUi.openSettings?.invoke()
                        }) { Text("Customize") }
                        TextButton(onClick = { ShortcutUi.cheatSheetVisible = false }) { Text("Close") }
                    }
                    Row(
                        Modifier.weight(1f).fillMaxWidth().padding(top = 16.dp).verticalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(36.dp),
                    ) {
                        val left = listOf(ShortcutCategory.Playback, ShortcutCategory.Seek)
                        val right = ShortcutCategory.entries - left.toSet()
                        for (group in listOf(left, right)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                                group.forEach { CheatCategory(it, keymap) }
                            }
                        }
                    }
                }
            }
          }
        }
    }
}

@Composable
private fun CheatCategory(category: ShortcutCategory, keymap: Keymap) {
    val actions = ShortcutAction.entries.filter { it.category == category }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(category.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        if (category == ShortcutCategory.Seek && actions.all { !keymap.isCustomized(it) && keymap.isEnabled(it) }) {
            CheatRow("Jump to 0% – 90% of the track") { KeyCap("0"); KeyCap("…", dimmed = true); KeyCap("9") }
            return@Column
        }
        actions.forEach { action ->
            val bindings = keymap.bindings(action)
            val off = !keymap.isEnabled(action) || !keymap.masterEnabled
            CheatRow(action.label, dimmed = off) {
                if (bindings.isEmpty()) Text("Unbound", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                bindings.forEachIndexed { i, b ->
                    if (i > 0) Text("or", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BindingCaps(b, dimmed = off)
                }
            }
        }
    }
}

@Composable
private fun CheatRow(label: String, dimmed: Boolean = false, caps: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { caps() }
    }
}
