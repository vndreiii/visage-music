package com.music.vivi.compose.ui.charts

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.music.innertube.YouTube
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.pages.ChartsPage
import com.music.vivi.compose.LocalPlaybackController
import com.music.vivi.compose.navigation.Routes
import com.music.vivi.compose.ui.artist.ArtistLinks
import com.music.vivi.compose.ui.component.CoverImage
import com.music.vivi.compose.ui.component.PageErrorState
import com.music.vivi.compose.ui.component.PageLoadingState
import com.music.vivi.compose.ui.component.VisageBackButton
import com.music.vivi.compose.ui.component.rememberNowPlayingIds
import com.music.vivi.compose.ui.component.smoothWheelScroll
import com.music.vivi.compose.ui.expressive.ExpressiveButton
import com.music.vivi.compose.ui.expressive.ExpressiveSize
import com.music.vivi.compose.ui.expressive.ExpressiveStyle
import com.music.vivi.compose.ui.motion.VisageMotion
import com.music.vivi.player.toQueueTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One selectable chart: a chart playlist, a song section, or the artist ranking. */
internal sealed class ChartTab(val label: String) {
    class Playlist(val item: PlaylistItem) : ChartTab(shortChartName(item.title))
    class Songs(val title: String, val items: List<SongItem>) : ChartTab(title)
    class Artists(val title: String, val items: List<ArtistItem>) : ChartTab(title)
}

internal fun shortChartName(title: String): String = title.trim()

/** Builds the chart tabs from the raw sections (playlists first, then songs, artists last). */
internal fun buildChartTabs(sections: List<ChartsPage.ChartSection>): List<ChartTab> {
    val tabs = mutableListOf<ChartTab>()
    sections.forEach { s ->
        s.items.filterIsInstance<PlaylistItem>().forEach { tabs += ChartTab.Playlist(it) }
        val songs = s.items.filterIsInstance<SongItem>()
        if (songs.isNotEmpty()) tabs += ChartTab.Songs(s.title, songs)
    }
    sections.forEach { s ->
        val artists = s.items.filterIsInstance<ArtistItem>()
        if (artists.isNotEmpty()) tabs += ChartTab.Artists(s.title, artists)
    }
    return tabs
}

/** Parses a chart movement label ("+3", "-2", "NEW", "=") into a displayable change. */
internal sealed class ChartMove {
    data class Up(val by: Int?) : ChartMove()
    data class Down(val by: Int?) : ChartMove()
    data object New : ChartMove()
    data object Same : ChartMove()
}

internal fun parseChartMove(raw: String?): ChartMove? {
    val t = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val up = t.contains("▲") || t.contains("↑") || t.startsWith("+") || t.equals("up", true)
    val down = t.contains("▼") || t.contains("↓") || t.startsWith("-") || t.startsWith("−") || t.equals("down", true)
    val n = t.filter { it.isDigit() }.toIntOrNull()
    return when {
        t.equals("new", true) || t.contains("NEW", true) -> ChartMove.New
        up -> ChartMove.Up(n)
        down -> ChartMove.Down(n)
        t == "=" || t == "-" || t.equals("same", true) || t.equals("no change", true) -> ChartMove.Same
        else -> null
    }
}

/** Rank-derived relative bar length in 0.12..1 (the API gives no real play counts). */
internal fun rankBar(rank: Int, total: Int): Float {
    if (total <= 1) return 1f
    return (1f - (rank - 1).coerceAtLeast(0).toFloat() / total * 0.88f).coerceIn(0.12f, 1f)
}

