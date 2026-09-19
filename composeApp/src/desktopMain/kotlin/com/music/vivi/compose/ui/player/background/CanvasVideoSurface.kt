package com.music.vivi.compose.ui.player.background

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.layout.StackPane
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.scene.media.MediaView
import java.awt.Color
import javax.swing.SwingUtilities

@Composable
fun CanvasVideoSurface(
    videoUrl: String,
    playing: Boolean,
    modifier: Modifier = Modifier,
    aspectRatio: Float? = null
) {
    val jfxPanel = remember { JFXPanel().apply { background = Color.BLACK } }
    
    var playerState by remember { mutableStateOf<MediaPlayer?>(null) }
    
    LaunchedEffect(playing, playerState) {
        val p = playerState ?: return@LaunchedEffect
        Platform.runLater {
            if (playing) p.play() else p.pause()
        }
    }
    
    DisposableEffect(videoUrl) {
        var player: MediaPlayer? = null
        Platform.runLater {
            try {
                val media = Media(videoUrl)
                player = MediaPlayer(media).apply {
                    isAutoPlay = true
                    cycleCount = MediaPlayer.INDEFINITE
                    isMute = true
                }
                playerState = player
                val mediaView = MediaView(player).apply {
                    isPreserveRatio = false // fill
                }
                val root = StackPane(mediaView)
                root.style = "-fx-background-color: black;"
                val scene = Scene(root)
                mediaView.fitWidthProperty().bind(scene.widthProperty())
                mediaView.fitHeightProperty().bind(scene.heightProperty())
                jfxPanel.scene = scene
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        onDispose {
            Platform.runLater {
                player?.stop()
                player?.dispose()
            }
        }
    }

    SwingPanel(
        background = androidx.compose.ui.graphics.Color.Black,
        modifier = modifier.fillMaxSize(),
        factory = { jfxPanel }
    )
}
