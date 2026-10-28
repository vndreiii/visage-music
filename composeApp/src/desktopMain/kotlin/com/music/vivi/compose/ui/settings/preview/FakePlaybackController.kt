package com.music.vivi.compose.ui.settings.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.music.vivi.player.NowPlaying
import com.music.vivi.player.PlaybackController
import com.music.vivi.player.PlaybackState
import com.music.vivi.player.QueueTrack
import com.music.vivi.player.RepeatMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A [PlaybackController] for Settings live previews: no audio, no network, no database.
 *
 * It holds a small queue and a "playing" state. Time only moves when [advance] is called (the composable
 * [rememberFakePlaybackController] calls it a few times a second), so tests can drive it deterministically.
 * When the position reaches the end of a track the next queue entry starts, wrapping around at the end.
 */
class FakePlaybackController(
    tracks: List<QueueTrack> = PreviewSampleData.queue,
    startIndex: Int = 0,
    startPlaying: Boolean = true,
    startPositionMs: Long = 48_000L,
) : PlaybackController {
    private val _state = MutableStateFlow(if (startPlaying) PlaybackState.Playing else PlaybackState.Paused)
    private val _queue = MutableStateFlow(tracks)
    private val _index = MutableStateFlow(startIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0)))
    private val _position = MutableStateFlow(startPositionMs)
    private val _duration = MutableStateFlow(tracks.getOrNull(_index.value)?.durationMs ?: 0L)
    private val _nowPlaying = MutableStateFlow(tracks.getOrNull(_index.value)?.toNowPlaying())
    private val _favorite = MutableStateFlow(false)
    private val _volume = MutableStateFlow(0.7f)
    private val _shuffle = MutableStateFlow(false)
    private val _repeat = MutableStateFlow(RepeatMode.Off)
    private val _null = MutableStateFlow<String?>(null)
    private val _false = MutableStateFlow(false)

    override val state: StateFlow<PlaybackState> = _state.asStateFlow()
    override val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()
    override val positionMs: StateFlow<Long> = _position.asStateFlow()
    override val durationMs: StateFlow<Long> = _duration.asStateFlow()
    override val isFavorite: StateFlow<Boolean> = _favorite.asStateFlow()
    override val lastError: StateFlow<String?> = _null
    override val notice: StateFlow<String?> = _null
    private val queueIdsFlow = MutableStateFlow(tracks.map { it.videoId })
    override val queueIds: StateFlow<List<String>> = queueIdsFlow.asStateFlow()
    override val queueTracks: StateFlow<List<QueueTrack>> = _queue.asStateFlow()
    override val queueIndex: StateFlow<Int> = _index.asStateFlow()
    override val queueLoading: StateFlow<Boolean> = _false
    override val radioActive: StateFlow<Boolean> = _false
    override val volume: StateFlow<Float> = _volume.asStateFlow()
    override val shuffleEnabled: StateFlow<Boolean> = _shuffle.asStateFlow()
    override val repeatMode: StateFlow<RepeatMode> = _repeat.asStateFlow()

    override fun clearNotice() = Unit

    /** Moves the clock forward by [deltaMs] while playing; rolls over to the next track at the end. */
    fun advance(deltaMs: Long) {
        if (_state.value != PlaybackState.Playing || _queue.value.isEmpty()) return
        val next = _position.value + deltaMs
        if (_duration.value in 1..next) next() else _position.value = next
    }

    private fun publishQueue(list: List<QueueTrack>) {
        _queue.value = list
        queueIdsFlow.value = list.map { it.videoId }
    }

    private fun select(index: Int, position: Long = 0L) {
        val list = _queue.value
        if (list.isEmpty()) return
        val i = index.mod(list.size)
        _index.value = i
        _nowPlaying.value = list[i].toNowPlaying()
        _duration.value = list[i].durationMs
        _position.value = position
    }

    override suspend fun restoreQueue(tracks: List<QueueTrack>, startIndex: Int, positionMs: Long) {
        publishQueue(tracks)
        select(startIndex, positionMs)
    }

    override suspend fun playVideo(videoId: String) {
        val i = _queue.value.indexOfFirst { it.videoId == videoId }
        if (i >= 0) { select(i); _state.value = PlaybackState.Playing }
    }

    override suspend fun playQueue(tracks: List<QueueTrack>, startIndex: Int) {
        publishQueue(tracks)
        select(startIndex)
        _state.value = PlaybackState.Playing
    }

    override suspend fun playLocalQueue(tracks: List<QueueTrack>, startIndex: Int) = playQueue(tracks, startIndex)

    override suspend fun startRadio(videoId: String) = Unit

    override fun playNext(track: QueueTrack) {
        val list = _queue.value.toMutableList()
        list.add((_index.value + 1).coerceIn(0, list.size), track)
        publishQueue(list)
    }

    override fun addToQueue(track: QueueTrack) = publishQueue(_queue.value + track)

    override fun moveQueueItem(from: Int, to: Int) {
        val list = _queue.value.toMutableList()
        if (from !in list.indices || to !in list.indices || from == to) return
        val current = list.getOrNull(_index.value)
        list.add(to, list.removeAt(from))
        publishQueue(list)
        // The current song keeps playing wherever it ended up.
        current?.let { c -> _index.value = list.indexOf(c).coerceAtLeast(0) }
    }

    override fun removeFromQueue(index: Int) {
        if (index !in _queue.value.indices || index == _index.value) return
        val list = _queue.value.toMutableList().also { it.removeAt(index) }
        publishQueue(list)
        if (index < _index.value) _index.value -= 1
    }

    override fun play() { _state.value = PlaybackState.Playing }
    override fun pause() { _state.value = PlaybackState.Paused }
    override fun toggleFromList() { if (_state.value == PlaybackState.Playing) pause() else play() }
    override fun stop() { _state.value = PlaybackState.Paused; _position.value = 0L }
    override fun seekTo(positionMs: Long) { _position.value = positionMs.coerceIn(0L, _duration.value) }
    override fun next() = select(_index.value + 1)
    override fun previous() = select(_index.value - 1)
    override fun setVolume(volume: Float) { _volume.value = volume.coerceIn(0f, 1f) }
    override fun toggleShuffle() { _shuffle.value = !_shuffle.value }
    override fun cycleRepeatMode() {
        _repeat.value = when (_repeat.value) { RepeatMode.Off -> RepeatMode.All; RepeatMode.All -> RepeatMode.One; RepeatMode.One -> RepeatMode.Off }
    }
    override suspend fun toggleFavorite() { _favorite.value = !_favorite.value }
    override suspend fun setLiked(videoId: String, like: Boolean) { _favorite.value = like }
    override fun release() = Unit
}

private fun QueueTrack.toNowPlaying() = NowPlaying(
    videoId = videoId, title = title, artist = artist, album = album, artworkUrl = artworkUrl, durationMs = durationMs,
)

/** A [FakePlaybackController] that keeps playing while it is in the composition (ticks 4 times a second). */
@Composable
fun rememberFakePlaybackController(paused: Boolean = false): FakePlaybackController {
    val controller = remember { FakePlaybackController() }
    LaunchedEffect(controller, paused) {
        if (paused) return@LaunchedEffect
        while (true) {
            delay(250)
            controller.advance(250)
        }
    }
    return controller
}
