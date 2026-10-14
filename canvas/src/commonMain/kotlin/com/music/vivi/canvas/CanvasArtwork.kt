package com.music.vivi.canvas

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CanvasArtwork(
    val name: String? = null,
    val artist: String? = null,
    @SerialName("albumId")
    val albumId: String? = null,
    val albumName: String? = null,
    val static: String? = null,
    val animated: String? = null,
    val videoUrl: String? = null,
    val animatedTall: String? = null,
    /**
     * Stable identity of the canvas: `apple:album:<id>` / `tidal:album:<artist>:<album>` are album-level
     * (one canvas shared by every track on the album), `vivi:<song>:<artist>:<album>` is per song.
     */
    val canvasKey: String? = null,
) {
    val preferredAnimationUrl: String?
        get() = animated ?: videoUrl
}
