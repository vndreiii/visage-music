package com.music.vivi.compose.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.PrefKeys
import com.music.vivi.compose.prefs.SecretStore
import com.music.vivi.compose.prefs.Secrets
import com.music.vivi.compose.ui.expressive.VisageTextField
import com.music.vivi.compose.ui.player.lyrics.AiConfig
import com.music.vivi.compose.ui.player.lyrics.GOOGLE_FREE
import com.music.vivi.compose.ui.player.lyrics.LyricsAiProvider
import com.music.vivi.compose.ui.player.lyrics.LyricsAiProviders
import com.music.vivi.compose.ui.player.lyrics.LyricsLanguages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Model lists fetched this session, per provider and endpoint, so reopening settings does not refetch. */
private val fetchedModels = ConcurrentHashMap<String, List<String>>()

/** Shown when the system has no credential store to save a key in. */
internal const val NO_KEYRING_NOTE = "No system keyring available — the key will not be saved"

/** The "Translate into" row: a searchable dropdown of every language Google Translate supports. */
@Composable
internal fun TranslateLanguageRow(prefs: DesktopPrefs) {
    var lang by remember { mutableStateOf(prefs.getString(PrefKeys.LyricsTranslateLang, "en")) }
    val options = remember {
        val commonCodes = LyricsLanguages.commonCodes.toSet()
        LyricsLanguages.common.map { DropdownOption(it.code, it.display, group = "Common", searchText = "${it.english} ${it.native} ${it.code}") } +
            LyricsLanguages.all.filter { it.code !in commonCodes }.sortedBy { it.english }
                .map { DropdownOption(it.code, it.display, group = "All languages", searchText = "${it.english} ${it.native} ${it.code}") }
    }
    SettingsDropdownRow(
        Icons.Outlined.Translate, "Translate into",
        options = options, selected = lang,
        searchable = true, searchPlaceholder = "Search languages",
    ) { lang = it; prefs.setString(PrefKeys.LyricsTranslateLang, it) }
}

/**
 * Provider picker (grouped dropdown) plus whatever that provider needs: API key, endpoint or command, and a model dropdown
 * filled from the provider's live model list (with a built-in list as fallback).
 */
@Composable
internal fun LyricsAiProviderSettings(prefs: DesktopPrefs) {
    val secrets = Secrets.store
    var providerId by remember {
        mutableStateOf(prefs.getString(PrefKeys.LyricsTranslateProvider, GOOGLE_FREE).lowercase().takeIf { it == GOOGLE_FREE || LyricsAiProviders.byId(it) != null } ?: GOOGLE_FREE)
    }
    val provider = LyricsAiProviders.byId(providerId)
    val options = remember {
        listOf(DropdownOption(GOOGLE_FREE, "Google Translate", "free, no key", LyricsAiProviders.Groups.FREE)) +
            LyricsAiProviders.Groups.order.flatMap { g ->
                LyricsAiProviders.all.filter { it.group == g }.map { DropdownOption(it.id, it.label, it.hint, g) }
            }
    }
    val (statusText, problem) = providerStatus(provider, secrets)
    SettingsDropdownRow(
        Icons.Outlined.AutoAwesome, "Provider",
        options = options, selected = providerId,
        status = statusText,
        statusColor = if (problem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    ) { providerId = it; prefs.setString(PrefKeys.LyricsTranslateProvider, it) }

    if (provider == null) return
    androidx.compose.runtime.key(provider.id) { ProviderFields(prefs, provider, secrets) }
}

/** The line under the provider selector, and whether it points at something the user must fix. */
internal fun providerStatus(provider: LyricsAiProvider?, secrets: SecretStore): Pair<String, Boolean> {
    if (provider == null) return "No key needed" to false
    val optionalKey = !provider.needsKey && provider.keyHint == "Optional"
    if (!provider.needsKey && !optionalKey) return "No key needed" to false
    val has = secrets.has(Secrets.lyricsAiKey(provider.id))
    return when {
        has && secrets.status == SecretStore.Status.Unavailable -> "Key kept for this session only (no system keyring)" to true
        has -> "Key saved in system keyring" to false
        optionalKey -> "No key set (optional)" to false
        else -> "Key missing" to true
    }
}

@Composable
private fun ProviderFields(prefs: DesktopPrefs, provider: LyricsAiProvider, secrets: SecretStore) {
    var model by remember { mutableStateOf(initialModel(prefs, provider)) }
    var url by remember { mutableStateOf(LyricsAiProviders.config(prefs, provider, secrets).baseUrl) }
    val keyName = Secrets.lyricsAiKey(provider.id)
    val storedKey = secrets.get(keyName)
    val cfg = AiConfig(storedKey.trim(), model, url.trim())
    val field = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)

    if (provider.needsKey || provider.keyHint == "Optional") {
        // What is typed is shown; until the first edit the stored value (which may still be loading) is.
        var edited by remember { mutableStateOf<String?>(null) }
        val unavailable = secrets.status == SecretStore.Status.Unavailable
        SettingsDivider()
        SettingsRowFrame(
            Icons.Outlined.Key, "API key", null, onClick = null,
            belowSubtitle = {
                VisageTextField(
                    value = edited ?: storedKey,
                    onValueChange = { edited = it; LyricsAiProviders.saveKey(prefs, provider, it, secrets) },
                    label = { Text("${provider.label} key") },
                    singleLine = true,
                    password = true,
                    supportingText = {
                        if (unavailable) Text(NO_KEYRING_NOTE, color = MaterialTheme.colorScheme.error)
                        else Text(listOf(provider.keyHint, "Saved in the system keyring").filter { it.isNotBlank() }.joinToString(". "))
                    },
                    modifier = field,
                )
            },
        )
    }
    provider.baseUrlLabel?.let { label ->
        SettingsDivider()
        val urlField: @Composable () -> Unit = {
            VisageTextField(
                value = url,
                onValueChange = { url = it; LyricsAiProviders.save(prefs, provider, "url", it) },
                label = { Text(label) },
                singleLine = true,
                supportingText = provider.baseUrlHelp?.let { help -> { Text(help) } },
                modifier = field,
            )
        }
        if (provider.id == "custom-cmd") {
            SettingsRowFrame(Icons.Outlined.Terminal, "Command", null, onClick = null, belowSubtitle = urlField)
        } else {
            SettingsRowFrame(Icons.Outlined.Link, "Endpoint", null, onClick = null, belowSubtitle = urlField)
        }
    }
    SettingsDivider()
    SettingsRowFrame(
        Icons.Outlined.Memory, "Model", null, onClick = null,
        belowSubtitle = {
            ModelDropdown(provider, cfg, field) { model = it; LyricsAiProviders.save(prefs, provider, "model", it) }
        },
    )
}

