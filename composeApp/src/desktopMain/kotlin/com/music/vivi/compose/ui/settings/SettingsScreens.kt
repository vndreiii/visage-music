package com.music.vivi.compose.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MovieFilter
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.materialkolor.dynamicColorScheme
import com.music.innertube.models.AccountInfo
import com.music.vivi.compose.navigation.Routes
import com.music.vivi.compose.prefs.AudioQuality
import com.music.vivi.compose.prefs.DarkMode
import com.music.vivi.compose.prefs.DefaultThemeColorArgb
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.DownloadAudioFormat
import com.music.vivi.compose.prefs.DownloadAudioQuality
import com.music.vivi.compose.prefs.LyricsProvider
import com.music.vivi.compose.prefs.LyricsTextAlign
import com.music.vivi.compose.prefs.M3PaletteStyle
import com.music.vivi.compose.prefs.MiniPlayerStyle
import com.music.vivi.compose.prefs.PlayerBackgroundStyle
import com.music.vivi.compose.prefs.PlayerButtonsStyle
import com.music.vivi.compose.prefs.PrefKeys
import com.music.vivi.compose.prefs.QueuePanelMode
import com.music.vivi.compose.prefs.SliderStyle
import com.music.vivi.compose.prefs.TopNavStyle
import com.music.vivi.compose.prefs.VolumeControlStyle
import com.music.vivi.compose.theme.MatugenColors
import com.music.vivi.compose.theme.label
import com.music.vivi.compose.theme.toKolor
import com.music.vivi.compose.ui.component.InteractionCornerRadius
import com.music.vivi.compose.ui.component.smoothWheelScroll
import com.music.vivi.compose.ui.player.canvas.CanvasSource
import com.music.vivi.compose.ui.resolveMiniPlayerStyle
import com.music.vivi.core.youtube.YouTubeMusicService
import com.music.vivi.database.DatabaseFactory
import com.music.vivi.player.stream.YtDlpBinary
import org.koin.compose.koinInject
import kotlinx.coroutines.launch

/** Forces recomposition of the *caller* when prefs change (not just a child). */
@Composable
internal fun rememberPrefsVersion(prefs: DesktopPrefs): Int {
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(prefs) {
        val listener = { version += 1 }
        prefs.addListener(listener)
        onDispose { prefs.removeListener(listener) }
    }
    return version
}

private inline fun <reified T : Enum<T>> DesktopPrefs.enumPref(key: String, default: T): T =
    runCatching { enumValueOf<T>(getString(key, default.name)) }.getOrDefault(default)

private fun Enum<*>.titleLabel(): String =
    name.lowercase().split('_').joinToString(" ").replaceFirstChar { it.titlecase() }

private fun PlayerBackgroundStyle.label(): String = when (this) {
    PlayerBackgroundStyle.DEFAULT -> "Solid"
    PlayerBackgroundStyle.GRADIENT -> "Gradient"
    PlayerBackgroundStyle.BLUR -> "Blur"
    PlayerBackgroundStyle.GLOW_ANIMATED -> "Glow"
    PlayerBackgroundStyle.APPLE_MUSIC -> "Apple Music"
    PlayerBackgroundStyle.LIVE_MESH -> "Live mesh"
    PlayerBackgroundStyle.LEGACY -> "Legacy"
    PlayerBackgroundStyle.FLUID -> "Fluid"
    PlayerBackgroundStyle.WAVE -> "Wave"
    PlayerBackgroundStyle.XMB -> "XMB"
    PlayerBackgroundStyle.DISTORT -> "Distort"
}

// —— Hub ——————————————————————————————————————————————————————