internal fun formatDuration(seconds: Int?): String {
    val s = seconds ?: return ""
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun ChartsScreen(navController: NavHostController) {
    val scope = rememberCoroutineScope()
    var chartsPage by remember { mutableStateOf<ChartsPage?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            loading = true
            error = null
            try {
                chartsPage = withContext(Dispatchers.IO) { YouTube.getChartsPage().getOrThrow() }
            } catch (e: Exception) {
                error = e.message ?: "Failed to load charts"
            } finally {
                loading = false
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    val page = chartsPage
    val tabs = remember(page) { page?.let { buildChartTabs(it.sections) }.orEmpty() }

    AnimatedContent(
        targetState = when {
            page != null && tabs.isNotEmpty() -> 0
            loading -> 1
            page != null -> 2
            else -> 3
        },
        transitionSpec = { fadeIn(VisageMotion.fade(350)) togetherWith fadeOut(tween(150)) },
        label = "chartsState",
    ) { state ->
        when (state) {
            1 -> PageLoadingState("Loading charts", detail = true)
            2 -> Column(Modifier.fillMaxSize()) {
                VisageBackButton(onClick = { navController.navigateUp() }, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
                PageErrorState("No charts available for your region right now", onRetry = { load() })
            }
            3 -> Column(Modifier.fillMaxSize()) {
                VisageBackButton(onClick = { navController.navigateUp() }, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Couldn't load charts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(error.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    Button(onClick = { load() }, modifier = Modifier.padding(top = 16.dp)) { Text("Retry") }
                }
            }
            else -> ChartsContent(navController, tabs)
        }
    }
}

/** A displayable ranked entry shared by every chart kind. */
private data class ChartEntry(
    val rank: Int,
    val id: String,
    val title: String,
    val credits: String,
    val cover: String?,
    val duration: String,
    val move: ChartMove?,
    val song: SongItem?,
    val artist: ArtistItem?,
)

@Composable
private fun ChartsContent(navController: NavHostController, tabs: List<ChartTab>) {
    val controller = LocalPlaybackController.current
    val scope = rememberCoroutineScope()
    val (playingId, isPlaying) = rememberNowPlayingIds()
    var selected by remember { mutableStateOf(0) }
    var previous by remember { mutableStateOf(0) }
    val loaded = remember { mutableStateMapOf<String, List<SongItem>>() }
    val failed = remember { mutableStateMapOf<String, Boolean>() }

    fun keyOf(tab: ChartTab) = when (tab) {
        is ChartTab.Playlist -> "pl:" + tab.item.id
        is ChartTab.Songs -> "songs:" + tab.title
        is ChartTab.Artists -> "artists:" + tab.title
    }

    val tab = tabs[selected.coerceIn(0, tabs.lastIndex)]
    LaunchedEffect(tab) {
        if (tab is ChartTab.Playlist && loaded[keyOf(tab)] == null) {
            failed.remove(keyOf(tab))
            val songs = withContext(Dispatchers.IO) { YouTube.playlist(tab.item.id).getOrNull()?.songs }
            if (songs != null) loaded[keyOf(tab)] = songs else failed[keyOf(tab)] = true
        }
    }

    fun openArtistByName(name: String) = navController.navigate(Routes.artistInfo(name))
    fun playSongs(songs: List<SongItem>, shuffle: Boolean, start: Int = 0) {
        if (songs.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            val ordered = if (shuffle) songs.shuffled() else songs
            controller.playQueue(ordered.map { it.toQueueTrack() }, if (shuffle) 0 else start)
        }
    }

    Column(Modifier.fillMaxSize()) {
        VisageBackButton(onClick = { navController.navigateUp() }, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
        TabStrip(
            labels = tabs.map { it.label },
            selected = selected,
            onSelect = { previous = selected; selected = it },
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
        )
        AnimatedContent(
            targetState = selected,
            transitionSpec = {
                val dir = if (targetState >= initialState) 1 else -1
                (fadeIn(VisageMotion.fade(380)) + slideInHorizontally(VisageMotion.spatialDefault()) { dir * it / 10 }) togetherWith
                    (fadeOut(tween(160)) + slideOutHorizontally(tween(220)) { -dir * it / 12 })
            },
            label = "chartSwap",
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { index ->
            val t = tabs[index.coerceIn(0, tabs.lastIndex)]
            val key = keyOf(t)
            val songs: List<SongItem>? = when (t) {
                is ChartTab.Playlist -> loaded[key]
                is ChartTab.Songs -> t.items
                is ChartTab.Artists -> null
            }
            val entries: List<ChartEntry>? = when (t) {
                is ChartTab.Artists -> t.items.mapIndexed { i, a ->
                    ChartEntry(i + 1, a.id, a.title, a.subtext.orEmpty(), a.thumbnail, "", null, null, a)
                }
                else -> songs?.mapIndexed { i, s ->
                    ChartEntry(s.chartPosition ?: (i + 1), s.id, s.title, s.artists.joinToString { it.name }, s.thumbnail,
                        formatDuration(s.duration), parseChartMove(s.chartChange), s, null)
                }
            }
            when {
                entries != null -> ChartList(
                    tab = t,
                    entries = entries,
                    playingId = playingId,
                    isPlaying = isPlaying,
                    onPlayAll = { playSongs(songs.orEmpty(), false) },
                    onShuffle = { playSongs(songs.orEmpty(), true) },
                    onPlayEntry = { e ->
                        val s = e.song
                        if (s != null) {
                            if (s.id == playingId) controller.toggleFromList()
                            else playSongs(songs.orEmpty(), false, songs.orEmpty().indexOfFirst { it.id == s.id }.coerceAtLeast(0))
                        }
                    },
                    onOpenEntry = { e -> e.artist?.let { navController.navigate(Routes.artist(it.id)) } },
                    onOpenArtistName = ::openArtistByName,
                )
                failed[key] == true -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Couldn't load this chart", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        ExpressiveButton("Retry", onClick = { failed.remove(key); loaded.remove(key); previous = selected; selected = selected }, modifier = Modifier.padding(top = 14.dp))
                    }
                }
                else -> PageLoadingState("Loading chart", detail = true)
            }
        }
    }
}

/** Chip tabs with a sliding indicator pill behind the selected one. */
@Composable
private fun TabStrip(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val scroll = androidx.compose.foundation.rememberScrollState()
    val offsets = remember(labels) { mutableStateMapOf<Int, Pair<Float, Float>>() }
    val target = offsets[selected]
    val x by animateFloatAsState(target?.first ?: 0f, VisageMotion.spatialDefault(), label = "tabX")
    val w by animateFloatAsState(target?.second ?: 0f, VisageMotion.spatialDefault(), label = "tabW")
    val pill = MaterialTheme.colorScheme.primary
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = 20.dp)
            .drawBehind {
                if (w > 0f) drawRoundRect(pill, androidx.compose.ui.geometry.Offset(x, 0f),
                    androidx.compose.ui.geometry.Size(w, size.height), androidx.compose.ui.geometry.CornerRadius(size.height / 2))
            },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            val color by animateColorAsState(
                if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                VisageMotion.effectsDefault(), label = "tabText",
            )
            Text(
                label,
                color = color,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier
                    .onGloballyPositioned { c -> offsets[i] = c.positionInParent().x to c.size.width.toFloat() }
                    .clip(CircleShape)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun ChartList(
    tab: ChartTab,
    entries: List<ChartEntry>,
    playingId: String?,
    isPlaying: Boolean,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onPlayEntry: (ChartEntry) -> Unit,
    onOpenEntry: (ChartEntry) -> Unit,
    onOpenArtistName: (String) -> Unit,
) {
    val state = rememberLazyListState()
    val podium = entries.take(3)
    val rest = entries.drop(3)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoColumns = maxWidth >= 1150.dp
        LazyColumn(
            state = state,
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize().smoothWheelScroll(state),
        ) {
            item(key = "header") {
                ChartHeader(tab, entries.size, onPlayAll, onShuffle, showButtons = tab !is ChartTab.Artists)
            }
            if (podium.isNotEmpty()) item(key = "podium") {
                Podium(podium, playingId, isPlaying, onPlayEntry, onOpenEntry)
            }
            if (twoColumns) {
                val half = (rest.size + 1) / 2
                items(half) { i ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.weight(1f)) {
                            RankRow(rest[i], rest.size + 3, i, playingId, isPlaying, onPlayEntry, onOpenEntry, onOpenArtistName)
                        }
                        Box(Modifier.weight(1f)) {
                            rest.getOrNull(i + half)?.let {
                                RankRow(it, rest.size + 3, i, playingId, isPlaying, onPlayEntry, onOpenEntry, onOpenArtistName)
                            }
                        }
                    }
                }
            } else {
                itemsIndexed(rest, key = { _, e -> e.id + e.rank }) { i, e ->
                    RankRow(e, rest.size + 3, i, playingId, isPlaying, onPlayEntry, onOpenEntry, onOpenArtistName)
                }
            }
        }
    }
}

@Composable
private fun ChartHeader(tab: ChartTab, count: Int, onPlayAll: () -> Unit, onShuffle: () -> Unit, showButtons: Boolean) {
    val title = when (tab) {
        is ChartTab.Playlist -> tab.item.title
        is ChartTab.Songs -> tab.title
        is ChartTab.Artists -> tab.title
    }
    val sub = when (tab) {
        is ChartTab.Playlist -> listOfNotNull("YouTube Music chart", tab.item.songCountText).joinToString("  ·  ")
        is ChartTab.Songs -> "YouTube Music chart  ·  $count tracks"
        is ChartTab.Artists -> "Ranked by subscribers  ·  $count artists"
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(6.dp).height(64.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        if (showButtons) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(start = 16.dp)) {
                ExpressiveButton("Play", onClick = onPlayAll, size = ExpressiveSize.Small, leadingIcon = Icons.Filled.PlayArrow)
                ExpressiveButton("Shuffle", onClick = onShuffle, size = ExpressiveSize.Small, style = ExpressiveStyle.Tonal, leadingIcon = Icons.Filled.Shuffle)
            }
        }
    }
}

@Composable
private fun rankColor(rank: Int): Color = when (rank) {
    1 -> MaterialTheme.colorScheme.primary
    2 -> MaterialTheme.colorScheme.secondary
    3 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun Podium(
    top: List<ChartEntry>,
    playingId: String?,
    isPlaying: Boolean,
    onPlay: (ChartEntry) -> Unit,
    onOpen: (ChartEntry) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        top.forEachIndexed { i, e ->
            Box(Modifier.weight(if (e.rank == 1) 1.15f else 1f)) {
                PodiumCard(e, i, playingId == e.id && e.song != null, isPlaying, onPlay, onOpen)
            }
        }
    }
}

@Composable
private fun PodiumCard(
    e: ChartEntry,
    index: Int,
    active: Boolean,
    isPlaying: Boolean,
    onPlay: (ChartEntry) -> Unit,
    onOpen: (ChartEntry) -> Unit,
) {
    val appear = rememberAppear(index)
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val lift by animateDpAsState(if (hovered) (-6).dp else 0.dp, VisageMotion.spatialFast(), label = "podiumLift")
    val container by animateColorAsState(
        if (hovered || active) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh,
        VisageMotion.effectsDefault(), label = "podiumBg",
    )
    val accent = rankColor(e.rank)
    val round = e.artist != null
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = appear.value; translationY = (1f - appear.value) * 40f }
            .offset(y = lift)
            .clip(RoundedCornerShape(24.dp))
            .background(container)
            .hoverable(source)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = source, indication = null) { if (e.song != null) onPlay(e) else onOpen(e) }
            .padding(if (e.rank == 1) 22.dp else 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                e.rank.toString(),
                fontSize = if (e.rank == 1) 72.sp else 56.sp,
                fontWeight = FontWeight.Black,
                color = accent,
            )
            Spacer(Modifier.weight(1f))
            MoveBadge(e.move)
        }
        Box(Modifier.padding(top = 4.dp).fillMaxWidth(if (e.rank == 1) 0.78f else 0.68f).aspectRatio(1f)) {
            CoverImage(
                model = e.cover, seed = e.id, label = e.title,
                modifier = Modifier.fillMaxSize().clip(if (round) CircleShape else RoundedCornerShape(16.dp)),
            )
            if (e.song != null) {
                val shown by animateFloatAsState(if (hovered || active) 1f else 0f, VisageMotion.fade(220), label = "podPlay")
                Box(
                    Modifier.align(Alignment.Center).alpha(shown).size(56.dp).clip(CircleShape).background(accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (active && isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(30.dp))
                }
            }
        }
        Text(
            e.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 14.dp),
        )
        if (e.credits.isNotBlank()) {
            Text(e.credits, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun rememberAppear(index: Int): Animatable<Float, androidx.compose.animation.core.AnimationVector1D> {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index % 14) * 40L)
        a.animateTo(1f, VisageMotion.spatialSlow())
    }
    return a
}

