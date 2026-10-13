package com.music.vivi.compose.local

import com.music.vivi.database.Local_track
import com.music.vivi.database.Downloaded_track
import com.music.vivi.player.QueueTrack
import java.io.File

fun Local_track.toQueueTrack(): QueueTrack = QueueTrack(
    videoId = "local:$id",
    title = title,
    artist = artist_name,
    artworkUrl = cover_path?.let {
        if (it.startsWith("opus-lazy://")) null
        else File(it).toURI().toString()
    },
    localPath = file_path,
    durationMs = (duration_sec ?: 0) * 1000,
    album = album_title,
    lyricsPath = lyrics_path,
    embeddedLyrics = embedded_lyrics,
)

fun Downloaded_track.toQueueTrack(): QueueTrack = QueueTrack(
    videoId = "offline:$video_id",
    title = title,
    artist = artist,
    artworkUrl = cover_path?.let { File(it).toURI().toString() },
    localPath = file_path,
    durationMs = (duration_sec ?: 0) * 1000,
    album = album,
)

