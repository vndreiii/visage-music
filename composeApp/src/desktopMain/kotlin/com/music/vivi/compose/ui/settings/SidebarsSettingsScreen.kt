package com.music.vivi.compose.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.ViewSidebar
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.LibrarySidebarPages
import com.music.vivi.compose.prefs.LibrarySidebarState
import com.music.vivi.compose.prefs.SidebarConfig
import com.music.vivi.compose.prefs.SidebarKeys
import com.music.vivi.compose.prefs.SidebarSide
import com.music.vivi.compose.prefs.reader
import com.music.vivi.compose.ui.expressive.ConnectedButtonGroup
import com.music.vivi.compose.ui.motion.VisageMotion
import com.music.vivi.compose.ui.settings.preview.PreviewPage
import com.music.vivi.compose.ui.settings.preview.SettingsPreviewFrame
import com.music.vivi.compose.ui.settings.preview.SidebarsPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun SidebarsSettingsScreen(navController: NavController) {
    val prefs = koinInject<DesktopPrefs>()
    @Suppress("UNUSED_VARIABLE")
    val observe = rememberPrefsVersion(prefs)
    val cfg = SidebarConfig.resolve(prefs.reader())
    val repo = koinInject<com.music.vivi.database.LibraryRepository>()
    val scope = rememberCoroutineScope()
    var resetDone by remember { mutableStateOf(false) }
    var previewPage by remember { mutableStateOf(PreviewPage.HOME) }
    var previewQueue by remember { mutableStateOf(true) }

    SettingsPage(title = "Sidebars", onBack = { navController.navigateUp() }) {
        // The real sidebars around a sample page; follows every control below instantly.
        SettingsPreviewFrame(
            pageKey = "sidebars",
            caption = "Real sidebars with sample playlists. Switch the page or the queue to see when each sidebar shows",
            actions = {
                ConnectedButtonGroup(
                    options = PreviewPage.entries.map { it to it.label },
                    selected = previewPage,
                    onSelect = { previewPage = it },
                )
                ConnectedButtonGroup(
                    options = listOf(true to "Queue open", false to "Queue closed"),
                    selected = previewQueue,
                    onSelect = { previewQueue = it },
                )
            },
        ) { SidebarsPreview(previewPage, previewQueue) }

        SettingsSection("Library") {
            SwitchItem(
                icon = Icons.Outlined.LibraryMusic,
                title = "Library sidebar",
                subtitle = "Your playlists, albums and folders beside the page. Ctrl+Shift+L shows or hides it",
                checked = cfg.libraryEnabled,
                onChecked = { prefs.setBoolean(SidebarKeys.LibraryEnabled, it) },
            )
            AnimatedVisibility(
                visible = cfg.libraryEnabled,
                enter = fadeIn(VisageMotion.fadeInSpec()) + expandVertically(VisageMotion.glide()),
                exit = fadeOut(VisageMotion.fadeOutSpec()) + shrinkVertically(VisageMotion.glide()),
            ) {
                Column {
                    SettingsDivider()
                    ChoiceRow(
                        icon = Icons.Outlined.OpenWith,
                        title = "Library side",
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
                    // Slider row: value in the supporting text, drag applies live, reset when off the default.
                    var dragWidth by remember(cfg.libraryWidthDp) { mutableFloatStateOf(cfg.libraryWidthDp.toFloat()) }
                    SettingsRowFrame(
                        Icons.Outlined.OpenInFull, "Library width", "${dragWidth.toInt()} dp when expanded", onClick = null,
                        belowSubtitle = {
                            Slider(
                                value = dragWidth,
                                onValueChange = { dragWidth = it; prefs.setInt(SidebarKeys.LibraryWidth, it.toInt()) },
                                valueRange = SidebarConfig.MinLibraryWidth.toFloat()..SidebarConfig.MaxLibraryWidth.toFloat(),
                                steps = 10,
                            )
                        },
                        trailing = {
                            if (cfg.libraryWidthDp != SidebarConfig.DefaultLibraryWidth) {
                                IconButton(onClick = { prefs.setInt(SidebarKeys.LibraryWidth, SidebarConfig.DefaultLibraryWidth) }) {
                                    Icon(Icons.Outlined.RestartAlt, "Reset to default", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
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
                        title = "Hidden items",
                        subtitle = if (resetDone) "All hidden items are visible again" else "Bring back everything you hid from the sidebar. Nothing was deleted",
                        onClick = {
                            scope.launch(Dispatchers.IO) { runCatching { repo.resetHidden() } }
                            prefs.setBoolean(SidebarKeys.LibraryShowHidden, false)
                            resetDone = true
                        },
                    )
                }
            }
        }

        SettingsSection("Queue") {
            ChoiceRow(
                icon = Icons.Outlined.QueueMusic,
                title = "Queue side",
                options = listOf(SidebarSide.LEFT to "Left", SidebarSide.RIGHT to "Right"),
                selected = cfg.queueSide,
                onSelect = { prefs.setString(SidebarKeys.QueueSide, it.name) },
            )
            SettingsDivider()
            SwitchItem(
                icon = Icons.Outlined.Dashboard,
                title = "Queue on Home",
                subtitle = "Open the queue sidebar on Home next to the library. Both can share a side",
                checked = cfg.queueOnHome,
                onChecked = { prefs.setBoolean(SidebarKeys.QueueOnHome, it) },
            )
        }
    }
}
