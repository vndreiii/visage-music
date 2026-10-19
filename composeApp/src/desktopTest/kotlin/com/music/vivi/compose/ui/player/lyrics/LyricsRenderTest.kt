@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.music.vivi.compose.ui.player.lyrics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.music.lyricsync.LyricsParser
import com.music.lyricsync.LyricsTimeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull

/** Smoke-renders the lyrics list in every style/animation/gap combination (no crashes, non-empty output). */
class LyricsRenderTest {
    private val sample = """
        [00:01.00]<00:01.00>City <00:01.50>lights <00:02.10>are <00:02.50>fading <00:03.30>slow<00:04.00>
        [00:04.20]<00:04.20>Hold <00:04.70>the <00:05.00>night <00:05.60>a <00:05.80>little <00:06.50>longer<00:07.40>
        [00:07.60]Every heartbeat sings along
        [00:07.70]{bg}<00:07.70>(ooh <00:08.30>ooh)<00:09.00>
        [00:10.50]Dancing through the afterglow
        [00:10.50]Bailando en el resplandor
        [00:20.50]<00:20.50>Hold <00:21.10>me <00:21.50>till <00:22.00>the <00:22.30>morning <00:23.30>comes<00:24.20>
    """.trimIndent()

    private fun render(cfg: LyricsConfig, atMs: Float, name: String) = runBlocking(Dispatchers.Swing) {
        val timeline = LyricsTimeline.build(LyricsParser.parse(sample), cfg.estimateWords)
        val clock = mutableFloatStateOf(atMs)
        val scene = ImageComposeScene(width = 700, height = 420, coroutineContext = coroutineContext) {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Box(Modifier.fillMaxSize().background(Color(0xFF14161A))) {
                    LyricsList(timeline, cfg, clock, accent = Color(0xFFFF6FA5), onSeek = null)
                }
            }
        }
        try {
            scene.render(0).close()
            scene.render(400_000_000).close()
            clock.floatValue = atMs + 16f
            val image = scene.render(900_000_000)
            val bytes = image.encodeToData()?.bytes
            assertNotNull(bytes)
            System.getenv("LYRICS_SHOT_DIR")?.let { File(it, "$name.png").apply { parentFile.mkdirs() }.writeBytes(bytes) }
        } finally {
            scene.close()
        }
    }

    @Test
    fun allStylesAndAnimationsRender() {
        for (style in LyricsTextStyle.entries) {
            render(LyricsConfig(textStyle = style, glow = 0.9f), 5300f, "style-$style")
        }
        for (anim in LyricsAnimation.entries) {
            render(LyricsConfig(animation = anim), 5300f, "anim-$anim")
        }
    }

    @Test
    fun gapStylesRender() {
        for (g in LyricsGapStyle.entries) {
            render(LyricsConfig(gapStyle = g), 16000f, "gap-$g")
        }
    }

    @Test
    fun visibilityModesRender() {
        for (v in LyricsVisibility.entries) {
            render(LyricsConfig(visibility = v), 11500f, "vis-$v")
        }
    }
}
