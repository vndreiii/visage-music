package com.music.vivi.compose.ui.player

import kotlin.test.Test
import kotlin.test.assertEquals

class AlbumGestureTest {
    @Test fun quickSwipesSkipButShakesAndSmallDragsDoNot() {
        assertEquals(CardReleaseAction.NEXT, cardReleaseAction(200, -90f, 72f, true, false))
        assertEquals(CardReleaseAction.NONE, cardReleaseAction(900, -90f, 72f, true, true))
        assertEquals(CardReleaseAction.NONE, cardReleaseAction(900, -30f, 72f, true, false))
        assertEquals(CardReleaseAction.NEXT, cardReleaseAction(900, -90f, 72f, true, false))
        assertEquals(CardReleaseAction.PREVIOUS, cardReleaseAction(900, 90f, 72f, true, false))
        assertEquals(CardReleaseAction.PREVIEW, cardReleaseAction(900, 0f, 72f, false, false))
    }
    @Test fun previewsFollowDirectionAndStopAtQueueEdges() {
        assertEquals(listOf(3, 2, 1), queuePreviewIndices(4, 8, true, 3))
        assertEquals(listOf(5, 6, 7), queuePreviewIndices(4, 8, false, 5))
        assertEquals(emptyList(), queuePreviewIndices(0, 8, true, 5))
        assertEquals(emptyList(), queuePreviewIndices(-1, 0, false, 3))
    }
}
