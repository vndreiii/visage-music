package com.music.vivi.compose.ui.settings.preview

/*
 * SETTINGS LIVE-PREVIEW INFRASTRUCTURE
 * ------------------------------------
 * Show the REAL app composables, in a fake environment, at the top of a settings page, updating as the user
 * changes controls (docs: settings-design.md section 7).
 *
 *   SettingsPreviewFrame(pageKey = "sidebars", caption = "...", actions = { ...small preview-only controls... }) {
 *       // runs inside a virtual 1280x720 dp window, scaled down to the page width
 *       MyRealComponent()
 *   }
 *
 * What the frame does
 *  - Sticky: pins itself to the top of the settings page while the rest scrolls underneath (needs the page's
 *    LocalSettingsViewport, which SettingsPage provides; without one it simply scrolls with the page).
 *  - Collapsible: a header with a chevron; the choice is remembered per [pageKey] (pref `settingsPreview.<pageKey>.collapsed`).
 *    A collapsed or scrolled-off frame composes NOTHING, so no shader, animation or clock keeps running.
 *  - Virtual window: content is laid out at [virtualWidth] x [virtualHeight] and scaled to the available width
 *    ([previewScale]), so real breakpoints and proportions hold. Content is pointer-blocked and hidden from
 *    semantics; put interactive things in [actions] (outside the scaled area).
 *  - Fake environment: content sees `LocalSettingsPreview == true` and a [FakePlaybackController] in
 *    `LocalPlaybackController` (sample track, queue, advancing position, no audio/network). Sample data lives in
 *    [PreviewSampleData].
 *
 * Rules for content (so previews stay live and safe)
 *  1. Read prefs the way the real screen does AND call `rememberPrefsVersion(prefs)` in the composable that reads
 *     them, even when you also receive style overrides: a composable that skips the observation when an override is
 *     passed stays stale until the settings page is left.
 *  2. Never reach a repository/network in preview mode: take data as parameters, or branch on `LocalSettingsPreview`.
 *  3. Do not create your own animation/shader that keeps running when the frame is collapsed; the frame stops
 *     composing you, so nothing extra is needed.
 *
 * Existing examples: [SidebarsPreview] (real library sidebar + queue rail around a sample page and mini-player).
 */

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import com.music.vivi.compose.ui.component.PanelHeaderGap
import com.music.vivi.compose.ui.component.panelInset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.zIndex
import com.music.vivi.compose.LocalPlaybackController
import com.music.vivi.compose.theme.LocalArtworkPalette
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.ui.motion.VisageMotion
import com.music.vivi.compose.ui.settings.LocalSettingsViewport
import org.koin.compose.koinInject
import kotlin.math.max

/** True inside a [SettingsPreviewFrame]: components can skip repositories/network and use the sample data. */
val LocalSettingsPreview = staticCompositionLocalOf { false }

/** Size of the virtual window the preview content is laid out in (logical dp), before scaling. */
const val PreviewVirtualWidthDp = 1280
const val PreviewVirtualHeightDp = 720

/** Scale that fits a virtual width into [availableWidth] (never enlarges past 1:1, never below a sliver). */
fun previewScale(availableWidth: Float, virtualWidth: Float): Float =
    if (virtualWidth <= 0f || availableWidth <= 0f) 1f else (availableWidth / virtualWidth).coerceIn(0.05f, 1f)

/** Height the scaled window occupies for a virtual [virtualHeight] at [scale]. */
fun previewScaledHeight(virtualHeight: Float, scale: Float): Float = virtualHeight * scale

/** Pref key remembering whether the preview of [pageKey] is collapsed. */
fun previewCollapsedKey(pageKey: String) = "settingsPreview.$pageKey.collapsed"

/** How far a sticky frame must be shifted down so its top sits at the viewport top (0 until it would scroll off). */
fun stickyOffset(scrollValue: Int, naturalTopInContent: Int, margin: Int = 0): Int =
    max(0, scrollValue - naturalTopInContent + margin)

/**
 * A framed, scaled, non-interactive window onto real components. See the file header for the full contract.
 *
 * @param pageKey stable id of the page (used to remember the collapsed state)
 * @param title label in the frame header
 * @param caption small line under the window ("Sample data")
 * @param sticky pin to the top of the settings page while scrolling
 * @param maxWindowWidth cap for the width of the scaled window (centred); Unspecified = the whole page width
 * @param actions preview-only controls shown in the header (they ARE interactive; keep them tiny)
 * @param content the real UI, laid out in the virtual window
 */
