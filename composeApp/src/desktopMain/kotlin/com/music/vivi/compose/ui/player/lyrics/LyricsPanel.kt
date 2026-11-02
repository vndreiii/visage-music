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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.FormatAlignLeft
import androidx.compose.material.icons.automirrored.outlined.FormatAlignRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import com.music.vivi.database.LyricsCacheRepository
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
    // Cap the run-ahead: when the engine stops reporting (buffering) the lyrics hold instead of racing on.
    val interp = remember { PositionInterpolator(maxExtrapolateMs = 600f) }
    val offset by rememberUpdatedState(offsetMs)
    // Paused clocks only redraw on a report; re-evaluate right away when the offset is nudged.
    LaunchedEffect(offsetMs) { out.floatValue = interp.estimate(System.nanoTime()) + offsetMs }
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
 * Which track the lyrics lookup came back empty for (not found or failed). Now Playing and the fullscreen player hide
 * the lyrics UI for that track (`showLyrics && !LyricsAvailability.isNone(videoId)`) without touching the user's toggle.
 * Toggling the lyrics button clears it, so the lookup is tried again.
 */
object LyricsAvailability {
    var noneFor by androidx.compose.runtime.mutableStateOf<String?>(null)
    fun isNone(videoId: String?) = videoId != null && noneFor == videoId
}

/**
 * Lyrics over the player backdrop: per-word karaoke sync, glow, springy focus, optional background.
 * All look & feel comes from [DesktopPrefs] (Settings -> Now Playing -> Lyrics) and applies live.
 */
