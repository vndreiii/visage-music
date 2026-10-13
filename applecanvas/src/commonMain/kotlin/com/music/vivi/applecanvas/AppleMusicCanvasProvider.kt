package com.music.vivi.applecanvas

import com.music.vivi.canvas.CanvasArtwork
import com.music.vivi.canvas.normalizeForComparison
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.serialization.kotlinx.KotlinxSerializationConverter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

private object AppleCanvasLogger {
    fun d(msg: String) = println("AppleMusicCanvas: D: $msg")
    fun w(msg: String) = println("AppleMusicCanvas: W: $msg")
    fun e(t: Throwable, msg: String) {
        println("AppleMusicCanvas: E: $msg")
        t.printStackTrace()
    }
}

/**
 * Fetches Apple Music album motion artwork (HLS canvas) for the album screen.
 *
 * Two extraction strategies are tried in order:
 *
 * 1. **editorialVideo** — present on albums that have Apple Motion artwork.
 *    Accessed via `?extend=editorialVideo` on the AMP albums endpoint.
 *
 * 2. **music-video tracks** — some albums embed a full-length music video as a track.
 *    Accessed via `?include=tracks`.
 *
 * Results are cached for 24 hours.
 */
object AppleMusicCanvasProvider {

    // Public read-only JWT used by the Apple Music web player for unauthenticated catalog reads.
    private const val APPLE_MUSIC_TOKEN =
        "eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiIsImtpZCI6IldlYlBsYXlLaWQifQ" +
        ".eyJpc3MiOiJBTVBXZWJQbGF5IiwiaWF0IjoxNzgxMDMyODU1LCJleHAiOjE3ODQw" +
        "NTY4NTUsInJvb3RfaHR0cHNfb3JpZ2luIjpbImFwcGxlLmNvbSJdfQ" +
        ".fiMFcJWkfSlxKP9NVA0UW9CbItD1Rge0SISuepz203XcpU762OqdCpU9M-YkmtKkjRmaIWtjsfGgqZPrlMonpA"

    private var cachedToken: String? = null
    private var tokenExpiryMs: Long = 0L

    private suspend fun getOrFetchToken(forceRefresh: Boolean = false): String {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedToken != null && now < tokenExpiryMs - 60_000) {
            return cachedToken!!
        }

        AppleCanvasLogger.d("Fetching fresh developer token dynamically...")
        val candidateUrls = listOf(
            "https://music.apple.com/us/new",
            "https://music.apple.com/us/browse",
            "https://music.apple.com/us/search"
        )
        
        var fetchedToken: String? = null
        
