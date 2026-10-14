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

/** One process-wide CEF runtime, with a browser component embedded in the Account page. */
private object LoginCefRuntime {
    val debugPort = ServerSocket(0).use { it.localPort }
    val app: CefApp by lazy {
        val builder = CefAppBuilder()
        // Use the active Secret Service (GNOME Keyring or compatible KDE provider),
        // rather than Chromium guessing a wallet from desktop environment variables.
        if (System.getProperty("os.name").startsWith("Linux", ignoreCase = true)) {
            builder.addJcefArgs("--password-store=gnome-libsecret")
        }
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
