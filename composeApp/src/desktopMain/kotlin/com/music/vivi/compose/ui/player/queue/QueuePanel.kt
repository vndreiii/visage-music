package com.music.vivi.compose.ui.player.queue

import com.music.vivi.compose.ui.component.visageMarquee
import com.music.vivi.compose.ui.component.CoverImage
import com.music.vivi.compose.ui.component.WheelLazyRow
import com.music.vivi.compose.ui.component.smoothWheelScroll

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import kotlin.math.roundToInt
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.vivi.compose.LocalPlaybackController
import com.music.vivi.compose.ui.component.InteractionCornerRadius
import com.music.vivi.compose.ui.motion.VisageMotion
import com.music.vivi.database.PlayHistoryRepository
import com.music.vivi.database.Play_event
import com.music.vivi.player.NowPlaying
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

private enum class QueueTab { Queue, Recent }

private val PinOffsetSpring = VisageMotion.rail<IntOffset>()
private val PinSizeSpring = VisageMotion.rail<IntSize>()
private val TrayOffsetSpring = VisageMotion.settle<IntOffset>()

/**
 * Right-rail queue: slides in from the trailing edge and expands width so the
 * main Now Playing column is pushed inward.
 */
@Composable
fun AnimatedQueuePinnedRail(
    visible: Boolean,
    onDismiss: () -> Unit,
    onExpandFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
    /** Dock to the leading edge instead of the trailing one (sidebar-side setting). */
    fromStart: Boolean = false,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(animationSpec = PinOffsetSpring) { if (fromStart) -it else it } +
            expandHorizontally(animationSpec = PinSizeSpring, expandFrom = if (fromStart) Alignment.Start else Alignment.End),
        exit = slideOutHorizontally(animationSpec = PinOffsetSpring) { if (fromStart) -it else it } +
            shrinkHorizontally(animationSpec = PinSizeSpring, shrinkTowards = if (fromStart) Alignment.Start else Alignment.End),
        modifier = modifier,
    ) {
        QueuePinnedPanel(
            onDismiss = onDismiss,
            onSwitchMode = onExpandFullscreen,
            mirrored = fromStart,
        )
    }
}

/**
 * Bottom tray queue: flush to the window bottom (not a floating dialog), rounded top only.
 */
@Composable
fun BoxScope.AnimatedQueueBottomTray(
    visible: Boolean,
    onDismiss: () -> Unit,
    onPin: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss,
                ),
        )
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(animationSpec = TrayOffsetSpring) { it } + fadeIn(),
        exit = slideOutVertically(animationSpec = TrayOffsetSpring) { it } + fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .fillMaxHeight(0.92f),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = if (com.music.vivi.compose.ui.glass.glassActive()) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 16.dp,
        ) {
            Box {
                com.music.vivi.compose.ui.glass.GlassBackdrop(
                    tint = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                    modifier = Modifier.matchParentSize(),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                )
                QueueFullscreenPanel(
                    onDismiss = onDismiss,
                    onSwitchMode = onPin,
                )
            }
        }
    }
}

@Composable
fun QueuePinnedPanel(
    onDismiss: () -> Unit,
    onSwitchMode: () -> Unit,
    modifier: Modifier = Modifier,
    mirrored: Boolean = false,
) {
    Surface(
        modifier = modifier
            .width(360.dp)
            .fillMaxHeight()
            .padding(
                start = if (mirrored) 8.dp else 4.dp, top = 8.dp,
                end = if (mirrored) 4.dp else 8.dp, bottom = 8.dp,
            ),
        shape = RoundedCornerShape(20.dp),
        color = if (com.music.vivi.compose.ui.glass.glassActive()) Color.Transparent
            else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
        tonalElevation = if (com.music.vivi.compose.ui.glass.glassActive()) 0.dp else 3.dp,
    ) {
        Box {
            // Blur setting on: frosted glass over the tinted page background.
            com.music.vivi.compose.ui.glass.GlassBackdrop(
                tint = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f),
                modifier = Modifier.matchParentSize(),
                shape = RoundedCornerShape(20.dp),
            )
            QueuePanelBody(
                pinned = true,
                onDismiss = onDismiss,
                onSwitchMode = onSwitchMode,
            )
        }
    }
}

