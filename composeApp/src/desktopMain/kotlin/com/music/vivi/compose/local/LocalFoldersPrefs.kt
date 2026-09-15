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
    fun folders(prefs: DesktopPrefs): List<String> =
        prefs.getString(PrefKeys.LocalMusicFolders, "")
            .split('|')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    fun setFolders(prefs: DesktopPrefs, paths: List<String>) {
        prefs.setString(
            PrefKeys.LocalMusicFolders,
            paths.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("|"),
        )
    }

    fun addFolder(prefs: DesktopPrefs, path: String) {
        setFolders(prefs, folders(prefs) + path)
    }

    fun removeFolder(prefs: DesktopPrefs, path: String) {
        setFolders(prefs, folders(prefs).filterNot { it == path })
    }

    suspend fun pickDirectory(title: String = "Choose music folder"): File? =
        XdgPortalFolderPicker.pickDirectory(title)
}

/**
 * Startup auto-scan when [PrefKeys.LocalAutoScanOnStartup] is enabled.
 */
class LocalLibraryBootstrap(
    private val prefs: DesktopPrefs,
    private val scanner: LocalMusicScanner,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun maybeAutoScan() {
        if (!prefs.getBoolean(PrefKeys.LocalAutoScanOnStartup, false)) return
        scanNow()
    }

    fun scanNow() {
        val dirs = LocalFoldersPrefs.folders(prefs).map { File(it) }.filter { it.isDirectory }
        if (dirs.isEmpty()) return
        scope.launch {
            runCatching { scanner.scan(dirs) }
                .onFailure { println("Auto local scan failed: ${it.message}") }
        }
    }
}
