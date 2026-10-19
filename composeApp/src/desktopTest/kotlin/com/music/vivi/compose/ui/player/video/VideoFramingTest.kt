package com.music.vivi.compose.ui.player.video

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VideoFramingTest {
    @Test fun `new pref wins over legacy`() {
        assertEquals(VideoFraming.Original, VideoFraming.resolve("Original", "true"))
        assertEquals(VideoFraming.Fit, VideoFraming.resolve("Fit", "true"))
    }

    @Test fun `legacy fill boolean migrates`() {
        assertEquals(VideoFraming.Fill, VideoFraming.resolve(null, "true"))
        assertEquals(VideoFraming.Fit, VideoFraming.resolve(null, "false"))
        assertEquals(VideoFraming.Fit, VideoFraming.resolve(null, null))
        assertEquals(VideoFraming.Fit, VideoFraming.resolve("garbage", "nope"))
    }

    @Test fun `aspect resolution`() {
        assertEquals(16f / 9f, VideoFraming.aspectOf(0, 0))
        assertEquals(16f / 9f, VideoFraming.aspectOf(1920, 1080), 1e-4f)
        assertEquals(1f, VideoFraming.aspectOf(720, 1280)) // portrait clamps to square
        assertEquals(2.4f, VideoFraming.aspectOf(4000, 1000))
    }

    @Test fun `cover aspect only morphs for Original with a video`() {
        assertEquals(1f, VideoFraming.coverAspect(VideoFraming.Fit, 16f / 9f))
        assertEquals(1f, VideoFraming.coverAspect(VideoFraming.Fill, 16f / 9f))
        assertEquals(1f, VideoFraming.coverAspect(VideoFraming.Original, null))
        assertEquals(16f / 9f, VideoFraming.coverAspect(VideoFraming.Original, 16f / 9f))
    }
}
