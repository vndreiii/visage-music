package com.music.vivi.compose.ui.player.queue

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
                        overflow = TextOverflow.Ellipsis,
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

                LazyColumn(
                    modifier = Modifier.weight(1f).smoothWheelScroll(listState),
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
                        RevealRow(animate) {
                            QueueSongRow(
                                title = track.title.ifBlank { track.videoId },
                                subtitle = track.artist.ifBlank { "Queued track" },
                                artworkUrl = track.artworkUrl,
                                active = false,
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        controller.playQueue(queueTracks, queueIndex + 1 + i)
                                    }
                                },
                            )
                        }
                    }
                    items(placeholders, key = { "skeleton-$it" }) { QueueSkeletonRow(it) }
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
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track?.artist.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
                overflow = TextOverflow.Ellipsis,
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
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        }
    }
}

private const val SkeletonCount = 6

/** Fades + lifts a freshly spawned row into place; rows that were already there skip the motion. */
@Composable
private fun RevealRow(animate: Boolean, content: @Composable () -> Unit) {
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animate) progress.animateTo(1f, VisageMotion.settle())
    }
    Box(
        Modifier.graphicsLayer {
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
