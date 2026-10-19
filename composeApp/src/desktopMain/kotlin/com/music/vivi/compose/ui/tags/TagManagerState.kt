package com.music.vivi.compose.ui.tags

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.PrefKeys
import com.music.vivi.database.LocalMusicRepository
import com.music.vivi.database.Local_track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Maps a library row to the Tag Manager's view: scanner fallbacks (file name, "Unknown Artist", folder album) become blanks. */
fun Local_track.toTagTrack(checkWritable: Boolean = true): TagTrack {
    val f = File(file_path)
    val raw = buildMap<TagField, String> {
        put(TagField.Title, title)
        put(TagField.Artist, artist_name)
        album_title?.let { put(TagField.Album, it) }
        album_artist?.let { put(TagField.AlbumArtist, it) }
        year?.let { put(TagField.Year, it.toString()) }
        genre?.let { put(TagField.Genre, it) }
        track_number?.let { put(TagField.TrackNo, it.toString()) }
        disc_number?.let { put(TagField.DiscNo, it.toString()) }
        composer?.let { put(TagField.Composer, it) }
        embedded_lyrics?.let { put(TagField.Lyrics, it) }
    }
    val probe = TagTrack(id, file_path, raw, cover_path, duration_sec)
    val h = TagHealth.of(probe)
    val tags = raw.toMutableMap()
    if (h.missingTitle) tags.remove(TagField.Title)
    if (h.missingArtist) tags.remove(TagField.Artist)
    if (h.missingAlbum && tags.value(TagField.Album).equals(f.parentFile?.name.orEmpty(), true)) tags.remove(TagField.Album)
    return probe.copy(tags = tags.cleaned(), readonly = checkWritable && !f.canWrite())
}

fun Tags.toUpdate(coverPath: String?, size: Long, mtime: Long) = LocalMusicRepository.TagUpdate(
    title = this[TagField.Title], artist = this[TagField.Artist], album = this[TagField.Album], albumArtist = this[TagField.AlbumArtist],
    genre = this[TagField.Genre], composer = this[TagField.Composer], year = this[TagField.Year]?.toLongOrNull(),
    trackNo = this[TagField.TrackNo]?.toLongOrNull(), discNo = this[TagField.DiscNo]?.toLongOrNull(), lyrics = this[TagField.Lyrics],
    coverPath = coverPath, fileSize = size, fileMtimeMs = mtime,
)

/** Keeps the library database and the Library screen in step with the files. */
class DbLibrarySink(
    private val repo: LocalMusicRepository,
    private val onTrackSynced: (String, TagReadResult) -> Unit,
    private val refreshLibrary: suspend () -> Unit,
) : LibrarySink {
    override fun synced(trackId: String, path: String, read: TagReadResult, size: Long, mtimeMs: Long) {
        runCatching { repo.applyTags(trackId, read.tags.toUpdate(read.coverPath, size, mtimeMs)) }
        onTrackSynced(trackId, read)
    }

    override suspend fun refresh() = refreshLibrary()
}

enum class PanelMode(val label: String) { Edit("Edit"), Tools("Tools") }
enum class ToolKind(val label: String) { FromFilename("Tags from file name"), Normalise("Normalise case"), Covers("Fetch missing covers"), Duplicates("Find duplicates") }

sealed interface CoverAction {
    data object Keep : CoverAction
    data class Replace(val file: File) : CoverAction
    data object Remove : CoverAction
}

