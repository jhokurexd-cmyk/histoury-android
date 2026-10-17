package com.histoury.app.ui.components

import com.histoury.app.theme.softShadow
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Primary
import com.histoury.app.theme.RatingStar

/**
 * Compact horizontal-scroll card for Home's "Popular" row of top-rated
 * historical sites — full-bleed image with the title/location overlaid on
 * a gradient scrim, matching the reference design. The heart is the real
 * Favorites toggle ([SiteFavoriteButton]), shared with every other screen.
 */
@Composable
fun PopularSiteCard(
    title: String,
    location: String,
    rating: Double,
    imageUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    siteDocumentId: String = "",
    // "1.2 km" from the visitor, or null when their location isn't known.
    distanceText: String? = null
) {

    val cardInteractionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .width(230.dp)
            .height(210.dp)
            .pressScale(cardInteractionSource)
            .softShadow(elevation = 6.dp, shape = RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(CardSurface)
            .clickable(
                interactionSource = cardInteractionSource,
                indication = null,
                onClick = onClick
            )
    ) {

        AsyncImage(
            model = imageUrl,
            contentDescription = title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Gradient scrim so the overlaid title/location stay legible
        // regardless of the photo underneath.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.75f)
                        ),
                        startY = 40f
                    )
                )
        )

        // Same corner layout as the Recommended card: rating top-left,
        // heart top-right, name + arrow on one bottom row.
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
                    start = 14.dp,
                    end = OverlayControls.CardInset,
                    bottom = OverlayControls.CardInset
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column(modifier = Modifier.weight(1f)) {

                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(3.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = listOfNotNull(
                            distanceText?.let { if (it == "Nearby") it else "$it away" },
                            location.takeIf { it.isNotBlank() }
                        ).joinToString(" · "),
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(OverlayControls.ControlGap))

            CardArrowBadge()
        }
    }
}
