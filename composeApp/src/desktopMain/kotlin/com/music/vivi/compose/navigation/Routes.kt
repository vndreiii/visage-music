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
    const val SETTINGS_NOW_PLAYING = "settings/now_playing"
    const val SETTINGS_LYRICS = "settings/lyrics"
    const val SETTINGS_SONG_SWAP = "settings/song_swap"
    const val SETTINGS_FULLSCREEN_PLAYER = "settings/fullscreen_player"
    const val SETTINGS_DOWNLOADS = "settings/downloads"
    const val SETTINGS_LOCAL_OFFLINE = "settings/local_offline"
    const val SETTINGS_DIAGNOSTICS = "settings/diagnostics"
    const val SETTINGS_KEYBINDS = "settings/keybinds"
    const val SMOKE = "smoke"
    /** Tag Manager for local files (browse, edit, auto-tag, write). */
    const val TAG_MANAGER = "tag_manager"
    /** Full-page player (tablet Now Playing) — not a dialog. */
    const val NOW_PLAYING = "now_playing"
    /** Immersive fullscreen player (no Up-next strip). */
    const val NOW_PLAYING_FULLSCREEN = "now_playing_fullscreen"

    const val ALBUM = "album/{albumId}"
    const val ARTIST = "artist/{artistId}"
    /** Helper-backed artist page (portrait/bio from the Rust helper + local tracks), keyed by display name. */
    const val ARTIST_INFO = "artist_info/{name}"
    const val ONLINE_PLAYLIST = "online_playlist/{playlistId}"
    /** User-created playlist stored in the local database. */
    const val LOCAL_PLAYLIST = "local_playlist/{playlistId}"
    const val SETTINGS_SIDEBARS = "settings/sidebars"
    const val BROWSE = "browse/{browseId}?params={params}"
    const val CHARTS = "charts_screen"
    const val MOOD_AND_GENRES = "mood_and_genres"
    const val NEW_RELEASE = "new_release"
    const val HISTORY = "history"
    const val ARTIST_ITEMS = "artist/{artistId}/items?browseId={browseId}&params={params}"

    fun searchResult(query: String) = "search/${query.encodeURLPathSegment()}"
    fun album(albumId: String) = "album/${albumId.encodeURLPathSegment()}"
    fun artist(artistId: String) = "artist/${artistId.encodeURLPathSegment()}"
    fun artistInfo(name: String) = "artist_info/${name.encodeURLPathSegment()}"
    fun onlinePlaylist(playlistId: String) = "online_playlist/${playlistId.encodeURLPathSegment()}"
    fun localPlaylist(playlistId: String) = "local_playlist/${playlistId.encodeURLPathSegment()}"
    fun browse(browseId: String, params: String? = null) =
        "browse/${browseId.encodeURLPathSegment()}" + (params?.let { "?params=${it.encodeURLPathSegment()}" } ?: "")

    /**
     * Route for a YouTube Music browse endpoint, picked by id shape so "More" links open
     * the matching page (album / playlist / artist / artist tab) instead of a generic list.
     */
    fun forBrowse(browseId: String, params: String? = null, artistId: String? = null): String = when {
        browseId.startsWith("MPREb") -> album(browseId)
        browseId.startsWith("VL") -> onlinePlaylist(browseId.removePrefix("VL"))
        browseId.startsWith("PL") || browseId.startsWith("RDCLAK") || browseId.startsWith("OLAK") -> onlinePlaylist(browseId)
        browseId.startsWith("UC") && params == null -> artist(browseId)
        browseId.startsWith("MPAD") || (browseId.startsWith("UC") && params != null) ->
            artistItems(artistId ?: browseId.removePrefix("MPAD"), browseId, params)
        else -> browse(browseId, params)
    }
    fun artistItems(artistId: String, browseId: String, params: String?) =
        "artist/${artistId.encodeURLPathSegment()}/items?browseId=${browseId.encodeURLPathSegment()}" +
            (params?.let { "&params=${it.encodeURLPathSegment()}" } ?: "")

    /** Main rail / bottom destinations (upstream MainScreens minus ListenTogether for now). */
    val mainDestinations = listOf(
        MainDest(HOME, "Home"),
        MainDest(SEARCH, "Search"),
        MainDest(LIBRARY, "Library"),
        MainDest(SETTINGS, "Settings"),
    )
}

data class MainDest(val route: String, val label: String)

/** Minimal URL path encoding for search query segments. */
private fun String.encodeURLPathSegment(): String =
    java.net.URLEncoder.encode(this, Charsets.UTF_8).replace("+", "%20")
