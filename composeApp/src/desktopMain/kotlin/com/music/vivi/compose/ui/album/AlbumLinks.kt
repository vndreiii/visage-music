package com.music.vivi.compose.ui.album

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavController
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.vivi.compose.navigation.Routes
import com.music.vivi.compose.ui.component.visageMarquee
import com.music.vivi.compose.ui.motion.VisageMotion
import com.music.vivi.player.NowPlaying
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Pure helpers for turning a track's album name into an album page. */
object AlbumLinks {
    private fun key(s: String) = s.trim().lowercase().filter { it.isLetterOrDigit() }

    /** A name worth linking: tracks from local files without a known YouTube album id have nowhere to go. */
    fun isOpenable(album: String?, albumId: String?, isLocalFile: Boolean): Boolean =
        !album.isNullOrBlank() && (!albumId.isNullOrBlank() || !isLocalFile)

    /**
     * The search hit for [name] by [artist]: an exact (loosely normalised) title match, preferring one whose artists
     * include the track's artist; null when no title matches, so the caller falls back to a plain search.
     */
    fun pick(albums: List<AlbumItem>, name: String, artist: String): AlbumItem? {
        val wanted = key(name)
        val matches = albums.filter { key(it.title) == wanted }
        if (matches.isEmpty()) return null
        val artistKey = key(artist)
        return matches.firstOrNull { a -> a.artists.orEmpty().any { key(it.name).let { n -> n.isNotEmpty() && artistKey.contains(n) } } }
            ?: matches.first()
    }
}

/** Opens the album page for [np]: by id when known, otherwise by searching for it, otherwise a name search. */
suspend fun NavController.openAlbumOf(albumId: String?, album: String, artist: String) {
    if (!albumId.isNullOrBlank()) {
        navigate(Routes.album(albumId))
        return
    }
    val hit = withContext(Dispatchers.IO) {
        runCatching {
            YouTube.search("$album $artist".trim(), YouTube.SearchFilter.FILTER_ALBUM)
                .getOrNull()?.items.orEmpty().filterIsInstance<AlbumItem>()
        }.getOrNull()?.let { AlbumLinks.pick(it, album, artist) }
    }
    navigate(if (hit != null) Routes.album(hit.id) else Routes.searchResult(album))
}

suspend fun NavController.openAlbumOf(np: NowPlaying) = openAlbumOf(np.albumId, np.album.orEmpty(), np.artist)

/** An album name that underlines and brightens on hover and shows the hand cursor when [onClick] is given; plain text otherwise. */
@Composable
fun AlbumLink(
    name: String,
    style: TextStyle,
    color: Color,
    hoverColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)?,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val active = hovered && onClick != null
    val tint by animateColorAsState(if (active) hoverColor else color, VisageMotion.effectsFast(), label = "albumLinkColor")
    val underline by animateFloatAsState(if (active) 1f else 0f, VisageMotion.fade(200), label = "albumLinkUnderline")
    var m = modifier
    if (onClick != null) {
        m = m.hoverable(source).pointerHoverIcon(PointerIcon.Hand).clickable(interactionSource = source, indication = null, onClick = onClick)
    }
    Text(
        name,
        style = style,
        color = tint,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        modifier = m.drawBehind {
            if (underline > 0.01f) {
                val y = size.height - 1.5f
                drawLine(tint, Offset(0f, y), Offset(size.width * underline, y), strokeWidth = 1.5f)
            }
        }.visageMarquee(),
    )
}
