package com.music.vivi.compose.ui

import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import com.music.vivi.player.login.CdpClient
import com.music.vivi.player.login.reconnectLoop
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URL
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmbeddedBrowserTest {
    /** Regression guard: without the X11 Ozone switch, Chromium opens a separate Wayland window under Wayland. */
    @Test
    fun linuxEmbeddedBrowserForcesX11OzoneBackend() {
        val linux = embeddedBrowserChromiumSwitches("Linux")
        assertTrue("--ozone-platform=x11" in linux, "Missing X11 Ozone switch: $linux")
        assertTrue(embeddedBrowserChromiumSwitches("Windows 11").isEmpty())
    }

    @Test
    fun embeddedChromiumRendersAndCapturesHttpOnlyCookies(): Unit = runBlocking {
        assumeTrue(System.getenv("VISAGE_TEST_EMBEDDED_BROWSER") == "1")
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            exchange.responseHeaders.add("Content-Type", "text/html")
            exchange.responseHeaders.add("Set-Cookie", "EmbeddedProbe=fixture; HttpOnly; Path=/")
            val html = "<html><title>Embedded browser probe</title><body style='background:#123456;color:white;font:32px sans-serif;padding:40px'>Chromium inside VIsage<input placeholder='Keyboard test'></body></html>".toByteArray()
            exchange.sendResponseHeaders(200, html.size.toLong())
            exchange.responseBody.use { it.write(html) }
        }
        server.start()
        val browser = EmbeddedLoginBrowser()
        var window: ComposeWindow? = null
        try {
            val url = "http://127.0.0.1:${server.address.port}/"
            browser.open(url)
            withContext(Dispatchers.Swing) {
                window = ComposeWindow().apply {
                    title = "VIsage embedded browser test"
                    setSize(900, 650)
                    setContent { SwingPanel(factory = { browser.panel.value!! }, modifier = Modifier.fillMaxSize()) }
                    isVisible = true
                }
            }
            CdpClient(browser.debugPort).use { client ->
                client.reconnectLoop()
                withTimeout(10_000) { while (client.currentUrl() != url) delay(100) }
                assertEquals(url, client.currentUrl())
                assertEquals("Embedded browser probe", client.evaluateString("document.title"))
                assertTrue(client.cookieHeader(listOf(url)).contains("EmbeddedProbe=fixture"))
                assertEquals("", client.evaluateString("document.cookie"))
                assertTrue(withContext(Dispatchers.Swing) { browser.panel.value!!.isShowing })
                // Let any stray popup/DevTools/about:blank window finish appearing before checking.
                delay(2_000)
                assertSingleBrowserWindow(browser.debugPort)
                assertNoStrayX11Windows()
                System.getenv("VISAGE_TEST_HOLD_MS")?.toLongOrNull()?.takeIf { it > 0 }?.let { delay(it) }
            }
        } finally {
            withContext(Dispatchers.Swing) { browser.close(); window?.dispose() }
            server.stop(0)
        }
    }

    /**
     * Every CEF browser (popups, DevTools, about:blank, windowed Chromium toplevels on Wayland)
     * is a CDP "page" target. Exactly one page target means the embedded browser is the only window.
     */
    private fun assertSingleBrowserWindow(debugPort: Int) {
        val conn = URL("http://127.0.0.1:$debugPort/json/list").openConnection() as HttpURLConnection
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val pages = Regex("\"type\"\\s*:\\s*\"page\"").findAll(body).count()
        val urls = Regex("\"url\"\\s*:\\s*\"([^\"]*)\"").findAll(body).map { it.groupValues[1] }.toList()
        assertEquals(1, pages, "Expected one browser window, found page targets: $urls")
    }

    /**
     * On X11 (and XWayland) a stray Chromium toplevel would appear in the X window tree under its
     * page title. Skipped when xwininfo or DISPLAY is unavailable.
     */
    private fun assertNoStrayX11Windows() {
        if (System.getenv("DISPLAY").isNullOrBlank()) return
        val tree = runCatching {
            val p = ProcessBuilder("xwininfo", "-root", "-tree").redirectErrorStream(true).start()
            p.inputStream.bufferedReader().use { it.readText() }.also { p.waitFor() }
        }.getOrNull() ?: return
        assertTrue(tree.contains("\"VIsage embedded browser test\""), "Test window missing from X tree")
        assertFalse(tree.contains("\"Embedded browser probe\""), "Stray Chromium toplevel window present")
    }
}
