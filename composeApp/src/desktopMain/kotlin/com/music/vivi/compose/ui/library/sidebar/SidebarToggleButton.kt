package com.music.vivi.compose.ui.library.sidebar

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.music.vivi.compose.ui.component.interactiveScale
import com.music.vivi.compose.ui.motion.VisageMotion

/** Small top-bar toggle for a sidebar; tints with the primary container while its sidebar is open. */
@Composable
fun SidebarToggleButton(icon: ImageVector, description: String, active: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0f),
        VisageMotion.effectsDefault(), label = "toggleBg",
    )
    val fg by animateColorAsState(
        if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        VisageMotion.effectsDefault(), label = "toggleFg",
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = bg,
        contentColor = fg,
        modifier = Modifier.size(40.dp).interactiveScale(hoveredScale = 1.06f),
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(22.dp))
        }
    }
}
