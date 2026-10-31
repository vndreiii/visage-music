@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.music.vivi.compose.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.music.vivi.compose.ui.component.MediaHeaderAction
import com.music.vivi.compose.ui.component.MediaPageHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Renders the shared header with fake album artwork; writes a PNG only when HEADER_PNG_DIR is set. */
class MediaPageHeaderRenderTest {
    @Test
    fun albumHeaderRenders() {
        val dir = System.getenv("HEADER_PNG_DIR")
        runBlocking(Dispatchers.Swing) {
            for ((w, name) in listOf(1100 to "wide", 520 to "narrow")) {
                val scene = ImageComposeScene(width = w, height = 1000, density = Density(1f), coroutineContext = coroutineContext) {
                    MaterialTheme(colorScheme = darkColorScheme()) {
                        Box(Modifier.fillMaxSize().background(Color(0xFF14161A))) {
                            MediaPageHeader(
                                title = "Random Access Memories", meta = "2013 • 13 tracks • 1 hr 14 min",
                                description = "The fourth studio album by the French electronic music duo Daft Punk, released in 2013. It pays homage to late-1970s and early-1980s American music.",
                                onPlay = {}, onShuffle = {},
                                banner = { Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFB0306A), Color(0xFF3050C0))))) },
                                artwork = { Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFE0A030), Color(0xFF802060))))) },
                                artworkShape = RoundedCornerShape(24.dp),
                                artworkSize = { (it * 0.6f).coerceIn(180.dp, 340.dp) },
                                subtitle = { Text("Daft Punk", color = MaterialTheme.colorScheme.primary) },
                                actions = { MediaHeaderAction(androidx.compose.material.icons.Icons.Outlined.Link, "Copy link") {} },
                            )
                        }
                    }
                }
                val image = scene.render(0L)
                if (dir != null) {
                    File(dir).mkdirs()
                    File(dir, "album-header-$name.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
                }
                assertTrue(image.width == w)
                image.close(); scene.close()
            }
        }
    }
}
