package com.music.vivi.compose.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import com.music.vivi.compose.LocalPlaybackController
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.PrefKeys
import org.koin.compose.koinInject

/** Scrolling the mouse wheel over this element changes the playback volume (up = louder), like the mini player. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.volumeWheel(): Modifier {
    val controller = LocalPlaybackController.current
    val prefs = koinInject<DesktopPrefs>()
    return onPointerEvent(PointerEventType.Scroll) { event ->
        val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
        if (dy == 0f) return@onPointerEvent
        val next = (controller.volume.value + if (dy > 0f) -0.04f else 0.04f).coerceIn(0f, 1f)
        controller.setVolume(next)
        prefs.setFloat(PrefKeys.PlaybackVolume, next)
        event.changes.forEach { it.consume() }
    }
}
