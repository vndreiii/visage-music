package com.music.vivi.player.stream

import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class YtDlpAudioFormat { OPUS, M4A, MP3, BEST }

enum class YtDlpAudioQuality { BEST, HIGH, MEDIUM }

data class YtDlpDownloadRequest(
    val videoId: String,
    val outputDir: File,
    val format: YtDlpAudioFormat = YtDlpAudioFormat.OPUS,
    val quality: YtDlpAudioQuality = YtDlpAudioQuality.BEST,
    val embedMetadata: Boolean = true,
    /** Optional display name hints for the output template. */
    val titleHint: String? = null,
    val artistHint: String? = null,
)

data class YtDlpDownloadResult(
    val outputFile: File?,
    val log: String,
)

/**
 * Save a YouTube Music track to disk via yt-dlp (`-x` extract when format ≠ BEST).
 */
object YtDlpDownloader {
    private val audioExts = setOf("opus", "m4a", "mp3", "ogg", "webm", "flac", "wav", "aac")

    suspend fun download(request: YtDlpDownloadRequest): Result<YtDlpDownloadResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bin = YtDlpBinary.resolve()
                    ?: error("yt-dlp not found (install yt-dlp or set VIVI_YTDLP)")
                request.outputDir.mkdirs()
                if (!request.outputDir.isDirectory) {
                    error("Download folder is not writable: ${request.outputDir}")
                }

                val before = request.outputDir.listFiles()?.map { it.absolutePath }?.toSet().orEmpty()
                val page = "https://music.youtube.com/watch?v=${request.videoId}"
                val outTemplate = File(
                    request.outputDir,
                    "%(artist)s - %(title)s.%(ext)s",
                ).absolutePath

                val args = buildList {
                    add(bin)
                    add("-f")
                    add(formatSelector(request.quality))
                    add("--no-playlist")
                    add("--no-overwrites")
                    add("-o")
                    add(outTemplate)
                    when (request.format) {
                        YtDlpAudioFormat.BEST -> Unit
                        else -> {
                            add("-x")
                            add("--audio-format")
                            add(request.format.name.lowercase())
                        }
                    }
                    if (request.embedMetadata) {
                        add("--embed-thumbnail")
                        add("--add-metadata")
                        add("--convert-thumbnails")
                        add("jpg")
                    }
                    add("--")
                    add(page)
                }

                val proc = ProcessBuilder(args)
                    .directory(request.outputDir)
                    .redirectErrorStream(true)
                    .start()
                val finished = proc.waitFor(10, TimeUnit.MINUTES)
                val log = proc.inputStream.bufferedReader().readText()
                if (!finished) {
                    proc.destroyForcibly()
                    error("yt-dlp timed out")
                }
                if (proc.exitValue() != 0) {
                    error(log.lineSequence().lastOrNull { it.isNotBlank() } ?: "yt-dlp failed (${proc.exitValue()})")
                }

                val after = request.outputDir.listFiles()?.toList().orEmpty()
                val created = after
                    .filter { it.absolutePath !in before }
                    .filter { it.extension.lowercase() in audioExts }
                    .maxByOrNull { it.lastModified() }

                YtDlpDownloadResult(outputFile = created, log = log)
            }
        }

    private fun formatSelector(quality: YtDlpAudioQuality): String = when (quality) {
        YtDlpAudioQuality.BEST -> "bestaudio/best"
        YtDlpAudioQuality.HIGH -> "bestaudio[abr<=192]/bestaudio/best"
        YtDlpAudioQuality.MEDIUM -> "bestaudio[abr<=128]/bestaudio/best"
    }
}
