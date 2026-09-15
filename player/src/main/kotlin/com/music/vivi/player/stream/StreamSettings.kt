package com.music.vivi.player.stream

enum class StreamQuality { AUTO, HIGH, LOW }

data class StreamSettings(
    val saavnEnabled: Boolean = SaavnStreamResolver.defaultEnabled(),
    val quality: StreamQuality = StreamQuality.AUTO,
)
