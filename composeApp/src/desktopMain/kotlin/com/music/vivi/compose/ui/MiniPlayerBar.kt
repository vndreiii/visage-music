package com.music.vivi.compose.ui

import com.music.vivi.compose.ui.component.CoverImage
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.vivi.compose.LocalPlaybackController
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.MiniPlayerStyle
import com.music.vivi.compose.prefs.PlayerBackgroundStyle
import com.music.vivi.compose.prefs.PrefKeys
import com.music.vivi.compose.ui.component.PlayPauseIcon
import com.music.vivi.compose.ui.component.VisageLoadingIndicator
import com.music.vivi.compose.ui.player.AudioOutputSheet
import com.music.vivi.compose.ui.player.background.MiniPlayerBackgroundLayer
import com.music.vivi.player.PlaybackState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Resolves mini-player architecture. Default is always [MiniPlayerStyle.FLOATING_PILL].
 * Prefers the explicit [PrefKeys.MiniPlayerStyle] string; falls back to legacy booleans.
 */
fun resolveMiniPlayerStyle(prefs: DesktopPrefs): MiniPlayerStyle {
    val stored = prefs.getString(PrefKeys.MiniPlayerStyle, "")
    if (stored.isNotBlank()) {
        return runCatching { MiniPlayerStyle.valueOf(stored) }.getOrDefault(MiniPlayerStyle.FLOATING_PILL)
    }
    // Legacy migration (one-shot feel): Apple flag → compact; otherwise floating pill default.
    return when {
        prefs.getBoolean(PrefKeys.UseAppleMiniPlayer, false) -> MiniPlayerStyle.COMPACT_EDGE
        prefs.getBoolean(PrefKeys.UseNewMiniPlayerDesign, true) -> MiniPlayerStyle.FLOATING_PILL
        else -> MiniPlayerStyle.FULL_WIDTH_BAR
    }
}

