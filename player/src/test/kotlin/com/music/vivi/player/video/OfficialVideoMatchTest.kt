package com.music.vivi.player.video

import com.music.innertube.models.Artist
import com.music.innertube.models.SongItem
import com.music.innertube.pages.MusicVideoTypes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OfficialVideoMatchTest {
    private fun song(id: String, title: String, artist: String, type: String, dur: Int?) =
        SongItem(id = id, title = title, artists = listOf(Artist(artist, null)), duration = dur, musicVideoType = type, thumbnail = "")

    private val track = song("atv", "REDRED", "CORTIS", MusicVideoTypes.ATV, 207)

    @Test
    fun picksTheOfficialVideoClosestInLength() {
        val hits = listOf(
            song("dance", "REDRED Dance Cover", "SOMEONE", MusicVideoTypes.UGC, 208),
            song("short", "REDRED", "CORTIS", MusicVideoTypes.OMV, 168),
            song("mv", "REDRED", "CORTIS", MusicVideoTypes.OMV, 209),
        )
        assertEquals("mv", MusicVideoResolver.pickOfficialVideo(track, hits)?.id)
    }

    @Test
    fun rejectsUserUploadsOtherArtistsAndFarOffLengths() {
        val hits = listOf(
            song("ugc", "REDRED", "CORTIS", MusicVideoTypes.UGC, 207),
            song("other", "REDRED", "SOMEONE ELSE", MusicVideoTypes.OMV, 207),
            song("long", "REDRED", "CORTIS", MusicVideoTypes.OMV, 400),
        )
        assertNull(MusicVideoResolver.pickOfficialVideo(track, hits))
    }
}
