@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.music.vivi.compose.ui.expressive

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Renders the Expressive kit offscreen (CPU) so it can be inspected; set VIVI_RENDER_OUT to keep the PNG. */
class ExpressiveGalleryTest {
    @Test
    fun rendersGallery() {
        val scene = ImageComposeScene(width = 1000, height = 1150, density = Density(1.5f)) { ExpressiveGallery() }
        try {
            var image = scene.render(0L)
            // Let the spring-driven shapes settle.
            for (i in 1..30) image = scene.render(i * 16_000_000L)
            val png = image.encodeToData()!!.bytes
            assertTrue(png.size > 5_000, "gallery rendered blank")
            com.music.vivi.nativelib.AppEnv.get("RENDER_OUT")?.let { dir ->
                File(dir).mkdirs()
                File(dir, "expressive-gallery.png").writeBytes(png)
            }
        } finally {
            scene.close()
        }
    }
}
