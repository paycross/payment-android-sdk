package com.paycross.sdk.internal.ui.components

import android.annotation.SuppressLint
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.sp
import com.paycross.sdk.internal.ui.theme.LocalPayCrossAppearance

// The label room is a text unit rather than a dp, so it scales with the font scale.
private val LABEL_TOP_PADDING = 8.sp

// Locked paints the field as disabled but keeps the value at full contrast; readOnly is only input plumbing.
// valueIsPrompt keeps a select's prompt in the placeholder colour, so an unanswered select does not read as answered.
// The decoration box is driven directly because Material's OutlinedTextField takes no border thickness.
// default_error_message is borrowed because Compose ships it translated and the SDK ships no translations.
@SuppressLint("PrivateResource")
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
    locked: Boolean = false,
    valueIsPrompt: Boolean = false,
    singleLine: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    val appearance = LocalPayCrossAppearance.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    // Unspecified keeps Material's default for an unset role; the focused border stays the brand colour, the only focus cue once widths are equal.
    val container = appearance?.component ?: Color.Unspecified
    val placeholderColor = appearance?.placeholder ?: Color.Unspecified
    val colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = container,
        unfocusedContainerColor = container,
        errorContainerColor = container,
        focusedPlaceholderColor = placeholderColor,
        unfocusedPlaceholderColor = placeholderColor,
        errorPlaceholderColor = placeholderColor
    )
    val textStyle = LocalTextStyle.current
    val textColor = textStyle.color.takeOrElse {
        when {
            isError -> colors.errorTextColor
            // A prompt in the value slot takes the placeholder colour, so it does not read as an answer.
            valueIsPrompt -> placeholderColor.takeOrElse { colors.unfocusedPlaceholderColor }
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
            enabled = !locked,
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
                    enabled = !locked,
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
                            enabled = !locked,
                            isError = isError,
                            interactionSource = interactionSource,
                            colors = colors,
                            // No shape: PayCrossTheme already puts the merchant's
                            // corner radius on the extraSmall slot this reads.
                            focusedBorderThickness = appearance?.shapes?.borderWidth
                                ?: OutlinedTextFieldDefaults.FocusedBorderThickness,
                            unfocusedBorderThickness = appearance?.shapes?.borderWidth
                                ?: OutlinedTextFieldDefaults.UnfocusedBorderThickness
                        )
                    }
                )
            }
        )
    }
}
