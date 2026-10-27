package com.music.vivi.compose.ui.onboarding

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.ui.graphics.vector.ImageVector
import com.music.vivi.compose.navigation.Routes
import com.music.vivi.compose.prefs.LibrarySidebarState

/*
 * The tour is data. To add something new to onboarding:
 *  1. Tag the UI element to spotlight with `Modifier.tourAnchor("some.key")` (skip for a centred card).
 *  2. Add a [TourStepDef] to [TourCatalog.steps], at the position it should appear.
 *     - `anchor`: the key from step 1, or null.
 *     - `enter`: what the real app should do first (navigate, open the queue or library sidebar). Add a new
 *       [TourCommand] subtype and handle it in AppScaffold if a step needs something else.
 *     - `since`: the app version that introduced it.
 *  3. Add the row to docs/dev-ui-states.md only if it needs a new dev flag. Nothing else to wire.
 */

class TourStepDef(
    val title: String,
    val body: String,
    val anchor: String?,
    val icon: ImageVector,
    /** What the real app does on entering this step, so the thing described is on screen. */
    val enter: List<TourCommand> = emptyList(),
    /** App version that introduced this step; lets a future "what's new" tour show only newer steps. */
    val since: String = "1.3.0",
)

object TourCatalog {
    val steps: List<TourStepDef> = listOf(
        TourStepDef(
            "Welcome to VIsage",
            "A 1 minute tour. Each step opens the real screen it talks about, so you see where everything lives. Use Next, the arrow keys, or Skip any time.",
            null, Icons.Outlined.AutoAwesome,
            listOf(TourCommand.Navigate(Routes.HOME), TourCommand.Library(LibrarySidebarState.HIDDEN), TourCommand.Queue(false)),
        ),
        TourStepDef(
            "Home",
            "Your daily feed: mixes, quick picks and new releases from YouTube Music. Click any artist name to open their page.",
            "nav.home", Icons.Outlined.Home,
            listOf(TourCommand.Navigate(Routes.HOME)),
        ),
        TourStepDef(
            "Search",
            "Find songs, albums, artists and playlists, or paste a YouTube link to play it right away.",
            "nav.search", Icons.Outlined.Search,
        ),
        TourStepDef(
            "Your library sidebar",
            "Your playlists, albums and music folders, always one click away. Click an entry to open it, right-click for more options. Ctrl+Shift+L shows or hides it, and the top icon shrinks it to a slim rail.",
            "library.sidebar", Icons.Outlined.LibraryMusic,
            listOf(TourCommand.Navigate(Routes.HOME), TourCommand.Library(LibrarySidebarState.EXPANDED)),
        ),
        TourStepDef(
            "Create playlists and folders",
            "Press + to make a new playlist or a folder. Folders keep your playlists tidy, and you can drag songs from the queue or the player straight onto a playlist or folder to add them.",
            "library.add", Icons.Outlined.CreateNewFolder,
        ),
        TourStepDef(
            "Library",
            "The full page: your liked songs, local music, downloads and recent plays. Add your music folders in Settings, then search and filter everything from here.",
            "nav.library", Icons.Outlined.LibraryMusic,
            listOf(TourCommand.Navigate(Routes.LIBRARY)),
        ),
        TourStepDef(
            "Tag Manager",
            "Messy tags on your local files? Tag Manager fixes titles, artists and covers in bulk. Open it from here.",
            "library.tagmanager", Icons.Outlined.Sell,
        ),
        TourStepDef(
            "Inside the Tag Manager",
            "Drag to select songs, right-click for play, queue or open folder, then run auto-tag. You review every proposed change before anything is written, and you can type your own values.",
            null, Icons.Outlined.Sell,
            listOf(TourCommand.Navigate(Routes.TAG_MANAGER)),
        ),
        TourStepDef(
            "The queue",
            "What plays next. Drag a row to reorder it, or pull it sideways and drop it on a playlist to add it. It opens as a sidebar, and the arrow icon makes it fullscreen.",
            "queue.panel", Icons.Outlined.QueueMusic,
            listOf(TourCommand.Navigate(Routes.HOME), TourCommand.Library(LibrarySidebarState.RAIL), TourCommand.Queue(true)),
        ),
        TourStepDef(
            "The player bar",
            "Whatever is playing lives down here. Click it to open the full Now Playing page.",
            "miniplayer", Icons.Outlined.PlayCircle,
            listOf(TourCommand.Queue(false)),
        ),
        TourStepDef(
            "Now Playing",
            "The big player: cover art, synced lyrics with 3D scenes, volume and audio output, and the queue beside it. The expand button goes fullscreen.",
            null, Icons.Outlined.PlayCircle,
            listOf(TourCommand.Navigate(Routes.NOW_PLAYING)),
        ),
        TourStepDef(
            "Settings",
            "This is where VIsage becomes yours. The gear in the top bar opens it any time.",
            "nav.settings", Icons.Outlined.Settings,
            listOf(TourCommand.Navigate(Routes.SETTINGS)),
        ),
        TourStepDef(
            "Make it yours",
            "Themes and colours, fonts, glass blur, player and mini-player styles, lyric scenes, sidebar sides, keyboard shortcuts, local folders and more. Explore every page, nothing here is permanent.",
            null, Icons.Outlined.Palette,
            listOf(TourCommand.Navigate(Routes.SETTINGS_APPEARANCE)),
        ),
        TourStepDef(
            "You are set",
            "Enjoy the music. You can replay this tour any time from Settings, About, Replay tour.",
            null, Icons.Outlined.TaskAlt,
            listOf(TourCommand.Navigate(Routes.HOME), TourCommand.Library(LibrarySidebarState.EXPANDED)),
        ),
    )

    /** Steps added after [version], for a short "new since you last looked" tour. */
    fun stepsSince(version: String): List<TourStepDef> {
        val v = com.music.vivi.compose.update.SemVer.parseOrNull(version) ?: return steps
        return steps.filter { st -> com.music.vivi.compose.update.SemVer.parseOrNull(st.since)?.let { it > v } == true }
    }
}
