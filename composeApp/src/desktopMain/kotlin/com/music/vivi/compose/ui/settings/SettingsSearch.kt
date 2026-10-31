package com.music.vivi.compose.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.music.vivi.compose.prefs.DesktopPrefs

/** The text typed in the settings search box, shared so the sub-pages highlight what was searched for. */
object SettingsSearch {
    var query by mutableStateOf("")
    /** Title of the result that was opened; its row scrolls into view once, then this clears. */
    var target by mutableStateOf<String?>(null)
    /** Bumped to ask the search box to take focus (the top-bar "Search settings" item). */
    var focusRequest by mutableStateOf(0)

    fun terms(): List<String> = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }

    fun matches(vararg texts: String?): Boolean {
        val t = terms()
        if (t.isEmpty()) return false
        val hay = texts.filterNotNull().joinToString(" ").lowercase()
        return t.all { it in hay }
    }

    fun results(): List<SettingsEntry> {
        val t = terms()
        if (t.isEmpty()) return emptyList()
        return SettingsIndex
            .filter { matches(it.title, it.subtitle, it.options, it.page) }
            .sortedBy { e -> if (t.any { it in e.title.lowercase() }) 0 else 1 }
    }
}

/** [text] with every search term marked. */
@Composable
fun highlighted(text: String): AnnotatedString {
    val terms = SettingsSearch.terms()
    if (terms.isEmpty() || text.isEmpty()) return AnnotatedString(text)
    val mark = SpanStyle(
        background = MaterialTheme.colorScheme.tertiaryContainer,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        fontWeight = FontWeight.SemiBold,
    )
    val lower = text.lowercase()
    val hit = BooleanArray(text.length)
    terms.forEach { term ->
        var i = lower.indexOf(term)
        while (i >= 0) {
            for (k in i until minOf(i + term.length, text.length)) hit[k] = true
            i = lower.indexOf(term, i + term.length)
        }
    }
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            var j = i
            while (j < text.length && hit[j] == hit[i]) j++
            if (hit[i]) withStyle(mark) { append(text.substring(i, j)) } else append(text.substring(i, j))
            i = j
        }
    }
}

/**
 * Settings added in a release, so their page can wear a "New" chip until it has been visited. Add a line when a
 * setting ships; [rev] only ever goes up.
 */
object SettingsNew {
    class Item(val title: String, val route: String, val rev: Int)

    val items = listOf(
        Item("Refresh Quick picks automatically", com.music.vivi.compose.navigation.Routes.SETTINGS_APPEARANCE, 1),
        Item("Manual refresh button", com.music.vivi.compose.navigation.Routes.SETTINGS_APPEARANCE, 1),
    )
    val latestRev: Int get() = items.maxOf { it.rev }

    private fun seen(prefs: DesktopPrefs, route: String) =
        maxOf(prefs.getInt("settingsNewBaseline", 0), prefs.getInt("settingsNewSeen.$route", 0))

    fun pageHasNew(prefs: DesktopPrefs, route: String): Boolean =
        items.any { it.route == route && it.rev > seen(prefs, route) }

    fun isNewRow(prefs: DesktopPrefs, title: String): Boolean =
        items.any { it.title == title && it.rev > seen(prefs, it.route) }

    fun markVisited(prefs: DesktopPrefs, route: String) {
        val newest = items.filter { it.route == route }.maxOfOrNull { it.rev } ?: return
        if (newest > seen(prefs, route)) prefs.setInt("settingsNewSeen.$route", newest)
    }
}

@Composable
fun NewChip(modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary, modifier = modifier) {
        Text(
            "New",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
internal fun SettingsSearchField(modifier: Modifier = Modifier) {
    var focused by androidx.compose.runtime.remember { mutableStateOf(false) }
    val focusRequester = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(SettingsSearch.focusRequest) {
        if (SettingsSearch.focusRequest > 0) {
            kotlinx.coroutines.delay(120)
            runCatching { focusRequester.requestFocus() }
        }
    }
    val shape = RoundedCornerShape(28.dp)
    com.music.vivi.compose.ui.glass.GlassSurface(
        shape = shape,
        fallbackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)
            .then(if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier),
    ) {
        // Same 16dp inset, 22dp icon and 18dp gap as the settings rows below, so icons and text line up.
        Row(Modifier.fillMaxWidth().height(52.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(18.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (SettingsSearch.query.isEmpty()) {
                    Text("Search settings", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BasicTextField(
                    value = SettingsSearch.query,
                    onValueChange = { SettingsSearch.query = it; SettingsSearch.target = null },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).onFocusChanged { focused = it.isFocused },
                )
            }
            if (SettingsSearch.query.isNotEmpty()) {
                IconButton({ SettingsSearch.query = "" }) { Icon(Icons.Outlined.Close, "Clear search") }
            }
        }
    }
}

/** Matching settings from every page; opening one goes to its page with the match highlighted there. */
@Composable
internal fun SettingsSearchResults(navController: NavController) {
    val results = SettingsSearch.results()
    if (results.isEmpty()) {
        Text(
            "No settings match “${SettingsSearch.query.trim()}”.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 24.dp),
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        results.forEach { e ->
            Surface(
                onClick = { SettingsSearch.target = e.title; navController.navigate(e.route) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(highlighted(e.title), style = MaterialTheme.typography.bodyLarge)
                    if (e.subtitle.isNotBlank()) {
                        Text(highlighted(e.subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (e.options.isNotBlank() && SettingsSearch.matches(e.options) && !SettingsSearch.matches(e.title, e.subtitle)) {
                        Text(highlighted(e.options), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        highlighted(if (e.page == "Settings") "Settings" else "Settings › ${e.page}"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/** Shown on sub-pages while a search is active, so it can be cleared without going back. */
@Composable
internal fun SettingsSearchBanner() {
    if (SettingsSearch.query.isBlank()) return
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, modifier = Modifier.padding(end = 8.dp))
        Text("Highlighting “${SettingsSearch.query.trim()}”", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        IconButton({ SettingsSearch.query = "" }) { Icon(Icons.Outlined.Close, "Clear search") }
    }
}

/** The scrolling area of a settings page, so a search result can centre itself in it. */
class SettingsViewport(val scroll: androidx.compose.foundation.ScrollState) {
    var coords: androidx.compose.ui.layout.LayoutCoordinates? = null
}

val LocalSettingsViewport = androidx.compose.runtime.compositionLocalOf<SettingsViewport?> { null }

/** Wraps a settings scroll area: reports its bounds and shares the scroll state with the rows inside. */
@Composable
fun Modifier.settingsViewport(viewport: SettingsViewport): Modifier =
    this.onGloballyPositioned { viewport.coords = it }
