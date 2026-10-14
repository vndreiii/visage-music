@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.music.vivi.compose.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.music.vivi.compose.prefs.SliderStyle
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Renders the Expressive seek bar playing and paused so the look can be inspected (VISAGE_RENDER_OUT keeps the PNG). */
class SeekBarRenderTest {
    @Test
    fun rendersExpressiveSeekBar() {
        val scene = ImageComposeScene(width = 900, height = 260, density = Density(1.5f)) {
            Column(Modifier.fillMaxSize().background(Color(0xFF2A1F26)).padding(24.dp)) {
                PlayerSeekBar(0.4f, {}, {}, SliderStyle.M3_EXPRESSIVE, squiggly = false, isPlaying = true)
                PlayerSeekBar(0.4f, {}, {}, SliderStyle.M3_EXPRESSIVE, squiggly = false, isPlaying = false)
                PlayerSeekBar(0.7f, {}, {}, SliderStyle.M3_EXPRESSIVE, squiggly = false, compact = true)
            }
        }
        try {
            var image = scene.render(0L)
            for (i in 1..40) image = scene.render(i * 16_000_000L)
            val png = image.encodeToData()!!.bytes
            assertTrue(png.size > 3_000)
            com.music.vivi.nativelib.AppEnv.get("RENDER_OUT")?.let { dir ->
                File(dir).mkdirs()
                File(dir, "seekbar.png").writeBytes(png)
            }
        } finally {
            scene.close()
        }
    }
}
