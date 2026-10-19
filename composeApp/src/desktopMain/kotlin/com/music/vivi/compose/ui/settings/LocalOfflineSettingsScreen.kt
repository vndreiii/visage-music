package com.music.vivi.compose.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.music.vivi.compose.download.TrackDownloadService
import com.music.vivi.compose.local.LocalFoldersPrefs
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.PrefKeys
import com.music.vivi.database.DatabaseFactory
import com.music.vivi.database.DownloadedTrackRepository
import com.music.vivi.database.LocalMusicRepository
import com.music.vivi.database.LocalMusicScanner
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@Composable
fun LocalOfflineSettingsScreen(navController: NavController) {
    val prefs = koinInject<DesktopPrefs>()
    val scanner = koinInject<LocalMusicScanner>()
    val localRepo = koinInject<LocalMusicRepository>()
    val downloadedRepo = koinInject<DownloadedTrackRepository>()
    val downloads = koinInject<TrackDownloadService>()
    val scope = rememberCoroutineScope()
    val bootstrap = koinInject<com.music.vivi.compose.local.LocalLibraryBootstrap>()
    val prefVersion = rememberPrefsVersion(prefs)
    val progress by scanner.progress.collectAsState()
    var folders by remember { mutableStateOf(LocalFoldersPrefs.folders(prefs)) }
    var trackCount by remember { mutableStateOf(0L) }
    var offlineCount by remember { mutableStateOf(0L) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    suspend fun refreshCounts() {
        val counts = withContext(Dispatchers.IO) { localRepo.trackCount() to downloadedRepo.count() }
        trackCount = counts.first
        offlineCount = counts.second
        folders = LocalFoldersPrefs.folders(prefs)
    }
    LaunchedEffect(progress.running, prefVersion) {
        if (!progress.running) refreshCounts()
        statusMsg = progress.message
    }

    SettingsPage(title = "Local files", onBack = { navController.navigateUp() }) {

        SettingsSection("Offline (app downloads)") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    "Managed folder",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    downloads.resolveFolder().absolutePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    "$offlineCount tracks registered",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "Offline is only what VIVI downloads — not your OS Downloads folder.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        SettingsSection("Local music folders") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    "$trackCount local tracks indexed",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                folders.forEach { path ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            path,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                scope.launch {
                                    LocalFoldersPrefs.removeFolder(prefs, path)
                                    withContext(Dispatchers.IO) {
                                        val root = File(path).toPath()
                                        localRepo.removeTracks(localRepo.allTracks().filter { File(it.file_path).toPath().startsWith(root) }.map { it.id })
                                    }
                                    refreshCounts()
                                }
                            },
                            enabled = !progress.running,
                        ) { Text("Remove") }
                    }
                }
                if (folders.isEmpty()) {
                    Text(
                        "Add folders that hold your personal music collection.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val dir = LocalFoldersPrefs.pickDirectory()
                            if (dir != null) {
                                LocalFoldersPrefs.addFolder(prefs, dir.absolutePath)
                                refreshCounts()
                            }
                        }
                    },
                ) {
                    Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add folder")
                }
            }
        }

        SettingsSection("Scan") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Button(
                    onClick = {
                        bootstrap.scanNow()
                    },
                    enabled = !progress.running && folders.isNotEmpty(),
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (progress.running) "Scanning…" else "Scan now")
                }
                if (progress.running) {
                    TextButton(onClick = { scanner.requestCancel() }) { Text("Cancel scan") }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = {
                            if (progress.totalHint <= 0) 0f
                            else progress.scanned.toFloat() / progress.totalHint
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        progress.currentFile ?: "Working…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                statusMsg?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                SwitchItem(
                    icon = Icons.Outlined.Refresh,
                    title = "Auto-scan on startup",
                    subtitle = "Quietly look for new files when VIVI launches",
                    checked = prefs.getBoolean(PrefKeys.LocalAutoScanOnStartup, false),
                    onChecked = { prefs.setBoolean(PrefKeys.LocalAutoScanOnStartup, it) },
                )
                Text(
                    "Local files use their embedded tags and artwork. Lyrics are read from matching .lrc files or embedded tags.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        SettingsSection("Tag Manager") {
            Column(Modifier.padding(vertical = 4.dp)) {
                SwitchItem(
                    icon = Icons.Outlined.Folder,
                    title = "Back up originals before writing",
                    subtitle = "Keeps copies of changed files (last 10 batches) so a batch can be undone later",
                    checked = prefs.getBoolean(PrefKeys.TagBackupEnabled, true),
                    onChecked = { prefs.setBoolean(PrefKeys.TagBackupEnabled, it) },
                )
                SwitchItem(
                    icon = Icons.Outlined.Album,
                    title = "Write ID3v2.3 tags",
                    subtitle = "Better compatibility with old players; default is ID3v2.4",
                    checked = prefs.getBoolean(PrefKeys.TagId3v23, false),
                    onChecked = { prefs.setBoolean(PrefKeys.TagId3v23, it) },
                )
                OutlinedButton(
                    onClick = { navController.navigate(com.music.vivi.compose.navigation.Routes.TAG_MANAGER) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text("Open Tag Manager") }
            }
        }

        SettingsSection("Cache") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                OutlinedButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            val result = runCatching { scanner.clearCache() }
                            withContext(Dispatchers.Main) {
                                refreshCounts()
                                statusMsg = result.fold({ "Local library and cover cache cleared" }, { it.message })
                            }
                        }
                    },
                    enabled = !progress.running,
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Clear local cache")
                }
                Text(
                    "Removes indexed metadata and cached covers. Your music files stay in their folders.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
