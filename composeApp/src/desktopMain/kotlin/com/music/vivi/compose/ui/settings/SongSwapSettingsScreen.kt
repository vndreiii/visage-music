package com.music.vivi.compose.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
import com.music.vivi.compose.ui.player.swap.SongSwapPreview
import org.koin.compose.koinInject

@Composable
fun SongSwapSettingsScreen(navController: NavController) {
    val prefs = koinInject<DesktopPrefs>()
    @Suppress("UNUSED_VARIABLE")
    val observe = rememberPrefsVersion(prefs)
    val cfg = SongSwapConfig.resolve(prefs.reader())

    SettingsPage(title = "Song swappings", onBack = { navController.navigateUp() }) {
        SettingsSection("Preview") {
            Column(Modifier.padding(16.dp)) {
                SongSwapPreview(cfg)
                Text(
                    cfg.mode.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        SettingsSection("Transition") {
            ChipChoiceRow(
                icon = Icons.Outlined.OpenWith,
                title = "Style",
                options = SongSwapMode.entries.map { it to it.label },
                selected = cfg.mode,
                onSelect = { prefs.setString(SongSwapKeys.Mode, it.name) },
            )
            SettingsDivider()
            PrefSecondsSlider(
                title = "Speed",
                value = (cfg.speed * 100f),
                valueRange = 50f..200f,
                steps = 14,
                valueLabel = { "${(it / 100f * 10).toInt() / 10f}x".replace(".0x", "x") },
                onCommit = { prefs.setInt(SongSwapKeys.Speed, it.toInt()) },
            )
            PrefSecondsSlider(
                title = "Intensity",
                value = (cfg.intensity * 100f),
                valueRange = 25f..150f,
                steps = 24,
                valueLabel = { "${it.toInt()}%" },
                onCommit = { prefs.setInt(SongSwapKeys.Intensity, it.toInt()) },
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
            SettingsNavItem(
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
