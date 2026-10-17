package com.histoury.app.ui.components

import com.histoury.app.theme.overlayShadow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Primary
import com.histoury.app.theme.PrimarySoft
import com.histoury.app.theme.RatingStar
import com.histoury.app.theme.TextPrimary

/**
 * One set of measurements for every small control that floats over a photo
 * or sits beside a title: the Favorites heart, the rating badge, the coral
 * "view" arrow, and the dark glass buttons of the image viewer.
 *
 * Every card places its corner controls with these values, so a heart on a
 * Home card, a Recommended card and a Favorites card sits the same distance
 * from the same corner.
 */
object OverlayControls {

    /** Minimum touch target for anything tappable. */
    val TouchTarget: Dp = 44.dp

    /** Visible diameter of a heart / arrow disc, and height of a rating badge, on cards. */
    val CardControlSize: Dp = 34.dp

    /** Distance from a card's edge to the visible edge of a corner control. */
    val CardInset: Dp = 12.dp

    /**
     * The heart is drawn [CardControlSize] wide inside a [TouchTarget] tap
     * area. Pad it by this instead of [CardInset] so the VISIBLE disc lands
     * on the same inset line as the rating badge next to it.
     */
    val CardTouchSlack: Dp = (TouchTarget - CardControlSize) / 2

    /** Padding for a [FavoriteButton] in a card corner. */
    val CardFavoritePadding: Dp = CardInset - CardTouchSlack

    /** Visible size of corner controls on small two-column grid cards. */
    val CompactControlSize: Dp = 28.dp

    /** Corner inset on small two-column grid cards. */
    val CompactInset: Dp = 10.dp

    /** Gap between two controls placed side by side (heart + rating). */
    val ControlGap: Dp = 8.dp

    /** Size of the dark glass buttons in the full-screen image viewer. */
    val GlassButtonSize: Dp = 44.dp

    /** Icon size inside a [GlassIconButton] or [HeroIconButton]. */
    val GlassIconSize: Dp = 22.dp

    /** Background of the image viewer's glass controls. */
    val GlassColor: Color = Color.Black.copy(alpha = 0.45f)
}

enum class RatingBadgeStyle {
    /** Near-white pill with a soft shadow, for sitting on a photograph. */
    OVERLAY,
    /** Soft coral pill with no shadow, for sitting on a card or sheet. */
    TONAL
}

/**
 * The star-and-number rating pill. Same height as a [FavoriteButton]'s
 * visible disc by default, so the two line up wherever they share a row
 * or a card's top edge.
 */
@Composable
fun RatingBadge(
    rating: Double,
    modifier: Modifier = Modifier,
    style: RatingBadgeStyle = RatingBadgeStyle.OVERLAY,
    height: Dp = OverlayControls.CardControlSize
) {

    val shape = RoundedCornerShape(50)
    val compact = height < OverlayControls.CardControlSize

    Row(
        modifier = modifier
            .height(height)
            .then(
                if (style == RatingBadgeStyle.OVERLAY) {
                    Modifier.overlayShadow(elevation = 3.dp, shape = shape)
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .background(
                when (style) {
                    RatingBadgeStyle.OVERLAY -> CardSurface.copy(alpha = 0.94f)
                    RatingBadgeStyle.TONAL -> PrimarySoft
                }
            )
            .padding(horizontal = if (compact) 9.dp else 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            tint = RatingStar,
            modifier = Modifier.size(if (compact) 12.dp else 14.dp)
        )
        Spacer(Modifier.width(if (compact) 3.dp else 4.dp))
        Text(
            text = "%.1f".format(rating),
            fontWeight = FontWeight.Bold,
            fontSize = if (compact) 11.sp else 12.5.sp,
            color = TextPrimary,
            maxLines = 1
        )
    }
}

/**
 * The coral arrow disc in the bottom corner of a site card. Decorative —
 * the whole card is the tap target — so it carries no click of its own and
 * no separate accessibility label.
 */
@Composable
fun CardArrowBadge(
    modifier: Modifier = Modifier,
    size: Dp = OverlayControls.CardControlSize
) {
    Box(
        modifier = modifier
            .size(size)
            .overlayShadow(elevation = 3.dp, shape = CircleShape)
            .clip(CircleShape)
            .background(Primary),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

/**
 * A round dark-glass button for controls over a full-screen photo (the
 * image viewer's close and previous/next). Same size and look for all of
 * them; dims when disabled and dips slightly while pressed.
 */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = OverlayControls.GlassButtonSize,
    iconSize: Dp = OverlayControls.GlassIconSize
) {

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .size(size)
            .scale(if (pressed) 0.92f else 1f)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(CircleShape)
            .background(OverlayControls.GlassColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(iconSize)
        )
    }
}

/** A dark-glass text pill (the image viewer's "4 / 5" counter). */
@Composable
fun GlassLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(50))
            .background(OverlayControls.GlassColor)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
