package com.histoury.app.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.histoury.app.theme.OnColor
import com.histoury.app.theme.Primary
import com.histoury.app.theme.PrimarySoftStrong

/**
 * The app's standard call-to-action button.
 *
 * [isLoading] shows a spinner INSIDE the button (keeping its 56dp height,
 * so the layout never jumps) and blocks clicks — which also acts as
 * double-tap protection for submit actions.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    PrimaryButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        isLoading = isLoading
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * [PrimaryButton] with custom content — for a label that animates (e.g.
 * onboarding's Next → Get Started) while keeping the exact same button.
 *
 * Every primary button dips to 97% while pressed (the same spring as the
 * app's cards, via [pressScale]), so a tap is felt even before the ripple
 * spreads. Spring, so a quick tap-and-release reverses smoothly.
 */
@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {

    val interactionSource = remember { MutableInteractionSource() }

    Button(
        onClick = {
            if (!isLoading) {
                onClick()
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .pressScale(interactionSource)
            .semantics {
                if (isLoading) stateDescription = "Loading"
                if (!enabled || isLoading) disabled()
            },
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Primary,
            contentColor = OnColor,
            disabledContainerColor = PrimarySoftStrong,
            disabledContentColor = OnColor
        ),
        interactionSource = interactionSource
    ) {

        if (isLoading) {
            CircularProgressIndicator(
                color = OnColor,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(22.dp)
            )
        } else {
            content()
        }
    }
}
