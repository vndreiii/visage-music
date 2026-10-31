package com.music.vivi.compose.ui.library.sidebar

import java.text.Normalizer

/**
 * One hidden sidebar item for the Settings list. [type] and [id] are the persisted key. When the item cannot be resolved
 * (its playlist, album or artist is not in the loaded library), [resolved] is false and [title] falls back to the raw id,
 * so the item can still be restored.
 */
data class HiddenItemRow(
    val type: String,
    val id: String,
    val kind: EntryKind?,
    val title: String,
    val subtitle: String,
    val thumbnail: String?,
    val resolved: Boolean,
) {
    val key: String get() = "$type:$id"
}

private fun EntryKind.hiddenLabel(): String = when (this) {
    EntryKind.LIKED, EntryKind.YT_PLAYLIST, EntryKind.LOCAL_PLAYLIST -> "Playlist"
    EntryKind.DOWNLOADS -> "Downloads"
    EntryKind.ALBUM -> "Album"
    EntryKind.ARTIST -> "Artist"
}

private fun String.foldForSort(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

/**
 * Resolves persisted hidden (type, id) pairs against the known [entries]. Unknown pairs stay in the list with the raw id
 * as title. Sorted: resolved rows first, then by kind, then by title.
 */
fun hiddenItemRows(hidden: Set<Pair<String, String>>, entries: List<LibraryEntry>): List<HiddenItemRow> {
    val byDb = entries.associateBy { it.kind.name to it.id }
    return hidden.map { (type, id) ->
        val kind = EntryKind.entries.firstOrNull { it.name == type }
        val entry = byDb[type to id]
        if (entry != null) {
            val creator = entry.creator.takeIf {
                (entry.kind == EntryKind.YT_PLAYLIST || entry.kind == EntryKind.ALBUM) && it.isNotBlank()
            }
            HiddenItemRow(
                type = type,
                id = id,
                kind = entry.kind,
                title = entry.title,
                subtitle = listOfNotNull(entry.kind.hiddenLabel(), creator).joinToString(" · "),
                thumbnail = entry.thumbnail,
                resolved = true,
            )
        } else {
            HiddenItemRow(
                type = type,
                id = id,
                kind = kind,
                title = id,
                subtitle = listOfNotNull(kind?.hiddenLabel() ?: "Item", "Details unavailable").joinToString(" · "),
                thumbnail = null,
                resolved = false,
            )
        }
    }.sortedWith(
        compareBy<HiddenItemRow>({ !it.resolved }, { it.kind?.ordinal ?: Int.MAX_VALUE }, { it.title.foldForSort() }, { it.id }),
    )
}
