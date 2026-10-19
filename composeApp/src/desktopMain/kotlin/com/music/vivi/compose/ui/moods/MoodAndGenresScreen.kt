package com.music.vivi.compose.ui.moods

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.music.innertube.YouTube
import com.music.innertube.pages.MoodAndGenres
import com.music.vivi.compose.navigation.Routes
import com.music.vivi.compose.ui.component.ChipsRow
import com.music.vivi.compose.ui.component.DetailPageHeader
import com.music.vivi.compose.ui.component.MoodCard
import com.music.vivi.compose.ui.component.MoodCardSize
import com.music.vivi.compose.ui.component.PageErrorState
import com.music.vivi.compose.ui.component.PageLoadingState
import com.music.vivi.compose.ui.component.smoothWheelScroll
import com.music.vivi.compose.ui.motion.VisageMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class MoodFilter(val label: String) { All("All"), Moods("Moods"), Genres("Genres") }

private class MoodEntry(val item: MoodAndGenres.Item, val isMood: Boolean, val key: String)

private class MoodSection(val title: String, val isMood: Boolean, val entries: List<MoodEntry>)

private val featuredMoods = listOf("chill", "workout", "party", "focus", "feel good", "romance", "energize")

@Composable
fun MoodAndGenresScreen(navController: NavHostController) {
    val scope = rememberCoroutineScope()
    var moods by remember { mutableStateOf<List<MoodAndGenres>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf(MoodFilter.All) }
    var query by remember { mutableStateOf("") }
    var intro by remember { mutableStateOf(true) }

    fun load() {
        scope.launch {
            loading = true
            error = null
            try {
                moods = withContext(Dispatchers.IO) { YouTube.moodAndGenres().getOrThrow() }
            } catch (e: Exception) {
                error = e.message ?: "Failed to load moods"
            } finally {
                loading = false
            }
        }
    }
    LaunchedEffect(Unit) { load() }
    LaunchedEffect(filter, moods != null) {
        if (moods != null) { delay(1200); intro = false }
    }

    Column(Modifier.fillMaxSize()) {
        DetailPageHeader(title = "Moods & genres", onBack = { navController.navigateUp() })
        val loaded = moods
        when {
            loading && loaded == null -> PageLoadingState("Loading moods", detail = true)
            error != null && loaded == null -> PageErrorState(error.orEmpty()) { load() }
            loaded != null -> {
                val sections = remember(loaded) {
                    loaded.mapIndexed { si, s ->
                        val t = s.title.lowercase()
                        val isMood = "mood" in t || "moment" in t || (si == 0 && loaded.none { "mood" in it.title.lowercase() || "moment" in it.title.lowercase() })
                        MoodSection(s.title, isMood, s.items.map { MoodEntry(it, isMood, "${si}_${it.endpoint.browseId}_${it.endpoint.params}") })
                    }
                }
                FilterBar(
                    filter = filter,
                    onFilter = { if (it != filter) { filter = it; intro = true } },
                    query = query,
                    onQuery = { query = it },
                )
                MoodContent(
                    sections = sections, filter = filter, query = query.trim(), intro = intro,
                    onOpen = { e -> navController.navigate(Routes.forBrowse(e.item.endpoint.browseId, e.item.endpoint.params)) },
                )
            }
        }
    }
}

@Composable
private fun FilterBar(filter: MoodFilter, onFilter: (MoodFilter) -> Unit, query: String, onQuery: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            ChipsRow(
                chips = MoodFilter.entries.map { it to it.label },
                currentValue = filter,
                onValueUpdate = { onFilter(it ?: MoodFilter.All) },
            )
        }
        val focused = remember { mutableStateOf(false) }
        val w by animateDpAsState(if (focused.value || query.isNotEmpty()) 280.dp else 200.dp, VisageMotion.spatialDefault(), label = "searchW")
        Row(
            Modifier.padding(end = 20.dp).width(w).height(40.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) Text("Search", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BasicTextField(
                    value = query, onValueChange = onQuery, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().androidxOnFocus { focused.value = it },
                )
            }
            AnimatedVisibility(query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                IconButton(onClick = { onQuery("") }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Close, "Clear search", Modifier.size(18.dp))
                }
            }
        }
    }
}

