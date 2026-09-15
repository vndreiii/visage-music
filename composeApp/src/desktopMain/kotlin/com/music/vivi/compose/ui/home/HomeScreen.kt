package com.music.vivi.compose.ui.home

import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.music.vivi.compose.ui.component.PageLoadingState
import com.music.vivi.compose.ui.component.ViviLoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.pages.HomePage
import com.music.vivi.compose.LocalPlaybackController
import com.music.vivi.compose.navigation.Routes
import com.music.vivi.compose.ui.component.ChipsRow
import com.music.vivi.compose.ui.component.GridThumbnailHeight
import com.music.vivi.compose.ui.component.HeroCardHeight
import com.music.vivi.compose.ui.component.HeroCardWideWidth
import com.music.vivi.compose.ui.component.HeroCardWidth
import com.music.vivi.compose.ui.component.ListItemHeight
import com.music.vivi.compose.ui.component.ListThumbnailSize
import com.music.vivi.compose.ui.component.PlaylistShelfCard
import com.music.vivi.compose.ui.component.ThumbnailCornerRadius
import com.music.vivi.compose.ui.component.YouTubeGridItem
import com.music.vivi.compose.ui.component.YouTubeHeroCard
import com.music.vivi.compose.ui.component.RoundedClickable
import com.music.vivi.compose.ui.component.InteractionCornerRadius
import com.music.vivi.compose.ui.component.YouTubeListItem
import com.music.vivi.compose.ui.component.horizontalWheelScroll
import com.music.vivi.compose.ui.component.rememberNowPlayingIds
import com.music.vivi.core.youtube.YouTubeMusicService
import com.music.vivi.player.toQueueTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.util.Locale

