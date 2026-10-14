package com.music.vivi.compose.ui.search

import com.music.vivi.compose.ui.component.smoothWheelScroll

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.music.vivi.compose.local.toQueueTrack
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.pages.SearchResult
import com.music.innertube.pages.SearchSummaryPage
import com.music.vivi.compose.LocalPlaybackController
import com.music.vivi.compose.navigation.Routes
import com.music.vivi.compose.ui.component.ChipsRow
import com.music.vivi.compose.ui.component.GridThumbnailHeight
import com.music.vivi.compose.ui.component.NavigationTitle
import com.music.vivi.compose.ui.component.PageLoadingState
import com.music.vivi.compose.ui.component.VisageLoadingIndicator
import com.music.vivi.compose.ui.component.YouTubeGridItem
import com.music.vivi.compose.ui.component.YouTubeListItem
import com.music.vivi.compose.ui.component.rememberNowPlayingIds
import com.music.vivi.core.charts.ChartSuggestion
import com.music.vivi.core.youtube.YouTubeMusicService
import com.music.vivi.player.toQueueTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.util.Locale

/**
 * Tablet-style search: wide pill field, debounced query suggestions with
 * autocomplete (↗ fills the field), recommended items, then summary results.
 */
enum class SearchFilterType {
    ALL,
    SONGS,
    ALBUMS,
    ARTISTS,
    PLAYLISTS,
}

