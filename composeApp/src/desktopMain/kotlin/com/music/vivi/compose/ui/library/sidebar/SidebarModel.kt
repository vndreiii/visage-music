package com.music.vivi.compose.ui.library.sidebar

import java.text.Normalizer

enum class EntryKind { LIKED, DOWNLOADS, YT_PLAYLIST, LOCAL_PLAYLIST, ALBUM, ARTIST }

enum class LibraryFilter(val label: String) {
    ALL("All"), YOUTUBE("YouTube Music"), MINE("My playlists"),
    ALBUMS("Albums"), ARTISTS("Artists"), DOWNLOADED("Downloaded"),
}

enum class LibrarySort(val label: String) { RECENT("Recent"), ALPHABETICAL("Alphabetical"), CREATOR("Creator") }

/** One row of the library sidebar, already normalised from YT / local / download sources. */
data class LibraryEntry(
    val key: String,
    val id: String,
    val kind: EntryKind,
    val title: String,
    /** Author / artist / "You". */
    val creator: String = "",
    val trackCount: Int? = null,
    val thumbnail: String? = null,
    /** Lower is more recent (position in the source's own recency order). */
    val recentRank: Int = Int.MAX_VALUE,
) {
    val pinned: Boolean get() = kind == EntryKind.LIKED || kind == EntryKind.DOWNLOADS
    val isPlaylist: Boolean get() = kind == EntryKind.YT_PLAYLIST || kind == EntryKind.LOCAL_PLAYLIST

    val subtitle: String
        get() = buildList {
            when (kind) {
                EntryKind.ALBUM -> add("Album")
                EntryKind.ARTIST -> add("Artist")
                else -> if (creator.isNotBlank()) add(creator)
            }
            if (kind != EntryKind.ARTIST) trackCount?.let { add(if (it == 1) "1 track" else "$it tracks") }
        }.joinToString(" · ")
}

