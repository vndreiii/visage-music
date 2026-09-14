package com.music.vivi.compose.navigation

/**
 * Route names aligned with upstream [com.music.vivi.ui.screens.Screens] /
 * NavigationBuilder so cherry-picks stay easy.
 */
object Routes {
    const val HOME = "home"
    const val SEARCH = "search_input"
    const val SEARCH_RESULT = "search/{query}"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val SETTINGS_ACCOUNT = "settings/account"
    const val SETTINGS_APPEARANCE = "settings/appearance"
    const val SETTINGS_THEME = "settings/appearance/theme"
    const val SETTINGS_PLAYER = "settings/player"
    const val SETTINGS_DOWNLOADS = "settings/downloads"
    const val SMOKE = "smoke"
    /** Full-page player (tablet Now Playing) — not a dialog. */
    const val NOW_PLAYING = "now_playing"
    /** Immersive fullscreen player (no Up-next strip). */
    const val NOW_PLAYING_FULLSCREEN = "now_playing_fullscreen"

    const val ALBUM = "album/{albumId}"
    const val ARTIST = "artist/{artistId}"
    const val ONLINE_PLAYLIST = "online_playlist/{playlistId}"
    const val BROWSE = "browse/{browseId}"

    fun searchResult(query: String) = "search/${query.encodeURLPathSegment()}"
    fun album(albumId: String): String {
        PendingNav.setAlbumId(albumId)
        return "album/$albumId"
    }
    fun artist(artistId: String): String {
        PendingNav.setArtistId(artistId)
        return "artist/$artistId"
    }
    fun onlinePlaylist(playlistId: String): String {
        PendingNav.setPlaylistId(playlistId)
        return "online_playlist/$playlistId"
    }
    fun browse(browseId: String) = "browse/$browseId"

    /** Main rail / bottom destinations (upstream MainScreens minus ListenTogether for now). */
    val mainDestinations = listOf(
        MainDest(HOME, "Home"),
        MainDest(SEARCH, "Search"),
        MainDest(LIBRARY, "Library"),
        MainDest(SETTINGS, "Settings"),
        MainDest(SMOKE, "Smoke"),
    )
}

data class MainDest(val route: String, val label: String)

/** Minimal URL path encoding for search query segments. */
private fun String.encodeURLPathSegment(): String =
    java.net.URLEncoder.encode(this, Charsets.UTF_8).replace("+", "%20")
