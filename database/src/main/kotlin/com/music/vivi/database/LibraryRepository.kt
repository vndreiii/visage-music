package com.music.vivi.database

import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import kotlinx.coroutines.flow.map

/**
 * Cache/upsert helpers for YouTube (and later local) library data.
 * Stores the metadata shown on playlist headers: title, song count, total duration.
 */
class LibraryRepository(
    private val db: VisageDatabase,
) {
    private val _mutations = kotlinx.coroutines.flow.MutableStateFlow(0L)

    /** Bumped on user-driven library mutations (like/unlike) so in-memory caches can refresh. */
    val mutations: kotlinx.coroutines.flow.StateFlow<Long> = _mutations

    fun upsertSong(
        song: SongItem,
        liked: Boolean = false,
        inLibrary: Boolean = false,
        source: String = "youtube",
        localPath: String? = null,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        db.songQueries.upsert(
            id = song.id,
            title = song.title,
            artists = song.artists.joinToString { it.name },
            album = song.album?.name,
            duration_sec = song.duration?.toLong(),
            thumbnail = song.thumbnail,
            liked = if (liked) 1L else 0L,
            in_library = if (inLibrary) 1L else 0L,
            source = source,
            local_path = localPath,
            last_seen_ms = nowMs,
        )
        db.songQueries.updateMetadata(
            song.title, song.artists.joinToString { it.name }, song.album?.name,
            song.duration?.toLong(), song.thumbnail, if (liked) 1L else 0L,
            if (inLibrary) 1L else 0L, source, localPath, nowMs, song.id,
        )
    }

    /**
     * Persist a full playlist snapshot (header numbers + ordered songs).
     * Replaces [playlist_song] rows for this playlist id.
     */
    fun upsertPlaylistWithSongs(
        playlist: PlaylistItem,
        songs: List<SongItem>,
        source: String = "youtube",
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val totalDuration = songs.sumOf { (it.duration ?: 0).toLong() }
        val songCount = songs.size.takeIf { it > 0 }
            ?: playlist.songCountText?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() }

        db.transaction {
            db.playlistQueries.upsert(
                id = playlist.id,
                title = playlist.title,
                author = playlist.author?.name,
                thumbnail = playlist.thumbnail,
                description = playlist.description,
                song_count = songCount?.toLong(),
                total_duration_sec = totalDuration.takeIf { it > 0 },
                source = source,
                browse_id = playlist.id,
                updated_ms = nowMs,
            )
            db.playlistQueries.updateMetadata(
                playlist.title, playlist.author?.name, playlist.thumbnail, playlist.description,
                songCount?.toLong(), totalDuration.takeIf { it > 0 }, source, playlist.id, nowMs, playlist.id,
            )
            db.playlistSongQueries.clearForPlaylist(playlist.id)
            songs.forEachIndexed { index, song ->
                upsertSong(song, liked = playlist.id == "LM", inLibrary = true, source = source, nowMs = nowMs)
                db.playlistSongQueries.upsert(
                    playlist_id = playlist.id,
                    song_id = song.id,
                    position = index.toLong(),
                )
            }
        }
    }

    fun getPlaylist(id: String) = db.playlistQueries.selectById(id).executeAsOneOrNull()

    fun getPlaylistSongs(id: String) = db.playlistSongQueries.songsForPlaylist(id).executeAsList()

    fun allPlaylists(source: String? = null) =
        if (source == null) db.playlistQueries.selectAll().executeAsList()
        else db.playlistQueries.selectBySource(source).executeAsList()

    fun likedSongs() = db.songQueries.selectLiked().executeAsList()

    fun isLiked(id: String): Boolean {
        val song = db.songQueries.selectById(id).executeAsOneOrNull()
        return song?.liked == 1L
    }

    fun setLiked(id: String, liked: Boolean) {
        try {
            setLikedInternal(id, liked)
        } finally {
            _mutations.value++
        }
    }

    private fun setLikedInternal(id: String, liked: Boolean) {
        if (!liked) db.playlistSongQueries.removeSong("LM", id)
        val song = db.songQueries.selectById(id).executeAsOneOrNull()
        if (song != null) {
            db.songQueries.setLiked(if (liked) 1L else 0L, id)
            return
        }
        if (liked) {
            db.songQueries.upsert(
                id = id,
                title = id,
                artists = "",
                album = null,
                duration_sec = null,
                thumbnail = null,
                liked = 1L,
                in_library = 0L,
                source = "youtube",
                local_path = null,
                last_seen_ms = System.currentTimeMillis(),
            )
        }
    }

    // ── Local (user-created) playlists: rows in `playlist` with source = "local" ──────────────

    private val _playlistMutations = kotlinx.coroutines.flow.MutableStateFlow(0L)

    /** Bumped whenever a local playlist is created/renamed/deleted/changed. */
    val playlistMutations: kotlinx.coroutines.flow.StateFlow<Long> = _playlistMutations

    /** User-created playlists, most recently touched first. */
    fun localPlaylists(): List<Playlist> = db.playlistQueries.selectBySource(LOCAL_SOURCE).executeAsList()

    fun createLocalPlaylist(title: String, nowMs: Long = System.currentTimeMillis()): String {
        val id = LOCAL_PREFIX + java.util.UUID.randomUUID().toString()
        db.playlistQueries.upsert(
            id = id,
            title = title.trim().ifBlank { "New playlist" },
            author = "You",
            thumbnail = null,
            description = null,
            song_count = 0,
            total_duration_sec = null,
            source = LOCAL_SOURCE,
            browse_id = null,
            updated_ms = nowMs,
        )
        _playlistMutations.value++
        return id
    }

    fun renameLocalPlaylist(id: String, title: String, nowMs: Long = System.currentTimeMillis()) {
        if (!id.startsWith(LOCAL_PREFIX) || title.isBlank()) return
        db.playlistQueries.setTitle(title.trim(), nowMs, id)
        _playlistMutations.value++
    }

    fun deleteLocalPlaylist(id: String) {
        if (!id.startsWith(LOCAL_PREFIX)) return
        db.transaction {
            db.playlistSongQueries.clearForPlaylist(id)
            db.playlistQueries.deleteById(id)
            db.libraryFolderQueries.removeFolderItem("LOCAL_PLAYLIST", id)
            db.libraryFolderQueries.unhide("LOCAL_PLAYLIST", id)
        }
        _playlistMutations.value++
    }

    /** Appends [song] (persisting its metadata first). Returns false when it was already in the playlist. */
    fun addToLocalPlaylist(
        id: String,
        songId: String,
        title: String,
        artists: String,
        thumbnail: String?,
        durationSec: Long?,
        localPath: String? = null,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!id.startsWith(LOCAL_PREFIX)) return false
        var added = false
        db.transaction {
            if (db.playlistSongQueries.containsSong(id, songId).executeAsOne() > 0L) return@transaction
            db.songQueries.upsert(
                id = songId, title = title, artists = artists, album = null, duration_sec = durationSec,
                thumbnail = thumbnail, liked = 0L, in_library = 1L,
                source = if (localPath != null) "local" else "youtube", local_path = localPath, last_seen_ms = nowMs,
            )
            val position = db.playlistSongQueries.nextPosition(id).executeAsOne()
            db.playlistSongQueries.upsert(id, songId, position)
            refreshLocalStats(id, nowMs)
            added = true
        }
        if (added) _playlistMutations.value++
        return added
    }

    fun removeFromLocalPlaylist(id: String, songId: String, nowMs: Long = System.currentTimeMillis()) {
        if (!id.startsWith(LOCAL_PREFIX)) return
        db.transaction {
            db.playlistSongQueries.removeSong(id, songId)
            val rest = db.playlistSongQueries.songsForPlaylist(id).executeAsList()
            rest.forEachIndexed { i, s -> db.playlistSongQueries.setPosition(i.toLong(), id, s.id) }
            refreshLocalStats(id, nowMs)
        }
        _playlistMutations.value++
    }

    /** Moves the song at [from] to [to] (indices into the playlist order) and rewrites positions. */
    fun moveInLocalPlaylist(id: String, from: Int, to: Int) {
        if (!id.startsWith(LOCAL_PREFIX)) return
        db.transaction {
            val ids = db.playlistSongQueries.songsForPlaylist(id).executeAsList().map { it.id }
            val reordered = reorder(ids, from, to) ?: return@transaction
            reordered.forEachIndexed { i, sid -> db.playlistSongQueries.setPosition(i.toLong(), id, sid) }
        }
        _playlistMutations.value++
    }

    private fun refreshLocalStats(id: String, nowMs: Long) {
        val songs = db.playlistSongQueries.songsForPlaylist(id).executeAsList()
        db.playlistQueries.setStats(
            songs.size.toLong(),
            songs.sumOf { it.duration_sec ?: 0L }.takeIf { it > 0 },
            songs.firstNotNullOfOrNull { it.thumbnail },
            nowMs,
            id,
        )
    }

    // ── Sidebar folders + hidden items (sidebar-only; the underlying items are never touched) ──

    private val _folderMutations = kotlinx.coroutines.flow.MutableStateFlow(0L)

    /** Bumped whenever folders, folder membership or hidden items change. */
    val folderMutations: kotlinx.coroutines.flow.StateFlow<Long> = _folderMutations

    /** Emits the current folders + membership + hidden set now and after every change. */
    val folderState: kotlinx.coroutines.flow.Flow<FolderState> =
        _folderMutations.map { FolderState(libraryFolders(), folderItems(), hiddenItems()) }

    data class FolderState(val folders: List<Library_folder>, val items: List<Library_folder_item>, val hidden: Set<Pair<String, String>>)

    fun libraryFolders(): List<Library_folder> = db.libraryFolderQueries.allFolders().executeAsList()

    fun folderItems(): List<Library_folder_item> = db.libraryFolderQueries.allFolderItems().executeAsList()

    fun hiddenItems(): Set<Pair<String, String>> =
        db.libraryFolderQueries.allHidden().executeAsList().map { it.item_type to it.item_id }.toSet()

    fun createFolder(
        title: String,
        icon: String = "folder",
        accent: Long? = null,
        coverKind: String = "AUTO",
        coverRef: String? = null,
        nowMs: Long = System.currentTimeMillis(),
    ): String {
        val id = FOLDER_PREFIX + java.util.UUID.randomUUID().toString()
        db.libraryFolderQueries.insertFolder(
            id, title.trim().ifBlank { "New folder" }, icon, accent, coverKind, coverRef, "MANUAL",
            db.libraryFolderQueries.nextFolderPosition().executeAsOne(), nowMs,
        )
        _folderMutations.value++
        return id
    }

    fun updateFolder(id: String, title: String, icon: String, accent: Long?, coverKind: String, coverRef: String?) {
        db.libraryFolderQueries.updateFolder(title.trim().ifBlank { "New folder" }, icon, accent, coverKind, coverRef, id)
        _folderMutations.value++
    }

    fun setFolderSort(id: String, sort: String) {
        db.libraryFolderQueries.setFolderSort(sort, id)
        _folderMutations.value++
    }

    fun setFolderExpanded(id: String, expanded: Boolean) {
        db.libraryFolderQueries.setFolderExpanded(if (expanded) 1 else 0, id)
        _folderMutations.value++
    }

    /** Deletes the folder only; its items simply return to the main list. */
    fun deleteFolder(id: String) {
        db.libraryFolderQueries.deleteFolder(id)
        _folderMutations.value++
    }

    /** Puts an item in [folderId] (moving it out of any other folder), appended at the end. */
    fun addToFolder(folderId: String, itemType: String, itemId: String) {
        db.transaction {
            db.libraryFolderQueries.removeFolderItem(itemType, itemId)
            db.libraryFolderQueries.putFolderItem(
                folderId, itemType, itemId, db.libraryFolderQueries.nextItemPosition(folderId).executeAsOne(),
            )
        }
        _folderMutations.value++
    }

    fun removeFromFolder(itemType: String, itemId: String) {
        db.libraryFolderQueries.removeFolderItem(itemType, itemId)
        _folderMutations.value++
    }

    /** Moves an item within its folder's manual order. */
    fun moveInFolder(folderId: String, itemType: String, itemId: String, to: Int) {
        db.transaction {
            val ids = db.libraryFolderQueries.allFolderItems().executeAsList().filter { it.folder_id == folderId }
            val from = ids.indexOfFirst { it.item_type == itemType && it.item_id == itemId }
            val reordered = reorder(ids, from, to) ?: return@transaction
            reordered.forEachIndexed { i, r -> db.libraryFolderQueries.setItemPosition(i.toLong(), r.item_type, r.item_id) }
        }
        _folderMutations.value++
    }

    fun hideItem(itemType: String, itemId: String) {
        db.libraryFolderQueries.hide(itemType, itemId)
        _folderMutations.value++
    }

    fun unhideItem(itemType: String, itemId: String) {
        db.libraryFolderQueries.unhide(itemType, itemId)
        _folderMutations.value++
    }

    fun resetHidden() {
        db.libraryFolderQueries.clearHidden()
        _folderMutations.value++
    }

    companion object {
        const val FOLDER_PREFIX = "folder-"
        const val LOCAL_SOURCE = "local"
        const val LOCAL_PREFIX = "local-pl-"

        /** Pure list move used by [moveInLocalPlaylist]; null when the indices are invalid or a no-op. */
        fun <T> reorder(items: List<T>, from: Int, to: Int): List<T>? {
            if (from !in items.indices || to !in items.indices || from == to) return null
            return items.toMutableList().apply { add(to, removeAt(from)) }
        }
    }
}
