package com.music.vivi.compose.download

import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.DownloadAudioFormat
import com.music.vivi.compose.prefs.DownloadAudioQuality
import com.music.vivi.compose.prefs.PrefKeys
import com.music.vivi.player.stream.YtDlpAudioFormat
import com.music.vivi.player.stream.YtDlpAudioQuality
import com.music.vivi.player.stream.YtDlpBinary
import com.music.vivi.player.stream.YtDlpDownloadRequest
import com.music.vivi.player.stream.YtDlpDownloader
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class DownloadStatus {
    data object Idle : DownloadStatus()
    data class Running(val videoId: String, val title: String) : DownloadStatus()
    data class Succeeded(val file: File, val title: String) : DownloadStatus()
    data class Failed(val message: String) : DownloadStatus()
}

/**
 * Background yt-dlp downloads driven by [DesktopPrefs] (folder / format / quality / embed).
 */
class TrackDownloadService(
    private val prefs: DesktopPrefs,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _status = MutableStateFlow<DownloadStatus>(DownloadStatus.Idle)
    val status: StateFlow<DownloadStatus> = _status.asStateFlow()

    fun resolveFolder(): File {
        val custom = prefs.getString(PrefKeys.DownloadPath, "").trim()
        if (custom.isNotEmpty()) return File(custom).absoluteFile
        return XdgUserDirs.downloads()
    }

    fun listDownloadedAudio(): List<File> {
        val dir = resolveFolder()
        if (!dir.isDirectory) return emptyList()
        val exts = setOf("opus", "m4a", "mp3", "ogg", "webm", "flac", "wav", "aac")
        return dir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in exts }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
    }

    fun enqueue(
        videoId: String,
        title: String,
        artist: String = "",
    ) {
        if (videoId.isBlank()) {
            _status.value = DownloadStatus.Failed("Nothing to download")
            return
        }
        if (!YtDlpBinary.available()) {
            _status.value = DownloadStatus.Failed("yt-dlp not found — install it or set VIVI_YTDLP")
            return
        }
        scope.launch {
            mutex.withLock {
                _status.value = DownloadStatus.Running(videoId, title)
                val format = runCatching {
                    DownloadAudioFormat.valueOf(
                        prefs.getString(PrefKeys.DownloadFormat, DownloadAudioFormat.OPUS.name),
                    )
                }.getOrDefault(DownloadAudioFormat.OPUS)
                val quality = runCatching {
                    DownloadAudioQuality.valueOf(
                        prefs.getString(PrefKeys.DownloadQuality, DownloadAudioQuality.BEST.name),
                    )
                }.getOrDefault(DownloadAudioQuality.BEST)
                val embed = prefs.getBoolean(PrefKeys.DownloadEmbedMetadata, true)
                val result = YtDlpDownloader.download(
                    YtDlpDownloadRequest(
                        videoId = videoId,
                        outputDir = resolveFolder(),
                        format = format.toYtDlp(),
                        quality = quality.toYtDlp(),
                        embedMetadata = embed,
                        titleHint = title,
                        artistHint = artist,
                    ),
                )
                _status.value = result.fold(
                    onSuccess = { r ->
                        val file = r.outputFile
                        if (file != null) DownloadStatus.Succeeded(file, title)
                        else DownloadStatus.Succeeded(resolveFolder(), title)
                    },
                    onFailure = { DownloadStatus.Failed(it.message ?: "Download failed") },
                )
            }
        }
    }

    private fun DownloadAudioFormat.toYtDlp(): YtDlpAudioFormat = when (this) {
        DownloadAudioFormat.OPUS -> YtDlpAudioFormat.OPUS
        DownloadAudioFormat.M4A -> YtDlpAudioFormat.M4A
        DownloadAudioFormat.MP3 -> YtDlpAudioFormat.MP3
        DownloadAudioFormat.BEST -> YtDlpAudioFormat.BEST
    }

    private fun DownloadAudioQuality.toYtDlp(): YtDlpAudioQuality = when (this) {
        DownloadAudioQuality.BEST -> YtDlpAudioQuality.BEST
        DownloadAudioQuality.HIGH -> YtDlpAudioQuality.HIGH
        DownloadAudioQuality.MEDIUM -> YtDlpAudioQuality.MEDIUM
    }
}
