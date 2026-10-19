package com.music.vivi.database

import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlin.coroutines.coroutineContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey

data class LocalScanProgress(
    val running: Boolean = false,
    val scanned: Int = 0,
    val totalHint: Int = 0,
    val currentFile: String? = null,
    val message: String? = null,
)

/**
 * Walks user folders, extracts tags via Jaudiotagger, upserts into [LocalMusicRepository].
 */
class LocalMusicScanner(
    private val repo: LocalMusicRepository,
) {
    init {
        // Jaudiotagger is extremely chatty on stderr (INFO per WAV chunk).
        System.setProperty("org.jaudiotagger.level", "OFF")
        runCatching {
            java.util.logging.Logger.getLogger("org.jaudiotagger").level =
                java.util.logging.Level.OFF
        }
    }

    private val _progress = MutableStateFlow(LocalScanProgress())
    val progress: StateFlow<LocalScanProgress> = _progress.asStateFlow()

    private val cancel = AtomicBoolean(false)
    private val scanMutex = Mutex()

    fun requestCancel() {
        cancel.set(true)
    }

    suspend fun scan(folders: List<File>): Int = withContext(Dispatchers.IO) {
        if (!scanMutex.tryLock()) return@withContext 0
        cancel.set(false)
        var ok = 0
        try {
            val roots = folders.map { it.canonicalFile }.filter { it.isDirectory }.distinct()
            if (roots.isEmpty()) {
                _progress.value = LocalScanProgress(message = "No accessible folders to scan")
                return@withContext 0
            }
            _progress.value = LocalScanProgress(running = true, message = "Looking for music…")
            val coversRoot = DatabaseFactory.coversDir()
            // Rust fast path: parallel walk + tag read with an incremental cache. Files it
            // could not read come back flagged and go through Jaudiotagger below.
            val native = if (NativeHelpers.available) {
                _progress.value = LocalScanProgress(running = true, message = "Scanning with native helper…")
                NativeHelpers.scan(
                    roots,
                    coversRoot,
                    repo.knownFiles().map { KnownFileEntry(it.path, it.size, it.mtimeMs, it.coverPath) },
                )
            } else null
            val entries: List<Pair<File, NativeTrack?>> = if (native != null) {
                native.tracks.map { File(it.path) to it.takeUnless { t -> t.unreadable } }
            } else {
                roots.flatMap { root -> collectAudioFiles(root) }.distinctBy { it.canonicalPath }.map { it to null }
            }
            val files = entries.map { it.first }
            _progress.value = LocalScanProgress(running = true, totalHint = files.size)
            val now = System.currentTimeMillis()
            for ((index, entry) in entries.withIndex()) {
                val (file, nativeTrack) = entry
                coroutineContext.ensureActive()
                if (cancel.get()) break
                _progress.value = LocalScanProgress(true, index, files.size, file.name)
                try {
                    when {
                        nativeTrack?.unchanged == true -> Unit // row already current
                        nativeTrack != null -> indexNative(file, nativeTrack, now)
                        else -> indexFile(file, now, coversRoot)
                    }
                    ok++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Preserve previously indexed tags if a file is temporarily unreadable.
                    if (repo.trackByPath(file.absolutePath) == null) {
                        runCatching { indexFileFallback(file, now) }.onSuccess { ok++ }
                    }
                }
            }
            if (!cancel.get()) {
                val missing = repo.allTracks().filter { track ->
                    val path = File(track.file_path).toPath()
                    roots.any { path.startsWith(it.toPath()) } && !File(track.file_path).exists()
                }
                repo.removeTracks(missing.map { it.id })
            }
            _progress.value = LocalScanProgress(
                scanned = ok,
                totalHint = files.size,
                message = if (cancel.get()) "Scan cancelled · $ok files indexed" else "Indexed $ok of ${files.size} files",
            )
            ok
        } catch (e: CancellationException) {
            _progress.value = _progress.value.copy(running = false, message = "Scan cancelled")
            throw e
        } catch (e: Exception) {
            _progress.value = _progress.value.copy(running = false, message = "Scan failed: ${e.message}")
            ok
        } finally {
            _progress.value = _progress.value.copy(running = false)
            scanMutex.unlock()
        }
    }

    /** Recursive collect — every subfolder under [root], skipping junk dirs. */
    private suspend fun collectAudioFiles(root: File): List<File> {
        val out = ArrayList<File>(512)
        root.walkTopDown()
            .onEnter { dir -> shouldEnterDir(dir) && !java.nio.file.Files.isSymbolicLink(dir.toPath()) }
            .forEach { f ->
                coroutineContext.ensureActive()
                if (cancel.get()) return out
                if (f.isFile && isAudioFile(f) && !isJunkPath(f) && f.length() >= MIN_AUDIO_BYTES) {
                    out.add(f)
                }
            }
        return out
    }

    private fun shouldEnterDir(dir: File): Boolean {
        if (cancel.get()) return false
        return !isJunkDirName(dir.name)
    }

    private fun isJunkDirName(name: String): Boolean {
        if (name.startsWith(".")) return true
        if (SKIP_DIR_NAMES.any { it.equals(name, ignoreCase = true) }) return true
        return false
    }

    /** Path-level guard — catches site-packages / venv even if a parent slipped through. */
    private fun isJunkPath(file: File): Boolean {
        val parts = file.absolutePath.split('/', '\\')
        return parts.any { isJunkDirName(it) || JUNK_PATH_PARTS.any { junk -> junk.equals(it, ignoreCase = true) } }
    }

    private fun isAudioFile(file: File): Boolean {
        val ext = file.extension.lowercase()
        if (ext in AUDIO_EXTS) return true
        val name = file.name.lowercase()
        return AUDIO_EXTS.any { name.endsWith(".$it") }
    }

    private fun indexFileFallback(file: File, nowMs: Long) {
        val ext = file.extension.lowercase()
        val artist = "Unknown Artist"
        val album = file.parentFile?.name?.takeIf { it.isNotBlank() } ?: "Unknown Album"
        val title = file.nameWithoutExtension
        val existing = repo.trackByPath(file.absolutePath)
        val artistId = stableId("artist", artist)
        val albumId = stableId("album", file.parentFile.canonicalPath, album)
        val trackId = stableId("track", file.absolutePath)
        val folderArt = findFolderArt(file.parentFile)?.absolutePath
        val coverPath = folderArt ?: if (ext == "opus") "opus-lazy://${file.absolutePath}" else null

        repo.inTransaction {
            upsertArtist(artistId, artist)
            upsertAlbum(
                id = albumId,
                title = album,
                artistId = artistId,
                artistName = artist,
                releaseYear = null,
                coverPath = coverPath,
                addedAtMs = existing?.added_at_ms ?: nowMs,
            )
            upsertTrack(
                id = trackId,
                title = title,
                albumId = albumId,
                artistId = artistId,
                artistName = artist,
                albumTitle = album,
                durationSec = null,
                filePath = file.absolutePath,
                trackNumber = null,
                coverPath = coverPath,
                lyricsPath = File(file.parentFile, file.nameWithoutExtension + ".lrc")
                    .takeIf { it.isFile }?.absolutePath,
                embeddedLyrics = null,
                bitrate = null,
                sampleRate = null,
                bitDepth = null,
                isLossless = ext in LOSSLESS_EXTS,
                addedAtMs = existing?.added_at_ms ?: nowMs,
                lastPlayedMs = existing?.last_played_ms,
                playCount = existing?.play_count ?: 0L,
            )
        }
    }

    fun clearCache() {
        check(scanMutex.tryLock()) { "Wait for the scan to finish before clearing the library" }
        try {
            repo.clearAll()
            DatabaseFactory.coversDir().listFiles()?.forEach { it.delete() }
            _progress.value = LocalScanProgress(message = "Local index cleared")
        } finally {
            scanMutex.unlock()
        }
    }

    /** Upsert a track whose tags (and cover) were already extracted by the native helper. */
    private fun indexNative(file: File, t: NativeTrack, nowMs: Long) {
        val title = t.title ?: file.nameWithoutExtension
        val artist = t.artist ?: "Unknown Artist"
        val album = t.album ?: file.parentFile.name
        val albumArtist = t.album_artist ?: artist
        val artistId = stableId("artist", artist)
        val albumArtistId = stableId("artist", albumArtist)
        val albumId = stableId("album", file.parentFile.canonicalPath, album)
        val trackId = stableId("track", file.absolutePath)
        val existing = repo.trackByPath(file.absolutePath)
        val credits = (t.artists.ifEmpty { listOf(artist) }).map { stableId("artist", it) to it }
        repo.inTransaction {
            upsertArtist(artistId, artist)
            upsertArtist(albumArtistId, albumArtist)
            upsertAlbum(
                id = albumId,
                title = album,
                artistId = albumArtistId,
                artistName = albumArtist,
                releaseYear = t.year,
                coverPath = t.cover_path,
                addedAtMs = existing?.added_at_ms ?: nowMs,
            )
            upsertTrack(
                id = trackId,
                title = title,
                albumId = albumId,
                artistId = artistId,
                artistName = artist,
                albumTitle = album,
                durationSec = t.duration_sec,
                filePath = file.absolutePath,
                trackNumber = t.track_no,
                coverPath = t.cover_path,
                lyricsPath = t.lrc_path,
                embeddedLyrics = t.lyrics,
                bitrate = t.bitrate,
                sampleRate = t.sample_rate,
                bitDepth = t.bit_depth,
                isLossless = t.lossless,
                addedAtMs = existing?.added_at_ms ?: nowMs,
                lastPlayedMs = existing?.last_played_ms,
                playCount = existing?.play_count ?: 0L,
            )
            setTrackExtra(trackId, t.album_artist, t.genre, t.composer, t.year, t.disc_no, t.size, t.mtime_ms)
            setTrackArtists(trackId, credits)
        }
    }

    private fun indexFile(file: File, nowMs: Long, coversRoot: File) {
        val audio = AudioFileIO.read(file)
        val tag = audio.tag
        val header = audio.audioHeader
        val title = tag?.getFirst(FieldKey.TITLE)?.takeIf { it.isNotBlank() }
            ?: file.nameWithoutExtension
        val artist = tag?.getFirst(FieldKey.ARTIST)?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
        val album = tag?.getFirst(FieldKey.ALBUM)?.takeIf { it.isNotBlank() } ?: file.parentFile.name
        val year = tag?.getFirst(FieldKey.YEAR)?.take(4)?.toLongOrNull()
        val trackNo = tag?.getFirst(FieldKey.TRACK)?.substringBefore('/')?.toLongOrNull()
        val duration = header?.trackLength?.toLong()?.takeIf { it > 0 }
        val bitrate = header?.bitRateAsNumber?.toLong()?.takeIf { it > 0 }
        val sampleRate = header?.sampleRateAsNumber?.toLong()?.takeIf { it > 0 }
        val bitDepth = runCatching { header?.bitsPerSample?.toLong() }.getOrNull()?.takeIf { it > 0 }
        val format = header?.format?.lowercase().orEmpty()
        val ext = file.extension.lowercase()
        val lossless = ext in LOSSLESS_EXTS ||
            format.contains("flac") ||
            format.contains("alac") ||
            format.contains("wav") ||
            format.contains("aiff")
        val genre = tag?.getFirst(FieldKey.GENRE)?.takeIf { it.isNotBlank() }
        val composer = tag?.getFirst(FieldKey.COMPOSER)?.takeIf { it.isNotBlank() }
        val discNo = tag?.getFirst(FieldKey.DISC_NO)?.substringBefore('/')?.toLongOrNull()
        val lyricsEmbedded = tag?.getFirst(FieldKey.LYRICS)?.takeIf { it.isNotBlank() }
        val lrcSibling = File(file.parentFile, file.nameWithoutExtension + ".lrc")
            .takeIf { it.isFile }

        val albumArtist = tag?.getFirst(FieldKey.ALBUM_ARTIST)?.takeIf { it.isNotBlank() } ?: artist
        val artistId = stableId("artist", artist)
        val albumArtistId = stableId("artist", albumArtist)
        val albumId = stableId("album", file.parentFile.canonicalPath, album)
        val trackId = stableId("track", file.absolutePath)

        val existing = repo.trackByPath(file.absolutePath)
        val coverPath = extractOrFindCover(
            file = file,
            coversRoot = coversRoot,
            tagArtwork = tag?.firstArtwork?.binaryData,
        )

        // Hold the write lock only for the upserts — not for tag/cover I/O.
        repo.inTransaction {
            upsertArtist(artistId, artist)
            upsertArtist(albumArtistId, albumArtist)
            upsertAlbum(
                id = albumId,
                title = album,
                artistId = albumArtistId,
                artistName = albumArtist,
                releaseYear = year,
                coverPath = coverPath,
                addedAtMs = existing?.added_at_ms ?: nowMs,
            )
            upsertTrack(
                id = trackId,
                title = title,
                albumId = albumId,
                artistId = artistId,
                artistName = artist,
                albumTitle = album,
                durationSec = duration,
                filePath = file.absolutePath,
                trackNumber = trackNo,
                coverPath = coverPath,
                lyricsPath = lrcSibling?.absolutePath,
                embeddedLyrics = lyricsEmbedded,
                bitrate = bitrate,
                sampleRate = sampleRate,
                bitDepth = bitDepth,
                isLossless = lossless,
                addedAtMs = existing?.added_at_ms ?: nowMs,
                lastPlayedMs = existing?.last_played_ms,
                playCount = existing?.play_count ?: 0L,
            )
            setTrackExtra(trackId, albumArtist, genre, composer, year, discNo, file.length(), file.lastModified())
            setTrackArtists(trackId, listOf(artistId to artist))
        }
    }

    private fun extractOrFindCover(
        file: File,
        coversRoot: File,
        tagArtwork: ByteArray?,
    ): String? {
        if (tagArtwork != null && tagArtwork.isNotEmpty()) {
            val md = MessageDigest.getInstance("SHA-1")
            val hash = md.digest(tagArtwork).joinToString("") { "%02x".format(it) }.take(24)
            val out = File(coversRoot, "$hash.jpg")
            if (!out.exists() || out.length() != tagArtwork.size.toLong()) {
                out.writeBytes(tagArtwork)
            }
            return out.absolutePath
        }
        val folderArt = findFolderArt(file.parentFile)
        if (folderArt != null) {
            return folderArt.absolutePath
        }
        if (file.extension.equals("opus", ignoreCase = true)) {
            return "opus-lazy://${file.absolutePath}"
        }
        return null
    }

    /** Folder artwork, matched case-insensitively (cover/folder/front/album/albumart…, jpg/jpeg/png/webp). */
    private fun findFolderArt(dir: File?): File? {
        val files = dir?.listFiles() ?: return null
        val names = listOf("cover", "folder", "front", "album", "albumart", "albumartsmall", "artwork", "thumb")
        val exts = listOf("jpg", "jpeg", "png", "webp")
        return files
            .filter { it.isFile && it.extension.lowercase() in exts && it.nameWithoutExtension.lowercase().removePrefix(".") in names }
            .minByOrNull { names.indexOf(it.nameWithoutExtension.lowercase().removePrefix(".")) }
    }

    companion object {
        /** Common consumer audio types (lossy + lossless). */
        val AUDIO_EXTS = setOf(
            // lossy
            "mp3", "mp2", "aac", "m4a", "m4b", "ogg", "oga", "opus", "wma", "mp4",
            // lossless / hi-res
            "flac", "wav", "aiff", "aif", "alac", "ape", "wv", "tak", "dsf", "dff", "caf",
        )
        private val LOSSLESS_EXTS = setOf(
            "flac", "wav", "aiff", "aif", "alac", "ape", "wv", "tak", "dsf", "dff", "caf",
        )
        /** Don't descend into these (still scan normal Artist/Album subfolders). */
        private val SKIP_DIR_NAMES = setOf(
            ".git", ".svn", ".hg", "node_modules", "__macosx", ".trash", ".trashes",
            "\$recycle.bin", "system volume information",
            "venv", ".venv", "env", "__pycache__", "site-packages", "dist-packages",
        )
        /** Extra path segments that must never be indexed as music. */
        private val JUNK_PATH_PARTS = setOf(
            "site-packages", "dist-packages", "__pycache__", "venv", ".venv",
            "node_modules", "test-data", "testdata", "fixtures",
        )
        /** Skip tiny / invalid stubs (e.g. scipy unit-test wavs). */
        private const val MIN_AUDIO_BYTES = 8_192L

        fun stableId(vararg parts: String): String {
            val md = MessageDigest.getInstance("SHA-1")
            val digest = md.digest(parts.joinToString("\u0000").toByteArray())
            return digest.joinToString("") { "%02x".format(it) }.take(24)
        }
    }
}
