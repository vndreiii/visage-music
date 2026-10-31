package com.music.vivi.compose.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.FilterNone
import androidx.compose.material.icons.rounded.Minimize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.music.vivi.compose.navigation.MainDest
import com.music.vivi.compose.navigation.Routes
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.PrefKeys
import com.music.vivi.compose.prefs.TopNavStyle
import com.music.vivi.compose.theme.ensureContrastingOn
import com.music.vivi.compose.ui.onboarding.tourAnchor
import org.koin.compose.koinInject

private val TopBarHeight = com.music.vivi.compose.ui.glass.TopBarInset
private val CaptionHit = 40.dp
private val CaptionIconSlot = 20.dp

/**
 * Desktop chrome: logo + page title on the left, main nav centered,
 * caption buttons flush to the top-right edge.
 */
@Composable
fun FrameWindowScope.VisageTopBar(
    destinations: List<MainDest>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    windowState: WindowState,
    onClose: () -> Unit,
    /** Deep settings routes (Appearance, Player, …) — bypasses main-tab pop behavior. */
    onOpenSettingsRoute: (String) -> Unit = onNavigate,
    /** Compact windows: whether the nav tray ([NavTray], rendered by the caller below the bar) is open. */
    navMenuOpen: Boolean = false,
    onNavMenuToggle: () -> Unit = {},
    /** Page-contextual buttons (sidebar toggles) shown just left of the caption buttons. */
    extraActions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    val onMain = destinations.any {
        currentRoute == it.route ||
            (it.route == Routes.SEARCH && currentRoute?.startsWith("search/") == true)
    }
    val currentLabel = when {
        onMain -> destinations.firstOrNull {
            currentRoute == it.route ||
                (it.route == Routes.SEARCH && currentRoute?.startsWith("search/") == true)
        }?.label ?: "VIsage"
        currentRoute?.startsWith("settings") == true -> "Settings"
        currentRoute?.startsWith("online_playlist/") == true -> "Playlist"
        currentRoute?.startsWith("album/") == true -> "Album"
        currentRoute?.startsWith("artist/") == true -> "Artist"
        currentRoute == Routes.NOW_PLAYING -> "Now playing"
        else -> "VIsage"
    }

    val navDestinations = destinations.filter { it.route != Routes.SMOKE }
    val layout = com.music.vivi.compose.ui.responsive.windowLayout()
    // Below the desktop breakpoint the tabs live in a hamburger menu so nothing overlaps the title.
    val compact = layout.isCompactWidth
    val glass = com.music.vivi.compose.ui.glass.glassActive()
    Surface(
        tonalElevation = if (glass) 0.dp else 2.dp,
        color = if (glass) Color.Transparent else MaterialTheme.colorScheme.surface,
    ) {
        VisageWindowDragArea {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TopBarHeight),
            ) {
                // Frosted glass over whatever the page draws beneath the floating bar.
                com.music.vivi.compose.ui.glass.GlassBackdrop(
                    tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                    modifier = Modifier.matchParentSize(),
                    edges = com.music.vivi.compose.ui.glass.GlassEdges.Bottom,
                )
                // Compact: [menu | logo | title (shrinks)] ... [actions + caption buttons] as one row, so the title can
                // never run under the controls. Desktop: the original three-zone box layout.
                val leftZone: @Composable (Modifier) -> Unit = { zoneMod ->
                    Row(
                        modifier = zoneMod.fillMaxHeight().padding(start = if (compact) 6.dp else 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (compact && navDestinations.isNotEmpty()) {
                            WindowCaptionButton(
                                icon = if (navMenuOpen) Icons.Rounded.Close else Icons.Rounded.Menu,
                                description = if (navMenuOpen) "Close menu" else "Menu",
                                iconSize = 27.dp,
                            ) { onNavMenuToggle() }
                            Spacer(Modifier.width(if (layout.isMobile) 12.dp else 8.dp))
                        }
                        TopBarBrand(
                            title = currentLabel,
                            onLogoClick = { onNavigate(Routes.HOME) },
                            modifier = if (compact) Modifier.weight(1f, fill = false) else Modifier,
                            showLogo = !layout.isMobile,
                            compact = layout.isMobile,
                        )
                    }
                }

                // Center: main destinations
                if (!compact && navDestinations.isNotEmpty()) {
                    TopNavItems(
                        destinations = navDestinations,
                        currentRoute = currentRoute,
                        onNavigate = onNavigate,
                        onOpenSettingsRoute = onOpenSettingsRoute,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                // Right: caption buttons — compact M3 pill hover, not full-height bars
                val rightZone: @Composable (Modifier) -> Unit = { zoneMod ->
                Row(
                    modifier = zoneMod
                        .fillMaxHeight()
                        .padding(end = if (layout.isMobile) 4.dp else 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    extraActions()
                    WindowCaptionButton(
                        icon = Icons.Rounded.Minimize,
                        description = "Minimize",
                    ) {
                        windowState.isMinimized = true
                    }
                    val manualMax = com.music.vivi.nativelib.HostOs.isWindows
                    // Windows only: reading size/position recomposes on move/resize so isMaximized(window) refreshes.
                    // Elsewhere this would recompose the whole bar on every pixel of a window drag.
                    if (manualMax) {
                        @Suppress("UNUSED_VARIABLE") val geometry = windowState.size to windowState.position
                    }
                    val maximized = if (manualMax) {
                        com.music.vivi.compose.WindowMaximize.isMaximized(window)
                    } else {
                        windowState.placement == WindowPlacement.Maximized
                    }
                    WindowCaptionButton(
                        icon = if (maximized) Icons.Rounded.FilterNone else Icons.Rounded.CropSquare,
                        description = if (maximized) "Restore" else "Maximize",
                        iconSize = if (maximized) 16.dp else 18.dp,
                    ) {
                        if (manualMax) {
                            com.music.vivi.compose.WindowMaximize.toggle(window, windowState)
                        } else {
                            windowState.placement =
                                if (maximized) WindowPlacement.Floating else WindowPlacement.Maximized
                        }
                    }
                    WindowCaptionButton(
                        icon = Icons.Rounded.Close,
                        description = "Close",
                        danger = true,
                        onClick = onClose,
                    )
                }
                }
                if (compact) {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        leftZone(Modifier.weight(1f))
                        rightZone(Modifier)
                    }
                } else {
                    leftZone(Modifier.align(Alignment.CenterStart))
                    rightZone(Modifier.align(Alignment.CenterEnd))
                }
            }
        }
    }
}

@Composable
private fun SettingsDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        DropdownMenuItem(
            text = { Text("Settings") },
            leadingIcon = {
                Icon(Icons.Outlined.Settings, contentDescription = null)
            },
            onClick = { onOpen(Routes.SETTINGS) },
        )
        DropdownMenuItem(
            text = { Text("Search settings") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            onClick = {
                com.music.vivi.compose.ui.settings.SettingsSearch.query = ""
                com.music.vivi.compose.ui.settings.SettingsSearch.focusRequest++
                onOpen(Routes.SETTINGS)
            },
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Account") },
            leadingIcon = {
                Icon(Icons.Outlined.AccountCircle, contentDescription = null)
            },
            onClick = { onOpen(Routes.SETTINGS_ACCOUNT) },
        )
        DropdownMenuItem(
            text = { Text("Theme") },
            leadingIcon = { Icon(Icons.Outlined.Palette, contentDescription = null) },
            onClick = { onOpen(Routes.SETTINGS_THEME) },
        )
        DropdownMenuItem(
            text = { Text("Interface") },
            leadingIcon = { Icon(Icons.Outlined.Tune, contentDescription = null) },
            onClick = { onOpen(Routes.SETTINGS_APPEARANCE) },
        )
        DropdownMenuItem(
            text = { Text("Now Playing") },
            leadingIcon = { Icon(Icons.Outlined.PlayCircle, contentDescription = null) },
            onClick = { onOpen(Routes.SETTINGS_NOW_PLAYING) },
        )
        DropdownMenuItem(
            text = { Text("Playback & audio") },
            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = null) },
            onClick = { onOpen(Routes.SETTINGS_PLAYER) },
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Downloads") },
            leadingIcon = { Icon(Icons.Outlined.Download, contentDescription = null) },
            onClick = { onOpen(Routes.SETTINGS_DOWNLOADS) },
        )
        DropdownMenuItem(
            text = { Text("Local files") },
            leadingIcon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
            onClick = { onOpen(Routes.SETTINGS_LOCAL_OFFLINE) },
        )
        DropdownMenuItem(
            text = { Text("Diagnostics") },
            leadingIcon = { Icon(Icons.Outlined.BugReport, contentDescription = null) },
            onClick = { onOpen(Routes.SETTINGS_DIAGNOSTICS) },
        )
    }
}

