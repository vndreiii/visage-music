package com.music.vivi.desktop.ui

import com.music.vivi.player.DefaultPlaybackController
import com.music.vivi.player.PlaybackController
import com.music.vivi.player.PlaybackState
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.javafx.JavaFx
import kotlinx.coroutines.launch

/**
 * Minimal playback controls for verifying libmpv + MPRIS + Innertube like.
 */
class PlaybackPane(
    private val accountName: String,
) : BorderPane() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.JavaFx)
    private val controller: PlaybackController = DefaultPlaybackController.create()

    private val status = Label("Ready")
    private val track = Label("—").apply { isWrapText = true }
    private val videoField = TextField("dQw4w9WgXcQ").apply {
        promptText = "YouTube video id"
        prefColumnCount = 14
    }
    private val playButton = Button("Play")
    private val pauseButton = Button("Pause")
    private val favButton = Button("Favourite")

    init {
        playButton.setOnAction {
            val id = videoField.text.trim()
            if (id.isEmpty()) return@setOnAction
            scope.launch(Dispatchers.Default) {
                controller.playVideo(id)
            }
        }
        pauseButton.setOnAction {
            if (controller.state.value == PlaybackState.Playing) controller.pause()
            else controller.play()
        }
        favButton.setOnAction {
            scope.launch(Dispatchers.Default) {
                runCatching { controller.toggleFavorite() }
                    .onFailure { status.text = "Like failed: ${it.message}" }
            }
        }

        scope.launch {
            controller.state.collectLatest { s ->
                status.text = "State: $s" +
                    (controller.lastError.value?.let { " — $it" } ?: "")
            }
        }
        scope.launch {
            controller.nowPlaying.collectLatest { np ->
                track.text = if (np == null) "—" else "${np.title} — ${np.artist}"
            }
        }
        scope.launch {
            controller.isFavorite.collectLatest { fav ->
                favButton.text = if (fav) "Unlike" else "Favourite"
            }
        }
        scope.launch {
            controller.positionMs.collectLatest { pos ->
                val dur = controller.durationMs.value
                if (dur > 0) {
                    status.text = "State: ${controller.state.value}  ${pos / 1000}s / ${dur / 1000}s"
                }
            }
        }

        top = HBox(10.0).apply {
            alignment = Pos.CENTER_LEFT
            padding = Insets(12.0)
            children += Label("Signed in as $accountName").apply {
                style = "-fx-font-size: 14px; -fx-font-weight: bold;"
            }
        }
        center = VBox(10.0).apply {
            padding = Insets(0.0, 12.0, 12.0, 12.0)
            children += track
            children += status
            children += HBox(8.0).apply {
                alignment = Pos.CENTER_LEFT
                children += videoField
                children += playButton
                children += pauseButton
                children += favButton
            }
            children += Label("MPRIS: org.mpris.MediaPlayer2.vivi").apply {
                style = "-fx-text-fill: #666;"
            }
        }
    }

    fun dispose() {
        controller.release()
    }
}
