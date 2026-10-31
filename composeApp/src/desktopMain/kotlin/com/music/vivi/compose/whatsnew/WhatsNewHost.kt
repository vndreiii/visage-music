package com.music.vivi.compose.whatsnew

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.update.SemVer
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

/** Lets other screens (Settings) reopen the popup with every release listed. */
object WhatsNew {
    internal val reopenSignal = mutableStateOf(0)
    fun show() { reopenSignal.value++ }

    internal const val KEY_LAST_SEEN = "whatsnew.lastSeenVersion"
    /** The release before this feature existed, so upgraders from it still get the popup. */
    internal const val BASELINE = "1.2.0"

    /** Releases newer than [last] and no newer than [current], newest first. */
    internal fun pending(last: SemVer, current: SemVer): List<WhatsNewRelease> =
        WhatsNewCatalog.releases
            .filter { r -> SemVer.parseOrNull(r.version)?.let { it > last && it <= current } == true }
            .sortedByDescending { SemVer.parseOrNull(it.version) }
}

/** Shows the popup once on the first start after an update. Drop it as a child of the full-size app Box. */
@Composable
fun WhatsNewHost() {
    val prefs = koinInject<DesktopPrefs>()
    var releases by remember { mutableStateOf<List<WhatsNewRelease>>(emptyList()) }
    var visible by remember { mutableStateOf(false) }

    val current = remember {
        // VISAGE_WHATSNEW_PREVIEW=<version> lets a dev run (no visage.version) show the popup for that release.
        // (Superseded by VISAGE_UI_STATE=whats-new for a quick look; kept working.)
        (System.getProperty("visage.version")?.trim()?.takeIf { it.isNotEmpty() && it != "0.0.0-dev" }
            ?: System.getenv("VISAGE_WHATSNEW_PREVIEW"))?.let { SemVer.parseOrNull(it) }
    }

    // VISAGE_UI_STATE=whats-new: show the newest release now, touch no prefs (dismissing it does not mark it seen).
    val forced = com.music.vivi.compose.dev.DevUiState.has("whats-new")
    LaunchedEffect(Unit) {
        if (forced) {
            val newest = WhatsNewCatalog.releases.sortedByDescending { SemVer.parseOrNull(it.version) }
            if (newest.isNotEmpty()) {
                delay(500)
                releases = newest.take(1)
                visible = true
            }
            return@LaunchedEffect
        }
        if (current == null) return@LaunchedEffect
        val last = prefs.getString(WhatsNew.KEY_LAST_SEEN, "").let { SemVer.parseOrNull(it) }
            ?: SemVer.parseOrNull(WhatsNew.BASELINE)!!
        val due = WhatsNew.pending(last, current)
        if (due.isEmpty()) {
            prefs.setString(WhatsNew.KEY_LAST_SEEN, current.toString())
            return@LaunchedEffect
        }
        delay(2_000)
        releases = due
        visible = true
    }

    val signal = WhatsNew.reopenSignal.value
    LaunchedEffect(signal) {
        if (signal == 0) return@LaunchedEffect
        releases = WhatsNewCatalog.releases.sortedByDescending { SemVer.parseOrNull(it.version) }
        visible = true
    }

    if (releases.isNotEmpty()) {
        WhatsNewDialog(
            visible = visible,
            releases = releases,
            onDismiss = {
                visible = false
                if (!forced) current?.let { prefs.setString(WhatsNew.KEY_LAST_SEEN, it.toString()) }
            },
        )
    }
}
