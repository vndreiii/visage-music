package com.music.vivi.compose.ui.player.lyrics

import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.PrefKeys
import com.music.vivi.compose.prefs.SecretStore
import com.music.vivi.compose.prefs.Secrets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.TimeUnit

/** What a provider needs to make one request; stored per provider in prefs. */
data class AiConfig(val key: String, val model: String, val baseUrl: String)

/**
 * A text-completion backend for lyric romanization / translation. To support another service, add one
 * object to [LyricsAiProviders.all]; the settings screen (key field, model dropdown, base URL) and the
 * request path are driven entirely by these members.
 */
interface LyricsAiProvider {
    val id: String
    val label: String
    val needsKey: Boolean get() = true
    val keyHint: String get() = ""

    /** Heading this provider is listed under in the picker (see [LyricsAiProviders.Groups]). */
    val group: String get() = LyricsAiProviders.Groups.CLOUD

    /** One line shown beside the name in the picker. */
    val hint: String get() = if (needsKey) "needs API key" else "no key needed"

    /** Non-null when the endpoint (or command) is user-editable; the label of that field. */
    val baseUrlLabel: String? get() = null

    /** Extra explanation under the endpoint field. */
    val baseUrlHelp: String? get() = null
    val defaultBaseUrl: String get() = ""
    val suggestedModels: List<String>

    /** The live model list; throws when it cannot be fetched (the UI then falls back to [suggestedModels]). */
    fun listModels(cfg: AiConfig): List<String> = suggestedModels

    /** Sends [prompt] and returns the model's raw text answer. Blocking; call off the main thread. */
    fun complete(prompt: String, cfg: AiConfig): String
}

internal object AiHttp {
    val client: HttpClient by lazy { HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build() }

    fun send(req: HttpRequest, what: String): String {
        val res = client.send(req, HttpResponse.BodyHandlers.ofString())
        if (res.statusCode() !in 200..299) {
            val msg = runCatching {
                val e = Json.parseToJsonElement(res.body()).jsonObject["error"]
                (e as? JsonObject)?.get("message")?.jsonPrimitive?.content ?: e?.jsonPrimitive?.contentOrNull
            }.getOrNull()
            error("$what: ${msg ?: "HTTP ${res.statusCode()}"}")
        }
        return res.body()
    }

    fun post(url: String, body: JsonObject, headers: Map<String, String>, timeoutSec: Long = 90): HttpRequest =
        HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(timeoutSec))
            .header("Content-Type", "application/json")
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build()

    fun get(url: String, headers: Map<String, String>): HttpRequest =
        HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(15))
            .apply { headers.forEach { (k, v) -> header(k, v) } }.GET().build()
}

private object GeminiProvider : LyricsAiProvider {
    override val id = "gemini"
    override val label = "Gemini"
    override val keyHint = "Free key at aistudio.google.com"
    override val hint = "free key from AI Studio"
    override val suggestedModels = listOf("gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.5-pro", "gemini-2.0-flash")

    override fun listModels(cfg: AiConfig): List<String> {
        val body = AiHttp.send(AiHttp.get("https://generativelanguage.googleapis.com/v1beta/models?pageSize=200", mapOf("x-goog-api-key" to cfg.key)), "Gemini")
        return Json.parseToJsonElement(body).jsonObject["models"]!!.jsonArray.mapNotNull { m ->
            val o = m.jsonObject
            val methods = o["supportedGenerationMethods"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
            if ("generateContent" !in methods) null else o["name"]?.jsonPrimitive?.content?.removePrefix("models/")
        }.filter { it.startsWith("gemini") }.sorted()
    }

    override fun complete(prompt: String, cfg: AiConfig): String {
        val body = buildJsonObject {
            put("contents", buildJsonArray { add(buildJsonObject { put("parts", buildJsonArray { add(buildJsonObject { put("text", prompt) }) }) }) })
            put("generationConfig", buildJsonObject { put("responseMimeType", "application/json"); put("temperature", 0.2) })
        }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/${cfg.model}:generateContent"
        val res = AiHttp.send(AiHttp.post(url, body, mapOf("x-goog-api-key" to cfg.key)), "Gemini")
        return Json.parseToJsonElement(res).jsonObject["candidates"]!!.jsonArray[0].jsonObject["content"]!!
            .jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
    }
}

private object AnthropicProvider : LyricsAiProvider {
    override val id = "anthropic"
    override val label = "Anthropic"
    override val keyHint = "Key from console.anthropic.com"
    override val suggestedModels = listOf("claude-haiku-5-5", "claude-sonnet-5-5", "claude-opus-5-5")
    private fun headers(k: String) = mapOf("x-api-key" to k, "anthropic-version" to "2023-06-01")

