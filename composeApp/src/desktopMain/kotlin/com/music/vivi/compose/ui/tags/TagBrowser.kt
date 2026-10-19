package com.music.vivi.compose.ui.tags

import java.io.File

enum class TagFilter(val label: String) {
    All("All"),
    MissingTags("Missing tags"),
    MissingCover("Missing cover"),
    UnknownArtist("Unknown artist"),
    Modified("Modified"),
}

enum class TagGrouping(val label: String) { None("Flat"), Album("By album"), Folder("By folder") }

/** Filtering, searching and grouping for the Tag Manager list (pure, unit-tested). */
object TagBrowser {
    fun matches(track: TagTrack, filter: TagFilter, modified: Set<String>): Boolean {
        val h = TagHealth.of(track)
        return when (filter) {
            TagFilter.All -> true
            TagFilter.MissingTags -> h.missingTags
            TagFilter.MissingCover -> h.missingCover
            TagFilter.UnknownArtist -> h.missingArtist
            TagFilter.Modified -> track.id in modified
        }
    }

    fun search(track: TagTrack, query: String): Boolean {
        val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val hay = (TagField.entries.filter { !it.multiline }.map { track.tags.value(it) } + track.fileName).joinToString(" ").lowercase()
        return words.all { it in hay }
    }

    fun filter(tracks: List<TagTrack>, filter: TagFilter, query: String, modified: Set<String>): List<TagTrack> =
        tracks.filter { matches(it, filter, modified) && search(it, query) }

    fun counts(tracks: List<TagTrack>, modified: Set<String>): Map<TagFilter, Int> =
        TagFilter.entries.associateWith { f -> tracks.count { matches(it, f, modified) } }

    sealed interface Row {
        val key: String
        data class Header(override val key: String, val title: String, val subtitle: String, val count: Int) : Row
        data class Item(val track: TagTrack) : Row { override val key: String get() = track.id }
    }

    fun rows(tracks: List<TagTrack>, grouping: TagGrouping): List<Row> {
        if (grouping == TagGrouping.None) return tracks.map { Row.Item(it) }
        val groups = LinkedHashMap<String, MutableList<TagTrack>>()
        val sorted = when (grouping) {
            TagGrouping.Album -> tracks.sortedWith(compareBy({ albumKey(it) }, { it.tags.value(TagField.DiscNo).toIntOrNull() ?: 0 }, { it.tags.value(TagField.TrackNo).toIntOrNull() ?: Int.MAX_VALUE }, { it.fileName.lowercase() }))
            else -> tracks.sortedWith(compareBy({ it.folder.lowercase() }, { it.fileName.lowercase() }))
        }
        for (t in sorted) groups.getOrPut(if (grouping == TagGrouping.Album) albumKey(t) else t.folder) { ArrayList() }.add(t)
        return buildList {
            for ((k, list) in groups) {
                val head = if (grouping == TagGrouping.Album) {
                    val album = list.first().tags.value(TagField.Album).ifBlank { "No album" }
                    val artist = list.first().tags.value(TagField.AlbumArtist).ifBlank { list.first().tags.value(TagField.Artist) }
                    album to artist
                } else (File(k).name.ifEmpty { k }) to k
                add(Row.Header("g:$k", head.first, head.second, list.size))
                list.forEach { add(Row.Item(it)) }
            }
        }
    }

    private fun albumKey(t: TagTrack): String = t.folder + "\u0000" + t.tags.value(TagField.Album).lowercase()

    /** Possible duplicates: same canonical artist+title and similar length. */
    fun duplicates(tracks: List<TagTrack>): List<List<TagTrack>> =
        tracks.filter { TagScoring.canon(it.tags.value(TagField.Title)).isNotEmpty() }
            .groupBy { TagScoring.canon(it.tags.value(TagField.Artist)) + "|" + TagScoring.canon(it.tags.value(TagField.Title)) }
            .values
            .map { g -> g.filter { a -> g.any { b -> a !== b && (a.durationSec == null || b.durationSec == null || kotlin.math.abs(a.durationSec - b.durationSec) <= 3) } } }
            .filter { it.size > 1 }
}

/** Click/shift/ctrl selection over an ordered list of ids (pure so it can be tested). */
data class TagSelection(val ids: Set<String> = emptySet(), val anchor: String? = null) {
    fun click(id: String, order: List<String>, shift: Boolean, ctrl: Boolean): TagSelection = when {
        shift && anchor != null && anchor in order && id in order -> {
            val a = order.indexOf(anchor)
            val b = order.indexOf(id)
            val range = order.subList(minOf(a, b), maxOf(a, b) + 1).toSet()
            copy(ids = if (ctrl) ids + range else range)
        }
        ctrl -> if (id in ids) copy(ids = ids - id, anchor = id) else copy(ids = ids + id, anchor = id)
        else -> TagSelection(setOf(id), id)
    }

    fun toggle(id: String): TagSelection = if (id in ids) copy(ids = ids - id, anchor = id) else copy(ids = ids + id, anchor = id)
    fun selectAll(order: List<String>) = TagSelection(order.toSet(), order.firstOrNull())
    fun clear() = TagSelection()
    /** Drops ids that are no longer listed. */
    fun retain(order: Collection<String>): TagSelection {
        val all = order.toSet()
        val keep = ids.filter { it in all }.toSet()
        return if (keep.size == ids.size) this else copy(ids = keep)
    }
}
