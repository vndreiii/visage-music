package com.music.vivi.compose.ui.responsive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.floor

/**
 * The one breakpoint system for the whole app. See docs/responsive-layout.md.
 *
 * Classes come from the WINDOW size in dp (px / density), not from the pane a screen happens to get:
 * a screen that sits next to a sidebar should still ask [LocalWindowLayout] for the window class and
 * use its own `BoxWithConstraints` / [gridColumnsFor] for the pixels it actually has.
 */
enum class LayoutClass {
    /** Phones, < 600dp wide (390x844, 360x640 ...). One column, trays instead of sidebars. */
    Mobile,

    /** Small tablets / narrow windows, 600..900dp (800x1280 portrait). Still single column. */
    Tablet,

    /** Laptop and 1080p, 900..2200dp wide (1024x768, 1280x800, 1366x768, 1920x1080). Sidebars dock. */
    Desktop,

    /** 2K and bigger 16:9 screens, >= 2200dp (2560x1440). Content is capped and centred. */
    Large,

    /** 21:9 and wider, aspect >= 2.1 and >= 1800dp wide (3440x1440, 2560x1080). Extra columns, capped content. */
    UltraWide,
}

/** Breakpoints, in dp. Targets: 390x844 Mobile, 800x1280 Tablet, 1024x768/1280x800/1366x768/1920x1080 Desktop,
 *  2560x1440 Large, 3440x1440 UltraWide. */
object Breakpoints {
    val Tablet = 600.dp
    val Desktop = 900.dp
    val Large = 2200.dp
    val UltraWideMinWidth = 1800.dp
    const val UltraWideAspect = 2.1f

    /** Below this height the window is "short" (a landscape phone, a 360x560 sliver): drop optional chrome. */
    val Short = 520.dp
}

/** Pure classifier (also used by the unit tests). */
fun layoutClassOf(width: Dp, height: Dp): LayoutClass {
    val aspect = if (height.value > 0f) width.value / height.value else 1f
    return when {
        width < Breakpoints.Tablet -> LayoutClass.Mobile
        width < Breakpoints.Desktop -> LayoutClass.Tablet
        aspect >= Breakpoints.UltraWideAspect && width >= Breakpoints.UltraWideMinWidth -> LayoutClass.UltraWide
        width >= Breakpoints.Large -> LayoutClass.Large
        else -> LayoutClass.Desktop
    }
}

@Immutable
data class WindowLayout(val width: Dp, val height: Dp, val cls: LayoutClass) {
    val aspect: Float get() = if (height.value > 0f) width.value / height.value else 1f
    val isPortrait: Boolean get() = height > width
    val isShort: Boolean get() = height < Breakpoints.Short

    val isMobile: Boolean get() = cls == LayoutClass.Mobile

    /** Mobile or Tablet: one column, sidebars become overlays/trays, tabs collapse into a menu. */
    val isCompactWidth: Boolean get() = cls == LayoutClass.Mobile || cls == LayoutClass.Tablet

    /** Desktop and up: sidebars can dock next to the content. */
    val isDesktopUp: Boolean get() = !isCompactWidth

    /** Large or UltraWide: more room than any content needs, so cap and centre it. */
    val isExpanded: Boolean get() = cls == LayoutClass.Large || cls == LayoutClass.UltraWide
    val isUltraWide: Boolean get() = cls == LayoutClass.UltraWide

    /** Widest a page's main column should grow ([Dp.Infinity] on compact widths). Centre it with [ContentWidth]. */
    val contentMaxWidth: Dp
        get() = when (cls) {
            LayoutClass.Mobile, LayoutClass.Tablet -> Dp.Infinity
            LayoutClass.Desktop -> 1600.dp
            LayoutClass.Large -> 1900.dp
            LayoutClass.UltraWide -> 2200.dp
        }

    /** Side padding for page content. */
    val pagePadding: Dp
        get() = when (cls) {
            LayoutClass.Mobile -> 12.dp
            LayoutClass.Tablet -> 20.dp
            else -> 28.dp
        }

    /** How many `minCellWidth` columns fit into [available] (default: the window) with [gap] between them; >= 1. */
    fun gridColumns(minCellWidth: Dp, available: Dp = width, gap: Dp = 12.dp): Int =
        gridColumnsFor(available, minCellWidth, gap)
}

/** Pure column math: floor((available + gap) / (minCell + gap)), at least 1. */
fun gridColumnsFor(available: Dp, minCellWidth: Dp, gap: Dp = 12.dp): Int {
    if (!available.value.isFinite() || minCellWidth.value <= 0f) return 1
    return floor((available.value + gap.value) / (minCellWidth.value + gap.value)).toInt().coerceAtLeast(1)
}

fun windowLayoutOf(width: Dp, height: Dp) = WindowLayout(width, height, layoutClassOf(width, height))

/** Defaults to Desktop so previews and tests that do not provide a layout behave like before. */
val LocalWindowLayout = compositionLocalOf { windowLayoutOf(1280.dp, 800.dp) }

/** Provide an explicit layout (tests, previews). */
@Composable
fun ProvideWindowLayout(width: Dp, height: Dp, content: @Composable () -> Unit) {
    val layout = remember(width, height) { windowLayoutOf(width, height) }
    CompositionLocalProvider(LocalWindowLayout provides layout, content = content)
}

/** Provide the layout of the window this composition is in (from its container size). Call once, near the root. */
@Composable
fun ProvideWindowLayoutFromWindow(
    /** Used until the window reports its size (first frame), e.g. `windowState.size`. */
    fallback: androidx.compose.ui.unit.DpSize? = null,
    content: @Composable () -> Unit,
) {
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val layout = remember(size, density.density, fallback) {
        if (size.width <= 0 || size.height <= 0) {
            if (fallback != null && fallback.width.value > 0f && fallback.height.value > 0f) windowLayoutOf(fallback.width, fallback.height)
            else windowLayoutOf(1280.dp, 800.dp)
        }
        else windowLayoutOf((size.width / density.density).dp, (size.height / density.density).dp)
    }
    CompositionLocalProvider(LocalWindowLayout provides layout, content = content)
}

/** Shorthand: `val layout = windowLayout()`. */
@Composable
fun windowLayout(): WindowLayout = LocalWindowLayout.current

/** Centres [content] and caps it at the layout's [WindowLayout.contentMaxWidth] (no-op on compact widths). */
@Composable
fun ContentWidth(
    modifier: Modifier = Modifier,
    maxWidth: Dp = LocalWindowLayout.current.contentMaxWidth,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(if (maxWidth.value.isFinite()) Modifier.widthIn(max = maxWidth) else Modifier.fillMaxWidth()) { content() }
    }
}
