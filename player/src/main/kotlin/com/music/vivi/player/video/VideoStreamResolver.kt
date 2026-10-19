package com.music.vivi.player.video

import com.music.innertube.NewPipeExtractor
import com.music.innertube.models.YouTubeClient
import com.music.innertube.VideoStreamCandidate
import com.music.vivi.player.stream.YtDlpBinary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

data class ResolvedVideo(
    val videoId: String,
    val url: String,
    val height: Int,
    val httpHeaders: Map<String, String>,
)

/**
 * Resolves a silent video stream for a YouTube video id. Audio always stays on the main
 * player; this URL is only ever rendered muted, so video-only streams are ideal.
 */
object VideoStreamResolver {
    private val headers = mapOf(
        "User-Agent" to YouTubeClient.USER_AGENT_WEB,
        "Referer" to "https://music.youtube.com",
    )

    suspend fun resolve(videoId: String, cap: VideoQuality): ResolvedVideo? = withContext(Dispatchers.IO) {
        val picked = pickVideoCandidate(NewPipeExtractor.videoStreams(videoId), cap)
        if (picked != null) return@withContext ResolvedVideo(videoId, picked.url, picked.height, headers)
        viaYtDlp(videoId, cap)
    }

    private fun viaYtDlp(videoId: String, cap: VideoQuality): ResolvedVideo? {
        val bin = YtDlpBinary.resolve() ?: return null
        val h = cap.height
        return runCatching {
            val proc = ProcessBuilder(
                bin, "-f", "bv*[height<=$h][vcodec^=avc1]/bv*[height<=$h]/b[height<=$h]/bv*/b",
                "-g", "--no-playlist", "--", "https://music.youtube.com/watch?v=$videoId",
            ).redirectErrorStream(true).start()
            if (!proc.waitFor(45, TimeUnit.SECONDS)) {
                proc.destroyForcibly()
                return null
            }
            val out = proc.inputStream.bufferedReader().readText()
            if (proc.exitValue() != 0) return null
            val url = out.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("http") } ?: return null
            ResolvedVideo(videoId, url, h, headers)
        }.getOrNull()
    }
}
