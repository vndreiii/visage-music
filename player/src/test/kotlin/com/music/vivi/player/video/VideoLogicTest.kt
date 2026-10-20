package com.music.vivi.player.video

import com.music.innertube.pages.MusicVideoTypes
import com.music.innertube.VideoStreamCandidate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VideoLogicTest {
    @Test fun fallbackChain() {
        assertEquals(VisualSource.VIDEO, VisualChain.resolve(true, true, true))
        assertEquals(VisualSource.CANVAS, VisualChain.resolve(true, false, true))
        assertEquals(VisualSource.CANVAS, VisualChain.resolve(false, true, true))
        assertEquals(VisualSource.COVER, VisualChain.resolve(true, false, false))
        assertEquals(VisualSource.COVER, VisualChain.resolve(false, true, false))
    }

    @Test fun localTracksHaveNoVideo() {
        assertTrue(VisualChain.hasVideoCandidate("abc", null))
        assertEquals(false, VisualChain.hasVideoCandidate("local:1", null))
        assertEquals(false, VisualChain.hasVideoCandidate("abc", "/x.mp3"))
        assertEquals(false, VisualChain.hasVideoCandidate(null, null))
    }

    @Test fun driftDecisions() {
        assertEquals(SyncAction.None, VideoSync.decide(10.0, 10.1, true).action)
        assertEquals(SyncAction.Seek(10.0), VideoSync.decide(10.0, 10.4, true).action)
        assertEquals(SyncAction.Seek(5.0), VideoSync.decide(5.0, 1.0, true).action)
        assertEquals(SyncAction.None, VideoSync.decide(10.0, 10.4, false).action)
        assertTrue(VideoSync.decide(10.0, 10.0, false).videoPaused)
        assertEquals(SyncAction.None, VideoSync.decide(3.0, null, true).action)
        assertEquals(SyncAction.Seek(9.9), VideoSync.decide(12.0, 1.0, true, durationSec = 10.0).action)
    }

    private fun c(h: Int, codec: String) = VideoStreamCandidate("u$h$codec", h, codec, true, "mp4")

    @Test fun picksBestUnderCap() {
        val list = listOf(c(360, "avc1"), c(720, "av01"), c(720, "avc1.4d"), c(1080, "avc1"))
        assertEquals("u720avc1.4d", pickVideoCandidate(list, VideoQuality.P720)?.url)
        assertEquals("u360avc1", pickVideoCandidate(list, VideoQuality.P480)?.url)
        assertEquals("u1080avc1", pickVideoCandidate(list, VideoQuality.P1080)?.url)
        assertEquals("u1080avc1", pickVideoCandidate(listOf(c(1080, "avc1")), VideoQuality.P360)?.url)
        assertNull(pickVideoCandidate(emptyList(), VideoQuality.P720))
    }

    @Test fun videoIdSelection() {
        assertEquals("a", MusicVideoTypes.videoIdFor("a", MusicVideoTypes.OMV, null))
        assertEquals("v", MusicVideoTypes.videoIdFor("a", MusicVideoTypes.ATV, "v"))
        assertNull(MusicVideoTypes.videoIdFor("a", MusicVideoTypes.ATV, null))
    }

    @Test
    fun autoQualityFollowsMeasuredSpeed() {
        assertEquals(VideoQuality.P360, VideoQuality.forMbps(1.0))
        assertEquals(VideoQuality.P480, VideoQuality.forMbps(3.0))
        assertEquals(VideoQuality.P720, VideoQuality.forMbps(6.0))
        assertEquals(VideoQuality.P1080, VideoQuality.forMbps(25.0))
        assertEquals(VideoQuality.AUTO, VideoQuality.parse(null))
    }
}
