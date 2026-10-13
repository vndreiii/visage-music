@file:JvmName("alex")
package com.music.vivi

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.LocalWindowExceptionHandlerFactory
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowExceptionHandlerFactory
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.music.vivi.compose.App
import com.music.vivi.compose.local.toQueueTrack
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.PrefKeys
import com.music.vivi.core.di.coreModule
import com.music.vivi.core.session.SessionManager
import com.music.vivi.database.LibraryRepository
import com.music.vivi.player.DefaultPlaybackController
import com.music.vivi.player.FavoriteStore
import com.music.vivi.player.PlaybackController
import com.music.vivi.player.PlaybackFadeSettings
import com.music.vivi.player.PlaybackFadeSettingsProvider
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.java.KoinJavaComponent.getKoin

val desktopModule = module {
    single { DesktopPrefs() }
    single { com.music.vivi.compose.local.QueuePersistence(get(), get()) }
    single { com.music.vivi.compose.download.TrackDownloadService(get(), get()) }
    single { com.music.vivi.compose.local.LocalLibraryBootstrap(get(), get()) }
    single<PlaybackFadeSettingsProvider> {
        val prefs = get<DesktopPrefs>()
        PlaybackFadeSettingsProvider {
            PlaybackFadeSettings(
                crossfadeEnabled = prefs.getBoolean(PrefKeys.CrossfadeEnabled, false),
                crossfadeSeconds = prefs.getInt(PrefKeys.CrossfadeSeconds, 3).coerceIn(1, 10),
                playPauseFadeEnabled = prefs.getBoolean(PrefKeys.PlayPauseFadeEnabled, true),
                playPauseFadeSeconds = prefs.getFloat(PrefKeys.PlayPauseFadeSeconds, 0.5f)
                    .coerceIn(0.2f, 3f),
            )
        }
    }
    single<PlaybackController> {
        get<SessionManager>().applyStoredSession()
        val library = get<LibraryRepository>()
        val fades = get<PlaybackFadeSettingsProvider>()
        DefaultPlaybackController.create(
            applyStoredSession = false,
            favorites = object : FavoriteStore {
                override fun isLiked(videoId: String) = library.isLiked(videoId)
                override fun setLiked(videoId: String, liked: Boolean) = library.setLiked(videoId, liked)
            },
            fadeSettings = { fades.current() },
            fileTrackResolver = { id ->
                when {
                    id.startsWith("local:") -> get<com.music.vivi.database.LocalMusicRepository>().trackById(id.removePrefix("local:"))?.toQueueTrack()
                    id.startsWith("offline:") -> get<com.music.vivi.database.DownloadedTrackRepository>().all().firstOrNull { it.video_id == id.removePrefix("offline:") }?.toQueueTrack()
                    else -> null
                }
            },
            streamSettings = {
                val prefs = get<DesktopPrefs>()
                com.music.vivi.player.stream.StreamSettings(
                    saavnEnabled = prefs.getBoolean(PrefKeys.SaavnEnabled, com.music.vivi.player.stream.SaavnStreamResolver.defaultEnabled()),
                    quality = runCatching {
                        com.music.vivi.player.stream.StreamQuality.valueOf(prefs.getString(PrefKeys.AudioQuality, "AUTO"))
                    }.getOrDefault(com.music.vivi.player.stream.StreamQuality.AUTO),
                )
            },
        )
    }
}

/** Log Compose/UI crashes instead of the native "Unknown error" OK dialog. */
@OptIn(ExperimentalComposeUiApi::class)
private val SilentWindowExceptionHandlerFactory = WindowExceptionHandlerFactory { _ ->
    WindowExceptionHandler { throwable ->
        System.err.println(
            "Compose window exception: ${throwable.javaClass.simpleName}: ${throwable.message}",
        )
        throwable.printStackTrace(System.err)
    }
}

private fun installSilentExceptionHandlers() {
    val logOnly = Thread.UncaughtExceptionHandler { t, e ->
        System.err.println("Uncaught on ${t.name}: ${e.javaClass.simpleName}: ${e.message}")
        e.printStackTrace(System.err)
    }
    Thread.setDefaultUncaughtExceptionHandler(logOnly)
    runCatching {
        System.setProperty("sun.awt.exception.handler", SilentAwtExceptionHandler::class.java.name)
    }
}

/** Loaded reflectively by the AWT toolkit when sun.awt.exception.handler is set. */
class SilentAwtExceptionHandler {
    @Suppress("unused")
    fun handle(t: Throwable) {
        System.err.println("AWT: ${t.javaClass.simpleName}: ${t.message}")
        t.printStackTrace(System.err)
    }
}

/**
 * App entry — JVM class name is [com.music.vivi.alex] (WM_CLASS / Hyprland class).
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Prefer this as Wayland app_id / X11 WM_CLASS where the toolkit honors it
    System.setProperty("awt.appClassName", "com.music.vivi.alex")
    // Before any Jaudiotagger class loads (local scan is very chatty otherwise).
    System.setProperty("org.jaudiotagger.level", "OFF")
    runCatching {
        val off = java.util.logging.Level.OFF
        listOf(
            "org.jaudiotagger",
            "org.jaudiotagger.audio",
            "org.jaudiotagger.audio.flac",
            "org.jaudiotagger.audio.wav",
            "org.jaudiotagger.audio.mp3",
            "org.jaudiotagger.tag",
        ).forEach { name ->
            java.util.logging.Logger.getLogger(name).apply {
                level = off
                useParentHandlers = false
            }
        }
    }
    installSilentExceptionHandlers()
    application {
        remember { startKoin { modules(coreModule, desktopModule) } }
        val rpcManager = remember { com.music.vivi.compose.ui.player.DiscordRpcManager(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)).apply { start() } }
        val windowState = rememberWindowState()
        val closeApp = remember {
            {
                runCatching { getKoin().get<com.music.vivi.compose.local.QueuePersistence>().close() }
                runCatching { getKoin().get<PlaybackController>().release() }
                stopKoin()
                exitApplication()
            }
        }
        Window(
            onCloseRequest = closeApp,
            state = windowState,
            undecorated = true,
            title = "VIVI Desktop",
        ) {
            CompositionLocalProvider(
                LocalWindowExceptionHandlerFactory provides SilentWindowExceptionHandlerFactory,
            ) {
                App(
                    windowState = windowState,
                    onClose = closeApp,
                )
            }
        }
    }
}
