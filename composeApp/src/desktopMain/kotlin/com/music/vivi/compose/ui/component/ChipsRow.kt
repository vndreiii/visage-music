package com.music.vivi.compose.ui.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun <E> ChipsRow(
    chips: List<Pair<E, String>>,
    currentValue: E?,
    onValueUpdate: (E?) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    /** Leading/trailing gutter; matches the page's 20dp content inset. */
    gutter: androidx.compose.ui.unit.Dp = 20.dp,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(0.dp),
    ) {
        Spacer(Modifier.width(gutter))
        chips.forEach { (value, label) ->
            val selected = currentValue == value
            FilterChip(
                selected = selected,
                onClick = {
                    onValueUpdate(if (selected) null else value)
                },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = containerColor,
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        Spacer(Modifier.width(gutter - 8.dp))
    }
}
