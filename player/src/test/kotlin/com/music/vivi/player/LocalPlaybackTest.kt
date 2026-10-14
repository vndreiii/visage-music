package com.music.vivi.player

import com.music.vivi.player.mpv.MpvPlayer
import com.music.vivi.player.mpv.PlaybackEngine
import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.test.*

class LocalPlaybackTest {
    private class FakeEngine(val state: (MpvPlayer.State) -> Unit) : PlaybackEngine {
        var loaded: String? = null
        var paused = false
        var level = 100
        var seekSeconds = 0.0
        var holdReady = false
        var ready = CompletableDeferred<Unit>()
        override fun load(url: String, httpHeaders: Map<String, String>) {
            loaded = url; paused = false
            ready = CompletableDeferred()
            if (!holdReady) ready.complete(Unit)
        }
        override suspend fun awaitReady() { ready.await() }
        override fun play() { paused = false; state(MpvPlayer.State.Playing) }
        override fun pause() { paused = true; state(MpvPlayer.State.Paused) }
        override fun stop() { halt(); state(MpvPlayer.State.Idle) }
        override fun halt() { loaded = null; paused = true; ready.cancel() }
        override fun setVolume(percent: Int) { level = percent }
        override fun seek(positionSec: Double) { seekSeconds = positionSec }
        override fun close() { halt() }
    }

    private suspend fun fixture(crossfade: Boolean = false, block: suspend (DefaultPlaybackController, List<FakeEngine>, List<QueueTrack>) -> Unit) {
        val directory = Files.createTempDirectory("vivi-playback-test").toFile()
        val tracks = (0..2).map { i ->
            val file = directory.resolve("song $i.flac").apply { writeText("fixture") }
            QueueTrack("local:$i", title = "Track $i", localPath = file.absolutePath, durationMs = 60_000)
        }
        val engines = mutableListOf<FakeEngine>()
        val controller = DefaultPlaybackController(
            applyStoredSession = false,
            fadeSettings = { PlaybackFadeSettings(crossfadeEnabled = crossfade, crossfadeSeconds = 1, playPauseFadeEnabled = false) },
            enableMpris = false,
            engineFactory = { state, _, _ -> FakeEngine(state).also { engines += it } },
        )
        try { block(controller, engines, tracks) } finally { controller.release(); directory.deleteRecursively() }
    }

    private suspend fun eventually(predicate: () -> Boolean) = withTimeout(3000) { while (!predicate()) delay(10) }

    @Test fun `restored track warms paused and resumes without another load`() = runBlocking {
        fixture { controller, engines, tracks ->
            controller.restoreQueue(tracks, 1, 12_000)
            eventually { controller.state.value == PlaybackState.Paused }
            assertEquals(tracks[1].localPath, engines[0].loaded)
            assertTrue(engines[0].paused)
            assertEquals(12.0, engines[0].seekSeconds)
            engines[0].holdReady = true // A second load would block, instead Play resumes the prepared file.
            controller.play()
            eventually { controller.state.value == PlaybackState.Playing && !engines[0].paused }
            assertEquals(tracks[1].localPath, engines[0].loaded)
        }
    }

    @Test fun `queue row clicks retain the remaining local files`() = runBlocking {
        fixture { controller, _, tracks ->
            controller.playLocalQueue(tracks)
            controller.playQueue(controller.queueTracks.value, 1)
            assertEquals(tracks.map { it.videoId }, controller.queueIds.value)
            controller.next()
            eventually { controller.state.value == PlaybackState.Playing && controller.queueIndex.value == 2 }
            assertEquals(tracks[2].localPath, controller.nowPlaying.value?.localPath)
        }
    }

    @Test fun `local EOF repeats a file by reloading it`() = runBlocking {
        fixture { controller, engines, tracks ->
            controller.playLocalQueue(tracks)
            controller.cycleRepeatMode(); controller.cycleRepeatMode()
            engines[0].loaded = null
            engines[0].state(MpvPlayer.State.Ended)
            eventually { engines[0].loaded == tracks[0].localPath && controller.state.value == PlaybackState.Playing }
            controller.next()
            eventually { controller.queueIndex.value == 1 }
        }
    }

    @Test fun `stop invalidates a pending file load`() = runBlocking {
        fixture { controller, engines, tracks ->
            engines[0].holdReady = true
            val loading = launch { controller.playLocalQueue(tracks) }
            eventually { engines[0].loaded != null }
            assertEquals(PlaybackState.Loading, controller.state.value)
            controller.stop()
            loading.join()
            assertEquals(PlaybackState.Idle, controller.state.value)
            assertTrue(engines.all { it.loaded == null })
        }
    }

    @Test fun `pause during crossfade pauses both streams and resume starts promoted stream`() = runBlocking {
        fixture(crossfade = true) { controller, engines, tracks ->
            controller.playLocalQueue(tracks)
            val transition = launch { controller.playLocalQueue(tracks, 1) }
            eventually { engines.all { it.loaded != null } }
            controller.pause()
            assertEquals(PlaybackState.Paused, controller.state.value)
            assertTrue(engines.all { it.paused })
            controller.setVolume(.3f)
            transition.join()
            assertEquals(PlaybackState.Paused, controller.state.value)
            controller.play()
            eventually { !engines[1].paused }
            assertEquals(30, engines[1].level)
            assertNull(engines[0].loaded)
        }
    }

    @Test fun `stop during crossfade does not restart incoming audio`() = runBlocking {
        fixture(crossfade = true) { controller, engines, tracks ->
            controller.playLocalQueue(tracks)
            val transition = launch { controller.playLocalQueue(tracks, 1) }
            eventually { engines.all { it.loaded != null } }
            controller.stop()
            transition.join()
            assertEquals(PlaybackState.Idle, controller.state.value)
            assertTrue(engines.all { it.loaded == null })
        }
    }

    @Test fun `restored queue stays silent until play and restores position`() = runBlocking {
        fixture { controller, engines, tracks ->
            controller.restoreQueue(tracks, 1, 15_000)
            eventually { controller.state.value == PlaybackState.Paused }
            assertTrue(engines.all { it.paused })
            controller.play()
            eventually { controller.state.value == PlaybackState.Playing }
            assertEquals(tracks[1].localPath, engines[0].loaded)
            assertEquals(15.0, engines[0].seekSeconds)
        }
    }

    @Test fun `unavailable incoming file stops outgoing track and reports failure`() = runBlocking {
        fixture(crossfade = true) { controller, engines, tracks ->
            controller.playLocalQueue(tracks)
            controller.playLocalQueue(listOf(tracks[1].copy(localPath = "/missing/vivi-song.flac")))
            assertEquals(PlaybackState.Error, controller.state.value)
            assertTrue(controller.lastError.value.orEmpty().contains("unavailable"))
            assertTrue(engines.all { it.loaded == null })
        }
    }
}
