package com.music.vivi.player

import com.music.innertube.models.SongItem
import kotlinx.coroutines.flow.StateFlow

enum class PlaybackState {
    Idle,
    Loading,
    Playing,
    Paused,
    Ended,
    Error,
}

/** Queue repeat: off → all → one. */
enum class RepeatMode {
    Off,
    All,
    One,
}

data class NowPlaying(
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val durationMs: Long = 0L,
)

/** One entry in the playback queue (ids alone are not enough for UI). */
data class QueueTrack(
    val videoId: String,
    val title: String = videoId,
    val artist: String = "",
    val artworkUrl: String? = null,
)

/**
 * UI-facing playback API (desktop / Compose).
 */
interface PlaybackController {
    val state: StateFlow<PlaybackState>
    val nowPlaying: StateFlow<NowPlaying?>
    val positionMs: StateFlow<Long>
    val durationMs: StateFlow<Long>
    val isFavorite: StateFlow<Boolean>
    val lastError: StateFlow<String?>
    val queueIds: StateFlow<List<String>>
    val queueTracks: StateFlow<List<QueueTrack>>
    val queueIndex: StateFlow<Int>
    /** Linear volume 0f..1f (UI); applied to mpv as 0–100. */
    val volume: StateFlow<Float>
    val shuffleEnabled: StateFlow<Boolean>
    val repeatMode: StateFlow<RepeatMode>

    suspend fun playVideo(videoId: String)
    /** Replace queue and start at [startIndex]. */
    suspend fun playQueue(tracks: List<QueueTrack>, startIndex: Int = 0)
    /**
     * Start a YouTube Music radio (RDAMVM) seeded from [videoId].
     * If that track is already playing, keeps playback and rebuilds upcoming; otherwise plays it first.
     */
    suspend fun startRadio(videoId: String)
    /** Insert [track] immediately after the current song (Play next). */
    fun playNext(track: QueueTrack)
    /** Append [track] to the end of the queue. */
    fun addToQueue(track: QueueTrack)
    fun play()
    fun pause()
    fun stop()
    fun seekTo(positionMs: Long)
    fun next()
    fun previous()
    fun setVolume(volume: Float)
    fun toggleShuffle()
    fun cycleRepeatMode()
    suspend fun toggleFavorite()
    fun release()
}

fun SongItem.toQueueTrack(): QueueTrack = QueueTrack(
    videoId = id,
    title = title,
    artist = artists.joinToString { it.name },
    artworkUrl = thumbnail,
)
