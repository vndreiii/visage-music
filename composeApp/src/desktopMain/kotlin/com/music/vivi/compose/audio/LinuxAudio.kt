package com.music.vivi.compose.audio

import com.music.vivi.player.mpv.MpvPlayer
import com.music.vivi.player.stream.YtDlpBinary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.TimeUnit

enum class AudioBackend(val label: String) { PIPEWIRE("PipeWire"), PULSE("PulseAudio"), ALSA("ALSA") }

/** What the output picker found: the backend that answered, its devices, and what was missing on the way. */
class AudioListing(
    val backend: AudioBackend?,
    val devices: List<AudioOutputDevice>,
    /** Fallback notes ("PipeWire tools not found, using PulseAudio") for a toast. Empty when the first choice worked. */
    val problems: List<String>,
) {
    /** Set when nothing worked at all. */
    val fatal: String? =
        if (backend == null) "No audio output backend found. Install PipeWire (wireplumber), PulseAudio (pulseaudio-utils) or ALSA (alsa-utils)." else null
}

/**
 * Linux output devices, tried in the same order playback uses: PipeWire (pw-dump / wpctl), then PulseAudio (pactl),
 * then ALSA (aplay). Each step reports why it was skipped so the UI can say what is missing.
 */
object LinuxAudio {
    suspend fun list(): AudioListing = withContext(Dispatchers.IO) {
        val problems = mutableListOf<String>()

        when (val pw = pipeWire()) {
            is Attempt.Ok -> return@withContext AudioListing(AudioBackend.PIPEWIRE, pw.devices, problems)
            is Attempt.Skipped -> problems += pw.reason + " Trying PulseAudio."
        }
        when (val pa = pulse()) {
            is Attempt.Ok -> return@withContext AudioListing(AudioBackend.PULSE, pa.devices, problems)
            is Attempt.Skipped -> problems += pa.reason + " Trying ALSA."
        }
        when (val al = alsa()) {
            is Attempt.Ok -> AudioListing(AudioBackend.ALSA, al.devices, problems)
            is Attempt.Skipped -> AudioListing(null, emptyList(), problems + al.reason)
        }
    }

