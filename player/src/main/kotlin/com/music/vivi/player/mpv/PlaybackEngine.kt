package com.music.vivi.player.mpv

/** Audio device boundary; deterministic engines can exercise queue transitions without libmpv. */
interface PlaybackEngine : AutoCloseable {
    fun load(url: String, httpHeaders: Map<String, String> = emptyMap())
    suspend fun awaitReady()
    fun play()
    fun pause()
    fun stop()
    fun halt()
    fun setVolume(percent: Int)
    fun seek(positionSec: Double)

    /** Whether the engine itself is paused right now (independent of what the controller asked for). */
    fun isPaused(): Boolean = false
}