@Composable
fun SettingsHubScreen(navController: NavController) {
    val yt = koinInject<YouTubeMusicService>()
    var account by remember { mutableStateOf<AccountInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(Unit) {
        yt.accountInfo()
            .onSuccess { account = it }
            .onFailure { error = it.message ?: it.toString() }
    }

    SettingsPage(title = "Settings", onBack = null) {
        AccountCard(account, error) { navController.navigate(Routes.SETTINGS_ACCOUNT) }

        SettingsSection("Personalize") {
            SettingsNavItem(
                icon = Icons.Outlined.Palette,
                title = "Theme",
                subtitle = "Light or dark, dynamic color, palette, contrast",
                onClick = { navController.navigate(Routes.SETTINGS_THEME) },
            )
            SettingsDivider()
            SettingsNavItem(
                icon = Icons.Outlined.Dashboard,
                title = "Interface",
                subtitle = "Top bar and mini-player",
                onClick = { navController.navigate(Routes.SETTINGS_APPEARANCE) },
            )
            SettingsDivider()
            SettingsNavItem(
                icon = Icons.Outlined.PlayCircle,
                title = "Now Playing",
                subtitle = "Background, canvas, controls, lyrics, queue",
                onClick = { navController.navigate(Routes.SETTINGS_NOW_PLAYING) },
            )
        }

        SettingsSection("Listening") {
            SettingsNavItem(
                icon = Icons.AutoMirrored.Outlined.VolumeUp,
                title = "Playback & audio",
                subtitle = "Quality, sources, crossfade, Discord",
                onClick = { navController.navigate(Routes.SETTINGS_PLAYER) },
            )
        }

        SettingsSection("Library & storage") {
            SettingsNavItem(
                icon = Icons.Outlined.Download,
                title = "Downloads",
                subtitle = "Folder, format and quality",
                onClick = { navController.navigate(Routes.SETTINGS_DOWNLOADS) },
            )
            SettingsDivider()
            SettingsNavItem(
                icon = Icons.Outlined.Folder,
                title = "Local files",
                subtitle = "Music folders, scanning, offline library",
                onClick = { navController.navigate(Routes.SETTINGS_LOCAL_OFFLINE) },
            )
        }

        Text(
            "VIVI Desktop — Linux port of VIVI Music",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 20.dp),
        )
    }
}

