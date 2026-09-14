package com.music.vivi.compose

import androidx.compose.runtime.staticCompositionLocalOf
import com.music.vivi.player.PlaybackController

val LocalPlaybackController = staticCompositionLocalOf<PlaybackController> {
    error("PlaybackController not provided")
}
