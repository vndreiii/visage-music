package com.music.vivi.compose.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.materialkolor.dynamicColorScheme
import com.music.innertube.models.AccountInfo
import com.music.vivi.compose.download.XdgUserDirs
import com.music.vivi.compose.navigation.Routes
import com.music.vivi.database.DatabaseFactory
import com.music.vivi.compose.prefs.AudioQuality
import com.music.vivi.compose.prefs.DarkMode
import com.music.vivi.compose.prefs.DefaultThemeColorArgb
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.DownloadAudioFormat
import com.music.vivi.compose.prefs.DownloadAudioQuality
import com.music.vivi.compose.prefs.M3PaletteStyle
import com.music.vivi.compose.prefs.MiniPlayerStyle
import com.music.vivi.compose.prefs.LyricsProvider
import com.music.vivi.compose.prefs.LyricsTextAlign
import com.music.vivi.compose.prefs.PlayerBackgroundStyle
import com.music.vivi.compose.prefs.PlayerButtonsStyle
import com.music.vivi.compose.prefs.PrefKeys
import com.music.vivi.compose.prefs.QueuePanelMode
import com.music.vivi.compose.prefs.SliderStyle
import com.music.vivi.compose.prefs.VolumeControlStyle
import com.music.vivi.compose.prefs.TopNavStyle
import com.music.vivi.compose.ui.component.InteractionCornerRadius
import com.music.vivi.compose.ui.resolveMiniPlayerStyle
import com.music.vivi.compose.theme.MatugenColors
import com.music.vivi.compose.theme.label
import com.music.vivi.compose.theme.subtitle
import com.music.vivi.compose.theme.toKolor
import com.music.vivi.core.youtube.YouTubeMusicService
import com.music.vivi.player.stream.YtDlpBinary
import org.koin.compose.koinInject

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

