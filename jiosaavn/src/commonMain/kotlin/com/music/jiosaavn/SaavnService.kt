/**
 * JioSaavn audio streaming service (ported from upstream vivi-music).
 * Fetches search results and stream details from JioSaavn's public API,
 * decrypting CDN media links with DES-ECB.
 */
package com.music.jiosaavn

import com.music.vivi.log.ViviLog
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

@Serializable
data class SaavnDownloadUrl(
    @SerialName("quality") val quality: String = "",
    @SerialName("url") val url: String = "",
)

@Serializable
data class SaavnImage(
    @SerialName("quality") val quality: String = "",
    @SerialName("url") val url: String = "",
)

@Serializable
data class SaavnArtistItem(
    @SerialName("id") val id: String = "",
    @SerialName("name") val name: String = "",
)

@Serializable
data class SaavnArtists(
    @SerialName("primary") val primary: List<SaavnArtistItem> = emptyList(),
    @SerialName("featured") val featured: List<SaavnArtistItem> = emptyList(),
    @SerialName("all") val all: List<SaavnArtistItem> = emptyList(),
)

@Serializable
data class SaavnAlbum(
    @SerialName("id") val id: String? = null,
    @SerialName("name") val name: String? = null,
)

@Serializable
data class SaavnSong(
    @SerialName("id") val id: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("duration") val duration: Int? = null,
    @SerialName("explicitContent") val explicitContent: Boolean = false,
    @SerialName("artists") val artists: SaavnArtists = SaavnArtists(),
    @SerialName("image") val image: List<SaavnImage> = emptyList(),
    @SerialName("downloadUrl") val downloadUrl: List<SaavnDownloadUrl> = emptyList(),
    @SerialName("album") val album: SaavnAlbum? = null,
    val isProOnly: Boolean = false,
)

@Serializable
data class RawArtistMapItem(
    val id: String = "",
    val name: String = "",
    val role: String = "",
    val type: String = "",
)

@Serializable
data class RawArtistMap(
    @SerialName("primary_artists") val primaryArtists: List<RawArtistMapItem> = emptyList(),
    @SerialName("featured_artists") val featuredArtists: List<RawArtistMapItem> = emptyList(),
    val artists: List<RawArtistMapItem> = emptyList(),
)

@Serializable
data class RawRights(
    val code: String = "",
    val cacheable: String = "",
    @SerialName("delete_cached_object") val deleteCachedObject: String = "",
    val reason: String = "",
) {
    val isProOnly: Boolean
        get() = code == "1" || reason.contains("Pro Only", ignoreCase = true)
}

@Serializable
data class RawMoreInfo(
    val album_id: String = "",
    val album: String = "",
    @SerialName("encrypted_media_url") val encryptedMediaUrl: String = "",
    val duration: String = "",
    val artistMap: RawArtistMap = RawArtistMap(),
    val rights: RawRights = RawRights(),
)

@Serializable
data class RawSongItem(
    val id: String = "",
    val title: String = "",
    val type: String = "",
    val year: String = "",
    val image: String = "",
    val language: String = "",
    @SerialName("play_count") val playCount: String = "",
    @SerialName("explicit_content") val explicitContent: String = "",
    @SerialName("more_info") val moreInfo: RawMoreInfo = RawMoreInfo(),
)

@Serializable
data class RawSearchResponse(
    val total: Int = 0,
    val start: Int = 0,
    val results: List<RawSongItem> = emptyList(),
)

object SaavnService {
    private const val TAG = "SaavnService"