@Composable
fun SettingsPreviewFrame(
    pageKey: String,
    modifier: Modifier = Modifier,
    title: String = "Preview",
    caption: String = "Live preview with sample data",
    sticky: Boolean = true,
    virtualWidth: Dp = PreviewVirtualWidthDp.dp,
    virtualHeight: Dp = PreviewVirtualHeightDp.dp,
    maxWindowWidth: Dp = Dp.Unspecified,
    actions: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val prefs = koinInject<DesktopPrefs>()
    val key = previewCollapsedKey(pageKey)
    var collapsed by remember(key) { mutableStateOf(prefs.getBoolean(key, false)) }
    val viewport = LocalSettingsViewport.current
    var naturalTop by remember { mutableFloatStateOf(0f) }
    var visible by remember { mutableStateOf(true) }
    val stick = sticky && viewport != null
    val shift = if (stick) stickyOffset(viewport!!.scroll.value, naturalTop.toInt()) else 0

    // The slot keeps its place in the page (so nothing jumps); the frame itself is lifted by the scroll offset.
    Box(
        modifier
            .fillMaxWidth()
            .zIndex(1f)
            .onGloballyPositioned { c ->
                val vc = viewport?.coords
                if (vc != null && vc.isAttached) {
                    val top = c.positionInWindow().y
                    val vTop = vc.positionInWindow().y
                    // Natural top within the scrolled content = where the slot is now, plus how far the page has scrolled.
                    naturalTop = top - vTop + viewport.scroll.value
                    visible = top + c.size.height > vTop && top < vTop + vc.size.height
                }
            },
    ) {
        Box(Modifier.fillMaxWidth().graphicsLayer { translationY = shift.toFloat() }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = if (shift > 0) 6.dp else 0.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // One PanelInset on all four sides; the action row and the preview are separated by PanelHeaderGap.
                Column(Modifier.padding(panelInset()).animateContentSize(VisageMotion.glide())) {
                    val chevron by animateFloatAsState(if (collapsed) -90f else 0f, VisageMotion.effectsDefault(), label = "previewChevron")
                    Row(
                        Modifier.fillMaxWidth().height(40.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            Modifier
                                .weight(1f)
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                    collapsed = !collapsed
                                    prefs.setBoolean(key, collapsed)
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Outlined.Visibility, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Icon(
                                Icons.Outlined.ExpandMore,
                                if (collapsed) "Show preview" else "Hide preview",
                                Modifier.size(22.dp).graphicsLayer { rotationZ = chevron },
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        AnimatedVisibility(!collapsed, enter = fadeIn(), exit = fadeOut()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
                        }
                    }
                    if (!collapsed) {
                        Spacer(Modifier.height(PanelHeaderGap))
                        PreviewWindow(virtualWidth, virtualHeight, maxWindowWidth, live = visible || stick, content = content)
                        Text(
                            caption,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/** The scaled, clipped, pointer-blocked virtual window. [live] false keeps the size but composes nothing. */
@Composable
private fun PreviewWindow(virtualWidth: Dp, virtualHeight: Dp, maxWindowWidth: Dp, live: Boolean, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        // Never wider than the virtual window itself: a small window (lyrics, song swap) is shown at 1:1, centred.
        val w = minOf(maxWidth, virtualWidth, if (maxWindowWidth == Dp.Unspecified) maxWidth else maxWindowWidth)
        val scale = previewScale(w.value, virtualWidth.value)
        val h = previewScaledHeight(virtualHeight.value, scale).dp
        Box(
            Modifier
                .size(w, h)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.background),
        ) {
            if (live) {
                val controller = rememberFakePlaybackController()
                CompositionLocalProvider(
                    LocalSettingsPreview provides true,
                    LocalPlaybackController provides controller,
                    LocalArtworkPalette provides PreviewSampleData.palette,
                ) {
                    Box(
                        Modifier
                            .layout { measurable, _ ->
                                val vw = with(density) { virtualWidth.roundToPx() }
                                val vh = with(density) { virtualHeight.roundToPx() }
                                val placeable = measurable.measure(Constraints.fixed(vw, vh))
                                layout((vw * scale).toInt(), (vh * scale).toInt()) {
                                    placeable.placeWithLayer(0, 0) {
                                        scaleX = scale
                                        scaleY = scale
                                        transformOrigin = TransformOrigin(0f, 0f)
                                    }
                                }
                            }
                            .clearAndSetSemantics {},
                    ) { content() }
                }
            }
            // Eat every pointer event: the preview shows, it does not act.
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    },
            )
        }
    }
}
