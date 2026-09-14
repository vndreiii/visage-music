package com.music.vivi.compose.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.music.innertube.models.AccountInfo
import com.music.innertube.models.PlaylistItem
import com.music.innertube.pages.HomePage
import com.music.vivi.core.youtube.YouTubeMusicService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Desktop home state — YouTube-driven sections mirror upstream [com.music.vivi.viewmodels.HomeViewModel]
 * network phase (chips, home sections, account playlists). Local Room sections omitted until DB port.
 */
class HomeViewModel(
    private val yt: YouTubeMusicService,
    private val scope: CoroutineScope,
) {
    var homePage by mutableStateOf<HomePage?>(null)
        private set
    var accountPlaylists by mutableStateOf<List<PlaylistItem>>(emptyList())
        private set
    var accountInfo by mutableStateOf<AccountInfo?>(null)
        private set
    var selectedChip by mutableStateOf<HomePage.Chip?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isRefreshing by mutableStateOf(false)
        private set
    var isLoadingMore by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var moreJob: Job? = null
    private var previousHomePage: HomePage? = null

    fun refresh() {
        scope.launch {
            isRefreshing = true
            error = null
            selectedChip = null
            loadInitial()
            isRefreshing = false
        }
    }

    fun loadInitial() {
        scope.launch {
            isLoading = true
            error = null
            withContext(Dispatchers.IO) {
                yt.home()
                    .onSuccess { page ->
                        homePage = page
                    }
                    .onFailure {
                        error = it.message ?: it.toString()
                    }
                yt.accountInfo().onSuccess { accountInfo = it }
                yt.libraryLikedPlaylists().onSuccess { page ->
                    accountPlaylists = page.items.filterIsInstance<PlaylistItem>()
                }
            }
            isLoading = false
        }
    }

    fun toggleChip(chip: HomePage.Chip?) {
        scope.launch {
            if (chip == null || selectedChip == chip) {
                // deselect → restore previous
                selectedChip = null
                previousHomePage?.let { homePage = it }
                previousHomePage = null
                return@launch
            }
            if (previousHomePage == null) {
                previousHomePage = homePage
            }
            selectedChip = chip
            isLoading = true
            withContext(Dispatchers.IO) {
                yt.home(params = chip.endpoint?.params)
                    .onSuccess { next ->
                        homePage = next.copy(chips = previousHomePage?.chips ?: homePage?.chips)
                    }
                    .onFailure {
                        error = it.message ?: it.toString()
                        selectedChip = null
                    }
            }
            isLoading = false
        }
    }

    fun loadMore() {
        val continuation = homePage?.continuation ?: return
        if (isLoadingMore || selectedChip != null) return
        moreJob?.cancel()
        moreJob = scope.launch {
            isLoadingMore = true
            withContext(Dispatchers.IO) {
                yt.home(continuation = continuation)
                    .onSuccess { next ->
                        val current = homePage
                        homePage = next.copy(
                            chips = current?.chips,
                            sections = current?.sections.orEmpty() + next.sections,
                        )
                    }
            }
            isLoadingMore = false
        }
    }
}
