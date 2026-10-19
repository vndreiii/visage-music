package com.music.vivi.compose.ui.tags

/** Raw answer of the network lookup. */
data class LookupResult(val candidates: List<TagCandidate>, val errors: List<String> = emptyList())

/** Network lookup behind an interface so tests (and offline use) never touch the network. */
interface TagLookup {
    suspend fun lookup(q: TagQuery, fingerprint: Fingerprint? = null): LookupResult
}

data class Fingerprint(val durationSec: Long, val fingerprint: String)

/** Builds proposals for tracks from existing tags, the file name and (optionally) a lookup. */
class AutoTagger(private val lookup: TagLookup, private val fingerprinter: (TagTrack) -> Fingerprint? = { null }) {

    /** Query from existing tags, falling back to what the file name / folders say. */
    fun queryFor(track: TagTrack): TagQuery {
        val parsed = FilenameParser.parse(track.path)
        val title = track.tags.value(TagField.Title).ifBlank { parsed.title.orEmpty() }
            .let { if (it.equals(track.stem, true)) parsed.title ?: it else it }
        val artistTag = track.tags.value(TagField.Artist)
        val artist = if (TagHealth.isUnknownArtist(artistTag)) parsed.artist.orEmpty() else artistTag
        val album = track.tags.value(TagField.Album).let { if (TagHealth.of(track).missingAlbum) parsed.album.orEmpty() else it }
        return TagQuery(title, artist, album, track.durationSec)
    }

    /** Tags implied by the file name alone (always available, no network). */
    fun fromFilename(track: TagTrack): TagCandidate? {
        val p = FilenameParser.parse(track.path)
        val tags = p.toTags()
        return if (tags.isEmpty()) null else TagCandidate("filename", tags, durationSec = track.durationSec)
    }

    suspend fun propose(track: TagTrack, useNetwork: Boolean = true): TrackProposal {
        val q = queryFor(track)
        val health = TagHealth.of(track)
        val net = if (useNetwork && (q.title.isNotBlank())) {
            runCatching { lookup.lookup(q, fingerprinter(track)) }.getOrDefault(LookupResult(emptyList()))
        } else LookupResult(emptyList())
        val ranked = TagScoring.rank(q, net.candidates).toMutableList()
        // The file name is a weak, offline source: only worth showing when it adds something and nothing better exists.
        fromFilename(track)?.let { fc ->
            val useful = TagDiff.changes(track.tags, fc.tags).any { it.fillsEmpty || health.missingTitle && it.field == TagField.Title || health.missingArtist && it.field == TagField.Artist }
            if (useful) ranked += ScoredCandidate(fc, if (ranked.isEmpty()) 0.5 else 0.46)
        }
        val options = ranked.sortedByDescending { it.score }
        return TrackProposal(
            trackId = track.id,
            current = track.tags,
            options = options,
            hasCoverNow = !track.coverPath.isNullOrBlank(),
        )
    }
}
