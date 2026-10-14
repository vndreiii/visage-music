package com.music.vivi.compose.crash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.awt.ComposeWindow
import kotlinx.coroutines.delay
import com.music.vivi.compose.ui.expressive.ExpressiveButton
import com.music.vivi.compose.ui.expressive.ExpressiveStyle
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.system.exitProcess

/** Shows the latest [CrashReporter] error as a Material 3 dialog over the running app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashDialogHost(onQuit: () -> Unit) {
    val report by CrashReporter.current.collectAsState()
    val shown = report ?: return
    BasicAlertDialog(onDismissRequest = CrashReporter::dismiss) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
            modifier = Modifier.width(600.dp),
        ) {
            CrashPanel(shown, dismissLabel = "Dismiss", onDismiss = CrashReporter::dismiss, onQuit = onQuit)
        }
    }
}

/**
 * Standalone error window for when the main window can't be trusted any more. It is an independent
 * [ComposeWindow] (not part of the Compose `application` loop, which may be dead), so it works even
 * after a composition crash. Closing it ends the process; "Restart VIVI" relaunches first.
 */
fun showFatalCrashWindow(report: CrashReport) {
    SwingUtilities.invokeLater {
        val window = ComposeWindow()
        window.title = "VIVI Desktop — crashed"
        window.setSize(680, 680)
        window.setLocationRelativeTo(null)
        window.defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
        window.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) = exitProcess(0)
        })
        window.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    CrashPanel(
                        report,
                        dismissLabel = "Close",
                        onDismiss = { exitProcess(0) },
                        onQuit = null,
                        onRestart = {
                            CrashReporter.restart()
                            exitProcess(0)
                        },
                    )
                }
            }
        }
        window.isVisible = true
        // Hide the broken main window; it can no longer update.
        java.awt.Window.getWindows().filter { it !== window && it.isVisible }.forEach { it.isVisible = false }
    }
}

private enum class Detail { Error, Log }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CrashPanel(
    report: CrashReport,
    dismissLabel: String,
    onDismiss: () -> Unit,
    onQuit: (() -> Unit)?,
    onRestart: (() -> Unit)? = null,
) {
    var detail by remember(report.id) { mutableStateOf(Detail.Error) }
    var copied by remember(report.id) { mutableStateOf(false) }
    val logText = remember(report.id, detail) { if (detail == Detail.Log) AppLog.snapshot(200) else "" }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2_000)
            copied = false
        }
    }

    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxSize()) {}
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            }
            Column(Modifier.weight(1f)) {
                Text(report.title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    report.exceptionSummary.substringAfterLast('.').take(120),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Text(report.explanation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = detail == Detail.Error, onClick = { detail = Detail.Error }, label = { Text("Error details") })
            FilterChip(selected = detail == Detail.Log, onClick = { detail = Detail.Log }, label = { Text("Log") })
        }
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 260.dp),
        ) {
            val scroll = rememberScrollState()
            SelectionContainer {
                Text(
                    if (detail == Detail.Error) "${report.exceptionSummary}\n\n${report.stackTrace.trimEnd()}" else logText.ifBlank { "(log is empty)" },
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
                text = if (copied) "Copied" else "Copy report",
                onClick = {
                    CrashReporter.copyToClipboard(CrashReporter.fullReport(report))
                    copied = true
                },
                style = ExpressiveStyle.Outlined,
                leadingIcon = if (copied) Icons.Outlined.CheckCircle else Icons.Outlined.ContentCopy,
            )
            ExpressiveButton(
                text = "Report problem",
                onClick = {
                    CrashReporter.copyToClipboard(CrashReporter.fullReport(report))
                    copied = true
                    CrashReporter.openInBrowser(CrashReporter.issueUrl(report))
                },
                style = ExpressiveStyle.Tonal,
                leadingIcon = Icons.Outlined.BugReport,
            )
            ExpressiveButton(
                text = "Open log folder",
                onClick = CrashReporter::openLogFolder,
                style = ExpressiveStyle.Text,
                leadingIcon = Icons.Outlined.FolderOpen,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            ExpressiveButton(text = dismissLabel, onClick = onDismiss, style = ExpressiveStyle.Text)
            if (onRestart != null) ExpressiveButton(text = "Restart VIVI", onClick = onRestart)
            if (onQuit != null) ExpressiveButton(text = "Quit VIVI", onClick = onQuit)
        }
    }
}
