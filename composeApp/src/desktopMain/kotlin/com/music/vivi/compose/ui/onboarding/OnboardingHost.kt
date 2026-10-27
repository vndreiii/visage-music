package com.music.vivi.compose.ui.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import com.music.vivi.compose.ui.motion.VisageMotion
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import com.music.vivi.compose.dev.DevUiState
import com.music.vivi.compose.navigation.Routes
import com.music.vivi.compose.prefs.LibrarySidebarState
import com.music.vivi.compose.prefs.SidebarConfig
import com.music.vivi.compose.prefs.reader
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.PrefKeys
import com.music.vivi.compose.ui.YtLoginPanel
import com.music.vivi.core.session.SessionManager
import org.koin.compose.koinInject
import kotlin.math.roundToInt
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.drawBehind
import com.music.vivi.compose.ui.expressive.ExpressiveButton
import com.music.vivi.compose.ui.expressive.ExpressiveSize

/** Window-space rectangles of the UI elements the tour points at, keyed by [TourStep.anchor]. */
object TourAnchors {
    val rects = androidx.compose.runtime.mutableStateMapOf<String, Rect>()
}

/** Registers the modifier's bounds as a tour spotlight target. */
fun Modifier.tourAnchor(key: String): Modifier = onGloballyPositioned { coords ->
    val bounds = coords.boundsInWindow()
    if (TourAnchors.rects[key] != bounds) TourAnchors.rects[key] = bounds
}

private sealed interface Phase {
    data object Login : Phase
    data class Tour(val step: Int) : Phase
}

/**
 * First-run onboarding: sign-in step, then a short tour. Mounted once at the app root. Shows on a fresh
 * install (onboarding.done = "false"), from Settings → Replay tour, or forced with VISAGE_UI_STATE
 * (onboarding / onboarding-tour). Forced runs never write the done flag.
 */
@Composable
fun OnboardingHost() {
    val prefs = koinInject<DesktopPrefs>()
    val sessionManager = koinInject<SessionManager>()
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(prefs) {
        val listener = { tick += 1 }
        prefs.addListener(listener)
        onDispose { prefs.removeListener(listener) }
    }
    var phase by remember { mutableStateOf<Phase?>(null) }
    val prevLibrary = remember { SidebarConfig.resolve(prefs.reader()).libraryState }
    val forced = DevUiState.has("onboarding") || DevUiState.has("onboarding-tour")

    LaunchedEffect(tick) {
        if (phase != null) return@LaunchedEffect
        phase = when {
            DevUiState.has("onboarding-tour") -> Phase.Tour(0)
            DevUiState.has("onboarding") -> Phase.Login
            prefs.getBoolean(PrefKeys.OnboardingReplay, false) -> {
                prefs.setBoolean(PrefKeys.OnboardingReplay, false)
                Phase.Tour(0)
            }
            prefs.getString(PrefKeys.OnboardingDone, "") == "false" ->
                if (sessionManager.isLoggedIn()) Phase.Tour(0) else Phase.Login
            else -> null
        }
    }

    fun finish() {
        phase = null
        // Put the app back the way the tour found it.
        TourBus.send(TourCommand.Queue(false))
        TourBus.send(TourCommand.Library(prevLibrary))
        TourBus.send(TourCommand.Navigate(Routes.HOME))
        // Forced runs leave prefs untouched; a real first run is marked done so it never reappears.
        if (!forced) prefs.setString(PrefKeys.OnboardingDone, "true")
    }

    when (val p = phase) {
        null -> Unit
        Phase.Login -> LoginStep(
            onSignedIn = { phase = Phase.Tour(0) },
            onSkip = { phase = Phase.Tour(0) },
        )
        is Phase.Tour -> TourStep(
            index = p.step,
            onBack = { if (p.step > 0) phase = Phase.Tour(p.step - 1) },
            onNext = { if (p.step < TourCatalog.steps.lastIndex) phase = Phase.Tour(p.step + 1) else finish() },
            onSkip = { finish() },
        )
    }
}

@Composable
private fun LoginStep(onSignedIn: () -> Unit, onSkip: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = scheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(28.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Welcome to VIsage", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Sign in with YouTube Music to get your mixes, playlists and history. Your session stays on this device.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant,
                )
                YtLoginPanel(onLoggedIn = { onSignedIn() })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onSkip) { Text("Skip for now") }
                }
            }
        }
    }
}

