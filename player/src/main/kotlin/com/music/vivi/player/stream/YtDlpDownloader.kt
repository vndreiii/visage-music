package com.music.vivi.player.stream

import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlin.coroutines.coroutineContext

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

                val page = "https://music.youtube.com/watch?v=${request.videoId}"
                val outTemplate = File(
                    request.outputDir,
                    "%(artist)s - %(title)s [%(id)s].%(ext)s",
                ).absolutePath

                val args = buildList {
                    add(bin)
                    add("-f")
                    add(formatSelector(request.quality))
                    add("--no-playlist")
                    add("--no-progress")
                    add("--print")
                    add("after_move:filepath")
                    add("--write-thumbnail")
                    add("--convert-thumbnails")
                    add("jpg")
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

                val logFile = File.createTempFile("vivi-download-", ".log")
                val proc = ProcessBuilder(args)
                    .directory(request.outputDir)
                    .redirectErrorStream(true)
                    .redirectOutput(logFile)
                    .start()
                try {
                    val deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(10)
                    while (!proc.waitFor(250, TimeUnit.MILLISECONDS)) {
                        coroutineContext.ensureActive()
                        check(System.nanoTime() < deadline) { "yt-dlp timed out" }
                    }
                    val log = logFile.readText()
                    check(proc.exitValue() == 0) {
                        log.lineSequence().lastOrNull { it.isNotBlank() } ?: "yt-dlp failed (${proc.exitValue()})"
                    }
                    val output = log.lineSequence().map { File(it.trim()) }
                        .lastOrNull { it.isFile && it.extension.lowercase() in audioExts }
                        ?: request.outputDir.listFiles()?.firstOrNull {
                            it.isFile && it.nameWithoutExtension.endsWith("[${request.videoId}]") && it.extension.lowercase() in audioExts
                        }
                        ?: error("Download completed but the audio file could not be found")
                    YtDlpDownloadResult(outputFile = output, log = log)
                } finally {
                    if (proc.isAlive) proc.destroyForcibly()
                    logFile.delete()
                }
            }.onFailure { if (it is CancellationException) throw it }
        }

    private fun formatSelector(quality: YtDlpAudioQuality): String = when (quality) {
        YtDlpAudioQuality.BEST -> "bestaudio/best"
        YtDlpAudioQuality.HIGH -> "bestaudio[abr<=192]/bestaudio/best"
        YtDlpAudioQuality.MEDIUM -> "bestaudio[abr<=128]/bestaudio/best"
    }
}
