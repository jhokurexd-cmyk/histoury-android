package com.histoury.app.ui.components

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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.histoury.app.theme.RatingStar
import com.histoury.app.theme.SurfaceSoft
import com.histoury.app.theme.TextSecondary

/**
 * Two-column grid card for the Explore page. Works for both historical
 * sites and places — pass [rating] as null for places, which don't have
 * one. Only the category badge, title, and a "view details" arrow show
 * here; location and the save/heart action are left for the place info
 * screen once the person taps in, so the card stays uncluttered.
 *
 * [subtitle] is accepted for call-site compatibility but intentionally
 * unused — it's still shown on the details screen.
 */
@Composable
fun ExploreGridCard(
    title: String,
    subtitle: String,
    badgeText: String,
    imageUrl: String,
    rating: Double?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // "1.2 km" from the visitor, or null when their location isn't known.
    distanceText: String? = null
) {

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .pressScale(interactionSource)
            .shadow(elevation = 5.dp, shape = RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(CardSurface)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {

        if (imageUrl.isNotBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SurfaceSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Gradient so the overlaid title stays readable against any photo,
        // without needing a separate white text block below.
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
                        startY = 60f
                    )
                )
        )

        // Same corner system as the Home cards, at the compact grid size:
        // badge top-left, title + arrow sharing the bottom row.
        if (rating != null && rating > 0.0) {
            RatingBadge(
                rating = rating,
                height = OverlayControls.CompactControlSize,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(OverlayControls.CompactInset)
            )
        } else if (rating == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(OverlayControls.CompactInset)
                    .height(OverlayControls.CompactControlSize)
                    .shadow(elevation = 3.dp, shape = RoundedCornerShape(50))
                    .clip(RoundedCornerShape(50))
                    .background(CardSurface.copy(alpha = 0.94f))
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeText,
                    color = Primary,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(
                    start = 12.dp,
                    end = OverlayControls.CompactInset,
                    bottom = OverlayControls.CompactInset
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (distanceText != null) {
                    CardDistanceLine(distanceText, compact = true)
                }
            }

            Spacer(Modifier.width(6.dp))

            CardArrowBadge(size = OverlayControls.CompactControlSize)
        }
    }
}