@Composable
private fun TourStep(index: Int, onBack: () -> Unit, onNext: () -> Unit, onSkip: () -> Unit) {
    val step = TourCatalog.steps[index.coerceIn(0, TourCatalog.steps.lastIndex)]
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val target = step.anchor?.let { TourAnchors.rects[it] }?.translate(-origin.x, -origin.y)
    val cardWidthPx = with(density) { 360.dp.toPx() }
    val cardHeightPx = with(density) { 260.dp.toPx() }
    val gapPx = with(density) { 20.dp.toPx() }
    val padPx = with(density) { 8.dp.toPx() }

    // The real app moves first (navigation, sidebars opening), then the spotlight follows.
    LaunchedEffect(index) {
        step.enter.forEach { TourBus.send(it) }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    // Spotlight hole glides between targets; with no target it shrinks away and the card centres.
    val hole = target ?: Rect(size.width / 2f, size.height / 2f, size.width / 2f, size.height / 2f)
    val spec = VisageMotion.spatialDefault<Float>()
    val hl by animateFloatAsState(hole.left, spec, label = "holeL")
    val ht by animateFloatAsState(hole.top, spec, label = "holeT")
    val hr by animateFloatAsState(hole.right, spec, label = "holeR")
    val hb by animateFloatAsState(hole.bottom, spec, label = "holeB")
    val holeAlpha by animateFloatAsState(if (target != null) 1f else 0f, VisageMotion.fadeInSpec(), label = "holeA")

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                origin = coords.positionInWindow()
                size = coords.size
            }
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawBehind {
                drawRect(Color.Black.copy(alpha = 0.62f))
                if (holeAlpha > 0.01f) {
                    drawRoundRect(
                        color = Color.Black.copy(alpha = holeAlpha),
                        topLeft = Offset(hl - padPx, ht - padPx),
                        size = Size((hr - hl) + 2 * padPx, (hb - ht) + 2 * padPx),
                        cornerRadius = CornerRadius(18.dp.toPx()),
                        blendMode = BlendMode.Clear,
                    )
                }
            }
            // Swallow every click: while the tour runs the app underneath is driven by the tour only.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            },
    ) {
        val tall = target != null && target.height > size.height * 0.5f
        val cardX: Float
        val cardY: Float
        when {
            target == null -> {
                cardX = (size.width - cardWidthPx) / 2f
                cardY = ((size.height - cardHeightPx) / 2f).coerceAtLeast(16f)
            }
            tall -> {
                // A sidebar: put the card beside it, on the side with room.
                val roomRight = size.width - target.right
                cardX = if (roomRight >= cardWidthPx + gapPx + padPx) target.right + gapPx + padPx
                else (target.left - cardWidthPx - gapPx - padPx).coerceAtLeast(16f)
                cardY = ((size.height - cardHeightPx) / 2f).coerceAtLeast(16f)
            }
            else -> {
                cardX = (target.center.x - cardWidthPx / 2f).coerceIn(16f, (size.width - cardWidthPx - 16f).coerceAtLeast(16f))
                cardY = if (target.bottom + gapPx + cardHeightPx < size.height) target.bottom + gapPx + padPx
                else (target.top - gapPx - padPx - cardHeightPx).coerceAtLeast(16f)
            }
        }
        val x by animateFloatAsState(cardX, spec, label = "cardX")
        val y by animateFloatAsState(cardY, spec, label = "cardY")
        Surface(
            modifier = Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .widthIn(max = 360.dp)
                .focusRequester(focus)
                .focusable()
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.DirectionRight, Key.Enter -> { onNext(); true }
                        Key.DirectionLeft -> { onBack(); true }
                        Key.Escape -> { onSkip(); true }
                        else -> false
                    }
                },
            shape = RoundedCornerShape(28.dp),
            color = scheme.surfaceContainerHigh,
            shadowElevation = 12.dp,
        ) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(16.dp), color = scheme.primaryContainer, modifier = Modifier.size(44.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(step.icon, contentDescription = null, tint = scheme.onPrimaryContainer)
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "STEP ${index + 1} OF ${TourCatalog.steps.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(step.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(step.body, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                Spacer(Modifier.height(18.dp))
                LinearProgressIndicator(
                    progress = { (index + 1f) / TourCatalog.steps.size },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)),
                )
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onSkip) { Text("Skip tour") }
                    Spacer(Modifier.weight(1f))
                    if (index > 0) TextButton(onClick = onBack) { Text("Back") }
                    Spacer(Modifier.width(4.dp))
                    ExpressiveButton(onClick = onNext, size = ExpressiveSize.Small) {
                        Text(if (index == TourCatalog.steps.lastIndex) "Finish" else "Next")
                    }
                }
            }
        }
    }
}
