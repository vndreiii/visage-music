package com.music.vivi.compose.ui.tags

/** A field's value across the selected tracks. */
sealed interface FieldState {
    data class Same(val value: String) : FieldState
    data object Mixed : FieldState
}

/**
 * Batch-edit model: [FieldState]s for the selection plus the user's [edits]. Only edited fields are
 * written; an untouched "mixed" field keeps every track's own value.
 */
data class BatchEdit(
    val states: Map<TagField, FieldState>,
    /** Field -> new value for every selected track (blank = clear). */
    val edits: Tags = emptyMap(),
) {
    fun displayValue(f: TagField): String = edits[f] ?: (states[f] as? FieldState.Same)?.value.orEmpty()
    fun isMixed(f: TagField): Boolean = f !in edits && states[f] == FieldState.Mixed
    fun isEdited(f: TagField): Boolean = f in edits

    /** Sets a field; typing back the original uniform value drops the edit. A mixed field typed to blank is an explicit clear. */
    fun set(f: TagField, value: String): BatchEdit {
        val original = states[f]
        val next = if (original is FieldState.Same && original.value == value) edits - f else edits + (f to value)
        return copy(edits = next)
    }

    fun reset(f: TagField): BatchEdit = copy(edits = edits - f)
    val dirty: Boolean get() = edits.isNotEmpty()

    /** The edit map to write for [current] tags of one track (drops no-ops against that track's own value). */
    fun editsFor(current: Tags): Tags = edits.filter { (f, v) -> current.value(f).trim() != v.trim() }

    companion object {
        fun of(tracks: List<Tags>, fields: List<TagField> = TagField.entries): BatchEdit {
            if (tracks.isEmpty()) return BatchEdit(emptyMap())
            val states = fields.associateWith { f ->
                val first = tracks.first().value(f).trim()
                if (tracks.all { it.value(f).trim() == first }) FieldState.Same(first) else FieldState.Mixed
            }
            return BatchEdit(states)
        }
    }
}
