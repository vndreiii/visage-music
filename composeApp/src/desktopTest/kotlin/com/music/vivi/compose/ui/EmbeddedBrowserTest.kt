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
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmbeddedBrowserTest {
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
            }
        } finally {
            withContext(Dispatchers.Swing) { browser.close(); window?.dispose() }
            server.stop(0)
        }
    }
}