@Composable
private fun AccountCard(account: AccountInfo?, error: String?, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val thumb = account?.thumbnailUrl
            if (thumb != null) {
                AsyncImage(
                    model = thumb,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp).clip(CircleShape),
                )
            } else {
                Icon(
                    Icons.Outlined.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    account?.name ?: if (error != null) "Not signed in" else "Loading account…",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    account?.email ?: "YouTube Music account",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

// —— Theme ————————————————————————————————————————————————————

@Composable
fun ThemeSettingsScreen(navController: NavController) {
    val prefs = koinInject<DesktopPrefs>()
    @Suppress("UNUSED_VARIABLE")
    val observe = rememberPrefsVersion(prefs)

    val darkMode = prefs.enumPref(PrefKeys.DarkMode, DarkMode.AUTO)
    val pureBlack = prefs.getBoolean(PrefKeys.PureBlack, false)
    val colorArgb = prefs.getInt(PrefKeys.SelectedThemeColor, DefaultThemeColorArgb)
    val dynamicTheme = prefs.getBoolean(PrefKeys.DynamicTheme, true)

    SettingsPage(title = "Theme", onBack = { navController.navigateUp() }) {
        SettingsSection("Mode") {
            ChoiceRow(
                icon = Icons.Outlined.Contrast,
                title = "Appearance",
                options = listOf("auto" to "System", "light" to "Light", "dark" to "Dark", "black" to "Black"),
                selected = when {
                    darkMode == DarkMode.ON && pureBlack -> "black"
                    darkMode == DarkMode.ON -> "dark"
                    darkMode == DarkMode.OFF -> "light"
                    else -> "auto"
                },
                onSelect = { key ->
                    when (key) {
                        "auto" -> { prefs.setString(PrefKeys.DarkMode, DarkMode.AUTO.name); prefs.setBoolean(PrefKeys.PureBlack, false) }
                        "light" -> { prefs.setString(PrefKeys.DarkMode, DarkMode.OFF.name); prefs.setBoolean(PrefKeys.PureBlack, false) }
                        "dark" -> { prefs.setString(PrefKeys.DarkMode, DarkMode.ON.name); prefs.setBoolean(PrefKeys.PureBlack, false) }
                        "black" -> {
                            prefs.setString(PrefKeys.DarkMode, DarkMode.ON.name)
                            prefs.setBoolean(PrefKeys.PureBlack, true)
                            prefs.setBoolean(PrefKeys.PureBlackMiniPlayer, true)
                        }
                    }
                },
            )
            SettingsDivider()
            val contrast = prefs.getFloat(PrefKeys.ContrastLevel, 0f)
            ChoiceRow(
                icon = Icons.Outlined.Tune,
                title = "Contrast",
                options = listOf(0f to "Standard", 0.5f to "Medium", 1f to "High"),
                selected = listOf(0f, 0.5f, 1f).minBy { kotlin.math.abs(it - contrast) },
                onSelect = { prefs.setFloat(PrefKeys.ContrastLevel, it) },
            )
        }

        SettingsSection("Color source") {
            SwitchItem(
                icon = Icons.Outlined.ColorLens,
                title = "Dynamic color",
                subtitle = "Tint the app from the cover art that's playing",
                checked = dynamicTheme,
                onChecked = { prefs.setBoolean(PrefKeys.DynamicTheme, it) },
            )
            SettingsDivider()
            val matugenOn = prefs.getBoolean(PrefKeys.MatugenTheme, false)
            val matugenReady = MatugenColors.defaultFile.isFile
            SwitchItem(
                icon = Icons.Outlined.Wallpaper,
                title = "Matugen wallpaper colors",
                subtitle = if (matugenReady) {
                    "Use ~/.cache/matugen/vivi-desktop.colors"
                } else {
                    "Run matugen with the vivi-desktop template first"
                },
                checked = matugenOn,
                onChecked = { prefs.setBoolean(PrefKeys.MatugenTheme, it) },
            )
            AnimatedVisibility(matugenOn && matugenReady, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                val preview = remember(matugenOn, darkMode) {
                    MatugenColors.load()?.let { if (darkMode != DarkMode.OFF) it.dark else it.light }
                }
                if (preview != null) {
                    Row(Modifier.padding(start = 60.dp, end = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        PaletteSwatchMockup(
                            listOfNotNull(
                                preview["primary"], preview["secondary"], preview["tertiary"],
                                preview["primaryContainer"], preview["surfaceContainerHigh"],
                            ),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Live from wallpaper", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        SettingsSection("Palette style") {
            val style = prefs.enumPref(PrefKeys.PaletteStyle, M3PaletteStyle.TONAL_SPOT)
            PaletteGrid(selected = style, seedArgb = colorArgb, dark = darkMode != DarkMode.OFF) {
                prefs.setString(PrefKeys.PaletteStyle, it.name)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaletteGrid(
    selected: M3PaletteStyle,
    seedArgb: Int,
    dark: Boolean,
    onSelect: (M3PaletteStyle) -> Unit,
) {
    FlowRow(
        Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        M3PaletteStyle.entries.forEach { entry ->
            val scheme = remember(entry, seedArgb, dark) {
                dynamicColorScheme(
                    seedColor = Color(seedArgb.toLong() and 0xFFFFFFFFL),
                    isDark = dark,
                    style = entry.toKolor(),
                )
            }
            val isSelected = entry == selected
            val container by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                com.music.vivi.compose.ui.motion.ViviMotion.settle(),
                label = "paletteTile",
            )
            Surface(
                onClick = { onSelect(entry) },
                shape = RoundedCornerShape(16.dp),
                color = container,
                modifier = Modifier.width(148.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    PaletteSwatchMockup(
                        listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.primaryContainer, scheme.surfaceContainerHigh),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        entry.label(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// —— Interface ————————————————————————————————————————————————

@Composable
fun AppearanceSettingsScreen(navController: NavController) {
    val prefs = koinInject<DesktopPrefs>()
    @Suppress("UNUSED_VARIABLE")
    val observe = rememberPrefsVersion(prefs)
    val currentMiniLayout = resolveMiniPlayerStyle(prefs)
    val miniBg = prefs.enumPref(PrefKeys.MiniPlayerBackgroundStyle, PlayerBackgroundStyle.GRADIENT)
    val topNav = prefs.enumPref(PrefKeys.TopNavStyle, TopNavStyle.ICON_ONLY)

    SettingsPage(title = "Interface", onBack = { navController.navigateUp() }) {
        SettingsSection("Top bar") {
            LiveTopNavHeroPreview(style = topNav)
            ChoiceRow(
                icon = Icons.Outlined.Menu,
                title = "Tab labels",
                options = listOf(
                    TopNavStyle.ICON_ONLY to "Icons",
                    TopNavStyle.ICON_AND_TEXT to "Both",
                    TopNavStyle.TEXT_ONLY to "Text",
                ),
                selected = topNav,
                onSelect = { prefs.setString(PrefKeys.TopNavStyle, it.name) },
            )
            SettingsDivider()
            SwitchItem(
                icon = Icons.Outlined.Tune,
                title = "Settings shortcuts menu",
                subtitle = "Open a quick menu from the Settings tab",
                checked = prefs.getBoolean(PrefKeys.SettingsDropdown, true),
                onChecked = { prefs.setBoolean(PrefKeys.SettingsDropdown, it) },
            )
        }

        SettingsSection("Mini-player") {
            LiveMiniPlayerHeroPreview(layout = currentMiniLayout, background = miniBg)
            ChoiceRow(
                icon = Icons.Outlined.SmartDisplay,
                title = "Layout",
                options = listOf(
                    MiniPlayerStyle.FULL_WIDTH_BAR to "Bar",
                    MiniPlayerStyle.FLOATING_PILL to "Pill",
                    MiniPlayerStyle.COMPACT_EDGE to "Compact",
                ),
                selected = currentMiniLayout,
                onSelect = { style ->
                    prefs.setString(PrefKeys.MiniPlayerStyle, style.name)
                    prefs.setBoolean(PrefKeys.UseNewMiniPlayerDesign, style != MiniPlayerStyle.FULL_WIDTH_BAR)
                    prefs.setBoolean(PrefKeys.UseAppleMiniPlayer, style == MiniPlayerStyle.COMPACT_EDGE)
                },
            )
            SettingsDivider()
            ChipChoiceRow(
                icon = Icons.Outlined.Wallpaper,
                title = "Background",
                options = PlayerBackgroundStyle.entries
                    .filter { it != PlayerBackgroundStyle.APPLE_MUSIC }
                    .map { it to it.label() },
                selected = miniBg,
                onSelect = { prefs.setString(PrefKeys.MiniPlayerBackgroundStyle, it.name) },
            )
            SettingsDivider()
            SwitchItem(
                icon = Icons.Outlined.Contrast,
                title = "Pure black mini-player",
                subtitle = null,
                checked = prefs.getBoolean(PrefKeys.PureBlackMiniPlayer, false),
                onChecked = { prefs.setBoolean(PrefKeys.PureBlackMiniPlayer, it) },
            )
        }
    }
}

// —— Now Playing ——————————————————————————————————————————————

@Composable
fun NowPlayingSettingsScreen(navController: NavController) {
    val prefs = koinInject<DesktopPrefs>()
    @Suppress("UNUSED_VARIABLE")
    val observe = rememberPrefsVersion(prefs)
    val playerBg = prefs.enumPref(PrefKeys.PlayerBackgroundStyle, PlayerBackgroundStyle.GRADIENT)
    val canvasOn = prefs.getBoolean(PrefKeys.EnableCanvas, true)

    SettingsPage(title = "Now Playing", onBack = { navController.navigateUp() }) {
        SettingsSection("Background") {
            LivePlayerBackgroundHeroPreview(style = playerBg)
            ChipChoiceRow(
                icon = Icons.Outlined.Wallpaper,
                title = "Style",
                options = PlayerBackgroundStyle.entries.map { it to it.label() },
                selected = playerBg,
                onSelect = { prefs.setString(PrefKeys.PlayerBackgroundStyle, it.name) },
            )
            SettingsDivider()
            SwitchItem(
                icon = Icons.Outlined.MovieFilter,
                title = "Canvas video",
                subtitle = if (playerBg == PlayerBackgroundStyle.APPLE_MUSIC) {
                    "Animated album art in place of the cover when available"
                } else {
                    "Available with the Apple Music background"
                },
                checked = canvasOn,
                onChecked = { prefs.setBoolean(PrefKeys.EnableCanvas, it) },
            )
            AnimatedVisibility(canvasOn && playerBg == PlayerBackgroundStyle.APPLE_MUSIC, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column {
                    SettingsDivider()
                    ChoiceRow(
                        icon = Icons.Outlined.AutoAwesome,
                        title = "Canvas source",
                        options = listOf(
                            CanvasSource.AUTO to "Auto",
                            CanvasSource.APPLE_MUSIC to "Apple",
                            CanvasSource.TIDAL to "Tidal",
                            CanvasSource.VIVIMUSIC to "VIVI",
                        ),
                        selected = prefs.enumPref(PrefKeys.CanvasSource, CanvasSource.AUTO),
                        onSelect = { prefs.setString(PrefKeys.CanvasSource, it.name) },
                    )
                }
            }
        }

        SettingsSection("Layout & controls") {
            LivePlayerLayoutHeroPreview(framed = prefs.getBoolean(PrefKeys.UsePlayerV2, false))
            SwitchItem(
                icon = Icons.Outlined.SmartDisplay,
                title = "Framed controls",
                subtitle = "Put the controls on a translucent panel",
                checked = prefs.getBoolean(PrefKeys.UsePlayerV2, false),
                onChecked = { prefs.setBoolean(PrefKeys.UsePlayerV2, it) },
            )
            SettingsDivider()
            SwitchItem(
                icon = Icons.Outlined.Contrast,
                title = "Hide cover art",
                subtitle = null,
                checked = prefs.getBoolean(PrefKeys.HidePlayerThumbnail, false),
                onChecked = { prefs.setBoolean(PrefKeys.HidePlayerThumbnail, it) },
            )
            SettingsDivider()
            val buttons = prefs.enumPref(PrefKeys.PlayerButtonsStyle, PlayerButtonsStyle.DEFAULT)
            ChoiceRow(
                icon = Icons.Outlined.PlayCircle,
                title = "Button color",
                options = PlayerButtonsStyle.entries.map { it to it.titleLabel() },
                selected = buttons,
                onSelect = { prefs.setString(PrefKeys.PlayerButtonsStyle, it.name) },
            )
            SettingsDivider()
            val slider = prefs.enumPref(PrefKeys.SliderStyle, SliderStyle.DEFAULT)
            LiveSliderStyleHeroPreview(style = slider, squiggly = prefs.getBoolean(PrefKeys.SquigglySlider, false))
            ChipChoiceRow(
                icon = Icons.Outlined.GraphicEq,
                title = "Seek bar",
                options = SliderStyle.entries.map {
                    it to if (it == SliderStyle.M3_EXPRESSIVE) "M3 Expressive" else it.titleLabel()
                },
                selected = slider,
                onSelect = { prefs.setString(PrefKeys.SliderStyle, it.name) },
            )
            SettingsDivider()
            ChoiceRow(
                icon = Icons.AutoMirrored.Outlined.VolumeUp,
                title = "Volume control",
                options = listOf(VolumeControlStyle.SLIDER to "Slider", VolumeControlStyle.COMPACT_PILL to "Pill"),
                selected = prefs.enumPref(PrefKeys.VolumeControlStyle, VolumeControlStyle.SLIDER),
                onSelect = { prefs.setString(PrefKeys.VolumeControlStyle, it.name) },
            )
        }

        SettingsSection("Lyrics") {
            ChoiceRow(
                icon = Icons.Outlined.Lyrics,
                title = "Alignment",
                options = listOf(LyricsTextAlign.LEFT to "Left", LyricsTextAlign.CENTER to "Center", LyricsTextAlign.RIGHT to "Right"),
                selected = prefs.enumPref(PrefKeys.LyricsTextAlign, LyricsTextAlign.CENTER),
                onSelect = { prefs.setString(PrefKeys.LyricsTextAlign, it.name) },
            )
            SettingsDivider()
            ChipChoiceRow(
                icon = Icons.Outlined.AutoAwesome,
                title = "Provider",
                options = LyricsProvider.entries.map { it to it.label },
                selected = prefs.enumPref(PrefKeys.LyricsProvider, LyricsProvider.LRCLIB),
                onSelect = { prefs.setString(PrefKeys.LyricsProvider, it.name) },
            )
        }

        SettingsSection("Queue") {
            ChoiceRow(
                icon = Icons.AutoMirrored.Outlined.QueueMusic,
                title = "Open queue as",
                options = listOf(
                    QueuePanelMode.FULLSCREEN to "Sheet",
                    QueuePanelMode.PINNED_RIGHT to "Side panel",
                    QueuePanelMode.UNDER_PLAYBAR to "Inline",
                ),
                selected = prefs.enumPref(PrefKeys.QueuePanelMode, QueuePanelMode.FULLSCREEN),
                onSelect = { prefs.setString(PrefKeys.QueuePanelMode, it.name) },
            )
            SettingsDivider()
            SwitchItem(
                icon = Icons.Outlined.Restore,
                title = "Remember queue",
                subtitle = "Restore the queue and position at startup",
                checked = prefs.getBoolean(PrefKeys.PersistentQueue, true),
                onChecked = { prefs.setBoolean(PrefKeys.PersistentQueue, it) },
            )
        }
    }
}

// —— Playback ——————————————————————————————————————————————————

@Composable
fun PlayerSettingsScreen(navController: NavController) {
    val prefs = koinInject<DesktopPrefs>()
    @Suppress("UNUSED_VARIABLE")
    val observe = rememberPrefsVersion(prefs)
    val saavn = prefs.getBoolean(PrefKeys.SaavnEnabled, System.getenv("VIVI_SAAVN") != "0")

    SettingsPage(title = "Playback & audio", onBack = { navController.navigateUp() }) {
        SettingsSection("Stream") {
            ChoiceRow(
                icon = Icons.Outlined.HighQuality,
                title = "Audio quality",
                options = AudioQuality.entries.map { it to it.titleLabel() },
                selected = prefs.enumPref(PrefKeys.AudioQuality, AudioQuality.AUTO),
                onSelect = { prefs.setString(PrefKeys.AudioQuality, it.name) },
            )
            SettingsDivider()
            SwitchItem(
                icon = Icons.AutoMirrored.Outlined.VolumeUp,
                title = "Prefer JioSaavn",
                subtitle = "Use a matching JioSaavn stream when one exists",
                checked = saavn,
                onChecked = { prefs.setBoolean(PrefKeys.SaavnEnabled, it) },
            )
        }

        SettingsSection("Transitions") {
            val crossfade = prefs.getBoolean(PrefKeys.CrossfadeEnabled, false)
            SwitchItem(
                icon = Icons.Outlined.GraphicEq,
                title = "Crossfade",
                subtitle = "Blend into the next song",
                checked = crossfade,
                onChecked = { prefs.setBoolean(PrefKeys.CrossfadeEnabled, it) },
            )
            AnimatedVisibility(crossfade, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                PrefSecondsSlider(
                    title = "Length",
                    valueLabel = { "${it.toInt()} s" },
                    value = prefs.getInt(PrefKeys.CrossfadeSeconds, 3).toFloat(),
                    valueRange = 1f..10f,
                    steps = 8,
                    onCommit = { prefs.setInt(PrefKeys.CrossfadeSeconds, it.toInt().coerceIn(1, 10)) },
                )
            }
            SettingsDivider()
            val playPauseFade = prefs.getBoolean(PrefKeys.PlayPauseFadeEnabled, true)
            SwitchItem(
                icon = Icons.Outlined.Tune,
                title = "Fade on play / pause",
                subtitle = "Ramp volume instead of cutting",
                checked = playPauseFade,
                onChecked = { prefs.setBoolean(PrefKeys.PlayPauseFadeEnabled, it) },
            )
            AnimatedVisibility(playPauseFade, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                PrefSecondsSlider(
                    title = "Length",
                    valueLabel = { String.format("%.1f s", it) },
                    value = prefs.getFloat(PrefKeys.PlayPauseFadeSeconds, 0.5f),
                    valueRange = 0.2f..3f,
                    steps = 27,
                    onCommit = {
                        prefs.setFloat(PrefKeys.PlayPauseFadeSeconds, (kotlin.math.round(it * 10f) / 10f).coerceIn(0.2f, 3f))
                    },
                )
            }
        }

        SettingsSection("Integrations") {
            SwitchItem(
                icon = Icons.Outlined.Groups,
                title = "Discord Rich Presence",
                subtitle = "Show what you're listening to",
                checked = prefs.getBoolean(PrefKeys.DiscordRpcEnabled, true),
                onChecked = { prefs.setBoolean(PrefKeys.DiscordRpcEnabled, it) },
            )
        }
    }
}

// —— Downloads ————————————————————————————————————————————————

@Composable
fun DownloadsSettingsScreen(navController: NavController) {
    val prefs = koinInject<DesktopPrefs>()
    @Suppress("UNUSED_VARIABLE")
    val observe = rememberPrefsVersion(prefs)
    val managedOffline = remember { DatabaseFactory.offlineDir().absolutePath }
    val storedPath = prefs.getString(PrefKeys.DownloadPath, "")
    var pathDraft by remember(storedPath, managedOffline) { mutableStateOf(storedPath.ifBlank { managedOffline }) }
    val ytdlpOk = remember { YtDlpBinary.available() }

    SettingsPage(title = "Downloads", onBack = { navController.navigateUp() }) {
        if (!ytdlpOk) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(
                    "yt-dlp wasn't found. Install it or set VIVI_YTDLP to enable downloads.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        SettingsSection("Save to") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                OutlinedTextField(
                    value = pathDraft,
                    onValueChange = { pathDraft = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Folder") },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        pathDraft = managedOffline
                        prefs.setString(PrefKeys.DownloadPath, "")
                    }) { Text("Reset") }
                    TextButton(
                        enabled = pathDraft.trim() != storedPath.ifBlank { managedOffline },
                        onClick = {
                            val trimmed = pathDraft.trim()
                            prefs.setString(PrefKeys.DownloadPath, if (trimmed.isEmpty() || trimmed == managedOffline) "" else trimmed)
                        },
                    ) { Text("Save") }
                }
            }
        }

        SettingsSection("File") {
            ChoiceRow(
                icon = Icons.Outlined.Download,
                title = "Format",
                options = DownloadAudioFormat.entries.map { it to it.label },
                selected = prefs.enumPref(PrefKeys.DownloadFormat, DownloadAudioFormat.OPUS),
                onSelect = { prefs.setString(PrefKeys.DownloadFormat, it.name) },
            )
            SettingsDivider()
            ChoiceRow(
                icon = Icons.Outlined.HighQuality,
                title = "Quality",
                options = DownloadAudioQuality.entries.map { it to it.label },
                selected = prefs.enumPref(PrefKeys.DownloadQuality, DownloadAudioQuality.BEST),
                onSelect = { prefs.setString(PrefKeys.DownloadQuality, it.name) },
            )
            SettingsDivider()
            SwitchItem(
                icon = Icons.Outlined.Palette,
                title = "Embed cover and tags",
                subtitle = "Some formats need ffmpeg",
                checked = prefs.getBoolean(PrefKeys.DownloadEmbedMetadata, true),
                onChecked = { prefs.setBoolean(PrefKeys.DownloadEmbedMetadata, it) },
            )
        }
    }
}

// —— Account ——————————————————————————————————————————————————

@Composable
fun AccountSettingsScreen(navController: NavController) {
    val yt = koinInject<YouTubeMusicService>()
    var account by remember { mutableStateOf<AccountInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(Unit) {
        yt.accountInfo()
            .onSuccess { account = it }
            .onFailure { error = it.message ?: it.toString() }
    }
    SettingsPage(title = "Account", onBack = { navController.navigateUp() }) {
        AccountCard(account, error) {}
        Text(
            "Your session is stored in the system keyring. Sign in again below if it expires.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp),
        )
        com.music.vivi.compose.ui.YtLoginPanel(
            modifier = Modifier.padding(horizontal = 4.dp),
            onLoggedIn = {
                scope.launch {
                    error = null
                    yt.accountInfo().onSuccess { account = it }.onFailure { error = it.message ?: it.toString() }
                }
            },
        )
    }
}

// —— shared building blocks ————————————————————————————————

/** Scrolling settings page, centered at a readable width on wide windows. */
@Composable
internal fun SettingsPage(
    title: String,
    onBack: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize().smoothWheelScroll(scroll).verticalScroll(scroll), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 120.dp),
        ) {
            SettingsBackHeader(title = title, onBack = onBack)
            content()
        }
    }
}

@Composable
private fun SettingsGroupCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), content = content)
    }
}

@Composable
internal fun SettingsSection(
    title: String,
    @Suppress("UNUSED_PARAMETER") initiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp, top = 24.dp, bottom = 8.dp),
        )
        SettingsGroupCard(content)
    }
}

@Composable
internal fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 60.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
    )
}

@Composable
internal fun SettingsBackHeader(title: String, onBack: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp).height(48.dp),
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.width(4.dp))
        }
        Text(
            title,
            style = if (onBack == null) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = if (onBack == null) Modifier.padding(start = 4.dp) else Modifier,
        )
    }
}

