package com.music.vivi.compose.download

import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.DownloadAudioFormat
import com.music.vivi.compose.prefs.DownloadAudioQuality
import com.music.vivi.compose.prefs.PrefKeys
import com.music.vivi.database.DatabaseFactory
import com.music.vivi.database.DownloadedTrackRepository
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
 * Background yt-dlp downloads into the app-managed offline directory.
 * Registered rows live in [DownloadedTrackRepository] — not a scan of ~/Downloads.
 */
class TrackDownloadService(
    private val prefs: DesktopPrefs,
    private val downloaded: DownloadedTrackRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _status = MutableStateFlow<DownloadStatus>(DownloadStatus.Idle)
    val status: StateFlow<DownloadStatus> = _status.asStateFlow()

    /** Managed offline dir (or optional override via [PrefKeys.DownloadPath]). */
    fun resolveFolder(): File {
        val custom = prefs.getString(PrefKeys.DownloadPath, "").trim()
        if (custom.isNotEmpty()) {
            val dir = File(custom).absoluteFile
            if (!dir.exists()) dir.mkdirs()
            return dir
        }
        return DatabaseFactory.offlineDir()
    }

    /** Offline library = DB rows whose files still exist. */
    fun listOfflineTracks() = downloaded.all().filter { File(it.file_path).isFile }

    @Deprecated("Use listOfflineTracks() — Offline is DB-backed, not a folder listing")
    fun listDownloadedAudio(): List<File> =
        listOfflineTracks().map { File(it.file_path) }

    fun enqueue(
        videoId: String,
        title: String,
        artist: String = "",
    ) {
        if (videoId.startsWith("local:") || videoId.startsWith("offline:")) {
            _status.value = DownloadStatus.Failed("This track is already on your device")
            return
        }
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
                try {
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
                val outDir = resolveFolder()
                val result = YtDlpDownloader.download(
                    YtDlpDownloadRequest(
                        videoId = videoId,
                        outputDir = outDir,
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
                        if (file != null && file.isFile) {
                            val cover = findSiblingCover(file)
                            downloaded.upsert(
                                videoId = videoId,
                                title = title.ifBlank { file.nameWithoutExtension },
                                artist = artist,
                                filePath = file.absolutePath,
                                coverPath = cover?.absolutePath,
                            )
                            DownloadStatus.Succeeded(file, title)
                        } else {
                            DownloadStatus.Failed("The downloaded audio file could not be found")
                        }
                    },
                    onFailure = { DownloadStatus.Failed(it.message ?: "Download failed") },
                )
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _status.value = DownloadStatus.Failed(e.message ?: "Couldn't save the download")
                }
            }
        }
    }

    private fun findSiblingCover(audio: File): File? {
        val base = audio.nameWithoutExtension
        val dir = audio.parentFile ?: return null
        return listOf("jpg", "jpeg", "png", "webp")
            .map { File(dir, "$base.$it") }
            .firstOrNull { it.isFile }
            ?: dir.listFiles()?.firstOrNull {
                it.isFile && it.nameWithoutExtension.equals(base, ignoreCase = true) &&
                    it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp")
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
