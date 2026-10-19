package com.music.vivi.compose.ui.artist

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.music.vivi.compose.ui.motion.VisageMotion

/** Lookup state for an artist: [info] once known, [loading] until the helper answered. */
class ArtistLookup(val info: ArtistInfo?, val loading: Boolean)

/**
 * Looks the artist up through [ArtistInfoRepository] off the UI thread. [enabled] = false skips the
 * lookup (the caller already has an image). Starts from the in-memory cache so revisits never flash.
 */
@Composable
fun rememberArtistLookup(name: String, enabled: Boolean = true): State<ArtistLookup> {
    val (known, cached) = remember(name) { ArtistInfoRepository.peek(name) }
    val state = remember(name) { mutableStateOf(ArtistLookup(cached, loading = enabled && !known)) }
    LaunchedEffect(name, enabled) {
        if (!enabled || known) return@LaunchedEffect
        val info = ArtistInfoRepository.artistInfo(name)
        state.value = ArtistLookup(info, loading = false)
    }
    return state
}

/**
 * Generated stand-in for an artist without a portrait: a name-hashed gradient with initials.
 * Kept behind this one function so it can be swapped for the shared GeneratedCover component.
 */
@Composable
fun ArtistFallback(name: String, modifier: Modifier = Modifier, fontSize: Float = 0f) {
    com.music.vivi.compose.ui.component.GeneratedCover(seed = name, modifier = modifier, label = name)
}

private fun Color.luminanceIsLow() = (0.299f * red + 0.587f * green + 0.114f * blue) < 0.5f

/** Soft light sweep that tells the user an image is on its way. */
@Composable
fun Modifier.shimmerOverlay(active: Boolean): Modifier {
    val alpha by animateFloatAsState(if (active) 1f else 0f, VisageMotion.fade(400), label = "shimmerAlpha")
    if (alpha <= 0.01f) return this
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(VisageMotion.linear(1400), RepeatMode.Restart),
        label = "shimmerX",
    )
    return this.drawWithContent {
        drawContent()
        val w = size.width
        val start = x * w
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.16f * alpha), Color.Transparent),
                start = Offset(start, 0f),
                end = Offset(start + w * 0.5f, size.height * 0.4f),
            ),
        )
    }
}

/**
 * Artist image that never pops: the generated fallback is always underneath (with a shimmer while the
 * lookup runs) and the real image fades in over it once it has decoded. [imageUrl] short-circuits the
 * helper lookup when the caller already has a picture (YouTube artists); [banner] picks the wide image.
 */
@Composable
fun ArtistImage(
    name: String,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    banner: Boolean = false,
    fallbackFontSize: Float = 0f,
    onResolved: (ArtistInfo?) -> Unit = {},
) {
    val lookup by rememberArtistLookup(name, enabled = imageUrl == null)
    val info = lookup.info
    LaunchedEffect(info) { onResolved(info) }
    val url = imageUrl ?: if (banner) info?.bannerUrl ?: info?.imageUrl else info?.imageUrl
    var decoded by remember(url) { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (decoded) 1f else 0f, VisageMotion.fade(650), label = "artistImageFade")
    Box(modifier.shimmerOverlay(active = lookup.loading || (url != null && !decoded))) {
        ArtistFallback(name, Modifier.fillMaxSize(), fallbackFontSize)
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                onState = { if (it is AsyncImagePainter.State.Success) decoded = true },
                modifier = Modifier.fillMaxSize().alpha(alpha),
            )
        }
    }
}

/** Image that fades in over whatever is behind it once decoded (optionally blurred and over-scaled). */
@Composable
fun FadingImage(url: String?, modifier: Modifier = Modifier, blurDp: Int = 0, description: String? = null) {
    if (url == null) return
    var decoded by remember(url) { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (decoded) 1f else 0f, VisageMotion.fade(700), label = "fadingImage")
    AsyncImage(
        model = url,
        contentDescription = description,
        contentScale = ContentScale.Crop,
        onState = { if (it is AsyncImagePainter.State.Success) decoded = true },
        modifier = modifier
            .alpha(alpha)
            .then(if (blurDp > 0) Modifier.graphicsLayer { scaleX = 1.25f; scaleY = 1.25f }.blur(blurDp.dp) else Modifier),
    )
}

/**
 * Library grid tile: full-bleed portrait, name on a scrim, lifts and zooms on hover, dips on press.
 */
@Composable
fun ArtistTile(
    name: String,
    subtitle: String? = null,
    imageUrl: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        when {
            pressed -> 0.97f
            hovered -> 1.035f
            else -> 1f
        },
        if (pressed) VisageMotion.pressDown() else VisageMotion.press(),
        label = "artistTileScale",
    )
    val lift by animateFloatAsState(if (hovered) 14f else 2f, VisageMotion.press(), label = "artistTileLift")
    val zoom by animateFloatAsState(if (hovered) 1.08f else 1f, VisageMotion.spatialSlow(), label = "artistTileZoom")
    val scrimAlpha by animateFloatAsState(if (hovered) 0.88f else 0.7f, VisageMotion.effectsDefault(), label = "artistTileScrim")
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(lift.dp, shape, clip = false)
            .clip(shape)
            .hoverable(source)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
    ) {
        ArtistImage(
            name = name,
            imageUrl = imageUrl,
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom },
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = scrimAlpha))))
                .padding(start = 14.dp, end = 14.dp, top = 34.dp, bottom = 12.dp),
        ) {
            androidx.compose.foundation.layout.Column {
                Text(
                    name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