    override fun listModels(cfg: AiConfig): List<String> {
        val body = AiHttp.send(AiHttp.get("https://api.anthropic.com/v1/models?limit=100", headers(cfg.key)), "Anthropic")
        return Json.parseToJsonElement(body).jsonObject["data"]!!.jsonArray.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }
    }

    override fun complete(prompt: String, cfg: AiConfig): String {
        val body = buildJsonObject {
            put("model", cfg.model); put("max_tokens", 8192); put("temperature", 0.2)
            put("messages", buildJsonArray { add(buildJsonObject { put("role", "user"); put("content", prompt) }) })
        }
        val res = AiHttp.send(AiHttp.post("https://api.anthropic.com/v1/messages", body, headers(cfg.key)), "Anthropic")
        return Json.parseToJsonElement(res).jsonObject["content"]!!.jsonArray
            .mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }.joinToString("")
    }
}

/** Anything that speaks the OpenAI chat-completions dialect: OpenAI, OpenRouter, Groq, Ollama, LM Studio, ... */
private class OpenAiCompatProvider(
    override val id: String,
    override val label: String,
    override val defaultBaseUrl: String,
    override val suggestedModels: List<String>,
    override val needsKey: Boolean = true,
    override val keyHint: String = "",
    editableUrl: Boolean = false,
    override val group: String = LyricsAiProviders.Groups.CLOUD,
    override val hint: String = if (needsKey) "needs API key" else "runs on this machine",
    private val extraHeaders: Map<String, String> = emptyMap(),
) : LyricsAiProvider {
    override val baseUrlLabel: String? = if (editableUrl) "API base URL" else null
    private fun base(cfg: AiConfig) = cfg.baseUrl.ifBlank { defaultBaseUrl }.trimEnd('/')
    private fun headers(cfg: AiConfig) = extraHeaders + if (cfg.key.isNotBlank()) mapOf("Authorization" to "Bearer ${cfg.key}") else emptyMap()

    override fun listModels(cfg: AiConfig): List<String> {
        val body = AiHttp.send(AiHttp.get(base(cfg) + "/models", headers(cfg)), label)
        val data = Json.parseToJsonElement(body).jsonObject["data"]!!.jsonArray
        return data.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }.sorted()
    }

    override fun complete(prompt: String, cfg: AiConfig): String {
        val body = buildJsonObject {
            put("model", cfg.model); put("temperature", 0.2)
            put("messages", buildJsonArray { add(buildJsonObject { put("role", "user"); put("content", prompt) }) })
        }
        val res = AiHttp.send(AiHttp.post(base(cfg) + "/chat/completions", body, headers(cfg)), label)
        return Json.parseToJsonElement(res).jsonObject["choices"]!!.jsonArray[0].jsonObject["message"]!!
            .jsonObject["content"]!!.jsonPrimitive.content
    }
}

/** Runs a local command-line agent; the prompt goes in on stdin / argv and the answer comes back on stdout. */
private abstract class CliProvider : LyricsAiProvider {
    override val needsKey = false

    abstract fun command(cfg: AiConfig, prompt: String): List<String>
    open fun feedStdin(prompt: String): String? = prompt

    /** Called with the scratch directory before launch, e.g. to drop in a config that denies tools. */
    open fun prepare(dir: java.nio.file.Path) {}