@Composable
fun SettingsHubScreen(navController: NavController) {
    val yt = koinInject<YouTubeMusicService>()
    var account by remember { mutableStateOf<AccountInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        yt.accountInfo()
            .onSuccess { account = it }
            .onFailure { error = it.message ?: it.toString() }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            )
        }

        item {
            SettingsGroupCard {
                SettingsNavItem(
                    icon = Icons.Outlined.Update,
                    title = "System update",
                    subtitle = "Desktop builds — check releases manually",
                    onClick = { },
                    showChevron = false,
                )
                SettingsDivider()
                SettingsNavItem(
                    icon = Icons.Outlined.AccountCircle,
                    title = "Account",
                    subtitle = account?.email
                        ?: account?.name
                        ?: error
                        ?: "Manage logged-in profiles",
                    onClick = { navController.navigate(Routes.SETTINGS_ACCOUNT) },
                )
            }
        }

        item {
            SettingsGroupCard {
                SettingsNavItem(
                    icon = Icons.Outlined.Palette,
                    title = "Appearance",
                    subtitle = "Customize themes and layout",
                    onClick = { navController.navigate(Routes.SETTINGS_APPEARANCE) },
                )
                SettingsDivider()
                SettingsNavItem(
                    icon = Icons.AutoMirrored.Outlined.VolumeUp,
                    title = "Player and audio",
                    subtitle = "Stream, quality, playback fades",
                    onClick = { navController.navigate(Routes.SETTINGS_PLAYER) },
                )
                SettingsDivider()
                SettingsNavItem(
                    icon = Icons.Outlined.Download,
                    title = "Downloads",
                    subtitle = "Offline format and quality",
                    onClick = { navController.navigate(Routes.SETTINGS_DOWNLOADS) },
                )
                SettingsDivider()
                SettingsNavItem(
                    icon = Icons.Outlined.Folder,
                    title = "Local / Offline",
                    subtitle = "Folders, scan, managed offline library",
                    onClick = { navController.navigate(Routes.SETTINGS_LOCAL_OFFLINE) },
                )
                SettingsDivider()
                SettingsNavItem(
                    icon = Icons.Outlined.Groups,
                    title = "Listen Together",
                    subtitle = "Coming soon on desktop",
                    onClick = { },
                    showChevron = false,
                )
                SettingsDivider()
                SettingsNavItem(
                    icon = Icons.Outlined.Language,
                    title = "Content",
                    subtitle = "Language and library",
                    onClick = { navController.navigate(Routes.SETTINGS_THEME) },
                )
            }
        }

        item {
            SettingsGroupCard {
                SettingsNavItem(
                    icon = Icons.Outlined.Translate,
                    title = "AI Lyrics Translation",
                    subtitle = "Coming soon on desktop",
                    onClick = { },
                    showChevron = false,
                )
            }
        }

        item {
            Text(
                "VIVI Desktop — Linux port of VIVI Music",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
fun ThemeSettingsScreen(navController: NavController) {
    val prefs = koinInject<DesktopPrefs>()
    val prefsVersion = rememberPrefsVersion(prefs)
    @Suppress("UNUSED_VARIABLE")
    val observe = prefsVersion

    val darkMode = runCatching {
        DarkMode.valueOf(prefs.getString(PrefKeys.DarkMode, DarkMode.AUTO.name))
    }.getOrDefault(DarkMode.AUTO)
    val pureBlack = prefs.getBoolean(PrefKeys.PureBlack, false)
    val colorArgb = prefs.getInt(PrefKeys.SelectedThemeColor, DefaultThemeColorArgb)
    val dynamicTheme = prefs.getBoolean(PrefKeys.DynamicTheme, true)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 96.dp),
    ) {
        SettingsBackHeader(title = "Theme", onBack = { navController.navigateUp() })

        SettingsSection("Mode") {
            DarkMode.entries.forEachIndexed { index, mode ->
                if (index > 0) SettingsDivider()
                val label = when (mode) {
                    DarkMode.AUTO -> "Auto (system)"
                    DarkMode.OFF -> "Light"
                    DarkMode.ON -> "Dark"
                }
                EnumRadioItem(
                    label = label,
                    selected = darkMode == mode && !(mode == DarkMode.ON && pureBlack),
                    onSelect = {
                        prefs.setString(PrefKeys.DarkMode, mode.name)
                        if (mode != DarkMode.ON) prefs.setBoolean(PrefKeys.PureBlack, false)
                    },
                    trailing = {
                        val previewDark = when (mode) {
                            DarkMode.OFF -> false
                            DarkMode.ON -> true
                            DarkMode.AUTO -> true
                        }
                        MockupFrame(width = 72.dp, height = 40.dp) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(if (previewDark) Color(0xFF121212) else Color(0xFFF5F5F5)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (previewDark) Color(0xFFD0BCFF) else Color(0xFF6750A4),
                                    modifier = Modifier.size(width = 36.dp, height = 18.dp),
                                ) {}
                            }
                        }
                    },
                )
            }
            SettingsDivider()
            EnumRadioItem(
                label = "Pure black",
                selected = darkMode == DarkMode.ON && pureBlack,
                onSelect = {
                    prefs.setString(PrefKeys.DarkMode, DarkMode.ON.name)
                    prefs.setBoolean(PrefKeys.PureBlack, true)
                    prefs.setBoolean(PrefKeys.PureBlackMiniPlayer, true)
                },
                trailing = {
                    MockupFrame(width = 72.dp, height = 40.dp) {
                        Box(
                            Modifier.fillMaxSize().background(Color.Black),
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFD0BCFF),
                                modifier = Modifier.size(width = 36.dp, height = 18.dp),
                            ) {}
                        }
                    }
                },
            )
        }

        SettingsSection("Dynamic color") {
            SwitchItem(
                icon = Icons.Outlined.ColorLens,
                title = "Enable dynamic theme",
                subtitle = "Seed from cover art when playing",
                checked = dynamicTheme,
                onChecked = { prefs.setBoolean(PrefKeys.DynamicTheme, it) },
            )
            SettingsDivider()
            val matugenOn = prefs.getBoolean(PrefKeys.MatugenTheme, false)
            val matugenReady = MatugenColors.defaultFile.isFile
            SwitchItem(
                icon = Icons.Outlined.Palette,
                title = "Matugen",
                subtitle = if (matugenReady) {
                    "Tint from system wallpaper (~/.cache/matugen/vivi-desktop.colors)"
                } else {
                    "No colours yet — run matugen after installing the vivi-desktop template"
                },
                checked = matugenOn,
                onChecked = { prefs.setBoolean(PrefKeys.MatugenTheme, it) },
            )
            if (matugenOn && matugenReady) {
                SettingsDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val preview = remember(matugenOn) {
                        MatugenColors.load()?.let { if (darkMode != DarkMode.OFF) it.dark else it.light }
                    }
                    if (preview != null) {
                        PaletteSwatchMockup(
                            listOfNotNull(
                                preview["primary"],
                                preview["secondary"],
                                preview["tertiary"],
                                preview["primaryContainer"],
                                preview["surfaceContainerHigh"],
                            ),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Live from wallpaper",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        SettingsSection("Color algorithm") {
            val style = runCatching {
                M3PaletteStyle.valueOf(
                    prefs.getString(PrefKeys.PaletteStyle, M3PaletteStyle.TONAL_SPOT.name),
                )
            }.getOrDefault(M3PaletteStyle.TONAL_SPOT)
            M3PaletteStyle.entries.forEachIndexed { index, entry ->
                if (index > 0) SettingsDivider()
                val previewScheme = remember(entry, colorArgb, darkMode) {
                    dynamicColorScheme(
                        seedColor = Color(colorArgb.toLong() and 0xFFFFFFFFL),
                        isDark = darkMode != DarkMode.OFF,
                        style = entry.toKolor(),
                    )
                }
                EnumRadioItem(
                    label = entry.label(),
                    subtitle = entry.subtitle(),
                    selected = style == entry,
                    onSelect = { prefs.setString(PrefKeys.PaletteStyle, entry.name) },
                    trailing = {
                        PaletteSwatchMockup(
                            listOf(
                                previewScheme.primary,
                                previewScheme.secondary,
                                previewScheme.tertiary,
                                previewScheme.primaryContainer,
                                previewScheme.surfaceContainerHigh,
                            ),
                        )
                    },
                )
            }
        }

        SettingsSection("Contrast") {
            val contrast = prefs.getFloat(PrefKeys.ContrastLevel, 0f)
            listOf(
                0f to "Standard",
                0.5f to "Medium",
                1f to "High",
            ).forEachIndexed { index, (level, label) ->
                if (index > 0) SettingsDivider()
                EnumRadioItem(
                    label = label,
                    selected = kotlin.math.abs(contrast - level) < 0.05f,
                    onSelect = { prefs.setFloat(PrefKeys.ContrastLevel, level) },
                    trailing = { ContrastMockup(level) },
                )
            }
        }
    }
}

@Composable
fun AppearanceSettingsScreen(navController: NavController) {
    val prefs = koinInject<DesktopPrefs>()
    val prefsVersion = rememberPrefsVersion(prefs)
    @Suppress("UNUSED_VARIABLE")
    val observe = prefsVersion

    val dynamicTheme = prefs.getBoolean(PrefKeys.DynamicTheme, true)
    val pureBlackMini = prefs.getBoolean(PrefKeys.PureBlackMiniPlayer, false)
    val currentMiniLayout = resolveMiniPlayerStyle(prefs)
    val miniStyle = prefs.getString(
        PrefKeys.MiniPlayerBackgroundStyle,
        PlayerBackgroundStyle.GRADIENT.name,
    )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 96.dp),
    ) {
        SettingsBackHeader(title = "Appearance", onBack = { navController.navigateUp() })

        SettingsSection("Theme") {
            SettingsNavItem(
                icon = Icons.Outlined.Palette,
                title = "Theme",
                subtitle = "Dark mode, matugen, palette",
                onClick = { navController.navigate(Routes.SETTINGS_THEME) },
            )
            SettingsDivider()
            SettingsNavItem(
                icon = Icons.Outlined.Info,
                title = "App Font",
                subtitle = null,
                trailing = {
                    Text(
                        "System Default",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onClick = { },
                showChevron = false,
            )
            SettingsDivider()
            SwitchItem(
                icon = Icons.Outlined.Settings,
                title = "Enable Settings Dropdown",
                subtitle = "Show a menu under Settings in the top bar",
                checked = prefs.getBoolean(PrefKeys.SettingsDropdown, true),
                onChecked = { prefs.setBoolean(PrefKeys.SettingsDropdown, it) },
            )
            SettingsDivider()
            SwitchItem(
                icon = Icons.Outlined.ColorLens,
                title = "Enable dynamic theme",
                subtitle = null,
                checked = dynamicTheme,
                onChecked = { prefs.setBoolean(PrefKeys.DynamicTheme, it) },
            )
        }

        SettingsSection("Top bar style") {
            val topNav = runCatching {
                TopNavStyle.valueOf(
                    prefs.getString(PrefKeys.TopNavStyle, TopNavStyle.ICON_ONLY.name),
                )
            }.getOrDefault(TopNavStyle.ICON_ONLY)
            LiveTopNavHeroPreview(style = topNav)
            SettingsDivider()
            TopNavStyle.entries.forEachIndexed { index, style ->
                if (index > 0) SettingsDivider()
                val label = when (style) {
                    TopNavStyle.ICON_ONLY -> "Icons only"
                    TopNavStyle.ICON_AND_TEXT -> "Icons and text"
                    TopNavStyle.TEXT_ONLY -> "Text only"
                }
                EnumRadioItem(
                    label = label,
                    selected = topNav == style,
                    onSelect = { prefs.setString(PrefKeys.TopNavStyle, style.name) },
                )
            }
        }

        SettingsSection("Mini-player") {
            val currentBg = runCatching {
                PlayerBackgroundStyle.valueOf(miniStyle)
            }.getOrDefault(PlayerBackgroundStyle.GRADIENT)
            LiveMiniPlayerHeroPreview(
                layout = currentMiniLayout,
                background = currentBg,
            )
            SettingsDivider()
            MiniPlayerStyle.entries.forEachIndexed { index, style ->
                if (index > 0) SettingsDivider()
                val label = when (style) {
                    MiniPlayerStyle.FULL_WIDTH_BAR -> "Classic full-width"
                    MiniPlayerStyle.FLOATING_PILL -> "Floating pill"
                    MiniPlayerStyle.COMPACT_EDGE -> "Compact edge"
                }
                EnumRadioItem(
                    label = label,
                    selected = currentMiniLayout == style,
                    onSelect = {
                        prefs.setString(PrefKeys.MiniPlayerStyle, style.name)
                        when (style) {
                            MiniPlayerStyle.FULL_WIDTH_BAR -> {
                                prefs.setBoolean(PrefKeys.UseNewMiniPlayerDesign, false)
                                prefs.setBoolean(PrefKeys.UseAppleMiniPlayer, false)
                            }
                            MiniPlayerStyle.FLOATING_PILL -> {
                                prefs.setBoolean(PrefKeys.UseNewMiniPlayerDesign, true)
                                prefs.setBoolean(PrefKeys.UseAppleMiniPlayer, false)
                            }
                            MiniPlayerStyle.COMPACT_EDGE -> {
                                prefs.setBoolean(PrefKeys.UseAppleMiniPlayer, true)
                                prefs.setBoolean(PrefKeys.UseNewMiniPlayerDesign, true)
                            }
                        }
                    },
                )
            }
            SettingsDivider()
            SwitchItem(
                icon = Icons.Outlined.Contrast,
                title = "Pure black mini-player",
                subtitle = null,
                checked = pureBlackMini,
                onChecked = { prefs.setBoolean(PrefKeys.PureBlackMiniPlayer, it) },
            )
        }

        SettingsSection("Mini-player background") {
            val miniStyles = PlayerBackgroundStyle.entries.filter { it != PlayerBackgroundStyle.APPLE_MUSIC }
            val currentBg = runCatching {
                PlayerBackgroundStyle.valueOf(miniStyle)
            }.getOrDefault(PlayerBackgroundStyle.GRADIENT)
            LiveMiniPlayerHeroPreview(
                layout = currentMiniLayout,
                background = currentBg,
            )
            SettingsDivider()
            miniStyles.forEachIndexed { index, style ->
                if (index > 0) SettingsDivider()
                EnumRadioItem(
                    label = when (style) {
                        PlayerBackgroundStyle.LEGACY -> "Legacy"
                        else -> style.name.lowercase().replace('_', ' ')
                    },
                    selected = miniStyle == style.name,
                    onSelect = { prefs.setString(PrefKeys.MiniPlayerBackgroundStyle, style.name) },
                )
            }
        }

        SettingsSection("Lyrics") {
            val lyricsAlign = runCatching {
                LyricsTextAlign.valueOf(
                    prefs.getString(PrefKeys.LyricsTextAlign, LyricsTextAlign.CENTER.name),
                )
            }.getOrDefault(LyricsTextAlign.CENTER)
            LyricsTextAlign.entries.forEachIndexed { index, style ->
                if (index > 0) SettingsDivider()
                val label = when (style) {
                    LyricsTextAlign.LEFT -> "Left"
                    LyricsTextAlign.CENTER -> "Center"
                    LyricsTextAlign.RIGHT -> "Right"
                }
                EnumRadioItem(
                    label = label,
                    selected = lyricsAlign == style,
                    onSelect = { prefs.setString(PrefKeys.LyricsTextAlign, style.name) },
                )
            }
            SettingsDivider()
            val lyricsProvider = runCatching {
                LyricsProvider.valueOf(
                    prefs.getString(PrefKeys.LyricsProvider, LyricsProvider.LRCLIB.name),
                )
            }.getOrDefault(LyricsProvider.LRCLIB)
            LyricsProvider.entries.forEachIndexed { index, provider ->
                if (index > 0) SettingsDivider()
                EnumRadioItem(
                    label = provider.label,
                    selected = lyricsProvider == provider,
                    onSelect = { prefs.setString(PrefKeys.LyricsProvider, provider.name) },
                )
            }
        }

        SettingsSection("Player") {
            LivePlayerLayoutHeroPreview(framed = prefs.getBoolean(PrefKeys.UsePlayerV2, false))
            SwitchItem(
                icon = Icons.Outlined.SmartDisplay,
                title = "Framed player controls",
                subtitle = "A rounded translucent panel in Now Playing",
                checked = prefs.getBoolean(PrefKeys.UsePlayerV2, false),
                onChecked = { prefs.setBoolean(PrefKeys.UsePlayerV2, it) },
            )
            SettingsDivider()
            SwitchItem(
                icon = Icons.Outlined.Contrast,
                title = "Hide player thumbnail",
                subtitle = null,
                checked = prefs.getBoolean(PrefKeys.HidePlayerThumbnail, false),
                onChecked = { prefs.setBoolean(PrefKeys.HidePlayerThumbnail, it) },
            )
            SettingsDivider()
            SwitchItem(
                icon = Icons.Outlined.SmartDisplay,
                title = "Canvas video",
                subtitle = "Apple Music motion backgrounds when available",
                checked = prefs.getBoolean(PrefKeys.EnableCanvas, true),
                onChecked = { prefs.setBoolean(PrefKeys.EnableCanvas, it) },
            )
        }

        SettingsSection("Queue") {
            val queueMode = runCatching {
                QueuePanelMode.valueOf(
                    prefs.getString(PrefKeys.QueuePanelMode, QueuePanelMode.FULLSCREEN.name),
                )
            }.getOrDefault(QueuePanelMode.FULLSCREEN)
            EnumRadioItem(
                label = "Fullscreen queue",
                selected = queueMode == QueuePanelMode.FULLSCREEN,
                onSelect = { prefs.setString(PrefKeys.QueuePanelMode, QueuePanelMode.FULLSCREEN.name) },
            )
            SettingsDivider()
            EnumRadioItem(
                label = "Pin to right",
                selected = queueMode == QueuePanelMode.PINNED_RIGHT,
                onSelect = { prefs.setString(PrefKeys.QueuePanelMode, QueuePanelMode.PINNED_RIGHT.name) },
            )
            SettingsDivider()
            EnumRadioItem(
                label = "Queue under playbar",
                selected = queueMode == QueuePanelMode.UNDER_PLAYBAR,
                onSelect = { prefs.setString(PrefKeys.QueuePanelMode, QueuePanelMode.UNDER_PLAYBAR.name) },
            )
            SettingsDivider()
            SwitchItem(
                icon = Icons.Outlined.SmartDisplay,
                title = "Persistent queue",
                subtitle = "Restore your queue and position at startup; press Play to resume",
                checked = prefs.getBoolean(PrefKeys.PersistentQueue, true),
                onChecked = { prefs.setBoolean(PrefKeys.PersistentQueue, it) },
            )
        }

        SettingsSection("Player background") {
            val playerBg = runCatching {
                PlayerBackgroundStyle.valueOf(
                    prefs.getString(PrefKeys.PlayerBackgroundStyle, PlayerBackgroundStyle.GRADIENT.name),
                )
            }.getOrDefault(PlayerBackgroundStyle.GRADIENT)
            LivePlayerBackgroundHeroPreview(style = playerBg)
            SettingsDivider()
            PlayerBackgroundStyle.entries.forEachIndexed { index, style ->
                if (index > 0) SettingsDivider()
                EnumRadioItem(
                    label = when (style) {
                        PlayerBackgroundStyle.LEGACY -> "Legacy"
                        else -> style.name.lowercase().replace('_', ' ')
                    },
                    selected = playerBg == style,
                    onSelect = { prefs.setString(PrefKeys.PlayerBackgroundStyle, style.name) },
                )
            }
        }

        
        SettingsSection("Volume control style") {
            val volumeStyle = runCatching {
                VolumeControlStyle.valueOf(
                    prefs.getString(PrefKeys.VolumeControlStyle, VolumeControlStyle.SLIDER.name),
                )
            }.getOrDefault(VolumeControlStyle.SLIDER)
            
            VolumeControlStyle.entries.forEachIndexed { index, style ->
                if (index > 0) SettingsDivider()
                EnumRadioItem(
                    label = style.name.lowercase().replace("_", " ").replaceFirstChar { it.titlecase() },
                    selected = volumeStyle == style,
                    onSelect = { prefs.setString(PrefKeys.VolumeControlStyle, style.name) },
                )
            }
        }

        SettingsSection("Slider style") {
            val squiggly = prefs.getBoolean(PrefKeys.SquigglySlider, false)
            val sliderStyle = runCatching {
                SliderStyle.valueOf(
                    prefs.getString(PrefKeys.SliderStyle, SliderStyle.DEFAULT.name),
                )
            }.getOrDefault(SliderStyle.DEFAULT)
            LiveSliderStyleHeroPreview(style = sliderStyle, squiggly = squiggly)
            SettingsDivider()
            SliderStyle.entries.forEachIndexed { index, style ->
                if (index > 0) SettingsDivider()
                EnumRadioItem(
                    label = when (style) {
                        SliderStyle.M3_EXPRESSIVE -> "M3 Expressive"
                        else -> style.name.lowercase().replaceFirstChar { it.titlecase() }
                    },
                    selected = sliderStyle == style,
                    onSelect = { prefs.setString(PrefKeys.SliderStyle, style.name) },
                )
            }
        }

        SettingsSection("Buttons") {
            val buttonsStyle = runCatching {
                PlayerButtonsStyle.valueOf(
                    prefs.getString(PrefKeys.PlayerButtonsStyle, PlayerButtonsStyle.DEFAULT.name),
                )
            }.getOrDefault(PlayerButtonsStyle.DEFAULT)
            LivePlayerButtonsHeroPreview(style = buttonsStyle)
            SettingsDivider()
            PlayerButtonsStyle.entries.forEachIndexed { index, style ->
                if (index > 0) SettingsDivider()
                EnumRadioItem(
                    label = style.name.lowercase().replaceFirstChar { it.titlecase() },
                    selected = buttonsStyle == style,
                    onSelect = { prefs.setString(PrefKeys.PlayerButtonsStyle, style.name) },
                )
            }
        }
    }
}

@Composable
fun PlayerSettingsScreen(navController: NavController) {
    val prefs = koinInject<DesktopPrefs>()
    val prefsVersion = rememberPrefsVersion(prefs)
    @Suppress("UNUSED_VARIABLE")
    val observe = prefsVersion
    val envSaavn = System.getenv("VIVI_SAAVN") != "0"
    val saavn = prefs.getBoolean(PrefKeys.SaavnEnabled, envSaavn)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 96.dp),
    ) {
        SettingsBackHeader(title = "Player and audio", onBack = { navController.navigateUp() })

                SettingsSection("Integrations") {
            SwitchItem(
                icon = Icons.Outlined.Groups,
                title = "Discord Rich Presence",
                subtitle = "Show what you're listening to on Discord",
                checked = prefs.getBoolean(PrefKeys.DiscordRpcEnabled, true),
                onChecked = { prefs.setBoolean(PrefKeys.DiscordRpcEnabled, it) },
            )
        }

        SettingsSection("Stream") {
            SwitchItem(
                icon = Icons.AutoMirrored.Outlined.VolumeUp,
                title = "Prefer JioSaavn when available",
                subtitle = "Use a matching JioSaavn stream for the next song",
                checked = saavn,
                onChecked = { prefs.setBoolean(PrefKeys.SaavnEnabled, it) },
            )
        }

        SettingsSection("Audio quality") {
            AudioQuality.entries.forEachIndexed { index, q ->
                if (index > 0) SettingsDivider()
                EnumRadioItem(
                    label = q.name.lowercase().replaceFirstChar { it.titlecase() },
                    selected = prefs.getString(PrefKeys.AudioQuality, AudioQuality.AUTO.name) == q.name,
                    onSelect = { prefs.setString(PrefKeys.AudioQuality, q.name) },
                )
            }
        }

        SettingsSection("Playback fades") {
            val crossfade = prefs.getBoolean(PrefKeys.CrossfadeEnabled, false)
            SwitchItem(
                icon = Icons.Outlined.GraphicEq,
                title = "Crossfade on skip / next",
                subtitle = "Blend songs when skipping and near the end of a track",
                checked = crossfade,
                onChecked = { prefs.setBoolean(PrefKeys.CrossfadeEnabled, it) },
            )
            AnimatedVisibility(visible = crossfade) {
                Column {
                    SettingsDivider()
                    PrefSecondsSlider(
                        title = "Crossfade length",
                        valueLabel = { "${it.toInt()} s" },
                        value = prefs.getInt(PrefKeys.CrossfadeSeconds, 3).toFloat(),
                        valueRange = 1f..10f,
                        steps = 8,
                        onCommit = { prefs.setInt(PrefKeys.CrossfadeSeconds, it.toInt().coerceIn(1, 10)) },
                    )
                }
            }
            SettingsDivider()
            val playPauseFade = prefs.getBoolean(PrefKeys.PlayPauseFadeEnabled, true)
            SwitchItem(
                icon = Icons.AutoMirrored.Outlined.VolumeUp,
                title = "Fade on play / pause",
                subtitle = "Soft volume ramp before pause and after play",
                checked = playPauseFade,
                onChecked = { prefs.setBoolean(PrefKeys.PlayPauseFadeEnabled, it) },
            )
            AnimatedVisibility(visible = playPauseFade) {
                Column {
                    SettingsDivider()
                    PrefSecondsSlider(
                        title = "Play/pause fade length",
                        valueLabel = { String.format("%.1f s", it) },
                        value = prefs.getFloat(PrefKeys.PlayPauseFadeSeconds, 0.5f),
                        valueRange = 0.2f..3f,
                        steps = 27,
                        onCommit = {
                            prefs.setFloat(
                                PrefKeys.PlayPauseFadeSeconds,
                                (kotlin.math.round(it * 10f) / 10f).coerceIn(0.2f, 3f),
                            )
                        },
                    )
                }
            }
        }

        SettingsSection("Look") {
            SettingsNavItem(
                icon = Icons.Outlined.Palette,
                title = "Appearance",
                subtitle = "Player / mini designs, slider, backgrounds",
                onClick = { navController.navigate(Routes.SETTINGS_APPEARANCE) },
            )
        }
    }
}

