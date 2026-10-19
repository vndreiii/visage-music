package com.music.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class PlaylistPanelVideoRenderer(
    val title: Runs?,
    val lengthText: Runs?,
    val longBylineText: Runs?,
    val shortBylineText: Runs?,
    val badges: List<Badges>?,
    val videoId: String?,
    val playlistSetVideoId: String?,
    val selected: Boolean,
    val thumbnail: Thumbnails,
    val unplayableText: Runs?,
    val menu: Menu?,
    val navigationEndpoint: NavigationEndpoint,
    /** "Switch to Video" / "Switch to Audio" twin of this track (ATV <-> OMV). */
    val counterpart: List<Counterpart>? = null,
) {
    @Serializable
    data class Counterpart(val counterpartRenderer: CounterpartRenderer? = null)

    @Serializable
    data class CounterpartRenderer(val playlistPanelVideoRenderer: CounterpartVideo? = null)

    /** Only the fields needed to identify the twin; the real response carries a full renderer. */
    @Serializable
    data class CounterpartVideo(
        val videoId: String? = null,
        val navigationEndpoint: NavigationEndpoint? = null,
    )
}
