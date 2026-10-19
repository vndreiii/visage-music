package com.music.vivi.compose.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay

@Composable
fun VisageToast(
    message: String?,
    onDismiss: () -> Unit,
    durationMs: Long = 2_200,
) {
    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        delay(durationMs)
        onDismiss()
    }
    if (message == null) return
    Popup(
        popupPositionProvider = rememberToastPositionProvider(bottomGapPx = with(LocalDensity.current) { 96.dp.roundToPx() }),
        properties = PopupProperties(focusable = false),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            shadowElevation = 6.dp,
        ) {
            Text(
                message,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}
