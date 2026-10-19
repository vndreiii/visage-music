package com.music.vivi.compose.ui.tags

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.vivi.compose.ui.component.CoverImage
import com.music.vivi.compose.ui.component.smoothWheelScroll
import com.music.vivi.compose.ui.expressive.ConnectedButtonGroup
import com.music.vivi.compose.ui.expressive.ExpressiveButton
import com.music.vivi.compose.ui.expressive.ExpressiveSize
import com.music.vivi.compose.ui.expressive.ExpressiveStyle
import com.music.vivi.compose.ui.motion.VisageMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun SidePanel(state: TagManagerState) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                val n = state.selection.ids.size
                Column(Modifier.weight(1f)) {
                    Text(
                        if (n == 1) state.selectedTracks.firstOrNull()?.let { it.tags.value(TagField.Title).ifBlank { it.stem } }.orEmpty() else "$n tracks selected",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        when {
                            n == 1 -> state.reads.firstOrNull()?.let { r -> r.format + (if (r.readonly) " · read-only" else "") }.orEmpty().ifBlank { state.selectedTracks.firstOrNull()?.fileName.orEmpty() }
                            else -> "Fields with different values show “Mixed”; only fields you change are written."
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { state.select(state.selection.clear()) }) { Icon(Icons.Outlined.Close, "Close", Modifier.size(20.dp)) }
            }
            ConnectedButtonGroup(
                options = PanelMode.entries.map { it to it.label }, selected = state.panel, onSelect = { state.panel = it },
                size = ExpressiveSize.XSmall, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            )
            AnimatedContent(
                targetState = state.panel,
                transitionSpec = { fadeIn(VisageMotion.fadeInSpec()) togetherWith fadeOut(VisageMotion.fadeOutSpec()) },
                label = "panelMode", modifier = Modifier.weight(1f),
            ) { mode ->
                when (mode) {
                    PanelMode.Edit -> EditorPanel(state)
                    PanelMode.Tools -> ToolsPanel(state)
                }
            }
        }
    }
}

// ── editor ───────────────────────────────────────────────────────────────────────────────

