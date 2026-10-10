package com.histoury.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Primary

/**
 * Home's "Recommended For You" card — just the photo, the name, and a
 * rating badge in place of a heart. Tags, location, and description are
 * intentionally left off: they're already shown when the person taps
 * through, so repeating them here would just be noise.
 */
@Composable
fun FeaturedSiteCard(
    title: String,
    rating: Double,
    imageUrl: String,
    onViewClick: () -> Unit = {},
    siteDocumentId: String = "",
    // "1.2 km" from the visitor, or null when their location isn't known.
    distanceText: String? = null
) {

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .pressScale(interactionSource)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(28.dp),
                ambientColor = Primary.copy(alpha = 0.2f)
            )
            .clip(RoundedCornerShape(28.dp))
            .background(CardSurface)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onViewClick
            )
    ) {

        AsyncImage(
            model = imageUrl,
            contentDescription = title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.65f)
                        ),
                        startY = 80f
                    )
                )
        )

        // Rating top-left, heart top-right, both on the same inset line
        // and the same height; name and arrow share the bottom row.
        // No badge until a site has been rated — "0.0" reads as a bad score.
        if (rating > 0.0) {
            RatingBadge(
                rating = rating,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(OverlayControls.CardInset)
            )
        }

        SiteFavoriteButton(
            siteDocumentId = siteDocumentId,
            siteName = title,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(OverlayControls.CardFavoritePadding)
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    end = OverlayControls.CardInset,
                    bottom = OverlayControls.CardInset
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (distanceText != null) {
                    CardDistanceLine(distanceText)
                }
            }

            Spacer(Modifier.width(OverlayControls.ControlGap))

            CardArrowBadge()
        }
    }
}
