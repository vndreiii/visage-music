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
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
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
import com.music.vivi.compose.local.ScanStart
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
    val folders = remember(prefVersion) { LocalFoldersPrefs.folders(prefs) }
    // Per folder: how many indexed tracks live under it, and whether it can be reached right now (drive mounted).
    var folderStats by remember { mutableStateOf<Map<String, Pair<Boolean, Int>>>(emptyMap()) }
    var trackCount by remember { mutableStateOf(0L) }
    var offlineCount by remember { mutableStateOf(0L) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    suspend fun refreshCounts() {
        val counts = withContext(Dispatchers.IO) { localRepo.trackCount() to downloadedRepo.count() }
        trackCount = counts.first
        offlineCount = counts.second
        folderStats = withContext(Dispatchers.IO) {
            val paths = localRepo.allTracks().map { File(it.file_path).toPath().normalize() }
            LocalFoldersPrefs.folders(prefs).associateWith { folder ->
                val root = File(folder).toPath().normalize()
                File(folder).isDirectory to paths.count { it.startsWith(root) }
            }
        }
    }
    LaunchedEffect(progress.running, prefVersion) {
        if (!progress.running) refreshCounts()
        progress.message?.let { statusMsg = it }
    }
    fun startScan() {
        statusMsg = when (val r = bootstrap.scanNow()) {
            ScanStart.Started -> "Scanning…"
            ScanStart.NoFolders -> "Add a music folder first."
            ScanStart.AlreadyRunning -> "A scan is already running."
            is ScanStart.Unreachable -> "Can't reach ${r.missing.joinToString()} — is the drive mounted?"
        }
    }

    var confirmClear by remember { mutableStateOf(false) }

    SettingsPage(title = "Local files", onBack = { navController.navigateUp() }) {

        SettingsSection("Music folders") {
            folders.forEach { path ->
                val stat = folderStats[path]
                val reachable = stat?.first ?: File(path).isDirectory
                SettingsRowFrame(
                    Icons.Outlined.Folder,
                    path,
                    if (reachable) "${stat?.second ?: 0} tracks" else "Not found. Is the drive mounted?",
                    onClick = null,
                    trailing = {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    LocalFoldersPrefs.removeFolder(prefs, path)
                                    withContext(Dispatchers.IO) {
                                        scanner.pruneOutside(LocalFoldersPrefs.folders(prefs).map { File(it) })
                                    }
                                    refreshCounts()
                                }
                            },
                            enabled = !progress.running,
                        ) { Text("Remove") }
                    },
                )
                SettingsDivider()
            }
            SettingsRowFrame(
                Icons.Outlined.FolderOpen,
                "Add folders",
                if (folders.isEmpty()) "Pick the folders that hold your music. Subfolders are included"
                else "$trackCount local tracks indexed. Subfolders are included",
                onClick = {
                    scope.launch {
                        val picked = LocalFoldersPrefs.pickDirectories()
                        if (picked.isNotEmpty()) {
                            val r = LocalFoldersPrefs.addFolders(prefs, picked)
                            statusMsg = buildString {
                                if (r.added.isNotEmpty()) append("Added ${r.added.size} folder${if (r.added.size == 1) "" else "s"}. ")
                                if (r.alreadyCovered.isNotEmpty()) append("Already included: ${r.alreadyCovered.joinToString()}. ")
                                if (r.notFolders.isNotEmpty()) append("Not a folder: ${r.notFolders.joinToString()}.")
                            }.trim()
                            refreshCounts()
                            if (r.added.isNotEmpty()) startScan()
                        }
                    }
                },
            )
        }

        SettingsSection("Scan") {
            SettingsRowFrame(
                Icons.Outlined.Refresh,
                if (progress.running) "Scanning" else "Scan now",
                when {
                    progress.running -> progress.currentFile ?: "Working"
                    statusMsg != null -> statusMsg
                    else -> "Reads tags, artwork and .lrc lyrics from your folders"
                },
                onClick = if (progress.running) null else ({ startScan() }),
                belowSubtitle = {
                    if (progress.running) {
                        LinearProgressIndicator(
                            progress = {
                                if (progress.totalHint <= 0) 0f
                                else progress.scanned.toFloat() / progress.totalHint
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    }
                },
                trailing = {
                    if (progress.running) TextButton(onClick = { scanner.requestCancel() }) { Text("Cancel") }
                },
            )
            SettingsDivider()
            SwitchItem(
                icon = Icons.Outlined.Schedule,
                title = "Auto-scan on startup",
                subtitle = "Quietly look for new files when VIsage launches",
                checked = prefs.getBoolean(PrefKeys.LocalAutoScanOnStartup, false),
                onChecked = { prefs.setBoolean(PrefKeys.LocalAutoScanOnStartup, it) },
            )
        }

        SettingsSection("Offline downloads") {
            SettingsRowFrame(
                Icons.Outlined.Folder,
                "Download folder",
                downloads.resolveFolder().absolutePath,
                onClick = null,
            )
            SettingsDivider()
            SettingsRowFrame(
                Icons.Outlined.DownloadDone,
                "Offline tracks",
                "$offlineCount downloaded by VIsage. Your OS Downloads folder is not included",
                onClick = null,
            )
        }

        SettingsSection("Tag Manager") {
            SwitchItem(
                icon = Icons.Outlined.Folder,
                title = "Back up originals before writing",
                subtitle = "Keeps copies of changed files (last 10 batches) so a batch can be undone later",
                checked = prefs.getBoolean(PrefKeys.TagBackupEnabled, true),
                onChecked = { prefs.setBoolean(PrefKeys.TagBackupEnabled, it) },
            )
            SettingsDivider()
            SwitchItem(
                icon = Icons.Outlined.Album,
                title = "Write ID3v2.3 tags",
                subtitle = "Better compatibility with old players; default is ID3v2.4",
                checked = prefs.getBoolean(PrefKeys.TagId3v23, false),
                onChecked = { prefs.setBoolean(PrefKeys.TagId3v23, it) },
            )
            SettingsDivider()
            SettingsNavItem(
                icon = Icons.Outlined.Edit,
                title = "Bulk tag editor",
                subtitle = "Edit tags and artwork for many files at once",
                onClick = { navController.navigate(com.music.vivi.compose.navigation.Routes.TAG_MANAGER) },
            )
        }

        SettingsSection("Cache") {
            SettingsRowFrame(
                Icons.Outlined.Delete,
                "Clear local cache",
                if (progress.running) "Wait for the scan to finish" else "Removes indexed metadata and cached covers. Your music files stay in their folders",
                onClick = if (progress.running) null else ({ confirmClear = true }),
            )
        }

        if (confirmClear) {
            AlertDialog(
                onDismissRequest = { confirmClear = false },
                title = { Text("Clear the local cache?") },
                text = { Text("The index of your local tracks and the cached covers are removed. Your music files are not touched; a new scan rebuilds the index.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmClear = false
                        scope.launch(Dispatchers.IO) {
                            val result = runCatching { scanner.clearCache() }
                            withContext(Dispatchers.Main) {
                                refreshCounts()
                                statusMsg = result.fold({ "Local library and cover cache cleared" }, { it.message })
                            }
                        }
                    }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
            )
        }
    }
}
