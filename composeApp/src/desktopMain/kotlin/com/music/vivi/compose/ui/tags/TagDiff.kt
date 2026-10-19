package com.music.vivi.compose.ui.tags

/** One field's current -> proposed value. */
data class FieldChange(val field: TagField, val old: String, val new: String) {
    val fillsEmpty: Boolean get() = old.isBlank()
    /** Differs only by letter case / spacing. */
    val cosmetic: Boolean get() = !fillsEmpty && old.equals(new, ignoreCase = true)
}

/** Proposal for one track: the best candidates and which changes the user (or the defaults) accepted. */
data class TrackProposal(
    val trackId: String,
    val current: Tags,
    val options: List<ScoredCandidate>,
    val chosen: Int = 0,
    /** Fields the user switched off / on, overriding the defaults. */
    val overrides: Map<TagField, Boolean> = emptyMap(),
    val coverChoice: Boolean? = null,
    val hasCoverNow: Boolean = false,
    val skipped: Boolean = false,
) {
    val best: ScoredCandidate? get() = options.getOrNull(chosen)
    val changes: List<FieldChange> get() = best?.let { TagDiff.changes(current, it.candidate.tags) }.orEmpty()
    val proposesCover: Boolean get() = best?.candidate?.coverUrl != null && (!hasCoverNow || (best?.high == true))

    fun accepted(change: FieldChange): Boolean =
        overrides[change.field] ?: TagDiff.defaultAccept(change, best?.score ?: 0.0)

    fun acceptsCover(): Boolean = coverChoice ?: (best?.candidate?.coverUrl != null && (!hasCoverNow))

    /** The edit map to write: accepted fields only. Empty when skipped. */
    fun edits(): Tags = if (skipped) emptyMap() else changes.filter(::accepted).associate { it.field to it.new }
}

object TagDiff {
    /** Changes needed to turn [current] into [proposed]; fields the proposal doesn't know are never touched. */
    fun changes(current: Tags, proposed: Tags): List<FieldChange> =
        TagField.entries.mapNotNull { f ->
            val new = proposed.value(f).trim()
            val old = current.value(f).trim().let { if (f == TagField.Artist && TagHealth.isUnknownArtist(it)) "" else it }
            if (new.isEmpty() || new == old) null else FieldChange(f, old, new)
        }

    /**
     * Filling an empty field is always on; overwriting an existing value is on only for
     * high-confidence matches, and never for cosmetic-only differences (so we don't undo the user's casing).
     */
    fun defaultAccept(c: FieldChange, score: Double): Boolean = when {
        c.fillsEmpty -> score >= TagScoring.MIN_SHOWN
        c.cosmetic -> false
        else -> score >= TagScoring.HIGH
    }

    fun acceptAllHigh(proposals: List<TrackProposal>): List<TrackProposal> =
        proposals.map { p -> if (p.best?.high == true) p.copy(skipped = false, overrides = p.changes.associate { it.field to true }, coverChoice = p.best?.candidate?.coverUrl != null) else p }
}