    private val BASE_URL = String(
        Base64.getDecoder().decode("aHR0cHM6Ly93d3cuamlvc2Fhdm4uY29tL2FwaS5waHA="),
        Charsets.UTF_8,
    )

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val client by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                requestTimeoutMillis = 6_000
                connectTimeoutMillis = 4_000
                socketTimeoutMillis = 6_000
            }
            defaultRequest {
                url(BASE_URL)
                headers.append(HttpHeaders.Accept, "application/json")
                headers.append(
                    HttpHeaders.UserAgent,
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36",
                )
                headers.append("X-Forwarded-For", "49.36.0.1")
                headers.append("X-Real-IP", "49.36.0.1")
                headers.append("Accept-Language", "en-IN,en;q=0.9")
                headers.append(HttpHeaders.Cookie, "explicit_content=1")
            }
            expectSuccess = false
        }
    }

    private fun decryptUrl(encryptedUrl: String): String {
        if (encryptedUrl.isBlank()) return ""
        return try {
            val secretKey = SecretKeySpec("38346591".toByteArray(Charsets.UTF_8), "DES")
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey)
            val decodedBytes = Base64.getDecoder().decode(encryptedUrl)
            String(cipher.doFinal(decodedBytes), Charsets.UTF_8).trim()
        } catch (e: Exception) {
            ViviLog.e(TAG, "JioSaavn URL decryption failed", e)
            ""
        }
    }

    private fun createDownloadLinks(encryptedUrl: String): List<SaavnDownloadUrl> {
        val decryptedUrl = decryptUrl(encryptedUrl)
        if (decryptedUrl.isBlank()) return emptyList()

        val qualities = listOf(
            "_96" to "96kbps",
            "_160" to "160kbps",
            "_320" to "320kbps",
        )
        val suffixRegex = Regex("_(48|96|160|320)\\.(mp4|aac|mp3)$")
        return qualities.map { (suffix, bitrate) ->
            val url = if (decryptedUrl.contains(suffixRegex)) {
                decryptedUrl.replace(suffixRegex) { match ->
                    "${suffix}.${match.groupValues[2]}"
                }
            } else {
                decryptedUrl.replace("_96", suffix)
            }
            SaavnDownloadUrl(quality = bitrate, url = url)
        }
    }

    private fun createImageLinks(link: String): List<SaavnImage> {
        if (link.isBlank()) return emptyList()
        val qualities = listOf("50x50", "150x150", "500x500")
        val qualityRegex = Regex("150x150|50x50")
        val protocolRegex = Regex("^http://")
        return qualities.map { quality ->
            SaavnImage(
                quality = quality,
                url = link.replace(qualityRegex, quality).replace(protocolRegex, "https://"),
            )
        }
    }

    private fun mapRawToSaavnSong(raw: RawSongItem): SaavnSong {
        fun mapArtists(items: List<RawArtistMapItem>) =
            items.map { SaavnArtistItem(id = it.id, name = it.name) }
        return SaavnSong(
            id = raw.id,
            name = raw.title,
            duration = raw.moreInfo.duration.toIntOrNull(),
            explicitContent = raw.explicitContent == "1",
            artists = SaavnArtists(
                primary = mapArtists(raw.moreInfo.artistMap.primaryArtists),
                featured = mapArtists(raw.moreInfo.artistMap.featuredArtists),
                all = mapArtists(raw.moreInfo.artistMap.artists),
            ),
            image = createImageLinks(raw.image),
            downloadUrl = createDownloadLinks(raw.moreInfo.encryptedMediaUrl),
            album = SaavnAlbum(id = raw.moreInfo.album_id, name = raw.moreInfo.album),
            isProOnly = raw.moreInfo.rights.isProOnly,
        )
    }

    suspend fun searchSongs(query: String): Result<List<SaavnSong>> = runCatching {
        ViviLog.d(TAG, "searchSongs: query=\"$query\"")
        val response = client.get("") {
            parameter("__call", "search.getResults")
            parameter("_format", "json")
            parameter("_marker", "0")
            parameter("api_version", "4")
            parameter("ctx", "android")
            parameter("q", query)
            parameter("p", "1")
            parameter("n", "10")
        }
        if (response.status != HttpStatusCode.OK) {
            throw IllegalStateException("Saavn search failed: HTTP ${response.status.value}")
        }
        val body = json.decodeFromString<RawSearchResponse>(response.bodyAsText())
        val results = body.results.map { mapRawToSaavnSong(it) }
        if (results.isEmpty()) {
            throw NoSuchElementException("No songs found on JioSaavn for: \"$query\"")
        }
        results
    }.onFailure {
        ViviLog.e(TAG, "searchSongs: failed for query=\"$query\"", it)
    }

    fun selectBestUrl(urls: List<SaavnDownloadUrl>, quality: String): String? {
        val filteredUrls = urls.filter { it.url.isNotBlank() }
        if (filteredUrls.isEmpty()) return null
        filteredUrls.firstOrNull { it.quality.equals(quality, ignoreCase = true) }?.url
            ?.let { return it }
        filteredUrls.firstOrNull { it.quality.equals("320kbps", ignoreCase = true) }?.url
            ?.let { return it }
        return filteredUrls.lastOrNull()?.url
    }

    suspend fun getBestStreamUrl(saavnSongId: String, quality: String): String? {
        val result = runCatching {
            val response = client.get("") {
                parameter("__call", "song.getDetails")
                parameter("_format", "json")
                parameter("_marker", "0")
                parameter("api_version", "4")
                parameter("ctx", "android")
                parameter("pids", saavnSongId)
            }
            if (response.status != HttpStatusCode.OK) {
                throw IllegalStateException("Saavn getBestStreamUrl failed: HTTP ${response.status.value}")
            }
            val decodedMap = json.decodeFromString<Map<String, RawSongItem>>(response.bodyAsText())
            val rawSong = decodedMap.values.firstOrNull()
                ?: throw NoSuchElementException("Song not found")
            selectBestUrl(mapRawToSaavnSong(rawSong).downloadUrl, quality)
        }
        if (result.isFailure) {
            ViviLog.e(TAG, "getBestStreamUrl failed for saavnSongId=$saavnSongId", result.exceptionOrNull())
        }
        return result.getOrNull()
    }

    suspend fun getContentLength(url: String): Long? = runCatching {
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 4_000
                connectTimeoutMillis = 2_000
                socketTimeoutMillis = 4_000
            }
            expectSuccess = false
            followRedirects = true
        }.use { c ->
            val response = c.get(url) {
                headers.append(HttpHeaders.Range, "bytes=0-0")
                headers.append(
                    HttpHeaders.UserAgent,
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36",
                )
            }
            if (response.status == HttpStatusCode.PartialContent || response.status == HttpStatusCode.OK) {
                val contentRange = response.headers[HttpHeaders.ContentRange]
                contentRange?.substringAfter('/')?.toLongOrNull()
                    ?: response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            } else {
                null
            }
        }
    }.onFailure {
        ViviLog.e(TAG, "getContentLength failed for url=$url", it)
    }.getOrNull()
}
