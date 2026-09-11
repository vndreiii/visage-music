package com.music.lrclib

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class FixtureCase(
    val id: String,
    val kind: String,
    val artist: String,
    val title: String,
    val expect: String,
    val reason: String? = null,
)

@Serializable
data class FixtureFile(
    val cases: List<FixtureCase>,
)

object FixtureLoader {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(resourcePath: String): FixtureFile {
        val stream = requireNotNull(
            FixtureLoader::class.java.classLoader.getResourceAsStream(resourcePath),
        ) { "Missing fixture resource: $resourcePath" }
        return stream.bufferedReader().use { reader ->
            json.decodeFromString(FixtureFile.serializer(), reader.readText())
        }
    }
}
