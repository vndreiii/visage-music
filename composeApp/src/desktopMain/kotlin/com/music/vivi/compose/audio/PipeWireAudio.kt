package com.music.vivi.compose.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AudioOutputDevice(
    val name: String,
    val description: String,
    val state: String,
    val iconName: String?,
    val isDefault: Boolean,
)

/**
 * PipeWire / PulseAudio sinks via [pactl] (PipeWire's Pulse compatibility layer).
 */
object PipeWireAudio {
    suspend fun listSinks(): List<AudioOutputDevice> = withContext(Dispatchers.IO) {
        runCatching {
            val default = exec("pactl", "get-default-sink").trim()
            val raw = exec("pactl", "list", "sinks")
            parseSinks(raw, default)
        }.getOrElse { emptyList() }
    }

    suspend fun setDefaultSink(name: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            ProcessBuilder("pactl", "set-default-sink", name)
                .redirectErrorStream(true)
                .start()
                .waitFor() == 0
        }.getOrDefault(false)
    }

    private fun exec(vararg cmd: String): String {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        return out
    }

    private fun parseSinks(raw: String, defaultName: String): List<AudioOutputDevice> {
        val blocks = raw.split(Regex("(?=Sink #)"))
            .map { it.trim() }
            .filter { it.startsWith("Sink #") }
        return blocks.mapNotNull { block ->
            val name = block.lineValue("Name:") ?: return@mapNotNull null
            val description = block.lineValue("Description:") ?: name
            val state = block.lineValue("State:") ?: "UNKNOWN"
            val icon = Regex("""device\.icon_name\s*=\s*"([^"]+)"""")
                .find(block)
                ?.groupValues
                ?.getOrNull(1)
            AudioOutputDevice(
                name = name,
                description = description,
                state = state,
                iconName = icon,
                isDefault = name == defaultName,
            )
        }
    }

    private fun String.lineValue(key: String): String? =
        lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith(key) }
            ?.removePrefix(key)
            ?.trim()
}
