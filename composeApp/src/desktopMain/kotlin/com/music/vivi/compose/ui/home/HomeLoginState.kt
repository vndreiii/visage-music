package com.music.vivi.compose.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.music.vivi.compose.ui.expressive.ExpressiveButton
import com.music.vivi.compose.ui.expressive.ExpressiveSize
import com.music.vivi.compose.ui.motion.VisageMotion

/** Home shown without a signed-in session: a friendly hero with one clear way in. */
@Composable
fun HomeLoginState(
    onLogin: () -> Unit,
    onContinueWithoutAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val alpha by animateFloatAsState(if (appeared) 1f else 0f, VisageMotion.fadeInSpec(), label = "loginFade")
    val scale by animateFloatAsState(if (appeared) 1f else 0.92f, VisageMotion.spatialDefault(), label = "loginScale")

    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(scheme.primaryContainer.copy(alpha = 0.6f), scheme.tertiaryContainer.copy(alpha = 0.25f), scheme.surface),
                ),
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .graphicsLayer { this.alpha = alpha; scaleX = scale; scaleY = scale },
            shape = RoundedCornerShape(36.dp),
            color = scheme.surfaceContainerHigh,
        ) {
            Column(
                Modifier.padding(horizontal = 36.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val logo = remember {
                    runCatching { useResource("visage_icon.png") { loadImageBitmap(it) } }.getOrNull()
                }
                if (logo != null) {
                    Image(
                        bitmap = logo,
                        contentDescription = "VIsage",
                        modifier = Modifier.size(88.dp).clip(RoundedCornerShape(22.dp)),
                    )
                    Spacer(Modifier.height(28.dp))
                }
                Text(
                    "Sign in to see your music",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Recommendations, playlists and your history come from your YouTube Music account.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(32.dp))
                ExpressiveButton(
                    onClick = onLogin,
                    size = ExpressiveSize.Medium,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Log in with YouTube Music") }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onContinueWithoutAccount) { Text("Continue without an account") }
            }
        }
    }
}

/** Home feed failed for another reason: a friendly message and Retry, never the raw error text. */
@Composable
fun HomeErrorState(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Home didn't load", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Check your connection and try again.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        ExpressiveButton(onClick = onRetry, size = ExpressiveSize.Small) { Text("Retry") }
    }
}
