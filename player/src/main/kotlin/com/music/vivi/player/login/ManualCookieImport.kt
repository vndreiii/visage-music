package com.music.vivi.player.login

import com.music.innertube.YouTube
import com.music.innertube.auth.YtMusicSession
import com.music.innertube.models.AccountInfo
import com.music.vivi.player.session.SessionSecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Manual sign-in from a pasted cookie. Accepts a `Cookie:` request header value
 * or a Netscape `cookies.txt` export; visitorData and the account identity are
 * fetched from music.youtube.com with that cookie.
 */
object ManualCookieImport {
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

    /** Normalises pasted text to a `name=value; name=value` header, or null if it holds no cookies. */
    fun parseCookieHeader(raw: String): String? {
        val text = raw.trim().removePrefix("cookie:").removePrefix("Cookie:").trim()
        if (text.isBlank()) return null
        val pairs = linkedMapOf<String, String>()
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.size > 1 && lines.any { it.count { c -> c == '\t' } >= 6 }) {
            // Netscape format: domain, flag, path, secure, expiry, name, value
            for (line in lines) {
                val f = line.removePrefix("#HttpOnly_").split('\t')
                val comment = line.startsWith("#") && !line.startsWith("#HttpOnly_")
                if (!comment && f.size >= 7 && ("youtube.com" in f[0] || "google.com" in f[0])) pairs[f[5]] = f[6].trim()
            }
        } else {
            for (part in text.replace("\n", ";").split(';')) {
                val name = part.substringBefore('=').trim()
                if (name.isBlank() || !part.contains('=')) continue
                pairs[name] = part.substringAfter('=').trim()
            }
        }
        return pairs.takeIf { it.isNotEmpty() }?.entries?.joinToString("; ") { "${it.key}=${it.value}" }
    }

    suspend fun import(raw: String): Result<AccountInfo> = withContext(Dispatchers.IO) {
        runCatching {
            var cookie = parseCookieHeader(raw) ?: error("No cookies found in the pasted text.")
            // SAPISID is what signs requests; the 3P copy is identical and always present.
            if (!Regex("(^|; )SAPISID=").containsMatchIn(cookie)) {
                val alt = Regex("__Secure-3PAPISID=([^;]+)").find(cookie)?.groupValues?.get(1)
                    ?: error("Cookie is missing SAPISID. Copy the full cookie from a signed-in music.youtube.com request.")
                cookie += "; SAPISID=$alt"
            }
            val page = fetchMusicPage(cookie)
            val visitor = page.config("VISITOR_DATA")
                ?: YouTube.visitorData().getOrNull()
                ?: error("Could not get visitor data from YouTube Music.")
            val delegated = page.config("DELEGATED_SESSION_ID")
            val dataSync = delegated ?: page.config("DATASYNC_ID")
            val session = YtMusicSession(cookie, visitor, normalizeDataSyncId(dataSync))
            val account = session.validate().getOrElse {
                error("YouTube rejected the cookie (expired or incomplete): ${it.message?.substringBefore(". Text:")?.take(160)}")
            }
            SessionSecretStore.save(session, account)
            session.applyToYouTube()
            account
        }
    }

    private fun fetchMusicPage(cookie: String): String {
        val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(10)).build()
        val request = HttpRequest.newBuilder(URI("https://music.youtube.com/"))
            .header("Cookie", cookie)
            .header("User-Agent", UA)
            .header("Accept-Language", "en-US,en;q=0.9")
            .timeout(Duration.ofSeconds(20))
            .GET().build()
        return client.send(request, HttpResponse.BodyHandlers.ofString()).body()
    }

    /** Reads `"KEY":"value"` from the ytcfg blob; the page escapes some characters as \xNN. */
    private fun String.config(key: String): String? =
        Regex("\"$key\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(this)?.groupValues?.get(1)
            ?.replace(Regex("\\\\x([0-9a-fA-F]{2})")) { it.groupValues[1].toInt(16).toChar().toString() }
            ?.replace("\\/", "/")
            ?.takeIf { it.isNotBlank() }
}