private fun Modifier.androidxOnFocus(onChange: (Boolean) -> Unit): Modifier =
    this.then(Modifier.onFocusChanged { onChange(it.isFocused) })

@Composable
private fun MoodContent(
    sections: List<MoodSection>,
    filter: MoodFilter,
    query: String,
    intro: Boolean,
    onOpen: (MoodEntry) -> Unit,
) {
    val state = rememberLazyListState()
    val gap = 16.dp
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cols = ((maxWidth - 40.dp) / 270.dp).toInt().coerceIn(2, 4)
        val heroCount = if (maxWidth < 760.dp) 2 else 3

        val showHero = query.isEmpty() && filter != MoodFilter.Genres
        val heroes = remember(sections, showHero, heroCount) {
            if (!showHero) emptyList() else {
                val moodEntries = sections.filter { it.isMood }.flatMap { it.entries }
                val picked = featuredMoods.mapNotNull { f -> moodEntries.firstOrNull { it.item.title.lowercase() == f } }
                (picked + moodEntries).distinctBy { it.key }.take(heroCount)
            }
        }
        val heroKeys = heroes.map { it.key }.toSet()
        val visible = sections.filter {
            filter == MoodFilter.All || (filter == MoodFilter.Moods) == it.isMood
        }.map { s ->
            MoodSection(s.title, s.isMood, s.entries.filter { e ->
                e.key !in heroKeys && (query.isEmpty() || e.item.title.contains(query, ignoreCase = true))
            })
        }.filter { it.entries.isNotEmpty() }
        val heroesVisible = heroes.filter { query.isEmpty() }

        var delayIdx = 0
        fun nextDelay(): Int = if (intro && delayIdx < 16) 60 * delayIdx++ else -1

        LazyColumn(
            state = state,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(gap),
            modifier = Modifier.fillMaxSize().smoothWheelScroll(state),
        ) {
            if (heroesVisible.isNotEmpty()) {
                val delays = heroesVisible.map { nextDelay() }
                item(key = "hero", contentType = "hero") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        heroesVisible.forEachIndexed { i, e ->
                            MoodCard(
                                title = e.item.title, accentColor = Color(e.item.stripeColor.toInt()),
                                size = MoodCardSize.Hero, subtitle = "Mood", entranceDelayMs = delays[i],
                                modifier = Modifier.weight(if (i == 0 && heroesVisible.size > 2) 1.5f else 1f),
                                onClick = { onOpen(e) },
                            )
                        }
                    }
                }
            }
            if (visible.isEmpty()) {
                item(key = "empty") {
                    Column(Modifier.fillMaxWidth().padding(vertical = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Search, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (query.isNotEmpty()) "Nothing matches \"$query\"" else "Nothing here yet",
                            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            visible.forEachIndexed { si, section ->
                item(key = "head_${section.title}_$si", contentType = "header") { SectionHeader(section.title, section.entries.size) }
                val rows = section.entries.chunked(cols)
                rows.forEachIndexed { ri, row ->
                    val full = row.size == cols
                    val pattern = if (!full) 0 else (ri + si) % 3
                    val delays = row.map { nextDelay() }
                    item(key = "row_${section.title}_${si}_$ri", contentType = "row") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                            row.forEachIndexed { i, e ->
                                val wide = (pattern == 1 && i == 0) || (pattern == 2 && i == cols - 1)
                                MoodCard(
                                    title = e.item.title, accentColor = Color(e.item.stripeColor.toInt()),
                                    size = if (pattern == 0) MoodCardSize.Medium else MoodCardSize.Large,
                                    subtitle = if (e.isMood) "Mood" else "Genre",
                                    entranceDelayMs = delays[i],
                                    modifier = Modifier.weight(if (wide) 1.6f else 1f),
                                    onClick = { onOpen(e) },
                                )
                            }
                            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(6.dp).height(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Spacer(Modifier.width(12.dp))
        Text("$count", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 10.dp, vertical = 2.dp))
    }
}
