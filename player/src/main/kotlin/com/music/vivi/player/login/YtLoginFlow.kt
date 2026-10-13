package com.music.vivi.player.login

import com.music.innertube.auth.YtMusicSession
import com.music.vivi.player.session.SessionSecretStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

private const val LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"
private const val MUSIC_HOST_PREFIX = "https://music.youtube.com"

/**
 * YouTube Music sign-in. Google rejects embedded WebViews, so this opens a real Chromium
 * app window and reads the session (cookies + visitor data) over CDP. UI-toolkit agnostic.
 */
class YtLoginFlow(private val onLoggedIn: (accountName: String) -> Unit = {}) {
    data class State(
        val status: String = "Sign in with Google in a Chromium window.",
        val browserOpen: Boolean = false,
        val canConfirm: Boolean = false,
        val busy: Boolean = false,
        val signedInAs: String? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var pollJob: Job? = null
    private var browserProcess: Process? = null
    private var cdp: CdpClient? = null
    @Volatile private var readyToConfirm = false

    private fun set(transform: State.() -> State) = _state.update { it.transform() }

    fun open() {
        val browser = ChromiumLauncher.findBrowser()
        if (browser == null) {
            set { copy(status = "No Chromium/Chrome found. Install chromium and retry.") }
            return
        }
        runCatching { browserProcess?.destroy() }
        runCatching { cdp?.close() }
        cdp = null
        pollJob?.cancel()
        readyToConfirm = false

        val port = ChromiumLauncher.pickFreePort()
        set { copy(status = "Launching $browser…", browserOpen = true, canConfirm = false) }
        browserProcess = ChromiumLauncher.launch(browser, LOGIN_URL, port, ChromiumLauncher.profileDir())

        scope.launch {
            val client = CdpClient(port)
            try {
                withContext(Dispatchers.IO) {
                    client.waitUntilReady()
                    client.reconnectLoop()
                }
                cdp = client
                set { copy(status = "Sign in inside the Chromium window…") }
                startReadyPoll(client)
            } catch (t: Throwable) {
                client.close()
                set { copy(status = "Could not attach to Chromium: ${t.message}", browserOpen = false) }
            }
        }
    }

    private fun startReadyPoll(client: CdpClient) {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive && !_state.value.busy) {
                try {
                    val url = withContext(Dispatchers.IO) { client.currentUrl() }
                    if (url.startsWith(MUSIC_HOST_PREFIX)) {
                        if (!readyToConfirm) {
                            readyToConfirm = true
                            set { copy(canConfirm = true, status = "YouTube Music detected — press Confirm to save your session.") }
                        }
                    } else {
                        set { copy(status = "Browsing: ${url.take(80)}…") }
                    }
                } catch (_: Throwable) {
                    // Tab navigated / socket dropped — reattach.
                    runCatching { withContext(Dispatchers.IO) { client.reconnectLoop(3) } }
                }
                delay(1.seconds)
            }
        }
    }

    fun confirm() {
        if (!readyToConfirm || _state.value.busy) return
        val client = cdp ?: return fail("Not connected to Chromium.")
        set { copy(busy = true, canConfirm = false, status = "Reading cookies and validating…") }

        scope.launch {
            try {
                val (cookie, visitorData, dataSyncId) = withContext(Dispatchers.IO) {
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
                if (cookie.isBlank() || !cookie.contains("SAPISID")) {
                    return@launch fail("Cookies incomplete (missing SAPISID). Finish sign-in on YouTube Music, then retry.")
                }
                if (visitorData.isBlank()) {
                    return@launch fail("Missing VISITOR_DATA — wait for YouTube Music to finish loading, then retry.")
                }
                val session = YtMusicSession(cookie = cookie, visitorData = visitorData, dataSyncId = dataSyncId)
                val result = withTimeoutOrNull(20.seconds) {
                    withContext(Dispatchers.IO) {
                        var attempt = session.validate()
                        if (attempt.isFailure) {
                            delay(750)
                            attempt = session.validate()
                        }
                        attempt
                    }
                } ?: return@launch fail("Login timed out.")

                result.onSuccess { account ->
                    withContext(Dispatchers.IO) { SessionSecretStore.save(session, account) }
                    session.applyToYouTube()
                    pollJob?.cancel()
                    runCatching { browserProcess?.destroy() }
                    set { copy(busy = false, browserOpen = false, signedInAs = account.name, status = "Signed in as ${account.name}.") }
                    onLoggedIn(account.name)
                }.onFailure { fail("Login failed: ${it.message ?: it::class.simpleName}") }
            } catch (t: Throwable) {
                fail("Confirm failed: ${t.message}")
            }
        }
    }

    private fun fail(message: String) =
        set { copy(busy = false, canConfirm = readyToConfirm, status = message) }

    fun close() {
        pollJob?.cancel()
        runCatching { browserProcess?.destroy() }
        runCatching { cdp?.close() }
        scope.cancel()
    }
}
