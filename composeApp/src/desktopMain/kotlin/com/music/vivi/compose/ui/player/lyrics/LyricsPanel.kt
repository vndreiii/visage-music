package com.music.vivi.compose.ui.player.lyrics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.music.lyricsync.LyricsParser
import com.music.lyricsync.LyricsTimeline
import com.music.lyricsync.ParsedLyrics
import com.music.lyricsync.PositionInterpolator
import com.music.vivi.compose.LocalPlaybackController
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.LyricsProvider
import com.music.vivi.compose.prefs.LyricsTextAlign
import com.music.vivi.compose.prefs.PlayerBackgroundStyle
import com.music.vivi.compose.prefs.PrefKeys
import com.music.vivi.compose.theme.IdleArtworkPalette
import com.music.vivi.compose.theme.LocalArtworkPalette
import com.music.vivi.compose.ui.component.VisageLoadingIndicator
import com.music.vivi.player.PlaybackState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

private const val IDLE_COLLAPSE_MS = 2_400L

/**
 * Smoothed playback position (ms, per frame) for lyric rendering. The coarse player position is
 * extrapolated with the wall clock while playing; writes only invalidate draw / derived readers.
 */
@Composable
internal fun rememberLyricsClock(position: StateFlow<Long>, playing: Boolean, offsetMs: Int): State<Float> {
    val out = remember { mutableFloatStateOf(0f) }
    val interp = remember { PositionInterpolator() }
    val offset by rememberUpdatedState(offsetMs)
    LaunchedEffect(position) {
        position.collect {
            val now = System.nanoTime()
            interp.report(it, now)
            out.floatValue = interp.estimate(now) + offset
        }
    }
    LaunchedEffect(playing) {
        interp.setPlaying(playing, System.nanoTime())
        if (playing) {
            while (true) withFrameNanos { out.floatValue = interp.estimate(System.nanoTime()) + offset }
        } else {
            out.floatValue = interp.estimate(System.nanoTime()) + offset
        }
    }
    return out
}

/** Accent used for glow / gradient / gap indicator. */
@Composable
internal fun lyricsAccent(mode: LyricsAccent): Color {
    val palette = LocalArtworkPalette.current ?: IdleArtworkPalette
    return when (mode) {
        LyricsAccent.WHITE -> Color.White
        LyricsAccent.THEME -> MaterialTheme.colorScheme.primary
        LyricsAccent.ARTWORK -> androidx.compose.ui.graphics.lerp(palette.glowColors.firstOrNull() ?: palette.seed, Color.White, 0.2f)
    }
}

/**
 * Lyrics over the player backdrop: per-word karaoke sync, glow, springy focus, optional background.
 * All look & feel comes from [DesktopPrefs] (Settings -> Now Playing -> Lyrics) and applies live.
 */
@Composable
fun LyricsPanel(
    @Suppress("UNUSED_PARAMETER") onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val controller = LocalPlaybackController.current
    val prefs = koinInject<DesktopPrefs>()
    val nowPlaying by controller.nowPlaying.collectAsState()
    val durationMs by controller.durationMs.collectAsState()
    val playbackState by controller.state.collectAsState()
    var prefsTick by remember { mutableStateOf(0) }
    DisposableEffect(prefs) {
        val listener = { prefsTick += 1 }
        prefs.addListener(listener)
        onDispose { prefs.removeListener(listener) }
    }
    val cfg = remember(prefsTick) { prefs.lyricsConfig() }

    var parsed by remember { mutableStateOf(ParsedLyrics.Empty) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val provider = remember(prefsTick) {
        runCatching {
            LyricsProvider.valueOf(prefs.getString(PrefKeys.LyricsProvider, LyricsProvider.LRCLIB.name))
        }.getOrDefault(LyricsProvider.LRCLIB)
    }

    LaunchedEffect(nowPlaying?.videoId, nowPlaying?.title, provider, durationMs) {
        val track = nowPlaying
        if (track == null) {
            parsed = ParsedLyrics.Empty
            return@LaunchedEffect
        }
        loading = true
        error = null
        parsed = ParsedLyrics.Empty
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
            parsed = withContext(Dispatchers.Default) { LyricsParser.parse(raw) }
            if (parsed.lines.isEmpty()) error = "No lyrics found"
        }.onFailure {
            error = it.message ?: "Lyrics lookup failed"
            parsed = ParsedLyrics.Empty
        }
    }

    val timeline = remember(parsed, cfg.estimateWords, cfg.showBackgroundVocals, cfg.gapStyle) {
        LyricsTimeline.build(
            parsed,
            estimateWords = cfg.estimateWords,
            minGapMs = if (cfg.gapStyle == LyricsGapStyle.NONE) Long.MAX_VALUE / 4 else LyricsTimeline.DEFAULT_MIN_GAP_MS,
            showBackground = cfg.showBackgroundVocals,
        )
    }
    val clock = rememberLyricsClock(controller.positionMs, playbackState == PlaybackState.Playing, cfg.syncOffsetMs)
    val accent = lyricsAccent(cfg.accent)
    val bgStyle = remember(prefsTick) {
        runCatching { PlayerBackgroundStyle.valueOf(prefs.getString(PrefKeys.PlayerBackgroundStyle, PlayerBackgroundStyle.GRADIENT.name)) }
            .getOrDefault(PlayerBackgroundStyle.GRADIENT)
    }

    var align by remember(cfg.align) { mutableStateOf(cfg.align) }

    Box(modifier.fillMaxSize()) {
        LyricsBackground(
            mode = cfg.background,
            intensity = cfg.backgroundIntensity,
            artworkUrl = nowPlaying?.artworkUrl,
            palette = LocalArtworkPalette.current,
            playerBackgroundStyle = bgStyle,
        )
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                VisageLoadingIndicator(color = Color.White)
            }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error ?: "", color = Color.White.copy(alpha = 0.55f), textAlign = TextAlign.Center)
            }
            timeline.lines.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No lyrics available", color = Color.White.copy(alpha = 0.5f))
            }
            else -> LyricsList(
                timeline = timeline,
                cfg = cfg.copy(align = align),
                clock = clock,
                accent = accent,
                onSeek = { controller.seekTo(it) },
            )
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
