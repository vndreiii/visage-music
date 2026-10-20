package com.music.vivi.compose.ui.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class YouTubeLinkTest {
    private val id = "VzyRFyPadiw"

    @Test
    fun readsTheIdFromCommonLinkShapes() {
        assertEquals(id, youTubeVideoId("https://music.youtube.com/watch?v=$id"))
        assertEquals(id, youTubeVideoId("  https://music.youtube.com/watch?v=$id&list=RDAMVM$id  "))
        assertEquals(id, youTubeVideoId("https://www.youtube.com/watch?feature=share&v=$id"))
        assertEquals(id, youTubeVideoId("https://youtu.be/$id?t=42"))
        assertEquals(id, youTubeVideoId("https://m.youtube.com/shorts/$id"))
        assertEquals(id, youTubeVideoId("music.youtube.com/watch?v=$id"))
    }

    @Test
    fun ignoresAnythingThatIsNotAVideoLink() {
        assertNull(youTubeVideoId(""))
        assertNull(youTubeVideoId("lisa sawadika"))
        assertNull(youTubeVideoId("https://music.youtube.com/playlist?list=PLabc"))
        assertNull(youTubeVideoId("https://example.com/watch?v=$id"))
        assertNull(youTubeVideoId("https://music.youtube.com/watch?v=short"))
        assertNull(youTubeVideoId("https://music.youtube.com/channel/UCabc"))
    }
}
