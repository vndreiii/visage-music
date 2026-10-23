package com.music.vivi.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QueueOpsTest {
    private val q = listOf("a", "b", "c", "d", "e").map { QueueTrack(it) }
    private fun ids(e: QueueEdit?) = e!!.tracks.joinToString("") { it.videoId }

    @Test fun moveUpcomingAroundCurrent() {
        val e = moveQueueEntry(q, 1, 4, 2)
        assertEquals("abecd", ids(e)); assertEquals(1, e!!.currentIndex)
    }

    @Test fun moveAcrossCurrentShiftsIt() {
        assertEquals(0, moveQueueEntry(q, 1, 0, 4)!!.currentIndex)
        assertEquals(2, moveQueueEntry(q, 1, 4, 0)!!.currentIndex)
    }

    @Test fun moveCurrentFollowsIt() {
        assertEquals(3, moveQueueEntry(q, 1, 1, 3)!!.currentIndex)
    }

    @Test fun invalidMovesAreNoOps() {
        assertNull(moveQueueEntry(q, 0, 2, 2))
        assertNull(moveQueueEntry(q, 0, -1, 2))
        assertNull(moveQueueEntry(q, 0, 1, 9))
    }

    @Test fun removeAdjustsCurrent() {
        assertEquals(1, removeQueueEntry(q, 2, 0)!!.currentIndex)
        assertEquals(2, removeQueueEntry(q, 2, 4)!!.currentIndex)
        assertNull(removeQueueEntry(q, 2, 2))
    }
}
