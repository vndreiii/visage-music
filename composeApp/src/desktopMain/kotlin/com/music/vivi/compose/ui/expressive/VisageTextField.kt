package com.music.vivi.compose.ui.expressive

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/** The two Material 3 text field styles (https://m3.material.io/components/text-fields/overview). */
enum class TextFieldVariant { Filled, Outlined }

/**
 * The app's one text input, following the Material 3 text field spec: a [TextFieldVariant.Filled] field (tinted container,
 * label inside it, active-indicator line underneath) or an [TextFieldVariant.Outlined] one (label notched into the outline),
 * 56dp tall, with optional leading/trailing icons and supporting text below.
 *
 * Use this for every form input. Search boxes are the separate M3 "search bar" pattern and stay pill-shaped.
 *
 * [highlighted] tints the container to show a value that was changed (the Tag Manager marks edited fields this way).
 */
@Composable
fun VisageTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    variant: TextFieldVariant = TextFieldVariant.Filled,
    highlighted: Boolean = false,
) {
    when (variant) {
        TextFieldVariant.Filled -> {
            val container = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
            TextField(
                value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled, readOnly = readOnly,
                textStyle = textStyle, label = label, placeholder = placeholder, leadingIcon = leadingIcon, trailingIcon = trailingIcon,
                supportingText = supportingText, isError = isError, visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, singleLine = singleLine,
                maxLines = maxLines, minLines = minLines,
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = container,
                    unfocusedContainerColor = container,
                    errorContainerColor = container,
                ),
            )
        }
        TextFieldVariant.Outlined -> OutlinedTextField(
            value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled, readOnly = readOnly,
            textStyle = textStyle, label = label, placeholder = placeholder, leadingIcon = leadingIcon, trailingIcon = trailingIcon,
            supportingText = supportingText, isError = isError, visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, singleLine = singleLine,
            maxLines = maxLines, minLines = minLines,
            shape = RoundedCornerShape(4.dp),
        )
    }
}
