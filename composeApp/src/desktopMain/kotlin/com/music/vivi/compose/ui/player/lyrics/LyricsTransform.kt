package com.music.vivi.compose.ui.player.lyrics

import com.music.lyricsync.ParsedLyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

enum class TranslateProvider(val label: String) {
    GOOGLE("Google (free, no key)"),
    GEMINI("Gemini (API key)"),
}

/** Languages offered as translation targets: code to name. */
val TranslateLanguages: List<Pair<String, String>> = listOf(
    "en" to "English", "es" to "Spanish", "fr" to "French", "de" to "German", "pt" to "Portuguese", "it" to "Italian",
    "ru" to "Russian", "ja" to "Japanese", "ko" to "Korean", "zh-CN" to "Chinese", "id" to "Indonesian",
    "tr" to "Turkish", "ar" to "Arabic", "hi" to "Hindi", "nl" to "Dutch", "pl" to "Polish", "vi" to "Vietnamese", "tl" to "Filipino",
)

/** What to add under each lyric line. */
data class LyricsTransformOptions(
    val romanize: Boolean,
    val translate: Boolean,
    val targetLang: String,
    val provider: TranslateProvider,
    val geminiKey: String,
    val geminiModel: String,
) {
    val enabled: Boolean get() = romanize || translate
}

class LyricsTransformResult(val lyrics: ParsedLyrics, val problem: String?)

/**
 * Fills each line's translation slot with romanization (offline for kana / Hangul / Cyrillic; kanji go to the provider)
 * and/or a translation into [LyricsTransformOptions.targetLang]. Results are cached per song and settings.
 */
object LyricsTransform {
    private val http: HttpClient by lazy { HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build() }
    private val cache = java.util.concurrent.ConcurrentHashMap<String, List<String?>>()

    suspend fun apply(key: String, lyrics: ParsedLyrics, o: LyricsTransformOptions): LyricsTransformResult = withContext(Dispatchers.IO) {
        val lines = lyrics.lines
        val cacheKey = "$key|${o.romanize}|${o.translate}|${o.targetLang}|${o.provider}"
        cache[cacheKey]?.let { return@withContext LyricsTransformResult(merge(lyrics, it), null) }

        val rom = arrayOfNulls<String>(lines.size)
        val tr = arrayOfNulls<String>(lines.size)
        if (o.romanize) lines.forEachIndexed { i, l -> if (!l.isBackground) rom[i] = LyricsRomanizer.romanize(l.text) }

        // Lines the provider must handle: translations, and kanji readings the offline tables cannot give.
        val wanted = lines.indices.filter { i ->
            val l = lines[i]
            !l.isBackground && l.text.isNotBlank() && l.translation.isNullOrBlank() &&
                ((o.translate && l.text.any { it.isLetter() }) || (o.romanize && rom[i] == null && LyricsRomanizer.needsDictionary(l.text)))
        }
        var problem: String? = null
        if (wanted.isNotEmpty()) {
            try {
                val remote = if (o.provider == TranslateProvider.GEMINI) {
                    if (o.geminiKey.isBlank()) error("Add a Gemini API key in Lyrics settings")
                    gemini(wanted.map { lines[it].text }, o)
                } else google(wanted.map { lines[it].text }, o)
                wanted.forEachIndexed { n, i ->
                    val (r, t) = remote[n]
                    if (o.romanize && rom[i] == null) rom[i] = r
                    if (o.translate) tr[i] = t?.takeUnless { it.equals(lines[i].text, ignoreCase = true) }
                }
            } catch (e: Exception) {
                problem = e.message?.take(120) ?: "Translation failed"
            }
        }
        val combined = lines.indices.map { i ->
            listOfNotNull(
                rom[i]?.takeUnless { it.equals(lines[i].text, ignoreCase = true) },
                tr[i],
            ).joinToString("\n").ifBlank { null }
        }
        if (problem == null) cache[cacheKey] = combined
        LyricsTransformResult(merge(lyrics, combined), problem)
    }

    private fun merge(lyrics: ParsedLyrics, extra: List<String?>): ParsedLyrics =
        lyrics.copy(
            lines = lyrics.lines.mapIndexed { i, l ->
                val add = extra.getOrNull(i)
                when {
                    add == null -> l
                    l.translation.isNullOrBlank() -> l.copy(translation = add)
                    else -> l.copy(translation = add + "\n" + l.translation)
                }
            },
        )

    // ---- Google (the public web endpoint; no key, best effort) ----

