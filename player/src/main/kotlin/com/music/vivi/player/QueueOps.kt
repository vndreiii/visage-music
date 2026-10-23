package com.music.vivi.player

/** Result of a pure queue edit: the new list and where the current song now sits. */
data class QueueEdit(val tracks: List<QueueTrack>, val currentIndex: Int)

/** Move the entry at [from] to [to] (indexes into [tracks]); the current song keeps playing. */
fun moveQueueEntry(tracks: List<QueueTrack>, currentIndex: Int, from: Int, to: Int): QueueEdit? {
    if (from !in tracks.indices || to !in tracks.indices || from == to) return null
    val list = tracks.toMutableList()
    list.add(to, list.removeAt(from))
    val cur = when {
        currentIndex == from -> to
        from < currentIndex && to >= currentIndex -> currentIndex - 1
        from > currentIndex && to <= currentIndex -> currentIndex + 1
        else -> currentIndex
    }
    return QueueEdit(list, cur)
}

/** Remove the entry at [index]; the current song itself cannot be removed this way. */
fun removeQueueEntry(tracks: List<QueueTrack>, currentIndex: Int, index: Int): QueueEdit? {
    if (index !in tracks.indices || index == currentIndex) return null
    val list = tracks.toMutableList().also { it.removeAt(index) }
    return QueueEdit(list, if (index < currentIndex) currentIndex - 1 else currentIndex)
}
