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
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MovieFilter
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.Favorite
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
import com.music.vivi.compose.prefs.FullscreenCanvasMode
import com.music.vivi.compose.prefs.FullscreenCoverShape
import com.music.vivi.compose.prefs.FullscreenFollow
import com.music.vivi.compose.prefs.FullscreenKeys
import com.music.vivi.compose.prefs.FullscreenLayout
import com.music.vivi.compose.prefs.FullscreenSettings
import com.music.vivi.compose.prefs.reader
import com.music.vivi.compose.prefs.resetFullscreenPrefs
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shuffle
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

internal inline fun <reified T : Enum<T>> DesktopPrefs.enumPref(key: String, default: T): T =
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

        var confirmSignOut by remember { mutableStateOf(false) }
        if (account != null) {
            SettingsSection("Account") {
                SettingsNavItem(
                    icon = Icons.AutoMirrored.Outlined.Logout,
                    title = "Sign out",
                    subtitle = "Remove your YouTube Music account from this device",
                    onClick = { confirmSignOut = true },
                )
            }
        }
        if (confirmSignOut) {
            SignOutDialog(
                onDismiss = { confirmSignOut = false },
                onConfirm = {
                    confirmSignOut = false
                    scope.launch { performSignOut(yt) ; account = null; error = "Not signed in" }
                },
            )
        }

        SettingsSection("Interface") {
            InterfaceLinkItems(navController, skipRoute = null)
        }

        SettingsSection("Playback") {
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
            SettingsDivider()
            SettingsNavItem(
                icon = Icons.Outlined.Sell,
                title = "Tag Manager",
                subtitle = "Fix missing tags and cover art in local files",
                onClick = { navController.navigate(Routes.TAG_MANAGER) },
            )
            SettingsDivider()
            SettingsNavItem(
                icon = Icons.Outlined.BugReport,
                title = "Diagnostics",
                subtitle = "Logs, system info, report a problem",
                onClick = { navController.navigate(Routes.SETTINGS_DIAGNOSTICS) },
            )
        }

        com.music.vivi.compose.update.UpdateSettingsSection()

        SettingsSection("About") {
            SettingsNavItem(
                icon = Icons.Outlined.Favorite,
                title = "Support VIsage",
                subtitle = "VIsage is free software. Donations pay for development.",
                onClick = { openExternalUrl(SUPPORT_URL) },
            )
        }

        Text(
            "VIsage — desktop port of VIVI Music",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 20.dp),
        )
    }
}

@Composable
private fun AccountCard(account: AccountInfo?, error: String?, onClick: (() -> Unit)?) {
    // With no onClick the card is informational: no ripple and no chevron.
    val card: @Composable (@Composable () -> Unit) -> Unit = { content ->
        if (onClick != null) {
            Surface(
                onClick = onClick,
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                content = content,
            )
        } else {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                content = content,
            )
        }
    }
    card {
        AccountCardRow(account, error, showChevron = onClick != null)
    }
}

