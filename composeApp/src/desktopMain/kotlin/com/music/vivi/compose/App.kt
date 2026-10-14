package com.music.vivi.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.unit.dp
import com.music.vivi.compose.navigation.AppScaffold
import com.music.vivi.compose.local.LocalLibraryBootstrap
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.PrefKeys
import com.music.vivi.compose.theme.ViviTheme
import com.music.vivi.compose.theme.rememberArtworkPalette
import com.music.vivi.database.LocalMusicRepository
import com.music.vivi.database.PlayHistoryRepository
import com.music.vivi.player.PlaybackController
import com.music.vivi.player.PlaybackState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import org.koin.compose.koinInject

@Composable
fun FrameWindowScope.App(
    windowState: WindowState,
    onClose: () -> Unit,
) {
    val controller = koinInject<PlaybackController>()
    val prefs = koinInject<DesktopPrefs>()
    val history = koinInject<PlayHistoryRepository>()
    val localMusic = koinInject<LocalMusicRepository>()
    val queuePersistence = koinInject<com.music.vivi.compose.local.QueuePersistence>()
    val localBootstrap = koinInject<LocalLibraryBootstrap>()
    val nowPlaying by controller.nowPlaying.collectAsState()
    val state by controller.state.collectAsState()
    val artworkPalette = rememberArtworkPalette(nowPlaying?.artworkUrl)

    // Developer aid: VIVI_DEBUG_CRASH=1 throws inside the UI after a few seconds to exercise the error dialog.
    if (System.getenv("VIVI_DEBUG_CRASH") == "1") {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(7_000)
            error("Debug crash (VIVI_DEBUG_CRASH=1)")
        }
    }

    LaunchedEffect(Unit) {
        queuePersistence.start()
        localBootstrap.maybeAutoScan()
    }

    LaunchedEffect(nowPlaying?.videoId) {
        val id = nowPlaying?.videoId ?: return@LaunchedEffect
        // Record once after audio is ready, never on optimistic loading or pause/resume.
        controller.state.first { it == PlaybackState.Playing }
        val track = controller.nowPlaying.value?.takeIf { it.videoId == id } ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching {
                history.recordPlay(
                    songId = track.videoId,
                    title = track.title,
                    artists = track.artist,
                    thumbnail = track.artworkUrl,
                    durationSec = controller.durationMs.value.takeIf { it > 0 }?.div(1000),
                )
                if (id.startsWith("local:")) localMusic.recordPlay(id.removePrefix("local:"))
            }.onFailure { System.err.println("Could not record play: ${it.message}") }
        }
    }

    CompositionLocalProvider(
        LocalPlaybackController provides controller,
        LocalWindowState provides windowState,
    ) {
        LaunchedEffect(Unit) {
            controller.setVolume(prefs.getFloat(PrefKeys.PlaybackVolume, 1f).coerceIn(0f, 1f))
        }
        ViviTheme(prefs = prefs, artworkPalette = artworkPalette) {
            // Clip to rounded bounds so glow/blur backgrounds can't bleed past the
            // compositor's window corner (the bottom-left "overflow" spit).
            // Skip rounding while OS-fullscreen so edges aren't inset.
            val osFullscreen = windowState.placement == WindowPlacement.Fullscreen
            val windowShape = RoundedCornerShape(16.dp)
            Surface(
                modifier = if (osFullscreen) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.fillMaxSize().clip(windowShape)
                },
                shape = if (osFullscreen) RectangleShape else windowShape,
                color = MaterialTheme.colorScheme.background,
            ) {
                AppScaffold(
                    windowState = windowState,
                    onClose = onClose,
                )
                com.music.vivi.compose.crash.CrashDialogHost(onQuit = onClose)
            }
        }
    }
}
