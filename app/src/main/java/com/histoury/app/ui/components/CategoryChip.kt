package com.histoury.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.OnColor
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Primary

@Composable
fun CategoryChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {

    val backgroundColor by animateColorAsState(
        targetValue = if (selected) Primary else CardSurface,
        animationSpec = tween(durationMillis = 180),
        label = "categoryChipBackground"
    )

    val contentColor by animateColorAsState(
        targetValue = if (selected) OnColor else TextPrimary,
        animationSpec = tween(durationMillis = 180),
        label = "categoryChipContent"
    )

    Text(
        text = text,
        modifier = Modifier
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(24.dp)
            )
            .clickable { onClick() }
            .padding(
                horizontal = 18.dp,
                vertical = 10.dp
            ),
        color = contentColor,
        fontWeight = FontWeight.SemiBold
    )
}