package com.music.vivi.player.login

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.seconds

/**
 * Minimal Chrome DevTools Protocol client for cookie + JS capture from a
 * Chromium instance started with --remote-debugging-port.
 */
class CdpClient(
    private val port: Int,
) : AutoCloseable {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val http = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
        install(WebSockets)
    }

    private val nextId = AtomicLong(1)
    private var session: WebSocketSession? = null

    suspend fun waitUntilReady(timeoutMs: Long = 30_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val ok = runCatching { http.get("http://127.0.0.1:$port/json/version").status }.getOrNull()
            if (ok?.value in 200..299) return
            delay(250)
        }
        error("Chromium DevTools port $port did not become ready")
    }

    suspend fun connectToBestTarget() {
        waitUntilReady()
        val targets: List<CdpTarget> = http.get("http://127.0.0.1:$port/json/list").body()
        val preferred = targets.firstOrNull {
            it.type == "page" && (
                it.url.startsWith("https://music.youtube.com") ||
                    it.url.startsWith("https://accounts.google.com") ||
                    it.url.startsWith("https://accounts.youtube.com")
                )
        } ?: targets.firstOrNull { it.type == "page" && it.webSocketDebuggerUrl != null }
            ?: error("No Chromium page targets found")

        runCatching { session?.close() }
        val wsUrl = preferred.webSocketDebuggerUrl
            ?: error("Target has no webSocketDebuggerUrl")
        session = http.webSocketSession(urlString = wsUrl)
        call("Runtime.enable")
        call("Network.enable")
        call("Page.enable")
    }

    suspend fun currentUrl(): String {
        val result = call(
            "Runtime.evaluate",
            buildJsonObject {
                put("expression", "location.href")
                put("returnByValue", true)
            },
        )
        return result.resultValueString().orEmpty()
    }

    suspend fun cookieHeader(
        urls: List<String> = listOf("https://music.youtube.com", "https://www.youtube.com"),
    ): String {
        val result = call(
            "Network.getCookies",
            buildJsonObject {
                put("urls", JsonArray(urls.map { JsonPrimitive(it) }))
            },
        )
        val cookies = result["result"]?.jsonObject?.get("cookies")?.jsonArray.orEmpty()
        return cookies.mapNotNull { el ->
            val obj = el.jsonObject
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val value = obj["value"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            "$name=$value"
        }.distinctBy { it.substringBefore('=') }.joinToString("; ")
    }

    suspend fun evaluateString(expression: String): String {
        val result = call(
            "Runtime.evaluate",
            buildJsonObject {
                put("expression", expression)
                put("returnByValue", true)
                put("awaitPromise", false)
            },
        )
        return result.resultValueString().orEmpty()
    }

    private suspend fun call(method: String, params: JsonObject? = null): JsonObject {
        val ws = session ?: error("CDP not connected")
        val id = nextId.getAndIncrement()
        val payload = buildJsonObject {
            put("id", id)
            put("method", method)
            if (params != null) put("params", params)
        }
        ws.send(Frame.Text(json.encodeToString(JsonElement.serializer(), payload)))

        return withTimeout(10.seconds) {
            while (true) {
                val frame = ws.incoming.receive() as? Frame.Text ?: continue
                val obj = json.parseToJsonElement(frame.readText()).jsonObject
                if (obj["id"]?.jsonPrimitive?.content?.toLongOrNull() == id) {
                    obj["error"]?.let { err ->
                        error("CDP $method failed: $err")
                    }
                    return@withTimeout obj
                }
            }
            @Suppress("UNREACHABLE_CODE")
            error("unreachable")
        }
    }

    private fun JsonObject.resultValueString(): String? {
        val value = this["result"]?.jsonObject
            ?.get("result")?.jsonObject
            ?.get("value")
            ?: return null
        return when (value) {
            is JsonNull -> null
            is JsonPrimitive -> value.contentOrNull
            else -> value.toString()
        }
    }

    override fun close() {
        runCatching {
            runBlocking {
                session?.close()
            }
        }
        session = null
        http.close()
    }

    @Serializable
    data class CdpTarget(
        val id: String? = null,
        val type: String? = null,
        val title: String? = null,
        val url: String = "",
        @SerialName("webSocketDebuggerUrl")
        val webSocketDebuggerUrl: String? = null,
    )
}

suspend fun CdpClient.reconnectLoop(attempts: Int = 5) {
    var last: Throwable? = null
    repeat(attempts) {
        try {
            connectToBestTarget()
            return
        } catch (t: Throwable) {
            last = t
            delay(500)
        }
    }
    throw last ?: error("CDP connect failed")
}