@Composable
private fun MoveBadge(move: ChartMove?) {
    move ?: return
    val (text, color) = when (move) {
        is ChartMove.Up -> "▲" + (move.by?.let { " $it" } ?: "") to Color(0xFF2EA043)
        is ChartMove.Down -> "▼" + (move.by?.let { " $it" } ?: "") to Color(0xFFE5484D)
        ChartMove.New -> "NEW" to MaterialTheme.colorScheme.tertiary
        ChartMove.Same -> "=" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun RankRow(
    e: ChartEntry,
    total: Int,
    index: Int,
    playingId: String?,
    isPlaying: Boolean,
    onPlay: (ChartEntry) -> Unit,
    onOpen: (ChartEntry) -> Unit,
    onOpenArtistName: (String) -> Unit,
) {
    val appear = rememberAppear(index)
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val active = e.song != null && playingId == e.id
    val bg by animateColorAsState(
        when {
            active -> MaterialTheme.colorScheme.secondaryContainer
            hovered -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        VisageMotion.effectsDefault(), label = "rowBg",
    )
    val lift by animateDpAsState(if (hovered) 4.dp else 0.dp, VisageMotion.spatialFast(), label = "rowLift")
    val round = e.artist != null
    val bar by animateFloatAsState(if (hovered) 1f else 0.85f, VisageMotion.fade(300), label = "rowBar")
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = appear.value; translationX = (1f - appear.value) * -36f }
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .hoverable(source)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = source, indication = null) { if (e.song != null) onPlay(e) else onOpen(e) }
            .padding(start = 12.dp + lift, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            e.rank.toString(),
            modifier = Modifier.width(64.dp),
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        )
        Box(Modifier.width(44.dp)) { MoveBadge(e.move) }
        Box(Modifier.size(56.dp)) {
            CoverImage(
                model = e.cover, seed = e.id, label = e.title,
                modifier = Modifier.fillMaxSize().clip(if (round) CircleShape else RoundedCornerShape(10.dp)),
            )
            if (e.song != null) {
                val shown by animateFloatAsState(if (hovered || active) 1f else 0f, VisageMotion.fade(200), label = "rowPlay")
                Box(
                    Modifier.fillMaxSize().alpha(shown).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (active && isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                e.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
            )
            if (e.credits.isNotBlank()) {
                if (e.song != null) {
                    ArtistLinks(
                        credits = e.credits,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        hoverColor = MaterialTheme.colorScheme.primary,
                        onOpen = onOpenArtistName,
                    )
                } else {
                    Text(e.credits, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
        if (e.song != null) {
            Box(
                Modifier.padding(horizontal = 16.dp).width(110.dp).height(6.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            ) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth(rankBar(e.rank, total) * bar).clip(CircleShape)
                        .background(if (e.rank <= 10) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                )
            }
            Text(e.duration, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(48.dp))
        }
    }
}
