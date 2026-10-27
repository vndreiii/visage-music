package com.music.vivi.compose.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.music.innertube.models.AccountInfo
import com.music.innertube.YouTube
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.pages.HomePage
import com.music.vivi.core.youtube.YouTubeMusicService
import kotlinx.coroutines.*

/** A single owner for refresh/filter requests; cancelled responses cannot replace a newer page. */
class HomeViewModel(
    private val yt: YouTubeMusicService,
    private val scope: CoroutineScope,
    /** Whether a YouTube Music session is stored; a failed load without one means "log in". */
    private val isLoggedIn: () -> Boolean = { true },
    private val quickPicksCache: QuickPicksCache? = null,
    /** How long Quick picks stay before being replaced on their own; null turns the schedule off. */
    private val quickPicksIntervalMs: () -> Long? = { null },
) {
    var homePage by mutableStateOf<HomePage?>(null); private set
    var accountPlaylists by mutableStateOf<List<PlaylistItem>>(emptyList()); private set
    var accountInfo by mutableStateOf<AccountInfo?>(null); private set
    var selectedChip by mutableStateOf<HomePage.Chip?>(null); private set
    var isLoading by mutableStateOf(true); private set
    var isRefreshing by mutableStateOf(false); private set
    var isLoadingMore by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    /** The feed failed because there is no session, or the session expired. Shown as a log-in prompt, not an error. */
    var needsLogin by mutableStateOf(false); private set
    /** Songs fetched by the Quick picks refresh button; replaces the feed's own Quick picks while set. */
    var quickPicksOverride by mutableStateOf<List<SongItem>?>(null); private set
    var quickPicksLoading by mutableStateOf(false); private set
    private val seenQuickPicks = mutableSetOf<String>()
    private var quickJob: Job? = null
    private var request: Job? = null
    private var moreJob: Job? = null
    private var previousHomePage: HomePage? = null

    init {
        // Check now and then while the app stays open, not only when Home loads.
        scope.launch {
            while (true) {
                delay(10 * 60_000L)
                if (homePage != null && !isLoading) applyQuickPicksSchedule()
            }
        }
    }

    private fun feedQuickPicks(): List<SongItem> =
        homePage?.sections.orEmpty()
            .filter { it.title.equals("Quick picks", ignoreCase = true) }
            .flatMap { it.items }.filterIsInstance<SongItem>()

    /**
     * Keeps the last refresh until its interval is up, then builds a new set (same radio-based refresh the button
     * runs). A first run just remembers the feed's own picks as the starting point.
     */
    private fun applyQuickPicksSchedule() {
        val cache = quickPicksCache ?: return
        val interval = quickPicksIntervalMs() ?: return
        val saved = cache.load()
        val now = System.currentTimeMillis()
        if (saved == null) {
            val feed = feedQuickPicks()
            if (feed.isNotEmpty()) cache.save(feed, now)
            return
        }
        if (now - saved.savedAtMs < interval) {
            if (quickPicksOverride == null) quickPicksOverride = saved.songs
            return
        }
        if (quickPicksOverride == null) quickPicksOverride = saved.songs
        refreshQuickPicks(saved.songs, automatic = true)
    }

    fun refresh() = loadInitial()

    fun loadInitial() {
        request?.cancel()
        moreJob?.cancel()
        quickJob?.cancel()
        quickPicksLoading = false
        selectedChip = null
        previousHomePage = null
        isRefreshing = homePage != null
        request = scope.launch {
            isLoading = true
            error = null
            needsLogin = false
            try {
                val page = withContext(Dispatchers.IO) { yt.home().getOrThrow() }
                ensureActive()
                homePage = page
                applyQuickPicksSchedule()
                // Trickle-build the cross-country chart in the background once a day; Home shows it when ready.
                com.music.vivi.compose.ui.charts.WorldChart.ensureFresh(24 * 3_600_000L)
                // Render the feed as soon as it arrives; account metadata is secondary.
                isLoading = false
                coroutineScope {
                    launch {
                        runCatching { withContext(Dispatchers.IO) { yt.accountInfo().getOrThrow() } }
                            .onSuccess { ensureActive(); accountInfo = it }
                    }
                    launch {
                        runCatching { withContext(Dispatchers.IO) { yt.libraryLikedPlaylists().getOrThrow() } }
                            .onSuccess { ensureActive(); accountPlaylists = it.items.filterIsInstance<PlaylistItem>().distinctBy { p -> p.id } }
                    }
                }
                loadMore()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                needsLogin = !isLoggedIn() || looksLikeAuthFailure(e.message)
                error = e.message ?: "Couldn't load Home"
            } finally {
                isLoading = false
                isRefreshing = false
            }
        }
    }

    /**
     * New Quick picks: the feed returns the same shelf for a while, so instead of re-requesting it this builds a
     * fresh one from YouTube Music radios seeded by a few of the current picks. Songs already shown since the last
     * feed load are excluded, so every press brings different ones.
     */
    fun refreshQuickPicks(current: List<SongItem>, automatic: Boolean = false) {
        if (quickPicksLoading || current.isEmpty()) return
        seenQuickPicks.addAll(current.map { it.id })
        quickJob = scope.launch {
            quickPicksLoading = true
            try {
                var seeds = current.shuffled().take(4)
                val fresh = LinkedHashMap<String, SongItem>()
                repeat(2) {
                    if (fresh.size >= 12) return@repeat
                    val batches = withContext(Dispatchers.IO) {
                        seeds.map { seed ->
                            async {
                                YouTube.next(WatchEndpoint(videoId = seed.id, playlistId = "RDAMVM${seed.id}"))
                                    .getOrNull()?.items.orEmpty()
                            }
                        }.awaitAll()
                    }
                    batches.flatten().forEach { if (it.id !in seenQuickPicks) fresh.putIfAbsent(it.id, it) }
                    // Not enough that is new: seed the next round from what was just found.
                    seeds = fresh.values.shuffled().take(4).ifEmpty { seeds }
                }
                ensureActive()
                if (fresh.isNotEmpty()) {
                    val picks = fresh.values.shuffled().take(40)
                    seenQuickPicks.addAll(picks.map { it.id })
                    quickPicksOverride = picks
                    quickPicksCache?.save(picks)
                } else if (!automatic) {
                    error = "Couldn't find new picks right now. Try again in a moment."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!automatic) error = "Couldn't refresh quick picks."
            } finally {
                quickPicksLoading = false
            }
        }
    }

    fun toggleChip(chip: HomePage.Chip?) {
        request?.cancel()
        moreJob?.cancel()
        error = null
        if (chip == null || selectedChip == chip) {
            selectedChip = null
            previousHomePage?.let { homePage = it }
            previousHomePage = null
            isLoading = false
            return
        }
        if (previousHomePage == null) previousHomePage = homePage
        selectedChip = chip
        request = scope.launch {
            isLoading = true
            try {
                val page = withContext(Dispatchers.IO) { yt.home(params = chip.endpoint?.params).getOrThrow() }
                ensureActive()
                homePage = page.copy(chips = previousHomePage?.chips)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Couldn't load this filter"
                selectedChip = null
                previousHomePage?.let { homePage = it }
            } finally {
                isLoading = false
            }
        }
    }

    fun loadMore() {
        val continuation = homePage?.continuation ?: return
        if (isLoadingMore || isLoading || selectedChip != null) return
        moreJob = scope.launch {
            isLoadingMore = true
            try {
                val next = withContext(Dispatchers.IO) { yt.home(continuation = continuation).getOrThrow() }
                ensureActive()
                val current = homePage ?: return@launch
                homePage = current.copy(
                    sections = current.sections + next.sections,
                    continuation = next.continuation?.takeUnless { it == continuation },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "Couldn't load more music. Refresh to try again."
            } finally {
                isLoadingMore = false
            }
        }
    }
}

/** Messages that mean the request was refused for lack of a valid signed-in session. */
internal fun looksLikeAuthFailure(message: String?): Boolean {
    val m = message?.lowercase() ?: return false
    return listOf("401", "403", "unauthorized", "unauthenticated", "not logged in", "not signed in",
        "login required", "sign in", "signed out", "expired").any { it in m }
}
