package com.music.vivi.compose.ui.tags

import java.io.File

/**
 * Which tags look missing, judged from what the library stored (the scanner falls back to the file
 * name / folder name / "Unknown Artist" when a tag is absent, so those fallbacks are the signal).
 */
data class TagHealth(
    val missingTitle: Boolean,
    val missingArtist: Boolean,
    val missingAlbum: Boolean,
    val missingCover: Boolean,
) {
    val any: Boolean get() = missingTitle || missingArtist || missingAlbum || missingCover
    val missingTags: Boolean get() = missingTitle || missingArtist || missingAlbum

    companion object {
        const val UNKNOWN_ARTIST = "Unknown Artist"

        fun isUnknownArtist(artist: String?): Boolean {
            val a = artist?.trim().orEmpty()
            return a.isEmpty() || a.equals(UNKNOWN_ARTIST, true) || a.equals("<unknown>", true) || a.equals("unknown", true)
        }

        fun of(track: TagTrack): TagHealth {
            val t = track.tags
            val title = t.value(TagField.Title).trim()
            val album = t.value(TagField.Album).trim()
            val folderName = File(track.path).parentFile?.name.orEmpty()
            // An album equal to the folder name with nothing else filled in is the scanner's fallback.
            val albumFallback = album.equals(folderName, true) &&
                t.value(TagField.Year).isBlank() && t.value(TagField.Genre).isBlank() && t.value(TagField.TrackNo).isBlank()
            return TagHealth(
                missingTitle = title.isEmpty() || title.equals(track.stem, true),
                missingArtist = isUnknownArtist(t.value(TagField.Artist)),
                missingAlbum = album.isEmpty() || albumFallback,
                missingCover = track.coverPath.isNullOrBlank(),
            )
        }
    }
}
