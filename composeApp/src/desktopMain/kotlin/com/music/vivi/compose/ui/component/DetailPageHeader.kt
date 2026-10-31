package com.music.vivi.compose.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Space under every page header (back arrow + title) before the list or grid starts; keep identical on all pages. */
val PageHeaderBottomPadding = 16.dp

/** Space above every page header (the top bar already floats over the page, so this stays small). */
val PageHeaderTopPadding = 8.dp

/** Left/right inset of page content; page headers (back button + title) start at the same edge. */
val PageInset = 20.dp

/** Back arrow + large title used at the top of pushed (non-tab) pages. */
@Composable
fun DetailPageHeader(
    title: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    /** Optional control on the header row's right edge (e.g. a page search field), vertically centred with the title. */
    trailing: (@Composable () -> Unit)? = null,
) {
    // Phones: the trailing control (search field ...) gets its own row under the title instead of crowding it out.
    val stackTrailing = trailing != null && com.music.vivi.compose.ui.responsive.windowLayout().isMobile
    androidx.compose.foundation.layout.Column(
        modifier.fillMaxWidth().padding(start = PageInset, end = PageInset, top = PageHeaderTopPadding, bottom = PageHeaderBottomPadding),
    ) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.music.vivi.compose.ui.component.VisageBackButton(onClick = onBack)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title.orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.visageMarquee(),
            )
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!stackTrailing) trailing?.let { Spacer(Modifier.width(16.dp)); it() }
    }
    if (stackTrailing) {
        Spacer(Modifier.height(8.dp))
        trailing?.invoke()
    }
    }
}

@Composable
fun PageErrorState(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Couldn't load this page", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = onRetry) { Text("Try again") }
    }
}
