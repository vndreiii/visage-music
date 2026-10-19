package com.music.vivi.compose.ui.library

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.vivi.compose.ui.component.GeneratedCover
import com.music.vivi.compose.ui.component.LabelTile
import com.music.vivi.compose.ui.component.coverPalette
import com.music.vivi.compose.ui.expressive.ExpressiveCard
import com.music.vivi.compose.ui.motion.VisageMotion
import com.music.vivi.database.Local_track
import java.io.File

/** Up to [perKey] distinct, already-extracted cover paths per key (lower-cased), from cached tracks. */
internal fun coversBy(tracks: List<Local_track>, perKey: Int = 3, key: (Local_track) -> String?): Map<String, List<String>> {
    val out = HashMap<String, LinkedHashSet<String>>()
    for (t in tracks) {
        val g = key(t)?.lowercase()?.takeIf { it.isNotBlank() } ?: continue
        val c = t.cover_path ?: continue
        if (c.startsWith("opus-lazy://")) continue
        val set = out.getOrPut(g) { LinkedHashSet() }
        if (set.size < perKey) set.add(c)
    }
    return out.mapValues { it.value.toList() }
}

internal fun genreCovers(tracks: List<Local_track>) = coversBy(tracks) { it.genre }
internal fun yearCovers(tracks: List<Local_track>) = coversBy(tracks) { it.year?.toString() }
internal fun composerCovers(tracks: List<Local_track>) = coversBy(tracks) { it.composer }

/** A cover that fades in only once decoded, so it never pops. */
@Composable
private fun FadeCover(path: String, modifier: Modifier, delayMs: Int, blurDp: Int = 0) {
    var loaded by remember(path) { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        if (loaded) 1f else 0f,
        androidx.compose.animation.core.tween(700, delayMillis = delayMs),
        label = "coverFade",
    )
    AsyncImage(
        model = File(path),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onSuccess = { loaded = true },
        modifier = modifier.graphicsLayer { this.alpha = alpha }.let { if (blurDp > 0) it.blur(blurDp.dp) else it },
    )
}

/**
 * Large genre/year/composer tile: flat tonal text zone on the left (name + track count), art block on the right
 * (local cover collage, or the generated flat cover). Text never sits on the artwork. Hover lift.
 */
@Composable
internal fun CollectionCard(
    name: String,
    trackCount: Long,
    covers: List<String>,
    seed: String = name,
    onClick: () -> Unit,
) {
    val accent = remember(name) { tintFor(name, 0.7f) }
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val lift by animateFloatAsState(if (hovered) 1.045f else 1f, VisageMotion.spatialDefault(), label = "genreLift")
    val zoom by animateFloatAsState(if (hovered) 1.08f else 1f, VisageMotion.spatialSlow(), label = "genreZoom")
    val pal = remember(accent) { coverPalette(accent) }

    Box(Modifier.hoverable(hover).graphicsLayer { scaleX = lift; scaleY = lift }) {
        ExpressiveCard(
            onClick = onClick,
            restRadius = 28.dp,
            pressedRadius = 14.dp,
            color = Color.Transparent,
            modifier = Modifier.width(236.dp).height(136.dp),
        ) {
            LabelTile(
                accent = accent,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(0.dp),
                text = { on ->
                    Text(
                        name,
                        style = MaterialTheme.typography.titleLarge.copy(letterSpacing = (-0.3).sp),
                        fontWeight = FontWeight.ExtraBold,
                        color = on,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Box(Modifier.clip(RoundedCornerShape(50)).background(pal[1]).padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Text(
                            "$trackCount tracks", style = MaterialTheme.typography.labelMedium,
                            color = com.music.vivi.compose.theme.ensureContrastingOn(pal[1], Color.White, 4.5f),
                            fontWeight = FontWeight.SemiBold, maxLines = 1,
                        )
                    }
                },
                art = {
                    if (covers.isEmpty()) {
                        GeneratedCover(seed, Modifier.fillMaxSize(), accent = accent)
                    } else {
                        Row(Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom }) {
                            covers.forEachIndexed { i, p ->
                                Box(Modifier.weight(1f).fillMaxHeight()) { FadeCover(p, Modifier.fillMaxSize(), delayMs = i * 140) }
                            }
                        }
                    }
                },
            )
        }
    }
}