@Composable
fun LyricsPanel(
    @Suppress("UNUSED_PARAMETER") onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** Popup: the toolbar and delay pill fade out after a short idle and only show while the pointer is over the panel. */
    autoHideControls: Boolean = false,
) {
    val controller = LocalPlaybackController.current
    val prefs = koinInject<DesktopPrefs>()
    val nowPlaying by controller.nowPlaying.collectAsState()
    val panelHover = remember { MutableInteractionSource() }
    val panelHovered by panelHover.collectIsHoveredAsState()
    var controlsShown by remember { mutableStateOf(true) }
    LaunchedEffect(panelHovered, autoHideControls) {
        if (!autoHideControls || panelHovered) controlsShown = true
        else { kotlinx.coroutines.delay(1200); controlsShown = false }
    }
    val controlsAlpha by androidx.compose.animation.core.animateFloatAsState(
        if (controlsShown) 1f else 0f, androidx.compose.animation.core.tween(220), label = "lyricsControlsAlpha",
    )
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

    val cache = koinInject<LyricsCacheRepository>()
    val scope = rememberCoroutineScope()
    // Per-song delay (ms, + = lyrics later), persisted next to the cached lyrics.
    var songDelayMs by remember { mutableIntStateOf(0) }
    // Provider of the lyrics on screen (cached choice or the one that just answered).
    var shownProvider by remember { mutableStateOf<LyricsProvider?>(null) }
    // The in-panel provider picker forces one fresh fetch; the effect consumes the request.
    val pendingForce = remember { arrayOfNulls<LyricsProvider>(1) }
    var forceTick by remember { mutableIntStateOf(0) }

    val provider = remember(prefsTick) {
        runCatching {
            LyricsProvider.valueOf(prefs.getString(PrefKeys.LyricsProvider, LyricsProvider.LRCLIB.name))
        }.getOrDefault(LyricsProvider.LRCLIB)
    }

    val hasDuration = durationMs > 0
    LaunchedEffect(nowPlaying?.videoId, nowPlaying?.title, hasDuration, forceTick) {
        val track = nowPlaying
        if (track == null) {
            parsed = ParsedLyrics.Empty
            shownProvider = null
            songDelayMs = 0
            return@LaunchedEffect
        }
        val force = pendingForce[0].also { pendingForce[0] = null }
        loading = true
        error = null
        parsed = ParsedLyrics.Empty
        val cached = withContext(Dispatchers.IO) { runCatching { cache.get(track.videoId) }.getOrNull() }
        songDelayMs = cached?.offsetMs ?: 0
        // Order: a .lrc next to the song, then tags embedded in the file, then the cache, then online providers.
        val localLyrics = withContext(Dispatchers.IO) {
            val lrc = track.localPath?.let { LocalLrc.find(java.io.File(it), track.lyricsPath, track.title) }
                ?: track.lyricsPath?.let { java.io.File(it) }?.takeIf { it.isFile }
            lrc?.let(LocalLrc::read) ?: track.embeddedLyrics?.takeIf { it.isNotBlank() }
        }
        var fetched: FetchedLyrics? = null
        val cachedText = cached?.lyrics
        val result = when {
            localLyrics != null -> Result.success(localLyrics)
            force == null && cachedText != null -> {
                shownProvider = cached.provider?.let { n -> LyricsProvider.entries.firstOrNull { it.name == n } }
                Result.success(cachedText)
            }
            else -> fetchLyricsWithSource(
                provider = force ?: provider,
                title = track.title,
                artist = track.artist,
                durationMs = durationMs.takeIf { it > 0 } ?: track.durationMs,
                album = track.album,
                videoId = track.videoId,
            ).onSuccess { fetched = it; shownProvider = it.provider }.map { it.text }
        }
        if (localLyrics != null) shownProvider = null
        loading = false
        result.onSuccess { raw ->
            parsed = withContext(Dispatchers.Default) { LyricsParser.parse(raw) }
            if (parsed.lines.isEmpty()) { error = "No lyrics found"; LyricsAvailability.noneFor = track.videoId }
            else fetched?.let { f ->
                withContext(Dispatchers.IO) { runCatching { cache.putLyrics(track.videoId, f.provider.name, f.text) } }
            }
        }.onFailure {
            error = it.message ?: "Lyrics lookup failed"
            parsed = ParsedLyrics.Empty
            LyricsAvailability.noneFor = track.videoId
        }
        if (error == null) LyricsAvailability.noneFor = null
    }

    // Romanization / translation under each line, filled in after the lyrics load (offline kana/Hangul first).
    val tOpts = remember(prefsTick) { prefs.lyricsTransformOptions() }
    var shownParsed by remember { mutableStateOf(parsed) }
    var transformProblem by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(parsed, tOpts) {
        shownParsed = parsed
        transformProblem = null
        if (!tOpts.enabled || parsed.lines.isEmpty()) return@LaunchedEffect
        val key = nowPlaying?.videoId ?: return@LaunchedEffect
        // Offline part first so Japanese/Korean/Cyrillic romanization shows immediately.
        if (tOpts.romanize) shownParsed = LyricsTransform.apply(key, parsed, tOpts.copy(translate = false, provider = GOOGLE_FREE)).lyrics
        val full = LyricsTransform.apply(key, parsed, tOpts)
        shownParsed = full.lyrics
        transformProblem = full.problem
    }
    val timeline = remember(shownParsed, cfg.estimateWords, cfg.showBackgroundVocals, cfg.gapStyle) {
        LyricsTimeline.build(
            shownParsed,
            estimateWords = cfg.estimateWords,
            minGapMs = if (cfg.gapStyle == LyricsGapStyle.NONE) Long.MAX_VALUE / 4 else LyricsTimeline.DEFAULT_MIN_GAP_MS,
            showBackground = cfg.showBackgroundVocals,
        )
    }
    val clock = rememberLyricsClock(controller.positionMs, playbackState == PlaybackState.Playing, cfg.syncOffsetMs - songDelayMs)
    val accent = lyricsAccent(cfg.accent)
    val bgStyle = remember(prefsTick) {
        runCatching { PlayerBackgroundStyle.valueOf(prefs.getString(PrefKeys.PlayerBackgroundStyle, com.music.vivi.compose.prefs.LookDefaults.PlayerStyle.name)) }
            .getOrDefault(PlayerBackgroundStyle.GRADIENT)
    }

    var align by remember(cfg.align) { mutableStateOf(cfg.align) }

    Box(modifier.fillMaxSize().then(if (autoHideControls) Modifier.hoverable(panelHover) else Modifier)) {
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
            else -> when {
                cfg.scene == LyricsScene.ROW && timeline.isSynced -> {
                    Lyrics3DScene(
                        timeline = timeline,
                        cfg = cfg.copy(align = align),
                        clock = clock,
                        accent = accent,
                    )
                }
                cfg.scene == LyricsScene.CUTS -> {
                    LyricsCutsScene(
                        timeline = timeline,
                        cfg = cfg.copy(align = align),
                        clock = clock,
                        accent = accent,
                        isPlaying = playbackState == PlaybackState.Playing,
                        palette = LocalArtworkPalette.current,
                    )
                }
                else -> LyricsList(
                    timeline = timeline,
                    cfg = cfg.copy(align = align),
                    clock = clock,
                    accent = accent,
                    onSeek = { controller.seekTo(it) },
                    // The delay pill sits at the bottom centre (16dp + ~48dp tall); Resume goes above it.
                    bottomInset = if (parsed.isSynced) 64.dp else 0.dp,
                    fitLongestLine = autoHideControls,
                )
            }
        }

        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 4.dp, end = 8.dp)
                .graphicsLayer { alpha = controlsAlpha },
            horizontalArrangement = Arrangement.spacedBy(if (autoHideControls) 2.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LyricsSizeControl(prefs = prefs, version = prefsTick)
            LyricsTransformControl(prefs = prefs, version = prefsTick, problem = transformProblem)
            LyricsProviderControl(
                current = shownProvider ?: provider,
                onSelect = {
                    prefs.setString(PrefKeys.LyricsProvider, it.name)
                    pendingForce[0] = it
                    forceTick++
                },
            )
            LyricsAlignControls(
                current = align,
                onSelect = { next ->
                    align = next
                    prefs.setString(PrefKeys.LyricsTextAlign, next.name)
                },
            )
            LyricsSceneControl(
                current = cfg.scene,
                dissolve = cfg.sceneDissolve,
                lingerSec = cfg.sceneLingerSec,
                bounceAmp = cfg.bounceAmp,
                cutsSlice = cfg.cutsSliceText,
                cutsSync = cfg.cutsSyncRings,
                cutsReaction = if (cfg.cutsSyncRings) prefs.getFloat(PrefKeys.RingsReaction, 1f) else cfg.cutsReaction,
                onChangeCutsSlice = { prefs.setBoolean(PrefKeys.LyricsCutsSlice, it) },
                onChangeCutsSync = { prefs.setBoolean(PrefKeys.LyricsCutsSync, it) },
                onChangeCutsReaction = { next ->
                    // Synced, the stepper edits the Rings background's own reactivity, as Settings does.
                    prefs.setFloat(if (cfg.cutsSyncRings) PrefKeys.RingsReaction else PrefKeys.LyricsCutsReaction, next)
                },
                onSelectScene = { next ->
                    prefs.setString(PrefKeys.LyricsScene, next.name)
                },
                onChangeDissolve = { next ->
                    prefs.setFloat(PrefKeys.LyricsSceneDissolve, next)
                },
                onChangeLinger = { next ->
                    prefs.setFloat(PrefKeys.LyricsSceneLinger, next)
                },
                onChangeBounceAmp = { next ->
                    prefs.setFloat(PrefKeys.LyricsBounceAmp, next)
                },
            )
        }

        if (!loading && error == null && timeline.lines.isNotEmpty() && parsed.isSynced) {
            LyricsDelayPill(
                delayMs = songDelayMs,
                onChange = { next ->
                    songDelayMs = next
                    val id = nowPlaying?.videoId
                    if (id != null) scope.launch(Dispatchers.IO) { runCatching { cache.setOffset(id, next) } }
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp).graphicsLayer { alpha = controlsAlpha },
            )
        }
    }
}

