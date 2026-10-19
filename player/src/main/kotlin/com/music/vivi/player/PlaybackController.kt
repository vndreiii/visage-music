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
    val localPath: String? = null,
    val lyricsPath: String? = null,
    val embeddedLyrics: String? = null,
)

/** One entry in the playback queue (ids alone are not enough for UI). */
data class QueueTrack(
    val videoId: String,
    val title: String = videoId,
    val artist: String = "",
    val artworkUrl: String? = null,
    /** Absolute path when this is a local/offline file (mpv loads it directly). */
    val localPath: String? = null,
    val durationMs: Long = 0L,
    val album: String? = null,
    val lyricsPath: String? = null,
    val embeddedLyrics: String? = null,
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

    /** True while follow-up tracks (radio) for the current song are being fetched. */
    val queueLoading: StateFlow<Boolean>

    /** True once the user started a radio; the upcoming tracks then come from that radio. */
    val radioActive: StateFlow<Boolean>
    /** Linear volume 0f..1f (UI); applied to mpv as 0–100. */
    val volume: StateFlow<Float>
    val shuffleEnabled: StateFlow<Boolean>
    val repeatMode: StateFlow<RepeatMode>

    /** Restore a saved queue without starting audio. */
    suspend fun restoreQueue(tracks: List<QueueTrack>, startIndex: Int, positionMs: Long)
    suspend fun playVideo(videoId: String)
    /** Replace queue and start at [startIndex]. */
    suspend fun playQueue(tracks: List<QueueTrack>, startIndex: Int = 0)
    /**
     * Play a local/offline file in-app (no stream resolve / radio).
     */
    suspend fun playLocalQueue(tracks: List<QueueTrack>, startIndex: Int = 0)
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

    /**
     * Row-click toggle for the track that is already current (lists, search results, cards).
     * Ignored right after a track was started, so a double-click on a row doesn't play the song
     * and immediately pause it again.
     */
    fun toggleFromList()
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

fun com.music.innertube.models.YTItem.toQueueTrack(): QueueTrack = when (this) {
    is com.music.innertube.models.SongItem -> QueueTrack(
        videoId = id,
        title = title,
        artist = artists.joinToString { it.name },
        artworkUrl = thumbnail,
        durationMs = (duration?.toLong() ?: 0L) * 1000L,
        album = album?.name,
    ).also { ArtistIdRegistry.remember(artists) }
    else -> QueueTrack(
        videoId = id,
        title = title,
        artist = "",
        artworkUrl = thumbnail,
    )
}