private fun String.fold(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

fun LibraryEntry.matches(filter: LibraryFilter): Boolean = when (filter) {
    LibraryFilter.ALL -> true
    LibraryFilter.YOUTUBE -> kind == EntryKind.YT_PLAYLIST || kind == EntryKind.LIKED
    LibraryFilter.MINE -> kind == EntryKind.LOCAL_PLAYLIST
    LibraryFilter.ALBUMS -> kind == EntryKind.ALBUM
    LibraryFilter.ARTISTS -> kind == EntryKind.ARTIST
    LibraryFilter.DOWNLOADED -> kind == EntryKind.DOWNLOADS
}

/**
 * Filter by chip, search by title/creator (accent- and case-insensitive), then sort.
 * Pinned entries (Liked Songs, Downloaded) always lead, in a fixed order, and only
 * survive a search when they match it.
 */
fun filterAndSort(
    entries: List<LibraryEntry>,
    filter: LibraryFilter,
    sort: LibrarySort,
    query: String,
): List<LibraryEntry> {
    val q = query.trim().fold()
    val kept = entries.filter { e ->
        e.matches(filter) && (q.isEmpty() || e.title.fold().contains(q) || e.creator.fold().contains(q))
    }
    val (pinned, rest) = kept.partition { it.pinned }
    val sorted = when (sort) {
        LibrarySort.RECENT -> rest.sortedBy { it.recentRank }
        LibrarySort.ALPHABETICAL -> rest.sortedWith(compareBy({ it.title.fold() }, { it.recentRank }))
        LibrarySort.CREATOR -> rest.sortedWith(compareBy({ it.creator.isBlank() }, { it.creator.fold() }, { it.title.fold() }))
    }
    return pinned.sortedBy { it.kind.ordinal } + sorted
}

/** Merge the three sources into one list; local playlists are "recent" first since the user just touched them. */
fun buildEntries(
    ytPlaylists: List<LibraryEntry>,
    localPlaylists: List<LibraryEntry>,
    albums: List<LibraryEntry>,
    artists: List<LibraryEntry>,
    liked: LibraryEntry?,
    downloads: LibraryEntry?,
): List<LibraryEntry> =
    listOfNotNull(liked, downloads) + localPlaylists + ytPlaylists.filter { it.id != "LM" } + albums + artists

// ── Folders + hidden items ─────────────────────────────────────────────────────────────────────

enum class CoverKind { AUTO, GENERATED, ICON, IMAGE }

enum class FolderSort(val label: String) { MANUAL("Manual"), RECENT("Recent"), ALPHABETICAL("Alphabetical") }

/** A user folder as the sidebar sees it. [children] are entry keys in manual order. */
data class LibraryFolderModel(
    val id: String,
    val title: String,
    val icon: String = "folder",
    val accent: Long? = null,
    val coverKind: CoverKind = CoverKind.AUTO,
    val coverRef: String? = null,
    val sort: FolderSort = FolderSort.MANUAL,
    val expanded: Boolean = true,
    val position: Int = 0,
    val children: List<String> = emptyList(),
) {
    val key: String get() = "folder:$id"
}

sealed interface SidebarRow {
    val key: String

    data class Item(val entry: LibraryEntry, val folderId: String? = null, val hidden: Boolean = false) : SidebarRow {
        override val key: String get() = entry.key
    }

    /** [count] is the total in the folder, [shown] how many survive the active filter/search. */
    data class Folder(val folder: LibraryFolderModel, val count: Int, val shown: Int, val open: Boolean) : SidebarRow {
        override val key: String get() = folder.key
    }
}

/** Entry key as persisted in the DB (type + id). */
fun LibraryEntry.dbType(): String = kind.name

private fun LibraryEntry.matchesQuery(q: String) = q.isEmpty() || title.fold().contains(q) || creator.fold().contains(q)

/**
 * The full sidebar list: pinned entries, folders (first or mixed with the rest) with their children
 * inline when open, then ungrouped entries. Hidden entries vanish unless [showHidden]. Search looks
 * inside folders (a matching folder title keeps all its children; otherwise only matching children
 * show and the folder force-opens). Pinned entries (Liked / Downloaded) cannot be foldered.
 */
fun buildSidebarRows(
    entries: List<LibraryEntry>,
    folders: List<LibraryFolderModel>,
    hidden: Set<String>,
    showHidden: Boolean,
    filter: LibraryFilter,
    sort: LibrarySort,
    query: String,
    foldersFirst: Boolean = true,
): List<SidebarRow> {
    val q = query.trim().fold()
    val byKey = entries.associateBy { it.key }
    val inFolder = folders.flatMap { it.children }.filter { it in byKey && !byKey.getValue(it).pinned }.toSet()
    fun isHidden(e: LibraryEntry) = e.key in hidden
    fun visibleEntry(e: LibraryEntry) = showHidden || !isHidden(e)

    val top = entries.filter { it.key !in inFolder && visibleEntry(it) && it.matches(filter) && it.matchesQuery(q) }
    val (pinned, rest) = top.partition { it.pinned }
    val sortedRest = sortEntries(rest, sort)

    class Built(val row: SidebarRow.Folder, val kids: List<SidebarRow.Item>)

    val built = folders.mapNotNull { f ->
        val all = f.children.mapNotNull { byKey[it] }.filter { !it.pinned && visibleEntry(it) }
        val titleHit = q.isNotEmpty() && f.title.fold().contains(q)
        val kidsMatching = all.filter { it.matches(filter) && (titleHit || it.matchesQuery(q)) }
        val searching = q.isNotEmpty() || filter != LibraryFilter.ALL
        if (searching && kidsMatching.isEmpty() && !(titleHit && filter == LibraryFilter.ALL)) return@mapNotNull null
        val ordered = when (f.sort) {
            FolderSort.MANUAL -> kidsMatching
            FolderSort.RECENT -> kidsMatching.sortedBy { it.recentRank }
            FolderSort.ALPHABETICAL -> kidsMatching.sortedWith(compareBy({ it.title.fold() }, { it.recentRank }))
        }
        val open = (f.expanded && q.isEmpty()) || (q.isNotEmpty() && kidsMatching.isNotEmpty() && !titleHit) || (titleHit && f.expanded)
        Built(
            SidebarRow.Folder(f, count = all.size, shown = ordered.size, open = open),
            ordered.map { SidebarRow.Item(it, f.id, isHidden(it)) },
        )
    }

    fun flat(b: Built): List<SidebarRow> = listOf(b.row) + if (b.row.open) b.kids else emptyList()
    fun item(e: LibraryEntry) = SidebarRow.Item(e, null, isHidden(e))

    val sortedFolders = when (sort) {
        LibrarySort.ALPHABETICAL -> built.sortedBy { it.row.folder.title.fold() }
        else -> built.sortedBy { it.row.folder.position }
    }
    val out = ArrayList<SidebarRow>()
    pinned.sortedBy { it.kind.ordinal }.forEach { out += item(it) }
    if (foldersFirst || sort == LibrarySort.CREATOR) {
        sortedFolders.forEach { out += flat(it) }
        sortedRest.forEach { out += item(it) }
    } else {
        // Mixed: folders slot into the sorted list by title (alphabetical) or lead the recents.
        val merged = sortedFolders.map { it.row.folder.title.fold() to flat(it) } +
            sortedRest.map { it.title.fold() to listOf<SidebarRow>(item(it)) }
        if (sort == LibrarySort.ALPHABETICAL) merged.sortedBy { it.first }.forEach { out += it.second }
        else { sortedFolders.forEach { out += flat(it) }; sortedRest.forEach { out += item(it) } }
    }
    return out
}

private fun sortEntries(rest: List<LibraryEntry>, sort: LibrarySort): List<LibraryEntry> = when (sort) {
    LibrarySort.RECENT -> rest.sortedBy { it.recentRank }
    LibrarySort.ALPHABETICAL -> rest.sortedWith(compareBy({ it.title.fold() }, { it.recentRank }))
    LibrarySort.CREATOR -> rest.sortedWith(compareBy({ it.creator.isBlank() }, { it.creator.fold() }, { it.title.fold() }))
}
