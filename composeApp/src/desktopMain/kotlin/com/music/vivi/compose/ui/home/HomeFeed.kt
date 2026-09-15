package com.music.vivi.compose.ui.home

import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.pages.HomePage

/** Combine real recommendations into a fuller Quick Picks shelf and avoid repeated playlists. */
internal fun prepareHomeSections(sections: List<HomePage.Section>, savedPlaylistIds: Set<String>): List<HomePage.Section> {
    val quick = sections.filter { it.title.equals("Quick picks", ignoreCase = true) }
    val songs = (quick + sections).flatMap { it.items }.filterIsInstance<SongItem>().distinctBy { it.id }.take(60)
    val playlistIds = savedPlaylistIds.toMutableSet()
    val shelves = sections.filterNot { it in quick }.mapNotNull { section ->
        val items = section.items.distinctBy { it.id }.filter { it !is PlaylistItem || playlistIds.add(it.id) }
        items.takeIf { it.isNotEmpty() }?.let { section.copy(items = it) }
    }
    // Alternate playlist shelves with the remaining feed, keeping order within each group.
    val playlists = shelves.filter { shelf -> shelf.items.all { it is PlaylistItem } }.toMutableList()
    val others = shelves.filterNot { it in playlists }.toMutableList()
    return buildList {
        if (songs.isNotEmpty()) {
            add(HomePage.Section("Quick picks", "From your recommendations", null, null, songs))
        }
        while (playlists.isNotEmpty() || others.isNotEmpty()) {
            if (playlists.isNotEmpty()) add(playlists.removeAt(0))
            if (others.isNotEmpty()) add(others.removeAt(0))
        }
    }
}