@Composable
fun QueueFullscreenPanel(
    onDismiss: () -> Unit,
    onSwitchMode: () -> Unit,
) {
    QueuePanelBody(
        pinned = false,
        onDismiss = onDismiss,
        onSwitchMode = onSwitchMode,
    )
}

@Composable
private fun QueuePanelBody(
    pinned: Boolean,
    onDismiss: () -> Unit,
    onSwitchMode: () -> Unit,
) {
    val controller = LocalPlaybackController.current
    val history = koinInject<PlayHistoryRepository>()
    val scope = rememberCoroutineScope()
    val nowPlaying by controller.nowPlaying.collectAsState()
    val queueTracks by controller.queueTracks.collectAsState()
    val queueIndex by controller.queueIndex.collectAsState()
    val radioActive by controller.radioActive.collectAsState()
    var tab by remember { mutableStateOf(QueueTab.Queue) }
    var recent by remember { mutableStateOf<List<Play_event>>(emptyList()) }

    LaunchedEffect(tab) {
        if (tab == QueueTab.Recent) {
            recent = withContext(Dispatchers.IO) { history.recentDistinct(80) }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabLabel("Queue", selected = tab == QueueTab.Queue) { tab = QueueTab.Queue }
            Spacer(Modifier.width(4.dp))
            TabLabel("History", selected = tab == QueueTab.Recent) { tab = QueueTab.Recent }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSwitchMode) {
                Icon(
                    if (pinned) Icons.Outlined.OpenInFull else Icons.Outlined.PushPin,
                    contentDescription = if (pinned) "Fullscreen queue" else "Pin queue to the right",
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Close")
            }
        }

        if (!pinned) {
            Spacer(Modifier.height(12.dp))
            NowPlayingHeader(nowPlaying)
            Spacer(Modifier.height(12.dp))
        } else {
            Spacer(Modifier.height(8.dp))
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ModeChip(Icons.Outlined.Shuffle, "Shuffle", Modifier.weight(1f)) {
                controller.toggleShuffle()
            }
            ModeChip(Icons.Outlined.Repeat, "Repeat", Modifier.weight(1f)) {
                controller.cycleRepeatMode()
            }
            ModeChip(Icons.Outlined.Radio, "Radio", Modifier.weight(1f), active = radioActive) {
                val id = nowPlaying?.videoId ?: return@ModeChip
                scope.launch(Dispatchers.IO) {
                    runCatching { controller.startRadio(id) }
                }
            }
        }
        if (!pinned) {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        nowPlaying?.album ?: "Queue",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.visageMarquee(),
                    )
                    NextLabel(
                        if (radioActive) "Next from Radio" else "Next in Queue",
                        radio = radioActive,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    "${queueTracks.size} songs",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            WheelLazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val chips = listOf("All", "Popular", "Discover", "Deep cuts", "Party", "Electronic")
                items(chips) { label ->
                    FilterChip(
                        selected = label == "All",
                        onClick = {},
                        label = { Text(label) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        AnimatedContent(
            targetState = tab,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (slideInHorizontally(VisageMotion.settle()) { it / 6 * dir } + fadeIn(VisageMotion.fadeInSpec())) togetherWith
                    (slideOutHorizontally(VisageMotion.settle()) { -it / 6 * dir } + fadeOut(VisageMotion.fadeOutSpec()))
            },
            label = "queueTab",
        ) { shownTab ->
        Column(Modifier.fillMaxSize()) {
        when (shownTab) {
            QueueTab.Queue -> {
                if (pinned && nowPlaying != null) {
                    Text(
                        "Now playing",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    QueueSongRow(
                        title = nowPlaying!!.title,
                        subtitle = nowPlaying!!.artist,
                        artworkUrl = nowPlaying!!.artworkUrl,
                        active = true,
                        onClick = {},
                    )
                    Spacer(Modifier.height(12.dp))
                    NextLabel(
                        if (radioActive) "Next from Radio" else "Next from queue",
                        radio = radioActive,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                val listState = rememberLazyListState()
                val queueLoading by controller.queueLoading.collectAsState()
                // Only what comes after the current song; the current one lives in the header,
                // so a picked track always sits directly under "Now playing".
                // The header flips to the picked song a beat before the queue swaps; until the two
                // agree, show placeholders instead of the previous song's follow-ups.
                val inSync = nowPlaying == null || queueTracks.getOrNull(queueIndex)?.videoId == nowPlaying?.videoId
                val upcoming = remember(queueTracks, queueIndex, inSync) {
                    when {
                        !inSync -> emptyList()
                        queueIndex in queueTracks.indices -> queueTracks.drop(queueIndex + 1)
                        else -> queueTracks
                    }
                }
                // Follow-ups appear one by one as they arrive instead of popping in as a block.
                var shown by remember { mutableIntStateOf(upcoming.size) }
                var revealing by remember { mutableStateOf(false) }
                LaunchedEffect(upcoming.size) {
                    if (shown > upcoming.size) shown = upcoming.size
                    if (shown < upcoming.size) {
                        revealing = true
                        while (shown < upcoming.size) {
                            delay(55)
                            shown++
                        }
                        revealing = false
                    }
                }
                // New song picked: start from the top of its fresh follow-ups.
                LaunchedEffect(nowPlaying?.videoId) { listState.animateScrollToItem(0) }
                val placeholders = when {
                    (queueLoading || !inSync) && shown >= upcoming.size -> SkeletonCount
                    else -> queuePlaceholderCount(upcoming.size, shown, SkeletonCount)
                }

                val drag = remember(listState) { QueueDragState(listState) }
                val dropBase = queueIndex + 1
                Box(Modifier.weight(1f)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().smoothWheelScroll(listState)
                        .queueReorder(drag, listState, maxIndex = { shown.coerceAtMost(upcoming.size) - 1 }, trackAt = { upcoming.getOrNull(it) }) { f, t ->
                            controller.moveQueueItem(dropBase + f, dropBase + t)
                        },
                    state = listState,
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    if (upcoming.isEmpty() && placeholders == 0) {
                        item {
                            Text(
                                "Queue is empty",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    }
                    itemsIndexed(upcoming.take(shown), key = { i, t -> "${t.videoId}-$i" }) { i, track ->
                        val animate = remember { revealing }
                        val base = queueIndex + 1
                        // The lifted row must sit above its neighbours: zIndex has to be on the lazy item's own root.
                        RevealRow(animate, Modifier.zIndex(if (drag.from == i) 1f else 0f)) {
                            UpcomingRow(
                                track = track,
                                position = i,
                                count = upcoming.size,
                                drag = drag,
                                onPlay = {
                                    scope.launch(Dispatchers.IO) {
                                        controller.playQueue(queueTracks, base + i)
                                    }
                                },
                                onMove = { to -> controller.moveQueueItem(base + i, base + to) },
                                onRemove = { controller.removeFromQueue(base + i) },
                            )
                        }
                    }
                    items(placeholders, key = { "skeleton-$it" }) { QueueSkeletonRow(it) }
                }
                DragGhost(drag)
                }
            }
            QueueTab.Recent -> {
                val listState = rememberLazyListState()
                
                LaunchedEffect(queueIndex) {
                    if (queueIndex in queueTracks.indices) {
                        listState.animateScrollToItem(queueIndex)
                    }
                }
                
                LazyColumn(
                    modifier = Modifier.weight(1f).smoothWheelScroll(listState),
                    state = listState,
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    if (recent.isEmpty()) {
                        item {
                            Text(
                                "Nothing played yet",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    } else {
                        items(recent, key = { it.id }) { ev ->
                            QueueSongRow(
                                title = ev.title,
                                subtitle = ev.artists,
                                artworkUrl = ev.thumbnail,
                                active = ev.song_id == nowPlaying?.videoId,
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        controller.playVideo(ev.song_id)
                                    }
                                },
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

@Composable
private fun TabLabel(text: String, selected: Boolean, onClick: () -> Unit) {
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0f),
        VisageMotion.settle(),
        label = "tabContainer",
    )
    val content by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        VisageMotion.settle(),
        label = "tabContent",
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = container,
    ) {
        Box(Modifier.height(40.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                color = content,
            )
        }
    }
}

@Composable
private fun NowPlayingHeader(track: NowPlaying?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CoverImage(
            track?.artworkUrl, track?.title ?: "np",
            Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
        )
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                track?.title ?: "Nothing playing",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.visageMarquee(),
            )
            Text(
                track?.artist.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.visageMarquee(),
            )
        }
    }
}

/** Section label; with a radio icon that animates in while the upcoming tracks come from Radio. */
@Composable
private fun NextLabel(
    text: String,
    radio: Boolean,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        AnimatedVisibility(
            visible = radio,
            enter = fadeIn(VisageMotion.fadeInSpec()) + expandHorizontally(VisageMotion.settle()),
            exit = fadeOut(VisageMotion.fadeOutSpec()) + shrinkHorizontally(VisageMotion.settle()),
        ) {
            Icon(
                Icons.Outlined.Radio,
                contentDescription = "Radio",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 6.dp).size(18.dp),
            )
        }
        Text(
            text,
            style = style,
            color = if (radio) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ModeChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val container by androidx.compose.animation.animateColorAsState(
        if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        VisageMotion.settle(),
        label = "modeChipBg",
    )
    val content by androidx.compose.animation.animateColorAsState(
        if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        VisageMotion.settle(),
        label = "modeChipFg",
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = container,
        contentColor = content,
        modifier = modifier.height(48.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier.visageMarquee(),
            )
        }
    }
}

@Composable
private fun QueueSongRow(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    active: Boolean,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (active) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        } else {
            Color.Transparent
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        Box {
            CoverImage(
                artworkUrl, title,
                Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
            )
            if (active) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
                }
            }
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Clip, fontWeight = FontWeight.Medium, modifier = Modifier.visageMarquee())
            Text(
                subtitle,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.visageMarquee(),
            )
        }
        trailing?.invoke()
        }
    }
}

private const val SkeletonCount = 6

/** Fades + lifts a freshly spawned row into place; rows that were already there skip the motion. */
@Composable
private fun RevealRow(animate: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animate) progress.animateTo(1f, VisageMotion.settle())
    }
    Box(
        modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * 14.dp.toPx()
        },
    ) { content() }
}

/** Preview card scaffold shown while follow-ups are fetched. */
@Composable
private fun QueueSkeletonRow(index: Int) {
    val pulse = rememberInfiniteTransition(label = "queueSkeleton")
    val alpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            com.music.vivi.compose.ui.motion.VisageMotion.pulse(index * 90),
            RepeatMode.Reverse,
        ),
        label = "alpha",
    )
    val bar = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f * alpha)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(bar))
        Column(Modifier.padding(start = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth(0.6f - (index % 3) * 0.08f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(bar))
            Box(Modifier.fillMaxWidth(0.35f).height(9.dp).clip(RoundedCornerShape(5.dp)).background(bar))
        }
    }
}