/** Logo mark and section title at the left of the top bar. Shared by the window bar and the Interface settings preview. */
@Composable
internal fun TopBarBrand(
    title: String,
    onLogoClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** False on phones: the menu button and title need the room. */
    showLogo: Boolean = true,
    compact: Boolean = false,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp),
    ) {
        val logo = remember {
            runCatching {
                useResource("visage_icon.png") { loadImageBitmap(it) }
            }.getOrNull()
        }
        if (!showLogo) {
            // no logo
        } else if (logo != null) {
            Image(
                bitmap = logo,
                contentDescription = "VIsage",
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp)) // the icon is a rounded square; a circle would cut its corners
                    .clickable { onLogoClick() },
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(
                "V",
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { onLogoClick() }
                    .padding(6.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = if (compact) 18.sp else 22.sp,
            ),
        )
    }
}

/**
 * The centred tab chips of the top bar. Reads (and observes) the tab-label and settings-menu prefs itself, so the real bar
 * and the Interface settings preview both follow them the moment they change.
 */
@Composable
internal fun TopNavItems(
    destinations: List<MainDest>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettingsRoute: (String) -> Unit = onNavigate,
    /** False in settings previews: their chips must not take over the onboarding tour's spotlight targets. */
    anchors: Boolean = true,
) {
    val prefs = koinInject<DesktopPrefs>()
    var prefsTick by remember { mutableIntStateOf(0) }
    DisposableEffect(prefs) {
        val listener = { prefsTick += 1 }
        prefs.addListener(listener)
        onDispose { prefs.removeListener(listener) }
    }
    @Suppress("UNUSED_VARIABLE")
    val observe = prefsTick
    val navStyle = runCatching {
        TopNavStyle.valueOf(prefs.getString(PrefKeys.TopNavStyle, TopNavStyle.ICON_ONLY.name))
    }.getOrDefault(TopNavStyle.ICON_ONLY)
    val settingsDropdown = prefs.getBoolean(PrefKeys.SettingsDropdown, true)
    var settingsMenuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        destinations.forEach { dest ->
            val selected = when (dest.route) {
                Routes.SETTINGS ->
                    currentRoute == Routes.SETTINGS ||
                        currentRoute?.startsWith("settings/") == true
                Routes.SEARCH ->
                    currentRoute == dest.route ||
                        currentRoute?.startsWith("search/") == true
                else -> currentRoute == dest.route
            }
            Box(if (!anchors) Modifier else Modifier.tourAnchor(
                when (dest.route) {
                    Routes.HOME -> "nav.home"
                    Routes.SEARCH -> "nav.search"
                    Routes.LIBRARY -> "nav.library"
                    Routes.SETTINGS -> "nav.settings"
                    else -> "nav.${dest.route}"
                },
            )) {
                TopNavChip(
                    label = dest.label,
                    icon = destIcon(dest.route, selected),
                    style = navStyle,
                    selected = selected,
                    onClick = {
                        if (dest.route == Routes.SETTINGS && settingsDropdown) {
                            settingsMenuOpen = true
                        } else {
                            settingsMenuOpen = false
                            onNavigate(dest.route)
                        }
                    },
                )
                if (dest.route == Routes.SETTINGS) {
                    SettingsDropdownMenu(
                        expanded = settingsMenuOpen && settingsDropdown,
                        onDismiss = { settingsMenuOpen = false },
                        onOpen = { route ->
                            settingsMenuOpen = false
                            onOpenSettingsRoute(route)
                        },
                    )
                }
            }
        }
    }
}

