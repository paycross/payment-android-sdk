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

// Material's own value for the room a floating label needs above the border. It
// is a text unit rather than a dp, so it scales with the font scale and has to
// be resolved against the current density.
private val LABEL_TOP_PADDING = 8.sp

/**
 * Material's outlined text field, reassembled from the pieces Material exposes.
 *
 * [locked] and [readOnly] are not the same thing and must not be collapsed into
 * one. [readOnly] is Compose's input plumbing and a select sets it always, since
 * a select is chosen from rather than typed into. [locked] says the shopper
 * cannot change this value at all, and is what paints the field as such: it
 * takes Material's disabled container, border and label, and with them the
 * caret, the click action and the focusability, so the field stops inviting the
 * keystrokes it was going to discard and announces itself as unwritable. The
 * value itself stays at full contrast, because it is the reason the field is on
 * the sheet — the inner text takes its colour from [textStyle] here rather than
 * from the decoration box's colour set, so muting the box does not mute it.
 *
 * [androidx.compose.material3.OutlinedTextField] takes a shape and a colour set
 * but no border thickness, and [OutlinedTextFieldDefaults.Container] is the only
 * seam that carries one. Reaching it means driving the decoration box directly,
 * so everything the wrapper composable does around it — the label's top padding,
 * the error semantics, the minimum size, the cursor brush, the selection colours
 * and the merged text style — is reproduced here rather than inherited.
 */
// PrivateResource: default_error_message is the string Material's own
// OutlinedTextField announces on an invalid field, and Compose ships it
// translated while this SDK ships no translations at all - so borrowing it is
// what keeps a non-English shopper hearing their own language. It is a
// compile-time reference, so a Compose release that drops it fails the build
// rather than degrading the sheet quietly.
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
    singleLine: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    val appearance = LocalPayCrossAppearance.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    // Unspecified is how Material's own colours() says "leave this one alone",
    // so an unset role keeps the default rather than painting over it. The
    // borders are not here: componentBorder reaches the resting border through
    // the outline slot, and the focused border stays the brand, which is the
    // only thing left indicating focus once a merchant equalizes the widths.
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
