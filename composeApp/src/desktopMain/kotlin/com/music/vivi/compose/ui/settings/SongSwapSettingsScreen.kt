package com.music.vivi.compose.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.reader
import com.music.vivi.compose.ui.player.swap.SongSwapConfig
import com.music.vivi.compose.ui.player.swap.SongSwapKeys
import com.music.vivi.compose.ui.player.swap.SongSwapMode
import com.music.vivi.compose.ui.player.swap.SongSwapPreviewScene
import com.music.vivi.compose.ui.player.swap.SwapDirection
import com.music.vivi.compose.ui.player.swap.rememberSongSwapPreviewState
import com.music.vivi.compose.ui.settings.preview.SettingsPreviewFrame
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import org.koin.compose.koinInject

@Composable
fun SongSwapSettingsScreen(navController: NavController) {
    val prefs = koinInject<DesktopPrefs>()
    @Suppress("UNUSED_VARIABLE")
    val observe = rememberPrefsVersion(prefs)
    val cfg = SongSwapConfig.resolve(prefs.reader())

    val swapPreview = rememberSongSwapPreviewState(cfg)

    SettingsPage(title = "Song swappings", onBack = { navController.navigateUp() }) {
        SettingsPreviewFrame(
            pageKey = "songSwap",
            caption = "Sample songs",
            virtualWidth = 640.dp,
            virtualHeight = 360.dp,
            maxWindowWidth = 480.dp,
            actions = {
                TextButton(onClick = { swapPreview.slowLoad = !swapPreview.slowLoad }) {
                    Text(if (swapPreview.slowLoad) "Slow load: on" else "Slow load: off")
                }
                OutlinedButton(onClick = { swapPreview.play(SwapDirection.PREVIOUS) }) { Text("Previous") }
                FilledTonalButton(onClick = { swapPreview.play(SwapDirection.NEXT) }) { Text("Play next") }
            },
        ) { SongSwapPreviewScene(swapPreview) }

        SettingsSection("Transition") {
            SettingsDropdownRow(
                icon = Icons.Outlined.OpenWith,
                title = "Swap style",
                options = SongSwapMode.entries.map { DropdownOption(it, it.label, hint = it.description) },
                selected = cfg.mode,
                onSelect = { prefs.setString(SongSwapKeys.Mode, it.name) },
            )
            SettingsDivider()
            LiveSlider(
                "Speed", cfg.speed * 100f, 50f..200f, 14, { "${(it / 100f * 10).toInt() / 10f}x".replace(".0x", "x") },
                { prefs.setInt(SongSwapKeys.Speed, it.toInt()) }, { prefs.setInt(SongSwapKeys.Speed, it.toInt()) },
                icon = Icons.Outlined.Speed, default = 100f,
            )
            SettingsDivider()
            LiveSlider(
                "Intensity", cfg.intensity * 100f, 25f..150f, 24, { "${it.toInt()}%" },
                { prefs.setInt(SongSwapKeys.Intensity, it.toInt()) }, { prefs.setInt(SongSwapKeys.Intensity, it.toInt()) },
                icon = Icons.Outlined.Tune, default = 100f,
            )
        }
        SettingsSection("Layers") {
            SwitchItem(
                icon = Icons.Outlined.Wallpaper,
                title = "Also animate background",
                subtitle = "Parallax / dissolve the player background with the cover",
                checked = cfg.animateBackground,
                onChecked = { prefs.setBoolean(SongSwapKeys.AnimateBackground, it) },
            )
            SettingsDivider()
            SwitchItem(
                icon = Icons.Outlined.Tune,
                title = "Also animate controls",
                subtitle = "Title, artist, seek bar and buttons travel with the cover",
                checked = cfg.animateControls,
                onChecked = { prefs.setBoolean(SongSwapKeys.AnimateControls, it) },
            )
            SettingsDivider()
            SwitchItem(
                icon = Icons.Outlined.Contrast,
                title = "Grayscale while loading",
                subtitle = "Camera pan hovers in black and white until the next song is ready",
                checked = cfg.grayscaleWhileLoading,
                onChecked = { prefs.setBoolean(SongSwapKeys.GrayscaleWhileLoading, it) },
            )
        }
        SettingsSection("Reset") {
            SettingsRowFrame(
                icon = Icons.Outlined.Restore,
                title = "Reset song swappings",
                subtitle = "Back to Slide at normal speed",
                onClick = {
                    prefs.setString(SongSwapKeys.Mode, SongSwapMode.Default.name)
                    prefs.setInt(SongSwapKeys.Speed, 100)
                    prefs.setInt(SongSwapKeys.Intensity, 100)
                    prefs.setBoolean(SongSwapKeys.AnimateBackground, true)
                    prefs.setBoolean(SongSwapKeys.AnimateControls, true)
                    prefs.setBoolean(SongSwapKeys.GrayscaleWhileLoading, true)
                },
            )
        }
    }
}
