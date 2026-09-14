package com.music.vivi.core.youtube

import com.music.innertube.YouTube
import com.music.innertube.models.AccountInfo
import com.music.innertube.models.SearchSuggestions
import com.music.innertube.pages.HomePage
import com.music.innertube.pages.LibraryPage
import com.music.innertube.pages.PlaylistContinuationPage
import com.music.innertube.pages.PlaylistPage
import com.music.innertube.pages.SearchResult
import com.music.innertube.pages.SearchSummaryPage
import com.music.innertube.utils.completed
import com.music.vivi.core.session.SessionManager

/**
 * Session-aware facade over [YouTube] for Compose / ViewModels.
 * Always call through here from UI so cookie/visitorData are applied.
 */
class YouTubeMusicService(
    private val sessionManager: SessionManager,
) {
    fun ensureSession(): Boolean = sessionManager.applyStoredSession()

    fun requireSession() {
        if (!ensureSession()) {
            error("Not signed in — run ./gradlew :desktop:run to log in")
        }
    }

    suspend fun accountInfo(): Result<AccountInfo> {
        requireSession()
        return YouTube.accountInfo()
    }

    suspend fun home(continuation: String? = null, params: String? = null): Result<HomePage> {
        requireSession()
        return YouTube.home(continuation, params)
    }

    suspend fun searchSuggestions(query: String): Result<SearchSuggestions> {
        requireSession()
        return YouTube.searchSuggestions(query)
    }

    suspend fun searchSummary(query: String): Result<SearchSummaryPage> {
        requireSession()
        return YouTube.searchSummary(query)
    }

    suspend fun searchSongs(query: String): Result<SearchResult> {
        requireSession()
        return YouTube.search(query, YouTube.SearchFilter.FILTER_SONG)
    }

    suspend fun searchContinuation(continuation: String): Result<SearchResult> {
        requireSession()
        return YouTube.searchContinuation(continuation)
    }

    suspend fun library(browseId: String, tabIndex: Int = 0): Result<LibraryPage> {
        requireSession()
        return YouTube.library(browseId, tabIndex)
    }

    /** Liked songs — YT Music browse id is liked_videos (not liked_songs). */
    suspend fun libraryLikedSongs(): Result<LibraryPage> =
        library("FEmusic_liked_videos")

    suspend fun libraryLikedPlaylists(): Result<LibraryPage> =
        library("FEmusic_liked_playlists")

    suspend fun libraryLikedAlbums(): Result<LibraryPage> =
        library("FEmusic_liked_albums")

    suspend fun libraryArtists(): Result<LibraryPage> =
        library("FEmusic_library_corpus_artists")

    suspend fun libraryRecentActivity(): Result<LibraryPage> {
        requireSession()
        return YouTube.libraryRecentActivity()
    }

    suspend fun artist(browseId: String) = run {
        requireSession()
        YouTube.artist(browseId)
    }

    suspend fun album(browseId: String) = run {
        requireSession()
        YouTube.album(browseId)
    }

    suspend fun playlist(playlistId: String): Result<PlaylistPage> {
        requireSession()
        return YouTube.playlist(playlistId)
    }

    suspend fun playlistContinuation(continuation: String): Result<PlaylistContinuationPage> {
        requireSession()
        return YouTube.playlistContinuation(continuation)
    }

    suspend fun addToPlaylist(playlistId: String, videoId: String): Result<Unit> = runCatching {
        requireSession()
        YouTube.addToPlaylist(playlistId, videoId).getOrThrow()
        Unit
    }

    /** User's liked/saved playlists (paginated to completion). */
    suspend fun libraryPlaylists(): Result<List<com.music.innertube.models.PlaylistItem>> {
        requireSession()
        return YouTube.library("FEmusic_liked_playlists")
            .completed()
            .map { page ->
                page.items
                    .filterIsInstance<com.music.innertube.models.PlaylistItem>()
                    .filter { it.isEditable || it.id == "LM" }
            }
    }

    /** Idle search Suggestions — Apple Music most-played charts for [countryCode]. */
    suspend fun suggestionCharts(countryCode: String? = null): Result<List<com.music.vivi.core.charts.ChartSuggestion>> =
        runCatching {
            val cc = countryCode?.lowercase()?.takeIf { it.isNotBlank() && it != "system" }
                ?: java.util.Locale.getDefault().country.lowercase().ifBlank { "us" }
            com.music.vivi.core.charts.AppleMusicCharts.topSongs(cc)
        }

    /** Resolve an Apple chart row to a YouTube song and return it (best effort). */
    suspend fun resolveChartSuggestion(
        title: String,
        artist: String,
    ): Result<com.music.innertube.models.SongItem> {
        requireSession()
        return YouTube.search("$title $artist", YouTube.SearchFilter.FILTER_SONG).mapCatching { page ->
            val songs = page.items.filterIsInstance<com.music.innertube.models.SongItem>()
            fun artistMatches(yt: String, apple: String): Boolean {
                val a = yt.trim().lowercase()
                val b = apple.trim().lowercase()
                return a.contains(b) || b.contains(a)
            }
            songs.firstOrNull { s ->
                s.title.equals(title, ignoreCase = true) &&
                    s.artists.any { artistMatches(it.name, artist) }
            } ?: songs.firstOrNull { s ->
                s.title.contains(title, ignoreCase = true) &&
                    s.artists.any { artistMatches(it.name, artist) }
            } ?: songs.firstOrNull { s ->
                s.title.equals(title, ignoreCase = true)
            } ?: songs.firstOrNull()
            ?: error("No YouTube match for \"$title\" — $artist")
        }
    }
}
