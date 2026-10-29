package com.music.vivi.compose.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * One entry of a [SettingsDropdownRow]. [group] sets the heading it is listed under (entries keep their order; headings appear
 * in order of first use). [hint] is a one-line description shown under the label. [searchText] is matched by the menu's search box.
 */
data class DropdownOption<T>(
    val value: T,
    val label: String,
    val hint: String? = null,
    val group: String? = null,
    val searchText: String = "$label ${hint.orEmpty()}",
)

/**
 * A settings row whose value is picked from a long list: icon, title and the current value as a normal settings row,
 * which opens a menu on click. The menu can have a search box ([searchable]), group headings ([DropdownOption.group]),
 * a hint under each entry and a check on the selected one. Use it (never a chip cloud) when the options do not fit
 * on a couple of lines.
 *
 * [status] is an optional small line under the current value (for example whether a key is saved), tinted with [statusColor].
 */
@Composable
internal fun <T> SettingsDropdownRow(
    icon: ImageVector?,
    title: String,
    options: List<DropdownOption<T>>,
    selected: T,
    subtitle: String? = null,
    searchable: Boolean = false,
    searchPlaceholder: String = "Search",
    status: String? = null,
    statusColor: Color = Color.Unspecified,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.value == selected }
    val shown = subtitle ?: current?.let { c -> listOfNotNull(c.label, c.hint).joinToString(" · ") }
    Box(Modifier.fillMaxWidth()) {
        SettingsRowFrame(
            icon, title, shown, onClick = { open = true },
            belowSubtitle = {
                if (status != null) {
                    Text(
                        status, style = MaterialTheme.typography.bodySmall,
                        color = if (statusColor == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else statusColor,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            },
            extraText = options.joinToString(" ") { it.label },
            trailing = { Icon(Icons.Outlined.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        )
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.width(380.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            OptionList(options, selected, searchable, searchPlaceholder, onPick = { onSelect(it); open = false })
        }
    }
}

/** The content of a dropdown menu: optional search box, then the (filtered) options under their group headings. */
@Composable
internal fun <T> OptionList(
    options: List<DropdownOption<T>>,
    selected: T?,
    searchable: Boolean,
    searchPlaceholder: String,
    onPick: (T) -> Unit,
    extraTop: @Composable (query: String) -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    val scroll = rememberScrollState()
    if (searchable) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        MenuSearchField(query, { query = it }, searchPlaceholder, Modifier.padding(horizontal = 12.dp, vertical = 4.dp).focusRequester(focus))
    }
    val filtered = if (query.isBlank()) options else options.filter { it.searchText.contains(query.trim(), ignoreCase = true) }
    Column(Modifier.heightIn(max = 380.dp).verticalScroll(scroll)) {
        extraTop(query)
        var lastGroup: String? = null
        filtered.forEach { o ->
            if (o.group != null && o.group != lastGroup) {
                Text(
                    o.group, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = if (lastGroup == null) 6.dp else 14.dp, bottom = 4.dp),
                )
            }
            lastGroup = o.group
            val isSelected = o.value == selected
            DropdownMenuItem(
                text = {
                    Column {
                        Text(o.label, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 1)
                        if (o.hint != null) Text(o.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                },
                trailingIcon = if (isSelected) ({ Icon(Icons.Outlined.Check, "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }) else null,
                onClick = { onPick(o.value) },
            )
        }
        if (filtered.isEmpty()) {
            Text("Nothing matches “${query.trim()}”", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        }
    }
}

/** The small search pill used inside menus (same pill shape as the app's other search boxes, not a form field). */
@Composable
private fun MenuSearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(24.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BasicTextField(
                value, onValueChange, singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
