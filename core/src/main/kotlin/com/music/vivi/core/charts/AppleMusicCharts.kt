package com.music.vivi.core.charts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URI

data class ChartSuggestion(
    val rank: Int,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val appleMusicUrl: String? = null,
)

/**
 * Apple Marketing Tools RSS — same feed upstream Suggestions tab uses.
 */
object AppleMusicCharts {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun topSongs(countryCode: String = "us", limit: Int = 50): List<ChartSuggestion> =
        withContext(Dispatchers.IO) {
            val cc = countryCode.lowercase().ifBlank { "us" }
            val url =
                "https://rss.applemarketingtools.com/api/v2/$cc/music/most-played/$limit/songs.json"
            val body = httpGet(url) ?: return@withContext emptyList()
            runCatching {
                val root = json.parseToJsonElement(body).jsonObject
                val results = root["feed"]?.jsonObject?.get("results")?.jsonArray
                    ?: return@runCatching emptyList()
                results.mapIndexedNotNull { index, el ->
                    val item = el.jsonObject
                    val title = item["name"]?.jsonPrimitive?.content ?: return@mapIndexedNotNull null
                    val artist = item["artistName"]?.jsonPrimitive?.content
                        ?: return@mapIndexedNotNull null
                    val art = item["artworkUrl100"]?.jsonPrimitive?.content
                        ?.replace(Regex("""(\d+)x(\d+)"""), "600x600")
                    val appleUrl = item["url"]?.jsonPrimitive?.content
                    ChartSuggestion(
                        rank = index + 1,
                        title = title,
                        artist = artist,
                        thumbnailUrl = art,
                        appleMusicUrl = appleUrl,
                    )
                }
            }.getOrElse { emptyList() }
        }

    private fun httpGet(url: String): String? {
        val conn = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            requestMethod = "GET"
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36",
            )
        }
        return try {
            if (conn.responseCode !in 200..299) null
            else conn.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }
}
