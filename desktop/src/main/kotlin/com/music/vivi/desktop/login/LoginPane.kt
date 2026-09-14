package com.music.vivi.desktop.login

import com.music.innertube.auth.YtMusicSession
import com.music.vivi.player.session.SessionSecretStore
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ProgressIndicator
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.javafx.JavaFx
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

private const val LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"
private const val MUSIC_HOST_PREFIX = "https://music.youtube.com"

/**
 * Control pane for YT Music login. Google blocks JavaFX WebKit, so sign-in
 * happens in a real Chromium app window; we scrape cookies/JS via CDP.
 */
class LoginPane(
    private val onLoggedIn: (accountName: String) -> Unit,
) : BorderPane() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.JavaFx)
    private var pollJob: Job? = null
    private var browserProcess: Process? = null
    private var cdp: CdpClient? = null
    private var debugPort: Int = -1

    private val statusLabel = Label("Opening Chromium for Google sign-in…")
    private val detailLabel = Label(
        "Google blocks embedded WebViews. A real Chromium window will open; " +
            "sign in there, wait until YouTube Music loads, then confirm here.",
    ).apply {
        isWrapText = true
        maxWidth = 520.0
    }
    private val openButton = Button("Open sign-in window")
    private val confirmButton = Button("Confirm login").apply {
        isDisable = true
        isDefaultButton = true
    }
    private val progress = ProgressIndicator().apply {
        prefWidth = 18.0
        prefHeight = 18.0
        isVisible = false
    }

    private var readyToConfirm = false
    private var isConfirming = false

    init {
        openButton.setOnAction { launchBrowser() }
        confirmButton.setOnAction { confirmLogin() }

        val topBar = HBox(12.0).apply {
            alignment = Pos.CENTER_LEFT
            padding = Insets(10.0, 12.0, 10.0, 12.0)
            children += Label("YouTube Music login").apply {
                style = "-fx-font-size: 16px; -fx-font-weight: bold;"
            }
            val spacer = HBox().also { HBox.setHgrow(it, Priority.ALWAYS) }
            children += spacer
            children += progress
            children += openButton
            children += confirmButton
        }

        val body = VBox(10.0, statusLabel, detailLabel).apply {
            padding = Insets(16.0, 16.0, 16.0, 16.0)
            alignment = Pos.TOP_LEFT
        }

        top = topBar
        center = body

        // Auto-launch once the scene is up.
        Platform.runLater { launchBrowser() }
    }

    private fun launchBrowser() {
        val browser = ChromiumLauncher.findBrowser()
        if (browser == null) {
            statusLabel.text = "No Chromium/Chrome found. Install chromium and retry."
            return
        }

        runCatching { browserProcess?.destroy() }
        runCatching { cdp?.close() }
        cdp = null
        pollJob?.cancel()

        debugPort = ChromiumLauncher.pickFreePort()
        val profile = ChromiumLauncher.profileDir()
        statusLabel.text = "Launching $browser (CDP :$debugPort)…"
        browserProcess = ChromiumLauncher.launch(browser, LOGIN_URL, debugPort, profile)
        openButton.text = "Reopen sign-in window"
        confirmButton.isDisable = true
        readyToConfirm = false

        scope.launch {
            val client = CdpClient(debugPort)
            try {
                withContext(Dispatchers.IO) {
                    client.waitUntilReady()
                    client.reconnectLoop()
                }
                cdp = client
                statusLabel.text = "Sign in inside the Chromium window…"
                startReadyPoll(client)
            } catch (t: Throwable) {
                client.close()
                statusLabel.text = "Could not attach to Chromium: ${t.message}"
            }
        }
    }

    private fun startReadyPoll(client: CdpClient) {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive && !isConfirming) {
                try {
                    val url = withContext(Dispatchers.IO) { client.currentUrl() }
                    if (url.startsWith(MUSIC_HOST_PREFIX)) {
                        if (!readyToConfirm) {
                            readyToConfirm = true
                            confirmButton.isDisable = false
                            statusLabel.text =
                                "YouTube Music detected — press Confirm login to save to your keyring."
                        }
                    } else {
                        statusLabel.text = "Browsing: ${url.take(80)}…"
                    }
                } catch (_: Throwable) {
                    // Tab may navigate / WS drop — try reconnect
                    runCatching {
                        withContext(Dispatchers.IO) { client.reconnectLoop(3) }
                    }
                }
                delay(1.seconds)
            }
        }
    }

    private fun confirmLogin() {
        if (!readyToConfirm || isConfirming) return
        val client = cdp ?: run {
            failConfirm("Not connected to Chromium.")
            return
        }

        isConfirming = true
        confirmButton.isDisable = true
        progress.isVisible = true
        statusLabel.text = "Reading cookies and validating…"

        scope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) {
                    client.reconnectLoop()
                    val cookie = client.cookieHeader()
                    val visitor = client.evaluateString(
                        "(function(){try{return (window.yt&&window.yt.config_&&window.yt.config_.VISITOR_DATA)||" +
                            "(window.ytcfg&&window.ytcfg.get&&window.ytcfg.get('VISITOR_DATA'))||'';}catch(e){return '';}})()",
                    )
                    val dataSyncRaw = client.evaluateString(
                        "(function(){try{" +
                            "var a=(window.yt&&window.yt.config_&&window.yt.config_.DATASYNC_ID)||'';" +
                            "var b=(window.ytcfg&&window.ytcfg.get&&window.ytcfg.get('DELEGATED_SESSION_ID'))||'';" +
                            "var c=(window.ytcfg&&window.ytcfg.get&&window.ytcfg.get('DATASYNC_ID'))||'';" +
                            "return b||a||c||'';}catch(e){return '';}})()",
                    )
                    Triple(cookie, visitor, normalizeDataSyncId(dataSyncRaw))
                }

                val (cookie, visitorData, dataSyncId) = snapshot
                if (cookie.isBlank() || !cookie.contains("SAPISID")) {
                    failConfirm("Cookies incomplete (missing SAPISID). Finish sign-in on YouTube Music, then retry.")
                    return@launch
                }
                if (visitorData.isBlank()) {
                    failConfirm("Missing VISITOR_DATA — wait for YouTube Music to finish loading, then retry.")
                    return@launch
                }

                val session = YtMusicSession(
                    cookie = cookie,
                    visitorData = visitorData,
                    dataSyncId = dataSyncId,
                )

                val result = withTimeoutOrNull(20.seconds) {
                    withContext(Dispatchers.IO) {
                        var attempt = session.validate()
                        if (attempt.isFailure) {
                            delay(750)
                            attempt = session.validate()
                        }
                        attempt
                    }
                }

                if (result == null) {
                    failConfirm("Login timed out.")
                    return@launch
                }

                result.onSuccess { account ->
                    withContext(Dispatchers.IO) {
                        SessionSecretStore.save(session, account)
                    }
                    progress.isVisible = false
                    statusLabel.text = "Signed in as ${account.name}. Saved to system keyring."
                    confirmButton.text = "Done"
                    confirmButton.isDisable = true
                    openButton.isDisable = true
                    pollJob?.cancel()
                    runCatching { browserProcess?.destroy() }
                    onLoggedIn(account.name)
                }.onFailure { error ->
                    failConfirm("Login failed: ${error.message ?: error::class.simpleName}")
                }
            } catch (t: Throwable) {
                failConfirm("Confirm failed: ${t.message}")
            }
        }
    }

    private fun failConfirm(message: String) {
        Platform.runLater {
            isConfirming = false
            progress.isVisible = false
            confirmButton.isDisable = !readyToConfirm
            statusLabel.text = message
        }
    }
}