/**
 * Tablet-style miniplayer (desktop). Layout varies by [MiniPlayerStyle].
 *
 * @param styleOverride force a style (settings live preview); null reads prefs
 * @param backgroundStyleOverride force background (settings live preview)
 * @param interactive when false, expand / transport clicks are no-ops (preview)
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun MiniPlayerBar(
    onExpand: (() -> Unit)? = null,
    /** Makes each credited artist a link (name) -> artist page. Null keeps plain text. */
    onOpenArtist: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    styleOverride: MiniPlayerStyle? = null,
    backgroundStyleOverride: PlayerBackgroundStyle? = null,
    interactive: Boolean = true,
) {
    val controller = LocalPlaybackController.current
    val prefs = koinInject<DesktopPrefs>()
    var prefsTick by remember { mutableStateOf(0) }
    DisposableEffect(prefs) {
        val listener = { prefsTick += 1 }
        prefs.addListener(listener)
        onDispose { prefs.removeListener(listener) }
    }
    val scope = rememberCoroutineScope()
    val nowPlaying by controller.nowPlaying.collectAsState()
    val state by controller.state.collectAsState()
    val lastError by controller.lastError.collectAsState()
    val isFavorite by controller.isFavorite.collectAsState()
    val queue by controller.queueIds.collectAsState()
    val queueIndex by controller.queueIndex.collectAsState()
    // Held as State and read lazily (progress lambda / derivedStateOf) so position ticks
    // redraw only the progress line instead of recomposing the whole bar.
    val positionState = controller.positionMs.collectAsState()
    val durationState = controller.durationMs.collectAsState()
    val hasPosition by remember { derivedStateOf { positionState.value > 0L } }
    val volume by controller.volume.collectAsState()

    var showVolumeOverlay by remember { mutableStateOf(false) }
    var volumeOverlayUntil by remember { mutableLongStateOf(0L) }
    var showAudioOutput by remember { mutableStateOf(false) }

    LaunchedEffect(volumeOverlayUntil) {
        if (volumeOverlayUntil <= 0L) return@LaunchedEffect
        val wait = (volumeOverlayUntil - System.currentTimeMillis()).coerceAtLeast(0L)
        delay(wait)
        if (System.currentTimeMillis() >= volumeOverlayUntil) {
            showVolumeOverlay = false
        }
    }

    fun nudgeVolume(scrollY: Float) {
        if (!interactive) return
        val step = 0.04f
        val delta = when {
            scrollY > 0.01f -> -step
            scrollY < -0.01f -> step
            else -> return
        }
        val next = (volume + delta).coerceIn(0f, 1f)
        controller.setVolume(next)
        prefs.setFloat(PrefKeys.PlaybackVolume, next)
        showVolumeOverlay = true
        volumeOverlayUntil = System.currentTimeMillis() + 1200L
    }

    val track = nowPlaying
    val idleEmpty = track == null && state == PlaybackState.Idle && lastError == null
    val canPrev = queueIndex > 0 || hasPosition
    val canNext = queueIndex >= 0 && queueIndex + 1 < queue.size
    val playing = state == PlaybackState.Playing
    val loading = state == PlaybackState.Loading
    val progress: () -> Float = {
        val d = durationState.value
        if (d > 0L) (positionState.value.toFloat() / d.toFloat()).coerceIn(0f, 1f) else 0f
    }

    @Suppress("UNUSED_EXPRESSION")
    val miniStyle = styleOverride ?: run {
        prefsTick // recompose when Appearance prefs change
        resolveMiniPlayerStyle(prefs)
    }
    val miniBg = backgroundStyleOverride ?: runCatching {
        PlayerBackgroundStyle.valueOf(
            prefs.getString(PrefKeys.MiniPlayerBackgroundStyle, PlayerBackgroundStyle.GRADIENT.name),
        )
    }.getOrDefault(PlayerBackgroundStyle.GRADIENT)

    val hoverInteraction = remember { MutableInteractionSource() }
    val hovered by hoverInteraction.collectIsHoveredAsState()
    val hoverLift by animateDpAsState(
        targetValue = when {
            !interactive -> 0.dp
            miniStyle == MiniPlayerStyle.FULL_WIDTH_BAR || miniStyle == MiniPlayerStyle.FLOATING_PILL -> 0.dp
            hovered -> (-6).dp
            else -> 0.dp
        },
        label = "miniHoverNudge",
    )

    val outerPadding = when (miniStyle) {
        MiniPlayerStyle.FULL_WIDTH_BAR -> Modifier.padding(0.dp)
        // "Notch" from the bottom: attached to the bottom edge of the viewport, no outer bottom/top margin.
        MiniPlayerStyle.FLOATING_PILL -> Modifier.padding(horizontal = 24.dp)
        MiniPlayerStyle.COMPACT_EDGE -> Modifier.padding(end = 20.dp, bottom = 20.dp, start = 20.dp)
    }
    val boxAlign = when (miniStyle) {
        MiniPlayerStyle.COMPACT_EDGE -> Alignment.CenterEnd
        MiniPlayerStyle.FLOATING_PILL -> Alignment.BottomCenter
        else -> Alignment.Center
    }
    val surfaceShape = when (miniStyle) {
        MiniPlayerStyle.FULL_WIDTH_BAR -> RoundedCornerShape(0.dp)
        MiniPlayerStyle.FLOATING_PILL -> RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
        MiniPlayerStyle.COMPACT_EDGE -> RoundedCornerShape(20.dp)
    }
    val surfaceWidth = when (miniStyle) {
        MiniPlayerStyle.FULL_WIDTH_BAR -> Modifier.fillMaxWidth()
        MiniPlayerStyle.FLOATING_PILL -> Modifier.widthIn(max = 720.dp).fillMaxWidth()
        MiniPlayerStyle.COMPACT_EDGE -> Modifier.widthIn(max = 360.dp).fillMaxWidth(0.42f)
    }
    val elevation = when (miniStyle) {
        MiniPlayerStyle.FULL_WIDTH_BAR -> 4.dp
        MiniPlayerStyle.FLOATING_PILL -> 14.dp
        MiniPlayerStyle.COMPACT_EDGE -> 12.dp
    }

    fun expand() {
        if (interactive) onExpand?.invoke()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(outerPadding),
        contentAlignment = boxAlign,
    ) {
        Surface(
            shape = surfaceShape,
            color = Color.Transparent,
            shadowElevation = elevation,
            modifier = Modifier
                .offset(y = hoverLift)
                .hoverable(hoverInteraction)
                .then(surfaceWidth)
                .onPointerEvent(PointerEventType.Scroll) { event ->
                    val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                    if (dy != 0f) nudgeVolume(dy)
                },
        ) {
            Box {
                MiniPlayerBackgroundLayer(
                    style = miniBg,
                    artworkUrl = track?.artworkUrl,
                    modifier = Modifier.matchParentSize(),
                )
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    ) {
                        // Surface(shape) bounds hover/ripple to the circle — avoids hard-square bleed.
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.08f),
                            onClick = { expand() },
                            modifier = Modifier.size(44.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                CoverImage(track?.artworkUrl, track?.title ?: "mini", Modifier.fillMaxSize())
                                if (loading) {
                                    VisageLoadingIndicator(
                                        modifier = Modifier.size(22.dp),
                                        size = 22.dp,
                                        color = Color.White,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.size(10.dp))

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    onClick = { expand() },
                                ),
                        ) {
                            Text(
                                track?.title ?: when (state) {
                                    PlaybackState.Loading -> "Loading…"
                                    PlaybackState.Error -> "Playback error"
                                    else -> "Nothing playing"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val credits = track?.artist.orEmpty()
                            if (onOpenArtist != null && credits.isNotBlank() && state != PlaybackState.Error && !idleEmpty) {
                                com.music.vivi.compose.ui.artist.ArtistLinks(
                                    credits = credits,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.75f),
                                    hoverColor = Color.White,
                                    onOpen = onOpenArtist,
                                )
                            } else {
                                Text(
                                    when {
                                        state == PlaybackState.Error && !lastError.isNullOrBlank() ->
                                            lastError!!.take(64)
                                        state == PlaybackState.Loading && track?.artist.isNullOrBlank() -> "Resolving stream…"
                                        idleEmpty -> "Pick a song to start"
                                        else -> track?.artist?.takeIf { it.isNotBlank() } ?: ""
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.75f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        if (miniStyle != MiniPlayerStyle.COMPACT_EDGE) {
                            IconButton(
                                onClick = { if (interactive) controller.previous() },
                                enabled = interactive && (canPrev || track != null),
                            ) {
                                Icon(Icons.Filled.SkipPrevious, "Previous", tint = Color.White)
                            }
                        }
                        IconButton(
                            onClick = {
                                if (!interactive) return@IconButton
                                when (state) {
                                    PlaybackState.Playing -> controller.pause()
                                    PlaybackState.Loading -> Unit
                                    else -> controller.play()
                                }
                            },
                            enabled = interactive && (track != null || state == PlaybackState.Error),
                        ) {
                            PlayPauseIcon(
                                playing = playing,
                                tint = Color.White,
                                iconSize = if (miniStyle == MiniPlayerStyle.COMPACT_EDGE) 26.dp else 30.dp,
                            )
                        }
                        IconButton(
                            onClick = { if (interactive) controller.next() },
                            enabled = interactive && canNext,
                        ) {
                            Icon(Icons.Filled.SkipNext, "Next", tint = Color.White)
                        }
                        if (miniStyle != MiniPlayerStyle.COMPACT_EDGE) {
                            Box {
                                IconButton(onClick = { if (interactive) showAudioOutput = true }) {
                                    Icon(
                                        Icons.Outlined.Speaker,
                                        contentDescription = "Audio output",
                                        tint = Color.White.copy(alpha = 0.9f),
                                    )
                                }
                                if (showAudioOutput && interactive) {
                                    AudioOutputSheet(onDismiss = { showAudioOutput = false })
                                }
                            }
                            IconButton(
                                onClick = {
                                    if (!interactive) return@IconButton
                                    scope.launch(Dispatchers.IO) {
                                        runCatching { controller.toggleFavorite() }
                                    }
                                },
                                enabled = interactive && track != null,
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    tint = if (isFavorite) MaterialTheme.colorScheme.primary
                                    else Color.White.copy(alpha = 0.9f),
                                )
                            }
                        }
                    }

                    if (miniStyle != MiniPlayerStyle.COMPACT_EDGE) {
                        MiniSeekLine(
                            progress = progress,
                            isPlaying = playing,
                            enabled = durationState.value > 0L,
                            onSeekFraction = { f -> controller.seekTo((f * durationState.value).toLong()) },
                        )
                    }
                }

                VolumeOverlay(
                    visible = showVolumeOverlay,
                    volume = volume,
                    shape = surfaceShape,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.VolumeOverlay(
    visible: Boolean,
    volume: Float,
    shape: RoundedCornerShape,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.matchParentSize(),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f), shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Volume ${(volume * 100).toInt()}%",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}


/**
 * Seekable progress line for the mini player in the M3 Expressive style: a thin rounded track that
 * parts around a vertical pill handle. It has its own row at the bottom of the bar. Tap or drag to
 * seek; the seek commits on release.
 */
@Composable
private fun MiniSeekLine(
    progress: () -> Float,
    enabled: Boolean,
    isPlaying: Boolean,
    onSeekFraction: (Float) -> Unit,
) {
    val interaction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var drag by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Float?>(null) }
    val active = hovered || drag != null
    val spec = com.music.vivi.compose.ui.motion.VisageMotion.spatialFast<Float>()
    val trackH by androidx.compose.animation.core.animateFloatAsState(if (active) 6f else 4f, spec, label = "miniTrack")
    val handleW by androidx.compose.animation.core.animateFloatAsState(if (drag != null) 3f else 4f, spec, label = "miniHandleW")
    val handleH by androidx.compose.animation.core.animateFloatAsState(if (active) 20f else 14f, spec, label = "miniHandleH")
    var widthPx by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(1f) }
    // Takes its own row at the bottom of the bar (the bar's bottom edge is flat), so nothing overlaps it.
    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(26.dp)
                .hoverable(interaction)
                .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { o -> onSeekFraction(((o.x - 18.dp.toPx()) / (widthPx - 36.dp.toPx())).coerceIn(0f, 1f)) }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    val pad = 18.dp.toPx()
                    fun frac(x: Float) = ((x - pad) / (widthPx - 2 * pad)).coerceIn(0f, 1f)
                    detectHorizontalDragGestures(
                        onDragStart = { o -> drag = frac(o.x) },
                        onDragEnd = { drag?.let(onSeekFraction); drag = null },
                        onDragCancel = { drag = null },
                        onHorizontalDrag = { change, _ -> change.consume(); drag = frac(change.position.x) },
                    )
                },
        ) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val pad = 18.dp.toPx()
                val x0 = pad
                val x1 = size.width - pad
                val w = x1 - x0
                val th = trackH.dp.toPx()
                val cy = size.height - 13.dp.toPx()
                val f = (drag ?: progress()).coerceIn(0f, 1f)
                val hx = x0 + w * f
                val gap = 4.dp.toPx()
                val r = androidx.compose.ui.geometry.CornerRadius(th / 2f, th / 2f)
                // Unplayed part (right of the handle)
                val rightStart = (hx + gap).coerceAtMost(x1)
                if (x1 - rightStart > 0.5f) {
                    drawRoundRect(Color.White.copy(alpha = 0.30f), androidx.compose.ui.geometry.Offset(rightStart, cy - th / 2f), androidx.compose.ui.geometry.Size(x1 - rightStart, th), r)
                }
                // Played part (left of the handle)
                val leftEnd = (hx - gap).coerceAtLeast(x0)
                if (leftEnd - x0 > 0.5f) {
                    drawRoundRect(Color.White, androidx.compose.ui.geometry.Offset(x0, cy - th / 2f), androidx.compose.ui.geometry.Size(leftEnd - x0, th), r)
                }
                // Pill handle
                val hw = handleW.dp.toPx()
                val hh = handleH.dp.toPx()
                drawRoundRect(
                    Color.White,
                    androidx.compose.ui.geometry.Offset(hx - hw / 2f, cy - hh / 2f),
                    androidx.compose.ui.geometry.Size(hw, hh),
                    androidx.compose.ui.geometry.CornerRadius(hw / 2f, hw / 2f),
                )
            }
        }
    }
}
