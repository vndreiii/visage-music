package com.music.vivi.compose.local

import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.PrefKeys
import com.music.vivi.player.PlaybackController
import com.music.vivi.player.QueueTrack
import kotlinx.coroutines.*
import java.io.File
import java.util.Properties

/** Saves queue metadata, never expiring stream URLs. Restored audio stays stopped. */
class QueuePersistence(private val prefs: DesktopPrefs, private val controller: PlaybackController) {
    private val file = File(DesktopPrefs.defaultFile().parentFile, "queue.properties")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            if (prefs.getBoolean(PrefKeys.PersistentQueue, true)) {
                runCatching {
                    if (file.isFile) {
                        val props = Properties().apply { file.inputStream().use { load(it) } }
                        val count = props.getProperty("count")?.toIntOrNull()?.coerceIn(0, 2000) ?: 0
                        val tracks = (0 until count).mapNotNull { i ->
                            val id = props.getProperty("$i.id") ?: return@mapNotNull null
                            QueueTrack(
                                videoId = id,
                                title = props.getProperty("$i.title", id),
                                artist = props.getProperty("$i.artist", ""),
                                artworkUrl = props.getProperty("$i.art"),
                                localPath = props.getProperty("$i.path"),
                                durationMs = props.getProperty("$i.duration")?.toLongOrNull() ?: 0,
                                album = props.getProperty("$i.album"),
                                lyricsPath = props.getProperty("$i.lyrics"),
                                embeddedLyrics = props.getProperty("$i.embeddedLyrics"),
                            )
                        }
                        controller.restoreQueue(tracks, props.getProperty("index")?.toIntOrNull() ?: 0, props.getProperty("position")?.toLongOrNull() ?: 0)
                    }
                }.onFailure { System.err.println("Couldn't restore queue: ${it.message}") }
            }
            while (isActive) {
                delay(5000)
                save()
            }
        }
    }

    @Synchronized fun save() {
        runCatching {
            if (!prefs.getBoolean(PrefKeys.PersistentQueue, true)) {
                file.delete()
                return
            }
            val tracks = controller.queueTracks.value.take(2000)
            val props = Properties()
            props["count"] = tracks.size.toString()
            props["index"] = controller.queueIndex.value.toString()
            props["position"] = controller.positionMs.value.toString()
            tracks.forEachIndexed { i, track ->
                props["$i.id"] = track.videoId
                props["$i.title"] = track.title
                props["$i.artist"] = track.artist
                props["$i.duration"] = track.durationMs.toString()
                track.artworkUrl?.let { props["$i.art"] = it }
                track.localPath?.let { props["$i.path"] = it }
                track.album?.let { props["$i.album"] = it }
                track.lyricsPath?.let { props["$i.lyrics"] = it }
                track.embeddedLyrics?.let { props["$i.embeddedLyrics"] = it }
            }
            file.parentFile.mkdirs()
            val temporary = File.createTempFile("queue-", ".tmp", file.parentFile)
            try {
                temporary.outputStream().use { props.store(it, "VIVI saved queue") }
                java.nio.file.Files.move(temporary.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            } finally {
                temporary.delete()
            }
        }.onFailure { System.err.println("Couldn't save queue: ${it.message}") }
    }

    fun close() {
        scope.cancel()
        save()
    }
}
