package com.music.vivi.compose.whatsnew

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.music.vivi.compose.ui.expressive.ExpressiveButton
import com.music.vivi.compose.ui.expressive.ExpressiveSize
import com.music.vivi.compose.ui.expressive.ExpressiveStyle
import com.music.vivi.compose.ui.motion.VisageMotion
import kotlinx.coroutines.launch

private class FeaturePage(val version: String, val feature: WhatsNewFeature)

/**
 * "What's new" carousel: one page per feature (video on top, explanation below), then a colour-coded changelog.
 * Scroll, drag, use the arrow keys or the buttons to move between pages.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun WhatsNewDialog(visible: Boolean, releases: List<WhatsNewRelease>, onDismiss: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(VisageMotion.fadeInSpec()),
        exit = fadeOut(VisageMotion.fadeOutSpec<Float>()),
    ) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
            contentAlignment = Alignment.Center,
        ) {
            val pages = remember(releases) { releases.flatMap { r -> r.features.map { FeaturePage(r.version, it) } } }
            val pageCount = pages.size + 1
            val pager = rememberPagerState { pageCount }
            val scope = rememberCoroutineScope()
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

            fun go(delta: Int) {
                val target = (pager.currentPage + delta).coerceIn(0, pageCount - 1)
                if (target != pager.currentPage) scope.launch { pager.animateScrollToPage(target) }
            }

            val wheelLockedUntil = remember { longArrayOf(0L) }
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 16.dp,
                modifier = Modifier
                    .padding(if (com.music.vivi.compose.ui.responsive.windowLayout().isMobile) 10.dp else 24.dp)
                    .widthIn(max = 760.dp)
                    .heightIn(max = 760.dp)
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .focusable()
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.DirectionRight, Key.PageDown -> { go(1); true }
                            Key.DirectionLeft, Key.PageUp -> { go(-1); true }
                            Key.Escape -> { onDismiss(); true }
                            else -> false
                        }
                    }
                    .onPointerEvent(PointerEventType.Scroll) { event ->
                        val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                        val now = System.currentTimeMillis()
                        // The changelog page scrolls on its own, so the wheel only pages through the features.
                        if (dy == 0f || now < wheelLockedUntil[0] || pager.currentPage == pageCount - 1) return@onPointerEvent
                        wheelLockedUntil[0] = now + 450
                        go(if (dy > 0f) 1 else -1)
                        event.changes.forEach { it.consume() }
                    },
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Header(releases.first().version, pager.currentPage, pageCount, onDismiss)
                    LookChangedNotice()
                    HorizontalPager(
                        state = pager,
                        modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
                        beyondViewportPageCount = 1,
                    ) { index ->
                        if (index < pages.size) {
                            FeaturePageContent(pages[index], active = pager.currentPage == index)
                        } else {
                            ChangelogPage(releases)
                        }
                    }
                    Footer(
                        page = pager.currentPage,
                        pageCount = pageCount,
                        onPage = { p -> scope.launch { pager.animateScrollToPage(p) } },
                        onBack = { go(-1) },
                        onNext = { if (pager.currentPage == pageCount - 1) onDismiss() else go(1) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(version: String, page: Int, count: Int, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("What's new in VIsage $version", style = MaterialTheme.typography.titleLarge)
            Text(
                if (page == count - 1) "Changelog" else "${page + 1} of ${count - 1}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Close") }
    }
}

@Composable
private fun FeaturePageContent(page: FeaturePage, active: Boolean) {
    // Scrolls: in a short window the page is taller than the dialog.
    Column(Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp)) {
        WhatsNewVideo(
            version = page.version,
            feature = page.feature,
            active = active,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(20.dp)),
        )
        Spacer(Modifier.height(16.dp))
        Text(page.feature.title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            page.feature.summary,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            page.feature.howTo,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private sealed interface ChangeRow {
    class Version(val version: String) : ChangeRow
    class Entry(val entry: ChangeEntry) : ChangeRow
}

@Composable
private fun ChangelogPage(releases: List<WhatsNewRelease>) {
    val rows = remember(releases) {
        releases.flatMap { r -> listOf<ChangeRow>(ChangeRow.Version(r.version)) + r.changes.map { ChangeRow.Entry(it) } }
    }
    LazyColumn(
        Modifier.fillMaxWidth().heightIn(max = 560.dp).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(rows) { row ->
            when (row) {
                is ChangeRow.Version -> Text(
                    "Version ${row.version}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                is ChangeRow.Entry -> Row(verticalAlignment = Alignment.Top) {
                    KindChip(row.entry.kind)
                    Spacer(Modifier.size(10.dp))
                    Text(
                        row.entry.text,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f).padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun KindChip(kind: ChangeKind) {
    Box(
        Modifier.widthIn(min = 84.dp).clip(RoundedCornerShape(8.dp)).background(kind.color.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "[${kind.label}]",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = kind.color,
        )
    }
}

@Composable
private fun Footer(page: Int, pageCount: Int, onPage: (Int) -> Unit, onBack: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        ExpressiveButton(
            text = "Back", onClick = onBack, size = ExpressiveSize.Small,
            style = ExpressiveStyle.Text, enabled = page > 0,
        )
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            repeat(pageCount) { i ->
                val size by animateFloatAsState(if (i == page) 1f else 0f, VisageMotion.spatialFast(), label = "dot")
                Box(
                    Modifier.padding(3.dp).size((8 + 4 * size).dp).clip(CircleShape)
                        .background(
                            if (i == page) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        )
                        .clickable { onPage(i) },
                )
            }
        }
        ExpressiveButton(
            text = if (page == pageCount - 1) "Got it" else "Next",
            onClick = onNext, size = ExpressiveSize.Small, style = ExpressiveStyle.Filled,
        )
    }
}


/**
 * Shown when the default look changed under an existing user (blur glass, Distort background at 85%): the choice is
 * to keep it or to restore exactly what they had before.
 */
@Composable
private fun LookChangedNotice() {
    val prefs = org.koin.compose.koinInject<com.music.vivi.compose.prefs.DesktopPrefs>()
    @Suppress("UNUSED_VARIABLE") val version = com.music.vivi.compose.ui.settings.rememberPrefsVersion(prefs)
    if (!com.music.vivi.compose.prefs.LookMigration.pending(prefs)) return
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("The default look changed", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "VIsage now starts with frosted-glass blur and a Distort background at 85% darkness. " +
                    "Anything you set yourself is untouched. Not into it? You can put your previous look back.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpressiveButton(
                    "Keep my previous look",
                    { com.music.vivi.compose.prefs.LookMigration.restorePrevious(prefs) },
                    size = ExpressiveSize.XSmall,
                )
                ExpressiveButton(
                    "Keep the new look",
                    { com.music.vivi.compose.prefs.LookMigration.acknowledge(prefs) },
                    size = ExpressiveSize.XSmall,
                    style = ExpressiveStyle.Text,
                )
            }
        }
    }
}
