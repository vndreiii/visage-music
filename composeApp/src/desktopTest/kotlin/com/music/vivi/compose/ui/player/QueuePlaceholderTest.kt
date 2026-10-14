package com.music.vivi.compose.ui.player

import com.music.vivi.compose.ui.player.queue.queuePlaceholderCount
import kotlin.test.Test
import kotlin.test.assertEquals

class QueuePlaceholderTest {
    @Test fun shrinkingQueueBeforeRevealStateCatchesUpHasNoNegativeItems() {
        assertEquals(0, queuePlaceholderCount(2, 12, 5))
        assertEquals(0, queuePlaceholderCount(0, 12, 5))
        assertEquals(5, queuePlaceholderCount(12, 2, 5))
        assertEquals(2, queuePlaceholderCount(4, 2, 5))
    }
}
