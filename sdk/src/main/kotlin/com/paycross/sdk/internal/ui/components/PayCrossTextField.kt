package com.paycross.sdk.internal.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.sp

// Material's own value for the room a floating label needs above the border. It
// is a text unit rather than a dp, so it scales with the font scale and has to
// be resolved against the current density.
private val LABEL_TOP_PADDING = 8.sp

/**
 * Material's outlined text field, reassembled from the pieces Material exposes.
 *
 * [androidx.compose.material3.OutlinedTextField] takes a shape and a colour set
 * but no border thickness, and [OutlinedTextFieldDefaults.Container] is the only
 * seam that carries one. Reaching it means driving the decoration box directly,
 * so everything the wrapper composable does around it — the label's top padding,
 * the error semantics, the minimum size, the cursor brush, the selection colours
 * and the merged text style — is reproduced here rather than inherited.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PayCrossOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    readOnly: Boolean = false,
    singleLine: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val colors = OutlinedTextFieldDefaults.colors()
    val textStyle = LocalTextStyle.current
    val textColor = textStyle.color.takeOrElse {
        when {
            isError -> colors.errorTextColor
            focused -> colors.focusedTextColor
            else -> colors.unfocusedTextColor
        }
    }
    val labelTopPadding = with(LocalDensity.current) { LABEL_TOP_PADDING.toDp() }
    val errorMessage = stringResource(androidx.compose.ui.R.string.default_error_message)

    CompositionLocalProvider(LocalTextSelectionColors provides colors.textSelectionColors) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier
                .then(
                    if (label != null) {
                        Modifier
                            .semantics(mergeDescendants = true) {}
                            .padding(top = labelTopPadding)
                    } else {
                        Modifier
                    }
                )
                .then(
                    if (isError) Modifier.semantics { error(errorMessage) } else Modifier
                )
                .defaultMinSize(
                    minWidth = OutlinedTextFieldDefaults.MinWidth,
                    minHeight = OutlinedTextFieldDefaults.MinHeight
                ),
            readOnly = readOnly,
            textStyle = textStyle.merge(TextStyle(color = textColor)),
            keyboardOptions = keyboardOptions,
            singleLine = singleLine,
            maxLines = if (singleLine) 1 else Int.MAX_VALUE,
            visualTransformation = visualTransformation,
            interactionSource = interactionSource,
            cursorBrush = SolidColor(if (isError) colors.errorCursorColor else colors.cursorColor),
            decorationBox = { innerTextField ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = value,
                    innerTextField = innerTextField,
                    enabled = true,
                    singleLine = singleLine,
                    visualTransformation = visualTransformation,
                    interactionSource = interactionSource,
                    isError = isError,
                    label = label,
                    placeholder = placeholder,
                    trailingIcon = trailingIcon,
                    supportingText = supportingText,
                    colors = colors,
                    container = {
                        OutlinedTextFieldDefaults.Container(
                            enabled = true,
                            isError = isError,
                            interactionSource = interactionSource,
                            colors = colors,
                            shape = OutlinedTextFieldDefaults.shape
                        )
                    }
                )
            }
        )
    }
}
