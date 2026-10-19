package com.music.vivi.compose.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.music.vivi.compose.keys.BindingCaps
import com.music.vivi.compose.keys.CapturePrompt
import com.music.vivi.compose.keys.ConflictPolicy
import com.music.vivi.compose.keys.KeyBinding
import com.music.vivi.compose.keys.KeyInput
import com.music.vivi.compose.keys.Keymap
import com.music.vivi.compose.keys.ShortcutAction
import com.music.vivi.compose.keys.ShortcutCapture
import com.music.vivi.compose.keys.ShortcutCategory
import com.music.vivi.compose.keys.keymap
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.ui.motion.VisageMotion
import org.koin.compose.koinInject
import java.awt.event.KeyEvent as Awt

/** Which binding slot is waiting for a key. `slot == null` appends a new binding. */
private data class Capturing(val action: ShortcutAction, val slot: Int?)

/** A captured binding that is already used elsewhere, awaiting the user's choice. */
private data class PendingConflict(
    val action: ShortcutAction,
    val slot: Int?,
    val binding: KeyBinding,
    val others: List<ShortcutAction>,
)

/** Settings → Keyboard shortcuts: searchable, grouped, rebindable. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeybindsSettingsScreen(navController: NavController) {
    val prefs = koinInject<DesktopPrefs>()
    @Suppress("UNUSED_VARIABLE")
    val observe = rememberPrefsVersion(prefs)
    val keymap = remember(prefs) { prefs.keymap() }
    var query by remember { mutableStateOf("") }
    var capturing by remember { mutableStateOf<Capturing?>(null) }
    var pending by remember { mutableStateOf<PendingConflict?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    // While a slot waits for a key, every key press is routed here instead of triggering shortcuts.
    DisposableEffect(capturing) {
        val target = capturing
        if (target != null) {
            ShortcutCapture.begin { input ->
                capturing = null
                handleCapture(keymap, target, input) { pending = it }
            }
        }
        onDispose { ShortcutCapture.end() }
    }

    SettingsPage(title = "Keyboard shortcuts", onBack = { navController.popBackStack() }) {
        Spacer(Modifier.height(12.dp))
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Keyboard, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Enable keyboard shortcuts", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Work from any page, except while typing in a text field.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = keymap.masterEnabled, onCheckedChange = { keymap.masterEnabled = it })
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search actions or keys") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f),
            )
            AnimatedContent(confirmReset, transitionSpec = { fadeIn(VisageMotion.fadeInSpec()) togetherWith fadeOut(VisageMotion.fadeOutSpec()) }, label = "resetAll") { confirming ->
                if (!confirming) {
                    OutlinedButton(onClick = { confirmReset = true }) {
                        Icon(Icons.Outlined.RestartAlt, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Reset all")
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { keymap.resetAll(); pending = null; confirmReset = false }) { Text("Reset everything") }
                        TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
                    }
                }
            }
        }

        val q = query.trim().lowercase()
        var any = false
        ShortcutCategory.entries.forEach { category ->
            val actions = ShortcutAction.entries.filter { a ->
                a.category == category && (q.isEmpty() || a.label.lowercase().contains(q) || category.label.lowercase().contains(q) ||
                    keymap.bindings(a).any { b -> b.display().lowercase().contains(q) })
            }
            if (actions.isEmpty()) return@forEach
            any = true
            SettingsSection(category.label) {
                actions.forEachIndexed { i, action ->
                    if (i > 0) SettingsDivider()
                    ShortcutRow(
                        action = action,
                        keymap = keymap,
                        capturing = capturing?.takeIf { it.action == action },
                        pending = pending?.takeIf { it.action == action },
                        onCapture = { slot -> pending = null; capturing = Capturing(action, slot) },
                        onCancelCapture = { capturing = null },
                        onResolve = { policy ->
                            pending?.let { p -> keymap.assign(p.action, p.slot, p.binding, policy) }
                            pending = null
                        },
                        onDismissPending = { pending = null },
                    )
                }
            }
        }
        AnimatedVisibility(!any, enter = fadeIn(), exit = fadeOut()) {
            Text(
                "No shortcuts match \"$query\".",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
        Text(
            "Esc cancels while rebinding, Backspace clears the binding you are editing. " +
                "Single-key shortcuts are paused while you type in a text field.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 20.dp),
        )
    }
}

private fun handleCapture(keymap: Keymap, target: Capturing, input: KeyInput, onConflict: (PendingConflict) -> Unit) {
    val plain = !input.ctrl && !input.alt && !input.shift && !input.meta
    when {
        input.keyCode == Awt.VK_ESCAPE && plain -> return
        (input.keyCode == Awt.VK_BACK_SPACE || input.keyCode == Awt.VK_DELETE) && plain -> {
            target.slot?.let { keymap.removeBinding(target.action, it) }
            return
        }
    }
    val binding = KeyBinding.fromInput(input) ?: return
    val others = keymap.conflicts(binding, target.action)
    if (others.isEmpty()) keymap.assign(target.action, target.slot, binding, ConflictPolicy.Reject)
    else onConflict(PendingConflict(target.action, target.slot, binding, others))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShortcutRow(
    action: ShortcutAction,
    keymap: Keymap,
    capturing: Capturing?,
    pending: PendingConflict?,
    onCapture: (slot: Int?) -> Unit,
    onCancelCapture: () -> Unit,
    onResolve: (ConflictPolicy) -> Unit,
    onDismissPending: () -> Unit,
) {
    val bindings = keymap.bindings(action)
    val enabled = keymap.isEnabled(action) && keymap.masterEnabled
    val active = capturing != null
    val bg by animateColorAsState(
        if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else androidx.compose.ui.graphics.Color.Transparent,
        VisageMotion.effectsDefault(), label = "rowBg",
    )
    Column(Modifier.fillMaxWidth().animateContentSize(VisageMotion.glide()).padding(horizontal = 4.dp, vertical = 2.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .androidxBackground(bg)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = androidx.compose.material3.ripple(),
                ) { if (active) onCancelCapture() else onCapture(if (bindings.isEmpty()) null else 0) }
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    action.label, style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
                action.hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1.4f),
            ) {
                bindings.forEachIndexed { index, binding ->
                    val capturingThis = capturing?.slot == index
                    AnimatedContent(
                        capturingThis,
                        transitionSpec = { (fadeIn(VisageMotion.fadeInSpec()) + scaleIn(VisageMotion.bounce(), initialScale = 0.8f)) togetherWith fadeOut(VisageMotion.fadeOutSpec()) },
                        label = "chip$index",
                    ) { c ->
                        Box(Modifier.clip(RoundedCornerShape(10.dp)).clickable { if (c) onCancelCapture() else onCapture(index) }.padding(2.dp)) {
                            if (c) CapturePrompt() else BindingCaps(binding, dimmed = !enabled)
                        }
                    }
                }
                if (bindings.isEmpty() && capturing == null) {
                    Text("Unbound", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AnimatedVisibility(capturing?.slot == null && capturing != null, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                    CapturePrompt()
                }
                IconButton(onClick = { onCapture(null) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Add, "Add another binding", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AnimatedVisibility(keymap.isCustomized(action), enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                    IconButton(onClick = { keymap.setBindings(action, action.defaults) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.RestartAlt, "Reset to default", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = keymap.isEnabled(action), onCheckedChange = { keymap.setEnabled(action, it) }, enabled = keymap.masterEnabled)
        }
        AnimatedVisibility(
            visible = pending != null,
            enter = fadeIn(VisageMotion.fadeInSpec()) + expandVertically(VisageMotion.glide()),
            exit = fadeOut(VisageMotion.fadeOutSpec()) + shrinkVertically(VisageMotion.glide()),
        ) {
            val p = pending
            if (p != null) ConflictPanel(p, keymap, onResolve, onDismissPending)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConflictPanel(p: PendingConflict, keymap: Keymap, onResolve: (ConflictPolicy) -> Unit, onDismiss: () -> Unit) {
    val canSwap = p.slot != null && keymap.bindings(p.action).getOrNull(p.slot) != null
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 10.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BindingCaps(p.binding)
                Text(
                    "is already used by " + p.others.joinToString(", ") { it.label } + ".",
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = { onResolve(ConflictPolicy.Replace) }) { Text("Replace") }
                if (canSwap) OutlinedButton(onClick = { onResolve(ConflictPolicy.Swap) }) { Text("Swap") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    }
}

private fun Modifier.androidxBackground(color: androidx.compose.ui.graphics.Color): Modifier =
    this.then(Modifier.background(color))
