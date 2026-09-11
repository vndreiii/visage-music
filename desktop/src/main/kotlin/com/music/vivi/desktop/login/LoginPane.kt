package com.music.vivi.desktop.login

import com.music.innertube.auth.YtMusicSession
import com.music.vivi.desktop.session.SessionSecretStore
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ProgressIndicator
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.web.WebView
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
 * Mirrors upstream [LoginScreen]: Google sign-in in a WebView, check button
 * after landing on YT Music, then cookie + visitor/dataSync capture.
 */
class LoginPane(
    private val onLoggedIn: (accountName: String) -> Unit,
) : BorderPane() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.JavaFx)
    private var pollJob: Job? = null

    private val webView = WebView()
    private val statusLabel = Label("Sign in with Google, then confirm once YouTube Music loads.")
    private val confirmButton = Button("Confirm login").apply {
        isDisable = true
        isDefaultButton = true
    }
    private val progress = ProgressIndicator().apply {
        prefWidth = 18.0
        prefHeight = 18.0
        isVisible = false
    }

    private var visitorData: String = ""
    private var dataSyncId: String = ""
    private var liveDelegatedSessionId: String = ""
    private var readyToConfirm = false
    private var isConfirming = false

    init {
        WebCookieStore.install()

        val engine = webView.engine
        // Prefer a desktop Chrome UA so Google is less likely to block the embed.
        engine.userAgent =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/122.0.0.0 Safari/537.36"

        engine.loadWorker.stateProperty().addListener { _, _, newState ->
            if (newState == Worker.State.SUCCEEDED) {
                onPageFinished(engine.location)
            }
        }

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
            children += confirmButton
        }

        val hint = VBox(4.0, statusLabel).apply {
            padding = Insets(0.0, 12.0, 8.0, 12.0)
        }

        top = VBox(topBar, hint)
        center = webView

        engine.load(LOGIN_URL)
    }

    private fun onPageFinished(url: String?) {
        val engine = webView.engine
        runCatching {
            engine.executeScript(
                "(function(){try{return window.yt&&window.yt.config_&&window.yt.config_.VISITOR_DATA||'';}catch(e){return '';}})()",
            )?.toString()?.takeIf { it.isNotBlank() && it != "null" }?.let { visitorData = it }
        }
        runCatching {
            engine.executeScript(
                "(function(){try{return window.yt&&window.yt.config_&&window.yt.config_.DATASYNC_ID||'';}catch(e){return '';}})()",
            )?.toString()?.takeIf { it.isNotBlank() && it != "null" }?.let {
                dataSyncId = normalizeDataSyncId(it)
            }
        }

        if (url?.startsWith(MUSIC_HOST_PREFIX) == true) {
            setReadyToConfirm(true)
        }
    }

    private fun setReadyToConfirm(ready: Boolean) {
        if (readyToConfirm == ready) return
        readyToConfirm = ready
        confirmButton.isDisable = !ready || isConfirming
        if (ready) {
            statusLabel.text =
                "YouTube Music loaded — use a brand account if prompted, then press Confirm login."
            startDelegatedSessionPoll()
        } else {
            pollJob?.cancel()
            pollJob = null
        }
    }

    private fun startDelegatedSessionPoll() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive && readyToConfirm && !isConfirming) {
                val id = runCatching {
                    webView.engine.executeScript(
                        "(function(){try{return (window.ytcfg&&window.ytcfg.get)?" +
                            "(window.ytcfg.get('DELEGATED_SESSION_ID')||''):'';}catch(e){return '';}})()",
                    )?.toString()?.trim('"')?.takeUnless { it == "null" }.orEmpty()
                }.getOrDefault("")
                if (id.isNotBlank() && id != liveDelegatedSessionId) {
                    liveDelegatedSessionId = id
                }
                delay(1.seconds)
            }
        }
    }

    private fun confirmLogin() {
        if (!readyToConfirm || isConfirming) return
        isConfirming = true
        confirmButton.isDisable = true
        progress.isVisible = true
        statusLabel.text = "Validating session…"

        val currentUrl = webView.engine.location ?: MUSIC_HOST_PREFIX
        val cookie = WebCookieStore.cookieHeaderFor(currentUrl)
        val syncId = when {
            liveDelegatedSessionId.isNotBlank() -> liveDelegatedSessionId
            else -> dataSyncId
        }.let { normalizeDataSyncId(it) }

        if (cookie.isBlank()) {
            failConfirm("No cookies captured — finish signing in, then try again.")
            return
        }
        if (visitorData.isBlank()) {
            // Best-effort refresh from page
            runCatching {
                webView.engine.executeScript(
                    "(function(){try{return window.yt&&window.yt.config_&&window.yt.config_.VISITOR_DATA||'';}catch(e){return '';}})()",
                )?.toString()?.takeIf { it.isNotBlank() && it != "null" }?.let { visitorData = it }
            }
        }
        if (visitorData.isBlank()) {
            failConfirm("Missing VISITOR_DATA — wait for YouTube Music to finish loading, then retry.")
            return
        }

        val session = YtMusicSession(
            cookie = cookie,
            visitorData = visitorData,
            dataSyncId = syncId,
        )

        scope.launch {
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
                pollJob?.cancel()
                onLoggedIn(account.name)
            }.onFailure { error ->
                failConfirm("Login failed: ${error.message ?: error::class.simpleName}")
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
