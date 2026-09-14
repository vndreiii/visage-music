package com.music.vivi.player.stream

import com.music.innertube.YouTube
import com.music.innertube.models.YouTubeClient
import com.music.vivi.player.NowPlaying
import java.util.concurrent.TimeUnit

/**
 * Resolve a playable audio URL via system [yt-dlp] (or youtube-dl).
 * Innertube googlevideo links often 403 without PoToken; yt-dlp still works.
 */
object YtDlpStreamResolver {
    fun available(): Boolean = YtDlpBinary.available()

    suspend fun resolve(videoId: String): ResolvedStream? {
        val bin = YtDlpBinary.resolve() ?: return null
        val page = "https://music.youtube.com/watch?v=$videoId"
        return runCatching {
            val proc = ProcessBuilder(
                bin,
                "-f", "bestaudio/best",
                "-g",
                "--no-playlist",
                "--",
                page,
            )
                .redirectErrorStream(true)
                .start()
            val finished = proc.waitFor(45, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                return null
            }
            val out = proc.inputStream.bufferedReader().readText().trim()
            if (proc.exitValue() != 0) return null
            val url = out.lineSequence().map { it.trim() }.lastOrNull { it.startsWith("http") }
                ?: return null

            val meta = YouTube.player(videoId, client = YouTubeClient.WEB_REMIX).getOrNull()?.videoDetails
            val title = meta?.title?.takeIf { it.isNotBlank() } ?: videoId
            val artist = meta?.author?.removeSuffix(" - Topic")?.takeIf { it.isNotBlank() } ?: "Unknown"
            val thumb = meta?.thumbnail?.thumbnails?.lastOrNull()?.url
            val durationMs = (meta?.lengthSeconds?.toLongOrNull() ?: 0L) * 1000L

            ResolvedStream(
                nowPlaying = NowPlaying(
                    videoId = videoId,
                    title = title,
                    artist = artist,
                    artworkUrl = thumb,
                    durationMs = durationMs,
                ),
                streamUrl = url,
                source = "yt-dlp",
                httpHeaders = mapOf(
                    "User-Agent" to YouTubeClient.USER_AGENT_WEB,
                    "Referer" to "https://music.youtube.com",
                ),
            )
        }.getOrNull()
    }
}
