package com.music.vivi.compose.crash

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowExceptionHandlerFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One crash/error occurrence, ready to be shown and reported. */
data class CrashReport(
    val id: Long,
    /** Plain-language headline. */
    val title: String,
    /** One or two plain sentences on what probably happened and what to do. */
    val explanation: String,
    val exceptionSummary: String,
    val stackTrace: String,
    val thread: String,
    val timeMs: Long,
    /** The UI can't be trusted to keep working (composition / main-thread failure). */
    val fatal: Boolean,
)

/**
 * Collects uncaught errors and exposes the latest one to the UI, instead of the default Java
 * error output. Errors on UI threads become a Material 3 dialog; background-thread failures
 * (network blips etc.) are only logged so they don't interrupt listening.
 */
object CrashReporter {
    /** Where bug reports go (Gitea "new issue" form; title and body are prefilled). */
    const val ISSUES_URL = "https://code.milfs.party/alex/vivi-desktop/issues/new"

    private val _current = MutableStateFlow<CrashReport?>(null)
    val current: StateFlow<CrashReport?> = _current.asStateFlow()

    /** Set once a composition error killed the UI; `main` then shows the standalone error window. */
    @Volatile var uiDied = false
        private set

    /** Closes the broken window (and releases audio) so the standalone error window can take over. */
    @Volatile var onUiDied: (() -> Unit)? = null

    private var nextId = 1L
    private var lastSignature: String? = null
    private var lastAtMs = 0L

    fun install() {
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            record(thread.name, error, fatal = isUiThread(thread.name))
        }
        runCatching { System.setProperty("sun.awt.exception.handler", AwtHandler::class.java.name) }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    val windowExceptionHandlerFactory = WindowExceptionHandlerFactory { _ ->
        // A composition error leaves Compose unable to update the window, so a dialog inside it could
        // never appear: record it and hand over to the standalone window.
        WindowExceptionHandler { error ->
            record(Thread.currentThread().name, error, fatal = true)
            uiDied = true
            onUiDied?.invoke()
        }
    }

    /** Loaded reflectively by the AWT toolkit when `sun.awt.exception.handler` is set. */
    class AwtHandler {
        @Suppress("unused")
        fun handle(t: Throwable) = record(Thread.currentThread().name, t, fatal = true)
    }

    fun record(thread: String, error: Throwable, fatal: Boolean) {
        val stack = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        System.err.println("Uncaught on $thread: ${error.javaClass.simpleName}: ${error.message}")
        System.err.println(stack)
        if (!fatal) return

        // The same failure repeating every frame must show one dialog, not hundreds.
        val signature = error.javaClass.name + error.stackTrace.firstOrNull()
        val now = System.currentTimeMillis()
        synchronized(this) {
            if (signature == lastSignature && now - lastAtMs < 5_000) return
            lastSignature = signature
            lastAtMs = now
            val (title, explanation) = describe(error)
            _current.value = CrashReport(
                id = nextId++,
                title = title,
                explanation = explanation,
                exceptionSummary = "${error.javaClass.name}: ${error.message ?: "(no message)"}",
                stackTrace = stack,
                thread = thread,
                timeMs = now,
                fatal = true,
            )
        }
    }

    /** Starts a fresh copy of the app (same command line) for the "Restart" button. */
    fun restart() {
        runCatching {
            val info = ProcessHandle.current().info()
            val command = info.command().orElse(null) ?: return
            val args = info.arguments().orElse(emptyArray())
            ProcessBuilder(listOf(command) + args).inheritIO().start()
        }
    }

    fun dismiss() {
        _current.value = null
    }

    fun isUiThread(name: String) = name.startsWith("AWT-EventQueue") || name == "main" || name.startsWith("AWT-Shutdown")

    /** Headline + plain-language explanation for the most common failure families. */
    fun describe(error: Throwable): Pair<String, String> {
        val chain = generateSequence(error) { it.cause }.take(6).toList()
        fun has(vararg names: String) = chain.any { t -> names.any { t.javaClass.name.contains(it, ignoreCase = true) } }
        return when {
            has("OutOfMemoryError") ->
                "VIVI ran out of memory" to "The app needed more memory than it was given. Closing other apps or restarting VIVI usually fixes it."
            has("UnsatisfiedLinkError", "NoClassDefFoundError") ->
                "A required component is missing" to "A native library (for example libmpv) or a part of the app could not be loaded. Reinstalling or rebuilding VIVI should fix it."
            has("SQLite", "SQLException") ->
                "The music library database hit a problem" to "Reading or writing the local library failed. Restarting usually clears it; if it keeps happening, report it."
            has("UnknownHost", "ConnectException", "SocketTimeout", "SSLException") ->
                "Couldn't reach the network" to "A connection to an online service failed. Check your internet connection and try again."
            has("FileNotFound", "NoSuchFile", "AccessDenied") ->
                "A file couldn't be opened" to "VIVI couldn't read or write a file it needed. Check that the folder still exists and is accessible."
            else ->
                "Something went wrong in VIVI" to "An unexpected error interrupted the app. You can keep going, but if things look off, restarting is a good idea. Reporting it helps get it fixed."
        }
    }

    /** Everything a maintainer needs: version info, the error and the recent log. */
    fun fullReport(report: CrashReport, logLines: Int = 250): String = buildString {
        appendLine("VIVI Desktop crash report")
        appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT).format(Date(report.timeMs))}")
        appendLine("Thread: ${report.thread}")
        appendLine("Java: ${System.getProperty("java.version")} (${System.getProperty("java.vendor")})")
        appendLine("OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}")
        appendLine("Session: ${System.getenv("XDG_SESSION_TYPE") ?: "?"} / ${System.getenv("XDG_CURRENT_DESKTOP") ?: "?"}")
        appendLine("Log file: ${AppLog.logFile ?: "-"}")
        appendLine()
        appendLine("--- Error ---")
        appendLine(report.exceptionSummary)
        appendLine(report.stackTrace.trimEnd())
        appendLine()
        appendLine("--- Last log lines ---")
        appendLine(AppLog.snapshot(logLines))
    }

    fun copyToClipboard(text: String) {
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
    }

    /** Short issue body (URLs can't carry a whole log); the full report is put on the clipboard. */
    fun issueUrl(report: CrashReport): String {
        val body = buildString {
            appendLine("**What happened**")
            appendLine("<!-- describe what you were doing -->")
            appendLine()
            appendLine("**Error**")
            appendLine("```")
            appendLine(report.exceptionSummary)
            appendLine(report.stackTrace.lines().take(12).joinToString("\n"))
            appendLine("```")
            appendLine()
            appendLine("_Full report copied to clipboard; paste it below._")
        }.take(1800)
        fun enc(s: String) = URLEncoder.encode(s, Charsets.UTF_8)
        return "$ISSUES_URL?title=${enc("[crash] " + report.exceptionSummary.take(90))}&body=${enc(body)}"
    }

    fun openInBrowser(url: String) {
        val ok = runCatching {
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(java.net.URI(url)); true
            } else false
        }.getOrDefault(false)
        if (!ok) runCatching { ProcessBuilder("xdg-open", url).start() }
    }

    fun openLogFolder() {
        val dir: File = AppLog.logDir()
        runCatching { ProcessBuilder("xdg-open", dir.absolutePath).start() }
    }
}
