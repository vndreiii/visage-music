package com.music.vivi.player.art

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.readBytes
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.pathString

/**
 * Caches remote artwork under ~/.cache/vivi-desktop/art for MPRIS file:// URLs.
 */
class ArtworkCache(
    private val cacheDir: Path = Path.of(
        System.getenv("XDG_CACHE_HOME")
            ?: Path.of(System.getProperty("user.home"), ".cache").pathString,
        "vivi-desktop",
        "art",
    ),
) : AutoCloseable {
    private val http = HttpClient(CIO)

    suspend fun fileUrlFor(videoId: String, remoteUrl: String?): String? {
        if (remoteUrl.isNullOrBlank()) return null
        Files.createDirectories(cacheDir)
        val target = cacheDir.resolve("$videoId.jpg")
        if (!target.exists()) {
            runCatching {
                val bytes = http.get(remoteUrl).readBytes()
                Files.write(target, bytes)
            }.getOrElse { return remoteUrl }
        }
        return "file://${target.toAbsolutePath()}"
    }

    override fun close() {
        http.close()
    }
}
