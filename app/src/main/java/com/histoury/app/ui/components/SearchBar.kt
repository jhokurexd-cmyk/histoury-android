package com.histoury.app.ui.components

import com.histoury.app.theme.softShadow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Primary
import com.histoury.app.theme.PrimarySoft
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextTertiary
import com.histoury.app.theme.isNeumorphic
import com.histoury.app.theme.softInset

@Composable
fun SearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    onFilterClick: () -> Unit = {},
    hasActiveFilters: Boolean = false,
    modifier: Modifier = Modifier,
    placeholder: String = "Search historical sites, places...",
    // Off where there's no tag filter to open (e.g. the itinerary builder).
    showFilterButton: Boolean = true,
    // Shorter where screen space is tight (the itinerary builder, View all).
    height: androidx.compose.ui.unit.Dp = 52.dp
) {

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            // Default: a raised pill with a soft coral shadow (a glass pill
            // in Glassmorphism, via softShadow).
            // Neumorphism: pressed into the page instead (softInset below),
            // the usual soft-UI look for a text field.
            .then(
                if (isNeumorphic) Modifier else Modifier.softShadow(
                    elevation = 6.dp,
                    shape = RoundedCornerShape(height / 2),
                    ambientColor = Primary.copy(alpha = 0.15f),
                    spotColor = Primary.copy(alpha = 0.15f)
                )
            )
            .clip(RoundedCornerShape(height / 2))
            .background(CardSurface)
            .softInset(RoundedCornerShape(height / 2))
            // Less on the right: the trailing buttons carry their own 44dp
            // touch area, which supplies that side's breathing room.
            .padding(start = 16.dp, end = if (showFilterButton || value.isNotEmpty()) 4.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(19.dp)
        )

        Spacer(Modifier.width(10.dp))

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {

            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = TextTertiary,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 16.sp,
                    maxLines = 1
                )
            }

            val focusManager = LocalFocusManager.current
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                // Results filter as you type; the keyboard's Search key just
                // puts the keyboard away so they can be seen.
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = TextPrimary,
                    lineHeight = 16.sp
                ),
                cursorBrush = SolidColor(Primary),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (value.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onValueChange("") }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear search",
                    tint = TextTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // 44dp to tap, 34dp to see. It was a 32dp target, small enough to
        // miss and land in the text field instead.
        if (showFilterButton) Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onFilterClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (hasActiveFilters) PrimarySoft else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = if (hasActiveFilters) "Filter by tags (active)" else "Filter by tags",
                    tint = if (hasActiveFilters) Primary else TextTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
