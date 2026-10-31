@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.music.vivi.compose.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.navigation.compose.rememberNavController
import com.music.vivi.compose.LocalPlaybackController
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.FullscreenKeys
import com.music.vivi.compose.prefs.MiniPlayerStyle
import com.music.vivi.compose.prefs.PlayerBackgroundStyle
import com.music.vivi.compose.prefs.PrefKeys
import com.music.vivi.compose.ui.player.FullscreenPlayerPreview
import com.music.vivi.compose.ui.player.lyrics.lyricsConfig
import com.music.vivi.compose.ui.player.lyrics.saveLyricsConfig
import com.music.vivi.compose.ui.settings.preview.AppPreviewSizes
import com.music.vivi.compose.ui.settings.preview.FakePlaybackController
import com.music.vivi.compose.ui.settings.preview.MiniPlayerPreview
import com.music.vivi.compose.ui.settings.preview.NowPlayingPreview
import com.music.vivi.compose.ui.settings.preview.PageBackgroundPreview
import com.music.vivi.compose.ui.settings.preview.SettingsPreviewFrame
import com.music.vivi.compose.ui.settings.preview.TopBarPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import androidx.compose.ui.unit.dp

/**
 * Renders settings pages and their live previews offscreen with a [FakePlaybackController] (no real player, no network).
 * Set SETTINGS_SHOT_DIR to keep the PNGs. The "flip a pref" tests are the real-time rule: they change a pref while the scene is
 * running and assert the pixels change without recreating anything.
 */
class SettingsPagesRenderTest {
    private class Rig(val prefs: DesktopPrefs, val scene: ImageComposeScene) {
        var t = 0L
        fun settle(frames: Int = 12): org.jetbrains.skia.Image {
            var last: org.jetbrains.skia.Image? = null
            repeat(frames) { last?.close(); last = scene.render(t); t += 50_000_000 }
            return last!!
        }
    }

