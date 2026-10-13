package com.music.vivi.player.stream

import com.music.innertube.models.WatchEndpoint
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import com.music.innertube.YouTube
import com.music.jiosaavn.SaavnService
import com.music.jiosaavn.SaavnSong
import com.music.vivi.player.NowPlaying
import kotlin.math.abs

/**
 * Match a YouTube track to a JioSaavn stream (upstream YTPlayerUtils Saavn path).
 * Returns null on any failure so callers fall through to Innertube.
 */
object SaavnStreamResolver {
    const val QUALITY_320 = "320kbps"

    /**
     * Prefer Saavn when [enabled]. Uses YT next()/player metadata for title+artist matching.
     */
    suspend fun resolveForVideo(
        videoId: String,
        enabled: Boolean = defaultEnabled(),
        quality: String = QUALITY_320,
    ): ResolvedStream? {
        if (!enabled) return null
        return runCatching {
            val (title, artists, album, durationSec, artwork) = loadHints(videoId)
            if (title.isBlank()) return null

            val artistJoined = artists.joinToString(", ")
            val primaryQuery = listOf(album, title, artistJoined)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .replace(Regex("\\s+"), " ")
            val fallbackQuery = "$title $artistJoined".replace(Regex("\\s+"), " ")

            val match = findMatch(primaryQuery, title, artists, album, durationSec)
                ?: if (fallbackQuery != primaryQuery) {
                    findMatch(fallbackQuery, title, artists, album, durationSec)
                } else {
                    null
                }
                ?: return null

            var streamUrl = SaavnService.selectBestUrl(match.downloadUrl, quality)
            if (streamUrl.isNullOrBlank()) {
                streamUrl = SaavnService.getBestStreamUrl(match.id, quality)
            }
            if (streamUrl.isNullOrBlank()) return null

            ResolvedStream(
                nowPlaying = NowPlaying(
                    videoId = videoId,
                    title = match.name.ifBlank { title },
                    artist = artistJoined.ifBlank {
                        match.artists.primary.firstOrNull()?.name
                            ?: match.artists.all.firstOrNull()?.name
                            ?: "Unknown"
                    },
                    album = match.album?.name ?: album.takeIf { it.isNotBlank() },
                    artworkUrl = match.image.lastOrNull()?.url ?: artwork,
                    durationMs = (match.duration ?: durationSec)?.times(1000L) ?: 0L,
                ),
                streamUrl = streamUrl,
                source = "jiosaavn",
            )
        }.getOrNull()
    }

    fun defaultEnabled(): Boolean {
        val env = System.getenv("VIVI_SAAVN")?.lowercase()
        return when (env) {
            "0", "false", "off", "no" -> false
            else -> true // default on: upstream often uses Saavn for audio
        }
    }

    private data class Hints(
        val title: String,
        val artists: List<String>,
        val album: String,
        val durationSec: Int?,
        val artwork: String?,
    )

    private suspend fun loadHints(videoId: String): Hints = coroutineScope {
        val nextDef = async { YouTube.next(WatchEndpoint(videoId = videoId)).getOrNull() }
        val playerDef = async { YouTube.player(videoId, client = com.music.innertube.models.YouTubeClient.WEB_REMIX).getOrNull() }
        
        val next = nextDef.await()
        val player = playerDef.await()
        
        val item = next?.items?.getOrNull(next.currentIndex ?: 0) ?: next?.items?.firstOrNull()
        val details = player?.videoDetails

        val title = item?.title?.takeIf { it.isNotBlank() }
            ?: details?.title.orEmpty()
        val artists = when {
            !item?.artists.isNullOrEmpty() -> item!!.artists.map { it.name }
            else -> listOfNotNull(
                details?.author?.removeSuffix(" - Topic")?.takeIf { it.isNotBlank() },
            )
        }
        val album = item?.album?.name.orEmpty()
        val durationSec = details?.lengthSeconds?.toIntOrNull()
            ?: item?.duration
        val artwork = details?.thumbnail?.thumbnails?.lastOrNull()?.url
            ?: item?.thumbnail
        Hints(title, artists, album, durationSec, artwork)
    }

    private suspend fun findMatch(
        query: String,
        wantedTitle: String,
        wantedArtists: List<String>,
        wantedAlbum: String,
        expectedDuration: Int?,
    ): SaavnSong? {
        if (query.isBlank()) return null
        val songs = SaavnService.searchSongs(query).getOrNull() ?: return null
        val wantedTitleLower = wantedTitle.lowercase().trim()
        val wantedArtistsLower = wantedArtists.map { it.lowercase().trim() }.toSet()
        val wantedAlbumLower = wantedAlbum.lowercase().trim()

        return songs.firstOrNull { candidate ->
            if (candidate.isProOnly) return@firstOrNull false
            val titleMatches = candidate.name.lowercase().trim() == wantedTitleLower
            val candidateArtists = candidate.artists.all.map { it.name.lowercase().trim() }.toSet()
            val artistMatches = candidateArtists.intersect(wantedArtistsLower).isNotEmpty() ||
                wantedArtistsLower.isEmpty()
            val albumMatches = wantedAlbumLower.isBlank() ||
                candidate.album?.name?.lowercase() == wantedAlbumLower
            val candDuration = candidate.duration
            val durationMatches = if (expectedDuration != null && candDuration != null) {
                abs(expectedDuration - candDuration) <= 12
            } else {
                true
            }
            titleMatches && artistMatches && albumMatches && durationMatches
        }
    }
}