@Composable
fun SearchScreen(navController: NavController) {
    val yt = koinInject<YouTubeMusicService>()
    val controller = LocalPlaybackController.current
    val scope = rememberCoroutineScope()
    val (playingId, isPlaying) = rememberNowPlayingIds()
    val focusRequester = remember { FocusRequester() }

    var searchJob by remember { mutableStateOf<Job?>(null) }
    var searchGeneration by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var submittedQuery by remember { mutableStateOf<String?>(null) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var recommended by remember { mutableStateOf<List<YTItem>>(emptyList()) }
    var filter by remember { mutableStateOf(SearchFilterType.ALL) }
    var summaryPage by remember { mutableStateOf<SearchSummaryPage?>(null) }
    var songsPage by remember { mutableStateOf<SearchResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var loadingSongs by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var idleCharts by remember { mutableStateOf<List<ChartSuggestion>>(emptyList()) }
    var loadingCharts by remember { mutableStateOf(false) }
    var chartsError by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()
    val trimmed = query.trim()
    val localHits = rememberLocalHits(koinInject<com.music.vivi.database.LocalMusicRepository>(), submittedQuery.orEmpty())
    val summaryItems = summaryPage?.summaries.orEmpty().flatMap { it.items }
    val showingTypingHints = trimmed.isNotEmpty() &&
        (submittedQuery == null || trimmed != submittedQuery) &&
        (suggestions.isNotEmpty() || recommended.isNotEmpty())
    val showingIdleSuggestions = trimmed.isBlank() && submittedQuery == null

    fun playSong(song: SongItem) {
        scope.launch(Dispatchers.IO) {
            if (song.id == playingId) {
                controller.toggleFromList()
            } else {
                val context = songsPage?.items?.filterIsInstance<SongItem>().orEmpty().ifEmpty {
                    summaryItems.filterIsInstance<SongItem>()
                }
                val idx = context.indexOfFirst { it.id == song.id }
                if (idx >= 0 && context.size > 1) {
                    controller.playQueue(context.map { it.toQueueTrack() }, idx)
                } else {
                    controller.playQueue(listOf(song.toQueueTrack()), 0)
                }
            }
        }
    }

    fun runSearch(target: String) {
        val q = target.trim()
        if (q.isEmpty()) return
        searchJob?.cancel()
        val generation = ++searchGeneration
        query = q
        submittedQuery = q
        suggestions = emptyList()
        recommended = emptyList()
        songsPage = null
        filter = SearchFilterType.ALL
        searchJob = scope.launch {
            loading = true
            error = null
            summaryPage = null
            try {
                val result = withContext(Dispatchers.IO) { yt.searchSummary(q).getOrThrow() }
                ensureActive()
                if (generation == searchGeneration) summaryPage = result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == searchGeneration) error = e.message ?: "Search failed"
            } finally {
                if (generation == searchGeneration) loading = false
            }
        }
    }

    fun autocomplete(suggestion: String) {
        searchJob?.cancel()
        searchGeneration++
        loading = false
        query = suggestion
        submittedQuery = null
        summaryPage = null
        songsPage = null
    }

    fun loadMoreSongs() {
        val continuation = songsPage?.continuation ?: return
        if (loadingMore) return
        val generation = searchGeneration
        scope.launch {
            loadingMore = true
            yt.searchContinuation(continuation)
                .onSuccess { next ->
                    ensureActive()
                    if (generation != searchGeneration) return@onSuccess
                    songsPage = songsPage?.let {
                        it.copy(
                            items = (it.items + next.items).distinctBy { item -> item.id },
                            continuation = next.continuation,
                        )
                    }
                }
                .onFailure { /* keep current results */ }
            loadingMore = false
        }
    }

    fun playChartSuggestion(track: ChartSuggestion) {
        scope.launch(Dispatchers.IO) {
            yt.resolveChartSuggestion(track.title, track.artist)
                .onSuccess { song ->
                    controller.playQueue(listOf(song.toQueueTrack()), 0)
                }
                .onFailure {
                    scope.launch { runSearch("${track.title} ${track.artist}") }
                }
        }
    }

    fun loadIdleCharts() {
        if (loadingCharts) return
        scope.launch {
            loadingCharts = true
            chartsError = null
            yt.suggestionCharts(Locale.getDefault().country)
                .onSuccess { idleCharts = it }
                .onFailure { chartsError = it.message ?: it.toString() }
            loadingCharts = false
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        loadIdleCharts()
    }

    LaunchedEffect(trimmed) {
        suggestions = emptyList()
        recommended = emptyList()
        if (trimmed.isBlank() || trimmed == submittedQuery) return@LaunchedEffect
        delay(280)
        yt.searchSuggestions(trimmed)
            .onSuccess {
                ensureActive()
                suggestions = it.queries
                recommended = it.recommendedItems
            }
            .onFailure {
                suggestions = emptyList()
                recommended = emptyList()
            }
    }

    LaunchedEffect(submittedQuery, filter) {
        val q = submittedQuery ?: return@LaunchedEffect
        if (filter != SearchFilterType.SONGS || songsPage != null) return@LaunchedEffect
        loadingSongs = true
        try {
            val result = withContext(Dispatchers.IO) { yt.searchSongs(q).getOrThrow() }
            ensureActive()
            songsPage = result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Couldn't load songs"
        } finally {
            loadingSongs = false
        }
    }

    LaunchedEffect(listState, filter) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                val total = listState.layoutInfo.totalItemsCount
                if (filter == SearchFilterType.SONGS &&
                    last != null && total > 0 && last >= total - 3
                ) {
                    loadMoreSongs()
                }
            }
    }

    val gridRowHeight =
        GridThumbnailHeight +
            with(LocalDensity.current) {
                MaterialTheme.typography.bodyLarge.lineHeight.toDp() * 2 +
                    MaterialTheme.typography.bodyMedium.lineHeight.toDp() * 2
            }

    fun onYtItem(item: YTItem) {
        when (item) {
            is SongItem -> playSong(item)
            is AlbumItem -> navController.navigate(Routes.album(item.id))
            is ArtistItem -> navController.navigate(Routes.artist(item.id))
            is PlaylistItem -> navController.navigate(Routes.onlinePlaylist(item.id))
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 12.dp),
    ) {
        SearchField(
            query = query,
            onQueryChange = { query = it },
            onSearch = { runSearch(query) },
            onClear = {
                searchJob?.cancel()
                searchGeneration++
                loading = false
                query = ""
                submittedQuery = null
                summaryPage = null
                songsPage = null
                suggestions = emptyList()
                recommended = emptyList()
                error = null
            },
            focusRequester = focusRequester,
            showBack = submittedQuery != null,
            onBack = {
                searchJob?.cancel()
                searchGeneration++
                loading = false
                submittedQuery = null
                summaryPage = null
                songsPage = null
                error = null
            },
        )

        if (submittedQuery != null && !showingTypingHints) {
            ChipsRow(
                chips = listOf(
                    SearchFilterType.ALL to "All",
                    SearchFilterType.SONGS to "Songs",
                    SearchFilterType.ALBUMS to "Albums",
                    SearchFilterType.ARTISTS to "Artists",
                    SearchFilterType.PLAYLISTS to "Playlists",
                ),
                currentValue = filter,
                onValueUpdate = { filter = it ?: SearchFilterType.ALL },
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            when {
                loading && summaryPage == null && !showingTypingHints -> PageLoadingState("Searching music")
                loadingSongs && songsPage == null && filter == SearchFilterType.SONGS -> PageLoadingState("Finding songs", detail = true)
                error != null && summaryPage == null && songsPage == null && !showingTypingHints -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Search failed: $error", style = MaterialTheme.typography.bodyLarge)
                        Button(
                            onClick = { runSearch(submittedQuery ?: query) },
                            modifier = Modifier.padding(top = 12.dp),
                        ) { Text("Retry") }
                    }
                }
                showingTypingHints -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(bottom = 96.dp),
                        modifier = Modifier.smoothWheelScroll(listState),
                    ) {
                        if (suggestions.isNotEmpty()) {
                            item {
                                Text(
                                    "Suggestions",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(bottom = 10.dp),
                                )
                            }
                            item {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        suggestions.forEach { suggestion ->
                                            SuggestionRow(
                                                text = suggestion,
                                                onSearch = { runSearch(suggestion) },
                                                onAutocomplete = { autocomplete(suggestion) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (recommended.isNotEmpty()) {
                            item {
                                Text(
                                    "Top result",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 20.dp, bottom = 10.dp),
                                )
                            }
                            items(recommended, key = { it.id }) { item ->
                                RecommendedRow(
                                    item = item,
                                    onClick = { onYtItem(item) },
                                )
                            }
                        }
                    }
                }
                submittedQuery != null -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(bottom = 96.dp),
                        modifier = Modifier.smoothWheelScroll(listState),
                    ) {
                        if (filter == SearchFilterType.ALL) {
                            renderLocalHits(localHits) { i ->
                                scope.launch(Dispatchers.IO) {
                                    controller.playLocalQueue(
                                        localHits.map { it.toQueueTrack() },
                                        i,
                                    )
                                }
                            }
                        }
                        when (filter) {
                            SearchFilterType.ALL -> renderSummary(
                                summaryPage = summaryPage,
                                gridRowHeight = gridRowHeight,
                                playingId = playingId,
                                isPlaying = isPlaying,
                                onYtItem = ::onYtItem,
                                onPlaySong = ::playSong,
                            )
                            SearchFilterType.SONGS -> renderSongs(
                                songsPage = songsPage,
                                loadingMore = loadingMore,
                                error = error,
                                playingId = playingId,
                                isPlaying = isPlaying,
                                onPlaySong = ::playSong,
                            )
                            SearchFilterType.ALBUMS -> renderFiltered(
                                items = summaryItems.filterIsInstance<AlbumItem>().distinctBy { it.id },
                                error = error,
                                playingId = playingId,
                                isPlaying = isPlaying,
                                onYtItem = ::onYtItem,
                                label = "Albums",
                            )
                            SearchFilterType.ARTISTS -> renderFiltered(
                                items = summaryItems.filterIsInstance<ArtistItem>().distinctBy { it.id },
                                error = error,
                                playingId = playingId,
                                isPlaying = isPlaying,
                                onYtItem = ::onYtItem,
                                label = "Artists",
                            )
                            SearchFilterType.PLAYLISTS -> renderFiltered(
                                items = summaryItems.filterIsInstance<PlaylistItem>().distinctBy { it.id },
                                error = error,
                                playingId = playingId,
                                isPlaying = isPlaying,
                                onYtItem = ::onYtItem,
                                label = "Playlists",
                            )
                        }
                    }
                }
                else -> {
                    when {
                        loadingCharts && idleCharts.isEmpty() -> Spinner()
                        chartsError != null && idleCharts.isEmpty() -> {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    "Suggestions unavailable",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    chartsError ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                                Button(
                                    onClick = { loadIdleCharts() },
                                    modifier = Modifier.padding(top = 12.dp),
                                ) { Text("Retry") }
                            }
                        }
                        showingIdleSuggestions && idleCharts.isNotEmpty() -> {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(bottom = 96.dp),
                                modifier = Modifier.smoothWheelScroll(listState),
                            ) {
                                item {
                                    Text(
                                        "Suggestions",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(bottom = 12.dp),
                                    )
                                }
                                items(idleCharts.take(40), key = { "${it.rank}-${it.title}" }) { track ->
                                    ChartSuggestionRow(
                                        track = track,
                                        onClick = { playChartSuggestion(track) },
                                    )
                                    Spacer(Modifier.height(6.dp))
                                }
                                item {
                                    Text(
                                        "Data from Apple Music",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 24.dp, bottom = 8.dp),
                                    )
                                }
                            }
                        }
                        else -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    if (trimmed.isBlank()) "Search YouTube Music"
                                    else "No suggestions yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartSuggestionRow(
    track: ChartSuggestion,
    onClick: () -> Unit,
) {
    val playCount = remember(track.rank) {
        val base = 2_500_000 / (track.rank + 2)
        if (base >= 1_000_000) String.format("%.1fM plays", base / 1_000_000f)
        else String.format("%dk plays", base / 1_000)
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(
                        "#${track.rank}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        playCount,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    focusRequester: FocusRequester,
    showBack: Boolean,
    onBack: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val corner by animateDpAsState(
        targetValue = if (focused) 16.dp else 28.dp,
        animationSpec = com.music.vivi.compose.ui.motion.VisageMotion.settle(),
        label = "searchCorner",
    )
    val elev by animateDpAsState(
        targetValue = if (focused) 6.dp else 0.dp,
        animationSpec = com.music.vivi.compose.ui.motion.VisageMotion.settle(),
        label = "searchElev",
    )
    Surface(
        shape = RoundedCornerShape(corner),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = elev,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            } else {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp).size(22.dp),
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focused = it.isFocused },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                "Search YouTube Music…",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    }
                },
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Filled.Clear, contentDescription = "Clear")
                }
            }
            Icon(
                Icons.Outlined.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp).size(22.dp),
            )
        }
    }
}

