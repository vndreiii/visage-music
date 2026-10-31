package com.music.vivi.compose.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.vivi.compose.ui.responsive.windowLayout

val ListItemHeight = 64.dp
val ListThumbnailSize = 48.dp
val GridThumbnailHeight = 128.dp
val ThumbnailCornerRadius = 6.dp

/** Bottom space every scrolling page keeps so its last row clears the floating mini-player. */
val MiniPlayerClearance = 140.dp

/**
 * Inset between a panel's edge (card, glass shelf, tray, dialog body) and its content, identical on all four sides.
 * See docs/design-system.md section 6. Use [panelInset] so compact windows get [PanelInsetCompact].
 */
val PanelInset = 20.dp

/** [PanelInset] on compact widths (phones and tablets), where every panel is narrower. */
val PanelInsetCompact = 16.dp

/** Gap between a panel's header row (title, strapline, action buttons) and the content below it. */
val PanelHeaderGap = 12.dp

/** The panel inset for the current window: [PanelInset], or [PanelInsetCompact] on compact widths. */
@Composable
fun panelInset(): Dp = if (windowLayout().isCompactWidth) PanelInsetCompact else PanelInset
