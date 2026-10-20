package com.music.vivi.compose.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.music.vivi.compose.ui.glass.LocalHazeState
import com.music.vivi.compose.ui.glass.glassActive
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

/** The framed panel behind the player controls; frosted glass over the player background when the blur setting is on. */
@Composable
fun Modifier.playerControlsSurface(framed: Boolean): Modifier {
    if (!framed) return this
    val shape = RoundedCornerShape(28.dp)
    val haze = LocalHazeState.current
    return if (haze != null && glassActive()) {
        clip(shape)
            .hazeChild(haze) {
                blurRadius = 32.dp
                tints = listOf(HazeTint(Color.Black.copy(alpha = 0.22f)))
                noiseFactor = 0.03f
            }
            .background(Color.White.copy(alpha = 0.04f), shape)
            .padding(20.dp)
    } else {
        background(Color.Black.copy(alpha = .28f), shape).padding(20.dp)
    }
}
