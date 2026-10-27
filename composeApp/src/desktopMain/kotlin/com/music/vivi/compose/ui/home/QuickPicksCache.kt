package com.music.vivi.compose.ui.home

import com.music.innertube.models.Album
import com.music.innertube.models.Artist
import com.music.innertube.models.SongItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File

/** Bounds and defaults for how often Quick picks are replaced on their own. */
object QuickPicksSchedule {
    const val MinHours = 1
    const val MaxHours = 168
    const val DefaultHours = 24
}

/** The Quick picks last shown, kept on disk so a refresh survives restarts until the next scheduled one. */
class QuickPicksCache(private val file: File) {
    class Snapshot(val savedAtMs: Long, val songs: List<SongItem>)

    fun load(): Snapshot? = runCatching {
        if (!file.exists()) return null
        val root = Json.parseToJsonElement(file.readText()).jsonObject
        val songs = root.getValue("songs").jsonArray.mapNotNull { it.toSong() }
        if (songs.isEmpty()) null else Snapshot(root.getValue("savedAt").jsonPrimitive.long, songs)
    }.getOrNull()

    fun save(songs: List<SongItem>, nowMs: Long = System.currentTimeMillis()) {
        runCatching {
            file.parentFile?.mkdirs()
            val root = JsonObject(
                mapOf(
                    "savedAt" to JsonPrimitive(nowMs),
                    "songs" to JsonArray(songs.map { it.toJson() }),
                ),
            )
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(root.toString())
            tmp.renameTo(file)
        }
    }

    private fun SongItem.toJson() = JsonObject(
        mapOf(
            "id" to JsonPrimitive(id),
            "title" to JsonPrimitive(title),
            "thumb" to JsonPrimitive(thumbnail),
            "explicit" to JsonPrimitive(explicit),
            "duration" to (duration?.let { JsonPrimitive(it) } ?: JsonNull),
            "album" to (album?.let { JsonArray(listOf(JsonPrimitive(it.name), JsonPrimitive(it.id))) } ?: JsonNull),
            "artists" to JsonArray(
                artists.map { JsonArray(listOf(JsonPrimitive(it.name), it.id?.let(::JsonPrimitive) ?: JsonNull)) },
            ),
        ),
    )

    private fun kotlinx.serialization.json.JsonElement.toSong(): SongItem? = runCatching {
        val o = jsonObject
        SongItem(
            id = o.getValue("id").jsonPrimitive.content,
            title = o.getValue("title").jsonPrimitive.content,
            thumbnail = o.getValue("thumb").jsonPrimitive.content,
            explicit = o["explicit"]?.jsonPrimitive?.booleanOrNull ?: false,
            duration = o["duration"]?.jsonPrimitive?.intOrNull,
            album = (o["album"] as? JsonArray)?.let { Album(it[0].jsonPrimitive.content, it[1].jsonPrimitive.content) },
            artists = o.getValue("artists").jsonArray.map {
                val a = it.jsonArray
                Artist(a[0].jsonPrimitive.content, a[1].jsonPrimitive.contentOrNull)
            },
        )
    }.getOrNull()
}
