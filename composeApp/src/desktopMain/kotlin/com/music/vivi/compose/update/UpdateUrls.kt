package com.music.vivi.compose.update

import java.net.URI

/** Where the updater may send requests and which URLs it will download from. */
object UpdateUrls {
    const val OWNER = "vndreiii"
    const val REPO = "visage-music"

    const val RELEASES_API = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
    const val RELEASE_PAGE = "https://github.com/$OWNER/$REPO/releases/latest"

    private const val DOWNLOAD_PATH_PREFIX = "/$OWNER/$REPO/releases/download/"

    /** Hosts GitHub may redirect a release download to. Nothing else is followed. */
    private val REDIRECT_HOSTS = setOf(
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
    )

    fun isTrustedApi(url: String): Boolean = url == RELEASES_API

    /**
     * The newest release's copy of [name]. GitHub redirects it to the real asset. Unlike the API, this is not
     * rate limited (the API allows 60 anonymous requests per hour per IP, and shared networks use that up).
     */
    fun latestAsset(name: String): String = "https://github.com/$OWNER/$REPO/releases/latest/download/$name"

    /** A release asset of this repository, as the first hop (no redirect yet). */
    fun isTrustedDownload(url: String): Boolean {
        val uri = parseHttps(url) ?: return false
        return uri.host == "github.com" && uri.rawPath != null && uri.rawPath.startsWith(DOWNLOAD_PATH_PREFIX) &&
            !uri.rawPath.split('/').any { it == ".." || it == "." } && uri.rawQuery == null
    }

    /** Redirect targets GitHub hands out for release assets. */
    fun isTrustedRedirect(url: String): Boolean {
        val uri = parseHttps(url) ?: return false
        return uri.host in REDIRECT_HOSTS
    }

    /** Exact expected location of an asset of a given release tag. */
    fun expectedAssetUrl(tag: String, name: String): String =
        "https://github.com/$OWNER/$REPO/releases/download/$tag/$name"

    private fun parseHttps(url: String): URI? {
        if (url.any { it.isWhitespace() || it == '\\' }) return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (uri.scheme != "https") return null
        if (uri.host == null || uri.userInfo != null || uri.fragment != null) return null
        if (uri.port != -1 && uri.port != 443) return null
        return uri
    }
}