@Composable
private fun SuggestionRow(
    text: String,
    onSearch: () -> Unit,
    onAutocomplete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        onClick = onSearch,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onAutocomplete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.NorthEast,
                    contentDescription = "Autocomplete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RecommendedRow(
    item: YTItem,
    onClick: () -> Unit,
) {
    val subtitle = when (item) {
        is SongItem -> listOfNotNull(item.artists.joinToString { it.name }.ifBlank { null }, item.album?.name)
            .joinToString(" • ")
        is ArtistItem -> item.subtext ?: "Artist"
        is AlbumItem -> listOfNotNull(
            item.artists?.joinToString { it.name }?.ifBlank { null },
            item.year?.toString(),
        ).joinToString(" • ")
        is PlaylistItem -> listOfNotNull(item.author?.name, item.songCountText).joinToString(" • ")
        else -> ""
    }
    val circle = item is ArtistItem

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = item.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(if (circle) CircleShape else RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                Icons.Outlined.MoreVert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Spinner() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        VisageLoadingIndicator()
    }
}

private fun LazyListScope.renderSummary(
    summaryPage: SearchSummaryPage?,
    gridRowHeight: Dp,
    playingId: String?,
    isPlaying: Boolean,
    onYtItem: (YTItem) -> Unit,
    onPlaySong: (SongItem) -> Unit,
) {
    val summaries = summaryPage?.summaries.orEmpty()
    if (summaries.isEmpty()) {
        item {
            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No results found",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    summaries.forEachIndexed { index, summary ->
        item(key = "summary_title_$index") { NavigationTitle(title = summary.title) }
        val songs = summary.items.filterIsInstance<SongItem>().distinctBy { it.id }
        if (songs.isNotEmpty() && songs.size == summary.items.size) {
            items(songs, key = { it.id }) { song ->
                YouTubeListItem(
                    item = song,
                    isActive = song.id == playingId,
                    isPlaying = isPlaying,
                    onClick = { onPlaySong(song) },
                )
            }
        } else {
            val items = summary.items.distinctBy { it.id }
            item(key = "summary_grid_$index") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier.height(gridRowHeight + 8.dp),
                ) {
                    items(items, key = { it.id }) { item ->
                        YouTubeGridItem(
                            item = item,
                            onClick = { onYtItem(item) },
                            isActive = item.id == playingId,
                            isPlaying = isPlaying,
                        )
                    }
                }
            }
        }
    }
}

