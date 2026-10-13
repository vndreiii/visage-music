package com.music.vivi.compose.ui.player.lyrics

import com.music.vivi.compose.ui.component.smoothWheelScroll

import androidx.compose.runtime.derivedStateOf

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatAlignLeft
import androidx.compose.material.icons.automirrored.outlined.FormatAlignRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.music.vivi.compose.LocalPlaybackController
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.LyricsProvider
import com.music.vivi.compose.prefs.LyricsTextAlign
import com.music.vivi.compose.prefs.PrefKeys
import com.music.vivi.compose.ui.component.ViviLoadingIndicator
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import kotlin.math.abs

data class LyricLine(val timeMs: Long?, val text: String)

private const val IDLE_COLLAPSE_MS = 2_400L

/**
 * Floating lyrics over the player backdrop.
 *
 * Readable first: no edge blackouts, no distance blur mush.
 * Hierarchy = scale + opacity (+ soft text shadow on the active line only).
 */
@Composable
fun LyricsPanel(
    @Suppress("UNUSED_PARAMETER") onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val controller = LocalPlaybackController.current
    val prefs = koinInject<DesktopPrefs>()
    val nowPlaying by controller.nowPlaying.collectAsState()
    val positionState = controller.positionMs.collectAsState()
    val durationMs by controller.durationMs.collectAsState()
    var prefsTick by remember { mutableStateOf(0) }
    DisposableEffect(prefs) {
        val listener = { prefsTick += 1 }
        prefs.addListener(listener)
        onDispose { prefs.removeListener(listener) }
    }
    @Suppress("UNUSED_VARIABLE")
    val observePrefs = prefsTick

    var lines by remember { mutableStateOf<List<LyricLine>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val density = androidx.compose.ui.platform.LocalDensity.current.density

    val alignPref = runCatching {
        LyricsTextAlign.valueOf(
            prefs.getString(PrefKeys.LyricsTextAlign, LyricsTextAlign.CENTER.name),
        )
    }.getOrDefault(LyricsTextAlign.CENTER)
    var align by remember(alignPref) { mutableStateOf(alignPref) }
    val textAlign = when (align) {
        LyricsTextAlign.LEFT -> TextAlign.Start
        LyricsTextAlign.CENTER -> TextAlign.Center
        LyricsTextAlign.RIGHT -> TextAlign.End
    }

    val provider = runCatching {
        LyricsProvider.valueOf(
            prefs.getString(PrefKeys.LyricsProvider, LyricsProvider.LRCLIB.name),
        )
    }.getOrDefault(LyricsProvider.LRCLIB)

    LaunchedEffect(nowPlaying?.videoId, nowPlaying?.title, provider, durationMs) {
        val track = nowPlaying
        if (track == null) {
            lines = emptyList()
            return@LaunchedEffect
        }
        loading = true
        error = null
        lines = emptyList()
        val localLyrics = withContext(Dispatchers.IO) {
            val file = track.lyricsPath?.let { java.io.File(it) }
                ?: track.localPath?.let { java.io.File(it).let { audio -> java.io.File(audio.parentFile, audio.nameWithoutExtension + ".lrc") } }
            runCatching { file?.takeIf { it.isFile }?.readText() }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: track.embeddedLyrics?.takeIf { it.isNotBlank() }
        }
        val result = if (localLyrics != null) Result.success(localLyrics) else fetchLyrics(
            provider = provider,
            title = track.title,
            artist = track.artist,
            durationMs = durationMs.takeIf { it > 0 } ?: track.durationMs,
            album = track.album,
            videoId = track.videoId,
        )
        loading = false
        result.onSuccess { raw ->
            lines = parseLrc(raw)
            if (lines.isEmpty()) error = "No lyrics found"
        }.onFailure {
            error = it.message ?: "Lyrics lookup failed"
            lines = emptyList()
        }
    }

    // derivedStateOf: recompose only when the active line changes, not on every position tick.
    val activeIndex by remember(lines) {
        derivedStateOf {
            val positionMs = positionState.value
            var idx = -1
            for (i in lines.indices) {
                val t = lines[i].timeMs ?: continue
                if (t <= positionMs) idx = i else break
            }
            idx
        }
    }

    // Pause auto-follow while the user scrolls the lyrics by hand; resume shortly after.
    var userScrolledAt by remember { mutableStateOf(0L) }
    val dragging = listState.isScrollInProgress
    var autoScrolling by remember { mutableStateOf(false) }
    LaunchedEffect(dragging) {
        if (dragging && !autoScrolling) userScrolledAt = System.currentTimeMillis()
    }

    LaunchedEffect(activeIndex, lines) {
        if (activeIndex < 0) return@LaunchedEffect
        if (System.currentTimeMillis() - userScrolledAt < 3500) return@LaunchedEffect
        autoScrolling = true
        try {
            val target = activeIndex + 1 // +1 for the leading spacer item
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == target }
            if (item == null) {
                listState.scrollToItem(target)
            }
            val fresh = listState.layoutInfo
            val placed = fresh.visibleItemsInfo.firstOrNull { it.index == target } ?: return@LaunchedEffect
            val delta = placed.offset + placed.size / 2f - fresh.viewportSize.height / 2f
            listState.animateScrollBy(
                delta,
                androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
                ),
            )
        } finally {
            autoScrolling = false
        }
    }

    Box(modifier.fillMaxSize()) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ViviLoadingIndicator(color = Color.White)
            }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error ?: "", color = Color.White.copy(alpha = 0.55f), textAlign = TextAlign.Center)
            }
            lines.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No lyrics available", color = Color.White.copy(alpha = 0.5f))
            }
            else -> {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxSize()
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.1f to Color.Black,
                                    0.9f to Color.Black,
                                    1f to Color.Transparent,
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                        .smoothWheelScroll(listState)
                ) {
                    item { Spacer(Modifier.height(350.dp)) }
                    itemsIndexed(lines, key = { i, line -> "${line.timeMs}-$i-${line.text}" }) { index, line ->
                        LyricLineItem(
                            text = line.text,
                            active = index == activeIndex,
                            distance = if (activeIndex < 0) 1 else abs(index - activeIndex),
                            textAlign = textAlign,
                            onClick = line.timeMs?.let { ms -> { userScrolledAt = 0L; controller.seekTo(ms) } },
                        )
                    }
                    item { Spacer(Modifier.height(350.dp)) }
                }
            }
        }

        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 4.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LyricsProviderControl(
                current = provider,
                onSelect = { prefs.setString(PrefKeys.LyricsProvider, it.name) },
            )
            LyricsAlignControls(
                current = align,
                onSelect = { next ->
                    align = next
                    prefs.setString(PrefKeys.LyricsTextAlign, next.name)
                },
            )
        }
    }
}

