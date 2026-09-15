package com.music.vivi.compose.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

fun Modifier.playerControlsSurface(framed: Boolean): Modifier = if (framed) {
    background(Color.Black.copy(alpha = .28f), RoundedCornerShape(28.dp)).padding(20.dp)
} else this