@Composable
private fun EditorPanel(state: TagManagerState) {
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).smoothWheelScroll(scroll).verticalScroll(scroll).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AnimatedVisibility(state.editorLoading, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)))
            }
            val roCount = state.selectedTracks.count { it.readonly }
            AnimatedVisibility(roCount > 0, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Text("$roCount read-only file(s) can't be changed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            CoverEditor(state)
            Field(state, TagField.Title)
            Field(state, TagField.Artist)
            Field(state, TagField.Album)
            Field(state, TagField.AlbumArtist)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(state, TagField.Year, Modifier.weight(1f))
                Field(state, TagField.TrackNo, Modifier.weight(1f))
                Field(state, TagField.TrackTotal, Modifier.weight(1f))
                Field(state, TagField.DiscNo, Modifier.weight(1f))
            }
            Field(state, TagField.Genre)
            Field(state, TagField.Composer)
            Field(state, TagField.Comment)
            Field(state, TagField.Lyrics, minLines = 4)
            Spacer(Modifier.height(4.dp))
        }
        val dirty = state.editorDirty
        AnimatedVisibility(dirty, enter = fadeIn() + expandVertically(VisageMotion.spatialDefault()), exit = fadeOut() + shrinkVertically()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val changed = state.batch.edits.size + if (state.coverAction != CoverAction.Keep) 1 else 0
                ExpressiveButton(
                    "Apply to ${state.selection.ids.size}", { state.applyEditor() },
                    modifier = Modifier.weight(1f), enabled = !state.busy,
                )
                ExpressiveButton("Reset", { state.resetEditor() }, style = ExpressiveStyle.Tonal, enabled = !state.busy)
                Text("$changed", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Field(state: TagManagerState, f: TagField, modifier: Modifier = Modifier.fillMaxWidth(), minLines: Int = 1) {
    val edited = state.batch.isEdited(f)
    val mixed = state.batch.isMixed(f)
    val bg by animateColorAsState(
        if (edited) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else androidx.compose.ui.graphics.Color.Transparent,
        VisageMotion.effectsDefault(), label = "fieldBg",
    )
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(bg)) {
        OutlinedTextField(
            value = state.batch.displayValue(f),
            onValueChange = { state.setField(f, it) },
            label = { Text(f.label) },
            placeholder = if (mixed) ({ Text("Mixed — unchanged") }) else null,
            singleLine = !f.multiline,
            minLines = minLines,
            maxLines = if (f.multiline) 10 else 1,
            shape = RoundedCornerShape(14.dp),
            trailingIcon = if (edited) ({
                IconButton(onClick = { state.resetField(f) }, Modifier.size(32.dp)) { Icon(Icons.Outlined.RestartAlt, "Revert ${f.label}", Modifier.size(18.dp)) }
            }) else null,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ── cover ────────────────────────────────────────────────────────────────────────────────

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun CoverEditor(state: TagManagerState) {
    val scope = rememberCoroutineScope()
    var urlOpen by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var hover by remember { mutableStateOf(false) }
    val current = state.reads.firstOrNull { it.coverPath != null }?.coverPath
    val mixedCover = state.reads.map { it.coverPath }.distinct().size > 1
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            val borderColor by animateColorAsState(if (hover) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, VisageMotion.effectsFast(), label = "coverBorder")
            Box(
                Modifier.size(128.dp).clip(RoundedCornerShape(20.dp)).border(2.dp, borderColor, RoundedCornerShape(20.dp))
                    .coverDropTarget(onHover = { hover = it }, onFile = { state.chooseCoverFile(it) }),
            ) {
                Crossfade(state.coverAction, label = "coverPreview", animationSpec = VisageMotion.effectsDefault()) { action ->
                    when (action) {
                        is CoverAction.Replace -> CoverImage(action.file, seed = "new", modifier = Modifier.fillMaxSize())
                        CoverAction.Remove -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer), contentAlignment = Alignment.Center) {
                            Text("Will be\nremoved", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.labelLarge)
                        }
                        CoverAction.Keep -> CoverImage(current?.let { File(it) }, seed = state.selection.ids.firstOrNull() ?: "none", modifier = Modifier.fillMaxSize())
                    }
                }
                androidx.compose.animation.AnimatedVisibility(hover, enter = fadeIn(), exit = fadeOut()) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                        Text("Drop image", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    when {
                        state.coverAction is CoverAction.Replace -> "New cover selected"
                        state.coverAction == CoverAction.Remove -> "Cover will be removed"
                        mixedCover -> "Covers differ between tracks"
                        current != null -> "Embedded cover"
                        else -> "No embedded cover"
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
                ExpressiveButton("Choose…", {
                    scope.launch { pickImage()?.let(state::chooseCoverFile) }
                }, size = ExpressiveSize.XSmall, style = ExpressiveStyle.Tonal, leadingIcon = Icons.Outlined.AddPhotoAlternate)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExpressiveButton("URL", { urlOpen = !urlOpen }, size = ExpressiveSize.XSmall, style = ExpressiveStyle.Text, leadingIcon = Icons.Outlined.Link)
                    if (state.coverAction == CoverAction.Keep) ExpressiveButton("Remove", { state.removeCover() }, size = ExpressiveSize.XSmall, style = ExpressiveStyle.Text, leadingIcon = Icons.Outlined.DeleteOutline, enabled = current != null || mixedCover)
                    else ExpressiveButton("Undo", { state.keepCover() }, size = ExpressiveSize.XSmall, style = ExpressiveStyle.Text, leadingIcon = Icons.Outlined.RestartAlt)
                }
            }
        }
        AnimatedVisibility(urlOpen, enter = fadeIn() + expandVertically(VisageMotion.spatialDefault()), exit = fadeOut() + shrinkVertically()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(url, { url = it }, placeholder = { Text("https://… image link") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f))
                ExpressiveButton("Fetch", { state.chooseCoverUrl(url.trim()); urlOpen = false }, size = ExpressiveSize.XSmall, enabled = url.isNotBlank())
            }
        }
        AnimatedVisibility(state.coverMessage != null, enter = fadeIn(), exit = fadeOut()) {
            Text(state.coverMessage.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

private suspend fun pickImage(): File? = withContext(Dispatchers.IO) {
    runCatching {
        val dlg = java.awt.FileDialog(null as java.awt.Frame?, "Choose cover image", java.awt.FileDialog.LOAD)
        dlg.setFilenameFilter { _, name -> name.lowercase().let { it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") || it.endsWith(".gif") || it.endsWith(".bmp") } }
        dlg.isVisible = true
        val f = dlg.file
        val d = dlg.directory
        if (f != null && d != null) File(d, f) else null
    }.getOrNull()
}

// ── tools ────────────────────────────────────────────────────────────────────────────────

@Composable
private fun ToolsPanel(state: TagManagerState) {
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().smoothWheelScroll(scroll).verticalScroll(scroll).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ToolKind.entries.forEach { k ->
                ExpressiveButton(k.label, { state.tool = k }, size = ExpressiveSize.XSmall, style = ExpressiveStyle.Filled, selected = state.tool == k, modifier = Modifier.fillMaxWidth())
            }
        }
        val scope = if (state.selection.ids.isEmpty()) "all ${state.tracks.size} tracks" else "${state.selection.ids.size} selected"
        Text("Applies to $scope. Nothing is written until you press Apply.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AnimatedContent(state.tool, transitionSpec = { fadeIn(VisageMotion.fadeInSpec()) togetherWith fadeOut(VisageMotion.fadeOutSpec()) }, label = "tool") { tool ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (tool) {
                    ToolKind.FromFilename -> {
                        OutlinedTextField(
                            state.filenamePattern, { state.filenamePattern = it }, label = { Text("Pattern") },
                            supportingText = { Text("%artist% %title% %album% %albumartist% %year% %genre% %track% %disc% %composer%") },
                            singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
                        )
                        PreviewList(state.filenamePreview(), "No file names match this pattern.")
                        ExpressiveButton("Apply", { state.applyFilenameTags() }, enabled = !state.busy && state.filenamePreview().isNotEmpty())
                    }
                    ToolKind.Normalise -> {
                        Text("Turns ALL CAPS or all lower-case values into Title Case; mixed-case values are left alone.", style = MaterialTheme.typography.bodySmall)
                        PreviewList(state.normalisePreview(), "Nothing needs fixing.")
                        ExpressiveButton("Apply", { state.applyNormalise() }, enabled = !state.busy && state.normalisePreview().isNotEmpty())
                    }
                    ToolKind.Covers -> {
                        val missing = state.toolTargets().filter { TagHealth.of(it).missingCover }
                        Text("${missing.size} track(s) have no cover. Looks them up by artist and album and shows the matches for review.", style = MaterialTheme.typography.bodySmall)
                        ExpressiveButton("Find covers for ${missing.size}", { state.startAutoTag(missing, coversOnly = true) }, enabled = missing.isNotEmpty() && !state.autoRunning && !state.busy)
                    }
                    ToolKind.Duplicates -> {
                        ExpressiveButton("Scan library", { state.findDuplicates() }, style = ExpressiveStyle.Tonal)
                        if (state.duplicateGroups.isEmpty()) Text("No scan results yet (or no duplicates).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        state.duplicateGroups.take(20).forEach { g ->
                            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, onClick = { state.selectIds(g.map { it.id }) }, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("${g.first().tags.value(TagField.Artist)} — ${g.first().tags.value(TagField.Title)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    g.forEach { Text(it.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                }
                            }
                        }
                        if (state.duplicateGroups.size > 20) Text("…and ${state.duplicateGroups.size - 20} more groups", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewList(items: List<Pair<TagTrack, Tags>>, empty: String) {
    if (items.isEmpty()) {
        Text(empty, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Text("${items.size} track(s) would change", style = MaterialTheme.typography.labelLarge)
    items.take(6).forEach { (t, edits) ->
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp)) {
                Text(t.fileName, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                edits.forEach { (f, v) -> Text("${f.label}: ${t.tags.value(f).ifBlank { "—" }} → $v", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
    if (items.size > 6) Text("…and ${items.size - 6} more", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Accepts an image file dropped from the file manager. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun Modifier.coverDropTarget(onHover: (Boolean) -> Unit, onFile: (File) -> Unit): Modifier {
    val target = remember {
        object : androidx.compose.ui.draganddrop.DragAndDropTarget {
            override fun onEntered(event: androidx.compose.ui.draganddrop.DragAndDropEvent) = onHover(true)
            override fun onExited(event: androidx.compose.ui.draganddrop.DragAndDropEvent) = onHover(false)
            override fun onEnded(event: androidx.compose.ui.draganddrop.DragAndDropEvent) = onHover(false)
            override fun onDrop(event: androidx.compose.ui.draganddrop.DragAndDropEvent): Boolean {
                onHover(false)
                val data = event.dragData()
                if (data is androidx.compose.ui.draganddrop.DragData.FilesList) {
                    val first = data.readFiles().firstOrNull() ?: return false
                    runCatching { File(java.net.URI(first)) }.getOrNull()?.let(onFile)
                    return true
                }
                return false
            }
        }
    }
    return dragAndDropTarget(shouldStartDragAndDrop = { true }, target = target)
}