/** Reveal state can lag a shrinking queue by a composition frame. */
internal fun queuePlaceholderCount(upcomingSize: Int, shown: Int, maximum: Int): Int =
    (upcomingSize - shown).coerceIn(0, maximum)


/**
 * Drag-to-reorder state for the upcoming list; indexes are positions within the upcoming tracks.
 * The gesture lives on the list (see [queueReorder]) and tracks the pointer in the list's own coordinates, so the
 * lifted row keeps following it while the list auto-scrolls and never depends on the row staying composed.
 */
internal class QueueDragState(private val list: androidx.compose.foundation.lazy.LazyListState) {
    var from by mutableIntStateOf(-1)
    var to by mutableIntStateOf(-1)
    /** Height of the lifted row; the rows it passes slide by this much so they make room live. */
    var itemHeight by mutableFloatStateOf(0f)
    var pointerY by mutableFloatStateOf(0f)
    private var downY = 0f
    private var startCentre = 0f
    private var settleTop by mutableStateOf<Float?>(null)
    /** The track being carried; drawn by [DragGhost] above the list so it survives its own slot scrolling away. */
    var ghost by mutableStateOf<com.music.vivi.player.QueueTrack?>(null)
    val active get() = from >= 0
    /** True while the released row glides into its slot; input is ignored until it lands. */
    var settling by mutableStateOf(false)

