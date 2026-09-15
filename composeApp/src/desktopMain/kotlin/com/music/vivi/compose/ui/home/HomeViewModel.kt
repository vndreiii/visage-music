package com.music.vivi.compose.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.music.innertube.models.AccountInfo
import com.music.innertube.models.PlaylistItem
import com.music.innertube.pages.HomePage
import com.music.vivi.core.youtube.YouTubeMusicService
import kotlinx.coroutines.*

/** A single owner for refresh/filter requests; cancelled responses cannot replace a newer page. */
class HomeViewModel(private val yt: YouTubeMusicService, private val scope: CoroutineScope) {
    var homePage by mutableStateOf<HomePage?>(null); private set
    var accountPlaylists by mutableStateOf<List<PlaylistItem>>(emptyList()); private set
    var accountInfo by mutableStateOf<AccountInfo?>(null); private set
    var selectedChip by mutableStateOf<HomePage.Chip?>(null); private set
    var isLoading by mutableStateOf(true); private set
    var isRefreshing by mutableStateOf(false); private set
    var isLoadingMore by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    private var request: Job? = null
    private var moreJob: Job? = null
    private var previousHomePage: HomePage? = null

    fun refresh() = loadInitial()

    fun loadInitial() {
        request?.cancel()
        moreJob?.cancel()
        selectedChip = null
        previousHomePage = null
        isRefreshing = homePage != null
        request = scope.launch {
            isLoading = true
            error = null
            try {
                val page = withContext(Dispatchers.IO) { yt.home().getOrThrow() }
                ensureActive()
                homePage = page
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
                error = e.message ?: "Couldn't load Home"
            } finally {
                isLoading = false
                isRefreshing = false
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