@Composable
private fun SettingsRowFrame(
    icon: ImageVector?,
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)?,
    trailing: @Composable () -> Unit = {},
) {
    val rowContent: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(18.dp))
            }
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            trailing()
        }
    }
    val mod = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 1.dp)
    if (onClick != null) {
        Surface(onClick = onClick, shape = RoundedCornerShape(InteractionCornerRadius), color = Color.Transparent, modifier = mod) { rowContent() }
    } else {
        Box(mod) { rowContent() }
    }
}

@Composable
private fun SettingsNavItem(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    SettingsRowFrame(icon, title, subtitle, onClick) {
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun SwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    // Optimistic UI — don't wait for prefs listener round-trip.
    var local by remember(checked) { mutableStateOf(checked) }
    SettingsRowFrame(icon, title, subtitle, onClick = { local = !local; onChecked(local) }) {
        Switch(checked = local, onCheckedChange = { local = it; onChecked(it) })
    }
}

/**
 * A setting with a handful of short options: segmented buttons beside the title on wide
 * rows, wrapping under the title when space is tight.
 */
@Composable
private fun <T> ChoiceRow(
    icon: ImageVector?,
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    var local by remember(selected) { mutableStateOf(selected) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val inline = maxWidth >= 560.dp
        val segmented: @Composable (Modifier) -> Unit = { modifier ->
            SingleChoiceSegmentedButtonRow(modifier) {
                options.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = local == value,
                        onClick = { local = value; onSelect(value) },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        icon = {},
                        label = { Text(label, maxLines = 1, style = MaterialTheme.typography.labelLarge) },
                    )
                }
            }
        }
        if (inline) {
            SettingsRowFrame(icon, title, null, onClick = null) { segmented(Modifier.widthIn(max = 380.dp)) }
        } else {
            Column {
                SettingsRowFrame(icon, title, null, onClick = null)
                segmented(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
            }
        }
    }
}

/** A setting with many options: title row plus a wrapping set of chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipChoiceRow(
    icon: ImageVector?,
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    var local by remember(selected) { mutableStateOf(selected) }
    Column(Modifier.fillMaxWidth()) {
        SettingsRowFrame(icon, title, null, onClick = null)
        FlowRow(
            Modifier.fillMaxWidth().padding(start = if (icon != null) 56.dp else 16.dp, end = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEach { (value, label) ->
                FilterChip(
                    selected = local == value,
                    onClick = { local = value; onSelect(value) },
                    label = { Text(label) },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                )
            }
        }
    }
}

@Composable
private fun PrefSecondsSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: (Float) -> String,
    onCommit: (Float) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Row(
        Modifier.fillMaxWidth().padding(start = 56.dp, end = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = local.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = { local = it },
            onValueChangeFinished = { onCommit(local) },
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        Text(
            valueLabel(local),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.widthIn(min = 40.dp),
        )
    }
}