/** Soften ALL-CAPS shelf labels ("DISCOVER THE HITS!") → "Discover the hits!". */
private fun String.asSoftSentenceCase(): String {
    val trimmed = trim()
    if (trimmed.isEmpty()) return trimmed
    val letters = trimmed.filter { it.isLetter() }
    if (letters.isNotEmpty() && letters.all { it.isUpperCase() }) {
        return trimmed.lowercase(Locale.getDefault())
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
    return trimmed
}

private enum class HomeCategoryKind(val label: String) {
    Songs("Songs"),
    Playlists("Playlists"),
    Albums("Albums"),
    Artists("Artists"),
    Mixes("Mixes"),
    Featured("Featured"),
}

private fun HomePage.Section.categoryKind(songsOnly: Boolean, hero: Boolean): HomeCategoryKind {
    if (isQuickPicks() || songsOnly) return HomeCategoryKind.Songs
    if (hero || titleSuggestsPlaylistsOrMixes()) {
        val t = title.lowercase()
        return when {
            t.contains("mix") || t.contains("radio") || t.contains("discover") -> HomeCategoryKind.Mixes
            else -> HomeCategoryKind.Playlists
        }
    }
    val albums = items.count { it is AlbumItem }
    val artists = items.count { it is ArtistItem }
    val playlists = items.count { it is PlaylistItem }
    return when {
        artists > albums && artists > playlists -> HomeCategoryKind.Artists
        albums >= playlists && albums > 0 -> HomeCategoryKind.Albums
        playlists > 0 -> HomeCategoryKind.Playlists
        else -> HomeCategoryKind.Featured
    }
}

private fun HomePage.Section.isQuickPicks(): Boolean =
    title.equals("Quick picks", ignoreCase = true)

/** Song-rail / featured shelves get expressive surface pills; album carousels stay flat. */
private fun HomePage.Section.isHotSection(songsOnly: Boolean): Boolean =
    isQuickPicks() ||
        songsOnly ||
        title.contains("trending", ignoreCase = true) ||
        title.contains("mixed for you", ignoreCase = true) ||
        title.contains("forgotten favorites", ignoreCase = true)

private fun HomePage.Section.titleSuggestsPlaylistsOrMixes(): Boolean {
    val t = title.lowercase()
    return t.contains("mix") ||
        t.contains("community") ||
        t.contains("discover") ||
        t.contains("radio") ||
        t.contains("for you") ||
        t.contains("made for") ||
        t.contains("playlist") ||
        t.contains("your library") ||
        t.contains("from your")
}

/**
 * Whole shelf uses large cards when it's playlist/mix-forward.
 * Album/artist carousels stay compact; individual playlists inside a mixed
 * shelf still render big via [itemUsesLargeCard].
 */
private fun HomePage.Section.isHeroCardSection(): Boolean {
    if (items.isEmpty()) return false
    val playlists = items.count { it is PlaylistItem }
    val albums = items.count { it is AlbumItem }
    val mostlyPlaylists = playlists > 0 && playlists >= albums && playlists * 2 >= items.size
    return mostlyPlaylists || titleSuggestsPlaylistsOrMixes()
}

/** Per-item: user/community playlists & mixes → large; albums/artists → small. */
private fun itemUsesLargeCard(item: YTItem, section: HomePage.Section): Boolean =
    when (item) {
        is PlaylistItem -> true
        is SongItem -> section.titleSuggestsPlaylistsOrMixes()
        else -> false
    }

/** Song shelves that fill a full-width rail — never pair. */
private fun HomePage.Section.isSongsShelf(): Boolean =
    items.isNotEmpty() && items.all { it is SongItem }

/**
 * Sparse carousel (few cards) — candidate to sit beside another sparse shelf
 * so ultrawide home doesn't look empty.
 */
private fun HomePage.Section.isSparseShelf(): Boolean {
    if (isQuickPicks() || isSongsShelf()) return false
    return items.size in 1..4
}

/**
 * Only denser playlist/mix shelves + Quick picks get a surface frame.
 * Sparse shelves, albums, artists stay flat (title + content only).
 */
private fun HomePage.Section.wantsSurfaceFrame(): Boolean {
    if (isQuickPicks()) return true
    if (isSparseShelf()) return false
    if (isSongsShelf()) return false
    return isHeroCardSection() && items.size >= 5
}

private sealed class HomeFeedRow {
    data class Solo(val section: HomePage.Section, val index: Int) : HomeFeedRow()
    data class Duo(
        val left: HomePage.Section,
        val leftIndex: Int,
        val right: HomePage.Section,
        val rightIndex: Int,
    ) : HomeFeedRow()
}

/** Walk shelves in order; pull the next sparse shelf beside the current sparse one. */
private fun packHomeFeedRows(sections: List<HomePage.Section>): List<HomeFeedRow> {
    val remaining = sections.mapIndexed { i, s -> i to s }.toMutableList()
    val rows = mutableListOf<HomeFeedRow>()
    while (remaining.isNotEmpty()) {
        val (idx, section) = remaining.removeAt(0)
        if (section.isSparseShelf()) {
            val partnerAt = remaining.indexOfFirst { it.second.isSparseShelf() }
            if (partnerAt >= 0) {
                val (idx2, section2) = remaining.removeAt(partnerAt)
                rows += HomeFeedRow.Duo(section, idx, section2, idx2)
            } else {
                rows += HomeFeedRow.Solo(section, idx)
            }
        } else {
            rows += HomeFeedRow.Solo(section, idx)
        }
    }
    return rows
}

/**
 * Desktop port of upstream HomeScreen’s YouTube-driven layout.
 * Refresh: Ctrl/Cmd+R.
 */
@Composable
fun HomeScreen(navController: NavController) {
    val yt = koinInject<YouTubeMusicService>()
    val controller = LocalPlaybackController.current
    val scope = rememberCoroutineScope()
    val vm = remember { HomeViewModel(yt, scope) }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { vm.loadInitial() }
    LaunchedEffect(vm.homePage != null) {
        if (vm.homePage != null) focusRequester.requestFocus()
    }

    LaunchedEffect(listState, vm.homePage?.continuation) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                val total = listState.layoutInfo.totalItemsCount
                if (last != null && total > 0 && last >= total - 3) {
                    vm.loadMore()
                }
            }
    }

    fun triggerRefresh() {
        if (!vm.isRefreshing) {
            scope.launch { listState.animateScrollToItem(0) }
            vm.refresh()
        }
    }

    val (playingId, isPlaying) = rememberNowPlayingIds()

    when {
        vm.isLoading && vm.homePage == null -> {
            PageLoadingState("Finding your next listen", detail = false)
        }
        vm.error != null && vm.homePage == null -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Home failed: ${vm.error}", style = MaterialTheme.typography.bodyLarge)
                Button(onClick = { vm.refresh() }, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Retry")
                }
            }
        }
        else -> {
            // No PullToRefreshBox — its nested-scroll connection fights LazyColumn
            // wheel scrolling on desktop (intermittent / stuck). Refresh via Ctrl/Cmd+R.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown &&
                            event.key == Key.R &&
                            (event.isCtrlPressed || event.isMetaPressed)
                        ) {
                            triggerRefresh()
                            true
                        } else {
                            false
                        }
                    },
            ) {
                if (vm.isRefreshing) {
                    ViviLoadingIndicator(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp),
                    )
                }
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    // Phone uses ~47.5% width (≈2 columns). On wide desktop that leaves
                    // huge empty gutters — cap so ~3–4 song columns fit and scroll.
                    val horizontalLazyGridItemWidth = when {
                        maxWidth >= 900.dp -> 300.dp
                        maxWidth >= 600.dp -> 280.dp
                        maxWidth * 0.475f >= 320.dp -> maxWidth * 0.475f
                        else -> maxWidth * 0.9f
                    }
                    val gridRowHeight =
                        GridThumbnailHeight +
                            with(LocalDensity.current) {
                                MaterialTheme.typography.bodyLarge.lineHeight.toDp() * 2 +
                                    MaterialTheme.typography.bodyMedium.lineHeight.toDp() * 2
                            }

                    fun playSong(song: SongItem, context: List<SongItem> = emptyList()) {
                        scope.launch(Dispatchers.IO) {
                            if (song.id == playingId) {
                                if (isPlaying) controller.pause() else controller.play()
                            } else if (context.isNotEmpty()) {
                                val idx = context.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                                controller.playQueue(context.map { it.toQueueTrack() }, idx)
                            } else {
                                controller.playVideo(song.id)
                            }
                        }
                    }

                    fun onYtItem(item: YTItem, context: List<SongItem> = emptyList()) {
                        when (item) {
                            is SongItem -> playSong(item, context)
                            is AlbumItem -> navController.navigate(Routes.album(item.id))
                            is ArtistItem -> navController.navigate(Routes.artist(item.id))
                            is PlaylistItem -> navController.navigate(Routes.onlinePlaylist(item.id))
                        }
                    }

                    val sections = remember(vm.homePage, vm.accountPlaylists) {
                        prepareHomeSections(vm.homePage?.sections.orEmpty(), vm.accountPlaylists.map { it.id }.toSet())
                    }
                    val (quickPicks, otherSections) = sections.partition { it.isQuickPicks() }
                    val showAccountPlaylists =
                        vm.selectedChip == null && vm.accountPlaylists.isNotEmpty()

                    fun LazyListScope.emitSection(
                        orderIndex: Int,
                        sectionData: HomePage.Section,
                        halfWidth: Boolean = false,
                    ) {
                        homeSectionItems(
                            keyPrefix = "home_section_$orderIndex",
                            sectionData = sectionData,
                            horizontalLazyGridItemWidth = horizontalLazyGridItemWidth,
                            gridRowHeight = gridRowHeight,
                            playingId = playingId,
                            isPlaying = isPlaying,
                            framed = sectionData.wantsSurfaceFrame() && !halfWidth,
                            halfWidth = halfWidth,
                            onPlaySong = ::playSong,
                            onYtItem = ::onYtItem,
                            onBrowse = { browseId -> navController.navigate(Routes.browse(browseId)) },
                            onPlayAll = { songs ->
                                scope.launch(Dispatchers.IO) {
                                    controller.playQueue(songs.map { it.toQueueTrack() }, 0)
                                }
                            },
                        )
                    }

                    fun LazyListScope.emitAccountPlaylists() {
                        item(key = "account_playlists") {
                            HomeCategoryFrame(
                                kind = HomeCategoryKind.Playlists,
                                emphasized = true,
                            ) {
                                HomeCategoryHeader(
                                    kind = HomeCategoryKind.Playlists,
                                    title = vm.accountInfo?.name ?: "You",
                                    strapline = "Your YouTube Music playlists",
                                    thumbnail = {
                                        val url = vm.accountInfo?.thumbnailUrl
                                        if (url != null) {
                                            AsyncImage(
                                                model = url,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(ListThumbnailSize)
                                                    .clip(CircleShape),
                                            )
                                        }
                                    },
                                    onClick = null,
                                    onPlayAllClick = null,
                                )
                                val rowState = rememberLazyListState()
                                LazyRow(
                                    state = rowState,
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(244.dp)
                                        .horizontalWheelScroll(rowState),
                                ) {
                                    items(
                                        items = vm.accountPlaylists.distinctBy { it.id },
                                        key = { it.id },
                                    ) { item ->
                                        YouTubeGridItem(
                                            item = item,
                                            onClick = { onYtItem(item) },
                                            modifier = Modifier.width(180.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    val feedRows = packHomeFeedRows(otherSections)

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 128.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        item(key = "chips") {
                            val chips = vm.homePage?.chips
                                ?.filter { !it.title.equals("Podcasts", ignoreCase = true) }
                                ?.map { it to it.title }
                                .orEmpty()
                            if (chips.isNotEmpty()) {
                                ChipsRow(
                                    chips = chips,
                                    currentValue = vm.selectedChip,
                                    onValueUpdate = { vm.toggleChip(it) },
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }
                        }

                        // 1) Quick picks  2) Your playlists  3) Packed feed (sparse pairs)
                        quickPicks.forEachIndexed { i, sectionData ->
                            emitSection(i, sectionData)
                        }
                        if (showAccountPlaylists) {
                            emitAccountPlaylists()
                        }
                        feedRows.forEach { row ->
                            when (row) {
                                is HomeFeedRow.Solo -> {
                                    emitSection(quickPicks.size + row.index, row.section)
                                }
                                is HomeFeedRow.Duo -> {
                                    item(key = "duo_${row.leftIndex}_${row.rightIndex}") {
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        ) {
                                            Box(Modifier.weight(1f)) {
                                                HomeSectionBlock(
                                                    sectionData = row.left,
                                                    hot = false,
                                                    hero = row.left.isHeroCardSection(),
                                                    isSongsOnly = false,
                                                    sectionSongs = emptyList(),
                                                    horizontalLazyGridItemWidth = horizontalLazyGridItemWidth,
                                                    gridRowHeight = gridRowHeight,
                                                    playingId = playingId,
                                                    isPlaying = isPlaying,
                                                    framed = false,
                                                    halfWidth = true,
                                                    onPlaySong = ::playSong,
                                                    onYtItem = ::onYtItem,
                                                    onBrowse = { browseId ->
                                                        navController.navigate(Routes.browse(browseId))
                                                    },
                                                    onPlayAll = { songs ->
                                                        scope.launch(Dispatchers.IO) {
                                                            controller.playQueue(
                                                                songs.map { it.toQueueTrack() },
                                                                0,
                                                            )
                                                        }
                                                    },
                                                )
                                            }
                                            Box(Modifier.weight(1f)) {
                                                HomeSectionBlock(
                                                    sectionData = row.right,
                                                    hot = false,
                                                    hero = row.right.isHeroCardSection(),
                                                    isSongsOnly = false,
                                                    sectionSongs = emptyList(),
                                                    horizontalLazyGridItemWidth = horizontalLazyGridItemWidth,
                                                    gridRowHeight = gridRowHeight,
                                                    playingId = playingId,
                                                    isPlaying = isPlaying,
                                                    framed = false,
                                                    halfWidth = true,
                                                    onPlaySong = ::playSong,
                                                    onYtItem = ::onYtItem,
                                                    onBrowse = { browseId ->
                                                        navController.navigate(Routes.browse(browseId))
                                                    },
                                                    onPlayAll = { songs ->
                                                        scope.launch(Dispatchers.IO) {
                                                            controller.playQueue(
                                                                songs.map { it.toQueueTrack() },
                                                                0,
                                                            )
                                                        }
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (vm.isLoadingMore || vm.isLoading) {
                            item(key = "loading_more") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    ViviLoadingIndicator()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.homeSectionItems(
    keyPrefix: String,
    sectionData: HomePage.Section,
    horizontalLazyGridItemWidth: Dp,
    gridRowHeight: Dp,
    playingId: String?,
    isPlaying: Boolean,
    framed: Boolean,
    halfWidth: Boolean,
    onPlaySong: (SongItem, List<SongItem>) -> Unit,
    onYtItem: (YTItem, List<SongItem>) -> Unit,
    onBrowse: (String) -> Unit,
    onPlayAll: (List<SongItem>) -> Unit,
) {
    val sectionSongs = sectionData.items.filterIsInstance<SongItem>()
    val isSongsOnly = sectionData.items.isNotEmpty() &&
        sectionData.items.all { it is SongItem }
    val hero = !isSongsOnly && sectionData.isHeroCardSection()
    val hot = !hero && sectionData.isHotSection(isSongsOnly)

    item(key = "${keyPrefix}_block") {
        HomeSectionBlock(
            sectionData = sectionData,
            hot = hot,
            hero = hero,
            isSongsOnly = isSongsOnly,
            sectionSongs = sectionSongs,
            horizontalLazyGridItemWidth = horizontalLazyGridItemWidth,
            gridRowHeight = gridRowHeight,
            playingId = playingId,
            isPlaying = isPlaying,
            framed = framed,
            halfWidth = halfWidth,
            onPlaySong = onPlaySong,
            onYtItem = onYtItem,
            onBrowse = onBrowse,
            onPlayAll = onPlayAll,
        )
    }
}

@Composable
private fun HomeSectionBlock(
    sectionData: HomePage.Section,
    hot: Boolean,
    hero: Boolean,
    isSongsOnly: Boolean,
    sectionSongs: List<SongItem>,
    horizontalLazyGridItemWidth: Dp,
    gridRowHeight: Dp,
    playingId: String?,
    isPlaying: Boolean,
    framed: Boolean,
    halfWidth: Boolean,
    onPlaySong: (SongItem, List<SongItem>) -> Unit,
    onYtItem: (YTItem, List<SongItem>) -> Unit,
    onBrowse: (String) -> Unit,
    onPlayAll: (List<SongItem>) -> Unit,
) {
    val kind = sectionData.categoryKind(isSongsOnly, hero)
    val isQuickPicks = sectionData.isQuickPicks()
    var shuffleNonce by remember(sectionData.title, sectionSongs.map { it.id }) {
        mutableIntStateOf(0)
    }
    val displaySongs = remember(sectionSongs, shuffleNonce) {
        val distinct = sectionSongs.distinctBy { it.id }
        if (shuffleNonce == 0) distinct else distinct.shuffled()
    }
    // Quick picks: taller grid (6 rows) + denser columns so more tracks show at once.
    val songGridRows = if (isQuickPicks) 6.coerceAtMost(displaySongs.size.coerceAtLeast(1)) else 4.coerceAtMost(displaySongs.size.coerceAtLeast(1))
    val songGridItemWidth =
        if (isQuickPicks) (horizontalLazyGridItemWidth * 0.88f).coerceAtLeast(240.dp)
        else horizontalLazyGridItemWidth

    val sectionTitle: @Composable () -> Unit = {
        HomeCategoryHeader(
            kind = kind,
            title = sectionData.title,
            strapline = sectionData.label,
            thumbnail = sectionData.thumbnail?.let { thumbnailUrl ->
                {
                    val shape =
                        if (sectionData.endpoint?.isArtistEndpoint == true) {
                            CircleShape
                        } else {
                            RoundedCornerShape(ThumbnailCornerRadius)
                        }
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(ListThumbnailSize)
                            .clip(shape),
                    )
                }
            },
            onClick = sectionData.endpoint?.browseId?.let { browseId ->
                { onBrowse(browseId) }
            },
            onShuffleClick = if (isQuickPicks && displaySongs.size > 1) {
                { shuffleNonce += 1 }
            } else {
                null
            },
            onPlayAllClick = if (displaySongs.isNotEmpty()) {
                { onPlayAll(displaySongs) }
            } else {
                null
            },
        )
    }

    val sectionBody: @Composable () -> Unit = {
        when {
            isSongsOnly -> {
                val gridState = rememberLazyGridState()
                LazyHorizontalGrid(
                    rows = GridCells.Fixed(songGridRows),
                    state = gridState,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ListItemHeight * songGridRows)
                        .horizontalWheelScroll(gridState),
                ) {
                    itemsIndexed(
                        items = displaySongs,
                        key = { _, it -> "${it.id}_$shuffleNonce" },
                    ) { _, song ->
                        YouTubeListItem(
                            item = song,
                            isActive = song.id == playingId,
                            isPlaying = isPlaying,
                            onClick = { onPlaySong(song, displaySongs) },
                            modifier = Modifier.width(songGridItemWidth),
                        )
                    }
                }
            }
            hero -> {
                val rowState = rememberLazyListState()
                val playlistsOnly = sectionData.items.all { it is PlaylistItem }
                val tall = false
                val cardH = when {
                    tall -> 320.dp
                    halfWidth -> 220.dp
                    else -> 260.dp
                }
                val cardW = if (halfWidth) 168.dp else 200.dp
                LazyRow(
                    state = rowState,
                    contentPadding = PaddingValues(horizontal = if (halfWidth) 4.dp else 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cardH + 8.dp)
                        .horizontalWheelScroll(rowState),
                ) {
                    items(sectionData.items, key = { it.id }) { item ->
                        when {
                            item is PlaylistItem -> {
                                YouTubeGridItem(
                                    item = item,
                                    onClick = { onYtItem(item, emptyList()) },
                                    modifier = Modifier.width(cardW),
                                )
                            }
                            itemUsesLargeCard(item, sectionData) -> {
                                YouTubeHeroCard(
                                    item = item,
                                    onClick = { onYtItem(item, emptyList()) },
                                    width = when {
                                        halfWidth -> cardW
                                        sectionData.items.size <= 2 -> HeroCardWideWidth
                                        else -> HeroCardWidth
                                    },
                                    height = cardH,
                                    caption = sectionData.label,
                                    parallaxPx = rowState.firstVisibleItemScrollOffset.toFloat(),
                                )
                            }
                            else -> {
                                YouTubeGridItem(
                                    item = item,
                                    onClick = { onYtItem(item, emptyList()) },
                                    isActive = item.id == playingId,
                                    isPlaying = isPlaying,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
            else -> {
                val rowState = rememberLazyListState()
                val hasLarge = sectionData.items.any { itemUsesLargeCard(it, sectionData) }
                val rowH = when {
                    halfWidth && hasLarge -> 228.dp
                    hasLarge -> HeroCardHeight + 8.dp
                    else -> gridRowHeight + 8.dp
                }
                LazyRow(
                    state = rowState,
                    contentPadding = PaddingValues(horizontal = if (halfWidth) 4.dp else 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowH)
                        .horizontalWheelScroll(rowState),
                ) {
                    items(sectionData.items, key = { it.id }) { item ->
                        if (itemUsesLargeCard(item, sectionData)) {
                            YouTubeHeroCard(
                                item = item,
                                onClick = { onYtItem(item, emptyList()) },
                                width = if (halfWidth) 168.dp else if (item is PlaylistItem) 260.dp else HeroCardWidth,
                                height = if (halfWidth) 220.dp else HeroCardHeight,
                                caption = sectionData.label,
                                parallaxPx = rowState.firstVisibleItemScrollOffset.toFloat(),
                            )
                        } else {
                            YouTubeGridItem(
                                item = item,
                                onClick = { onYtItem(item, emptyList()) },
                                isActive = item.id == playingId,
                                isPlaying = isPlaying,
                            )
                        }
                    }
                }
            }
        }
    }

    if (framed) {
        HomeCategoryFrame(kind = kind, emphasized = hot || hero) {
            sectionTitle()
            sectionBody()
        }
    } else {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = if (halfWidth) 4.dp else 12.dp, vertical = 8.dp),
        ) {
            sectionTitle()
            sectionBody()
        }
    }
}

@Composable
private fun HomeCategoryHeader(
    kind: HomeCategoryKind,
    title: String,
    strapline: String?,
    thumbnail: (@Composable () -> Unit)?,
    onClick: (() -> Unit)?,
    onPlayAllClick: (() -> Unit)?,
    onShuffleClick: (() -> Unit)? = null,
) {
    // kind kept for call-site / frame coloring; chrome is title + bg only
    @Suppress("UNUSED_PARAMETER")
    val unusedKind = kind
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        thumbnail?.invoke()
        val softStrap = strapline?.takeIf { it.isNotBlank() }?.asSoftSentenceCase()
        val titleText: @Composable () -> Unit = {
            Text(
                text = buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        append(title)
                    }
                    if (softStrap != null) {
                        withStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        ) {
                            append(" · ")
                            append(softStrap)
                        }
                    }
                },
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
        if (onClick != null) {
            RoundedClickable(
                onClick = onClick,
                shape = RoundedCornerShape(InteractionCornerRadius),
                modifier = Modifier.weight(1f),
            ) {
                titleText()
            }
        } else {
            Box(Modifier.weight(1f)) {
                titleText()
            }
        }
        if (onShuffleClick != null) {
            IconButton(onClick = onShuffleClick) {
                Icon(
                    Icons.Outlined.Shuffle,
                    contentDescription = "Shuffle quick picks",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (onPlayAllClick != null) {
            TextButton(onClick = onPlayAllClick) {
                Text("Play all")
            }
        }
    }
}

@Composable
private fun HomeCategoryFrame(
    kind: HomeCategoryKind,
    emphasized: Boolean,
    content: @Composable () -> Unit,
) {
    val container = when {
        emphasized && kind == HomeCategoryKind.Songs ->
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.50f)
        emphasized && (kind == HomeCategoryKind.Playlists || kind == HomeCategoryKind.Mixes) ->
            MaterialTheme.colorScheme.surfaceContainerHigh
        else ->
            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        shape = RoundedCornerShape(24.dp),
        color = container,
    ) {
        Column(Modifier.padding(bottom = 14.dp, top = 10.dp, start = 8.dp, end = 8.dp)) {
            content()
        }
    }
}
