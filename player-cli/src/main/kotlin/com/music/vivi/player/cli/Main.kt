package com.music.vivi.player.cli

import com.music.vivi.player.DefaultPlaybackController
import com.music.vivi.player.FavoriteActions
import com.music.vivi.player.PlaybackState
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

fun main(args: Array<String>) = runBlocking {
    when (args.firstOrNull()) {
        "play" -> runPlay(args.drop(1))
        "like" -> runLike(args.drop(1))
        "switch" -> runSwitch()
        else -> {
            System.err.println(
                """
                Usage:
                  player-cli play <videoId> [--seconds N] [--like]
                  player-cli like <videoId> <true|false>
                """.trimIndent(),
            )
            kotlin.system.exitProcess(2)
        }
    }
}

private suspend fun runLike(args: List<String>) {
    if (args.size < 2) {
        System.err.println("Usage: player-cli like <videoId> <true|false>")
        kotlin.system.exitProcess(2)
    }
    FavoriteActions.setLiked(args[0], args[1].toBooleanStrict())
    println("likeVideo(${args[0]}, ${args[1]}) ok")
}

private suspend fun runPlay(args: List<String>) {
    if (args.isEmpty()) {
        System.err.println("Usage: player-cli play <videoId> [--seconds N] [--like]")
        kotlin.system.exitProcess(2)
    }
    val videoId = args[0]
    val holdSeconds = args
        .indexOf("--seconds")
        .takeIf { it >= 0 && it + 1 < args.size }
        ?.let { args[it + 1].toLongOrNull() }
        ?: 25L
    val doLike = args.contains("--like")

    val controller = DefaultPlaybackController.create()
    try {
        println("Resolving + loading $videoId …")
        controller.playVideo(videoId)
        val err = controller.lastError.value
        if (controller.state.value == PlaybackState.Error || err != null) {
            System.err.println("Playback failed: $err")
            kotlin.system.exitProcess(1)
        }
        val np = controller.nowPlaying.value
        println("Playing: ${np?.title} — ${np?.artist}")
        println("State=${controller.state.value} durationMs=${controller.durationMs.value}")
        println("MPRIS bus: org.mpris.MediaPlayer2.vivi  (try: playerctl -p vivi metadata)")

        if (doLike) {
            delay(2.seconds)
            runCatching { controller.toggleFavorite() }
                .onSuccess { println("Favourite toggled -> ${controller.isFavorite.value}") }
                .onFailure { System.err.println("Like failed: ${it.message}") }
            delay(1.seconds)
            println("playerctl rating should be ${if (controller.isFavorite.value) 1.0 else 0.0}")
        }

        withTimeoutOrNull(holdSeconds.seconds) {
            while (true) {
                delay(1.seconds)
                val pos = controller.positionMs.value
                println("pos=${pos}ms state=${controller.state.value} fav=${controller.isFavorite.value}")
                if (controller.state.value == PlaybackState.Ended) break
                if (controller.state.value == PlaybackState.Error) {
                    System.err.println("Error: ${controller.lastError.value}")
                    break
                }
            }
        } ?: println("Hold time elapsed (${holdSeconds}s)")
    } finally {
        controller.release()
    }
}

/** Repro: play a queue, then pick a song from a different queue mid-playback. */
private suspend fun runSwitch() {
    val controller = DefaultPlaybackController.create(
        fadeSettings = { com.music.vivi.player.PlaybackFadeSettings(crossfadeEnabled = System.getenv("XFADE") == "1") },
    )
    controller.setVolume(0f)
    fun q(vararg ids: String) = ids.map { com.music.vivi.player.QueueTrack(videoId = it) }
    val a = q("dQw4w9WgXcQ", "kJQP7kiw5Fk", "fJ9rUzIMcZQ")
    val b = q("hTWKbfoikeg", "RgKAFK5djSk", "JGwWNGJdvx8", "60ItHLz5WEA")
    suspend fun watch(label: String, seconds: Int) {
        repeat(seconds) {
            delay(1.seconds)
            println("[$label] t=${it + 1}s state=${controller.state.value} pos=${controller.positionMs.value} np=${controller.nowPlaying.value?.videoId} err=${controller.lastError.value}")
        }
    }
    println("== play queue A[0]")
    controller.playQueue(a, 0)
    watch("A", 6)
    println("== pick B[2] from another list")
    controller.playQueue(b, 2)
    watch("B", 10)
    System.getenv("LOCAL_FILE")?.let { path ->
        println("== pick a local file")
        controller.playLocalQueue(listOf(com.music.vivi.player.QueueTrack(videoId = "local:test", title = "local", localPath = path)), 0)
        watch("L", 8)
        println("== back to online B[0]")
        controller.playQueue(b, 0)
        watch("B0", 8)
    }
    println("== pause, then pick A[1]")
    controller.pause()
    delay(1.seconds)
    controller.playQueue(a, 1)
    watch("A1", 10)
    kotlin.system.exitProcess(0)
}
