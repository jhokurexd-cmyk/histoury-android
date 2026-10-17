package com.histoury.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.histoury.app.theme.OnColor
import com.histoury.app.theme.Primary
import com.histoury.app.theme.SurfaceSoft
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary

/**
 * "Avoid expressways and tolls" for car (and motorcycle) routes. In Metro
 * Manila the expressways are the toll roads, so one switch covers both.
 * The whole row is the touch target, not just the switch.
 */
@Composable
fun AvoidExpresswaysToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceSoft)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Avoid expressways and tolls",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = note ?: "NLEX, SLEX, Skyway and other toll roads",
                color = TextSecondary,
                fontSize = 11.5.sp,
                lineHeight = 15.sp
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            // The row handles the tap, so the switch itself doesn't.
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = OnColor,
                checkedTrackColor = Primary
            )
        )
    }
}
