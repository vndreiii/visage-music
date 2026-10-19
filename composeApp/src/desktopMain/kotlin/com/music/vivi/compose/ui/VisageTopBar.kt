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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.outlined.Tune
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
import org.koin.compose.koinInject

private val TopBarHeight = 64.dp
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
    /** Page-contextual buttons (sidebar toggles) shown just left of the caption buttons. */
    extraActions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
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
    var settingsMenuOpen by remember { mutableStateOf(false) }

    Surface(tonalElevation = 2.dp) {
        WindowDraggableArea {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TopBarHeight),
            ) {
                // Left: brand + section title (inset from edge)
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .padding(start = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val logo = remember {
                        runCatching {
                            useResource("visage_icon.png") { loadImageBitmap(it) }
                        }.getOrNull()
                    }
                    if (logo != null) {
                        Image(
                            bitmap = logo,
                            contentDescription = "VIsage",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp)) // the icon is a rounded square; a circle would cut its corners
                                .clickable { onNavigate(Routes.HOME) },
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Text(
                            "V",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable { onNavigate(Routes.HOME) }
                                .padding(6.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = currentLabel,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                        ),
                    )
                }

                // Center: main destinations
                if (navDestinations.isNotEmpty()) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        navDestinations.forEach { dest ->
                            val selected = when (dest.route) {
                                Routes.SETTINGS ->
                                    currentRoute == Routes.SETTINGS ||
                                        currentRoute?.startsWith("settings/") == true
                                Routes.SEARCH ->
                                    currentRoute == dest.route ||
                                        currentRoute?.startsWith("search/") == true
                                else -> currentRoute == dest.route
                            }
                            Box {
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

                // Right: caption buttons — compact M3 pill hover, not full-height bars
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(end = 10.dp),
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
                    val maximized = windowState.placement == WindowPlacement.Maximized
                    WindowCaptionButton(
                        icon = if (maximized) Icons.Rounded.FilterNone else Icons.Rounded.CropSquare,
                        description = if (maximized) "Restore" else "Maximize",
                        iconSize = if (maximized) 16.dp else 18.dp,
                    ) {
                        windowState.placement =
                            if (maximized) WindowPlacement.Floating else WindowPlacement.Maximized
                    }
                    WindowCaptionButton(
                        icon = Icons.Rounded.Close,
                        description = "Close",
                        danger = true,
                        onClick = onClose,
                    )
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
            text = { Text("Settings hub") },
            leadingIcon = {
                Icon(Icons.Outlined.Settings, contentDescription = null)
            },
            onClick = { onOpen(Routes.SETTINGS) },
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
            modifier = Modifier.size(CaptionIconSlot),
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