    /** Top edge (list coordinates) the carried row is drawn at: follows the pointer, but never leaves the list. */
    val ghostTop: Float get() = settleTop ?: run {
        val info = list.layoutInfo
        val top = info.viewportStartOffset.toFloat()
        val bottom = (info.viewportEndOffset - itemHeight).coerceAtLeast(top)
        (startCentre + (pointerY - downY) - itemHeight / 2f).coerceIn(top, bottom)
    }

    fun begin(index: Int, offset: Int, size: Int, y: Float, track: com.music.vivi.player.QueueTrack?) {
        from = index; to = index; itemHeight = size.toFloat()
        downY = y; pointerY = y; startCentre = offset + size / 2f; settleTop = null; ghost = track
    }

    /** Re-derives the target slot from where the lifted row's centre now is. */
    fun update(maxIndex: Int) {
        if (!active || settling) return
        val items = list.layoutInfo.visibleItemsInfo.filter { it.index <= maxIndex }
        if (items.isEmpty()) return
        val centre = startCentre + (pointerY - downY)
        to = (items.firstOrNull { centre >= it.offset && centre < it.offset + it.size }
            ?: if (centre < items.first().offset) items.first() else items.last()).index
    }

    /** Called every frame while dragging: scrolls the list when the pointer rests near its top or bottom edge. */
    fun autoScroll(edge: Float, maxIndex: Int) {
        if (!active || settling) return
        val info = list.layoutInfo
        val top = info.viewportStartOffset + edge
        val bottom = info.viewportEndOffset - edge
        val speed = when {
            pointerY < top -> -(((top - pointerY) / edge).coerceIn(0f, 1.5f)) * 18f
            pointerY > bottom -> (((pointerY - bottom) / edge).coerceIn(0f, 1.5f)) * 18f
            else -> 0f
        }
        if (speed == 0f) return
        list.dispatchRawDelta(speed)
        update(maxIndex)
    }