@Composable
private fun AccountCardRow(account: AccountInfo?, error: String?, showChevron: Boolean) {
    run {
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
            if (showChevron) {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
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
                    "Use ~/.cache/matugen/visage.colors"
                } else {
                    "Run matugen with the visage template first"
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
                com.music.vivi.compose.ui.motion.VisageMotion.settle(),
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

/** Every page that belongs under "Interface"; shown in the hub and on the Interface page, so none is hard to find. */
private data class InterfaceLink(val icon: ImageVector, val title: String, val subtitle: String, val route: String)

private val interfaceLinks = listOf(
    InterfaceLink(Icons.Outlined.Palette, "Theme", "Light or dark, dynamic color, palette, contrast", Routes.SETTINGS_THEME),
    InterfaceLink(Icons.Outlined.Dashboard, "Top bar & mini-player", "Layout and style of the top bar and mini-player", Routes.SETTINGS_APPEARANCE),
    InterfaceLink(Icons.Outlined.PlayCircle, "Now Playing", "Background, canvas, controls, lyrics, queue", Routes.SETTINGS_NOW_PLAYING),
    InterfaceLink(Icons.Outlined.PlayCircle, "Song swappings", "How the player animates when the song changes", Routes.SETTINGS_SONG_SWAP),
    InterfaceLink(Icons.Outlined.Dashboard, "Sidebars", "Library and queue sidebars: side, width, default state", Routes.SETTINGS_SIDEBARS),
    InterfaceLink(Icons.Outlined.Keyboard, "Keyboard shortcuts", "Play/pause, seek, lyrics, navigation: view and rebind", Routes.SETTINGS_KEYBINDS),
)

@Composable
private fun InterfaceLinkItems(navController: NavController, skipRoute: String?) {
    interfaceLinks.filter { it.route != skipRoute }.forEachIndexed { i, link ->
        if (i > 0) SettingsDivider()
        SettingsNavItem(
            icon = link.icon,
            title = link.title,
            subtitle = link.subtitle,
            onClick = { navController.navigate(link.route) },
        )
    }
}

@Composable
fun AppearanceSettingsScreen(navController: NavController) {
    val prefs = koinInject<DesktopPrefs>()
    @Suppress("UNUSED_VARIABLE")
    val observe = rememberPrefsVersion(prefs)
    val currentMiniLayout = resolveMiniPlayerStyle(prefs)
    val miniBg = prefs.enumPref(PrefKeys.MiniPlayerBackgroundStyle, PlayerBackgroundStyle.GRADIENT)
    val topNav = prefs.enumPref(PrefKeys.TopNavStyle, TopNavStyle.ICON_ONLY)

    SettingsPage(title = "Interface", onBack = { navController.navigateUp() }) {
        SettingsSection("More interface settings") {
            InterfaceLinkItems(navController, skipRoute = Routes.SETTINGS_APPEARANCE)
        }

        SettingsSection("Typography") {
            FontPairingContent(prefs)
        }

        SettingsSection("Layout") {
            SwitchItem(
                icon = Icons.Outlined.Tune,
                title = "Adwaita-Inspired",
                subtitle = "Centered, compact settings and player controls. Keeps your colors and style.",
                checked = prefs.getBoolean(PrefKeys.AdwaitaInspired, false),
                onChecked = { prefs.setBoolean(PrefKeys.AdwaitaInspired, it) },
            )
            SettingsDivider()
            SwitchItem(
                icon = Icons.Outlined.Tune,
                title = "Material 3 Expressive Blur",
                subtitle = "Surfaces over other content (mini-player, player controls, queue tray) become translucent frosted glass. Same design, blurred backdrop.",
                checked = prefs.getBoolean(PrefKeys.GlassBlur, false),
                onChecked = { prefs.setBoolean(PrefKeys.GlassBlur, it) },
            )
            if (prefs.getBoolean(PrefKeys.GlassBlur, false)) {
                SettingsDivider()
                PrefSecondsSlider(
                    title = "Page background darkness",
                    value = prefs.getInt(PrefKeys.PageBackgroundDarkness, 50).coerceIn(0, 100).toFloat(),
                    valueRange = 0f..100f,
                    steps = 19,
                    valueLabel = { "${it.toInt()}%" },
                    onCommit = { prefs.setInt(PrefKeys.PageBackgroundDarkness, it.toInt()) },
                )
                ChipChoiceRow(
                    icon = Icons.Outlined.Wallpaper,
                    title = "Page background style",
                    options = PlayerBackgroundStyle.entries.map { it to it.label() },
                    selected = prefs.enumPref(PrefKeys.PageBackgroundStyle, PlayerBackgroundStyle.BLUR),
                    onSelect = { prefs.setString(PrefKeys.PageBackgroundStyle, it.name) },
                )
            }
        }

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
            PrefSecondsSlider(
                title = "Darkness",
                value = prefs.getInt(PrefKeys.MiniPlayerBackgroundDarkness, 0).coerceIn(0, 100).toFloat(),
                valueRange = 0f..100f,
                steps = 19,
                valueLabel = { "${it.toInt()}%" },
                onCommit = { prefs.setInt(PrefKeys.MiniPlayerBackgroundDarkness, it.toInt()) },
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
        SettingsSection("Fullscreen") {
            SettingsNavItem(
                icon = Icons.Outlined.OpenInFull,
                title = "Fullscreen player",
                subtitle = "Layout, cover, controls, auto-hide, background overrides",
                onClick = { navController.navigate(Routes.SETTINGS_FULLSCREEN_PLAYER) },
            )
        }

        SettingsSection("Cover") {
            SwitchItem(
                icon = Icons.Outlined.CropSquare,
                title = "Square cover",
                subtitle = "Show the cover and canvas with square corners",
                checked = prefs.getBoolean(PrefKeys.SquareCover, false),
                onChecked = { prefs.setBoolean(PrefKeys.SquareCover, it) },
            )
        }

        SettingsSection("Background") {
            LivePlayerBackgroundHeroPreview(style = playerBg)
            PrefSecondsSlider(
                title = "Darkness",
                value = prefs.getInt(PrefKeys.PlayerBackgroundDarkness, 0).coerceIn(0, 100).toFloat(),
                valueRange = 0f..100f,
                steps = 19,
                valueLabel = { "${it.toInt()}%" },
                onCommit = { prefs.setInt(PrefKeys.PlayerBackgroundDarkness, it.toInt()) },
            )
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
                subtitle = "Animated album art in place of the cover when available, with any background",
                checked = canvasOn,
                onChecked = { prefs.setBoolean(PrefKeys.EnableCanvas, it) },
            )
            AnimatedVisibility(canvasOn, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column {
                    SettingsDivider()
                    SwitchItem(
                        icon = Icons.Outlined.SmartDisplay,
                        title = "Use GPU for canvas",
                        subtitle = "Off uses CPU decoding. GPU decoding may crash on incompatible drivers.",
                        checked = prefs.getBoolean(PrefKeys.CanvasUseGpu, false),
                        onChecked = { prefs.setBoolean(PrefKeys.CanvasUseGpu, it) },
                    )
                    SettingsDivider()
                    SwitchItem(
                        icon = Icons.Outlined.MovieFilter,
                        title = "Ping-pong canvas",
                        subtitle = "Play forward, then backward. Off uses a smooth forward loop.",
                        checked = prefs.getBoolean(PrefKeys.CanvasPingPong, false),
                        onChecked = { prefs.setBoolean(PrefKeys.CanvasPingPong, it) },
                    )
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

        SettingsSection("Music video") {
            val videoOn = prefs.getBoolean(PrefKeys.PlayerVideoEnabled, false)
            SwitchItem(
                icon = Icons.Outlined.SmartDisplay,
                title = "Play music videos",
                subtitle = "Show the music video instead of the cover or canvas when one exists. " +
                    "Uses more data and CPU; audio still plays from the normal stream. Local files and tracks without a video fall back to canvas or cover.",
                checked = videoOn,
                onChecked = { prefs.setBoolean(PrefKeys.PlayerVideoEnabled, it) },
            )
            AnimatedVisibility(videoOn, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column {
                    SettingsDivider()
                    val framing = com.music.vivi.compose.ui.player.video.VideoFraming.resolve(prefs.reader())
                    ChoiceRow(
                        icon = Icons.Outlined.OpenInFull,
                        title = "Framing",
                        options = com.music.vivi.compose.ui.player.video.VideoFraming.entries.map { it to it.name.replace("Original", "Original size") },
                        selected = framing,
                        onSelect = { prefs.setString(PrefKeys.PlayerVideoFraming, it.name) },
                    )
                    Text(
                        when (framing) {
                            com.music.vivi.compose.ui.player.video.VideoFraming.Fit -> "Fit: the video keeps its shape inside the square cover, with black bars."
                            com.music.vivi.compose.ui.player.video.VideoFraming.Fill -> "Fill: the video is cropped to fill the square cover."
                            com.music.vivi.compose.ui.player.video.VideoFraming.Original -> "Original size: the cover morphs into the video's own shape (usually 16:9) and shows it uncropped."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 56.dp, end = 16.dp, bottom = 12.dp),
                    )
                    SettingsDivider()
                    ChoiceRow(
                        icon = Icons.Outlined.Tune,
                        title = "Quality (Auto adapts to your connection speed)",
                        options = com.music.vivi.player.video.VideoQuality.entries.map { it to it.label },
                        selected = com.music.vivi.player.video.VideoQuality.parse(prefs.getString(PrefKeys.PlayerVideoQuality, "AUTO")),
                        onSelect = { prefs.setString(PrefKeys.PlayerVideoQuality, it.name) },
                    )
                }
            }
        }

        SettingsSection("Play bar") {
            val limitWidth = prefs.getBoolean(PrefKeys.LimitPlayerControlsWidth, prefs.getBoolean(PrefKeys.AdwaitaInspired, false))
            SwitchItem(
                icon = Icons.Outlined.Tune,
                title = "Limit controls width",
                subtitle = "Center the controls and limit their width; shrink to fit smaller windows",
                checked = limitWidth,
                onChecked = { prefs.setBoolean(PrefKeys.LimitPlayerControlsWidth, it) },
            )
            if (limitWidth) {
                PrefSecondsSlider(
                    title = "Maximum width",
                    value = prefs.getInt(PrefKeys.PlayerControlsWidth, 600).toFloat(),
                    valueRange = 400f..600f,
                    steps = 9,
                    valueLabel = { "${it.toInt()} dp" },
                    onCommit = { prefs.setInt(PrefKeys.PlayerControlsWidth, it.toInt()) },
                )
            }
            PrefSecondsSlider(
                title = "Play / pause height",
                value = prefs.getInt(PrefKeys.PlayPauseHeight, 56).toFloat(),
                valueRange = 48f..96f,
                steps = 5,
                valueLabel = { "${it.toInt()} dp" },
                onCommit = { prefs.setInt(PrefKeys.PlayPauseHeight, it.toInt()) },
            )
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
            val slider = prefs.enumPref(PrefKeys.SliderStyle, SliderStyle.M3_EXPRESSIVE)
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
            SettingsNavItem(
                icon = Icons.Outlined.Lyrics,
                title = "Lyrics",
                subtitle = "Style, word-by-word sync, glow, animation, background",
                onClick = { navController.navigate(Routes.SETTINGS_LYRICS) },
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

@Composable
fun FullscreenPlayerSettingsScreen(navController: NavController) {
    val prefs = koinInject<DesktopPrefs>()
    @Suppress("UNUSED_VARIABLE")
    val observe = rememberPrefsVersion(prefs)
    val cfg = FullscreenSettings.resolve(prefs.reader())
    fun bool(key: String) = prefs.getBoolean(key, FullscreenSettings.defaults.getValue(key).toBoolean())
    val bgOverride = prefs.getString(FullscreenKeys.Background, FullscreenFollow)
    val canvasMode = prefs.enumPref(FullscreenKeys.CanvasMode, FullscreenCanvasMode.FOLLOW)

    SettingsPage(title = "Fullscreen player", onBack = { navController.navigateUp() }) {
        SettingsSection("Layout") {
            ChoiceRow(
                icon = Icons.Outlined.OpenInFull,
                title = "Cover position",
                options = listOf(
                    FullscreenLayout.COVER_LEFT to "Left",
                    FullscreenLayout.CENTERED to "Centered",
                    FullscreenLayout.COVER_RIGHT to "Right",
                ),
                selected = cfg.layout,
                onSelect = { prefs.setString(FullscreenKeys.Layout, it.name) },
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
            PrefSecondsSlider(
                title = "Cover size",
                value = (cfg.coverScale * 100f),
                valueRange = 50f..100f,
                steps = 9,
                valueLabel = { "${it.toInt()}%" },
                onCommit = { prefs.setInt(FullscreenKeys.CoverScale, it.toInt()) },
            )
            SettingsDivider()
            SwitchItem(
                icon = Icons.Outlined.Lyrics,
                title = "Show lyrics by default",
                subtitle = "Open the fullscreen player with the lyrics panel visible",
                checked = cfg.lyricsByDefault,
                onChecked = { prefs.setBoolean(FullscreenKeys.LyricsByDefault, it) },
            )
        }

        SettingsSection("Show") {
            SwitchItem(Icons.Outlined.Person, "Artist links", null, cfg.showArtist) { prefs.setBoolean(FullscreenKeys.ShowArtist, it) }
            SettingsDivider()
            SwitchItem(Icons.Outlined.Album, "Album", null, cfg.showAlbum) { prefs.setBoolean(FullscreenKeys.ShowAlbum, it) }
            SettingsDivider()
            SwitchItem(Icons.AutoMirrored.Outlined.QueueMusic, "Queue button", null, cfg.showQueueButton) { prefs.setBoolean(FullscreenKeys.ShowQueueButton, it) }
            SettingsDivider()
            SwitchItem(Icons.AutoMirrored.Outlined.VolumeUp, "Volume slider", null, cfg.showVolume) { prefs.setBoolean(FullscreenKeys.ShowVolume, it) }
            SettingsDivider()
            SwitchItem(Icons.Outlined.Shuffle, "Shuffle button", null, cfg.showShuffle) { prefs.setBoolean(FullscreenKeys.ShowShuffle, it) }
            SettingsDivider()
            SwitchItem(Icons.Outlined.Repeat, "Repeat button", null, cfg.showRepeat) { prefs.setBoolean(FullscreenKeys.ShowRepeat, it) }
            SettingsDivider()
            SwitchItem(Icons.Outlined.Radio, "Radio button", "Start a radio from the current track", cfg.showRadio) { prefs.setBoolean(FullscreenKeys.ShowRadio, it) }
            SettingsDivider()
            SwitchItem(Icons.Outlined.Schedule, "Time labels", "Elapsed and total time under the seek bar", cfg.showTimes) { prefs.setBoolean(FullscreenKeys.ShowTimes, it) }
            SettingsDivider()
            SwitchItem(Icons.Outlined.Schedule, "Clock", "Show the time of day at the top", cfg.showClock) { prefs.setBoolean(FullscreenKeys.ShowClock, it) }
        }

        SettingsSection("Background") {
            ChipChoiceRow(
                icon = Icons.Outlined.Wallpaper,
                title = "Style",
                options = listOf(FullscreenFollow to "Follow Now Playing") +
                    PlayerBackgroundStyle.entries.map { it.name to it.label() },
                selected = if (bgOverride == FullscreenFollow || PlayerBackgroundStyle.entries.any { it.name == bgOverride }) bgOverride else FullscreenFollow,
                onSelect = { prefs.setString(FullscreenKeys.Background, it) },
            )
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

        SettingsSection("Controls") {
            SwitchItem(
                icon = Icons.Outlined.Tune,
                title = "Auto-hide controls",
                subtitle = "Fade the controls and cursor after the mouse is still",
                checked = cfg.autoHide,
                onChecked = { prefs.setBoolean(FullscreenKeys.AutoHide, it) },
            )
            AnimatedVisibility(cfg.autoHide, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                PrefSecondsSlider(
                    title = "Hide after",
                    value = cfg.autoHideSeconds.toFloat(),
                    valueRange = 2f..30f,
                    steps = 13,
                    valueLabel = { "${it.toInt()} s" },
                    onCommit = { prefs.setInt(FullscreenKeys.AutoHideSeconds, it.toInt()) },
                )
            }
        }

        SettingsSection("Reset") {
            SettingsNavItem(
                icon = Icons.Outlined.Restore,
                title = "Reset to Now Playing settings",
                subtitle = "Clear every fullscreen override and follow Now Playing again",
                onClick = { prefs.resetFullscreenPrefs() },
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
    val saavn = prefs.getBoolean(PrefKeys.SaavnEnabled, com.music.vivi.nativelib.AppEnv.get("SAAVN") != "0")

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
            SwitchItem(
                icon = Icons.Outlined.Groups,
                title = "Discord: song button",
                subtitle = "Add a button that opens the current song on YouTube Music",
                checked = prefs.getBoolean(PrefKeys.DiscordRpcSongButton, false),
                onChecked = { prefs.setBoolean(PrefKeys.DiscordRpcSongButton, it) },
            )
            SwitchItem(
                icon = Icons.Outlined.Groups,
                title = "Discord: VIsage button",
                subtitle = "Add a button that opens the VIsage GitHub page",
                checked = prefs.getBoolean(PrefKeys.DiscordRpcVisageButton, false),
                onChecked = { prefs.setBoolean(PrefKeys.DiscordRpcVisageButton, it) },
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
    var confirmSignOut by remember { mutableStateOf(false) }
    var loginKey by remember { mutableStateOf(0) }
    SettingsPage(title = "Account", onBack = { navController.navigateUp() }) {
        AccountCard(account, error, onClick = null)
        Text(
            "Your session is stored on this device. Sign in again below if it expires.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp),
        )
        if (account != null) {
            Row(Modifier.padding(horizontal = 4.dp).padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.music.vivi.compose.ui.expressive.ExpressiveButton(
                    text = "Manage Google account",
                    onClick = { openExternalUrl("https://myaccount.google.com/") },
                    style = com.music.vivi.compose.ui.expressive.ExpressiveStyle.Tonal,
                )
                com.music.vivi.compose.ui.expressive.ExpressiveButton(
                    text = "Sign out",
                    onClick = { confirmSignOut = true },
                    style = com.music.vivi.compose.ui.expressive.ExpressiveStyle.Outlined,
                )
            }
        }
        if (confirmSignOut) {
            SignOutDialog(
                onDismiss = { confirmSignOut = false },
                onConfirm = {
                    confirmSignOut = false
                    scope.launch {
                        performSignOut(yt)
                        account = null
                        error = "Not signed in"
                        loginKey++
                    }
                },
            )
        }
        androidx.compose.runtime.key(loginKey) { com.music.vivi.compose.ui.YtLoginPanel(
            modifier = Modifier.padding(horizontal = 4.dp),
            onLoggedIn = {
                scope.launch {
                    error = null
                    yt.accountInfo().onSuccess { account = it }.onFailure { error = it.message ?: it.toString() }
                }
            },
        ) }
    }
}

/** Deletes the stored session, the live client session and the embedded browser's Google sign-in. */
private suspend fun performSignOut(yt: YouTubeMusicService) {
    withContext(Dispatchers.IO) {
        yt.signOut()
        com.music.vivi.compose.ui.clearEmbeddedLoginProfile()
    }
}

@Composable
private fun SignOutDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sign out?") },
        text = { Text("VIsage forgets your YouTube Music account on this device. Local music and downloads stay.") },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onConfirm) { Text("Sign out") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// —— shared building blocks ————————————————————————————————

/** Scrolling settings page, centered at a readable width on wide windows. */
@Composable
internal fun SettingsPage(
    title: String,
    onBack: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val prefs = koinInject<DesktopPrefs>()
    @Suppress("UNUSED_VARIABLE")
    val observe = rememberPrefsVersion(prefs)
    val adwaita = prefs.getBoolean(PrefKeys.AdwaitaInspired, false)
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize().smoothWheelScroll(scroll).verticalScroll(scroll), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = if (adwaita) 600.dp else 760.dp)
                .fillMaxWidth()
                .padding(horizontal = if (adwaita) 12.dp else 24.dp)
                .padding(top = if (adwaita) 24.dp else 8.dp, bottom = 120.dp),
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
            com.music.vivi.compose.ui.component.VisageBackButton(onClick = onBack)
            Spacer(Modifier.width(14.dp))
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
internal fun SettingsRowFrame(
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
internal fun SettingsNavItem(
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
internal fun <T> ChoiceRow(
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
            com.music.vivi.compose.ui.expressive.ConnectedButtonGroup(
                options = options,
                selected = local,
                onSelect = { local = it; onSelect(it) },
                modifier = modifier,
            )
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
internal fun <T> ChipChoiceRow(
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
internal fun PrefSecondsSlider(
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

private const val SUPPORT_URL = "https://ko-fi.com/vndreiii"

/** Opens [url] in the default browser. Failure is ignored: there is nothing useful to show. */
internal fun openExternalUrl(url: String) {
    Thread {
        runCatching {
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(java.net.URI(url))
            } else {
                ProcessBuilder(if (com.music.vivi.nativelib.HostOs.isWindows) "explorer" else "xdg-open", url).start()
            }
        }
    }.apply { isDaemon = true }.start()
}
