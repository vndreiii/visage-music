package com.music.vivi.compose.ui.tags

import java.io.File

/** Every editable tag. [key] is the wire name understood by the native writer. */
enum class TagField(val key: String, val label: String, val numeric: Boolean = false, val multiline: Boolean = false) {
    Title("title", "Title"),
    Artist("artist", "Artist"),
    Album("album", "Album"),
    AlbumArtist("albumArtist", "Album artist"),
    Year("year", "Year", numeric = true),
    Genre("genre", "Genre"),
    TrackNo("trackNo", "Track", numeric = true),
    TrackTotal("trackTotal", "Of", numeric = true),
    DiscNo("discNo", "Disc", numeric = true),
    Composer("composer", "Composer"),
    Comment("comment", "Comment"),
    Lyrics("lyrics", "Lyrics", multiline = true),
}

/** Tag values by field; a missing key and a blank value both mean "not set". */
typealias Tags = Map<TagField, String>

fun Tags.value(f: TagField): String = this[f].orEmpty()

/** Same tags with blanks dropped and values trimmed (multi-line lyrics only lose trailing space). */
fun Tags.cleaned(): Tags =
    entries.mapNotNull { (k, v) ->
        val t = if (k.multiline) v.trimEnd() else v.trim()
        if (t.isBlank()) null else k to t
    }.toMap()

/** What the native reader reports for one file. */
data class TagReadResult(
    val path: String,
    val tags: Tags,
    val format: String = "",
    val readonly: Boolean = false,
    val hasCover: Boolean = false,
    /** Extracted copy of the embedded cover in the app's covers folder. */
    val coverPath: String? = null,
    val supportsCover: Boolean = true,
    val durationSec: Long? = null,
)

data class WriteSpec(
    val path: String,
    /** Only these fields are touched; a blank value clears the tag. */
    val fields: Tags,
    val coverFile: String? = null,
    val removeCover: Boolean = false,
    val id3v23: Boolean = false,
    val backupPath: String? = null,
)

data class WriteOutcome(val ok: Boolean, val error: String? = null, val size: Long = 0, val mtimeMs: Long = 0)

/** Reads/writes tags in audio files. The real one is Rust/lofty; tests use a fake. */
interface TagWriter {
    fun read(path: String): TagReadResult?
    fun write(spec: WriteSpec): WriteOutcome
    /** Atomically puts [backup] back over [dest]. */
    fun restore(backup: String, dest: String): WriteOutcome
}

/** The slice of a library row the Tag Manager works on. */
data class TagTrack(
    val id: String,
    val path: String,
    val tags: Tags,
    val coverPath: String?,
    val durationSec: Long? = null,
    val readonly: Boolean = false,
) {
    val folder: String get() = File(path).parent.orEmpty()
    val fileName: String get() = File(path).name
    val stem: String get() = File(path).nameWithoutExtension
}
