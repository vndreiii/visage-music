package com.music.vivi.compose.ui.tags

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.music.vivi.compose.ui.expressive.ExpressiveCard
import com.music.vivi.compose.ui.expressive.ExpressiveButton
import com.music.vivi.compose.ui.expressive.ExpressiveSize
import com.music.vivi.compose.ui.expressive.ExpressiveStyle
import com.music.vivi.compose.ui.motion.VisageMotion
import com.music.vivi.database.Local_track

/** Number of local tracks whose title/artist/album/cover look missing (no file access). */
fun countTracksMissingTags(tracks: List<Local_track>): Int = tracks.count { TagHealth.of(it.toTagTrack(checkWritable = false)).any }

/** Subtle "N tracks are missing tags — Fix in Tag Manager" strip for the Library. */
@Composable
fun TagFixCta(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = count > 0,
        enter = fadeIn(VisageMotion.fadeInSpec()) + expandVertically(VisageMotion.spatialDefault()),
        exit = fadeOut(VisageMotion.fadeOutSpec()) + shrinkVertically(VisageMotion.spatialDefault()),
        modifier = modifier,
    ) {
        ExpressiveCard(
            onClick = onClick,
            restRadius = 18.dp,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Sell, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    "$count track${if (count == 1) " is" else "s are"} missing tags or cover art",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                ExpressiveButton("Fix in Tag Manager", onClick, size = ExpressiveSize.XSmall, style = ExpressiveStyle.Text)
            }
        }
    }
}
