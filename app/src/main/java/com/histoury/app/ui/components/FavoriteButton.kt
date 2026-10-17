package com.histoury.app.ui.components

import com.histoury.app.theme.overlayShadow
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.histoury.app.data.repository.FavoritesRepository
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Primary
import com.histoury.app.theme.PrimarySoft

enum class FavoriteButtonStyle {
    /** White disc, for sitting on top of a photograph. */
    OVERLAY,
    /** Soft coral disc, for sitting on a card or sheet. */
    TONAL
}

/**
 * The one heart used everywhere a historical site appears.
 *
 * Stateless: whether it's filled comes from the caller (normally
 * [FavoritesRepository.favoriteIds]), so two hearts for the same site on
 * different screens can't disagree.
 */
@Composable
fun FavoriteButton(
    isFavorite: Boolean,
    onToggle: () -> Unit,
    siteName: String,
    modifier: Modifier = Modifier,
    style: FavoriteButtonStyle = FavoriteButtonStyle.OVERLAY,
    visualSize: Dp = OverlayControls.CardControlSize
) {

    // A one-shot pop when the state changes, not on first appearance, so a
    // screen full of hearts doesn't bounce in unison when it opens.
    val scale = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(isFavorite) }

    LaunchedEffect(isFavorite) {
        if (previous != isFavorite) {
            previous = isFavorite
            scale.snapTo(0.8f)
            scale.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
            )
        }
    }

    // 44dp tap target around a smaller visible disc.
    Box(
        modifier = modifier
            .size(OverlayControls.TouchTarget)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onToggle
            )
            .semantics {
                contentDescription = if (isFavorite) {
                    "Remove $siteName from Favorites"
                } else {
                    "Add $siteName to Favorites"
                }
                stateDescription = if (isFavorite) "In Favorites" else "Not in Favorites"
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(visualSize)
                // Over a photo it gets the same soft shadow as the other hero
                // buttons, so a white disc stays visible on a bright image.
                .then(
                    if (style == FavoriteButtonStyle.OVERLAY) {
                        Modifier.overlayShadow(elevation = 3.dp, shape = CircleShape)
                    } else {
                        Modifier
                    }
                )
                .clip(CircleShape)
                .background(
                    when (style) {
                        FavoriteButtonStyle.OVERLAY -> CardSurface.copy(alpha = 0.94f)
                        FavoriteButtonStyle.TONAL -> PrimarySoft
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier
                    .size(visualSize * 0.5f)
                    .scale(scale.value)
            )
        }
    }
}

/**
 * [FavoriteButton] wired straight to [FavoritesRepository] for one site —
 * the usual case, so callers don't each repeat the collect-and-toggle code.
 */
@Composable
fun SiteFavoriteButton(
    siteDocumentId: String,
    siteName: String,
    modifier: Modifier = Modifier,
    style: FavoriteButtonStyle = FavoriteButtonStyle.OVERLAY,
    visualSize: Dp = OverlayControls.CardControlSize
) {
    if (siteDocumentId.isBlank()) return
    val favoriteIds by FavoritesRepository.favoriteIds.collectAsState()
    val context = LocalContext.current
    FavoriteButton(
        isFavorite = siteDocumentId in favoriteIds,
        onToggle = {
            FavoritesRepository.toggle(siteDocumentId) { message ->
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            }
        },
        siteName = siteName,
        modifier = modifier,
        style = style,
        visualSize = visualSize
    )
}
