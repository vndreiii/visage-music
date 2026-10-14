@file:JvmName("alex")
package com.music.vivi

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.LocalWindowExceptionHandlerFactory
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.music.vivi.compose.App
import com.music.vivi.compose.crash.AppLog
import com.music.vivi.compose.crash.CrashReporter
import com.music.vivi.compose.crash.showFatalCrashWindow
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

/**
 * App entry — window class (WM_CLASS / Hyprland class) is "visage"; the JVM main class stays com.music.vivi.alex.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Prefer this as Wayland app_id / X11 WM_CLASS where the toolkit honors it
    System.setProperty("awt.appClassName", "visage")
    // The JDK ignores that property on X11 and derives WM_CLASS from the main class; set the toolkit's
    // field directly (needs --add-opens java.desktop/sun.awt.X11, already in the run args).
    runCatching {
        val toolkit = java.awt.Toolkit.getDefaultToolkit()
        if (toolkit.javaClass.name == "sun.awt.X11.XToolkit") {
            toolkit.javaClass.getDeclaredField("awtAppClassName").apply { isAccessible = true }.set(toolkit, "visage")
        }
    }
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
    AppLog.install()
    CrashReporter.install()
    if (com.music.vivi.nativelib.AppEnv.get("DEBUG_CRASH_WINDOW") == "1") {
        // Developer aid: open only the standalone error window with a sample report.
        CrashReporter.record("main", IllegalStateException("Sample error (VIVI_DEBUG_CRASH_WINDOW=1)"), fatal = true)
        CrashReporter.current.value?.let { showFatalCrashWindow(it) }
        return
    }
    try {
        runApp()
    } catch (t: Throwable) {
        // The Compose loop itself died: show a standalone Material 3 error window instead of Java's output.
        CrashReporter.record(Thread.currentThread().name, t, fatal = true)
        CrashReporter.current.value?.let { showFatalCrashWindow(it) }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun runApp() {
    application {
        val windowIcon = remember {
            runCatching {
                BitmapPainter(useResource("visage_icon.png") { loadImageBitmap(it) })
            }.getOrNull()
        }
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
        androidx.compose.runtime.LaunchedEffect(Unit) {
            // After a composition error the window can't update; release audio, close it and
            // explain in a standalone Material 3 window instead.
            CrashReporter.onUiDied = {
                println("crash: UI died, opening the error window")
                // Stop audio without going through Compose or Koin teardown (both race with the broken UI).
                Thread({
                    runCatching { getKoin().get<com.music.vivi.compose.local.QueuePersistence>().close() }
                    runCatching { getKoin().get<PlaybackController>().release() }
                }, "crash-cleanup").apply { isDaemon = true }.start()
                CrashReporter.current.value?.let { showFatalCrashWindow(it) }
            }
        }
        // The factory is read when the Window is created, so it must wrap the Window call itself
        // (providing it inside the content never took effect and Java's own error dialog appeared).
        CompositionLocalProvider(
            LocalWindowExceptionHandlerFactory provides CrashReporter.windowExceptionHandlerFactory,
        ) {
            Window(
                onCloseRequest = closeApp,
                state = windowState,
                undecorated = true,
                title = "VIsage",
                icon = windowIcon,
            ) {
                App(
                    windowState = windowState,
                    onClose = closeApp,
                )
            }
        }
    }
}