@Composable
fun DownloadsSettingsScreen(navController: NavController) {
    val prefs = koinInject<DesktopPrefs>()
    val prefsVersion = rememberPrefsVersion(prefs)
    @Suppress("UNUSED_VARIABLE")
    val observe = prefsVersion
    val managedOffline = remember { DatabaseFactory.offlineDir().absolutePath }
    val storedPath = prefs.getString(PrefKeys.DownloadPath, "")
    var pathDraft by remember(storedPath, managedOffline) {
        mutableStateOf(storedPath.ifBlank { managedOffline })
    }
    val format = runCatching {
        DownloadAudioFormat.valueOf(
            prefs.getString(PrefKeys.DownloadFormat, DownloadAudioFormat.OPUS.name),
        )
    }.getOrDefault(DownloadAudioFormat.OPUS)
    val quality = runCatching {
        DownloadAudioQuality.valueOf(
            prefs.getString(PrefKeys.DownloadQuality, DownloadAudioQuality.BEST.name),
        )
    }.getOrDefault(DownloadAudioQuality.BEST)
    val embed = prefs.getBoolean(PrefKeys.DownloadEmbedMetadata, true)
    val ytdlpOk = remember { YtDlpBinary.available() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 96.dp),
    ) {
        SettingsBackHeader(title = "Downloads", onBack = { navController.navigateUp() })

        SettingsSection("Folder") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(20.dp))
                    Text(
                        "Save downloads to",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = pathDraft,
                    onValueChange = { pathDraft = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Folder path") },
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = {
                            pathDraft = managedOffline
                            prefs.setString(PrefKeys.DownloadPath, "")
                        },
                    ) {
                        Text("Use managed offline folder")
                    }
                    TextButton(
                        onClick = {
                            val trimmed = pathDraft.trim()
                            prefs.setString(
                                PrefKeys.DownloadPath,
                                if (trimmed.isEmpty() || trimmed == managedOffline) "" else trimmed,
                            )
                        },
                    ) {
                        Text("Save path")
                    }
                }
                Text(
                    "Default: $managedOffline (app-managed offline library)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SettingsSection("Format") {
            DownloadAudioFormat.entries.forEachIndexed { index, f ->
                if (index > 0) SettingsDivider()
                EnumRadioItem(
                    label = f.label,
                    selected = format == f,
                    onSelect = { prefs.setString(PrefKeys.DownloadFormat, f.name) },
                )
            }
        }

        SettingsSection("Quality") {
            DownloadAudioQuality.entries.forEachIndexed { index, q ->
                if (index > 0) SettingsDivider()
                EnumRadioItem(
                    label = q.label,
                    selected = quality == q,
                    onSelect = { prefs.setString(PrefKeys.DownloadQuality, q.name) },
                )
            }
        }

        SettingsSection("Metadata") {
            SwitchItem(
                icon = Icons.Outlined.Download,
                title = "Embed cover and tags",
                subtitle = "Requires ffmpeg for some formats",
                checked = embed,
                onChecked = { prefs.setBoolean(PrefKeys.DownloadEmbedMetadata, it) },
            )
        }

        SettingsSection("Engine") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(
                    if (ytdlpOk) "yt-dlp found on PATH"
                    else "yt-dlp not found — install it or set VIVI_YTDLP",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (ytdlpOk) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
        }
    }
}

