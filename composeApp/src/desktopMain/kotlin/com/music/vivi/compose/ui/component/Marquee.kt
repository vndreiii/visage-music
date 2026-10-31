package com.music.vivi.compose.ui.component

import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * For single-line text that may be wider than its box: instead of cutting it off with "…" it scrolls sideways
 * (after a short pause, forever). Use with `maxLines = 1` and `overflow = TextOverflow.Clip`; with Ellipsis the text
 * is shortened before the marquee ever sees it. Text that fits does not move.
 */
fun Modifier.visageMarquee(): Modifier = basicMarquee(
    iterations = Int.MAX_VALUE,
    initialDelayMillis = 1_200,
    repeatDelayMillis = 1_500,
    spacing = MarqueeSpacing(32.dp),
    velocity = 36.dp,
)