/** Everything the Tag Manager page does, as plain state so the composables stay thin. */
class TagManagerState(
    private val scope: CoroutineScope,
    private val repo: LocalMusicRepository,
    private val prefs: DesktopPrefs,
    val writer: TagWriter,
    lookup: TagLookup,
    backupRoot: File,
    private val refreshLibrary: suspend () -> Unit,
) {
    var tracks by mutableStateOf(emptyList<TagTrack>())
    var loading by mutableStateOf(true)

    var filter by mutableStateOf(TagFilter.All)
    var query by mutableStateOf("")
    var grouping by mutableStateOf(TagGrouping.None)
    var selection by mutableStateOf(TagSelection())
    var modified by mutableStateOf(emptySet<String>())

    var panel by mutableStateOf(PanelMode.Edit)

    // editor
    var reads by mutableStateOf(emptyList<TagReadResult>())
    var batch by mutableStateOf(BatchEdit(emptyMap()))
    var coverAction by mutableStateOf<CoverAction>(CoverAction.Keep)
    var editorLoading by mutableStateOf(false)
    var coverMessage by mutableStateOf<String?>(null)
    private var editorJob: Job? = null

    // auto-tag review
    var proposals by mutableStateOf<List<TrackProposal>?>(null)
    var autoRunning by mutableStateOf(false)
    var autoDone by mutableStateOf(0)
    var autoTotal by mutableStateOf(0)
    var autoNote by mutableStateOf<String?>(null)
    private var autoJob: Job? = null
    private val tagger = AutoTagger(lookup) { t -> if (Fingerprinter.available && !prefs.getString(PrefKeys.TagAcoustidKey, "").isBlank()) Fingerprinter.fingerprint(t.path) else null }

    // writing
    var busy by mutableStateOf(false)
    var progressDone by mutableStateOf(0)
    var progressTotal by mutableStateOf(0)
    var report by mutableStateOf<BatchReport?>(null)

    // tools
    var tool by mutableStateOf(ToolKind.FromFilename)
    var filenamePattern by mutableStateOf("%artist% - %title%")
    var duplicateGroups by mutableStateOf(emptyList<List<TagTrack>>())

    private val sink = DbLibrarySink(repo, ::applyRead) { refreshLibrary(); reloadQuiet() }
    val service = TagBatchService(writer, backupRoot, sink)
    val lastBatch: StateFlow<BatchManifest?> get() = service.lastBatch

    val backupsOn: Boolean get() = prefs.getBoolean(PrefKeys.TagBackupEnabled, true)
    val id3v23: Boolean get() = prefs.getBoolean(PrefKeys.TagId3v23, false)

    val byId: Map<String, TagTrack> get() = tracks.associateBy { it.id }
    val selectedTracks: List<TagTrack> get() = selection.ids.let { ids -> tracks.filter { it.id in ids } }
    val missingCount: Int get() = tracks.count { TagHealth.of(it).any }

    fun load() {
        scope.launch {
            loading = true
            tracks = withContext(Dispatchers.IO) { repo.allTracks().map { it.toTagTrack() } }
            selection = selection.retain(tracks.map { it.id })
            loading = false
        }
    }

    private fun applyRead(id: String, @Suppress("UNUSED_PARAMETER") r: TagReadResult) {
        modified = modified + id
    }

    private suspend fun reloadQuiet() {
        val fresh = withContext(Dispatchers.IO) { repo.allTracks().map { it.toTagTrack() } }
        tracks = fresh
    }

    // ── selection / editor ───────────────────────────────────────────────────────────────

    fun select(newSel: TagSelection) {
        selection = newSel
        loadEditor()
    }

    fun loadEditor() {
        editorJob?.cancel()
        coverAction = CoverAction.Keep
        coverMessage = null
        val sel = selectedTracks
        if (sel.isEmpty()) {
            reads = emptyList(); batch = BatchEdit(emptyMap()); editorLoading = false
            return
        }
        // Instant first paint from the library row; the file's real tags replace it a moment later.
        batch = BatchEdit.of(sel.map { it.tags })
        editorLoading = true
        editorJob = scope.launch {
            val real = withContext(Dispatchers.IO) { sel.take(MAX_READ).mapNotNull { t -> if (!isActive) null else writer.read(t.path) } }
            if (isActive) {
                reads = real
                if (real.isNotEmpty()) batch = BatchEdit.of(real.map { it.tags })
                editorLoading = false
            }
        }
    }

    fun setField(f: TagField, v: String) { batch = batch.set(f, v) }
    fun resetField(f: TagField) { batch = batch.reset(f) }
    fun resetEditor() { batch = batch.copy(edits = emptyMap()); coverAction = CoverAction.Keep; coverMessage = null }

    val editorDirty: Boolean get() = batch.dirty || coverAction != CoverAction.Keep

    fun chooseCoverFile(file: File) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { file.isFile && file.length() in 1..MAX_COVER && CoverFetcher.looksLikeImage(file.inputStream().use { it.readNBytes(32) }) }
            if (ok) { coverAction = CoverAction.Replace(file); coverMessage = null } else coverMessage = "That file isn't a usable JPEG/PNG image."
        }
    }

    fun chooseCoverUrl(url: String) {
        scope.launch {
            coverMessage = "Downloading…"
            val f = withContext(Dispatchers.IO) { CoverFetcher.download(url) }
            if (f != null) { coverAction = CoverAction.Replace(f); coverMessage = null } else coverMessage = "Couldn't download an image from that link."
        }
    }

    fun removeCover() { coverAction = CoverAction.Remove; coverMessage = null }
    fun keepCover() { coverAction = CoverAction.Keep }

    fun applyEditor() {
        val sel = selectedTracks
        val ro = sel.filter { it.readonly }
        val currentReads = reads.associateBy { it.path }
        val items = sel.map { t ->
            val current = currentReads[t.path]?.tags ?: t.tags
            BatchItem(
                trackId = t.id, path = t.path, edits = batch.editsFor(current),
                coverFile = (coverAction as? CoverAction.Replace)?.file?.absolutePath,
                removeCover = coverAction == CoverAction.Remove,
            )
        }
        runBatch(items, note = if (ro.isNotEmpty()) "${ro.size} read-only file(s) will be reported, not changed." else null) { loadEditor() }
    }

    // ── batch writing ────────────────────────────────────────────────────────────────────

    fun runBatch(items: List<BatchItem>, note: String? = null, after: () -> Unit = {}) {
        if (busy) return
        val work = items.filterNot { it.isNoop }
        if (work.isEmpty()) { report = BatchReport(emptyList()); return }
        busy = true; progressDone = 0; progressTotal = work.size; report = null
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                service.apply(work, keepBackups = backupsOn, id3v23 = id3v23) { d, t -> progressDone = d; progressTotal = t }
            }
            report = r
            busy = false
            after()
        }
    }

    fun undo() {
        if (busy) return
        busy = true; progressDone = 0; progressTotal = service.lastBatch.value?.entries?.size ?: 0
        scope.launch {
            val r = withContext(Dispatchers.IO) { service.undoLast { d, t -> progressDone = d; progressTotal = t } }
            report = r
            busy = false
            loadEditor()
        }
    }

    fun dismissReport() { report = null }

    // ── auto-tag ─────────────────────────────────────────────────────────────────────────

    fun startAutoTag(targets: List<TagTrack> = selectedTracks, coversOnly: Boolean = false, network: Boolean = true) {
        if (autoRunning || targets.isEmpty()) return
        autoRunning = true; autoDone = 0; autoTotal = targets.size; autoNote = null
        proposals = emptyList()
        autoJob = scope.launch {
            var netFailures = 0
            for (t in targets) {
                if (!isActive) break
                val p = withContext(Dispatchers.IO) {
                    val real = writer.read(t.path)
                    val track = if (real != null) t.copy(tags = real.tags, coverPath = real.coverPath ?: (if (real.hasCover) "embedded" else null)) else t
                    runCatching { tagger.propose(track, network) }.getOrNull()
                }
                if (p != null) {
                    if (p.options.isEmpty()) { /* nothing found: leave out of the review */ } else {
                        val adj = if (coversOnly) p.copy(overrides = TagField.entries.associateWith { false }, coverChoice = p.best?.candidate?.coverUrl != null && !p.hasCoverNow) else p
                        proposals = proposals.orEmpty() + adj
                    }
                } else netFailures++
                autoDone++
            }
            autoRunning = false
            if (proposals.isNullOrEmpty()) autoNote = "No matches found for the selected tracks."
            else if (netFailures > 0) autoNote = "$netFailures track(s) failed to look up."
        }
    }

    fun cancelAutoTag() { autoJob?.cancel(); autoRunning = false }
    fun closeReview() { cancelAutoTag(); proposals = null; autoNote = null }

    fun updateProposal(id: String, f: (TrackProposal) -> TrackProposal) {
        proposals = proposals?.map { if (it.trackId == id) f(it) else it }
    }

    fun acceptAllHigh() { proposals = proposals?.let(TagDiff::acceptAllHigh) }

    val proposalEditCount: Int get() = proposals.orEmpty().count { !it.skipped && (it.edits().isNotEmpty() || it.acceptsCover()) }

    fun applyProposals() {
        val list = proposals.orEmpty().filter { !it.skipped }
        if (list.isEmpty() || busy) return
        busy = true; progressDone = 0; progressTotal = list.size; report = null
        scope.launch {
            val items = withContext(Dispatchers.IO) {
                list.mapNotNull { p ->
                    val t = byId[p.trackId] ?: return@mapNotNull null
                    val cover = if (p.acceptsCover()) p.best?.candidate?.coverUrl?.let { CoverFetcher.download(it) } else null
                    BatchItem(t.id, t.path, p.edits(), coverFile = cover?.absolutePath)
                }
            }
            busy = false
            runBatch(items) { proposals = null }
        }
    }

    // ── tools ────────────────────────────────────────────────────────────────────────────

    fun toolTargets(): List<TagTrack> = selectedTracks.ifEmpty { tracks }

    /** Preview of "tags from file name" for each target: only fields that would change. */
    fun filenamePreview(): List<Pair<TagTrack, Tags>> =
        toolTargets().mapNotNull { t ->
            val parsed = FilenameParser.parsePattern(filenamePattern, t.stem) ?: return@mapNotNull null
            val edits = parsed.filter { (f, v) -> t.tags.value(f).trim() != v }
            if (edits.isEmpty()) null else t to edits
        }

    fun applyFilenameTags() {
        runBatch(filenamePreview().map { (t, e) -> BatchItem(t.id, t.path, e) })
    }

    fun normalisePreview(): List<Pair<TagTrack, Tags>> =
        toolTargets().mapNotNull { t -> CaseNormalizer.fix(t.tags).takeIf { it.isNotEmpty() }?.let { t to it } }

    fun applyNormalise() { runBatch(normalisePreview().map { (t, e) -> BatchItem(t.id, t.path, e) }) }

    fun findDuplicates() { duplicateGroups = TagBrowser.duplicates(tracks) }

    fun selectIds(ids: Collection<String>) = select(TagSelection(ids.toSet(), ids.firstOrNull()))

    companion object {
        const val MAX_READ = 300
        const val MAX_COVER = 15L * 1024 * 1024
    }
}