private fun navSelected(route: String, currentRoute: String?) = when (route) {
    Routes.SETTINGS -> currentRoute == Routes.SETTINGS || currentRoute?.startsWith("settings/") == true
    Routes.SEARCH -> currentRoute == route || currentRoute?.startsWith("search/") == true
    else -> currentRoute == route
}

/**
 * Compact navigation tray: slides down from directly under the top bar, full width, over a scrim that dismisses it.
 * Draw it in a window-sized Box; it leaves the top [TopBarHeight] free so the menu button underneath still toggles it.
 */
@Composable
fun NavTray(
    open: Boolean,
    destinations: List<MainDest>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = destinations.filter { it.route != Routes.SMOKE }
    Box(modifier.fillMaxSize().padding(top = TopBarHeight)) {
        androidx.compose.animation.AnimatedVisibility(
            visible = open,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onDismiss),
            )
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = open,
            enter = androidx.compose.animation.slideInVertically { -it } + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically { -it } + androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = {}),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items.forEach { dest ->
                        val sel = navSelected(dest.route, currentRoute)
                        val fg = if (sel) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                        Row(
                            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp))
                                .background(if (sel) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                .clickable(role = Role.Button) { onNavigate(dest.route) }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                destIcon(dest.route, sel), contentDescription = null,
                                tint = if (sel) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                dest.label, color = fg, style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun destIcon(route: String, selected: Boolean): ImageVector = when (route) {
    Routes.HOME -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
    Routes.SEARCH -> if (selected) Icons.Filled.Search else Icons.Outlined.Search
    Routes.LIBRARY -> if (selected) Icons.Filled.LibraryMusic else Icons.Outlined.LibraryMusic
    Routes.SETTINGS -> if (selected) Icons.Filled.Settings else Icons.Outlined.Settings
    else -> Icons.Outlined.Home
}

@Composable
private fun TopNavChip(
    label: String,
    icon: ImageVector,
    style: TopNavStyle,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // primary/onPrimary is the high-contrast brand pair. secondaryContainer often
    // lands muddy mid-tones with artwork seeds (esp. Content/Fidelity) under SPEC_2021.
    val scheme = MaterialTheme.colorScheme
    val selectedBg = scheme.primary
    val selectedFg = ensureContrastingOn(selectedBg, scheme.onPrimary)
    val bg by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) selectedBg else Color.Transparent,
        animationSpec = com.music.vivi.compose.ui.motion.VisageMotion.settle(),
        label = "navChipBg",
    )
    val fg by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) {
            selectedFg
        } else {
            scheme.onSurfaceVariant
        },
        animationSpec = com.music.vivi.compose.ui.motion.VisageMotion.settle(),
        label = "navChipFg",
    )
    val iconScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (selected) 1.1f else 1f,
        animationSpec = com.music.vivi.compose.ui.motion.VisageMotion.bounce(),
        label = "navIconBounce",
    )
    val interaction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Expressive press morph: the pill tightens while held and springs back.
    val radius by androidx.compose.animation.core.animateDpAsState(
        if (pressed) 12.dp else 22.dp,
        com.music.vivi.compose.ui.motion.VisageMotion.spatialFast(),
        label = "navChipRadius",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(radius))
            .background(bg)
            .clickable(interactionSource = interaction, indication = androidx.compose.foundation.LocalIndication.current, onClick = onClick)
            .padding(
                horizontal = when (style) {
                    TopNavStyle.ICON_ONLY -> 12.dp
                    TopNavStyle.ICON_AND_TEXT -> 14.dp
                    TopNavStyle.TEXT_ONLY -> 14.dp
                },
                vertical = 10.dp,
            ),
    ) {
        if (style != TopNavStyle.TEXT_ONLY) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = fg,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
            )
        }
        if (style != TopNavStyle.ICON_ONLY) {
            Text(
                text = label,
                color = fg,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun WindowCaptionButton(
    icon: ImageVector,
    description: String,
    danger: Boolean = false,
    iconSize: Dp = 18.dp,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg = when {
        hovered && danger -> Color(0xFFE81123)
        hovered -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.Transparent
    }
    val fg = when {
        hovered && danger -> Color.White
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = Modifier
            .size(CaptionHit)
            .clip(CircleShape)
            .hoverable(interaction)
            .background(bg)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Fixed slot so restore/maximize glyphs never shift baseline
        Box(
            modifier = Modifier.size(maxOf(CaptionIconSlot, iconSize)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = fg,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
