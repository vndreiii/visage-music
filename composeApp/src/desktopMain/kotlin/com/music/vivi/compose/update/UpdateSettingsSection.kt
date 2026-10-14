package com.music.vivi.compose.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.ui.expressive.ExpressiveButton
import com.music.vivi.compose.ui.expressive.ExpressiveSize
import com.music.vivi.compose.ui.expressive.ExpressiveStyle
import com.music.vivi.compose.ui.settings.SettingsDivider
import com.music.vivi.compose.ui.settings.SettingsSection
import java.text.DateFormat
import java.util.Date
import org.koin.compose.koinInject

/** Settings → Updates: current version, manual check, auto-check toggle, and the status line. */
@Composable
fun UpdateSettingsSection() {
    val prefs = koinInject<DesktopPrefs>()
    val service = remember(prefs) { UpdateService.start(prefs) }
    val status by service.status.collectAsState()
    val busy = status.phase == UpdatePhase.Checking ||
        status.phase == UpdatePhase.Downloading || status.phase == UpdatePhase.Restarting
    val available = status.disabledReason == null

    SettingsSection(title = "Updates") {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Current version", style = MaterialTheme.typography.bodyLarge)
                Text(
                    status.currentVersion,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SettingsDivider()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Check automatically", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Every six hours, from GitHub Releases",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = status.autoCheck,
                onCheckedChange = service::setAutoCheck,
                enabled = available,
            )
        }
        SettingsDivider()
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                status.disabledReason ?: status.message ?: lastCheckedLine(status.lastCheckedAt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (status.phase == UpdatePhase.Downloading) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { status.progress }, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                status.offer?.let { offer ->
                    ExpressiveButton(
                        text = if (service.primaryAction(offer) == UpdateAction.InstallAndRestart) "Install & restart"
                        else "Open release page",
                        onClick = service::install,
                        size = ExpressiveSize.XSmall,
                        style = ExpressiveStyle.Tonal,
                        enabled = !busy,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                ExpressiveButton(
                    text = "Check for updates",
                    onClick = service::checkNow,
                    size = ExpressiveSize.XSmall,
                    style = ExpressiveStyle.Filled,
                    enabled = available && !busy,
                )
            }
        }
    }
}

private fun lastCheckedLine(lastChecked: Long): String =
    if (lastChecked <= 0L) "Not checked yet."
    else "Last checked ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(lastChecked))}."
