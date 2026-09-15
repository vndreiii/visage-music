@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.music.vivi.compose.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.music.vivi.compose.prefs.SliderStyle
import com.music.vivi.compose.ui.component.PageLoadingState
import com.music.vivi.compose.ui.settings.LivePlayerLayoutHeroPreview
import com.music.vivi.compose.ui.settings.LiveSliderStyleHeroPreview
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.io.File
import kotlin.test.*

class DesktopPreviewTest {
    @Test fun loadingAndSettingsRenderAtDesktopSize() = runBlocking(Dispatchers.Swing) {
        val scene = ImageComposeScene(width = 1100, height = 720, coroutineContext = coroutineContext) {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    Row {
                        Box(Modifier.weight(1f)) { PageLoadingState("Loading your library", detail = true) }
                        Column(Modifier.width(500.dp).padding(16.dp)) {
                            Text("Make it yours", style = MaterialTheme.typography.headlineMedium)
                            LiveSliderStyleHeroPreview(SliderStyle.WAVY, squiggly = true)
                            LiveSliderStyleHeroPreview(SliderStyle.EXPRESSIVE, squiggly = false)
                            LivePlayerLayoutHeroPreview(framed = false)
                            LivePlayerLayoutHeroPreview(framed = true)
                        }
                    }
                }
            }
        }
        try {
            scene.render(0).close()
            val image = scene.render(500_000_000)
            val bytes = image.encodeToData()?.bytes
            assertNotNull(bytes)
            File("build/reports/ui/desktop-preview.png").apply { parentFile.mkdirs(); writeBytes(bytes) }
            image.close()
        } finally { scene.close() }
    }
}
