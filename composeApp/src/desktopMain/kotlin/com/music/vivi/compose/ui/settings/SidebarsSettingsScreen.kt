package com.music.vivi.compose.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.ViewSidebar
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.LibrarySidebarPages
import com.music.vivi.compose.prefs.LibrarySidebarState
import com.music.vivi.compose.prefs.SidebarConfig
import com.music.vivi.compose.prefs.SidebarKeys
import com.music.vivi.compose.prefs.SidebarSide
import com.music.vivi.compose.prefs.reader
import org.koin.compose.koinInject

@Composable
fun SidebarsSettingsScreen(navController: NavController) {
    val prefs = koinInject<DesktopPrefs>()
    @Suppress("UNUSED_VARIABLE")
    val observe = rememberPrefsVersion(prefs)
    val cfg = SidebarConfig.resolve(prefs.reader())
    val repo = koinInject<com.music.vivi.database.LibraryRepository>()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var resetDone by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    SettingsPage(title = "Sidebars", onBack = { navController.navigateUp() }) {
        SettingsSection("Library sidebar") {
            SwitchItem(
                icon = Icons.Outlined.LibraryMusic,
                title = "Library sidebar",
                subtitle = "Your YouTube Music and custom playlists, Spotify-style (Ctrl+Shift+L toggles it)",
                checked = cfg.libraryEnabled,
                onChecked = { prefs.setBoolean(SidebarKeys.LibraryEnabled, it) },
            )
            SettingsDivider()
            ChoiceRow(
                icon = Icons.Outlined.OpenWith,
                title = "Side",
                options = listOf(SidebarSide.LEFT to "Left", SidebarSide.RIGHT to "Right"),
                selected = cfg.librarySide,
                onSelect = { prefs.setString(SidebarKeys.LibrarySide, it.name) },
            )
            SettingsDivider()
            ChoiceRow(
                icon = Icons.Outlined.ViewSidebar,
                title = "Default state",
                options = listOf(
                    LibrarySidebarState.EXPANDED to "Expanded",
                    LibrarySidebarState.RAIL to "Rail",
                    LibrarySidebarState.HIDDEN to "Hidden",
                ),
                selected = cfg.libraryDefaultState,
                onSelect = {
                    prefs.setString(SidebarKeys.LibraryDefaultState, it.name)
                    // Apply right away; afterwards the sidebar remembers how you left it.
                    prefs.setString(SidebarKeys.LibraryState, it.name)
                },
            )
            SettingsDivider()
            ChoiceRow(
                icon = Icons.Outlined.Dashboard,
                title = "Show on",
                options = listOf(
                    LibrarySidebarPages.ALL_EXCEPT_PLAYER_SETTINGS to "Everywhere",
                    LibrarySidebarPages.HOME_ONLY to "Home only",
                ),
                selected = cfg.libraryPages,
                onSelect = { prefs.setString(SidebarKeys.LibraryPages, it.name) },
            )
            SettingsDivider()
            PrefSecondsSlider(
                title = "Width",
                value = cfg.libraryWidthDp.toFloat(),
                valueRange = SidebarConfig.MinLibraryWidth.toFloat()..SidebarConfig.MaxLibraryWidth.toFloat(),
                steps = 10,
                valueLabel = { "${it.toInt()} dp" },
                onCommit = { prefs.setInt(SidebarKeys.LibraryWidth, it.toInt()) },
            )
            SettingsDivider()
            SwitchItem(
                icon = Icons.Outlined.Folder,
                title = "Folders first",
                subtitle = "Pin folders above your other items. Off mixes them in with the sort order",
                checked = prefs.getBoolean(SidebarKeys.LibraryFoldersFirst, true),
                onChecked = { prefs.setBoolean(SidebarKeys.LibraryFoldersFirst, it) },
            )
            SettingsDivider()
            SettingsNavItem(
                icon = Icons.Outlined.VisibilityOff,
                title = "Reset hidden items",
                subtitle = if (resetDone) "All hidden items are visible again" else "Bring back everything you hid from the sidebar (nothing was deleted)",
                onClick = {
                    scope.launch(Dispatchers.IO) { runCatching { repo.resetHidden() } }
                    prefs.setBoolean(SidebarKeys.LibraryShowHidden, false)
                    resetDone = true
                },
            )
        }

        SettingsSection("Queue sidebar") {
            ChoiceRow(
                icon = Icons.Outlined.QueueMusic,
                title = "Side",
                options = listOf(SidebarSide.LEFT to "Left", SidebarSide.RIGHT to "Right"),
                selected = cfg.queueSide,
                onSelect = { prefs.setString(SidebarKeys.QueueSide, it.name) },
            )
            SettingsDivider()
            SwitchItem(
                icon = Icons.Outlined.Dashboard,
                title = "Show on Home",
                subtitle = "Open the queue sidebar on Home alongside the library. Combine freely: queue left + library right, or both on one side",
                checked = cfg.queueOnHome,
                onChecked = { prefs.setBoolean(SidebarKeys.QueueOnHome, it) },
            )
        }
    }
}
