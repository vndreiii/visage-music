package com.music.vivi.desktop.login

import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpCookie
import java.net.URI

object WebCookieStore {
    private val manager = CookieManager(null, CookiePolicy.ACCEPT_ALL)

    fun install() {
        CookieHandler.setDefault(manager)
    }

    /** Cookie header string for [url], matching Android CookieManager.getCookie. */
    fun cookieHeaderFor(url: String): String {
        val uri = URI(url)
        val cookies = manager.cookieStore.get(uri)
        if (cookies.isEmpty()) {
            // Fall back: all cookies for youtube / google hosts
            return manager.cookieStore.cookies
                .filter { cookieMatchesHost(it, uri.host) }
                .joinToString("; ") { "${it.name}=${it.value}" }
        }
        return cookies.joinToString("; ") { "${it.name}=${it.value}" }
    }

    private fun cookieMatchesHost(cookie: HttpCookie, host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val domain = cookie.domain?.trimStart('.') ?: return false
        return host == domain || host.endsWith(".$domain")
    }
}
