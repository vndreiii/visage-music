package com.music.vivi.compose.local

import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.PrefKeys
import com.music.vivi.database.LocalMusicScanner
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Helpers for local-folder prefs + XDG portal directory picker. */
object LocalFoldersPrefs {
    /** What [addFolders] did with the picked folders. */
    data class AddResult(val added: List<String>, val alreadyCovered: List<String>, val notFolders: List<String>)

    fun folders(prefs: DesktopPrefs): List<String> =
        prefs.getString(PrefKeys.LocalMusicFolders, "")
            .split('|')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    fun setFolders(prefs: DesktopPrefs, paths: List<String>) {
        prefs.setString(
            PrefKeys.LocalMusicFolders,
            paths.map { it.trim().trimEnd('/', '\\').ifEmpty { "/" } }.filter { it.isNotEmpty() }.distinct().joinToString("|"),
        )
    }

    private fun covers(parent: String, child: String): Boolean {
        val p = File(parent).toPath().normalize()
        val c = File(child).toPath().normalize()
        return c.startsWith(p)
    }

    /**
     * Adds every folder in [picked]. A folder already inside a listed one is skipped; a folder that
     * contains listed ones replaces them, so nothing is scanned twice.
     */
    fun addFolders(prefs: DesktopPrefs, picked: List<File>): AddResult {
        var current = folders(prefs)
        val added = mutableListOf<String>()
        val covered = mutableListOf<String>()
        val notFolders = mutableListOf<String>()
        for (dir in picked) {
            val path = dir.absolutePath.trimEnd('/', '\\').ifEmpty { "/" }
            when {
                !dir.isDirectory -> notFolders += path
                current.any { covers(it, path) } -> covered += path
                else -> {
                    current = current.filterNot { covers(path, it) } + path
                    added += path
                }
            }
        }
        setFolders(prefs, current)
        return AddResult(added, covered, notFolders)
    }

    fun removeFolder(prefs: DesktopPrefs, path: String) {
        setFolders(prefs, folders(prefs).filterNot { it == path })
    }

    /** Lets the user pick one or several folders; empty when cancelled. */
    suspend fun pickDirectories(title: String = "Choose music folders"): List<File> =
        XdgPortalFolderPicker.pickDirectories(title)
}

/** Result of asking for a scan, so the UI can say why nothing started. */
sealed interface ScanStart {
    data object Started : ScanStart
    data object NoFolders : ScanStart
    data object AlreadyRunning : ScanStart
    data class Unreachable(val missing: List<String>) : ScanStart
}

/**
 * Startup auto-scan when [PrefKeys.LocalAutoScanOnStartup] is enabled, and the scan entry point for Settings.
 * The index only ever holds what the configured folders say: rows from folders that were removed or
 * changed are dropped at startup and after each scan.
 */
class LocalLibraryBootstrap(
    private val prefs: DesktopPrefs,
    private val scanner: LocalMusicScanner,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun maybeAutoScan() {
        scope.launch {
            runCatching {
                scanner.pruneOutside(LocalFoldersPrefs.folders(prefs).map { File(it) })
                scanner.normalizeArtistCredits()
            }.onFailure { println("Local index cleanup failed: ${it.message}") }
        }
        if (!prefs.getBoolean(PrefKeys.LocalAutoScanOnStartup, false)) return
        scanNow()
    }

    fun scanNow(): ScanStart {
        val configured = LocalFoldersPrefs.folders(prefs).map { File(it) }
        if (configured.isEmpty()) return ScanStart.NoFolders
        if (scanner.progress.value.running) return ScanStart.AlreadyRunning
        val reachable = configured.filter { it.isDirectory }
        if (reachable.isEmpty()) return ScanStart.Unreachable(configured.map { it.absolutePath })
        scope.launch {
            runCatching { scanner.scan(reachable, configured) }
                .onFailure { println("Local scan failed: ${it.message}") }
        }
        return ScanStart.Started
    }
}
