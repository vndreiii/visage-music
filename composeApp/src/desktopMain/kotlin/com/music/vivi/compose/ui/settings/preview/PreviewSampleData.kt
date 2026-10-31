package com.music.vivi.compose.ui.settings.preview

import com.music.vivi.compose.ui.library.sidebar.EntryKind
import com.music.vivi.compose.ui.library.sidebar.LibraryEntry
import com.music.vivi.compose.ui.library.sidebar.LibraryFolderModel
import androidx.compose.ui.graphics.Color
import com.music.vivi.compose.theme.ArtworkPalette
import com.music.vivi.player.QueueTrack

/**
 * Fixed sample content for Settings previews. Everything is local: no artwork URLs (covers are the generated
 * ones), no ids that exist in a real library, so a preview can never touch the network or the database.
 */
object PreviewSampleData {
    private fun track(id: String, title: String, artist: String, album: String, seconds: Int) =
        QueueTrack(videoId = "preview:$id", title = title, artist = artist, album = album, durationMs = seconds * 1000L)

    /** Colours the backgrounds get in previews (the generated covers have no real artwork to take them from). */
    val palette = ArtworkPalette(
        seed = Color(0xFFE0537A),
        gradient = listOf(Color(0xFFFF7A9A), Color(0xFF8A2F7C), Color(0xFF1B1030)),
        glowColors = listOf(
            Color(0xFFFF7A9A), Color(0xFFFFB067), Color(0xFF8A2F7C), Color(0xFF5B5BD6), Color(0xFF2FB8C4), Color(0xFF1B1030),
        ),
    )

    /** The queue; index 0 is the "now playing" song. */
    val queue: List<QueueTrack> = listOf(
        track("1", "Midnight Drive", "Neon Harbor", "Afterglow", 214),
        track("2", "Paper Lanterns", "Sora Keys", "Quiet Hours", 187),
        track("3", "Static Bloom", "The Low Tides", "Static Bloom", 242),
        track("4", "Golden Hour Loop", "Marlowe", "Golden Hour", 176),
        track("5", "Slow Orbit", "Neon Harbor", "Afterglow", 203),
        track("6", "Cardboard Stars", "Sora Keys", "Quiet Hours", 159),
        track("7", "Glasshouse", "Marlowe", "Golden Hour", 228),
    )

    val libraryEntries: List<LibraryEntry> = listOf(
        LibraryEntry("liked", "LM", EntryKind.LIKED, "Liked Songs", "Playlist", 128, null),
        LibraryEntry("downloads", "offline", EntryKind.DOWNLOADS, "Downloaded", "Available offline", 42, null),
        LibraryEntry("local:p1", "p1", EntryKind.LOCAL_PLAYLIST, "Late night coding", "You", 54, null, recentRank = 0),
        LibraryEntry("yt:p2", "p2", EntryKind.YT_PLAYLIST, "Morning coffee", "YouTube Music", 36, null, recentRank = 1_001),
        LibraryEntry("album:a1", "a1", EntryKind.ALBUM, "Afterglow", "Neon Harbor", null, null, recentRank = 2_000),
        LibraryEntry("yt:p3", "p3", EntryKind.YT_PLAYLIST, "Road trip mix", "YouTube Music", 80, null, recentRank = 1_002),
        LibraryEntry("artist:r1", "r1", EntryKind.ARTIST, "Sora Keys", "Sora Keys", null, null, recentRank = 3_000),
        LibraryEntry("album:a2", "a2", EntryKind.ALBUM, "Golden Hour", "Marlowe", null, null, recentRank = 2_001),
        LibraryEntry("local:p4", "p4", EntryKind.LOCAL_PLAYLIST, "Gym", "You", 24, null, recentRank = 1),
        LibraryEntry("yt:p5", "p5", EntryKind.YT_PLAYLIST, "Focus", "YouTube Music", 61, null, recentRank = 1_003),
    )

    val libraryFolders: List<LibraryFolderModel> = listOf(
        LibraryFolderModel(
            id = "f1", title = "Work", children = listOf("local:p1", "yt:p5"), expanded = false,
        ),
    )

    /** Shelf titles for the sample Home page. */
    val shelves: List<Pair<String, List<String>>> = listOf(
        "Quick picks" to listOf("Midnight Drive", "Paper Lanterns", "Static Bloom", "Golden Hour Loop", "Slow Orbit"),
        "Jump back in" to listOf("Afterglow", "Quiet Hours", "Golden Hour", "Static Bloom", "Glasshouse"),
    )
}
