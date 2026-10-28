package com.music.vivi.compose.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.MovieFilter
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.FullscreenCanvasMode
import com.music.vivi.compose.prefs.FullscreenCoverShape
import com.music.vivi.compose.prefs.FullscreenCoverSide
import com.music.vivi.compose.prefs.FullscreenFollow
import com.music.vivi.compose.prefs.FullscreenKeys
import com.music.vivi.compose.prefs.FullscreenSettings
import com.music.vivi.compose.prefs.PlayerBackgroundStyle
import com.music.vivi.compose.prefs.reader
import com.music.vivi.compose.prefs.resetFullscreenPrefs
import com.music.vivi.compose.ui.player.FullscreenPlayerPreview
import org.koin.compose.koinInject

private fun PlayerBackgroundStyle.fsLabel(): String = when (this) {
    PlayerBackgroundStyle.DEFAULT -> "Solid"
    PlayerBackgroundStyle.GRADIENT -> "Gradient"
    PlayerBackgroundStyle.BLUR -> "Blur"
    PlayerBackgroundStyle.GLOW_ANIMATED -> "Glow"
    PlayerBackgroundStyle.APPLE_MUSIC -> "Apple Music"
    PlayerBackgroundStyle.LIVE_MESH -> "Live mesh"
    PlayerBackgroundStyle.FLUID -> "Fluid"
    PlayerBackgroundStyle.WAVE -> "Wave"
    PlayerBackgroundStyle.XMB -> "XMB"
    PlayerBackgroundStyle.DISTORT -> "Distort"
    PlayerBackgroundStyle.RINGS -> "Rings"
}

