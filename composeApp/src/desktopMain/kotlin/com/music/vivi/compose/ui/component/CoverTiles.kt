package com.music.vivi.compose.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import com.music.vivi.compose.theme.ensureContrastingOn

/** Flat tonal text-zone colour for [accent], theme-aware (dark container in dark theme, pale in light). */
@Composable
internal fun tileZoneColor(accent: Color): Color {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return remember(accent, dark) {
        val p = coverPalette(accent)
        if (dark) p[0] else p[3]
    }
}

/** Thin solid outline colour for tiles. */
internal fun tileOutline(accent: Color): Color = coverPalette(accent)[1]

/**
 * Variant (b): wide label tile. Flat tonal text zone on the left, art block on the right, separated by clean
 * geometry (the art sits in its own rounded block), thin solid border. Text never overlaps the art.
 */
@Composable
fun LabelTile(
    accent: Color,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    artWeight: Float = 0.42f,
    text: @Composable ColumnScope.(textColor: Color) -> Unit,
    art: @Composable BoxScope.() -> Unit,
) {
    val zone = tileZoneColor(accent)
    val onZone = ensureContrastingOn(zone, MaterialTheme.colorScheme.onSurface, 7f)
    Row(modifier.clip(shape).background(zone)) {
        Column(
            Modifier.weight(1f - artWeight).fillMaxHeight().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        ) { text(onZone) }
        Box(Modifier.weight(artWeight).fillMaxHeight().padding(8.dp).clip(RoundedCornerShape(18.dp)), content = art)
    }
}

/**
 * Variant (c): Spotify "Browse all" style card. Flat [color], bold title top-left, art block rotated slightly
 * and tucked (clipped) into the bottom-right corner. Title is kept clear of the art block.
 */
@Composable
fun BrowseTile(
    title: String,
    color: Color,
    seed: String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    artSize: Dp = 68.dp,
) {
    val onColor = ensureContrastingOn(color, Color.White, 4.5f)
    Box(modifier.clip(shape).background(color)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = onColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 12.dp, end = artSize + 10.dp),
        )
        Box(
            Modifier.align(Alignment.BottomEnd).offset(x = artSize * 0.2f, y = artSize * 0.22f)
                .size(artSize).rotate(18f).clip(RoundedCornerShape(10.dp)),
        ) {
            GeneratedCover(seed, Modifier.fillMaxSize(), accent = color)
        }
    }
}
