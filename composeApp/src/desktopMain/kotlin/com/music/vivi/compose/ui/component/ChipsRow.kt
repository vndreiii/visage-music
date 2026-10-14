package com.music.vivi.compose.ui.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.music.vivi.compose.ui.expressive.ExpressiveButton
import com.music.vivi.compose.ui.expressive.ExpressiveSize
import com.music.vivi.compose.ui.expressive.ExpressiveStyle

/**
 * Filter row as Expressive toggle buttons: unselected sit back as round pills, the selected one
 * springs into a squarer, filled shape. Tapping the selected chip clears the selection.
 */
@Composable
fun <E> ChipsRow(
    chips: List<Pair<E, String>>,
    currentValue: E?,
    onValueUpdate: (E?) -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    /** Leading/trailing gutter; matches the page's 20dp content inset. */
    gutter: androidx.compose.ui.unit.Dp = 20.dp,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 2.dp),
    ) {
        Spacer(Modifier.width(gutter))
        chips.forEach { (value, label) ->
            val selected = currentValue == value
            ExpressiveButton(
                text = label,
                onClick = { onValueUpdate(if (selected) null else value) },
                size = ExpressiveSize.XSmall,
                style = ExpressiveStyle.Filled,
                selected = selected,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        Spacer(Modifier.width(gutter - 8.dp))
    }
}
