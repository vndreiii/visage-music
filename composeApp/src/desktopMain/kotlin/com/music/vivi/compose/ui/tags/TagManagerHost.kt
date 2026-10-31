package com.music.vivi.compose.ui.tags

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.PrefKeys
import com.music.vivi.compose.ui.library.LibraryCache
import com.music.vivi.database.DatabaseFactory
import com.music.vivi.database.LocalMusicRepository
import com.music.vivi.nativelib.AppPaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.context.GlobalContext
import java.io.File

/**
 * Owns the one [TagManagerState] for the whole app run, on a scope that outlives the Tag Manager page. Auto-tagging and
 * tag writing therefore keep going when the user leaves the page, and the floating progress button ([TagWorkFab]) can
 * take them back to it.
 */
object TagManagerHost {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Null until the Tag Manager has been opened once. Observable. */
    var state by mutableStateOf<TagManagerState?>(null)
        private set

    var nativeWriter: NativeTagWriter? = null
        private set

    fun get(repo: LocalMusicRepository, prefs: DesktopPrefs): TagManagerState {
        state?.let { return it }
        val writer = NativeTagWriter { DatabaseFactory.coversDir() }
        nativeWriter = writer
        return TagManagerState(
            scope = scope,
            repo = repo,
            prefs = prefs,
            writer = writer,
            lookup = NativeTagLookup {
                com.music.vivi.compose.prefs.Secrets.store.get(com.music.vivi.compose.prefs.Secrets.AcoustidKey).ifBlank { System.getenv("VISAGE_ACOUSTID_KEY").orEmpty() }.ifBlank { null }
            },
            backupRoot = File(AppPaths.data(), "tag-backups"),
            refreshLibrary = {
                runCatching { GlobalContext.get().get<LibraryCache>().loadLocal() }
            },
        ).also { state = it }
    }
}
