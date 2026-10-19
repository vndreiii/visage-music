package com.music.vivi.compose.ui.search

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.music.innertube.YouTube
import com.music.innertube.models.YouTubeLocale
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.ui.motion.VisageMotion
import java.util.Locale

/** Region (YouTube `gl`) and language (`hl`) that search, charts and browse use. */
object ContentRegion {
    const val PrefRegion = "contentRegion"
    const val PrefLanguage = "contentLanguage"
    const val Automatic = ""

    val languages: List<Pair<String, String>> = listOf(
        "en" to "English", "es" to "Español", "pt" to "Português", "fr" to "Français", "de" to "Deutsch",
        "it" to "Italiano", "nl" to "Nederlands", "pl" to "Polski", "ru" to "Русский", "tr" to "Türkçe",
        "ar" to "العربية", "hi" to "हिन्दी", "id" to "Bahasa Indonesia", "ja" to "日本語", "ko" to "한국어",
        "zh-CN" to "中文",
    )

    fun flag(code: String): String =
        if (code.length != 2) "🌐"
        else code.uppercase().map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")

    fun regions(): List<Pair<String, String>> =
        Locale.getISOCountries().map { it to Locale("", it).getDisplayCountry(Locale.ENGLISH) }
            .filter { it.second.isNotBlank() }
            .sortedBy { it.second }

    fun systemRegion(): String = Locale.getDefault().country.ifBlank { "US" }

    fun region(prefs: DesktopPrefs): String =
        prefs.getString(PrefRegion, Automatic).ifBlank { systemRegion() }

    fun language(prefs: DesktopPrefs): String =
        prefs.getString(PrefLanguage, Automatic).ifBlank { Locale.getDefault().language.ifBlank { "en" } }

    /** Pushes the saved choice into the YouTube client; call at startup and after changes. */
    fun apply(prefs: DesktopPrefs) {
        YouTube.locale = YouTubeLocale(gl = region(prefs), hl = language(prefs))
    }
}

@Composable
fun ContentRegionDialog(prefs: DesktopPrefs, onDismiss: () -> Unit, onChanged: () -> Unit) {
    var region by remember { mutableStateOf(prefs.getString(ContentRegion.PrefRegion, ContentRegion.Automatic)) }
    var language by remember { mutableStateOf(prefs.getString(ContentRegion.PrefLanguage, ContentRegion.Automatic)) }
    var filter by remember { mutableStateOf("") }
    val all = remember { ContentRegion.regions() }
    val shown = remember(filter, all) {
        if (filter.isBlank()) all else all.filter { it.second.contains(filter, ignoreCase = true) || it.first.equals(filter, true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Region & language") },
        text = {
            Column(Modifier.widthIn(min = 360.dp, max = 460.dp)) {
                Text(
                    "Changes what search, charts and suggestions return.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text("Language", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(selected = language.isBlank(), onClick = { language = ContentRegion.Automatic }, label = { Text("Automatic") })
                    ContentRegion.languages.forEach { (code, name) ->
                        FilterChip(selected = language == code, onClick = { language = code }, label = { Text(name) })
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("Region", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    singleLine = true,
                    placeholder = { Text("Search countries") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    item(key = "auto") {
                        RegionRow("🌐", "Automatic (${ContentRegion.systemRegion()})", region.isBlank()) { region = ContentRegion.Automatic }
                    }
                    items(shown, key = { it.first }) { (code, name) ->
                        RegionRow(ContentRegion.flag(code), name, region == code) { region = code }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                prefs.setString(ContentRegion.PrefRegion, region)
                prefs.setString(ContentRegion.PrefLanguage, language)
                ContentRegion.apply(prefs)
                onChanged()
                onDismiss()
            }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RegionRow(flag: String, name: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0f),
        VisageMotion.settle(),
        label = "regionRow",
    )
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            ,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bg).padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(flag, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(12.dp))
                Text(name, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}
