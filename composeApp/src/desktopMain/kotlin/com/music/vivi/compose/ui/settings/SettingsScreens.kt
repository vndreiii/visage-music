package com.music.vivi.compose.ui.settings

import com.music.vivi.compose.ui.expressive.VisageTextField
import com.music.vivi.compose.ui.component.visageMarquee
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MovieFilter
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.alpha
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
import com.music.vivi.compose.prefs.FullscreenCoverSide
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
import com.music.vivi.compose.ui.component.pageScrollEdge
import com.music.vivi.compose.ui.component.edgeProgress
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
    PlayerBackgroundStyle.RINGS -> "Rings"
}

// —— Hub ——————————————————————————————————————————————————————

@Composable
fun SettingsHubScreen(navController: NavController) {
    val yt = koinInject<YouTubeMusicService>()
    val prefs = koinInject<DesktopPrefs>()
    var account by remember { mutableStateOf<AccountInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(Unit) {
        yt.accountInfo()
            .onSuccess { account = it }
            .onFailure { error = it.message ?: it.toString() }
    }

    SettingsPage(title = "Settings", onBack = null, searchNav = navController) {
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
                icon = Icons.Outlined.Restore,
                title = "Replay tour",
                subtitle = "Walk through Home, Search, Library, the queue and Settings again",
                onClick = { prefs.setBoolean(PrefKeys.OnboardingReplay, true) },
            )
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
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.visageMarquee(),
                )
                Text(
                    account?.email ?: "YouTube Music account",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.visageMarquee(),
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
                // Dynamic color and Matugen are two sources for the same palette, so turning one on turns the other off.
                onChecked = { on ->
                    prefs.setBoolean(PrefKeys.DynamicTheme, on)
                    if (on) prefs.setBoolean(PrefKeys.MatugenTheme, false)
                },
            )
            SettingsDivider()
            val matugenSupported = !com.music.vivi.nativelib.HostOs.isWindows
            val matugenOn = matugenSupported && prefs.getBoolean(PrefKeys.MatugenTheme, false)
            val matugenReady = matugenSupported && MatugenColors.defaultFile.isFile
            SwitchItem(
                icon = Icons.Outlined.Wallpaper,
                title = "Matugen wallpaper colors",
                enabled = matugenSupported,
                subtitle = if (!matugenSupported) {
                    "Linux only"
                } else if (matugenReady) {
                    "Use ~/.cache/matugen/visage.colors"
                } else {
                    "Run matugen with the visage template first"
                },
                checked = matugenOn,
                onChecked = { on ->
                    prefs.setBoolean(PrefKeys.MatugenTheme, on)
                    if (on) prefs.setBoolean(PrefKeys.DynamicTheme, false)
                },
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
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.visageMarquee(),
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
    val glass = prefs.getBoolean(PrefKeys.GlassBlur, com.music.vivi.compose.prefs.LookDefaults.GlassBlur)
    val pageStyle = prefs.enumPref(PrefKeys.PageBackgroundStyle, com.music.vivi.compose.prefs.LookDefaults.PageStyle)
    val pageDarkness = prefs.getInt(PrefKeys.PageBackgroundDarkness, com.music.vivi.compose.prefs.LookDefaults.PageDarkness).coerceIn(0, 100)

    SettingsPage(title = "Interface", onBack = { navController.navigateUp() }) {
        SettingsSection("Top bar", preview = { LiveTopNavHeroPreview(style = topNav) }) {
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

        SettingsSection("Mini-player", preview = { LiveMiniPlayerHeroPreview(layout = currentMiniLayout, background = miniBg) }) {
            ChoiceRow(
                icon = Icons.Outlined.SmartDisplay,
                title = "Mini-player layout",
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
            SettingsDropdownRow(
                icon = Icons.Outlined.Wallpaper,
                title = "Mini-player background",
                options = PlayerBackgroundStyle.entries
                    .filter { it != PlayerBackgroundStyle.APPLE_MUSIC }
                    .map { DropdownOption(it, it.label()) },
                selected = miniBg,
                onSelect = { prefs.setString(PrefKeys.MiniPlayerBackgroundStyle, it.name) },
            )
            VisibleWhen(miniBg == PlayerBackgroundStyle.RINGS) { RingsControls(prefs) }
            PrefSecondsSlider(
                title = com.music.vivi.compose.theme.backdropLevelName(),
                value = prefs.getInt(PrefKeys.MiniPlayerBackgroundDarkness, 0).coerceIn(0, 100).toFloat(),
                valueRange = 0f..100f,
                steps = 19,
                valueLabel = { "${it.toInt()}%" },
                liveUpdate = true,
                defaultValue = 0f,
                onCommit = { prefs.setInt(PrefKeys.MiniPlayerBackgroundDarkness, it.toInt()) },
            )
            SettingsDivider()
            SwitchItem(
                icon = Icons.Outlined.Contrast,
                title = "Pure black mini-player",
                subtitle = "Solid black instead of the tinted background",
                checked = prefs.getBoolean(PrefKeys.PureBlackMiniPlayer, false),
                onChecked = { prefs.setBoolean(PrefKeys.PureBlackMiniPlayer, it) },
            )
        }

        SettingsSection(
            "Glass and backdrop",
            preview = if (glass) {
                {
                    LivePlayerBackgroundHeroPreview(
                        style = pageStyle, darkness = pageDarkness / 100f, id = "pageBackground",
                        caption = "The page background behind every screen",
                    )
                }
            } else null,
        ) {
            SwitchItem(
                icon = Icons.Outlined.Tune,
                title = "Material 3 Expressive Blur",
                subtitle = "Mini-player, player controls and queue tray become frosted glass over a tinted page background",
                checked = glass,
                onChecked = { prefs.setBoolean(PrefKeys.GlassBlur, it) },
            )
            VisibleWhen(glass) {
                SettingsDropdownRow(
                    icon = Icons.Outlined.Wallpaper,
                    title = "Page background",
                    options = PlayerBackgroundStyle.entries.map { DropdownOption(it, it.label()) },
                    selected = pageStyle,
                    onSelect = { prefs.setString(PrefKeys.PageBackgroundStyle, it.name) },
                )
                VisibleWhen(pageStyle == PlayerBackgroundStyle.RINGS) { RingsControls(prefs) }
                PrefSecondsSlider(
                    title = "Page background ${com.music.vivi.compose.theme.backdropLevelName().lowercase()}",
                    value = pageDarkness.toFloat(),
                    valueRange = 0f..100f,
                    steps = 19,
                    valueLabel = { "${it.toInt()}%" },
                    liveUpdate = true,
                    defaultValue = com.music.vivi.compose.prefs.LookDefaults.PageDarkness.toFloat(),
                    onCommit = { prefs.setInt(PrefKeys.PageBackgroundDarkness, it.toInt()) },
                )
            }
            SettingsDivider()
            SwitchItem(
                icon = Icons.Outlined.Tune,
                title = "Adwaita-Inspired",
                subtitle = "Centered, compact settings and player controls; keeps your colors and style",
                checked = prefs.getBoolean(PrefKeys.AdwaitaInspired, false),
                onChecked = { prefs.setBoolean(PrefKeys.AdwaitaInspired, it) },
            )
        }

        SettingsSection("Typography") {
            FontPairingContent(prefs)
        }

        SettingsSection("Home") {
            val autoQuick = prefs.getBoolean(PrefKeys.QuickPicksAutoRefresh, true)
            SwitchItem(
                icon = Icons.Outlined.Refresh,
                title = "Refresh Quick picks automatically",
                subtitle = "Build a new set once the interval has passed; until then your last refresh stays",
                checked = autoQuick,
                onChecked = { prefs.setBoolean(PrefKeys.QuickPicksAutoRefresh, it) },
            )
            VisibleWhen(autoQuick) {
                PrefSecondsSlider(
                    title = "Refresh interval",
                    value = prefs.getInt(PrefKeys.QuickPicksIntervalHours, 24)
                        .coerceIn(1, 168).toFloat(),
                    valueRange = 1f..168f,
                    steps = 166,
                    valueLabel = { h ->
                        val hours = h.toInt()
                        if (hours < 48) "${hours}h" else "${hours / 24}d ${hours % 24}h"
                    },
                    defaultValue = 24f,
                    onCommit = { prefs.setInt(PrefKeys.QuickPicksIntervalHours, it.toInt().coerceIn(1, 168)) },
                )
            }
            SettingsDivider()
            SwitchItem(
                icon = Icons.Outlined.Refresh,
                title = "Manual refresh button",
                subtitle = "Show the refresh button on the Quick picks shelf",
                checked = prefs.getBoolean(PrefKeys.QuickPicksManualRefresh, true),
                onChecked = { prefs.setBoolean(PrefKeys.QuickPicksManualRefresh, it) },
            )
        }

        SettingsSection("More interface settings") {
            InterfaceLinkItems(navController, skipRoute = Routes.SETTINGS_APPEARANCE)
        }
    }
}

// —— Now Playing ——————————————————————————————————————————————

@Composable
fun NowPlayingSettingsScreen(navController: NavController) {
    val prefs = koinInject<DesktopPrefs>()
    @Suppress("UNUSED_VARIABLE")
    val observe = rememberPrefsVersion(prefs)
    val playerBg = prefs.enumPref(PrefKeys.PlayerBackgroundStyle, com.music.vivi.compose.prefs.LookDefaults.PlayerStyle)
    val canvasOn = prefs.getBoolean(PrefKeys.EnableCanvas, true)
    val playerDarkness = prefs.getInt(PrefKeys.PlayerBackgroundDarkness, com.music.vivi.compose.prefs.LookDefaults.PlayerDarkness).coerceIn(0, 100)
    val limitWidth = prefs.getBoolean(PrefKeys.LimitPlayerControlsWidth, prefs.getBoolean(PrefKeys.AdwaitaInspired, false))
    val slider = prefs.enumPref(PrefKeys.SliderStyle, SliderStyle.M3_EXPRESSIVE)
    val track by com.music.vivi.compose.LocalPlaybackController.current.nowPlaying.collectAsState()

    SettingsPage(title = "Now Playing", onBack = { navController.navigateUp() }) {
        SettingsSection(
            "Cover and controls",
            preview = {
                LivePlayerLayoutHeroPreview(
                    framed = prefs.getBoolean(PrefKeys.UsePlayerV2, false),
                    buttons = prefs.enumPref(PrefKeys.PlayerButtonsStyle, PlayerButtonsStyle.DEFAULT),
                    playPauseHeight = prefs.getInt(PrefKeys.PlayPauseHeight, 56).dp,
                    slider = slider,
                    squiggly = prefs.getBoolean(PrefKeys.SquigglySlider, false),
                    squareCover = prefs.getBoolean(PrefKeys.SquareCover, false),
                    hideCover = prefs.getBoolean(PrefKeys.HidePlayerThumbnail, false),
                    limitWidth = if (limitWidth) prefs.getInt(PrefKeys.PlayerControlsWidth, 600).dp else null,
                    artworkUrl = track?.artworkUrl,
                    title = track?.title ?: "Your next favourite",
                    artist = track?.artist?.ifBlank { null } ?: "Now Playing preview",
                )
            },
        ) {
            SwitchItem(
                icon = Icons.Outlined.CropSquare,
                title = "Square cover",
                subtitle = "Show the cover and canvas with square corners",
                checked = prefs.getBoolean(PrefKeys.SquareCover, false),
                onChecked = { prefs.setBoolean(PrefKeys.SquareCover, it) },
            )
            SettingsDivider()
            SwitchItem(
                icon = Icons.Outlined.Contrast,
                title = "Hide cover art",
                subtitle = "Show the controls without the cover",
                checked = prefs.getBoolean(PrefKeys.HidePlayerThumbnail, false),
                onChecked = { prefs.setBoolean(PrefKeys.HidePlayerThumbnail, it) },
            )
            SettingsDivider()
            SwitchItem(
                icon = Icons.Outlined.SmartDisplay,
                title = "Framed controls",
                subtitle = "Put the controls on a translucent panel",
                checked = prefs.getBoolean(PrefKeys.UsePlayerV2, false),
                onChecked = { prefs.setBoolean(PrefKeys.UsePlayerV2, it) },
            )
            SettingsDivider()
            ChoiceRow(
                icon = Icons.Outlined.PlayCircle,
                title = "Button color",
                options = PlayerButtonsStyle.entries.map { it to it.titleLabel() },
                selected = prefs.enumPref(PrefKeys.PlayerButtonsStyle, PlayerButtonsStyle.DEFAULT),
                onSelect = { prefs.setString(PrefKeys.PlayerButtonsStyle, it.name) },
            )
            SettingsDivider()
            SettingsDropdownRow(
                icon = Icons.Outlined.GraphicEq,
                title = "Seek bar",
                options = SliderStyle.entries.map {
                    DropdownOption(it, if (it == SliderStyle.M3_EXPRESSIVE) "M3 Expressive" else it.titleLabel())
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
            SettingsDivider()
            SwitchItem(
                icon = Icons.Outlined.Tune,
                title = "Limit controls width",
                subtitle = "Center the controls and limit their width; shrink to fit smaller windows",
                checked = limitWidth,
                onChecked = { prefs.setBoolean(PrefKeys.LimitPlayerControlsWidth, it) },
            )
            VisibleWhen(limitWidth) {
                PrefSecondsSlider(
                    title = "Maximum width",
                    value = prefs.getInt(PrefKeys.PlayerControlsWidth, 600).toFloat(),
                    valueRange = 400f..600f,
                    steps = 9,
                    valueLabel = { "${it.toInt()} dp" },
                    liveUpdate = true,
                    defaultValue = 600f,
                    onCommit = { prefs.setInt(PrefKeys.PlayerControlsWidth, it.toInt()) },
                )
            }
            PrefSecondsSlider(
                title = "Play / pause height",
                value = prefs.getInt(PrefKeys.PlayPauseHeight, 56).toFloat(),
                valueRange = 48f..96f,
                steps = 5,
                valueLabel = { "${it.toInt()} dp" },
                liveUpdate = true,
                defaultValue = 56f,
                onCommit = { prefs.setInt(PrefKeys.PlayPauseHeight, it.toInt()) },
            )
        }

        SettingsSection(
            "Background",
            preview = { LivePlayerBackgroundHeroPreview(style = playerBg, darkness = playerDarkness / 100f) },
        ) {
            SettingsDropdownRow(
                icon = Icons.Outlined.Wallpaper,
                title = "Player background",
                options = PlayerBackgroundStyle.entries.map { DropdownOption(it, it.label()) },
                selected = playerBg,
                onSelect = { prefs.setString(PrefKeys.PlayerBackgroundStyle, it.name) },
            )
            VisibleWhen(playerBg == PlayerBackgroundStyle.RINGS) { RingsControls(prefs) }
            PrefSecondsSlider(
                title = com.music.vivi.compose.theme.backdropLevelName(),
                value = playerDarkness.toFloat(),
                valueRange = 0f..100f,
                steps = 19,
                valueLabel = { "${it.toInt()}%" },
                liveUpdate = true,
                defaultValue = com.music.vivi.compose.prefs.LookDefaults.PlayerDarkness.toFloat(),
                onCommit = { prefs.setInt(PrefKeys.PlayerBackgroundDarkness, it.toInt()) },
            )
        }

        SettingsSection("Canvas video") {
            SwitchItem(
                icon = Icons.Outlined.MovieFilter,
                title = "Canvas video",
                subtitle = "Animated album art in place of the cover when available, with any background",
                checked = canvasOn,
                onChecked = { prefs.setBoolean(PrefKeys.EnableCanvas, it) },
            )
            VisibleWhen(canvasOn) {
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
                SettingsDivider()
                SwitchItem(
                    icon = Icons.Outlined.MovieFilter,
                    title = "Ping-pong canvas",
                    subtitle = "Play forward, then backward; off uses a smooth forward loop",
                    checked = prefs.getBoolean(PrefKeys.CanvasPingPong, false),
                    onChecked = { prefs.setBoolean(PrefKeys.CanvasPingPong, it) },
                )
                AdvancedExpander(Icons.Outlined.Tune, "Advanced", "Hardware decoding") {
                    SwitchItem(
                        icon = Icons.Outlined.SmartDisplay,
                        title = "Use GPU for canvas",
                        subtitle = "Off uses CPU decoding; GPU decoding may crash on incompatible drivers",
                        checked = prefs.getBoolean(PrefKeys.CanvasUseGpu, false),
                        onChecked = { prefs.setBoolean(PrefKeys.CanvasUseGpu, it) },
                    )
                }
            }
        }

        SettingsSection("Music video") {
            val videoOn = prefs.getBoolean(PrefKeys.PlayerVideoEnabled, false)
            SwitchItem(
                icon = Icons.Outlined.SmartDisplay,
                title = "Play music videos",
                subtitle = "Replaces the cover or canvas when a video exists; uses more data and CPU",
                checked = videoOn,
                onChecked = { prefs.setBoolean(PrefKeys.PlayerVideoEnabled, it) },
            )
            VisibleWhen(videoOn) {
                SettingsDivider()
                val framing = com.music.vivi.compose.ui.player.video.VideoFraming.resolve(prefs.reader())
                ChoiceRow(
                    icon = Icons.Outlined.OpenInFull,
                    title = "Video framing",
                    subtitle = when (framing) {
                        com.music.vivi.compose.ui.player.video.VideoFraming.Fit -> "Keeps the video's shape inside the square cover, with black bars"
                        com.music.vivi.compose.ui.player.video.VideoFraming.Fill -> "Crops the video to fill the square cover"
                        com.music.vivi.compose.ui.player.video.VideoFraming.Original -> "The cover morphs into the video's own shape, uncropped"
                    },
                    options = com.music.vivi.compose.ui.player.video.VideoFraming.entries.map { it to it.name.replace("Original", "Original size") },
                    selected = framing,
                    onSelect = { prefs.setString(PrefKeys.PlayerVideoFraming, it.name) },
                )
                SettingsDivider()
                SettingsDropdownRow(
                    icon = Icons.Outlined.Tune,
                    title = "Video quality",
                    subtitle = "Auto adapts to your connection speed",
                    options = com.music.vivi.player.video.VideoQuality.entries.map { DropdownOption(it, it.label) },
                    selected = com.music.vivi.player.video.VideoQuality.parse(prefs.getString(PrefKeys.PlayerVideoQuality, "AUTO")),
                    onSelect = { prefs.setString(PrefKeys.PlayerVideoQuality, it.name) },
                )
            }
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
                selected = prefs.enumPref(PrefKeys.QueuePanelMode, QueuePanelMode.PINNED_RIGHT),
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

        SettingsSection("Lyrics") {
            SettingsNavItem(
                icon = Icons.Outlined.Lyrics,
                title = "Lyrics",
                subtitle = "Style, word-by-word sync, glow, animation, background",
                onClick = { navController.navigate(Routes.SETTINGS_LYRICS) },
            )
            SettingsDivider()
            val wordLevel = setOf(LyricsProvider.BETTER_LYRICS, LyricsProvider.PAXSENIX, LyricsProvider.YOULY_PLUS, LyricsProvider.MUSIXMATCH)
            SettingsDropdownRow(
                icon = Icons.Outlined.AutoAwesome,
                title = "Lyrics provider",
                options = LyricsProvider.entries
                    .sortedBy { if (it in wordLevel) 0 else 1 }
                    .map { DropdownOption(it, it.label, group = if (it in wordLevel) "Word-level" else "Line-level") },
                selected = prefs.enumPref(PrefKeys.LyricsProvider, LyricsProvider.LRCLIB),
                onSelect = { prefs.setString(PrefKeys.LyricsProvider, it.name) },
            )
        }

        SettingsSection("Fullscreen") {
            SettingsNavItem(
                icon = Icons.Outlined.OpenInFull,
                title = "Fullscreen player",
                subtitle = "Layout, cover, controls, auto-hide, background overrides",
                onClick = { navController.navigate(Routes.SETTINGS_FULLSCREEN_PLAYER) },
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
            VisibleWhen(crossfade) {
                PrefSecondsSlider(
                    title = "Crossfade length",
                    valueLabel = { "${it.toInt()} s" },
                    value = prefs.getInt(PrefKeys.CrossfadeSeconds, 3).toFloat(),
                    valueRange = 1f..10f,
                    steps = 8,
                    defaultValue = 3f,
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
            VisibleWhen(playPauseFade) {
                PrefSecondsSlider(
                    title = "Fade length",
                    valueLabel = { String.format("%.1f s", it) },
                    value = prefs.getFloat(PrefKeys.PlayPauseFadeSeconds, 0.5f),
                    valueRange = 0.2f..3f,
                    steps = 27,
                    defaultValue = 0.5f,
                    onCommit = {
                        prefs.setFloat(PrefKeys.PlayPauseFadeSeconds, (kotlin.math.round(it * 10f) / 10f).coerceIn(0.2f, 3f))
                    },
                )
            }
        }

        SettingsSection("Discord") {
            val rpc = prefs.getBoolean(PrefKeys.DiscordRpcEnabled, true)
            SwitchItem(
                icon = Icons.Outlined.Groups,
                title = "Discord Rich Presence",
                subtitle = "Show what you're listening to",
                checked = rpc,
                onChecked = { prefs.setBoolean(PrefKeys.DiscordRpcEnabled, it) },
            )
            VisibleWhen(rpc) {
                SettingsDivider()
                SwitchItem(
                    icon = Icons.Outlined.Groups,
                    title = "Discord: song button",
                    subtitle = "Add a button that opens the current song on YouTube Music",
                    checked = prefs.getBoolean(PrefKeys.DiscordRpcSongButton, false),
                    onChecked = { prefs.setBoolean(PrefKeys.DiscordRpcSongButton, it) },
                )
                SettingsDivider()
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
                    "yt-dlp wasn't found. Install it or set VISAGE_YTDLP to enable downloads.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        SettingsSection("Save to") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                VisageTextField(
                    value = pathDraft,
                    onValueChange = { pathDraft = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Folder") },
                    supportingText = { Text("Default: VIsage's own offline folder") },
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

        SettingsSection("Output") {
            ChoiceRow(
                icon = Icons.Outlined.Download,
                title = "Download format",
                options = DownloadAudioFormat.entries.map { it to it.label },
                selected = prefs.enumPref(PrefKeys.DownloadFormat, DownloadAudioFormat.OPUS),
                onSelect = { prefs.setString(PrefKeys.DownloadFormat, it.name) },
            )
            SettingsDivider()
            ChoiceRow(
                icon = Icons.Outlined.HighQuality,
                title = "Download quality",
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
        if (account != null) {
            SettingsSection("Manage") {
                SettingsNavItem(
                    icon = Icons.Outlined.AccountCircle,
                    title = "Manage Google account",
                    subtitle = "Opens myaccount.google.com in your browser",
                    onClick = { openExternalUrl("https://myaccount.google.com/") },
                )
                SettingsDivider()
                SettingsRowFrame(
                    Icons.AutoMirrored.Outlined.Logout, "Sign out", "Your session is stored on this device; this removes it",
                    onClick = { confirmSignOut = true },
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
        SettingsSection("Cookie") {
            ManualCookieSection(
                onImported = { info ->
                    account = info
                    error = null
                    loginKey++
                },
            )
        }
        SettingsSection("Sign in") {
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
}

/** Paste-a-cookie sign-in for accounts the embedded browser does not pick up correctly. */
@Composable
private fun ManualCookieSection(onImported: (AccountInfo) -> Unit) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var showHelp by remember { mutableStateOf(false) }
    AdvancedExpander(
        Icons.Outlined.Tune, "Import cookie manually",
        "Use it when sign-in shows no recommendations or the wrong account",
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            VisageTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Cookie") },
                singleLine = true,
                password = true,
                supportingText = { Text("A login key: never share it. Stored only on this device") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                com.music.vivi.compose.ui.expressive.ExpressiveButton(
                    text = if (busy) "Checking…" else "Import",
                    enabled = text.isNotBlank() && !busy,
                    onClick = {
                        busy = true
                        message = null
                        scope.launch {
                            com.music.vivi.player.login.ManualCookieImport.import(text)
                                .onSuccess { info ->
                                    text = ""
                                    message = "Signed in as ${info.name}."
                                    onImported(info)
                                }
                                .onFailure { message = it.message ?: it.toString() }
                            busy = false
                        }
                    },
                )
                TextButton(onClick = { showHelp = true }) { Text("How to find it") }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (showHelp) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("Finding your cookie") },
            text = {
                Text(
                    "1. In your normal browser, open music.youtube.com and make sure you are on the right " +
                        "account (profile picture, Switch account).\n" +
                        "2. Open developer tools (F12), Network tab, reload, and click any request to " +
                        "music.youtube.com (for example one named \"browse\").\n" +
                        "3. Under Request Headers, copy the whole value of \"cookie\" and paste it here. " +
                        "A cookies.txt export of youtube.com also works.\n\n" +
                        "Signing out of Google in your browser invalidates the cookie.",
                )
            },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("Close") } },
        )
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
    searchNav: NavController? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val prefs = koinInject<DesktopPrefs>()
    @Suppress("UNUSED_VARIABLE")
    val observe = rememberPrefsVersion(prefs)
    val adwaita = prefs.getBoolean(PrefKeys.AdwaitaInspired, false)
    val scroll = rememberScrollState()
    val viewport = remember(scroll) { SettingsViewport(scroll) }
    androidx.compose.runtime.CompositionLocalProvider(LocalSettingsViewport provides viewport) {
    Box(Modifier.fillMaxSize().pageScrollEdge { scroll.edgeProgress() }.settingsViewport(viewport).smoothWheelScroll(scroll).verticalScroll(scroll), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = if (adwaita) 600.dp else 760.dp)
                .fillMaxWidth()
                .padding(horizontal = if (adwaita) 12.dp else 24.dp)
                .padding(top = if (adwaita) 24.dp else 8.dp, bottom = 120.dp),
        ) {
            SettingsBackHeader(title = title, onBack = onBack)
            val route = remember(title) { SettingsIndex.firstOrNull { it.page == title }?.route }
            androidx.compose.runtime.DisposableEffect(route) {
                onDispose { if (route != null) SettingsNew.markVisited(prefs, route) }
            }
            if (onBack == null) {
                SettingsSearchField()
                if (SettingsSearch.query.isNotBlank()) {
                    if (searchNav != null) SettingsSearchResults(searchNav)
                    return@Column
                }
            } else {
                SettingsSearchBanner()
            }
            content()
        }
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
    /** Live preview shown between the heading and the card (see [PreviewFrame]). */
    preview: (@Composable () -> Unit)? = null,
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
        if (preview != null) {
            preview()
            Spacer(Modifier.height(8.dp))
        }
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
    belowSubtitle: @Composable () -> Unit = {},
    /** Extra searchable text for the row (the labels of its options). */
    extraText: String? = null,
    trailing: @Composable () -> Unit = {},
) {
    val prefs = koinInject<DesktopPrefs>()
    val isMatch = SettingsSearch.matches(title, subtitle, extraText)
    val isNew = SettingsNew.isNewRow(prefs, title)
    val bring = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    val flash = remember { androidx.compose.animation.core.Animatable(0f) }
    val viewport = LocalSettingsViewport.current
    val rowCoordsRef = remember { object { var value: androidx.compose.ui.layout.LayoutCoordinates? = null } }
    if (isMatch && SettingsSearch.target == title) {
        androidx.compose.runtime.LaunchedEffect(title) {
            val vp = viewport
            // The page may still be laying out (images, glass): wait for the row and the scroll area, then centre the
            // row, and check again in case the content moved while scrolling.
            var waited = 0
            while ((vp?.coords?.isAttached != true || rowCoordsRef.value?.isAttached != true || vp.scroll.maxValue == 0) && waited < 1500) {
                kotlinx.coroutines.delay(50); waited += 50
            }
            repeat(3) { attempt ->
                val rc = rowCoordsRef.value
                val vc = vp?.coords
                if (vp != null && rc != null && vc != null && rc.isAttached && vc.isAttached) {
                    // positionInWindow, not boundsInWindow: the latter is clipped to what is visible, so a row far
                    // below the fold would report an empty rectangle.
                    val boxTop = vc.positionInWindow().y
                    val rowMid = rc.positionInWindow().y + rc.size.height / 2f
                    val delta = rowMid - (boxTop + vc.size.height * 0.42f)
                    if (kotlin.math.abs(delta) > 24f) {
                        vp.scroll.animateScrollTo(
                            (vp.scroll.value + delta).toInt().coerceIn(0, vp.scroll.maxValue),
                            androidx.compose.animation.core.tween(450, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                        )
                        kotlinx.coroutines.delay(120)
                    } else return@repeat
                } else if (attempt == 0) bring.bringIntoView()
            }
            kotlinx.coroutines.delay(150)
            // One smooth pulse so the opened setting is easy to spot.
            flash.animateTo(1f, androidx.compose.animation.core.tween(380, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            flash.animateTo(0f, androidx.compose.animation.core.tween(700, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            SettingsSearch.target = null
        }
    }
    val flashColor = MaterialTheme.colorScheme.primary
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(highlighted(title), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = false))
                    if (isNew) NewChip(Modifier.padding(start = 8.dp))
                }
                if (!subtitle.isNullOrBlank()) {
                    Text(highlighted(subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                belowSubtitle()
            }
            trailing()
        }
    }
    val mod = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 1.dp)
        .bringIntoViewRequester(bring)
        .onGloballyPositioned { rowCoordsRef.value = it }
        .drawBehind {
            if (flash.value > 0f) drawRoundRect(
                flashColor.copy(alpha = 0.38f * flash.value),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(InteractionCornerRadius.toPx()),
            )
        }
        .then(if (isMatch) Modifier.background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f), RoundedCornerShape(InteractionCornerRadius)) else Modifier)
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
    val prefs = koinInject<DesktopPrefs>()
    val route = remember(title) { SettingsIndex.firstOrNull { it.page == "Settings" && it.title == title }?.route }
    val categoryNew = route != null && SettingsNew.pageHasNew(prefs, route)
    SettingsRowFrame(
        icon, title, subtitle, onClick,
    ) {
        if (categoryNew) NewChip(Modifier.padding(end = 8.dp))
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun SwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onChecked: (Boolean) -> Unit,
) {
    // Optimistic UI — don't wait for prefs listener round-trip.
    var local by remember(checked) { mutableStateOf(checked) }
    if (!enabled) {
        Box(Modifier.alpha(0.38f)) {
            SettingsRowFrame(icon, title, subtitle, onClick = null) {
                Switch(checked = false, onCheckedChange = null, enabled = false)
            }
        }
        return
    }
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
    subtitle: String? = null,
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
            SettingsRowFrame(icon, title, subtitle, onClick = null, extraText = options.joinToString(" ") { it.second }) { segmented(Modifier.widthIn(max = 380.dp)) }
        } else {
            Column {
                SettingsRowFrame(icon, title, subtitle, onClick = null, extraText = options.joinToString(" ") { it.second })
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
        SettingsRowFrame(icon, title, null, onClick = null, extraText = options.joinToString(" ") { it.second })
        FlowRow(
            Modifier.fillMaxWidth().padding(start = if (icon != null) 56.dp else 16.dp, end = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEach { (value, label) ->
                FilterChip(
                    selected = local == value,
                    onClick = { local = value; onSelect(value) },
                    label = { Text(highlighted(label)) },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                )
            }
        }
    }
}

/**
 * A numeric setting: title and value on one line, the slider full width below. [liveUpdate] also commits while dragging, so
 * previews follow the thumb. [defaultValue] adds a reset button that shows while the value differs from it.
 */
@Composable
internal fun PrefSecondsSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: (Float) -> String,
    onCommit: (Float) -> Unit,
    liveUpdate: Boolean = false,
    defaultValue: Float? = null,
) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(start = 56.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                highlighted(title), style = MaterialTheme.typography.bodyLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            if (defaultValue != null && kotlin.math.abs(local - defaultValue) > 1e-4f) {
                IconButton(onClick = { local = defaultValue; onCommit(defaultValue) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Restore, "Reset to default", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
            Text(
                valueLabel(local),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                modifier = Modifier.widthIn(min = 56.dp),
            )
        }
        Slider(
            value = local.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = { local = it; if (liveUpdate) onCommit(it) },
            onValueChangeFinished = { onCommit(local) },
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Rows that belong to the switch/value above them: they slide in below it only while [visible]. */
@Composable
internal fun VisibleWhen(visible: Boolean, content: @Composable ColumnScope.() -> Unit) {
    AnimatedVisibility(
        visible,
        enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
        exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(),
    ) { Column(content = content) }
}

/** A row that reveals secondary rows under it; collapsed by default. */
@Composable
internal fun AdvancedExpander(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    initiallyOpen: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var open by remember { mutableStateOf(initiallyOpen) }
    val turn by androidx.compose.animation.core.animateFloatAsState(if (open) 180f else 0f, label = "expander")
    SettingsRowFrame(icon, title, subtitle, onClick = { open = !open }) {
        Icon(
            Icons.Outlined.ExpandMore, if (open) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer { rotationZ = turn },
        )
    }
    VisibleWhen(open, content)
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


/** Spawn point and size of the Rings background; shown (collapsed) under every style picker that offers Rings. */
@Composable
internal fun RingsControls(prefs: DesktopPrefs, @Suppress("UNUSED_PARAMETER") showIntro: Boolean = false) {
    AdvancedExpander(Icons.Outlined.Tune, "Ring placement", "Where the rings spread from and how big they are") {
        RingsSliders(prefs)
    }
}

@Composable
private fun RingsSliders(prefs: DesktopPrefs) {
    PrefSecondsSlider(
        title = "Spawn X", value = prefs.getFloat(PrefKeys.RingsOriginX, 0.20f), valueRange = 0f..1f, steps = 19,
        valueLabel = { "${(it * 100).toInt()}%" }, liveUpdate = true, defaultValue = 0.20f, onCommit = { prefs.setFloat(PrefKeys.RingsOriginX, it) },
    )
    PrefSecondsSlider(
        title = "Spawn Y", value = prefs.getFloat(PrefKeys.RingsOriginY, 0.50f), valueRange = 0f..1f, steps = 19,
        valueLabel = { "${(it * 100).toInt()}%" }, liveUpdate = true, defaultValue = 0.50f, onCommit = { prefs.setFloat(PrefKeys.RingsOriginY, it) },
    )
    PrefSecondsSlider(
        title = "Ring size", value = prefs.getFloat(PrefKeys.RingsScale, 1.6f), valueRange = 0.5f..5f, steps = 44,
        valueLabel = { "%.1f×".format(it) }, liveUpdate = true, defaultValue = 1.6f, onCommit = { prefs.setFloat(PrefKeys.RingsScale, it) },
    )
    PrefSecondsSlider(
        title = "Ring opacity", value = prefs.getFloat(PrefKeys.RingsOpacity, 0.8f), valueRange = 0.1f..1f, steps = 17,
        valueLabel = { "${(it * 100).toInt()}%" }, liveUpdate = true, defaultValue = 0.8f, onCommit = { prefs.setFloat(PrefKeys.RingsOpacity, it) },
    )
    PrefSecondsSlider(
        title = "Reaction level", value = prefs.getFloat(PrefKeys.RingsReaction, 1f), valueRange = 0f..2f, steps = 19,
        valueLabel = { if (it <= 0f) "Off" else "${(it * 100).toInt()}%" }, liveUpdate = true, defaultValue = 1f, onCommit = { prefs.setFloat(PrefKeys.RingsReaction, it) },
    )
    PrefSecondsSlider(
        title = "Smoothing", value = prefs.getFloat(PrefKeys.RingsSmoothing, 0f), valueRange = 0f..1f, steps = 19,
        valueLabel = { if (it <= 0f) "Off" else "${(it * 100).toInt()}%" }, liveUpdate = true, defaultValue = 0f, onCommit = { prefs.setFloat(PrefKeys.RingsSmoothing, it) },
    )
}