    private fun withRig(
        width: Int, height: Int, density: Float = 1f,
        setup: DesktopPrefs.() -> Unit = {},
        content: @Composable () -> Unit,
        block: Rig.() -> Unit,
    ) {
        val dir = File(System.getProperty("java.io.tmpdir"), "settings-pages-${System.nanoTime()}").apply { mkdirs() }
        val prefs = DesktopPrefs(File(dir, "s.properties"))
        prefs.setup()
        val dbDir = File(dir, "db").apply { mkdirs() }
        val driver = com.music.vivi.database.DatabaseFactory.createDriver(File(dbDir, "library.db"))
        val session = object : com.music.vivi.core.session.SessionManager {
            override fun isLoggedIn() = false
            override fun loadSession() = null
            override fun loadAccount() = null
            override fun applyStoredSession() = false
            override fun signOut() = Unit
        }
        startKoin {
            modules(module {
                single { prefs }
                single { com.music.vivi.database.LibraryRepository(com.music.vivi.database.VisageDatabase(driver)) }
                single { com.music.vivi.core.youtube.YouTubeMusicService(session) }
            })
        }
        try {
            runBlocking(Dispatchers.Swing) {
                val controller = FakePlaybackController()
                val scene = ImageComposeScene(width = width, height = height, density = Density(density), coroutineContext = coroutineContext) {
                    CompositionLocalProvider(LocalPlaybackController provides controller) {
                        MaterialTheme(colorScheme = darkColorScheme()) { Surface(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize()) { content() } } }
                    }
                }
                try { Rig(prefs, scene).block() } finally { scene.close() }
            }
        } finally { stopKoin(); driver.close(); dir.deleteRecursively() }
    }

    private fun org.jetbrains.skia.Image.png(): ByteArray = encodeToData()!!.bytes

    private fun save(name: String, bytes: ByteArray) {
        assertTrue(bytes.size > 3_000, "$name rendered an almost empty image")
        System.getenv("SETTINGS_SHOT_DIR")?.let { File(it, "$name.png").apply { parentFile.mkdirs() }.writeBytes(bytes) }
    }

    /** Raw pixels of a rectangle, for comparing only the part of a page that holds the preview. */
    private fun org.jetbrains.skia.Image.pixels(x: Int, y: Int, w: Int, h: Int): ByteArray {
        val bmp = Bitmap.makeFromImage(this)
        return bmp.readPixels(ImageInfo.makeN32(w, h, ColorAlphaType.PREMUL), w * 4, x, y)!!
    }

    private fun page(name: String, width: Int = 900, height: Int = 1500, setup: DesktopPrefs.() -> Unit = {}, content: @Composable () -> Unit) =
        withRig(width, height, setup = setup, content = content) { save(name, settle().use { it.png() }) }

    private inline fun <T> org.jetbrains.skia.Image.use(f: (org.jetbrains.skia.Image) -> T): T = try { f(this) } finally { close() }

    // ---- pages -----------------------------------------------------------------------------------------------------

    @Test fun interfacePage() = page("page-interface", height = 2400) { AppearanceSettingsScreen(rememberNavController()) }
    @Test fun nowPlayingPage() = page("page-nowplaying", height = 2400) { NowPlayingSettingsScreen(rememberNavController()) }
    @Test fun fullscreenPage() = page("page-fullscreen", height = 2000) { FullscreenPlayerSettingsScreen(rememberNavController()) }
    @Test fun lyricsPage() = page("page-lyrics", height = 3200) { LyricsSettingsScreen(rememberNavController()) }
    @Test fun songSwapPage() = page("page-songswap", height = 1400) { SongSwapSettingsScreen(rememberNavController()) }
    @Test fun sidebarsPage() = page("page-sidebars", height = 2000) { SidebarsSettingsScreen(rememberNavController()) }
    @Test fun playbackPage() = page("page-playback") { PlayerSettingsScreen(rememberNavController()) }
    @Test fun downloadsPage() = page("page-downloads") { DownloadsSettingsScreen(rememberNavController()) }
    @Test fun accountPage() = page("page-account") { AccountSettingsScreen(rememberNavController()) }
    @Test fun diagnosticsPage() = page("page-diagnostics") { DiagnosticsScreen(rememberNavController()) }

    @Test fun layoutVariantsOfTheMiniPlayerAndNowPlaying() {
        for (layout in MiniPlayerStyle.entries) {
            page("preview-mini-${layout.name.lowercase()}", 800, 420, setup = {
                setString(PrefKeys.MiniPlayerStyle, layout.name)
            }) {
                SettingsPreviewFrame(pageKey = "t", sticky = false, virtualWidth = AppPreviewSizes.MiniPlayerWidth.dp, virtualHeight = AppPreviewSizes.MiniPlayerHeight.dp) { MiniPlayerPreview() }
            }
        }
        page("preview-nowplaying-framed", 800, 560, setup = {
            setBoolean(PrefKeys.UsePlayerV2, true); setBoolean(PrefKeys.SquareCover, true); setString(PrefKeys.PlayerButtonsStyle, "PRIMARY")
            setBoolean(PrefKeys.LimitPlayerControlsWidth, true); setInt(PrefKeys.PlayerControlsWidth, 420)
        }) { SettingsPreviewFrame(pageKey = "t") { NowPlayingPreview() } }
    }

    // ---- real-time rule: flip a pref, the preview changes -----------------------------------------------------------

    @Test fun miniPlayerBackgroundFollowsPrefs() = withRig(
        800, 420, setup = { setString(PrefKeys.MiniPlayerBackgroundStyle, PlayerBackgroundStyle.DEFAULT.name) },
        content = {
            SettingsPreviewFrame(pageKey = "t", sticky = false, virtualWidth = AppPreviewSizes.MiniPlayerWidth.dp, virtualHeight = AppPreviewSizes.MiniPlayerHeight.dp) { MiniPlayerPreview() }
        },
    ) {
        val before = settle().use { it.pixels(0, 0, 800, 420) }
        prefs.setString(PrefKeys.MiniPlayerBackgroundStyle, PlayerBackgroundStyle.GLOW_ANIMATED.name)
        val after = settle().use { it.pixels(0, 0, 800, 420) }
        assertFalse(before.contentEquals(after), "mini-player preview did not change after the background pref changed")
    }

    @Test fun pageBackgroundFollowsPrefs() = withRig(
        800, 420, setup = { setString(PrefKeys.PageBackgroundStyle, PlayerBackgroundStyle.DEFAULT.name) },
        content = {
            SettingsPreviewFrame(pageKey = "t", sticky = false, virtualWidth = AppPreviewSizes.PageBackgroundWidth.dp, virtualHeight = AppPreviewSizes.PageBackgroundHeight.dp) { PageBackgroundPreview() }
        },
    ) {
        val before = settle().use { it.pixels(0, 0, 800, 420) }
        prefs.setString(PrefKeys.PageBackgroundStyle, PlayerBackgroundStyle.GLOW_ANIMATED.name)
        val after = settle().use { it.pixels(0, 0, 800, 420) }
        assertFalse(before.contentEquals(after), "page background preview did not change after the style pref changed")
    }

    @Test fun nowPlayingPreviewFollowsPrefs() = withRig(
        800, 560, content = { SettingsPreviewFrame(pageKey = "t") { NowPlayingPreview() } },
    ) {
        val a = settle().use { it.pixels(0, 0, 800, 560) }
        prefs.setBoolean(PrefKeys.HidePlayerThumbnail, true)
        val b = settle().use { it.pixels(0, 0, 800, 560) }
        assertFalse(a.contentEquals(b), "Now Playing preview ignored 'Hide cover art'")
        prefs.setBoolean(PrefKeys.UsePlayerV2, true)
        val c = settle().use { it.pixels(0, 0, 800, 560) }
        assertFalse(b.contentEquals(c), "Now Playing preview ignored 'Framed controls'")
    }

    @Test fun topBarPreviewFollowsPrefs() = withRig(
        800, 200, content = {
            SettingsPreviewFrame(pageKey = "t", sticky = false, virtualWidth = AppPreviewSizes.TopBarWidth.dp, virtualHeight = AppPreviewSizes.TopBarHeight.dp) { TopBarPreview() }
        },
    ) {
        val a = settle().use { it.pixels(0, 0, 800, 200) }
        prefs.setString(PrefKeys.TopNavStyle, "TEXT_ONLY")
        val b = settle().use { it.pixels(0, 0, 800, 200) }
        assertFalse(a.contentEquals(b), "top bar preview ignored the tab-label style")
    }

    @Test fun fullscreenCoverSideFollowsPrefs() = withRig(
        800, 560, setup = { setString(FullscreenKeys.CoverSide, "LEFT"); setBoolean(FullscreenKeys.LyricsByDefault, false) },
        content = { SettingsPreviewFrame(pageKey = "t") { FullscreenPlayerPreview(Modifier) } },
    ) {
        val a = settle().use { it.pixels(0, 0, 800, 560) }
        prefs.setString(FullscreenKeys.CoverSide, "RIGHT")
        val b = settle().use { it.pixels(0, 0, 800, 560) }
        assertFalse(a.contentEquals(b), "fullscreen preview did not move the cover when the side pref changed")
    }

    @Test fun lyricsFontSizeFollowsPrefs() = withRig(
        900, 1500, content = { LyricsSettingsScreen(rememberNavController()) },
    ) {
        // Only the sticky preview area at the top of the page (the sliders and their readouts are further down).
        fun region(img: org.jetbrains.skia.Image) = img.pixels(24, 110, 852, 260)
        val a = settle().use { region(it) }
        val cfg = prefs.lyricsConfig()
        prefs.saveLyricsConfig(cfg.copy(fontSizeSp = if (cfg.fontSizeSp > 40f) 20f else 52f))
        val b = settle().use { region(it) }
        assertFalse(a.contentEquals(b), "lyrics preview did not change after the font size pref changed")
    }

    @Test fun collapsedPreviewComposesNothing() = withRig(
        800, 560, setup = { setBoolean("settingsPreview.t.collapsed", true) },
        content = { SettingsPreviewFrame(pageKey = "t") { NowPlayingPreview() } },
    ) {
        val img = settle()
        assertNotNull(img)
        // Collapsed: the frame is just its header, so the lower half of the scene is the plain background.
        val lower = img.use { it.pixels(0, 200, 800, 300) }
        val distinct = lower.toList().windowed(4, 4).distinct().size
        assertTrue(distinct <= 2, "collapsed preview still painted $distinct distinct pixels below the header")
    }
}