    /** Makes [name] the output. PipeWire and Pulse change the system default; ALSA routes this app's player. */
    suspend fun select(backend: AudioBackend, name: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            when (backend) {
                AudioBackend.PIPEWIRE -> pipeWireSetDefault(name)
                AudioBackend.PULSE -> {
                    require(YtDlpBinary.which("pactl") != null) { "pactl not found (install pulseaudio-utils)." }
                    check(run("pactl", "set-default-sink", name).first == 0) { "PulseAudio refused to switch to that output." }
                }
                AudioBackend.ALSA -> check(MpvPlayer.selectAudioDevice("alsa/$name")) { "The player isn't running yet." }
            }
        }
    }

    // ── PipeWire ──────────────────────────────────────────────────────────────────────────────

    private fun pipeWire(): Attempt {
        if (YtDlpBinary.which("pw-dump") == null) {
            return Attempt.Skipped("PipeWire tools not found (pw-dump). Install pipewire or pipewire-tools.")
        }
        val (code, out) = run("pw-dump")
        if (code != 0 || out.isBlank()) return Attempt.Skipped("PipeWire isn't running.")
        val root = runCatching { Json.parseToJsonElement(out) as? JsonArray }.getOrNull()
            ?: return Attempt.Skipped("PipeWire gave an unreadable answer.")
        val defaultName = defaultSinkName(root)
        val sinks = root.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            if (o["type"]?.jsonPrimitive?.contentOrNull != "PipeWire:Interface:Node") return@mapNotNull null
            val props = (o["info"] as? JsonObject)?.get("props") as? JsonObject ?: return@mapNotNull null
            if (props["media.class"]?.jsonPrimitive?.contentOrNull != "Audio/Sink") return@mapNotNull null
            val name = props["node.name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            AudioOutputDevice(
                name = name,
                description = props["node.description"]?.jsonPrimitive?.contentOrNull ?: name,
                state = (o["info"] as? JsonObject)?.get("state")?.jsonPrimitive?.contentOrNull.orEmpty(),
                iconName = props["device.icon-name"]?.jsonPrimitive?.contentOrNull,
                isDefault = name == defaultName,
            )
        }
        return if (sinks.isEmpty()) Attempt.Skipped("PipeWire has no audio outputs.") else Attempt.Ok(sinks)
    }

    private fun defaultSinkName(root: JsonArray): String? {
        for (el in root) {
            val o = el as? JsonObject ?: continue
            if (o["type"]?.jsonPrimitive?.contentOrNull != "PipeWire:Interface:Metadata") continue
            val entries = o["metadata"] as? JsonArray ?: continue
            for (key in listOf("default.audio.sink", "default.configured.audio.sink")) {
                entries.firstOrNull { (it as? JsonObject)?.get("key")?.jsonPrimitive?.contentOrNull == key }
                    ?.let { it as JsonObject }?.get("value")?.let { v ->
                        val obj = (v as? JsonObject) ?: (v as? JsonPrimitive)?.contentOrNull?.let {
                            runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull()
                        }
                        obj?.get("name")?.jsonPrimitive?.contentOrNull?.let { return it }
                    }
            }
        }
        return null
    }

    private fun pipeWireSetDefault(name: String) {
        val wpctl = YtDlpBinary.which("wpctl")
        if (wpctl != null) {
            // wpctl wants the object id, which pw-dump gives next to the node name.
            val root = Json.parseToJsonElement(run("pw-dump").second) as JsonArray
            val id = root.firstNotNullOfOrNull { el ->
                val o = el as? JsonObject ?: return@firstNotNullOfOrNull null
                val props = (o["info"] as? JsonObject)?.get("props") as? JsonObject
                if (props?.get("node.name")?.jsonPrimitive?.contentOrNull == name) o["id"]?.jsonPrimitive?.contentOrNull else null
            } ?: error("That output disappeared.")
            check(run(wpctl, "set-default", id).first == 0) { "wpctl couldn't switch to that output." }
            return
        }
        val meta = YtDlpBinary.which("pw-metadata") ?: error("Neither wpctl nor pw-metadata found (install wireplumber).")
        val json = """{"name":"$name"}"""
        check(run(meta, "0", "default.audio.sink", json, "Spa:String:JSON").first == 0) { "pw-metadata couldn't switch outputs." }
        run(meta, "0", "default.configured.audio.sink", json, "Spa:String:JSON")
    }

    // ── PulseAudio ────────────────────────────────────────────────────────────────────────────

    private fun pulse(): Attempt {
        if (YtDlpBinary.which("pactl") == null) return Attempt.Skipped("PulseAudio tools not found (pactl). Install pulseaudio-utils.")
        val (code, out) = run("pactl", "list", "sinks")
        if (code != 0) return Attempt.Skipped("PulseAudio isn't running.")
        val default = run("pactl", "get-default-sink").second.trim()
        val devices = PipeWireAudio.parseSinksPublic(out, default)
        return if (devices.isEmpty()) Attempt.Skipped("PulseAudio has no audio outputs.") else Attempt.Ok(devices)
    }

    // ── ALSA ──────────────────────────────────────────────────────────────────────────────────

    private fun alsa(): Attempt {
        if (YtDlpBinary.which("aplay") == null) return Attempt.Skipped("ALSA tools not found (aplay). Install alsa-utils.")
        val (code, out) = run("aplay", "-L")
        if (code != 0) return Attempt.Skipped("ALSA couldn't list outputs.")
        val chosen = MpvPlayer.audioDevice.removePrefix("alsa/").takeIf { MpvPlayer.audioDevice.startsWith("alsa/") }
        val devices = mutableListOf<AudioOutputDevice>()
        var name: String? = null
        val desc = StringBuilder()
        fun flush() {
            val n = name ?: return
            if (n == "default" || n.startsWith("sysdefault:") || n.startsWith("hdmi:") || n.startsWith("front:")) {
                devices += AudioOutputDevice(
                    name = n,
                    description = desc.toString().trim().ifEmpty { n },
                    state = "",
                    iconName = if (n.startsWith("hdmi:")) "video-display" else null,
                    isDefault = if (chosen != null) n == chosen else n == "default",
                )
            }
        }
        out.lineSequence().forEach { line ->
            if (line.isNotEmpty() && !line[0].isWhitespace()) {
                flush(); name = line.trim(); desc.clear()
            } else if (line.isNotBlank()) desc.append(line.trim()).append(" · ")
        }
        flush()
        val cleaned = devices.map { it.copy(description = it.description.trimEnd(' ', '·')) }
        return if (cleaned.isEmpty()) Attempt.Skipped("ALSA has no usable outputs.") else Attempt.Ok(cleaned)
    }

    // ── helpers ───────────────────────────────────────────────────────────────────────────────

    private sealed interface Attempt {
        class Ok(val devices: List<AudioOutputDevice>) : Attempt
        class Skipped(val reason: String) : Attempt
    }

    private fun run(vararg cmd: String): Pair<Int, String> = runCatching {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        if (!p.waitFor(4, TimeUnit.SECONDS)) { p.destroyForcibly(); return -1 to "" }
        p.exitValue() to out
    }.getOrDefault(-1 to "")
}
