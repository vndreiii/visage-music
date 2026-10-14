package com.music.vivi.player.login

import com.music.vivi.player.session.SessionSecretStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
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
 * YouTube Music sign-in in the UI-provided embedded Chromium browser.
 * Reads HttpOnly cookies and page configuration over the loopback CDP connection.
 */
class YtLoginFlow(
    private val browserHost: LoginBrowserHost,
    private val onLoggedIn: (accountName: String) -> Unit = {},
) {
    data class State(
        val status: String = "Sign in with Google inside VIsage.",
        val browserOpen: Boolean = false,
        val canConfirm: Boolean = false,
        val busy: Boolean = false,
        val signedInAs: String? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var pollJob: Job? = null
    private var openJob: Job? = null
    private var cdp: CdpClient? = null
    @Volatile private var readyToConfirm = false

    private fun set(transform: State.() -> State) = _state.update { it.transform() }

    fun open() {
        if (openJob?.isActive == true || _state.value.busy) return
        readyToConfirm = false
        set { copy(status = "Opening embedded sign-in…", busy = true, browserOpen = true, canConfirm = false) }
        openJob = scope.launch {
            pollJob?.cancelAndJoin()
            cdp?.close()
            cdp = null
            val client = CdpClient(browserHost.debugPort)
            try {
                browserHost.open(LOGIN_URL)
                withContext(Dispatchers.IO) {
                    client.waitUntilReady()
                    client.reconnectLoop()
                    // The browser must be attached to SwingPanel before its native
                    // page exists. Restore cookies only after CDP is ready.
                    SessionSecretStore.loadSession()?.let { saved ->
                        client.restoreSession(saved.cookie)
                        client.navigate("https://music.youtube.com/")
                    }
                }
                cdp = client
                set { copy(status = "Sign in below, then press Confirm.", busy = false) }
                startReadyPoll(client)
            } catch (t: Throwable) {
                client.close()
                set { copy(status = "Could not open embedded sign-in: ${t.message}", busy = false, browserOpen = false) }
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
                pollJob?.cancelAndJoin()
                val session = withContext(Dispatchers.IO) {
                    client.reconnectLoop()
                    client.captureSession()
                }
                val cookie = session.cookie
                val visitorData = session.visitorData
                if (cookie.isBlank() || !cookie.contains("SAPISID")) {
                    return@launch fail("Cookies incomplete (missing SAPISID). Finish sign-in on YouTube Music, then retry.")
                }
                if (visitorData.isBlank()) {
                    return@launch fail("Missing VISITOR_DATA — wait for YouTube Music to finish loading, then retry.")
                }
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
                    browserHost.close()
                    set { copy(busy = false, browserOpen = false, signedInAs = account.name, status = "Signed in as ${account.name}.") }
                    onLoggedIn(account.name)
                }.onFailure { fail("Login failed: ${it.message?.substringBefore(". Text:")?.take(180) ?: it::class.simpleName}") }
            } catch (t: Throwable) {
                fail("Confirm failed: ${t.message}")
            }
        }
    }

    private fun fail(message: String) =
        set { copy(busy = false, canConfirm = readyToConfirm, status = message) }

    fun close() {
        pollJob?.cancel()
        openJob?.cancel()
        browserHost.close()
        runCatching { cdp?.close() }
        scope.cancel()
    }
}
