package com.music.vivi.compose.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.PrefKeys
import com.music.vivi.compose.ui.player.lyrics.AiConfig
import com.music.vivi.compose.ui.player.lyrics.GOOGLE_FREE
import com.music.vivi.compose.ui.player.lyrics.LyricsAiProvider
import com.music.vivi.compose.ui.player.lyrics.LyricsAiProviders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Model lists fetched this session, per provider and endpoint, so reopening settings does not refetch. */
private val fetchedModels = ConcurrentHashMap<String, List<String>>()

/**
 * Provider picker plus whatever that provider needs: API key, endpoint or command, and a model dropdown
 * filled from the provider's live model list (with a built-in list as fallback).
 */
@Composable
internal fun LyricsAiProviderSettings(prefs: DesktopPrefs) {
    var providerId by remember {
        mutableStateOf(prefs.getString(PrefKeys.LyricsTranslateProvider, GOOGLE_FREE).lowercase().takeIf { it == GOOGLE_FREE || LyricsAiProviders.byId(it) != null } ?: GOOGLE_FREE)
    }
    ChipChoiceRow(
        Icons.Outlined.AutoAwesome, "Provider",
        listOf(GOOGLE_FREE to "Google (free, no key)") + LyricsAiProviders.all.map { it.id to it.label },
        providerId,
    ) { providerId = it; prefs.setString(PrefKeys.LyricsTranslateProvider, it) }

    val provider = LyricsAiProviders.byId(providerId) ?: return
    androidx.compose.runtime.key(provider.id) { ProviderFields(prefs, provider) }
}

@Composable
private fun ProviderFields(prefs: DesktopPrefs, provider: LyricsAiProvider) {
    var cfg by remember { mutableStateOf(initialConfig(prefs, provider)) }
    fun update(field: String, value: String, next: AiConfig) {
        LyricsAiProviders.save(prefs, provider, field, value)
        cfg = next
    }
    val field = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)

    if (provider.needsKey || provider.keyHint == "Optional") {
        var shown by remember { mutableStateOf(cfg.key) }
        OutlinedTextField(
            value = shown,
            onValueChange = { shown = it; update("key", it, cfg.copy(key = it.trim())) },
            label = { Text("${provider.label} API key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            supportingText = { Text(listOf(provider.keyHint, "Stored in plain text in the settings file").filter { it.isNotBlank() }.joinToString(". ")) },
            modifier = field,
        )
    }
    provider.baseUrlLabel?.let { label ->
        var shown by remember { mutableStateOf(cfg.baseUrl) }
        OutlinedTextField(
            value = shown,
            onValueChange = { shown = it; update("url", it, cfg.copy(baseUrl = it.trim())) },
            label = { Text(label) },
            singleLine = true,
            modifier = field,
        )
    }
    ModelDropdown(provider, cfg, field) { update("model", it, cfg.copy(model = it)) }
}

private fun initialConfig(prefs: DesktopPrefs, p: LyricsAiProvider): AiConfig {
    var c = LyricsAiProviders.config(prefs, p)
    // Carry over the key / model from the first, Gemini-only version of this setting.
    if (p.id == "gemini") {
        if (c.key.isBlank()) c = c.copy(key = prefs.getString(PrefKeys.LyricsGeminiKey, "").trim())
        val old = prefs.getString(PrefKeys.LyricsGeminiModel, "").trim()
        if (old.isNotBlank() && prefs.getString("lyricsAi.gemini.model", "").isBlank()) c = c.copy(model = old)
    }
    return c
}

@Composable
private fun ModelDropdown(provider: LyricsAiProvider, cfg: AiConfig, modifier: Modifier, onPick: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val cacheKey = provider.id + "|" + cfg.baseUrl
    var models by remember(cacheKey) { mutableStateOf(fetchedModels[cacheKey] ?: provider.suggestedModels) }
    var loading by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }

    fun refresh() {
        loading = true
        note = null
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { provider.listModels(cfg) } }
            loading = false
            r.onSuccess { list -> if (list.isNotEmpty()) { models = list; fetchedModels[cacheKey] = list } }
                .onFailure { note = "Could not load the model list (${it.message?.take(80)}); showing common models" }
        }
    }
    // Fetch the live list once a provider is usable (key present where needed).
    LaunchedEffect(cacheKey, cfg.key.isNotBlank()) {
        if (fetchedModels[cacheKey] == null && (!provider.needsKey || cfg.key.isNotBlank())) refresh()
    }

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Model", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { filter = ""; open = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(cfg.model.ifBlank { "Choose a model" }, Modifier.weight(1f), maxLines = 1)
                    Icon(Icons.Outlined.ArrowDropDown, null)
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    OutlinedTextField(
                        value = filter, onValueChange = { filter = it },
                        placeholder = { Text("Search, or type a model id") },
                        singleLine = true,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                    val shownModels = models.filter { it.contains(filter, ignoreCase = true) }
                    Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                        if (filter.isNotBlank() && filter !in models) {
                            DropdownMenuItem(text = { Text("Use “$filter”") }, onClick = { onPick(filter.trim()); open = false })
                        }
                        shownModels.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m, color = if (m == cfg.model) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                                onClick = { onPick(m); open = false },
                            )
                        }
                    }
                }
            }
            IconButton(onClick = { refresh() }, enabled = !loading) { Icon(Icons.Outlined.Refresh, "Reload models") }
        }
        val status = when {
            loading -> "Loading models…"
            note != null -> note
            else -> null
        }
        if (status != null) Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
    }
}
