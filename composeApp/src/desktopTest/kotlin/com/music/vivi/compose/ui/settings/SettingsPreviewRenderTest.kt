@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.music.vivi.compose.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.FullscreenKeys
import com.music.vivi.compose.ui.player.FullscreenPlayerPreview
import com.music.vivi.compose.ui.settings.preview.SettingsPreviewFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Smoke test for the live previews on the Lyrics and Fullscreen player settings pages: they compose with sample data and a
 * fake controller (no real player services) and keep composing when prefs change. Set SETTINGS_SHOT_DIR to save PNGs.
 */
class SettingsPreviewRenderTest {
    private fun withPrefs(block: (DesktopPrefs) -> Unit) {
        val prefs = DesktopPrefs(File.createTempFile("visage-prefs", ".properties").apply { deleteOnExit() })
        startKoin { modules(module { single { prefs } }) }
        try { block(prefs) } finally { stopKoin() }
    }

    private fun shoot(width: Int, height: Int, name: String, afterFirst: () -> Unit = {}, content: @Composable () -> Unit) =
        runBlocking(Dispatchers.Swing) {
            val scene = ImageComposeScene(width = width, height = height, coroutineContext = coroutineContext) {
                MaterialTheme(colorScheme = darkColorScheme()) { content() }
            }
            try {
                var t = 0L
                fun step(n: Int) = repeat(n) { scene.render(t).close(); t += 50_000_000 }
                step(6)
                afterFirst()
                step(24)
                val bytes = scene.render(t).encodeToData()?.bytes
                assertNotNull(bytes)
                System.getenv("SETTINGS_SHOT_DIR")?.let { File(it, "$name.png").apply { parentFile.mkdirs() }.writeBytes(bytes) }
            } finally {
                scene.close()
            }
        }

    @Test
    fun fullscreenPreviewRendersAndFollowsPrefs() = withPrefs { prefs ->
        shoot(1280, 720, "fs-preview-default") { SettingsPreviewFrame(pageKey = "t", sticky = false) { FullscreenPlayerPreview(Modifier) } }
        prefs.setString(FullscreenKeys.CoverSide, "RIGHT")
        prefs.setString(FullscreenKeys.CoverShape, "CIRCLE")
        prefs.setBoolean(FullscreenKeys.ShowRadio, true)
        prefs.setBoolean(FullscreenKeys.ShowClock, true)
        shoot(1280, 720, "fs-preview-right") { SettingsPreviewFrame(pageKey = "t", sticky = false) { FullscreenPlayerPreview(Modifier) } }
        prefs.setBoolean(FullscreenKeys.LyricsByDefault, false)
        shoot(1280, 720, "fs-preview-nolyrics") { SettingsPreviewFrame(pageKey = "t", sticky = false) { FullscreenPlayerPreview(Modifier) } }
    }

    @Test
    fun fullscreenPreviewUpdatesWithoutRecreating() = withPrefs { prefs ->
        shoot(1280, 720, "fs-preview-live", afterFirst = {
            prefs.setString(FullscreenKeys.CoverSide, "RIGHT")
            prefs.setString(FullscreenKeys.CoverShape, "SQUARE")
        }) { SettingsPreviewFrame(pageKey = "t", sticky = false) { FullscreenPlayerPreview(Modifier) } }
    }

    @Test
    fun fullscreenPageRenders() = withPrefs {
        shoot(900, 2000, "fs-page") { FullscreenPlayerSettingsScreen(rememberNavController()) }
    }

    @Test
    fun lyricsPageRenders() = withPrefs {
        shoot(900, 3200, "lyrics-page") { LyricsSettingsScreen(rememberNavController()) }
    }
}
