package com.music.vivi.compose.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.PrefKeys
import com.music.vivi.compose.ui.component.pageScrollEdge
import com.music.vivi.compose.ui.component.smoothWheelScroll
import org.koin.compose.koinInject

/*
 * Small building blocks for settings pages that have a live preview (Lyrics, Fullscreen player).
 * They are deliberately local to these two pages; when the shared preview infrastructure lands they can be swapped.
 */

/**
 * A settings page with a preview that stays pinned under the header while the rows scroll (wide windows), or scrolls
 * with the rows on narrow ones. [preview] is wrapped in a collapsible [PreviewFrame] remembered per page via [previewKey].
 */
@Composable
internal fun PreviewSettingsPage(
    title: String,
    onBack: (() -> Unit)?,
    previewKey: String,
    previewCaption: String,
    previewHeight: androidx.compose.ui.unit.Dp,
    preview: @Composable () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val prefs = koinInject<DesktopPrefs>()
    @Suppress("UNUSED_VARIABLE")
    val observe = rememberPrefsVersion(prefs)
    val adwaita = prefs.getBoolean(PrefKeys.AdwaitaInspired, false)
    val maxW = if (adwaita) 600.dp else 760.dp
    val pad = if (adwaita) 12.dp else 24.dp
    val scroll = rememberScrollState()
    val viewport = remember(scroll) { SettingsViewport(scroll) }
    val route = remember(title) { SettingsIndex.firstOrNull { it.page == title }?.route }
    DisposableEffect(route) { onDispose { if (route != null) SettingsNew.markVisited(prefs, route) } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pinned = maxWidth >= 720.dp
        val frame: @Composable () -> Unit = { PreviewFrame(previewKey, previewCaption, previewHeight, preview) }
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = maxW).fillMaxWidth().padding(horizontal = pad).padding(top = if (adwaita) 16.dp else 8.dp)) {
                SettingsBackHeader(title = title, onBack = onBack)
                SettingsSearchBanner()
                if (pinned) frame()
            }
            CompositionLocalProvider(LocalSettingsViewport provides viewport) {
                Box(
                    Modifier.weight(1f).fillMaxWidth()
                        .pageScrollEdge(progress = { (scroll.value / 48f).coerceIn(0f, 1f) })
                        .settingsViewport(viewport).smoothWheelScroll(scroll).verticalScroll(scroll),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(Modifier.widthIn(max = maxW).fillMaxWidth().padding(horizontal = pad).padding(bottom = 140.dp)) {
                        if (!pinned) frame()
                        content()
                    }
                }
            }
        }
    }
}

/** Collapsible rounded frame around a live preview ("Preview" header with expand / collapse, remembered in prefs). */
@Composable
internal fun PreviewFrame(
    key: String,
    caption: String,
    height: androidx.compose.ui.unit.Dp,
    content: @Composable () -> Unit,
) {
    val prefs = koinInject<DesktopPrefs>()
    var open by remember { mutableStateOf(prefs.getBoolean(key, true)) }
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Preview", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = { open = !open; prefs.setBoolean(key, open) }) {
                Icon(
                    Icons.Outlined.ExpandMore,
                    if (open) "Hide preview" else "Show preview",
                    modifier = Modifier.rotate(if (open) 0f else -90f),
                )
            }
        }
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            ) {
                Box(Modifier.fillMaxWidth().padding(8.dp).height(height)) {
                    content()
                }
            }
        }
    }
}

/** Swallows pointer input and hides the content from accessibility: previews show, they do not act. */
internal fun Modifier.previewOnly(): Modifier = this
    .pointerInput(Unit) {
        awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() } }
    }
    .clearAndSetSemantics { }

/** A row that reveals secondary rows (an "Advanced" group). Collapsed by default. */
@Composable
internal fun SettingsDisclosure(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    content: @Composable () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    SettingsRowFrame(icon, title, subtitle, onClick = { open = !open }) {
        Icon(Icons.Outlined.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.rotate(if (open) 180f else 0f))
    }
    AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Column(Modifier.fillMaxWidth()) { content() }
    }
}

/**
 * A slider setting: title and value on one line, slider below, trailing reset icon while the value differs from [default].
 * [onChange] fires while dragging (live preview), [onCommit] on release.
 */
@Composable
internal fun LiveSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    label: (Float) -> String,
    onChange: (Float) -> Unit,
    onCommit: (Float) -> Unit,
    icon: ImageVector? = null,
    subtitle: String? = null,
    default: Float? = null,
) {
    var last by remember(value) { mutableFloatStateOf(value) }
    SettingsRowFrame(
        icon, title, subtitle, onClick = null,
        belowSubtitle = {
            Slider(
                value = value.coerceIn(range.start, range.endInclusive),
                onValueChange = { last = it; onChange(it) },
                onValueChangeFinished = { onCommit(last) },
                valueRange = range,
                steps = steps,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        extraText = null,
    ) {
        Text(label(value), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(min = 56.dp))
        if (default != null && kotlin.math.abs(value - default) > 1e-4f) {
            IconButton(onClick = { onChange(default); onCommit(default) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.Restore, "Reset to default", modifier = Modifier.size(18.dp))
            }
        }
    }
}
