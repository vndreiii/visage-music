package com.music.vivi.player.mpv

/** The audio signal path as libmpv reports it: the decoded stream, and what it sends on to the output device. */
data class AudioStreamInfo(
    val codec: String?,
    val bitrateKbps: Int?,
    val sourceRate: Int,
    val sourceFormat: String?,
    val sourceChannels: String?,
    val outRate: Int?,
    val outFormat: String?,
    val outChannels: String?,
) {
    companion object {
        /** 44100 -> "44.1 kHz". */
        fun khz(hz: Int): String = (hz / 1000.0).let { if (hz % 1000 == 0) "${hz / 1000} kHz" else "${"%.1f".format(java.util.Locale.ROOT, it)} kHz" }

        /** mpv sample format -> "16-bit", "float32"... */
        fun depth(format: String?): String? = when {
            format == null -> null
            format.startsWith("float") -> "float32"
            format.startsWith("double") -> "float64"
            else -> Regex("""[su](\d+)""").find(format)?.groupValues?.get(1)?.let { "$it-bit" }
        }
    }
}
