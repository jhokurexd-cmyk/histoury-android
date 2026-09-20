package com.histoury.app.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

/**
 * Red asterisk marking a required field. Wrapped in a tooltip that appears
 * on hover (mouse/pointer input) or long-press (touch), explaining why the
 * marker is there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequiredMarker() {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text("This field is required") } },
        state = rememberTooltipState()
    ) {
        Text(
            text = " *",
            color = Color.Red,
            fontWeight = FontWeight.Bold
        )
    }
}
