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

    suspend fun resolve(videoId: String, requested: VideoQuality): ResolvedVideo? = withContext(Dispatchers.IO) {
        val candidates = NewPipeExtractor.videoStreams(videoId)
        val cap = if (requested == VideoQuality.AUTO) autoCap(candidates) else requested
        val picked = pickVideoCandidate(candidates, cap)
            ?.let { ResolvedVideo(videoId, it.url, it.height, headers) }
        // NewPipe often only returns the 360p muxed stream, so ask yt-dlp whenever it falls short of the cap.
        val best = if (picked != null && picked.height >= cap.height) picked else {
            val viaYtDlp = viaYtDlp(videoId, cap)
            if (viaYtDlp != null && viaYtDlp.height > (picked?.height ?: 0)) viaYtDlp else picked
        }
        // Serve it to mpv through the chunking proxy (see VideoChunkProxy) instead of a throttled open read.
        best?.copy(url = VideoChunkProxy.wrap(best.url, best.httpHeaders))
    }

    /** Auto: probe the CDN with the smallest stream NewPipe found; without a measurement assume a middling link. */
    private fun autoCap(candidates: List<VideoStreamCandidate>): VideoQuality {
        val probe = candidates.minByOrNull { it.height } ?: return VideoQuality.P720
        val mbps = BandwidthProbe.measureMbps(probe.url, headers) ?: return VideoQuality.P720
        return VideoQuality.forMbps(mbps)
    }

    private fun viaYtDlp(videoId: String, cap: VideoQuality): ResolvedVideo? {
        val bin = YtDlpBinary.resolve() ?: return null
        val h = cap.height
        return runCatching {
            val proc = ProcessBuilder(
                bin,
                "-f", "bv*[height<=$h][vcodec^=avc1][protocol=https]/bv*[height<=$h][protocol=https]/bv*[height<=$h]/b[height<=$h]/bv*/b",
                "--print", "%(height)s|%(urls)s", "--no-playlist", "--", "https://music.youtube.com/watch?v=$videoId",
            ).redirectErrorStream(true).start()
            if (!proc.waitFor(45, TimeUnit.SECONDS)) {
                proc.destroyForcibly()
                return null
            }
            val out = proc.inputStream.bufferedReader().readText()
            if (proc.exitValue() != 0) return null
            val line = out.lineSequence().map { it.trim() }.firstOrNull { it.contains("|http") } ?: return null
            val height = line.substringBefore('|').toIntOrNull() ?: h
            ResolvedVideo(videoId, line.substringAfter('|'), height, headers)
        }.getOrNull()
    }
}
