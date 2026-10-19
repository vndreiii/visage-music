package com.music.vivi.compose.ui.tags

import com.music.vivi.compose.ui.artist.ArtistNames
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** What we know about a track when looking it up. */
data class TagQuery(
    val title: String,
    val artist: String = "",
    val album: String = "",
    val durationSec: Long? = null,
)

/** One proposed set of tags from a source. */
data class TagCandidate(
    val source: String,
    val tags: Tags,
    val coverUrl: String? = null,
    val durationSec: Long? = null,
    /** Source-provided strength 0..1 (MusicBrainz / AcoustID), if any. */
    val sourceScore: Double? = null,
)

data class ScoredCandidate(val candidate: TagCandidate, val score: Double) {
    val high: Boolean get() = score >= TagScoring.HIGH
}

object TagScoring {
    const val HIGH = 0.85
    const val MIN_SHOWN = 0.45

    private val noise = Regex(
        """\s*[\[(][^\])]*(?:feat|ft|featuring|remaster|remastered|version|edit|mix|live|official|explicit|deluxe|bonus)[^\])]*[\])]|\s+-\s+(?:remaster(?:ed)?(?:\s+\d{4})?|single version|radio edit)\s*$""",
        RegexOption.IGNORE_CASE,
    )

    /** Lower-case alphanumeric form with feat./remaster noise removed. */
    fun canon(s: String): String = ArtistNames.normalize(s.replace(noise, ""))

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    /** 0..1 string similarity: best of edit-distance ratio and token overlap (so word order doesn't hurt). */
    fun similarity(a: String, b: String): Double {
        val x = canon(a)
        val y = canon(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        if (x == y) return 1.0
        val edit = 1.0 - levenshtein(x, y).toDouble() / max(x.length, y.length)
        val tx = x.split(' ').toSet()
        val ty = y.split(' ').toSet()
        val jaccard = tx.intersect(ty).size.toDouble() / tx.union(ty).size
        return max(edit, jaccard)
    }

    /** Artist similarity that accepts partial credit lists ("A & B" vs "A"). */
    fun artistSimilarity(query: String, candidate: String): Double {
        if (query.isBlank() || candidate.isBlank()) return 0.0
        val whole = similarity(query, candidate)
        val q = ArtistNames.split(query)
        val c = ArtistNames.split(candidate)
        val shared = q.count { a -> c.any { b -> similarity(a, b) >= 0.9 } }
        val part = if (q.isEmpty() || c.isEmpty()) 0.0 else 0.9 * shared / max(q.size, c.size).toDouble() + 0.1 * (if (shared > 0) 1 else 0)
        return max(whole, part)
    }

    fun durationScore(a: Long?, b: Long?): Double? {
        if (a == null || b == null || a <= 0 || b <= 0) return null
        val d = abs(a - b)
        return when {
            d <= 2 -> 1.0
            d <= 5 -> 0.8
            d <= 10 -> 0.5
            d <= 20 -> 0.2
            else -> 0.0
        }
    }

    /** Confidence 0..1 that [c] describes the same recording as [q]. */
    fun score(q: TagQuery, c: TagCandidate): Double {
        val title = similarity(q.title, c.tags.value(TagField.Title))
        var sum = 0.5 * title
        var weight = 0.5
        if (q.artist.isNotBlank() && !TagHealth.isUnknownArtist(q.artist)) {
            sum += 0.3 * artistSimilarity(q.artist, c.tags.value(TagField.Artist)); weight += 0.3
        }
        if (q.album.isNotBlank() && c.tags.value(TagField.Album).isNotBlank()) {
            sum += 0.1 * similarity(q.album, c.tags.value(TagField.Album)); weight += 0.1
        }
        durationScore(q.durationSec, c.durationSec)?.let { sum += 0.1 * it; weight += 0.1 }
        var s = sum / weight
        // A wrong title never gets rescued by a matching artist.
        if (title < 0.5) s = min(s, 0.4 + title * 0.2)
        c.sourceScore?.let { src ->
            s = if (c.source == "acoustid") max(s, 0.6 * s + 0.4 * src * (if (title >= 0.6 || q.title.isBlank()) 1.0 else 0.5)) else 0.8 * s + 0.2 * src
        }
        // Less information in the candidate means less trust.
        if (c.tags.value(TagField.Artist).isBlank()) s *= 0.8
        return s.coerceIn(0.0, 1.0)
    }

    /** Candidates scored, merged with equal tags from several sources (small bonus), best first. */
    fun rank(q: TagQuery, candidates: List<TagCandidate>, limit: Int = 5): List<ScoredCandidate> {
        val scored = candidates.map { ScoredCandidate(it, score(q, it)) }
        val boosted = scored.map { sc ->
            val agree = scored.count { o ->
                o !== sc && o.candidate.source != sc.candidate.source &&
                    similarity(o.candidate.tags.value(TagField.Title), sc.candidate.tags.value(TagField.Title)) >= 0.95 &&
                    artistSimilarity(o.candidate.tags.value(TagField.Artist), sc.candidate.tags.value(TagField.Artist)) >= 0.9
            }
            sc.copy(score = (sc.score + min(agree, 2) * 0.03).coerceAtMost(1.0))
        }
        val seen = HashSet<String>()
        return boosted
            .sortedByDescending { it.score }
            .filter { it.score >= MIN_SHOWN }
            .filter { seen.add(listOf(TagField.Title, TagField.Artist, TagField.Album).joinToString("|") { f -> canon(it.candidate.tags.value(f)) }) }
            .take(limit)
    }
}
