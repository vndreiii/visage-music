package com.music.vivi.player.stream

import com.music.innertube.NewPipeExtractor
import com.music.innertube.YouTube
import com.music.innertube.models.YouTubeClient
import com.music.innertube.models.response.PlayerResponse
import com.music.vivi.player.NowPlaying
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Resolve an audio stream URL for [videoId].
 * Prefer JioSaavn when enabled (default / [VIVI_SAAVN]), else Innertube + NewPipe,
 * then [YtDlpStreamResolver] (system yt-dlp) when CDN links 403 without PoToken.
 */
object StreamResolver {
    private val streamClients = listOf(
        // IOS and Android VR clients bypass 403 Forbidden errors for playback URLs
        YouTubeClient.IOS,
        YouTubeClient.ANDROID_VR_1_61_48,
        YouTubeClient.ANDROID_VR_1_43_32,
        YouTubeClient.WEB_REMIX,
        YouTubeClient.ANDROID_VR_NO_AUTH,
        YouTubeClient.MWEB,
        YouTubeClient.WEB,
        YouTubeClient.MOBILE,
    )

    /**
     * @param skipSources sources that already failed for this track (e.g. a URL mpv got a
     *   403 on); resolution moves on to the next source instead of returning it again.
     */
    suspend fun resolve(
        videoId: String,
        preferSaavn: Boolean = SaavnStreamResolver.defaultEnabled(),
        settings: StreamSettings = StreamSettings(saavnEnabled = preferSaavn),
        skipSources: Set<String> = emptySet(),
    ): ResolvedStream = coroutineScope {
        // Race everything that can run independently: a Saavn miss or the metadata
        // lookup no longer adds its full round-trip before YouTube is even tried.
        val saavn = if (settings.saavnEnabled && "saavn" !in skipSources) {
            async(Dispatchers.IO) {
                runCatching {
                    SaavnStreamResolver.resolveForVideo(
                        videoId,
                        enabled = true,
                        quality = if (settings.quality == StreamQuality.LOW) "96kbps" else "320kbps",
                    )
                }.getOrNull()
            }
        } else {
            null
        }
        val metadata = async(Dispatchers.IO) {
            YouTube.player(videoId, client = YouTubeClient.WEB_REMIX).getOrNull()?.videoDetails
        }
        val newPipe: kotlinx.coroutines.Deferred<List<Pair<Int, String>>>? = if ("newpipe" !in skipSources) {
            async(Dispatchers.IO) { runCatching { NewPipeExtractor.newPipePlayer(videoId) }.getOrDefault(emptyList<Pair<Int, String>>()) }
        } else {
            null
        }

        fun cancelRest() {
            metadata.cancel()
            newPipe?.cancel()
        }

        saavn?.let { withTimeoutOrNull(SAAVN_WAIT_MS) { it.await() } }?.let {
            cancelRest()
            return@coroutineScope it
        }
        saavn?.cancel()

        val streams = newPipe?.await().orEmpty()
        val best = if (settings.quality == StreamQuality.LOW) streams.minByOrNull { it.first } else streams.maxByOrNull { it.first }
        if (best != null) {
            val details = withTimeoutOrNull(METADATA_WAIT_MS) { metadata.await() }
            metadata.cancel()
            return@coroutineScope ResolvedStream(
                nowPlaying = toNowPlaying(videoId, details),
                streamUrl = best.second,
                source = "newpipe",
                httpHeaders = youtubeHeaders(YouTubeClient.WEB),
            )
        }

        if ("ytdlp" !in skipSources) {
            YtDlpStreamResolver.resolve(videoId, settings.quality)?.let {
                metadata.cancel()
                return@coroutineScope it
            }
        }

        // Last resort: InnerTube clients directly (often 403 without a PoToken).
        var lastError: Throwable? = null
        var fallbackDetails: PlayerResponse.VideoDetails? = withTimeoutOrNull(METADATA_WAIT_MS) { metadata.await() }
        for (client in streamClients) {
            val source = "youtube/${client.clientName}"
            if (source in skipSources) continue
            val response = YouTube.player(videoId, client = client).getOrElse {
                lastError = it
                continue
            }
            if (response.playabilityStatus.status != "OK") {
                lastError = IllegalStateException(
                    "playability ${response.playabilityStatus.status}: ${response.playabilityStatus.reason}",
                )
                continue
            }
            fallbackDetails = response.videoDetails ?: fallbackDetails
            val url = pickAudioUrl(response, videoId, settings.quality) ?: continue
            metadata.cancel()
            return@coroutineScope ResolvedStream(
                nowPlaying = toNowPlaying(videoId, fallbackDetails),
                streamUrl = url,
                source = source,
                httpHeaders = youtubeHeaders(client),
            )
        }

        throw lastError ?: IllegalStateException("No playable audio stream for $videoId")
    }

    private const val SAAVN_WAIT_MS = 4_000L
    private const val METADATA_WAIT_MS = 2_500L

    private fun youtubeHeaders(client: YouTubeClient): Map<String, String> = buildMap {
        put("User-Agent", client.userAgent)
        put("Referer", "https://music.youtube.com")
        put("Origin", "https://music.youtube.com")
        // Only attach cookies for web clients — desktop session cookies + IOS/ANDROID UA → CDN 403.
        val webLike = client.clientName.contains("WEB", ignoreCase = true) ||
            client.clientName == "MWEB"
        if (webLike) {
            YouTube.cookie?.takeIf { it.isNotBlank() }?.let { put("Cookie", it) }
        }
    }

    private fun pickAudioUrl(response: PlayerResponse, videoId: String, quality: StreamQuality): String? {
        val formats = buildList {
            response.streamingData?.adaptiveFormats?.let { addAll(it) }
            response.streamingData?.formats?.let { addAll(it) }
        }.filter { it.isAudio || it.mimeType.startsWith("audio/") }
            .sortedByDescending { it.bitrate }

        for (format in if (quality == StreamQuality.LOW) formats.asReversed() else formats) {
            val direct = format.url
            if (!direct.isNullOrBlank()) return direct
            val deciphered = NewPipeExtractor.getStreamUrl(format, videoId)
            if (!deciphered.isNullOrBlank()) return deciphered
        }
        return null
    }

    private fun toNowPlaying(videoId: String, details: PlayerResponse.VideoDetails?): NowPlaying {
        val thumb = details?.thumbnail?.thumbnails?.lastOrNull()?.url
        val durationSec = details?.lengthSeconds?.toLongOrNull() ?: 0L
        return NowPlaying(
            videoId = videoId,
            title = details?.title?.takeIf { it.isNotBlank() } ?: videoId,
            artist = details?.author?.removeSuffix(" - Topic")?.takeIf { it.isNotBlank() } ?: "Unknown",
            artworkUrl = thumb,
            durationMs = durationSec * 1000L,
        )
    }
}
