package com.music.vivi.compose.ui.player.background

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import com.music.vivi.player.mpv.MpvCanvasPlayer
import java.awt.Canvas
import java.awt.Color as AwtColor
import kotlin.concurrent.thread

/**
 * Silent looping canvas video via [MpvCanvasPlayer] embedded in an AWT [Canvas].
 */
@Composable
fun CanvasVideoSurface(
    videoUrl: String,
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    val canvas = remember { Canvas().apply { background = AwtColor.BLACK } }
    val player = remember { MpvCanvasPlayer() }

    DisposableEffect(videoUrl) {
        var running = true
        val starter = thread(name = "canvas-mpv-start", isDaemon = true) {
            var tries = 0
            while (running && tries < 60) {
                if (canvas.isDisplayable && canvas.width > 0) break
                Thread.sleep(50)
                tries++
            }
            if (!running) return@thread
            player.attachAndPlay(canvas, videoUrl)
            player.setPaused(!playing)
        }
        onDispose {
            running = false
            starter.interrupt()
            player.close()
        }
    }

    LaunchedEffect(playing) {
        player.setPaused(!playing)
    }

    SwingPanel(
        background = Color.Black,
        factory = { canvas },
        modifier = modifier.fillMaxSize(),
    )
}