    override fun complete(prompt: String, cfg: AiConfig): String {
        // A scratch directory keeps the agent away from the user's projects; the lyrics are untrusted text,
        // so tools are switched off wherever the CLI allows it (see each provider's command).
        val dir = Files.createTempDirectory("visage-ai-")
        try {
            prepare(dir)
            val pb = ProcessBuilder(command(cfg, prompt)).directory(dir.toFile()).redirectErrorStream(false)
            val p = try {
                pb.start()
            } catch (e: java.io.IOException) {
                error("Could not start '${command(cfg, prompt).first()}': is it installed and on PATH?")
            }
            feedStdin(prompt).let { input ->
                p.outputStream.use { if (input != null) it.write(input.toByteArray()) }
            }
            val err = StringBuilder()
            val errThread = Thread { p.errorStream.bufferedReader().useLines { ls -> ls.take(20).forEach { err.appendLine(it) } } }.also { it.isDaemon = true; it.start() }
            val out = p.inputStream.bufferedReader().readText()
            if (!p.waitFor(180, TimeUnit.SECONDS)) {
                p.destroyForcibly()
                error("$label timed out")
            }
            errThread.join(500)
            if (p.exitValue() != 0) error("$label: ${err.toString().trim().lines().lastOrNull().orEmpty().take(150).ifBlank { "exit ${p.exitValue()}" }}")
            return out
        } finally {
            runCatching {
                // Files.walk does not follow symlinks, so a link left in the scratch dir is deleted, never entered.
                Files.walk(dir).use { w -> w.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) } }
            }
        }
    }
}

private object ClaudeCodeProvider : CliProvider() {
    override val id = "claude-code"
    override val label = "Claude Code (local)"
    override val keyHint = "Uses your claude login; no key needed"
    override val group = LyricsAiProviders.Groups.AGENTS
    override val hint = "uses your claude login"
    override val suggestedModels = listOf("haiku", "sonnet", "opus")
    override fun command(cfg: AiConfig, prompt: String) = listOf(
        "claude", "-p", "--model", cfg.model.ifBlank { "haiku" },
        "--tools", "", "--strict-mcp-config", "--no-session-persistence", "--disable-slash-commands",
    )
}

private object OpenCodeProvider : CliProvider() {
    override val id = "opencode"
    override val label = "opencode (local)"
    override val group = LyricsAiProviders.Groups.AGENTS
    override val hint = "uses your opencode setup"
    override val suggestedModels = listOf("opencode/big-pickle")
    override fun listModels(cfg: AiConfig): List<String> {
        val p = ProcessBuilder("opencode", "models").redirectErrorStream(true).start()
        val lines = p.inputStream.bufferedReader().readLines().map { it.trim() }.filter { it.contains('/') && !it.contains(' ') }
        p.waitFor(20, TimeUnit.SECONDS)
        return lines.ifEmpty { error("opencode listed no models") }
    }
    // The lyrics are untrusted: an opencode.json in the scratch cwd denies every tool so an injected line cannot act.
    override fun prepare(dir: java.nio.file.Path) {
        Files.writeString(
            dir.resolve("opencode.json"),
            """{"permission":{"*":"deny"},"tools":{"*":false},"mcp":{}}""",
        )
    }
    override fun command(cfg: AiConfig, prompt: String) = listOf("opencode", "run", "--pure", "-m", cfg.model.ifBlank { "opencode/big-pickle" }, "--", prompt)
    override fun feedStdin(prompt: String): String? = null
}

/** Bring-your-own command: the prompt arrives on stdin, the answer is read from stdout. {model} is substituted. */
private object CustomCommandProvider : CliProvider() {
    override val id = "custom-cmd"
    override val label = "Custom command"
    override val group = LyricsAiProviders.Groups.LOCAL
    override val hint = "any command that reads stdin"
    override val baseUrlLabel = "Command"
    override val baseUrlHelp = "The prompt arrives on stdin and the answer is read from stdout; {model} is replaced by the model below"
    override val defaultBaseUrl = "ollama run {model}"
    override val suggestedModels = listOf("llama3.2")
    override fun command(cfg: AiConfig, prompt: String): List<String> =
        cfg.baseUrl.ifBlank { defaultBaseUrl }.replace("{model}", cfg.model).trim().split(Regex("\\s+"))
}