@Composable
fun AccountSettingsScreen(navController: NavController) {
    val yt = koinInject<YouTubeMusicService>()
    var account by remember { mutableStateOf<AccountInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        yt.accountInfo()
            .onSuccess { account = it }
            .onFailure { error = it.message ?: it.toString() }
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 96.dp),
    ) {
        SettingsBackHeader(title = "Account", onBack = { navController.navigateUp() })
        SettingsGroupCard {
            Column(Modifier.padding(16.dp)) {
                Text(
                    account?.name ?: "—",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    account?.email ?: error ?: "Loading…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Session lives in the system keyring. Re-run login from the gate if needed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// —— shared building blocks ————————————————————————————————

@Composable
private fun SettingsGroupCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
internal fun SettingsSection(
    title: String,
    initiallyExpanded: Boolean = true,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Column(Modifier.fillMaxWidth()) {
        Surface(
            onClick = { expanded = !expanded },
            shape = RoundedCornerShape(InteractionCornerRadius),
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, top = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            SettingsGroupCard {
                content()
            }
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
    )
}

@Composable
internal fun SettingsBackHeader(title: String, onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SettingsNavItem(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    showChevron: Boolean = true,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(InteractionCornerRadius),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            trailing?.invoke()
            if (showChevron) {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
    LaunchedEffect(value) { local = value }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                valueLabel(local),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = local.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = { local = it },
            onValueChangeFinished = { onCommit(local) },
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        )
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
    LaunchedEffect(checked) { local = checked }

    Surface(
        onClick = {
            val next = !local
            local = next
            onChecked(next)
        },
        shape = RoundedCornerShape(InteractionCornerRadius),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Switch(
                checked = local,
                onCheckedChange = {
                    local = it
                    onChecked(it)
                },
            )
        }
    }
}

@Composable
private fun EnumRadioItem(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(InteractionCornerRadius),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
        } else {
            Color.Transparent
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f).padding(end = 10.dp)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            trailing?.invoke()
        }
    }
}
