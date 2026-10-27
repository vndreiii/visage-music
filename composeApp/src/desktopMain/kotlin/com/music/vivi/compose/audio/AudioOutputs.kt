package com.music.vivi.compose.audio

import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.PrefKeys
import com.music.vivi.nativelib.HostOs
import com.music.vivi.player.mpv.MpvPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Output device picker backend. Linux tries PipeWire, then PulseAudio, then ALSA (see [LinuxAudio]); Windows lists
 * the devices libmpv sees (`audio-device-list`, e.g. `wasapi/{guid}`) and sets `audio-device`.
 */
object AudioOutputs {
    /** The Linux backend that answered the last listing; selection goes to the same one. */
    @Volatile private var backend: AudioBackend? = null

    /** Applies the saved output to the mpv engines before they are created (Windows device, or an ALSA pick). */
    fun restore(prefs: DesktopPrefs) {
        val saved = prefs.getString(PrefKeys.AudioOutputDevice, "auto")
        if (HostOs.isWindows || saved.startsWith("alsa/")) MpvPlayer.presetAudioDevice(saved)
    }

    suspend fun listing(): AudioListing {
        if (!HostOs.isWindows) return LinuxAudio.list().also { backend = it.backend }
        val devices = withContext(Dispatchers.IO) {
            val current = MpvPlayer.audioDevice
            MpvPlayer.listAudioDevices().map { device ->
                AudioOutputDevice(device.name, device.description, "", null, device.name == current)
            }
        }
        return AudioListing(null, devices, emptyList())
    }

    /** Returns null on success, otherwise a short message for a toast. */
    suspend fun select(name: String, prefs: DesktopPrefs): String? {
        if (!HostOs.isWindows) {
            val b = backend ?: return "No audio backend available."
            return LinuxAudio.select(b, name).fold(
                onSuccess = {
                    // Only ALSA is per-app; remember it so the next start uses it. The others are system defaults.
                    prefs.setString(PrefKeys.AudioOutputDevice, if (b == AudioBackend.ALSA) "alsa/$name" else "auto")
                    null
                },
                onFailure = { it.message ?: "Couldn't switch the output." },
            )
        }
        return withContext(Dispatchers.IO) {
            if (MpvPlayer.selectAudioDevice(name)) { prefs.setString(PrefKeys.AudioOutputDevice, name); null }
            else "The player isn't running yet."
        }
    }
}
