package com.music.vivi.core.youtube

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
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

    /** Signs out: deletes the stored session and clears it from the live client. */
    fun signOut() = sessionManager.signOut()

    fun requireSession() {
        if (!ensureSession()) {
            error("Not signed in — open Settings → Account to sign in")
        }
    }

    private suspend fun <T> withSession(block: suspend () -> Result<T>): Result<T> = try {
        if (!ensureSession()) Result.failure(IllegalStateException("Sign in to access your YouTube Music library"))
        else block().also {
            coroutineContext.ensureActive()
            it.exceptionOrNull()?.let { error -> if (error is CancellationException) throw error }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun accountInfo(): Result<AccountInfo> = withSession { YouTube.accountInfo() }
    suspend fun home(continuation: String? = null, params: String? = null): Result<HomePage> =
        withSession { YouTube.home(continuation, params) }
    suspend fun searchSuggestions(query: String): Result<SearchSuggestions> = withSession { YouTube.searchSuggestions(query) }
    suspend fun searchSummary(query: String): Result<SearchSummaryPage> = withSession { YouTube.searchSummary(query) }
    /** The song or music video behind [videoId] (for pasted links), as YouTube Music's watch page describes it. */
    suspend fun songById(videoId: String): Result<com.music.innertube.models.SongItem> = withSession {
        YouTube.next(com.music.innertube.models.WatchEndpoint(videoId = videoId)).mapCatching { next ->
            next.items.firstOrNull { it.id == videoId } ?: next.items.first()
        }
    }
    suspend fun searchSongs(query: String): Result<SearchResult> = withSession { YouTube.search(query, YouTube.SearchFilter.FILTER_SONG) }
    suspend fun searchContinuation(continuation: String): Result<SearchResult> = withSession { YouTube.searchContinuation(continuation) }
    suspend fun library(browseId: String, tabIndex: Int = 0): Result<LibraryPage> = withSession { YouTube.library(browseId, tabIndex) }
    suspend fun libraryLikedSongs(): Result<LibraryPage> = library("FEmusic_liked_videos")
    suspend fun libraryLikedPlaylists(): Result<LibraryPage> = library("FEmusic_liked_playlists")
    suspend fun libraryLikedAlbums(): Result<LibraryPage> = library("FEmusic_liked_albums")
    suspend fun libraryArtists(): Result<LibraryPage> = library("FEmusic_library_corpus_artists")
    suspend fun libraryRecentActivity(): Result<LibraryPage> = withSession { YouTube.libraryRecentActivity() }
    suspend fun artist(browseId: String) = withSession { YouTube.artist(browseId) }
    suspend fun album(browseId: String) = withSession { YouTube.album(browseId) }
    suspend fun playlist(playlistId: String): Result<PlaylistPage> = withSession { YouTube.playlist(playlistId) }
    suspend fun playlistContinuation(continuation: String): Result<PlaylistContinuationPage> = withSession { YouTube.playlistContinuation(continuation) }
    suspend fun addToPlaylist(playlistId: String, videoId: String): Result<Unit> = withSession { YouTube.addToPlaylist(playlistId, videoId) }
    suspend fun libraryPlaylists(): Result<List<com.music.innertube.models.PlaylistItem>> = withSession {
        YouTube.library("FEmusic_liked_playlists").completed().map { page ->
            page.items.filterIsInstance<com.music.innertube.models.PlaylistItem>().filter { it.isEditable || it.id == "LM" }
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
    ): Result<com.music.innertube.models.YTItem> {
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
                s.artists.any { artistMatches(it.name, artist) }
            } ?: songs.firstOrNull { s ->
                s.title.equals(title, ignoreCase = true)
            } ?: songs.firstOrNull()
            ?: error("No YouTube match for \"$title\" — $artist")
        }
    }
}
