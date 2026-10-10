package com.histoury.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import com.histoury.app.theme.Outline
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Danger
import com.histoury.app.theme.Primary
import com.histoury.app.theme.SurfaceSoft
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextTertiary

/**
 * Standard rounded, coral-accented input for the auth screens (Login,
 * Register). A light filled background instead of Material's default
 * outline reads more like the rest of the app's cards/search bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
    required: Boolean = false,
    // Next moves to the following field; the last field of a form passes
    // Done plus [onImeAction] so the keyboard's action key submits it.
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
    // Live validation: the message to show under the field (null = none),
    // and a callback when the user leaves the field, so a form can start
    // validating it then. See util/FormValidation.kt.
    error: String? = null,
    onFocusLost: (() -> Unit)? = null,
    // Extra content under the field (e.g. the password checklist).
    supportingContent: (@Composable () -> Unit)? = null
) {

    var isPasswordVisible by remember { mutableStateOf(false) }
    var hadFocus by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            if (required) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label)
                    RequiredMarker()
                }
            } else {
                Text(label)
            }
        },
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = if (error != null) Danger else TextTertiary
            )
        },
        trailingIcon = if (isPassword) {
            {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            isPasswordVisible = !isPasswordVisible
                        }
                ) {
                    Icon(
                        imageVector = if (isPasswordVisible) {
                            Icons.Default.VisibilityOff
                        } else {
                            Icons.Default.Visibility
                        },
                        contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                        tint = TextTertiary
                    )
                }
            }
        } else {
            null
        },
        visualTransformation = if (isPassword && !isPasswordVisible) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
            imeAction = imeAction
        ),
        keyboardActions = if (onImeAction != null) {
            KeyboardActions(
                onDone = { onImeAction() },
                onGo = { onImeAction() },
                onSend = { onImeAction() }
            )
        } else {
            KeyboardActions.Default
        },
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            // These fields sit straight on the page, not on a card, so a
            // container the same colour as the page made them invisible.
            // A card-coloured fill plus a real unfocused border gives them
            // an edge in both themes.
            focusedContainerColor = CardSurface,
            unfocusedContainerColor = CardSurface,
            disabledContainerColor = CardSurface,
            focusedBorderColor = Primary,
            unfocusedBorderColor = Outline,
            disabledBorderColor = Outline,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedLabelColor = Primary,
            cursorColor = Primary,
            errorContainerColor = CardSurface,
            errorBorderColor = Danger,
            errorLabelColor = Danger,
            errorCursorColor = Danger,
            errorTextColor = TextPrimary,
            errorLeadingIconColor = Danger
        ),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { state ->
                if (hadFocus && !state.isFocused) onFocusLost?.invoke()
                hadFocus = state.isFocused
            }
            .semantics { if (error != null) error(error) }
    )

    FieldErrorText(error)

    supportingContent?.invoke()
    }
}
