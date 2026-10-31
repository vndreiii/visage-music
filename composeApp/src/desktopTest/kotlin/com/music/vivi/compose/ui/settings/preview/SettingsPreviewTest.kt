@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.music.vivi.compose.ui.settings.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.SidebarKeys
import com.music.vivi.player.PlaybackState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FakePlaybackControllerTest {
    @Test
    fun positionAdvancesWhilePlayingAndHoldsWhilePaused() {
        val c = FakePlaybackController(startPositionMs = 1_000L)
        c.advance(500)
        assertEquals(1_500L, c.positionMs.value)
        c.pause()
        c.advance(500)
        assertEquals(1_500L, c.positionMs.value)
        assertEquals(PlaybackState.Paused, c.state.value)
    }

    @Test
    fun endOfTrackStartsNextAndWraps() {
        val c = FakePlaybackController(startIndex = PreviewSampleData.queue.lastIndex, startPositionMs = 0L)
        c.advance(c.durationMs.value)
        assertEquals(0, c.queueIndex.value)
        assertEquals(PreviewSampleData.queue.first().videoId, c.nowPlaying.value?.videoId)
    }

    @Test
    fun queueOperations() {
        val c = FakePlaybackController()
        val extra = PreviewSampleData.queue.last().copy(videoId = "preview:extra", title = "Extra")
        c.addToQueue(extra)
        assertEquals(PreviewSampleData.queue.size + 1, c.queueTracks.value.size)
        c.playNext(extra.copy(videoId = "preview:next"))
        assertEquals("preview:next", c.queueTracks.value[c.queueIndex.value + 1].videoId)
        val before = c.queueTracks.value.map { it.videoId }
        c.moveQueueItem(3, 1)
        assertEquals(before[3], c.queueTracks.value[1].videoId)
        assertEquals(c.queueTracks.value.map { it.videoId }, c.queueIds.value)
        c.removeFromQueue(c.queueIndex.value) // the current song cannot be removed
        assertEquals(before.size, c.queueTracks.value.size)
        c.removeFromQueue(1)
        assertEquals(before.size - 1, c.queueTracks.value.size)
        assertNotEquals(null, c.nowPlaying.value)
    }
}

class PreviewScaleTest {
    @Test
    fun scaleFitsWidthAndNeverEnlarges() {
        assertEquals(0.5f, previewScale(640f, 1280f))
        assertEquals(1f, previewScale(2000f, 1280f))
        assertEquals(0.05f, previewScale(1f, 1280f))
        assertEquals(1f, previewScale(0f, 1280f))
    }

    @Test
    fun scaledHeightKeepsAspect() {
        val s = previewScale(640f, 1280f)
        assertEquals(360f, previewScaledHeight(720f, s))
    }

    @Test
    fun stickyOffsetOnlyAfterScrollingPastTheFrame() {
        assertEquals(0, stickyOffset(scrollValue = 40, naturalTopInContent = 100))
        assertEquals(60, stickyOffset(scrollValue = 160, naturalTopInContent = 100))
        assertEquals(68, stickyOffset(scrollValue = 160, naturalTopInContent = 100, margin = 8))
    }
}

/** Renders the Sidebars preview offscreen in several states (PREVIEW_SHOT_DIR keeps the PNGs). */
class SidebarsPreviewRenderTest {
    private fun render(name: String, page: PreviewPage = PreviewPage.HOME, queue: Boolean = true, prefsSetup: DesktopPrefs.() -> Unit = {}) {
        val file = File.createTempFile("prefs", ".properties").also { it.delete(); it.deleteOnExit() }
        val prefs = DesktopPrefs(file)
        prefs.prefsSetup()
        startKoin { modules(module { single { prefs } }) }
        try {
            runBlocking(Dispatchers.Swing) {
                val scene = ImageComposeScene(width = 760, height = 520, density = Density(1.5f), coroutineContext = coroutineContext) {
                    MaterialTheme(colorScheme = darkColorScheme()) {
                        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                            SettingsPreviewFrame(pageKey = "test-$name") { SidebarsPreview(page, queue) }
                        }
                    }
                }
                try {
                    var t = 0L
                    var image = scene.render(t)
                    repeat(60) { t += 16_000_000; image = scene.render(t) }
                    val bytes = image.encodeToData()!!.bytes
                    assertTrue(bytes.size > 5_000)
                    System.getenv("PREVIEW_SHOT_DIR")?.let { File(it, "$name.png").apply { parentFile.mkdirs() }.writeBytes(bytes) }
                } finally {
                    scene.close()
                }
            }
        } finally {
            stopKoin()
        }
    }

    @Test fun defaults() = render("sidebars-default")

    @Test fun libraryLeftExpanded() = render("sidebars-left-expanded") {
        setString(SidebarKeys.LibrarySide, "LEFT"); setString(SidebarKeys.LibraryState, "EXPANDED")
    }

    @Test fun libraryRail() = render("sidebars-right-rail") {
        setString(SidebarKeys.LibrarySide, "RIGHT"); setString(SidebarKeys.LibraryState, "RAIL")
    }

    @Test fun libraryHidden() = render("sidebars-hidden") { setString(SidebarKeys.LibraryState, "HIDDEN") }

    @Test fun bothLeft() = render("sidebars-both-left") {
        setString(SidebarKeys.LibrarySide, "LEFT"); setString(SidebarKeys.LibraryState, "EXPANDED"); setString(SidebarKeys.QueueSide, "LEFT")
    }

    @Test fun homeOnlyOnOtherPage() = render("sidebars-homeonly-otherpage", page = PreviewPage.LIBRARY) {
        setString(SidebarKeys.LibraryPages, "HOME_ONLY")
    }
}
