@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.music.vivi.compose.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.music.vivi.compose.prefs.DesktopPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull

/** Smoke test: settings pages that need no playback controller compose offscreen; PNGs go to build/reports/ui. */
class SettingsScreensRenderTest {
    private fun render(name: String, content: @Composable () -> Unit) = runBlocking(Dispatchers.Swing) {
        val dir = File(System.getProperty("java.io.tmpdir"), "settings-render-${System.nanoTime()}").apply { mkdirs() }
        val prefs = DesktopPrefs(File(dir, "s.properties"))
        startKoin { modules(module { single { prefs } }) }
        try {
            val scene = ImageComposeScene(width = 900, height = 1500, coroutineContext = coroutineContext) {
                MaterialTheme(colorScheme = darkColorScheme()) { Surface(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize()) { content() } } }
            }
            try {
                scene.render(0).close()
                val image = scene.render(400_000_000)
                val bytes = image.encodeToData()?.bytes
                assertNotNull(bytes)
                File("build/reports/ui/$name.png").apply { parentFile.mkdirs(); writeBytes(bytes) }
                image.close()
            } finally { scene.close() }
        } finally { stopKoin(); dir.deleteRecursively() }
    }

    @Test fun playbackPage() = render("settings-playback") { PlayerSettingsScreen(rememberNavController()) }
    @Test fun downloadsPage() = render("settings-downloads") { DownloadsSettingsScreen(rememberNavController()) }
}
