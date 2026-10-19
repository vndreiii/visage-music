package com.music.vivi.compose.ui.tags

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.vivi.compose.ui.component.CoverImage
import com.music.vivi.compose.ui.component.smoothWheelScroll
import com.music.vivi.compose.ui.expressive.ExpressiveButton
import com.music.vivi.compose.ui.expressive.ExpressiveSize
import com.music.vivi.compose.ui.expressive.ExpressiveStyle
import com.music.vivi.compose.ui.motion.VisageMotion
import java.io.File

/** Side-by-side review of auto-tag proposals; nothing is written until Apply. */
@Composable
internal fun TagReviewScreen(state: TagManagerState) {
    val proposals = state.proposals.orEmpty()
    val listState = rememberLazyListState()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ExpressiveButton("Back", { state.closeReview() }, style = ExpressiveStyle.Tonal, size = ExpressiveSize.XSmall)
            Column(Modifier.weight(1f)) {
                Text("Review matches", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (state.autoRunning) "${proposals.size} found so far · ${state.autoDone}/${state.autoTotal} looked up"
                    else "${proposals.size} proposal(s) · ${state.proposalEditCount} with changes",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val high = proposals.count { it.best?.high == true }
            ExpressiveButton("Accept all high-confidence ($high)", { state.acceptAllHigh() }, style = ExpressiveStyle.Tonal, size = ExpressiveSize.XSmall, enabled = high > 0)
            ExpressiveButton("Apply ${state.proposalEditCount}", { state.applyProposals() }, size = ExpressiveSize.XSmall, enabled = state.proposalEditCount > 0 && !state.busy && !state.autoRunning)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (proposals.isEmpty() && !state.autoRunning) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.autoNote ?: "No matches found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else LazyColumn(
                state = listState, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize().smoothWheelScroll(listState),
            ) {
                items(proposals, key = { it.trackId }) { p ->
                    val track = state.byId[p.trackId]
                    ProposalCard(p, track, Modifier.animateItem()) { f -> state.updateProposal(p.trackId, f) }
                }
            }
        }
    }
}

@Composable
private fun ProposalCard(p: TrackProposal, track: TagTrack?, modifier: Modifier, update: ((TrackProposal) -> TrackProposal) -> Unit) {
    val dim by animateFloatAsState(if (p.skipped) 0.45f else 1f, VisageMotion.effectsDefault(), label = "proposalDim")
    val best = p.best
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth().alpha(dim)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(track?.let { it.tags.value(TagField.Title).ifBlank { it.stem } }.orEmpty(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track?.path.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                ExpressiveButton(if (p.skipped) "Skipped" else "Skip", { update { it.copy(skipped = !it.skipped) } }, size = ExpressiveSize.XSmall, style = if (p.skipped) ExpressiveStyle.Filled else ExpressiveStyle.Text, selected = if (p.skipped) true else null)
                ExpressiveButton("Accept all", { update { it.copy(skipped = false, overrides = it.changes.associate { c -> c.field to true }, coverChoice = it.best?.candidate?.coverUrl != null) } }, size = ExpressiveSize.XSmall, style = ExpressiveStyle.Tonal)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                p.options.forEachIndexed { i, o ->
                    ExpressiveButton(
                        "${sourceLabel(o.candidate.source)}  ${(o.score * 100).toInt()}%",
                        { update { it.copy(chosen = i, overrides = emptyMap(), coverChoice = null) } },
                        size = ExpressiveSize.XSmall, selected = i == p.chosen,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CoverCompare(track, best?.candidate?.coverUrl, p.acceptsCover(), update)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val changes = p.changes
                    if (changes.isEmpty()) Text("Nothing to change — tags already match.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    changes.forEach { c -> ChangeRow(c, p.accepted(c) && !p.skipped) { on -> update { it.copy(overrides = it.overrides + (c.field to on)) } } }
                }
            }
        }
    }
}

private fun sourceLabel(s: String) = when (s) {
    "musicbrainz" -> "MusicBrainz"; "deezer" -> "Deezer"; "itunes" -> "iTunes"; "theaudiodb" -> "AudioDB"
    "acoustid" -> "AcoustID"; "filename" -> "File name"; else -> s
}

@Composable
private fun ChangeRow(c: FieldChange, on: Boolean, onToggle: (Boolean) -> Unit) {
    val bg by animateColorAsState(if (on) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else androidx.compose.ui.graphics.Color.Transparent, VisageMotion.effectsDefault(), label = "changeBg")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bg).padding(end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(on, onToggle, Modifier.size(40.dp))
        Text(c.field.label, Modifier.width(92.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            c.old.ifBlank { "—" }, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textDecoration = if (c.old.isBlank()) null else TextDecoration.LineThrough,
        )
        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.padding(horizontal = 8.dp).size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(c.new, Modifier.weight(1.2f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun CoverCompare(track: TagTrack?, proposedUrl: String?, accepted: Boolean, update: ((TrackProposal) -> TrackProposal) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(14.dp))) {
                CoverImage(track?.coverPath?.takeIf { !it.startsWith("opus-lazy://") && it != "embedded" }?.let { File(it) }, seed = track?.id ?: "x", modifier = Modifier.fillMaxSize())
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                if (proposedUrl != null) AsyncImage(proposedUrl, null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(if (accepted) 1f else 0.4f))
                else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("no cover", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        AnimatedVisibility(proposedUrl != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(accepted, { on -> update { it.copy(coverChoice = on) } }, Modifier.size(32.dp))
                Text("Use cover", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