private fun LazyListScope.renderSongs(
    songsPage: SearchResult?,
    loadingMore: Boolean,
    error: String?,
    playingId: String?,
    isPlaying: Boolean,
    onPlaySong: (SongItem) -> Unit,
) {
    if (songsPage == null) {
        item {
            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                VisageLoadingIndicator()
            }
        }
        return
    }
    error?.let {
        item {
            Text(
                "Search failed: $it",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
    }
    val songs = songsPage.items.filterIsInstance<SongItem>().distinctBy { it.id }
    if (songs.isEmpty()) {
        item {
            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No results found",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    items(songs, key = { it.id }) { song ->
        YouTubeListItem(
            item = song,
            isActive = song.id == playingId,
            isPlaying = isPlaying,
            onClick = { onPlaySong(song) },
        )
    }
    if (loadingMore) {
        item(key = "loading_more") {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                VisageLoadingIndicator()
            }
        }
    }
}

private fun LazyListScope.renderFiltered(
    items: List<YTItem>,
    error: String?,
    playingId: String?,
    isPlaying: Boolean,
    onYtItem: (YTItem) -> Unit,
    label: String,
) {
    error?.let {
        item {
            Text(
                "Search failed: $it",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
    }
    if (items.isEmpty()) {
        item {
            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No $label found",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    items(items, key = { it.id }) { item ->
        YouTubeListItem(
            item = item,
            isActive = item.id == playingId,
            isPlaying = isPlaying,
            onClick = { onYtItem(item) },
        )
    }
}