@Composable
fun FullscreenPlayerSettingsScreen(navController: NavController) {
    val prefs = koinInject<DesktopPrefs>()
    val version = rememberPrefsVersion(prefs)
    val cfg = remember(version) { FullscreenSettings.resolve(prefs.reader()) }
    val bgOverride = prefs.getString(FullscreenKeys.Background, FullscreenFollow)
    val canvasMode = prefs.enumPref(FullscreenKeys.CanvasMode, FullscreenCanvasMode.FOLLOW)
    var confirmReset by remember { mutableStateOf(false) }

    PreviewSettingsPage(
        title = "Fullscreen player",
        onBack = { navController.navigateUp() },
        previewKey = "settingsPreviewFullscreenOpen",
        previewCaption = "Sample track, 16:9",
        previewHeight = 0.dp,
        preview = {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                FullscreenPlayerPreview(Modifier.widthIn(max = 560.dp))
            }
        },
        sized = false,
    ) {
        SettingsSection("Layout") {
            ChoiceRow(
                icon = Icons.Outlined.OpenInFull,
                title = "Cover position",
                options = listOf(FullscreenCoverSide.LEFT to "Left", FullscreenCoverSide.RIGHT to "Right"),
                selected = cfg.coverSide,
                onSelect = { prefs.setString(FullscreenKeys.CoverSide, it.name) },
            )
            SettingsDivider()
            ChoiceRow(
                icon = Icons.Outlined.Contrast,
                title = "Cover shape",
                options = listOf(
                    FullscreenCoverShape.ROUNDED to "Rounded",
                    FullscreenCoverShape.SOFT to "Soft",
                    FullscreenCoverShape.SQUARE to "Square",
                    FullscreenCoverShape.CIRCLE to "Circle",
                ),
                selected = cfg.coverShape,
                onSelect = { prefs.setString(FullscreenKeys.CoverShape, it.name) },
            )
            SettingsDivider()
            val size = cfg.coverScale * 100f
            LiveSlider(
                "Cover size", size, 50f..100f, 9, { "${it.toInt()}%" },
                { prefs.setInt(FullscreenKeys.CoverScale, it.toInt()) }, { prefs.setInt(FullscreenKeys.CoverScale, it.toInt()) },
                icon = Icons.Outlined.AspectRatio, default = 100f,
            )
            SettingsDivider()
            SwitchItem(
                icon = Icons.Outlined.Lyrics,
                title = "Lyrics on open",
                subtitle = "Lyrics beside the player on first open; later it reopens as you left it",
                checked = cfg.lyricsByDefault,
                onChecked = { prefs.setBoolean(FullscreenKeys.LyricsByDefault, it) },
            )
        }

        SettingsSection("Track info") {
            SwitchItem(Icons.Outlined.Person, "Artist links", "Artist names open the artist page", cfg.showArtist) { prefs.setBoolean(FullscreenKeys.ShowArtist, it) }
            SettingsDivider()
            SwitchItem(Icons.Outlined.Album, "Album", "Album name under the artist", cfg.showAlbum) { prefs.setBoolean(FullscreenKeys.ShowAlbum, it) }
            SettingsDivider()
            SwitchItem(Icons.Outlined.AccessTime, "Clock", "Time of day at the top", cfg.showClock) { prefs.setBoolean(FullscreenKeys.ShowClock, it) }
        }

        SettingsSection("Controls") {
            SwitchItem(Icons.Outlined.Schedule, "Time labels", "Elapsed and total time under the seek bar", cfg.showTimes) { prefs.setBoolean(FullscreenKeys.ShowTimes, it) }
            SettingsDivider()
            SwitchItem(Icons.Outlined.Shuffle, "Shuffle button", null, cfg.showShuffle) { prefs.setBoolean(FullscreenKeys.ShowShuffle, it) }
            SettingsDivider()
            SwitchItem(Icons.Outlined.Repeat, "Repeat button", null, cfg.showRepeat) { prefs.setBoolean(FullscreenKeys.ShowRepeat, it) }
            SettingsDivider()
            SwitchItem(Icons.Outlined.Radio, "Radio button", "Start a radio from the current track", cfg.showRadio) { prefs.setBoolean(FullscreenKeys.ShowRadio, it) }
            SettingsDivider()
            SwitchItem(Icons.AutoMirrored.Outlined.QueueMusic, "Queue button", "In the top bar", cfg.showQueueButton) { prefs.setBoolean(FullscreenKeys.ShowQueueButton, it) }
            SettingsDivider()
            SwitchItem(Icons.AutoMirrored.Outlined.VolumeUp, "Volume slider", null, cfg.showVolume) { prefs.setBoolean(FullscreenKeys.ShowVolume, it) }
        }

        SettingsSection("Background") {
            SettingsDropdownRow(
                Icons.Outlined.Wallpaper, "Fullscreen background",
                options = listOf(DropdownOption(FullscreenFollow, "Follow Now Playing", hint = "Uses the Now Playing background")) +
                    PlayerBackgroundStyle.entries.map { DropdownOption(it.name, it.fsLabel()) },
                selected = if (bgOverride == FullscreenFollow || PlayerBackgroundStyle.entries.any { it.name == bgOverride }) bgOverride else FullscreenFollow,
            ) { prefs.setString(FullscreenKeys.Background, it) }
            AnimatedVisibility(bgOverride == PlayerBackgroundStyle.RINGS.name, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column { RingsControls(prefs) }
            }
            SettingsDivider()
            ChoiceRow(
                icon = Icons.Outlined.MovieFilter,
                title = "Canvas video",
                options = listOf(
                    FullscreenCanvasMode.FOLLOW to "Follow",
                    FullscreenCanvasMode.ON to "On",
                    FullscreenCanvasMode.OFF to "Off",
                ),
                selected = canvasMode,
                onSelect = { prefs.setString(FullscreenKeys.CanvasMode, it.name) },
            )
        }

        SettingsSection("Auto-hide") {
            SwitchItem(
                icon = Icons.Outlined.Tune,
                title = "Auto-hide controls",
                subtitle = "Fade the controls and cursor after the mouse is still",
                checked = cfg.autoHide,
                onChecked = { prefs.setBoolean(FullscreenKeys.AutoHide, it) },
            )
            AnimatedVisibility(cfg.autoHide, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column {
                    SettingsDivider()
                    LiveSlider(
                        "Hide after", cfg.autoHideSeconds.toFloat(), 2f..30f, 13, { "${it.toInt()} s" },
                        { prefs.setInt(FullscreenKeys.AutoHideSeconds, it.toInt()) }, { prefs.setInt(FullscreenKeys.AutoHideSeconds, it.toInt()) },
                        icon = Icons.Outlined.Timer, default = 5f,
                    )
                }
            }
        }

        SettingsSection("Reset") {
            SettingsRowFrame(
                Icons.Outlined.Restore, "Follow Now Playing again",
                "Clears every fullscreen override on this page", onClick = { confirmReset = true },
            )
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset fullscreen settings?") },
            text = { Text("Every setting on this page goes back to its default and the background follows Now Playing again.") },
            confirmButton = { TextButton(onClick = { prefs.resetFullscreenPrefs(); confirmReset = false }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}
