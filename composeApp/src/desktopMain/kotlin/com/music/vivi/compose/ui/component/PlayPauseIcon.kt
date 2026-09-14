package com.music.vivi.compose.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun PlayPauseIcon(
    playing: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 28.dp,
    contentDescription: String? = null,
) {
    AnimatedContent(
        targetState = playing,
        transitionSpec = {
            (fadeIn() + scaleIn(initialScale = 0.75f)) togetherWith
                (fadeOut() + scaleOut(targetScale = 0.75f))
        },
        label = "playPauseMorph",
        modifier = modifier,
    ) { isPlaying ->
        Icon(
            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = contentDescription
                ?: if (isPlaying) "Pause" else "Play",
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}