    private suspend fun google(texts: List<String>, o: LyricsTransformOptions): List<Pair<String?, String?>> = coroutineScope {
        val gate = Semaphore(4)
        val results = texts.map { text -> async { gate.withPermit { googleOne(text, o.targetLang) } } }.awaitAll()
        results
    }

    private fun googleOne(text: String, target: String): Pair<String?, String?> {
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$target&dt=t&dt=rm&q=" +
            URLEncoder.encode(text, Charsets.UTF_8)
        val req = HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(10))
            .header("User-Agent", "Mozilla/5.0").GET().build()
        val res = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (res.statusCode() != 200 || !res.body().trimStart().startsWith("[")) {
            error("Google translate refused the request (HTTP ${res.statusCode()}); try Gemini")
        }
        val root = Json.parseToJsonElement(res.body()).jsonArray
        val parts = root.getOrNull(0)?.takeIf { it !is JsonNull }?.jsonArray ?: return null to null
        val translation = StringBuilder()
        var romanized: String? = null
        for (p in parts) {
            val a = p.jsonArray
            a.getOrNull(0)?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull?.let { translation.append(it) }
            a.getOrNull(3)?.takeIf { it !is JsonNull && a.getOrNull(0) is JsonNull }?.jsonPrimitive?.contentOrNull?.let { romanized = it }
        }
        return romanized to translation.toString().ifBlank { null }
    }

    // ---- Gemini ----

    private fun gemini(texts: List<String>, o: LyricsTransformOptions): List<Pair<String?, String?>> {
        val lang = TranslateLanguages.firstOrNull { it.first == o.targetLang }?.second ?: o.targetLang
        val input = buildJsonArray {
            texts.forEachIndexed { i, t -> add(buildJsonObject { put("i", i); put("text", t) }) }
        }
        val prompt = "You process song lyrics line by line. Input is a JSON array of {\"i\":index,\"text\":line}. " +
            "Return a JSON array with one {\"i\":index,\"romanization\":string,\"translation\":string} per input line. " +
            "romanization: the line in Latin letters (Hepburn romaji for Japanese including kanji readings, Revised " +
            "Romanization for Korean, pinyin for Chinese, standard transliteration otherwise); empty string if already Latin. " +
            "translation: a natural, singable-meaning translation into $lang; empty string if the line is already $lang. " +
            "Output only the JSON array.\n" + input
        val body = buildJsonObject {
            put("contents", buildJsonArray { add(buildJsonObject { put("parts", buildJsonArray { add(buildJsonObject { put("text", prompt) }) }) }) })
            put("generationConfig", buildJsonObject { put("responseMimeType", "application/json"); put("temperature", 0.2) })
        }
        val model = o.geminiModel.ifBlank { "gemini-2.5-flash" }
        val req = HttpRequest.newBuilder(URI("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"))
            .timeout(Duration.ofSeconds(60))
            .header("Content-Type", "application/json")
            .header("x-goog-api-key", o.geminiKey)
            .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build()
        val res = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (res.statusCode() != 200) {
            val msg = runCatching { Json.parseToJsonElement(res.body()).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content }.getOrNull()
            error("Gemini: ${msg ?: "HTTP ${res.statusCode()}"}")
        }
        val text = Json.parseToJsonElement(res.body()).jsonObject["candidates"]!!.jsonArray[0].jsonObject["content"]!!
            .jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        val arr = Json.parseToJsonElement(text).jsonArray
        val out = arrayOfNulls<Pair<String?, String?>>(texts.size)
        for (e in arr) {
            val obj = e as? JsonObject ?: continue
            val i = obj["i"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: continue
            if (i !in texts.indices) continue
            out[i] = (obj["romanization"]?.jsonPrimitive?.contentOrNull?.ifBlank { null }) to
                (obj["translation"]?.jsonPrimitive?.contentOrNull?.ifBlank { null })
        }
        return out.map { it ?: (null to null) }
    }
}

fun com.music.vivi.compose.prefs.DesktopPrefs.lyricsTransformOptions(): LyricsTransformOptions {
    val k = com.music.vivi.compose.prefs.PrefKeys
    return LyricsTransformOptions(
        romanize = getBoolean(k.LyricsRomanize, false),
        translate = getBoolean(k.LyricsAutoTranslate, false),
        targetLang = getString(k.LyricsTranslateLang, "en"),
        provider = runCatching { TranslateProvider.valueOf(getString(k.LyricsTranslateProvider, TranslateProvider.GOOGLE.name)) }
            .getOrDefault(TranslateProvider.GOOGLE),
        geminiKey = getString(k.LyricsGeminiKey, "").trim(),
        geminiModel = getString(k.LyricsGeminiModel, "gemini-2.5-flash").trim(),
    )
}