        for (pageUrl in candidateUrls) {
            try {
                val html = client.get(pageUrl) {
                    header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                }.body<String>()

                val scriptRegex = Regex("""/assets/[a-zA-Z0-9~_-]+\.js""")
                val scripts = scriptRegex.findAll(html).map { it.value }.distinct().toList()
                if (scripts.isEmpty()) continue
                
                AppleCanvasLogger.d("Found script candidates in HTML ($pageUrl): $scripts")

                for (scriptPath in scripts) {
                    val scriptUrl = "https://music.apple.com$scriptPath"
                    val scriptText = client.get(scriptUrl) {
                        header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    }.body<String>()

                    val tokenRegex = Regex("""ey[a-zA-Z0-9_-]+\.ey[a-zA-Z0-9_-]+\.[a-zA-Z0-9_-]+""")
                    val tokens = tokenRegex.findAll(scriptText).map { it.value }.distinct()
                    for (token in tokens) {
                        try {
                            val body = token.split(".")[1]
                            val decodedBytes = java.util.Base64.getUrlDecoder().decode(body)
                            val decoded = String(decodedBytes, Charsets.UTF_8)
                            if (decoded.contains("iss") && decoded.contains("exp")) {
                                val expIndex = decoded.indexOf("\"exp\":")
                                if (expIndex != -1) {
                                    val expValStr = decoded.substring(expIndex + 6).takeWhile { it.isDigit() }
                                    val expSeconds = expValStr.toLongOrNull() ?: 0L
                                    if (expSeconds * 1000 > now) {
                                        fetchedToken = token
                                        tokenExpiryMs = expSeconds * 1000
                                        AppleCanvasLogger.d("Successfully decoded and verified new token. Expires at: ${java.util.Date(tokenExpiryMs)}")
                                        break
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // ignore decoding issues
                        }
                    }
                    if (fetchedToken != null) break
                }
                if (fetchedToken != null) break
            } catch (e: Exception) {
                AppleCanvasLogger.e(e, "Error occurred fetching token from $pageUrl")
            }
        }

        return if (fetchedToken != null) {
            cachedToken = fetchedToken
            fetchedToken
        } else {
            AppleCanvasLogger.w("Could not locate any valid fresh token. Using fallback token.")
            APPLE_MUSIC_TOKEN
        }
    }

    private const val ITUNES_SEARCH_URL = "https://itunes.apple.com/search"
    private const val AMP_BASE_URL = "https://amp-api.music.apple.com"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(json)
                // iTunes returns text/javascript for JSON responses
                register(ContentType.Text.JavaScript, KotlinxSerializationConverter(json))
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = 25_000
                socketTimeoutMillis = 25_000
            }
            install(ContentEncoding) {
                gzip()
                deflate()
            }
            install(HttpCache)
            expectSuccess = false
        }
    }

    private data class CacheEntry(
        val value: CanvasArtwork?,
        val expiresAtMs: Long,
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private const val CACHE_TTL_MS = 1000L * 60 * 60 * 24 // 24 hours

    suspend fun getByAlbumArtist(
        album: String,
        artist: String,
        storefront: String = "us",
    ): CanvasArtwork? {
        AppleCanvasLogger.d("getByAlbumArtist: album='$album', artist='$artist'")
        val key = cacheKey("sa", album, artist, storefront)
        cache[key]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let { return it.value }

        val result = searchAndFetchMotion(album, artist, album, storefront, "albums")
        if (result != null) {
            cache[key] = CacheEntry(result, System.currentTimeMillis() + CACHE_TTL_MS)
        } else {
            cache[key] = CacheEntry(null, System.currentTimeMillis() + 10 * 60 * 1000L)
        }
        return result
    }

    suspend fun getBySongArtist(
        song: String,
        artist: String,
        album: String? = null,
        storefront: String = "us",
    ): CanvasArtwork? {
        val key = cacheKey("song", song, artist, album ?: "", storefront)
        cache[key]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let { return it.value }

        // Use searchAndFetchMotion which can handle song searches by resolving to albums
        val result = searchAndFetchMotion(song, artist, album, storefront, "songs")
        if (result != null) {
            cache[key] = CacheEntry(result, System.currentTimeMillis() + CACHE_TTL_MS)
        } else {
            cache[key] = CacheEntry(null, System.currentTimeMillis() + 10 * 60 * 1000L)
        }
        return result
    }

    suspend fun getByAlbumId(
        albumId: String,
        storefront: String = "us",
    ): CanvasArtwork? {
        val key = cacheKey("id", albumId, storefront)
        cache[key]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let { return it.value }

        val result = fetchMotionArtwork(albumId, storefront, null)
        if (result != null) {
            cache[key] = CacheEntry(result, System.currentTimeMillis() + CACHE_TTL_MS)
        } else {
            cache[key] = CacheEntry(null, System.currentTimeMillis() + 10 * 60 * 1000L)
        }
        return result
    }

    /**
     * Searches via AMP API and tries to fetch motion artwork.
     * This is faster than iTunes search + AMP lookup.
     */
    private suspend fun searchAndFetchMotion(
        term: String,
        artist: String,
        album: String?,
        storefront: String,
        type: String, // "albums" or "songs"
    ): CanvasArtwork? {
        return runCatching {
            AppleCanvasLogger.d("searching for $type: $term (album: $album) in $storefront")
            val cleanTerm = term.replace(Regex("(?i)\\(official video\\)|\\[lyrics\\]|\\bfeat\\..*|\\s+-\\s+remastered.*"), "").trim()
            val query = if (cleanTerm.contains(artist, ignoreCase = true)) cleanTerm else "$artist $cleanTerm"
            val url = "$AMP_BASE_URL/v1/catalog/$storefront/search"
            var response = client.get(url) {
                header("Authorization", "Bearer ${getOrFetchToken()}")
                header("Origin", "https://music.apple.com")
                header("Referer", "https://music.apple.com/")
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                parameter("term", query)
                parameter("types", type)
                parameter("limit", "10")
                parameter("extend", "editorialVideo")
                parameter("include", "albums")
            }
            if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) {
                cachedToken = null
                response = client.get(url) {
                    header("Authorization", "Bearer ${getOrFetchToken(forceRefresh = true)}")
                    header("Origin", "https://music.apple.com")
                    header("Referer", "https://music.apple.com/")
                    header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    parameter("term", query)
                    parameter("types", type)
                    parameter("limit", "10")
                    parameter("extend", "editorialVideo")
                    parameter("include", "albums")
                }
            }
            if (response.status != HttpStatusCode.OK) {
                AppleCanvasLogger.w("search failed with status ${response.status}")
                return@runCatching null
            }

            val root = response.body<JsonObject>()
            val results = root["results"]?.jsonObject?.get(type)?.jsonObject?.get("data")?.jsonArray ?: return@runCatching null
            
            // Score results for quality and edition matching
            val scoredResults = results.mapNotNull { item ->
                val obj = item.jsonObject
                val attributes = obj["attributes"]?.jsonObject ?: return@mapNotNull null
                val resultArtistName = attributes["artistName"]?.jsonPrimitive?.contentOrNull ?: ""
                val resultName = attributes["name"]?.jsonPrimitive?.contentOrNull ?: ""
                val resultCollectionName = attributes["albumName"]?.jsonPrimitive?.contentOrNull
                    ?: attributes["collectionName"]?.jsonPrimitive?.contentOrNull
                    ?: ""
                
                // --- Playlist/Set List Filtering ---
                // We should never use playlist animations as album canvas.
                val nameLower = resultName.lowercase(Locale.ROOT)
                val collectionLower = resultCollectionName.lowercase(Locale.ROOT)
                val isBlacklisted = nameLower.contains("playlist") || nameLower.contains("set list") ||
                        collectionLower.contains("playlist") || collectionLower.contains("set list") ||
                        nameLower.contains("essentials") || collectionLower.contains("essentials") ||
                        collectionLower.contains("dj mix") || collectionLower.contains("mixed") ||
                        collectionLower.contains("apple music") || collectionLower.contains("today's hits") ||
                        nameLower.contains("session") || collectionLower.contains("session")
                
                if (isBlacklisted) {
                    AppleCanvasLogger.d("  - Skipping blacklisted result: '$resultName' (Album: '$resultCollectionName')")
                    return@mapNotNull null
                }

                // Strict check: both artists must match exactly (no fuzzy containing of full string, but splits multi-artists)
                val artistMatch = artistMatches(artist, resultArtistName)
                
                if (!artistMatch) return@mapNotNull null
                
                var score = 0
                if (artistMatch) score += 10
                
                // Name matching (Song or Album title)
                val normTerm = term.normalizeForComparison()
                val normResultName = resultName.normalizeForComparison()
                val nameMatch = normResultName == normTerm
                val nameFuzzy = normResultName.contains(normTerm) || normTerm.contains(normResultName)
                
                if (nameMatch) {
                    score += 15
                } else if (nameFuzzy) {
                    score += 7
                } else {
                    // If name doesn't match at all, this is likely a different song by the same artist
                    score -= 10
                }

                // Special editions handling (Deluxe, Expanded, etc)
                val editionWords = listOf("deluxe", "expanded", "remastered", "remix", "version", "edit", "mix", "bonus")
                for (word in editionWords) {
                    val inTerm = term.contains(word, ignoreCase = true)
                    val inResult = resultName.contains(word, ignoreCase = true)
                    if (inTerm && inResult) score += 5
                    else if (inTerm != inResult && inResult) score -= 3 // Penalty for unexpected "Deluxe" etc.
                }

                // Album matching - very strong signal
                if (!album.isNullOrBlank() && resultCollectionName.isNotBlank()) {
                    val normAlbum = album.normalizeForComparison()
                    val normCollection = resultCollectionName.normalizeForComparison()
                    val albumMatch = normCollection == normAlbum
                    val albumFuzzy = normCollection.contains(normAlbum) || normAlbum.contains(normCollection)
                    
                    if (albumMatch) score += 20
                    else if (albumFuzzy) score += 10
                }
                
                AppleCanvasLogger.d("  - Result: '$resultName' by '$resultArtistName' (Album: '$resultCollectionName', ID: ${obj["id"]}) -> Score: $score")
                score to item
            }.sortedByDescending { it.first }
            
            AppleCanvasLogger.d("Found ${scoredResults.size} scored results for term '$term'")
            
            // Extract distinct albumIds (up to 3)
            data class JobParam(
                val targetAlbumId: String,
                val type: String?,
                val attributes: JsonObject,
                val resultArtistName: String
            )
            
            val fetchParams = mutableListOf<JobParam>()
            for ((score, item) in scoredResults) {
                if (score < 12) continue
                val obj = item.jsonObject
                val attributes = obj["attributes"]?.jsonObject ?: continue
                val resultArtistName = attributes["artistName"]?.jsonPrimitive?.contentOrNull ?: ""
                
                var targetAlbumId: String? = null
                val type = obj["type"]?.jsonPrimitive?.contentOrNull
                if (type == "songs") {
                    val relationships = obj["relationships"]?.jsonObject
                    targetAlbumId = relationships?.get("albums")?.jsonObject?.get("data")?.jsonArray?.firstOrNull()
                        ?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
                        ?: attributes["collectionId"]?.jsonPrimitive?.contentOrNull
                    
                    if (targetAlbumId == null) {
                        val url = attributes["url"]?.jsonPrimitive?.contentOrNull
                        if (url != null) {
                            val albumPart = url.substringAfter("/album/", "").substringBefore("?")
                            val id = albumPart.substringAfterLast("/", "")
                            if (id.isNotBlank() && id.all { it.isDigit() }) {
                                targetAlbumId = id
                            }
                        }
                    }
                } else if (type == "albums") {
                    targetAlbumId = obj["id"]?.jsonPrimitive?.contentOrNull
                }

                if (targetAlbumId == null || targetAlbumId.startsWith("pl.")) continue
                
                if (fetchParams.none { it.targetAlbumId == targetAlbumId }) {
                    fetchParams.add(JobParam(targetAlbumId, type, attributes, resultArtistName))
                    if (fetchParams.size >= 3) break
                }
            }
            
            if (fetchParams.isEmpty()) return@runCatching null

            val result = coroutineScope {
                fetchParams.map { param ->
                    async {
                        val type = param.type
                        val attributes = param.attributes
                        val targetAlbumId = param.targetAlbumId
                        val resultArtistName = param.resultArtistName
                        
                        val ev = attributes["editorialVideo"]?.jsonObject
                        if (ev != null) {
                            val hlsUrl = extractEditorialVideoUrl(ev, preferTall = false)
                            val tallHlsUrl = extractEditorialVideoUrl(ev, preferTall = true)
                            if (!hlsUrl.isNullOrBlank() || !tallHlsUrl.isNullOrBlank()) {
                                val name = attributes["name"]?.jsonPrimitive?.contentOrNull
                                val collName = attributes["collectionName"]?.jsonPrimitive?.contentOrNull
                                val resolvedAlbumName = if (type == "songs") collName else name
                                return@async CanvasArtwork(
                                    name = name,
                                    artist = resultArtistName,
                                    albumId = targetAlbumId,
                                    albumName = resolvedAlbumName,
                                    animated = hlsUrl,
                                    animatedTall = tallHlsUrl
                                )
                            }
                        }
                        
                        fetchMotionArtwork(
                            albumId = targetAlbumId,
                            storefront = storefront,
                            fallbackArtist = resultArtistName,
                            titleOverride = if (type == "songs") attributes["name"]?.jsonPrimitive?.contentOrNull else null,
                            artistOverride = if (type == "songs") resultArtistName else null
                        )
                    }
                }.awaitAll().firstOrNull { it != null }
            }
            
            if (result != null) return@runCatching result
            
            AppleCanvasLogger.d("no canvas found in resolution/lookup for $term after ${scoredResults.size} results")
            null
        }.onFailure {
            if (it is CancellationException) throw it
            AppleCanvasLogger.e(it, "error in searchAndFetchMotion for $term")
        }.getOrNull()
    }

    private suspend fun fetchMotionArtwork(
        albumId: String,
        storefront: String,
        fallbackArtist: String?,
        titleOverride: String? = null,
        artistOverride: String? = null,
    ): CanvasArtwork? {
        if (albumId.startsWith("pl.")) {
            AppleCanvasLogger.d("fetchMotionArtwork: ignoring playlist id $albumId")
            return null
        }
        return runCatching {
            AppleCanvasLogger.d("fetching album $albumId")
            val url = "$AMP_BASE_URL/v1/catalog/$storefront/albums/$albumId"
            var response = client.get(url) {
                header("Authorization", "Bearer ${getOrFetchToken()}")
                header("Origin", "https://music.apple.com")
                header("Referer", "https://music.apple.com/")
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                parameter("extend", "editorialVideo")
                parameter("include", "tracks")
            }
            if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) {
                cachedToken = null
                response = client.get(url) {
                    header("Authorization", "Bearer ${getOrFetchToken(forceRefresh = true)}")
                    header("Origin", "https://music.apple.com")
                    header("Referer", "https://music.apple.com/")
                    header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    parameter("extend", "editorialVideo")
                    parameter("include", "tracks")
                }
            }
            if (response.status != HttpStatusCode.OK) {
                AppleCanvasLogger.w("album fetch failed for $albumId: ${response.status}")
                return@runCatching null
            }

            val root = response.body<JsonObject>()
            val data = root["data"]?.jsonArray
            if (data.isNullOrEmpty()) return@runCatching null
            
            val albumObj = data.firstOrNull()?.jsonObject ?: return@runCatching null
            val attributes = albumObj["attributes"]?.jsonObject
            val albumName = attributes?.get("name")?.jsonPrimitive?.contentOrNull ?: ""
            val artistName = attributes?.get("artistName")?.jsonPrimitive?.contentOrNull ?: fallbackArtist
            
            // --- Playlist/Station Filtering ---
            val nameLower = albumName.lowercase(Locale.ROOT)
            val isBlacklisted = nameLower.contains("playlist") || nameLower.contains("set list") ||
                    nameLower.contains("essentials") || nameLower.contains("dj mix") ||
                    nameLower.contains("mixed") || nameLower.contains("apple music") ||
                    nameLower.contains("today's hits") || nameLower.contains("session")
            
            if (isBlacklisted) {
                AppleCanvasLogger.d("fetchMotionArtwork: ignoring blacklisted album '$albumName' ($albumId)")
                return@runCatching null
            }

            // titleOverride is the song name (when searching by song), albumName is always the album name
            val finalTitle = titleOverride ?: albumName
            val finalArtist = artistOverride ?: artistName

            // Strategy 1: editorialVideo
            val ev = attributes?.get("editorialVideo")?.jsonObject
            if (ev != null) {
                val url = extractEditorialVideoUrl(ev, preferTall = false)
                val tallUrl = extractEditorialVideoUrl(ev, preferTall = true)
                if (!url.isNullOrBlank()) {
                    AppleCanvasLogger.d("found editorialVideo for $finalTitle (album: $albumName, id: $albumId)")
                    return@runCatching CanvasArtwork(
                        name = finalTitle,
                        artist = finalArtist,
                        albumId = albumId,
                        albumName = albumName,
                        animated = url,
                        animatedTall = tallUrl
                    )
                }
            }

            
            // Strategy 2: music-video tracks
            val tracksData = albumObj["relationships"]?.jsonObject?.get("tracks")?.jsonObject?.get("data")?.jsonArray
            if (tracksData != null) {
                for (track in tracksData) {
                    if (track.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "music-videos") {
                        val trackAttrs = track.jsonObject["attributes"]?.jsonObject ?: continue
                        val previews = trackAttrs["previews"]?.jsonArray
                        if (!previews.isNullOrEmpty()) {
                            val preview = previews.firstOrNull()?.jsonObject
                            val hlsUrl = preview?.get("hlsUrl")?.jsonPrimitive?.contentOrNull
                                ?: preview?.get("url")?.jsonPrimitive?.contentOrNull
                            if (!hlsUrl.isNullOrBlank()) {
                                AppleCanvasLogger.d("found music-video track for $finalTitle (album: $albumName, id: $albumId)")
                                // Determine if it is tall by looking at preview artwork width/height
                                val artwork = preview?.get("artwork")?.jsonObject
                                val w = artwork?.get("width")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1
                                val h = artwork?.get("height")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1
                                val isTall = h > w
                                return@runCatching CanvasArtwork(
                                    name = finalTitle,
                                    artist = finalArtist,
                                    albumId = albumId,
                                    albumName = albumName,
                                    animated = if (!isTall) hlsUrl else null,
                                    animatedTall = if (isTall) hlsUrl else null
                                )
                            }
                        }
                    }
                }
            }

            AppleCanvasLogger.d("no editorialVideo for $albumId (available keys: ${attributes?.keys})")
            null
        }.onFailure {
            if (it is CancellationException) throw it
            AppleCanvasLogger.e(it, "error in fetchMotionArtwork for $albumId")
        }.getOrNull()
    }

    private fun extractEditorialVideoUrl(ev: JsonObject, preferTall: Boolean = false): String? {
        val order = if (preferTall) {
            listOf(
                ev["motionDetailTall"]?.jsonObject,
                ev["motionDetailRaw"]?.jsonObject,
                ev["motionDetailSquare"]?.jsonObject
            )
        } else {
            listOf(
                ev["motionDetailSquare"]?.jsonObject,
                ev["motionDetailRaw"]?.jsonObject,
                ev["motionDetailTall"]?.jsonObject
            )
        }
        val assets = order.filterNotNull()
        
        for (asset in assets) {
            // Try different possible keys for the video URL
            val video = asset["video"]?.jsonPrimitive?.contentOrNull
                ?: asset["videoUrl"]?.jsonPrimitive?.contentOrNull
                ?: asset["hlsUrl"]?.jsonPrimitive?.contentOrNull
                ?: asset["url"]?.jsonPrimitive?.contentOrNull
            
            if (!video.isNullOrBlank()) return video
        }

        AppleCanvasLogger.d("editorialVideo found but no video link in assets: ${ev.keys}")
        return null
    }

    private fun cacheKey(prefix: String, vararg parts: String): String {
        return "$prefix|" + parts.joinToString("|") { it.trim().lowercase(Locale.ROOT) }
    }

    private fun artistMatches(requested: String, returned: String): Boolean {
        val delimiters = Regex("(?:\\s*,\\s*|\\s*&\\s*|\\s+×\\s+|\\s+x\\s+|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bfeaturing\\b|\\bwith\\b)", RegexOption.IGNORE_CASE)
        val requestedList = requested.split(delimiters)
            .map { it.replace(Regex("(?i)\\s*-\\s*topic$"), "").replace(Regex("(?i)vevo$"), "") }
            .map { it.normalizeForComparison() }
            .filter { it.isNotBlank() }
        val returnedList = returned.split(delimiters).map { it.normalizeForComparison() }.filter { it.isNotBlank() }
        if (requestedList.isEmpty() || returnedList.isEmpty()) return false
        val firstReq = requestedList.first()
        return returnedList.any { it == firstReq }
    }
}