/** Floating +/- pill: nudges this song's lyrics delay in 100 ms steps; tap the value to reset. */
@Composable
private fun LyricsDelayPill(delayMs: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val alpha by androidx.compose.animation.core.animateFloatAsState(if (hovered) 1f else 0.55f, label = "delayPillAlpha")
    Surface(
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.34f),
        modifier = modifier.hoverable(hover).graphicsLayer { this.alpha = alpha },
    ) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            DelayStepButton(direction = -1, delayMs = delayMs, onChange = onChange) {
                Icon(Icons.Outlined.Remove, "Lyrics earlier", tint = Color.White)
            }
            Surface(
                onClick = { onChange(0) },
                shape = RoundedCornerShape(999.dp),
                color = Color.Transparent,
            ) {
                Text(
                    formatLyricsDelay(delayMs),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 56.dp).padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
            DelayStepButton(direction = 1, delayMs = delayMs, onChange = onChange) {
                Icon(Icons.Outlined.Add, "Lyrics later", tint = Color.White)
            }
        }
    }
}

/** Step button: a click nudges 100 ms; holding repeats, and speeds up to 500 ms steps after two seconds. */
@Composable
private fun DelayStepButton(direction: Int, delayMs: Int, onChange: (Int) -> Unit, content: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val current by rememberUpdatedState(delayMs)
    val change by rememberUpdatedState(onChange)
    var repeated by remember { mutableStateOf(false) }
    LaunchedEffect(pressed) {
        if (!pressed) return@LaunchedEffect
        repeated = false
        kotlinx.coroutines.delay(450)
        val heldSince = System.nanoTime()
        while (true) {
            repeated = true
            val steps = if ((System.nanoTime() - heldSince) / 1_000_000 > 2_000) 5 else 1
            change(stepLyricsDelay(current, direction * steps))
            kotlinx.coroutines.delay(90)
        }
    }
    IconButton(
        onClick = {
            // The click that ends a hold must not add one more step.
            if (repeated) repeated = false else change(stepLyricsDelay(current, direction))
        },
        interactionSource = source,
    ) { content() }
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

/** Text size for the lyrics, translation and romanization together; saved until changed again. */
@Composable
private fun LyricsSizeControl(prefs: DesktopPrefs, version: Int) {
    var open by remember { mutableStateOf(false) }
    val default = remember { LyricsConfig().fontSizeSp }
    val size = remember(version) { prefs.getFloat(PrefKeys.LyricsFontSize, default).coerceIn(16f, 56f) }
    fun set(v: Float) = prefs.setFloat(PrefKeys.LyricsFontSize, v.coerceIn(16f, 56f))
    Box {
        Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.28f)) {
            IconButton(onClick = { open = true }) {
                Icon(Icons.Outlined.FormatSize, "Lyrics text size", tint = if (size != default) MaterialTheme.colorScheme.primary else Color.White)
            }
        }
        if (open) {
            Popup(alignment = Alignment.TopEnd, offset = IntOffset(0, 52), onDismissRequest = { open = false }, properties = PopupProperties(focusable = true)) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 8.dp, shadowElevation = 10.dp,
                ) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { set(size - 2f) }, enabled = size > 16f) { Icon(Icons.Outlined.Remove, "Smaller") }
                        Text("${size.toInt()} sp", style = MaterialTheme.typography.titleSmall, modifier = Modifier.widthIn(min = 52.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        IconButton(onClick = { set(size + 2f) }, enabled = size < 56f) { Icon(Icons.Outlined.Add, "Larger") }
                        IconButton(onClick = { set(default) }, enabled = size != default) { Icon(Icons.Outlined.Restore, "Reset size") }
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsTransformControl(prefs: DesktopPrefs, version: Int, problem: String?) {
    var open by remember { mutableStateOf(false) }
    val o = remember(version) { prefs.lyricsTransformOptions() }
    Box {
        Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.28f)) {
            IconButton(onClick = { open = true }) {
                Icon(
                    Icons.Outlined.Translate, "Romanization and translation",
                    tint = if (o.enabled) MaterialTheme.colorScheme.primary else Color.White,
                )
            }
        }
        if (open) {
            Popup(alignment = Alignment.TopEnd, offset = IntOffset(0, 52), onDismissRequest = { open = false }, properties = PopupProperties(focusable = true)) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 8.dp, shadowElevation = 10.dp,
                    modifier = Modifier.widthIn(min = 240.dp, max = 300.dp),
                ) {
                    androidx.compose.foundation.layout.Column(Modifier.padding(vertical = 8.dp, horizontal = 4.dp)) {
                        fun flip(key: String, on: Boolean) {
                            prefs.setBoolean(key, on)
                            if (on) prefs.setBoolean(PrefKeys.LyricsShowTranslation, true)
                        }
                        TransformRow("Romanize (romaji, romaja…)", o.romanize) { flip(PrefKeys.LyricsRomanize, it) }
                        TransformRow("Translate to ${TranslateLanguages.firstOrNull { it.first == o.targetLang }?.second ?: o.targetLang}", o.translate) { flip(PrefKeys.LyricsAutoTranslate, it) }
                        if (problem != null) {
                            Text(problem, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                        }
                        Text(
                            "Language, provider and Gemini key: Settings → Lyrics",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransformRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onChange)
    }
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
private fun LyricsSceneControl(
    current: LyricsScene,
    dissolve: Float,
    lingerSec: Float,
    bounceAmp: Float,
    cutsSlice: Boolean,
    cutsSync: Boolean,
    cutsReaction: Float,
    onChangeCutsSlice: (Boolean) -> Unit,
    onChangeCutsSync: (Boolean) -> Unit,
    onChangeCutsReaction: (Float) -> Unit,
    onSelectScene: (LyricsScene) -> Unit,
    onChangeLinger: (Float) -> Unit,
    onChangeDissolve: (Float) -> Unit,
    onChangeBounceAmp: (Float) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    IdleCollapseCluster(
        collapsedContent = { expand ->
            IconButton(onClick = expand) {
                Surface(
                    shape = CircleShape,
                    color = if (current != LyricsScene.NONE) Color.White.copy(alpha = 0.22f) else Color.Transparent,
                ) {
                    Box(Modifier.padding(6.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.AutoAwesome, "3D Scene Mode", tint = Color.White)
                    }
                }
            }
        },
        expandedContent = { collapse ->
            Box {
                Row(
                    Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Surface(
                        onClick = { menuOpen = true },
                        shape = RoundedCornerShape(999.dp),
                        // No fill: the cluster pill around it already is the highlight.
                        color = Color.Transparent,
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Outlined.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Text(
                                current.label,
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                if (menuOpen) {
                    Popup(
                        alignment = Alignment.TopEnd,
                        offset = androidx.compose.ui.unit.IntOffset(0, 48),
                        onDismissRequest = {
                            menuOpen = false
                            collapse()
                        },
                        properties = PopupProperties(focusable = true),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = 8.dp,
                            shadowElevation = 12.dp,
                            modifier = Modifier.width(340.dp),
                        ) {
                            androidx.compose.foundation.layout.Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Text(
                                    "Lyric style",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                // One tile per style, with its icon; the chosen one is filled.
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    LyricsScene.entries.forEach { scene ->
                                        val selected = scene == current
                                        Surface(
                                            onClick = {
                                                onSelectScene(scene)
                                                if (scene == LyricsScene.NONE) {
                                                    menuOpen = false
                                                    collapse()
                                                }
                                            },
                                            shape = RoundedCornerShape(16.dp),
                                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                            contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            androidx.compose.foundation.layout.Column(
                                                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                Icon(sceneIcon(scene), null, modifier = Modifier.size(24.dp))
                                                Text(
                                                    scene.label,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                )
                                            }
                                        }
                                    }
                                }

                                if (current != LyricsScene.NONE) {
                                    androidx.compose.material3.HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    )
                                    if (current == LyricsScene.ROW) {
                                        SceneStepper(
                                            icon = Icons.Outlined.BlurOn, label = "Fade distance",
                                            value = "${(dissolve * 100).toInt()}%",
                                            onMinus = { onChangeDissolve((dissolve - 0.05f).coerceIn(0.55f, 0.95f)) },
                                            onPlus = { onChangeDissolve((dissolve + 0.05f).coerceIn(0.55f, 0.95f)) },
                                        )
                                        SceneStepper(
                                            icon = Icons.Outlined.HourglassEmpty, label = "Linger time",
                                            value = "${((lingerSec * 10).toInt()) / 10f} s",
                                            onMinus = { onChangeLinger((lingerSec - 0.5f).coerceIn(0.5f, 8f)) },
                                            onPlus = { onChangeLinger((lingerSec + 0.5f).coerceIn(0.5f, 8f)) },
                                        )
                                    }
                                    SceneStepper(
                                        icon = Icons.Outlined.Vibration, label = "Inertial bounce",
                                        value = if (bounceAmp <= 0f) "Off" else "${(bounceAmp * 100).toInt()}%",
                                        onMinus = { onChangeBounceAmp((bounceAmp - 0.20f).coerceIn(0f, 2.5f)) },
                                        onPlus = { onChangeBounceAmp((bounceAmp + 0.20f).coerceIn(0f, 2.5f)) },
                                    )
                                    if (current == LyricsScene.CUTS) {
                                        SceneSwitch(
                                            icon = Icons.Outlined.ContentCut, label = "Rings cut the lyrics",
                                            checked = cutsSlice, onChange = onChangeCutsSlice,
                                        )
                                        SceneSwitch(
                                            icon = Icons.Outlined.Sync, label = "Sync with rings background",
                                            checked = cutsSync, onChange = onChangeCutsSync,
                                        )
                                        SceneStepper(
                                            icon = Icons.Outlined.GraphicEq, label = "Reactivity",
                                            value = if (cutsReaction <= 0f) "Off" else "${(cutsReaction * 100).toInt()}%",
                                            onMinus = { onChangeCutsReaction((cutsReaction - 0.10f).coerceIn(0f, 2f)) },
                                            onPlus = { onChangeCutsReaction((cutsReaction + 0.10f).coerceIn(0f, 2f)) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}


private fun sceneIcon(scene: LyricsScene): ImageVector = when (scene) {
    LyricsScene.NONE -> Icons.AutoMirrored.Outlined.FormatListBulleted
    LyricsScene.ROW -> Icons.Outlined.ViewInAr
    LyricsScene.CUTS -> Icons.Outlined.ContentCut
}

/** One on/off option: icon and label on the left, a switch on the right. */
@Composable
private fun SceneSwitch(icon: ImageVector, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** One tunable value: icon and label on the left, a pill with − value + on the right. */
@Composable
private fun SceneStepper(icon: ImageVector, label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMinus, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Remove, "Less", modifier = Modifier.size(16.dp))
                }
                Text(
                    value,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.widthIn(min = 44.dp),
                )
                IconButton(onClick = onPlus, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Add, "More", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
