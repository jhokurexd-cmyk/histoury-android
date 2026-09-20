package com.histoury.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Primary
import com.histoury.app.theme.PrimarySoft
import com.histoury.app.theme.TextPrimary

/**
 * Onboarding's feature highlight card.
 *
 * [emoji] is the fallback for a card with no [icon]. It defaults to empty
 * rather than requiring a value: every call site was passing the same pin
 * character regardless of what the card described, so a missing icon would
 * have shown three identical pins under three different labels. An empty
 * badge is a clearer bug than a wrong one.
 */
@Composable
fun FeatureCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    emoji: String = ""
) {

    Card(
        modifier = modifier
            .size(
                width = 108.dp,
                height = 136.dp
            )
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(22.dp)
            ),

        shape = RoundedCornerShape(22.dp),

        colors = CardDefaults.cardColors(
            containerColor = CardSurface
        )

    ) {

        Column(

            modifier = Modifier
                .fillMaxSize(),

            verticalArrangement = Arrangement.Center,

            horizontalAlignment = Alignment.CenterHorizontally

        ) {

            if (icon != null) {

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(PrimarySoft, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(22.dp)
                    )
                }

            } else {

                Text(
                    text = emoji,
                    fontSize = 34.sp
                )
            }

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            Text(

                text = title,

                style = MaterialTheme.typography.bodyMedium,

                fontWeight = FontWeight.Bold,

                color = TextPrimary,

                textAlign = TextAlign.Center

            )

        }

    }

}