@Composable
private fun LyricsAlignControls(
    current: LyricsTextAlign,
    onSelect: (LyricsTextAlign) -> Unit,
) {
    IdleCollapseCluster(
        collapsedContent = { expand ->
            AlignChip(selected = true, onClick = expand) {
                Icon(alignIcon(current), current.name, tint = Color.White)
            }
        },
        expandedContent = { collapseAfterSelect ->
            Row(
                Modifier.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LyricsTextAlign.entries.forEach { option ->
                    AlignChip(
                        selected = current == option,
                        onClick = {
                            onSelect(option)
                            collapseAfterSelect()
                        },
                    ) {
                        Icon(alignIcon(option), option.name, tint = Color.White)
                    }
                }
            }
        },
    )
}

@Composable
private fun LyricsProviderControl(
    current: LyricsProvider,
    onSelect: (LyricsProvider) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    var expanded by remember { mutableStateOf(false) }
    var lastInteract by remember { mutableLongStateOf(0L) }

    fun bump() {
        lastInteract = System.currentTimeMillis()
        expanded = true
    }

    LaunchedEffect(expanded, lastInteract, hovered, menuOpen) {
        if (!expanded || hovered || menuOpen) return@LaunchedEffect
        delay(IDLE_COLLAPSE_MS)
        if (System.currentTimeMillis() - lastInteract >= IDLE_COLLAPSE_MS && !hovered && !menuOpen) {
            expanded = false
        }
    }

    Box {
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.28f),
            modifier = Modifier.hoverable(hoverSource),
        ) {
            AnimatedContent(
                targetState = expanded,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "lyricsProviderCollapse",
            ) { open ->
                if (!open) {
                    IconButton(onClick = { bump(); menuOpen = true }) {
                        Icon(Icons.Outlined.Lyrics, "Lyrics provider", tint = Color.White)
                    }
                } else {
                    Surface(
                        onClick = { bump(); menuOpen = true },
                        shape = RoundedCornerShape(999.dp),
                        color = Color.Transparent,
                    ) {
                        Text(
                            current.label,
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
        if (menuOpen) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, 52),
                onDismissRequest = { menuOpen = false },
                properties = PopupProperties(focusable = true),
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 8.dp,
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .widthIn(min = 180.dp, max = 260.dp)
                        .heightIn(max = 320.dp),
                ) {
                    androidx.compose.foundation.layout.Column(
                        Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 8.dp),
                    ) {
                        LyricsProvider.entries.forEach { option ->
                            Surface(
                                onClick = {
                                    onSelect(option)
                                    menuOpen = false
                                    bump()
                                },
                                color = Color.Transparent,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(
                                        option.label,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (option == current) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                    if (option == current) {
                                        Icon(
                                            Icons.Outlined.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IdleCollapseCluster(
    collapsedContent: @Composable (expand: () -> Unit) -> Unit,
    expandedContent: @Composable (collapseAfterSelect: () -> Unit) -> Unit,
) {
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    var expanded by remember { mutableStateOf(false) }
    var lastInteract by remember { mutableLongStateOf(0L) }

    fun bump() {
        lastInteract = System.currentTimeMillis()
        expanded = true
    }

    LaunchedEffect(expanded, lastInteract, hovered) {
        if (!expanded || hovered) return@LaunchedEffect
        delay(IDLE_COLLAPSE_MS)
        if (System.currentTimeMillis() - lastInteract >= IDLE_COLLAPSE_MS && !hovered) {
            expanded = false
        }
    }

    LaunchedEffect(hovered) {
        if (hovered) bump()
    }

    Surface(
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.28f),
        modifier = Modifier.hoverable(hoverSource),
    ) {
        AnimatedContent(
            targetState = expanded,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "lyricsAlignCollapse",
        ) { open ->
            if (!open) {
                collapsedContent { bump() }
            } else {
                expandedContent {
                    lastInteract = System.currentTimeMillis()
                    expanded = false
                }
            }
        }
    }
}

@Composable
private fun AlignChip(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    IconButton(onClick = onClick) {
        Surface(
            shape = CircleShape,
            color = if (selected) Color.White.copy(alpha = 0.22f) else Color.Transparent,
        ) {
            Box(Modifier.padding(6.dp), contentAlignment = Alignment.Center) {
                content()
            }
        }
    }
}

private fun alignIcon(align: LyricsTextAlign): ImageVector = when (align) {
    LyricsTextAlign.LEFT -> Icons.AutoMirrored.Outlined.FormatAlignLeft
    LyricsTextAlign.CENTER -> Icons.Outlined.FormatAlignCenter
    LyricsTextAlign.RIGHT -> Icons.AutoMirrored.Outlined.FormatAlignRight
}

@Composable
private fun LyricLineItem(
    text: String,
    active: Boolean,
    distance: Int,
    textAlign: TextAlign,
    onClick: (() -> Unit)? = null,
) {
    val targetScale = when {
        active -> 1.06f
        distance == 1 -> 0.97f
        else -> 0.94f
    }
    val targetAlpha = when {
        active -> 1f
        distance == 1 -> 0.55f
        distance == 2 -> 0.32f
        else -> 0.18f
    }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = targetScale,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
        ),
        label = "lyricScale",
    )
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = androidx.compose.animation.core.tween(220),
        label = "lyricAlpha",
    )
    Text(
        text = text,
        textAlign = textAlign,
        style = TextStyle(
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            shadow = if (active) {
                Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 2f), 8f)
            } else {
                null
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ) else Modifier
            )
            .padding(vertical = 10.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                    when (textAlign) {
                        TextAlign.Left, TextAlign.Start -> 0f
                        TextAlign.Right, TextAlign.End -> 1f
                        else -> 0.5f
                    },
                    0.5f,
                )
            },
    )
}

internal fun parseLrc(raw: String): List<LyricLine> {
    if (raw.isBlank()) return emptyList()
    val timed = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]\s*(.*)""")
    val out = mutableListOf<LyricLine>()
    raw.lineSequence().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return@forEach
        // Paxsenix / TTML rich-sync word rows: <Walk:7.022:7.291|into:7.291:7.657|…>
        if (isRichSyncWordLine(trimmed)) return@forEach

        val m = timed.find(trimmed)
        if (m != null) {
            val min = m.groupValues[1].toLong()
            val sec = m.groupValues[2].toLong()
            val frac = m.groupValues[3].ifBlank { "0" }
            val msPart = when (frac.length) {
                1 -> frac.toLong() * 100
                2 -> frac.toLong() * 10
                else -> frac.take(3).toLong()
            }
            val text = stripLyricsMeta(m.groupValues[4])
            if (text.isNotEmpty()) out += LyricLine(min * 60_000 + sec * 1000 + msPart, text)
        } else if (!trimmed.startsWith("[")) {
            val plain = stripLyricsMeta(trimmed)
            if (plain.isNotEmpty()) out += LyricLine(null, plain)
        }
    }
    return out
}

/** Strip Paxsenix/TTML agent + background markers from a lyric line body. */
private fun stripLyricsMeta(text: String): String =
    text
        .replace(Regex("""\{agent:[^}]+\}"""), "")
        .replace(Regex("""\{bg\}"""), "")
        .trim()

/** Word-level sync payload on its own line (not shown in the desktop lyrics list). */
private fun isRichSyncWordLine(line: String): Boolean {
    if (!line.startsWith("<") || !line.endsWith(">")) return false
    val inner = line.removeSurrounding("<", ">")
    // Paxsenix: word:start:end|word:start:end (single word may not have a pipe)
    if (inner.contains(':') && (inner.contains('|') || inner.count { it == ':' } >= 2)) return true
    // Alternate rich LRC: <00:01.23> word …
    if (Regex("""^\d{1,2}:\d{2}""").containsMatchIn(inner)) return true
    return false
}
