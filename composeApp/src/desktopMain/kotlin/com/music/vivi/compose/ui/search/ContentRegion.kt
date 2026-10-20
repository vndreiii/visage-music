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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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

    /** Interface languages YouTube Music accepts as `hl` (display names come from the JDK, so they stay in sync). */
    val languageCodes: List<String> = listOf(
        "af", "am", "ar", "as", "az", "be", "bg", "bn", "bs", "ca", "cs", "da", "de", "el", "en", "en-GB", "en-IN",
        "es", "es-419", "es-US", "et", "eu", "fa", "fi", "fil", "fr", "fr-CA", "gl", "gu", "hi", "hr", "hu", "hy",
        "id", "is", "it", "iw", "ja", "ka", "kk", "km", "kn", "ko", "ky", "lo", "lt", "lv", "mk", "ml", "mn", "mr",
        "ms", "my", "ne", "nl", "no", "or", "pa", "pl", "pt", "pt-PT", "ro", "ru", "si", "sk", "sl", "sq", "sr",
        "sr-Latn", "sv", "sw", "ta", "te", "th", "tr", "uk", "ur", "uz", "vi", "zh-CN", "zh-HK", "zh-TW", "zu",
    )

    /** (code, name in its own language, name in English). */
    fun languageOptions(): List<Triple<String, String, String>> = languageCodes.map { code ->
        val loc = Locale.forLanguageTag(code)
        val native = loc.getDisplayName(loc).replaceFirstChar { it.titlecase(loc) }
        code to native.ifBlank { code } to loc.getDisplayName(Locale.ENGLISH).ifBlank { code }
    }.map { Triple(it.first.first, it.first.second, it.second) }.sortedBy { it.third }

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

private class PickerOption(val key: String, val title: String, val subtitle: String?, val leading: String?)

/** Region and language, each a searchable list: tonal selection, no outlines. */
@Composable
fun ContentRegionDialog(prefs: DesktopPrefs, onDismiss: () -> Unit, onChanged: () -> Unit) {
    var region by remember { mutableStateOf(prefs.getString(ContentRegion.PrefRegion, ContentRegion.Automatic)) }
    var language by remember { mutableStateOf(prefs.getString(ContentRegion.PrefLanguage, ContentRegion.Automatic)) }

    val languageItems = remember {
        val systemLang = Locale.getDefault().let { it.getDisplayName(it) }.replaceFirstChar { it.uppercase() }
        listOf(PickerOption(ContentRegion.Automatic, "Automatic", "System language: $systemLang", "\uD83C\uDF10")) +
            ContentRegion.languageOptions().map { (code, native, english) ->
                PickerOption(code, native, english.takeIf { !it.equals(native, true) }, null)
            }
    }
    val regionItems = remember {
        listOf(PickerOption(ContentRegion.Automatic, "Automatic", "System region: ${ContentRegion.systemRegion()}", "\uD83C\uDF10")) +
            ContentRegion.regions().map { (code, name) -> PickerOption(code, name, code, ContentRegion.flag(code)) }
    }

    fun apply() {
        prefs.setString(ContentRegion.PrefRegion, region)
        prefs.setString(ContentRegion.PrefLanguage, language)
        ContentRegion.apply(prefs)
        onChanged()
        onDismiss()
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(0.92f).heightIn(max = 560.dp),
        ) {
            Column(Modifier.padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 16.dp)) {
                Text("Region & language", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Changes what search, charts and suggestions return.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.weight(1f, fill = false).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    PickerColumn("Language", "Search languages", languageItems, language, { language = it }, Modifier.weight(1f))
                    PickerColumn("Region", "Search countries", regionItems, region, { region = it }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(onClick = ::apply) { Text("Apply") }
                }
            }
        }
    }
}

@Composable
private fun PickerColumn(
    title: String,
    searchHint: String,
    items: List<PickerOption>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(query, items) {
        if (query.isBlank()) items
        else items.filter { it.title.contains(query, true) || it.subtitle?.contains(query, true) == true }
    }
    val listState = rememberLazyListState()
    // Open with the current choice in view.
    LaunchedEffect(Unit) {
        val i = items.indexOfFirst { it.key == selected }
        if (i > 3) listState.scrollToItem(i - 2)
    }
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        TextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(searchHint) },
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(state = listState, modifier = Modifier.weight(1f, fill = false).heightIn(min = 120.dp, max = 340.dp)) {
            items(shown, key = { it.key.ifEmpty { "auto" } }) { o ->
                PickerRow(o, o.key == selected) { onSelect(o.key) }
            }
        }
    }
}

@Composable
private fun PickerRow(option: PickerOption, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        VisageMotion.settle(),
        label = "pickerRow",
    )
    Row(
        Modifier.fillMaxWidth().padding(vertical = 1.dp).clip(RoundedCornerShape(14.dp)).background(bg)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        option.leading?.let {
            Text(it, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                option.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            option.subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
    }
}
