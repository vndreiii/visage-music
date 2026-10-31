package com.music.vivi.compose.ui.library.sidebar

import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.BoxWithConstraints
import com.music.vivi.compose.ui.expressive.VisageTextField
import com.music.vivi.compose.ui.component.visageMarquee
import com.music.vivi.compose.ui.component.PanelHeaderGap
import com.music.vivi.compose.ui.component.panelInset
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.Canvas
import com.music.vivi.compose.ui.component.CoverImage
import com.music.vivi.compose.ui.component.GeneratedCover
import com.music.vivi.compose.ui.expressive.ExpressiveButton
import com.music.vivi.compose.ui.expressive.ExpressiveSize
import com.music.vivi.compose.ui.expressive.ExpressiveStyle
import com.music.vivi.compose.ui.motion.VisageMotion
import com.music.vivi.database.DatabaseFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.music.vivi.compose.ui.dnd.dropTarget
import androidx.compose.ui.zIndex

// ── Palette / icons ────────────────────────────────────────────────────────────────────────────

internal val FolderAccents: List<Long> = listOf(
    0xFF6C4DFF, 0xFFE5484D, 0xFFF76B15, 0xFFF5B800, 0xFF30A46C, 0xFF12A594, 0xFF0091FF, 0xFFD6409F, 0xFF8E8E93,
)

internal val FolderIcons: Map<String, ImageVector> = linkedMapOf(
    "folder" to Icons.Outlined.Folder,
    "favorite" to Icons.Outlined.FavoriteBorder,
    "star" to Icons.Outlined.StarBorder,
    "music" to Icons.Outlined.MusicNote,
    "headphones" to Icons.Outlined.Headphones,
    "album" to Icons.Outlined.Album,
    "mic" to Icons.Outlined.Mic,
    "bolt" to Icons.Outlined.Bolt,
    "fire" to Icons.Outlined.LocalFireDepartment,
    "party" to Icons.Outlined.Celebration,
    "calm" to Icons.Outlined.SelfImprovement,
    "car" to Icons.Outlined.DirectionsCar,
    "gym" to Icons.Outlined.FitnessCenter,
    "moon" to Icons.Outlined.DarkMode,
    "sun" to Icons.Outlined.WbSunny,
    "cloud" to Icons.Outlined.Cloud,
)

internal fun folderIcon(name: String): ImageVector = FolderIcons[name] ?: Icons.Outlined.Folder

internal fun folderAccentColor(f: LibraryFolderModel, fallback: Color): Color = f.accent?.let { Color(it.toInt()) } ?: fallback

/** Copies picked / dropped / downloaded images next to the other app covers so they survive the original. */
internal object FolderCovers {
    private fun target(ext: String) = File(DatabaseFactory.coversDir(), "folder-${java.util.UUID.randomUUID()}.$ext")

    suspend fun importFile(src: File): String? = withContext(Dispatchers.IO) {
        runCatching {
            val ext = src.extension.lowercase().takeIf { it in setOf("jpg", "jpeg", "png", "gif", "bmp", "webp") } ?: return@runCatching null
            target(ext).also { src.copyTo(it, overwrite = true) }.absolutePath
        }.getOrNull()
    }

    suspend fun importUrl(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = java.net.URI(url).toURL().openConnection().apply { connectTimeout = 8000; readTimeout = 15000 }
            val ext = when {
                conn.contentType?.contains("png") == true -> "png"
                conn.contentType?.contains("gif") == true -> "gif"
                conn.contentType?.contains("webp") == true -> "webp"
                else -> "jpg"
            }
            val out = target(ext)
            conn.getInputStream().use { i -> out.outputStream().use { o -> i.copyTo(o) } }
            if (out.length() in 1..(12L shl 20)) out.absolutePath else { out.delete(); null }
        }.getOrNull()
    }

    suspend fun pick(): File? = com.music.vivi.compose.local.XdgPortalFolderPicker.pickImage("Choose folder cover")

    /** Deletes an app-owned copy that is no longer referenced. */
    fun discard(path: String?) {
        if (path == null) return
        runCatching { File(path).takeIf { it.parentFile == DatabaseFactory.coversDir() && it.name.startsWith("folder-") }?.delete() }
    }
}

