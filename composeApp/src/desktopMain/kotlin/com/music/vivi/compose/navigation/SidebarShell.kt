package com.music.vivi.compose.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.vivi.compose.prefs.LibrarySidebarState
import com.music.vivi.compose.prefs.SidebarConfig
import com.music.vivi.compose.prefs.SidebarSide
import com.music.vivi.compose.prefs.Sidebars
import com.music.vivi.compose.ui.motion.VisageMotion

/**
 * The window's sidebar geometry: library sidebar and pinned queue rail docked left/right of a main pane that is
 * inset and rounded while any sidebar is open, with widths from [Sidebars.libraryWidthFor].
 *
 * It owns only layout. What goes inside comes from the slots, so the real app ([AppScaffold]) and the Settings
 * live preview render the same geometry with different content (real nav host vs sample page).
 *
 * @param queueLeft whether the queue rail is open on the left; [queueRight] on the right
 * @param librarySidebar draws one library sidebar: side, state (HIDDEN for the side it is not on) and expanded width
 * @param queueRail draws one queue rail: visibility and whether it docks to the start edge
 * @param main the main pane content, in a column that already has the inset and rounding applied
 * @param overlay drawn over the whole area (the fullscreen player's floating queue)
 */
@Composable
internal fun SidebarShell(
    cfg: SidebarConfig,
    libraryState: LibrarySidebarState,
    queueLeft: Boolean,
    queueRight: Boolean,
    queueWidthForLibrary: Int,
    rowModifier: Modifier = Modifier,
    modifier: Modifier = Modifier.fillMaxSize(),
    librarySidebar: @Composable (side: SidebarSide, state: LibrarySidebarState, width: Dp) -> Unit,
    queueRail: @Composable (visible: Boolean, fromStart: Boolean) -> Unit,
    overlay: @Composable BoxScope.() -> Unit = {},
    main: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier) {
        val libVisible = libraryState != LibrarySidebarState.HIDDEN
        val libLeft = libVisible && cfg.librarySide == SidebarSide.LEFT
        val libRight = libVisible && cfg.librarySide == SidebarSide.RIGHT
        val libWidth = Sidebars.libraryWidthFor(
            available = maxWidth.value.toInt(),
            queueWidth = queueWidthForLibrary,
            desired = cfg.libraryWidthDp,
        ).dp
        // Round + inset the main pane whenever a sidebar is open (NP and every other page).
        // Insets spring in and out together with the rails instead of jumping.
        val leftFrac by animateFloatAsState(if (libLeft || queueLeft) 1f else 0f, VisageMotion.rail(), label = "insetLeft")
        val rightFrac by animateFloatAsState(if (libRight || queueRight) 1f else 0f, VisageMotion.rail(), label = "insetRight")
        val inset = maxOf(leftFrac, rightFrac)
        Row(Modifier.fillMaxSize().then(rowModifier)) {
            librarySidebar(
                SidebarSide.LEFT,
                if (cfg.librarySide == SidebarSide.LEFT) libraryState else LibrarySidebarState.HIDDEN,
                libWidth,
            )
            queueRail(queueLeft, true)
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .then(
                        if (inset > 0.001f) {
                            Modifier
                                .padding(
                                    start = (8f * inset - 4f * leftFrac).dp,
                                    top = (8f * inset).dp,
                                    bottom = (8f * inset).dp,
                                    end = (8f * inset - 4f * rightFrac).dp,
                                )
                                .clip(RoundedCornerShape((20f * inset).dp))
                        } else {
                            Modifier
                        },
                    ),
                content = main,
            )
            queueRail(queueRight, false)
            librarySidebar(
                SidebarSide.RIGHT,
                if (cfg.librarySide == SidebarSide.RIGHT) libraryState else LibrarySidebarState.HIDDEN,
                libWidth,
            )
        }
        overlay()
    }
}
