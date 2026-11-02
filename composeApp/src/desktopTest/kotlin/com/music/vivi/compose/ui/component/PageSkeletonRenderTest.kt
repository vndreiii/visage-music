@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.music.vivi.compose.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Renders every loading wireframe; writes PNGs only when SKELETON_PNG_DIR is set. */
class PageSkeletonRenderTest {
    @Test
    fun everySkeletonRenders() {
        val dir = System.getenv("SKELETON_PNG_DIR")
        runBlocking(Dispatchers.Default) {
            for ((w, h) in listOf(1100 to 900, 390 to 844)) for (kind in PageSkeleton.entries) {
                val scene = ImageComposeScene(width = w, height = h, density = Density(1f), coroutineContext = coroutineContext) {
                    MaterialTheme(colorScheme = darkColorScheme()) {
                        Box(Modifier.fillMaxSize().background(Color(0xFF14161A))) { PageLoadingState("t", kind) }
                    }
                }
                val image = scene.render(300_000_000L)
                if (dir != null) {
                    File(dir).mkdirs()
                    File(dir, "skeleton-${kind.name}-$w.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
                }
                assertTrue(image.width == w)
                image.close(); scene.close()
            }
        }
    }
}
