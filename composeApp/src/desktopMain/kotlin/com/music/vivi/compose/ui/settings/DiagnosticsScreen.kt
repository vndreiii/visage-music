package com.music.vivi.compose.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.music.vivi.canvas.NativeCanvas
import com.music.vivi.player.session.SessionSecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.music.vivi.compose.crash.AppLog
import com.music.vivi.compose.crash.CrashReport
import com.music.vivi.compose.crash.CrashReporter
import com.music.vivi.compose.ui.expressive.ExpressiveButton
import com.music.vivi.compose.ui.expressive.ExpressiveStyle
import com.music.vivi.compose.ui.expressive.ConnectedButtonGroup
import com.music.vivi.database.NativeHelpers
import kotlinx.coroutines.delay

private enum class LogFilter { All, Errors }

/** Settings → Diagnostics: system info, a live log viewer, copy, and a way to report a problem. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiagnosticsScreen(navController: NavController) {
    var filter by remember { mutableStateOf(LogFilter.All) }
    var log by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf<String?>(null) }

    // The log is live: refresh while the page is open.
    LaunchedEffect(filter) {
        while (true) {
            val lines = AppLog.snapshot(400).lines()
            log = (if (filter == LogFilter.Errors) lines.filter { it.startsWith("E ") } else lines).joinToString("\n")
            delay(1_000)
        }
    }
    LaunchedEffect(copied) {
        if (copied != null) {
            delay(2_000)
            copied = null
        }
    }

    // Probing the keyring can block for a few seconds, so keep it off the UI thread.
    var credentials by remember { mutableStateOf("checking…") }
    LaunchedEffect(Unit) {
        credentials = withContext(Dispatchers.IO) {
            runCatching { SessionSecretStore.backendDescription() }.getOrElse { "unavailable (${it.message})" }
        }
    }
    val system = remember(credentials) {
        listOf(
            "Version" to (System.getProperty("visage.version") ?: "dev"),
            "Credential storage" to credentials,
            "Java" to "${System.getProperty("java.version")} (${System.getProperty("java.vendor")})",
            "OS" to "${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}",
            "Session" to "${System.getenv("XDG_SESSION_TYPE") ?: "?"} / ${System.getenv("XDG_CURRENT_DESKTOP") ?: "?"}",
            "Native helpers" to (NativeHelpers.version()?.let { "loaded (v$it)" } ?: "not loaded — using JVM fallbacks"),
            "Canvas lookup" to if (NativeCanvas.available) "native (Rust)" else "Kotlin fallback",
            "Log file" to (AppLog.logFile?.absolutePath ?: "—"),
        )
    }
    val systemText = remember(system) { system.joinToString("\n") { "${it.first}: ${it.second}" } }

    SettingsPage(title = "Diagnostics", onBack = { navController.navigateUp() }) {
        DiagnosticsCard("System") {
            system.forEach { (k, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(k, Modifier.width(130.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SelectionContainer {
                        Text(v, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        DiagnosticsCard("Log") {
            ConnectedButtonGroup(
                options = listOf(LogFilter.All to "All", LogFilter.Errors to "Errors only"),
                selected = filter,
                onSelect = { filter = it },
                modifier = Modifier.width(280.dp),
            )
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth().height(320.dp),
            ) {
                val scroll = rememberScrollState()
                // Follow the newest lines.
                LaunchedEffect(log) { scroll.scrollTo(scroll.maxValue) }
                SelectionContainer {
                    Text(
                        log.ifBlank { "(nothing logged yet)" },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.verticalScroll(scroll).padding(12.dp),
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpressiveButton(
                    text = if (copied == "log") "Copied" else "Copy log",
                    onClick = {
                        CrashReporter.copyToClipboard(AppLog.snapshot(2_000))
                        copied = "log"
                    },
                    style = ExpressiveStyle.Outlined,
                    leadingIcon = if (copied == "log") Icons.Outlined.CheckCircle else Icons.Outlined.ContentCopy,
                )
                ExpressiveButton(
                    text = if (copied == "sys") "Copied" else "Copy system info",
                    onClick = {
                        CrashReporter.copyToClipboard(systemText)
                        copied = "sys"
                    },
                    style = ExpressiveStyle.Outlined,
                    leadingIcon = if (copied == "sys") Icons.Outlined.CheckCircle else Icons.Outlined.ContentCopy,
                )
                ExpressiveButton(
                    text = "Open log folder",
                    onClick = CrashReporter::openLogFolder,
                    style = ExpressiveStyle.Text,
                    leadingIcon = Icons.Outlined.FolderOpen,
                )
                ExpressiveButton(
                    text = "Report a problem",
                    onClick = {
                        val report = CrashReport(
                            id = 0,
                            title = "Manual report",
                            explanation = "",
                            exceptionSummary = "User report",
                            stackTrace = "",
                            thread = Thread.currentThread().name,
                            timeMs = System.currentTimeMillis(),
                            fatal = false,
                        )
                        CrashReporter.copyToClipboard(CrashReporter.fullReport(report, 400))
                        CrashReporter.openInBrowser(CrashReporter.issueUrl(report).replace("%5Bcrash%5D", "%5Bbug%5D"))
                    },
                    style = ExpressiveStyle.Tonal,
                    leadingIcon = Icons.Outlined.BugReport,
                )
            }
        }
        Text(
            "Reporting copies the full log to your clipboard and opens the issue form; paste it there.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun DiagnosticsCard(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}
