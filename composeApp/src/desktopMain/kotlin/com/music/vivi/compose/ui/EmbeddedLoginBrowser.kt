package com.music.vivi.compose.ui

import com.music.vivi.nativelib.AppPaths
import com.music.vivi.player.login.LoginBrowserHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import me.friwi.jcefmaven.CefAppBuilder
import org.cef.CefApp
import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.browser.CefFrame
import java.awt.BorderLayout
import java.net.ServerSocket
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * Chromium switches for the embedded (windowed) browser.
 *
 * On Linux, Chromium picks the Ozone/Wayland backend whenever WAYLAND_DISPLAY is set. JCEF's windowed
 * browser is embedded by handing Chromium an X11 window to reparent into the Swing canvas; the Wayland
 * backend ignores that and creates its own top-level xdg_toplevel, which is the separate window the user
 * sees. Forcing the X11 backend (XWayland under a Wayland session) makes the browser a child of the canvas.
 */
internal fun embeddedBrowserChromiumSwitches(osName: String): List<String> {
    // Without these, a fresh browser profile opens Chromium's "first run" window (a blank terms-of-service
    // dialog in this build) before the sign-in page.
    val common = listOf("--no-first-run", "--no-default-browser-check")
    return if (osName.startsWith("Linux", ignoreCase = true)) {
        common + listOf(
            // Use the active Secret Service (GNOME Keyring or compatible KDE provider),
            // rather than Chromium guessing a wallet from desktop environment variables.
            "--password-store=gnome-libsecret",
            "--ozone-platform=x11",
        )
    } else {
        common
    }
}

/** One process-wide CEF runtime, with a browser component embedded in the Account page. */
private object LoginCefRuntime {
    val debugPort = ServerSocket(0).use { it.localPort }
    @Volatile var started = false
    private val clearMarker get() = AppPaths.cache().resolve("embedded-login-profile.clear")

    /** Forget the Google sign-in kept by the embedded browser, so "Sign in" starts from a blank page. */
    fun clearProfile() {
        if (started) {
            runCatching { org.cef.network.CefCookieManager.getGlobalManager().deleteCookies("", "") }
        }
        // The profile folder is in use while CEF runs, so also delete it at the next start.
        runCatching { clearMarker.writeText("1") }
    }

    val app: CefApp by lazy {
        if (clearMarker.exists()) {
            runCatching { AppPaths.cache().resolve("embedded-login-profile").deleteRecursively() }
            runCatching { clearMarker.delete() }
        }
        started = true
        val builder = CefAppBuilder()
        embeddedBrowserChromiumSwitches(System.getProperty("os.name")).forEach { builder.addJcefArgs(it) }
        builder.setInstallDir(AppPaths.cache().resolve("jcef-152.0.6"))
        builder.cefSettings.apply {
            windowless_rendering_enabled = false
            remote_debugging_port = debugPort
            cache_path = AppPaths.cache().resolve("embedded-login-profile").absolutePath
            persist_session_cookies = true
            log_file = AppPaths.cache().resolve("embedded-login.log").absolutePath
        }
        builder.build()
    }
}

/** Clears the embedded browser's Google sign-in (used by Sign out). */
fun clearEmbeddedLoginProfile() = LoginCefRuntime.clearProfile()

class EmbeddedLoginBrowser : LoginBrowserHost {
    override val debugPort: Int get() = LoginCefRuntime.debugPort
    private val _panel = MutableStateFlow<JPanel?>(null)
    val panel = _panel.asStateFlow()
    private var client: CefClient? = null
    private var browser: CefBrowser? = null
    @Volatile private var closed = false

    override suspend fun open(url: String) {
        closed = false
        val app = withContext(Dispatchers.IO) { LoginCefRuntime.app }
        withContext(Dispatchers.Swing) {
            if (closed) return@withContext
            browser?.let { it.loadURL(url); return@withContext }
            val newClient = app.createClient()
            newClient.addLifeSpanHandler(object : CefLifeSpanHandlerAdapter() {
                override fun onBeforePopup(browser: CefBrowser, frame: CefFrame, targetUrl: String, targetFrameName: String): Boolean {
                    browser.loadURL(targetUrl)
                    return true
                }
            })
            // CEF's browser component stays inside SwingPanel; no JFrame is created.
            val newBrowser = newClient.createBrowser(url, false, false)
            client = newClient
            browser = newBrowser
            _panel.value = JPanel(BorderLayout()).apply { add(newBrowser.uiComponent, BorderLayout.CENTER) }
        }

    }

    override fun close() {
        closed = true
        val dispose = Runnable {
            browser?.close(true)
            client?.dispose()
            browser = null
            client = null
            _panel.value = null
        }
        if (SwingUtilities.isEventDispatchThread()) dispose.run() else SwingUtilities.invokeLater(dispose)
    }
}
