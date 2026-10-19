package com.music.innertube.pages

import com.music.innertube.models.Album
import com.music.innertube.models.Artist
import com.music.innertube.models.BrowseEndpoint
import com.music.innertube.models.PlaylistPanelVideoRenderer
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.oddElements
import com.music.innertube.models.splitBySeparator
import com.music.innertube.utils.parseTime

data class RadioChip(
    val title: String,
    val params: String,
    val isSelected: Boolean
)

data class NextResult(
    val title: String? = null,
    val items: List<SongItem>,
    val currentIndex: Int? = null,
    val lyricsEndpoint: BrowseEndpoint? = null,
    val relatedEndpoint: BrowseEndpoint? = null,
    val continuation: String?,
    val endpoint: WatchEndpoint, // current or continuation next endpoint
    val radioChips: List<RadioChip> = emptyList(),
)

object NextPage {
    fun fromPlaylistPanelVideoRenderer(renderer: PlaylistPanelVideoRenderer): SongItem? {
        val longByLineRuns = renderer.longBylineText?.runs?.splitBySeparator() ?: return null

        // Extract library tokens using the new method that properly handles multiple toggle items
        val libraryTokens = PageHelper.extractLibraryTokensFromMenuItems(renderer.menu?.menuRenderer?.items)

        return SongItem(
            id = renderer.videoId ?: return null,
            title =
                renderer.title
                    ?.runs
                    ?.firstOrNull()
                    ?.text ?: return null,
            artists =
                longByLineRuns.firstOrNull()?.oddElements()?.map {
                    Artist(
                        name = it.text,
                        id = it.navigationEndpoint?.browseEndpoint?.browseId,
                    )
                } ?: return null,
            album =
                longByLineRuns
                    .getOrNull(1)
                    ?.firstOrNull()
                    ?.takeIf {
                        it.navigationEndpoint?.browseEndpoint != null
                    }?.let {
                        Album(
                            name = it.text,
                            id = it.navigationEndpoint?.browseEndpoint?.browseId!!,
                        )
                    },
            duration =
                renderer.lengthText
                    ?.runs
                    ?.firstOrNull()
                    ?.text
                    ?.parseTime() ?: return null,
            musicVideoType = renderer.navigationEndpoint.musicVideoType,
            thumbnail =
                renderer.thumbnail.thumbnails
                    .lastOrNull()
                    ?.url ?: return null,
            explicit =
                renderer.badges?.find {
                    it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                } != null,
            libraryAddToken = libraryTokens.addToken,
            libraryRemoveToken = libraryTokens.removeToken,
            counterpartVideoId = counterpartVideoId(renderer),
        )
    }

    /** The "Switch to Video" twin's id, only when it really is a video (not another audio track). */
    fun counterpartVideoId(renderer: PlaylistPanelVideoRenderer): String? =
        renderer.counterpart?.asSequence()
            ?.mapNotNull { it.counterpartRenderer?.playlistPanelVideoRenderer }
            ?.firstOrNull { c ->
                c.videoId != null && c.videoId != renderer.videoId &&
                    (c.navigationEndpoint?.musicVideoType.let { it == null || MusicVideoTypes.isVideo(it) })
            }?.videoId
}

/** Classification of YouTube Music `musicVideoType` values. */
object MusicVideoTypes {
    const val ATV = "MUSIC_VIDEO_TYPE_ATV"
    const val OMV = "MUSIC_VIDEO_TYPE_OMV"
    const val UGC = "MUSIC_VIDEO_TYPE_UGC"
    const val OFFICIAL_SOURCE = "MUSIC_VIDEO_TYPE_OFFICIAL_SOURCE_MUSIC"

    /** True for types that carry real video (everything but the static-art audio track). */
    fun isVideo(type: String?): Boolean = type != null && type != ATV

    /**
     * The video id to show for a track: the track itself when it already is a video, else the
     * audio track's counterpart, else null (no video available).
     */
    fun videoIdFor(id: String, type: String?, counterpartVideoId: String?): String? = when {
        isVideo(type) -> id
        counterpartVideoId != null -> counterpartVideoId
        else -> null
    }
}
