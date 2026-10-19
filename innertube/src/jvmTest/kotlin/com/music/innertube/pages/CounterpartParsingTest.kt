package com.music.innertube.pages

import com.music.innertube.models.PlaylistPanelVideoRenderer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CounterpartParsingTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private fun panel(counterpartJson: String, selfId: String = "AUDIO1") = json.decodeFromString<PlaylistPanelVideoRenderer>(
        """
        {"title":{"runs":[{"text":"Song"}]},"selected":true,"videoId":"$selfId",
         "thumbnail":{"thumbnails":[{"url":"https://x/y.jpg","width":1,"height":1}]},
         "navigationEndpoint":{"watchEndpoint":{"videoId":"$selfId","watchEndpointMusicSupportedConfigs":{"watchEndpointMusicConfig":{"musicVideoType":"MUSIC_VIDEO_TYPE_ATV"}}}}
         $counterpartJson}
        """.trimIndent(),
    )

    @Test fun parsesOmvCounterpart() {
        val r = panel(""","counterpart":[{"counterpartRenderer":{"playlistPanelVideoRenderer":{"videoId":"VIDEO1","navigationEndpoint":{"watchEndpoint":{"videoId":"VIDEO1","watchEndpointMusicSupportedConfigs":{"watchEndpointMusicConfig":{"musicVideoType":"MUSIC_VIDEO_TYPE_OMV"}}}}}}}]""")
        assertEquals("VIDEO1", NextPage.counterpartVideoId(r))
    }

    @Test fun ignoresAudioCounterpartAndMissing() {
        val audio = panel(""","counterpart":[{"counterpartRenderer":{"playlistPanelVideoRenderer":{"videoId":"OTHER","navigationEndpoint":{"watchEndpoint":{"videoId":"OTHER","watchEndpointMusicSupportedConfigs":{"watchEndpointMusicConfig":{"musicVideoType":"MUSIC_VIDEO_TYPE_ATV"}}}}}}}]""")
        assertNull(NextPage.counterpartVideoId(audio))
        assertNull(NextPage.counterpartVideoId(panel("")))
    }
}