object LyricsAiProviders {
    /** Picker headings, in display order after "Free". */
    object Groups {
        const val FREE = "Free"
        const val CLOUD = "Cloud APIs"
        const val LOCAL = "Local"
        const val AGENTS = "Local agents / CLI"
        val order = listOf(FREE, CLOUD, LOCAL, AGENTS)
    }

    val all: List<LyricsAiProvider> = listOf(
        GeminiProvider,
        OpenAiCompatProvider(
            "openrouter", "OpenRouter", "https://openrouter.ai/api/v1",
            listOf("google/gemini-2.5-flash", "anthropic/claude-haiku-4.5", "openai/gpt-4o-mini", "deepseek/deepseek-chat"),
            keyHint = "Key from openrouter.ai/keys",
        ),
        OpenAiCompatProvider("openai", "OpenAI", "https://api.openai.com/v1", listOf("gpt-4o-mini", "gpt-4.1-mini", "gpt-4.1"), keyHint = "Key from platform.openai.com"),
        AnthropicProvider,
        OpenAiCompatProvider("groq", "Groq", "https://api.groq.com/openai/v1", listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant"), keyHint = "Key from console.groq.com"),
        OpenAiCompatProvider("mistral", "Mistral", "https://api.mistral.ai/v1", listOf("mistral-small-latest", "mistral-large-latest"), keyHint = "Key from console.mistral.ai"),
        OpenAiCompatProvider("deepseek", "DeepSeek", "https://api.deepseek.com/v1", listOf("deepseek-chat"), keyHint = "Key from platform.deepseek.com"),
        OpenAiCompatProvider(
            "ollama", "Ollama (local)", "http://localhost:11434/v1", listOf("llama3.2"),
            needsKey = false, editableUrl = true, group = LyricsAiProviders.Groups.LOCAL, hint = "runs on this machine",
        ),
        OpenAiCompatProvider(
            "openai-compat", "Custom OpenAI-compatible", "http://localhost:1234/v1", emptyList(),
            needsKey = false, keyHint = "Optional", editableUrl = true, group = LyricsAiProviders.Groups.LOCAL,
            hint = "LM Studio, vLLM, llama.cpp and similar",
        ),
        ClaudeCodeProvider,
        OpenCodeProvider,
        CustomCommandProvider,
    )

    fun byId(id: String): LyricsAiProvider? = all.firstOrNull { it.id == id }

    private fun k(id: String, field: String) = "lyricsAi.$id.$field"

    /** Reads model / endpoint from prefs and the API key from the secret store's in-memory cache (never the settings file). */
    fun config(prefs: DesktopPrefs, p: LyricsAiProvider, secrets: SecretStore = Secrets.store) = AiConfig(
        key = secrets.get(Secrets.lyricsAiKey(p.id)).trim(),
        model = prefs.getString(k(p.id, "model"), p.suggestedModels.firstOrNull().orEmpty()).trim(),
        baseUrl = prefs.getString(k(p.id, "url"), p.defaultBaseUrl).trim(),
    )

    /** Saves model / url to prefs. Keys go through [saveKey]. */
    fun save(prefs: DesktopPrefs, p: LyricsAiProvider, field: String, value: String) {
        require(field != "key") { "API keys are stored with saveKey, not in prefs" }
        prefs.setString(k(p.id, field), value.trim())
    }

    /** Stores the API key in the secret store; a stale plain-text copy from an older build is removed so it cannot come back. */
    fun saveKey(prefs: DesktopPrefs, p: LyricsAiProvider, value: String, secrets: SecretStore = Secrets.store) {
        secrets.set(Secrets.lyricsAiKey(p.id), value)
        prefs.remove(k(p.id, "key"))
        if (p.id == "gemini") prefs.remove(PrefKeys.LyricsGeminiKey)
    }
}
