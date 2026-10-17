package com.histoury.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * "➤ 1.2 km away" under a card's title, on the photo's dark scrim — the
 * same small line on every site and place card.
 */
@Composable
fun CardDistanceLine(distanceText: String, compact: Boolean = false) {
    Row(
        modifier = Modifier.padding(top = if (compact) 2.dp else 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.NearMe,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(if (compact) 10.dp else 12.dp)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = if (distanceText == "Nearby") distanceText else "$distanceText away",
            color = Color.White.copy(alpha = 0.92f),
            fontSize = if (compact) 10.5.sp else 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}