    /**
     * Lets go of the row: it glides to the slot it hovers over (or back where it started), then everything resets.
     * Returns (from,to) when a reorder should be applied.
     */
    suspend fun finish(): Pair<Int, Int>? {
        if (!active || settling) return null
        settling = true
        val result = if (to >= 0 && to != from) from to to else null
        val info = list.layoutInfo
        val dest = result?.let { (_, t) -> info.visibleItemsInfo.firstOrNull { it.index == t } }
        val start = ghostTop
        // The row ends up with its near edge flush to the slot it replaces.
        val target = when {
            dest == null -> start
            to > from -> dest.offset + dest.size - itemHeight
            else -> dest.offset.toFloat()
        }
        settleTop = start
        try {
            androidx.compose.animation.core.animate(start, target, animationSpec = VisageMotion.spatialFast()) { v, _ -> settleTop = v }
        } finally {
            from = -1; to = -1; settleTop = null; ghost = null; settling = false
        }
        return result
    }
}

/** Hold a row (or drag its handle) to lift it; see [QueueDragState]. */
internal fun Modifier.queueReorder(
    drag: QueueDragState,
    list: androidx.compose.foundation.lazy.LazyListState,
    maxIndex: () -> Int,
    trackAt: (Int) -> com.music.vivi.player.QueueTrack?,
    onDrop: (Int, Int) -> Unit,
): Modifier = pointerInput(list, drag) {
    val holdMs = 220L
    val wobble = 10.dp.toPx()
    val handleSlop = 3.dp.toPx()
    val handleZone = 56.dp.toPx()
    val edge = 72.dp.toPx()
    coroutineScope {
        val scope = this
        launch {
            while (true) { delay(16); drag.autoScroll(edge, maxIndex()) }
        }
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            if (drag.settling || currentEvent.buttons.isSecondaryPressed) return@awaitEachGesture
            val hit = list.layoutInfo.visibleItemsInfo.firstOrNull {
                down.position.y >= it.offset && down.position.y < it.offset + it.size && it.index <= maxIndex()
            } ?: return@awaitEachGesture
            val onHandle = down.position.x >= size.width - handleZone
            var lifted = false
            if (onHandle) {
                while (true) {
                    val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                    if (!ch.pressed) return@awaitEachGesture
                    if ((ch.position - down.position).getDistance() > handleSlop) { lifted = true; break }
                }
            } else {
                // A mouse wobbles, so the hold tolerates a few dp of drift rather than cancelling on the touch slop.
                val gaveUp = withTimeoutOrNull(holdMs) {
                    while (true) {
                        val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                        if (!ch.pressed || (ch.position - down.position).getDistance() > wobble) return@withTimeoutOrNull true
                    }
                    @Suppress("UNREACHABLE_CODE") false
                }
                if (gaveUp == true) return@awaitEachGesture
                lifted = true
            }
            if (!lifted) return@awaitEachGesture
            drag.begin(hit.index, hit.offset, hit.size, down.position.y, trackAt(hit.index))
            while (true) {
                // Initial pass: consume before the row's own click sees the release, so letting go never plays the track.
                val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                ch.consume()
                if (!ch.pressed) break
                drag.pointerY = ch.position.y
                drag.update(maxIndex())
            }
            scope.launch { drag.finish()?.let { (f, t) -> onDrop(f, t) } }
        }
    }
}

