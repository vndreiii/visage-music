package com.music.vivi.compose.ui.glass

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.PrefKeys
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import org.koin.compose.koinInject

/**
 * "Material 3 Expressive Blur": surfaces that sit over other content become translucent, frosted glass.
 * The content behind them is marked with [glassSource]; a surface adds [GlassBackdrop] (or [glassLayer]) to blur it.
 * Only surfaces use it, never buttons or chips.
 */
val LocalHazeState = compositionLocalOf<HazeState?> { null }

/** Height of the top bar; with the blur setting on it floats over the page, so full-bleed pages inset their content by it. */
val TopBarInset = 64.dp

/** How far the current page has scrolled down (px), so the background can drift with it. Reset on navigation. */
class BackdropScroll {
    var y by androidx.compose.runtime.mutableFloatStateOf(0f)
}
val LocalBackdropScroll = compositionLocalOf<BackdropScroll?> { null }

/** Inset the current page applies at its top because the top bar floats over it (0 when the bar is not floating). */
val LocalTopBarInset = compositionLocalOf { 0.dp }

/** Whether the blur setting is on (re-read when settings change). */
@Composable
fun rememberGlassEnabled(): Boolean {
    val prefs = koinInject<DesktopPrefs>()
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(prefs) {
        val listener = { tick += 1 }
        prefs.addListener(listener)
        onDispose { prefs.removeListener(listener) }
    }
    @Suppress("UNUSED_EXPRESSION") tick
    return prefs.getBoolean(PrefKeys.GlassBlur, false)
}

/** Marks this content as what glass surfaces blur. [state] must be the one provided via [LocalHazeState]. */
fun Modifier.glassSource(state: HazeState): Modifier = haze(state)

/**
 * Frosted layer to put behind a surface's content (fills its parent). Draws nothing when the setting is off or
 * no [LocalHazeState] is provided, so callers keep their normal opaque look in that case.
 */
@Composable
fun GlassBackdrop(tint: Color, modifier: Modifier = Modifier, shape: Shape = RectangleShape, edges: GlassEdges = GlassEdges.All) {
    val state = LocalHazeState.current
    if (state == null || !rememberGlassEnabled()) return
    Box(
        modifier
            .clip(shape)
            .hazeChild(state) {
                blurRadius = 28.dp
                tints = listOf(HazeTint(tint))
                noiseFactor = 0.03f
            }
            .then(
                when (edges) {
                    GlassEdges.All -> Modifier.border(1.dp, Color.White.copy(alpha = 0.12f), shape)
                    // A full-width bar only needs a divider on the side that meets the page.
                    GlassEdges.Bottom -> Modifier.drawBehind { drawLine(Color.White.copy(alpha = 0.12f), Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1.dp.toPx()) }
                    GlassEdges.Top -> Modifier.drawBehind { drawLine(Color.White.copy(alpha = 0.12f), Offset(0f, 0.5f), Offset(size.width, 0.5f), 1.dp.toPx()) }
                    GlassEdges.None -> Modifier
                },
            ),
    )
}

/** Which edges of a glass surface get the thin light border. */
enum class GlassEdges { All, Top, Bottom, None }

/** True when a glass surface can actually blur something right now. */
@Composable
fun glassActive(): Boolean = LocalHazeState.current != null && rememberGlassEnabled()

@Composable
fun ProvideGlass(
    state: HazeState,
    topInset: androidx.compose.ui.unit.Dp = 0.dp,
    scroll: BackdropScroll? = null,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalHazeState provides state,
        LocalTopBarInset provides topInset,
        LocalBackdropScroll provides scroll,
        content = content,
    )
}
