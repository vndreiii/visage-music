package com.music.vivi.compose.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.PrefKeys
import com.music.vivi.compose.theme.fontDisplayName
import com.music.vivi.compose.theme.fontFamilyFor
import com.music.vivi.compose.theme.installedFontFamilies
import com.music.vivi.compose.theme.legacyFontPair

/** Heading and body fonts are chosen independently: any bundled font, the system default, or anything installed. */
@Composable
internal fun ColumnScope.FontPairingContent(prefs: DesktopPrefs) {
    val legacy = legacyFontPair(prefs.getString(PrefKeys.InterfaceFont, "expressive"))
    val heading = prefs.getString(PrefKeys.HeadingFont, "").ifBlank { legacy.first }
    val body = prefs.getString(PrefKeys.BodyFont, "").ifBlank { legacy.second }
    var picking by remember { mutableStateOf<String?>(null) }

    // Live preview: the theme typography already follows the two choices.
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)) {
        Text("Make room for your music", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Readable details, expressive headings",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
    SettingsDivider()
    SettingsRowFrame(Icons.Outlined.Tune, "Headings", "Page titles, section headers, big text", { picking = PrefKeys.HeadingFont }) {
        FontValue(heading)
    }
    SettingsDivider()
    SettingsRowFrame(Icons.Outlined.Tune, "Body & labels", "Lists, buttons, descriptions", { picking = PrefKeys.BodyFont }) {
        FontValue(body)
    }
    Row(Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = {
            prefs.setString(PrefKeys.HeadingFont, "Manrope")
            prefs.setString(PrefKeys.BodyFont, "Roboto")
        }) { Text("Reset to default") }
    }

    picking?.let { key ->
        FontPickerDialog(
            title = if (key == PrefKeys.HeadingFont) "Heading font" else "Body font",
            current = if (key == PrefKeys.HeadingFont) heading else body,
            onPick = { prefs.setString(key, it); picking = null },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun FontValue(key: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Text(
            fontDisplayName(key),
            fontFamily = fontFamilyFor(key),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 180.dp).padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

private class FontChoice(val key: String, val label: String)

@Composable
private fun FontPickerDialog(title: String, current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val bundled = remember { listOf(FontChoice("Manrope", "Manrope"), FontChoice("DMSans", "DM Sans"), FontChoice("Roboto", "Roboto"), FontChoice("system", "System default")) }
    val installed = remember { installedFontFamilies().map { FontChoice("sys:$it", it) } }
    val q = query.trim()
    val bundledShown = bundled.filter { q.isEmpty() || it.label.contains(q, true) }
    val installedShown = installed.filter { q.isEmpty() || it.label.contains(q, true) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth(0.92f).heightIn(max = 600.dp),
        ) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Search fonts") },
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    if (bundledShown.isNotEmpty()) {
                        item(key = "h-bundled") { FontGroupLabel("Included") }
                        items(bundledShown, key = { "b-" + it.key }) { FontRow(it, it.key == current) { onPick(it.key) } }
                    }
                    if (installedShown.isNotEmpty()) {
                        item(key = "h-installed") { FontGroupLabel("Installed on this computer") }
                        items(installedShown, key = { "i-" + it.key }) { FontRow(it, it.key == current) { onPick(it.key) } }
                    }
                    if (bundledShown.isEmpty() && installedShown.isEmpty()) {
                        item(key = "none") {
                            Text("No fonts match", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

@Composable
private fun FontGroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun FontRow(choice: FontChoice, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 1.dp).clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Each name is drawn in its own font, so the list is also the preview.
        Text(
            choice.label,
            fontFamily = fontFamilyFor(choice.key),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}