/** The carried row, drawn over the list at the pointer (clamped inside the list) rather than inside its lazy slot. */
@Composable
private fun DragGhost(drag: QueueDragState) {
    val track = drag.ghost ?: return
    val bg = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        Modifier
            .fillMaxWidth()
            .offset { androidx.compose.ui.unit.IntOffset(0, drag.ghostTop.roundToInt()) }
            .graphicsLayer {
                scaleX = 1.02f; scaleY = 1.02f
                alpha = 0.95f
                shadowElevation = 12.dp.toPx()
                shape = RoundedCornerShape(12.dp)
                clip = false
            }
            .background(bg, RoundedCornerShape(12.dp)),
    ) {
        QueueSongRow(
            title = track.title.ifBlank { track.videoId },
            subtitle = track.artist.ifBlank { "Queued track" },
            artworkUrl = track.artworkUrl,
            active = false,
            onClick = {},
            trailing = {
                Icon(Icons.Filled.DragHandle, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp).size(22.dp))
            },
        )
    }
}

/** Upcoming row: right-click menu, drag handle, long-press drag, drop indicator. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun UpcomingRow(
    track: com.music.vivi.player.QueueTrack,
    position: Int,
    count: Int,
    drag: QueueDragState,
    onPlay: () -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val dragging = drag.from == position
    val accent = MaterialTheme.colorScheme.primary
    // Rows between the lifted row's start and its current slot slide out of the way as it moves.
    val shiftTarget = when {
        !drag.active || dragging -> 0f
        drag.from < drag.to && position > drag.from && position <= drag.to -> -drag.itemHeight
        drag.from > drag.to && position >= drag.to && position < drag.from -> drag.itemHeight
        else -> 0f
    }
    val shift by animateFloatAsState(shiftTarget, VisageMotion.spatialFast(), label = "queueShift")
    val pos by rememberUpdatedState(position)
    Box(
        Modifier
            .fillMaxWidth()
            .zIndex(if (dragging) 1f else 0f)
            // The carried row is drawn by DragGhost; its own slot just stays empty so the gap reads as the drop target.
            .graphicsLayer { if (dragging) alpha = 0f else translationY = shift }
            .onPointerEvent(PointerEventType.Press) { if (it.buttons.isSecondaryPressed) menu = true },
    ) {
        QueueSongRow(
            title = track.title.ifBlank { track.videoId },
            subtitle = track.artist.ifBlank { "Queued track" },
            artworkUrl = track.artworkUrl,
            active = false,
            onClick = onPlay,
            trailing = {
                Icon(
                    Icons.Filled.DragHandle,
                    contentDescription = "Drag to reorder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp).size(22.dp),
                )
            },
        )
        DropdownMenu(
            expanded = menu,
            onDismissRequest = { menu = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            fun act(run: () -> Unit) = { menu = false; run() }
            DropdownMenuItem(text = { Text("Play now") }, onClick = act(onPlay))
            if (position > 0) DropdownMenuItem(text = { Text("Play next") }, onClick = act { onMove(0) })
            if (position > 0) DropdownMenuItem(text = { Text("Move up") }, onClick = act { onMove(position - 1) })
            if (position < count - 1) DropdownMenuItem(text = { Text("Move down") }, onClick = act { onMove(position + 1) })
            if (position < count - 1) DropdownMenuItem(text = { Text("Move to bottom") }, onClick = act { onMove(count - 1) })
            DropdownMenuItem(text = { Text("Remove from queue") }, onClick = act(onRemove))
        }
    }
}