/** (thumbnail url or null, seed) of one child; used for the auto collage. */
internal data class ChildThumb(val url: String?, val seed: String)

@Composable
internal fun FolderCover(
    f: LibraryFolderModel,
    thumbs: List<ChildThumb>,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
) {
    val accent = folderAccentColor(f, MaterialTheme.colorScheme.primary)
    Box(modifier.clip(shape)) {
        Crossfade(
            Triple(f.coverKind, f.coverRef, thumbs.take(4).map { it.url }),
            animationSpec = tween(260), label = "folderCover",
        ) { (kind, ref, _) ->
            when (kind) {
                CoverKind.IMAGE -> if (ref.isNullOrBlank()) GeneratedCover(f.id, Modifier.fillMaxSize(), accent = accent)
                else CoverImage(
                    if (ref.startsWith("http")) ref else File(ref), seed = f.id, modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                CoverKind.GENERATED -> GeneratedCover(ref?.takeIf { it.isNotBlank() } ?: f.id, Modifier.fillMaxSize(), accent = accent)
                CoverKind.ICON -> {
                    val c by animateColorAsState(accent, tween(260), label = "folderAccent")
                    Box(
                        Modifier.fillMaxSize().background(Brush.linearGradient(listOf(c, c.copy(alpha = 0.55f)))),
                        contentAlignment = Alignment.Center,
                    ) { Icon(folderIcon(f.icon), null, tint = Color.White, modifier = Modifier.fillMaxSize(0.52f)) }
                }
                CoverKind.AUTO -> {
                    val picks = thumbs.distinctBy { it.url ?: it.seed }.take(4)
                    when {
                        picks.isEmpty() -> GeneratedCover(f.id, Modifier.fillMaxSize(), accent = accent)
                        picks.size < 4 -> picks.first().let { CoverImage(it.url, it.seed, Modifier.fillMaxSize()) }
                        else -> Column(Modifier.fillMaxSize()) {
                            picks.chunked(2).forEach { pair ->
                                Row(Modifier.weight(1f).fillMaxWidth()) {
                                    pair.forEach { CoverImage(it.url, it.seed, Modifier.weight(1f).fillMaxSize()) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Folder row ─────────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun FolderRowView(
    row: SidebarRow.Folder,
    thumbs: List<ChildThumb>,
    f: Float,
    actions: FolderActions,
    modifier: Modifier = Modifier,
) {
    val folder = row.folder
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var menu by remember { mutableStateOf(false) }
    val accent = folderAccentColor(folder, MaterialTheme.colorScheme.primary)
    val bg by animateFloatAsState(if (hovered) 1f else if (row.open) 0.55f else 0f, VisageMotion.effectsDefault(), label = "folderBg")
    val rot by animateFloatAsState(if (row.open) 0f else -90f, VisageMotion.effectsDefault(), label = "folderChevron")
    // A library entry carried over this folder drops into it; the folder grows to say so.
    val dropMod = Modifier.dropTarget<LibraryEntry>("folder:${folder.id}") { e -> actions.drop(folder, e) }
    val dropHover by animateFloatAsState(
        if (com.music.vivi.compose.ui.dnd.TrackDrag.hoverKey == "folder:${folder.id}") 1f else 0f,
        androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 420f), label = "folderDrop",
    )
    Box(
        modifier.then(dropMod).padding(vertical = 1.dp)
            .zIndex(dropHover)
            .graphicsLayer { val sc = 1f + 0.05f * dropHover; scaleX = sc; scaleY = sc },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(accent.copy(alpha = 0.10f * bg).compositeOver(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = bg)))
                .background(accent.copy(alpha = 0.35f * dropHover.coerceIn(0f, 1f)))
                .hoverable(interaction)
                .onSecondary { menu = true }
                .clickable { actions.toggle(folder) }
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(RowCover)) {
                FolderCover(folder, thumbs, Modifier.fillMaxSize(), RoundedCornerShape(12.dp))
                // Item count shows on the tile in the slim rail, where there is no text.
                Surface(
                    shape = CircleShape, color = accent, contentColor = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).alpha(1f - f).padding(2.dp),
                ) {
                    Text("${row.count}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 5.dp))
                }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp).alpha(f)) {
                Text(
                    folder.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Clip,
                    modifier = Modifier.visageMarquee(),
                )
                Text(
                    "Folder · " + if (row.count == 1) "1 item" else "${row.count} items",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Clip,
                    modifier = Modifier.visageMarquee(),
                )
            }
            Icon(
                Icons.Filled.ExpandMore, if (row.open) "Collapse folder" else "Expand folder",
                Modifier.alpha(f).rotate(rot), tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FolderMenu(menu, { menu = false }, folder, row.open, actions)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.onSecondary(onClick: () -> Unit): Modifier =
    onPointerEvent(PointerEventType.Press) { if (it.buttons.isSecondaryPressed) onClick() }

internal class FolderActions(
    val toggle: (LibraryFolderModel) -> Unit,
    val edit: (LibraryFolderModel) -> Unit,
    val delete: (LibraryFolderModel) -> Unit,
    val setSort: (LibraryFolderModel, FolderSort) -> Unit,
    /** A library entry was dropped on the folder; returns the toast text. */
    val drop: suspend (LibraryFolderModel, LibraryEntry) -> String?,
)

@Composable
private fun FolderMenu(expanded: Boolean, onDismiss: () -> Unit, folder: LibraryFolderModel, open: Boolean, a: FolderActions) {
    var page by remember(expanded) { mutableStateOf(0) }
    androidx.compose.material3.DropdownMenu(
        expanded = expanded, onDismissRequest = onDismiss, shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        if (page == 0) {
            MenuItem(if (open) "Collapse" else "Expand", Icons.Outlined.UnfoldMore) { onDismiss(); a.toggle(folder) }
            MenuItem("Edit folder", Icons.Outlined.Edit) { onDismiss(); a.edit(folder) }
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("Sort inside · ${folder.sort.label}") },
                leadingIcon = { Icon(Icons.Outlined.SwapVert, null) },
                onClick = { page = 1 },
            )
            MenuItem("Delete folder", Icons.Outlined.Delete) { onDismiss(); a.delete(folder) }
        } else {
            MenuItem("Back", Icons.Outlined.ArrowBack) { page = 0 }
            FolderSort.entries.forEach { s ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(s.label, fontWeight = if (s == folder.sort) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { onDismiss(); a.setSort(folder, s) },
                )
            }
        }
    }
}

@Composable
internal fun MenuItem(label: String, icon: ImageVector, run: () -> Unit) =
    androidx.compose.material3.DropdownMenuItem(
        text = { Text(label) },
        // Every menu icon gets the same 20 dp glyph in the same 24 dp slot, so none looks bigger than its neighbours.
        leadingIcon = { Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(20.dp)) } },
        onClick = run,
    )

// ── Source icon (replaces the text pills) ─────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SourceIcon(e: LibraryEntry, modifier: Modifier = Modifier) {
    val local = e.kind == EntryKind.LOCAL_PLAYLIST || e.kind == EntryKind.DOWNLOADS
    val name = if (e.kind == EntryKind.DOWNLOADS) "Downloaded, available offline" else if (local) "Local playlist on this device" else "YouTube Music"
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    TooltipArea(
        tooltip = {
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.inverseSurface, tonalElevation = 4.dp) {
                Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        },
        modifier = modifier,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f), modifier = Modifier.size(24.dp)) {
            Box(contentAlignment = Alignment.Center) {
                if (local) Icon(Icons.Outlined.Computer, name, Modifier.size(14.dp), tint = tint)
                else YouTubeMusicGlyph(tint, Modifier.size(14.dp), name)
            }
        }
    }
}

/** Ring with a play triangle: the YouTube Music mark, drawn in one colour. */
@Composable
internal fun YouTubeMusicGlyph(color: Color, modifier: Modifier = Modifier, description: String = "YouTube Music") {
    Canvas(modifier) {
        val s = size.minDimension
        drawCircle(color, radius = s / 2f - s * 0.07f, style = Stroke(width = s * 0.14f))
        val p = Path().apply {
            moveTo(s * 0.42f, s * 0.32f); lineTo(s * 0.70f, s * 0.5f); lineTo(s * 0.42f, s * 0.68f); close()
        }
        drawPath(p, color)
    }
}

// ── Create / edit dialog ───────────────────────────────────────────────────────────────────────

internal data class FolderDraft(
    val title: String,
    val icon: String,
    val accent: Long?,
    val coverKind: CoverKind,
    val coverRef: String?,
)

private val GeneratedSeeds = listOf("aurora", "ember", "tide", "moss", "dusk", "coral", "frost", "sable")

@OptIn(ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun FolderDialog(
    initial: LibraryFolderModel?,
    thumbs: List<ChildThumb>,
    onDismiss: () -> Unit,
    onSave: (FolderDraft) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var icon by remember { mutableStateOf(initial?.icon ?: "folder") }
    var accent by remember { mutableStateOf(initial?.accent ?: FolderAccents.first()) }
    var kind by remember { mutableStateOf(initial?.coverKind ?: CoverKind.AUTO) }
    var ref by remember { mutableStateOf(initial?.coverRef) }
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var dropHover by remember { mutableStateOf(false) }
    val owned = remember { mutableListOf<String>() }

    fun useImage(path: String?) {
        busy = false
        if (path == null) { message = "Couldn't use that image"; return }
        message = null; owned += path; kind = CoverKind.IMAGE; ref = path
    }
    fun importFile(file: File) { busy = true; scope.launch { useImage(FolderCovers.importFile(file)) } }

    val draftFolder = LibraryFolderModel(
        id = initial?.id ?: "preview", title = title, icon = icon, accent = accent, coverKind = kind, coverRef = ref,
    )
    fun dismiss() { owned.forEach { if (it != initial?.coverRef) FolderCovers.discard(it) }; onDismiss() }

    LibraryDialogShell(
        title = if (initial == null) "New folder" else "Edit folder",
        subtitle = "Group playlists and albums together.",
        maxWidth = 760.dp,
        onDismiss = ::dismiss,
        actions = {
            ExpressiveButton("Cancel", ::dismiss, style = ExpressiveStyle.Text)
            ExpressiveButton(
                if (initial == null) "Create folder" else "Save",
                {
                    owned.filter { it != ref }.forEach { FolderCovers.discard(it) }
                    if (initial?.coverRef != null && initial.coverRef != ref) FolderCovers.discard(initial.coverRef)
                    onSave(FolderDraft(title.trim().ifBlank { "New folder" }, icon, accent, kind, ref))
                },
                enabled = !busy,
            )
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 620.dp
            @Composable
            fun Preview(tile: Dp) {
                Column(Modifier.width(tile), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Live preview; also a drop target for image files.
                    val tileSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    val tileHovered by tileSource.collectIsHoveredAsState()
                    Box(
                        Modifier.size(tile)
                            .clip(RoundedCornerShape(24.dp))
                            .then(if (dropHover) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp)) else Modifier)
                            .hoverable(tileSource)
                            .imageDropTarget({ dropHover = it }, ::importFile)
                            .clickable(interactionSource = tileSource, indication = null) {
                                if (!busy) { busy = true; scope.launch { val f = FolderCovers.pick(); if (f != null) importFile(f) else busy = false } }
                            },
                    ) {
                        FolderCover(draftFolder, thumbs, Modifier.fillMaxSize(), RoundedCornerShape(24.dp))
                        val show by animateFloatAsState(if (tileHovered || busy) 1f else 0f, VisageMotion.effectsDefault(), label = "folderCoverHover")
                        Box(
                            Modifier.fillMaxSize().graphicsLayer { alpha = show }.background(Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Outlined.Image, null, tint = Color.White, modifier = Modifier.size(36.dp))
                                Text(
                                    if (busy) "Working…" else "Choose photo",
                                    color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                    VisageTextField(
                        title, { title = it }, singleLine = true, label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Click the tile or drop an image on it to use it as the cover.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            @Composable
            fun Controls() {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    DialogSection("Cover") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CoverKind.entries.forEach { k ->
                                ExpressiveButton(
                                    when (k) { CoverKind.AUTO -> "Collage"; CoverKind.GENERATED -> "Generated"; CoverKind.ICON -> "Icon"; CoverKind.IMAGE -> "Image" },
                                    { kind = k; if (k != CoverKind.IMAGE && k != CoverKind.GENERATED) ref = null },
                                    modifier = Modifier.weight(1f),
                                    size = ExpressiveSize.XSmall, style = ExpressiveStyle.Tonal, selected = kind == k,
                                )
                            }
                        }
                        AnimatedVisibility(kind == CoverKind.GENERATED, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                GeneratedSeeds.forEach { seed ->
                                    val sel = ref == seed
                                    GeneratedCover(
                                        seed, Modifier.size(44.dp).clip(RoundedCornerShape(if (sel) 14.dp else 10.dp))
                                            .border(if (sel) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(if (sel) 14.dp else 10.dp))
                                            .clickable { ref = seed },
                                        accent = Color(accent.toInt()),
                                    )
                                }
                            }
                        }
                        AnimatedVisibility(kind == CoverKind.IMAGE, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                            Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    ExpressiveButton(
                                        "Choose file…",
                                        { scope.launch { FolderCovers.pick()?.let(::importFile) } },
                                        size = ExpressiveSize.XSmall, style = ExpressiveStyle.Tonal, leadingIcon = Icons.Outlined.Image,
                                    )
                                    Text(if (busy) "Working…" else "or paste a link", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    VisageTextField(
                                        url, { url = it }, placeholder = { Text("https://… image link") }, singleLine = true,
                                        modifier = Modifier.weight(1f),
                                    )
                                    ExpressiveButton(
                                        "Use", { busy = true; scope.launch { useImage(FolderCovers.importUrl(url.trim())) } },
                                        size = ExpressiveSize.XSmall, enabled = url.isNotBlank() && !busy,
                                    )
                                }
                                AnimatedVisibility(message != null) {
                                    Text(message.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    DialogSection("Colour") {
                        // One evenly spaced row, so no swatch ends up alone on a second line.
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            FolderAccents.forEach { c ->
                                val sel = accent == c
                                val grow by animateFloatAsState(if (sel) 1f else 0.84f, VisageMotion.press(), label = "swatch")
                                Box(
                                    Modifier.size(32.dp).clip(CircleShape).background(Color(c.toInt()))
                                        .border(if (sel) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        .clickable { accent = c }
                                        .graphicsLayer { scaleX = grow; scaleY = grow },
                                )
                            }
                        }
                    }

                    DialogSection("Icon") {
                        FlowRow(
                            Modifier.fillMaxWidth(),
                            maxItemsInEachRow = 8,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FolderIcons.forEach { (name, vec) ->
                                val sel = icon == name
                                Surface(
                                    onClick = { icon = name; if (kind == CoverKind.AUTO && thumbs.isEmpty()) kind = CoverKind.ICON },
                                    shape = RoundedCornerShape(if (sel) 16.dp else 12.dp),
                                    color = if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier.weight(1f).aspectRatio(1f),
                                ) { Box(contentAlignment = Alignment.Center) { Icon(vec, name, Modifier.size(22.dp)) } }
                            }
                        }
                    }
                }
            }

            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Preview(220.dp)
                    Box(Modifier.weight(1f)) { Controls() }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Preview(180.dp)
                    Controls()
                }
            }
        }
    }
}

/** A labelled group inside a library dialog. */
@Composable
internal fun DialogSection(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(label)
        content()
    }
}

/**
 * Shared card for the library's create/edit dialogs: title block on top, scrolling body, and a pinned action bar
 * under a hairline, so the buttons never scroll out of view and every dialog has the same shape.
 */
@Composable
internal fun LibraryDialogShell(
    title: String,
    subtitle: String?,
    maxWidth: Dp,
    onDismiss: () -> Unit,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.padding(24.dp).widthIn(max = maxWidth).heightIn(max = 760.dp).fillMaxWidth(),
        ) {
            // One PanelInset on every edge of the title block, body and action bar (see design-system.md section 6).
            val inset = panelInset()
            Column {
                Column(Modifier.padding(start = inset, end = inset, top = inset)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                        .padding(start = inset, end = inset, top = PanelHeaderGap, bottom = PanelHeaderGap),
                    content = content,
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)))
                Row(
                    Modifier.fillMaxWidth().padding(inset),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) =
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** Accepts an image file dropped from the file manager. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Modifier.imageDropTarget(onHover: (Boolean) -> Unit, onFile: (File) -> Unit): Modifier {
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


/**
 * New playlist: a large cover tile (hover it to choose a photo, or drop an image on it), the name, and an optional
 * description. Without a photo the cover is the generated one and later becomes the first songs' art.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun PlaylistDialog(
    initialName: String = "",
    initialDescription: String = "",
    initialCover: String? = null,
    title: String = "New playlist",
    confirm: String = "Create",
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String, coverPath: String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }
    var cover by remember { mutableStateOf(initialCover) }
    var busy by remember { mutableStateOf(false) }
    var dropHover by remember { mutableStateOf(false) }
    val owned = remember { mutableListOf<String>() }
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()

    fun use(path: String?) { busy = false; if (path != null) { owned += path; cover = path } }
    fun importFile(file: File) { busy = true; scope.launch { use(FolderCovers.importFile(file)) } }
    fun dismiss() { owned.forEach { if (it != initialCover) FolderCovers.discard(it) }; onDismiss() }

    LibraryDialogShell(
        title = title,
        subtitle = "Give it a name, a photo and a description. Everything is optional.",
        maxWidth = 680.dp,
        onDismiss = ::dismiss,
        actions = {
            ExpressiveButton("Cancel", ::dismiss, style = ExpressiveStyle.Text)
            ExpressiveButton(
                confirm,
                {
                    owned.filter { it != cover }.forEach { FolderCovers.discard(it) }
                    onConfirm(name.trim().ifBlank { "New playlist" }, description.trim(), cover)
                },
                enabled = !busy,
            )
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 520.dp
            val tile = if (wide) 210.dp else 170.dp

            @Composable
            fun CoverTile() {
                Box(
                    Modifier.size(tile)
                        .clip(RoundedCornerShape(24.dp))
                        .then(if (dropHover) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp)) else Modifier)
                        .hoverable(source)
                        .imageDropTarget({ dropHover = it }, ::importFile)
                        .clickable(interactionSource = source, indication = null) {
                            scope.launch { FolderCovers.pick()?.let(::importFile) }
                        },
                ) {
                    if (cover != null) {
                        coil3.compose.AsyncImage(
                            model = File(cover!!), contentDescription = "Playlist cover",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        com.music.vivi.compose.ui.component.GeneratedCover(name.ifBlank { "playlist" }, Modifier.fillMaxSize())
                    }
                    if (cover != null) {
                        Surface(
                            onClick = { cover = null },
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.6f),
                            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(30.dp).zIndex(1f),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(androidx.compose.material.icons.Icons.Outlined.Delete, "Remove photo", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    // Hover (or an empty cover) offers the photo picker.
                    val show by animateFloatAsState(if (hovered || busy) 1f else 0f, VisageMotion.effectsDefault(), label = "coverHover")
                    Box(
                        Modifier.fillMaxSize().graphicsLayer { alpha = show }.background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Outlined.Image, null, tint = Color.White, modifier = Modifier.size(36.dp))
                            Text(
                                if (busy) "Working…" else if (cover == null) "Choose photo" else "Change photo",
                                color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            @Composable
            fun Fields(modifier: Modifier) {
                // Exactly the tile's height, so the description's bottom edge is flush with the cover's bottom edge.
                Column(modifier.height(tile), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    VisageTextField(
                        value = name, onValueChange = { name = it }, singleLine = true,
                        label = { Text("Name") }, modifier = Modifier.fillMaxWidth(),
                    )
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        VisageTextField(
                            value = description, onValueChange = { if (it.length <= 300) description = it },
                            label = { Text("Description") }, placeholder = { Text("Add an optional description") },
                            modifier = Modifier.fillMaxSize(),
                        )
                        Text(
                            "${description.length}/300",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 8.dp),
                        )
                    }
                }
            }

            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    CoverTile()
                    Fields(Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CoverTile()
                    Fields(Modifier.fillMaxWidth())
                }
            }
        }
    }
}
