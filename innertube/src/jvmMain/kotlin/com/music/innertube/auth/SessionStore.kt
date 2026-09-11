package com.music.innertube.auth

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

private val sessionJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private const val ENV_COOKIE = "VIVI_YT_COOKIE"
private const val ENV_VISITOR_DATA = "VIVI_YT_VISITOR_DATA"
private const val ENV_DATASYNC_ID = "VIVI_YT_DATASYNC_ID"

/**
 * Loads a session from process environment.
 *
 * Required: [ENV_COOKIE], [ENV_VISITOR_DATA].
 * Optional: [ENV_DATASYNC_ID] (defaults to empty).
 *
 * @param getenv env lookup; defaults to [System.getenv] for production use.
 */
fun loadSessionFromEnv(
    getenv: (String) -> String? = System::getenv,
): YtMusicSession? {
    val cookie = getenv(ENV_COOKIE)?.takeIf { it.isNotBlank() } ?: return null
    val visitorData = getenv(ENV_VISITOR_DATA)?.takeIf { it.isNotBlank() } ?: return null
    val dataSyncId = getenv(ENV_DATASYNC_ID).orEmpty()
    return YtMusicSession(
        cookie = cookie,
        visitorData = visitorData,
        dataSyncId = dataSyncId,
    )
}

fun loadSessionFromFile(path: Path): YtMusicSession {
    val text = Files.readString(path)
    return sessionJson.decodeFromString(YtMusicSession.serializer(), text)
}

fun saveSessionToFile(path: Path, session: YtMusicSession) {
    path.parent?.let { parent ->
        if (!Files.exists(parent)) {
            Files.createDirectories(parent)
        }
    }
    Files.writeString(path, sessionJson.encodeToString(session))
}
