package com.music.vivi.player.stream

import com.music.innertube.NewPipeExtractor
import com.music.innertube.YouTube
import com.music.innertube.models.YouTubeClient
import com.music.innertube.models.response.PlayerResponse
import com.music.vivi.player.NowPlaying

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

    suspend fun resolve(
        videoId: String,
        preferSaavn: Boolean = SaavnStreamResolver.defaultEnabled(),
        settings: StreamSettings = StreamSettings(saavnEnabled = preferSaavn),
    ): ResolvedStream {
        if (settings.saavnEnabled) {
            SaavnStreamResolver.resolveForVideo(videoId, enabled = true, quality = if (settings.quality == StreamQuality.LOW) "96kbps" else "320kbps")?.let { return it }
        }

        // Prefer yt-dlp when available — Innertube googlevideo URLs currently 403 here.
        var metadata: PlayerResponse.VideoDetails? = null
        var lastError: Throwable? = null

        for (client in streamClients) {
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
            metadata = response.videoDetails ?: metadata
            val url = pickAudioUrl(response, videoId, settings.quality) ?: continue
            val details = response.videoDetails ?: metadata
            return ResolvedStream(
                nowPlaying = toNowPlaying(videoId, details),
                streamUrl = url,
                source = "youtube/${client.clientName}",
                httpHeaders = youtubeHeaders(client),
            )
        }

        val newPipe = NewPipeExtractor.newPipePlayer(videoId)
        val best = if (settings.quality == StreamQuality.LOW) newPipe.minByOrNull { it.first } else newPipe.maxByOrNull { it.first }
        if (best != null) {
            if (metadata == null) {
                YouTube.player(videoId, client = YouTubeClient.WEB_REMIX).getOrNull()
                    ?.videoDetails
                    ?.let { metadata = it }
            }
            return ResolvedStream(
                nowPlaying = toNowPlaying(videoId, metadata),
                streamUrl = best.second,
                source = "newpipe",
                httpHeaders = youtubeHeaders(YouTubeClient.WEB),
            )
        }

        YtDlpStreamResolver.resolve(videoId, settings.quality)?.let { return it }

        throw lastError ?: IllegalStateException("No playable audio stream for $videoId")
    }

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