private fun initialModel(prefs: DesktopPrefs, p: LyricsAiProvider): String {
    var model = LyricsAiProviders.config(prefs, p).model
    // Carry over the model from the first, Gemini-only version of this setting.
    if (p.id == "gemini") {
        val old = prefs.getString(PrefKeys.LyricsGeminiModel, "").trim()
        if (old.isNotBlank() && prefs.getString("lyricsAi.gemini.model", "").isBlank()) model = old
    }
    return model
}

@Composable
private fun ModelDropdown(provider: LyricsAiProvider, cfg: AiConfig, modifier: Modifier, onPick: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val cacheKey = provider.id + "|" + cfg.baseUrl
    var models by remember(cacheKey) { mutableStateOf(fetchedModels[cacheKey] ?: provider.suggestedModels) }
    var loading by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf(false) }

    fun refresh() {
        loading = true
        note = null
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { provider.listModels(cfg) } }
            loading = false
            r.onSuccess { list -> if (list.isNotEmpty()) { models = list; fetchedModels[cacheKey] = list } }
                .onFailure { note = "Could not load the model list (${(it.message ?: it::class.simpleName)?.take(80)}); showing common models" }
        }
    }
    // Fetch the live list once a provider is usable (key present where needed).
    LaunchedEffect(cacheKey, cfg.key.isNotBlank()) {
        if (fetchedModels[cacheKey] == null && (!provider.needsKey || cfg.key.isNotBlank())) refresh()
    }

    Row(modifier, verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.weight(1f)) {
            VisageTextField(
                value = cfg.model, onValueChange = {}, readOnly = true, singleLine = true,
                label = { Text(if (cfg.model.isBlank()) "Choose a model" else "Selected model") },
                trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, null) },
                supportingText = {
                    Text(
                        when {
                            loading -> "Loading models…"
                            note != null -> note.orEmpty()
                            else -> "${models.size} available. Search, or type any model id"
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            // A read-only text field swallows clicks, so a transparent layer over it (not over the supporting text) opens the menu.
            Box(Modifier.matchParentSize().padding(bottom = 22.dp).clickable { open = true })
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.width(380.dp), shape = RoundedCornerShape(16.dp)) {
                val options = remember(models) { models.map { DropdownOption(it, it) } }
                OptionList(
                    options, cfg.model, searchable = true, searchPlaceholder = "Search, or type a model id",
                    onPick = { onPick(it); open = false },
                    extraTop = { q ->
                        val typed = q.trim()
                        if (typed.isNotEmpty() && typed !in models) {
                            DropdownMenuItem(text = { Text("Use “$typed”") }, onClick = { onPick(typed); open = false })
                        }
                    },
                )
            }
        }
        IconButton(onClick = { refresh() }, enabled = !loading, modifier = Modifier.padding(top = 4.dp)) { Icon(Icons.Outlined.Refresh, "Reload models") }
    }
}
