package com.histoury.app.ui.components

import com.histoury.app.theme.overlayShadow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Primary
import com.histoury.app.theme.TextPrimary

/**
 * The controls and labels that float over a full-bleed hero photo — on Site
 * Information and Place Details — so both screens use one definition
 * instead of hand-built copies that drift apart.
 */

/** Size of a hero button: the icon plus 10dp all round, i.e. a 44dp touch target. */
val HeroButtonSize = 44.dp

/** How far the details card below a hero photo slides up over it. */
val HeroCardOverlap = 24.dp

/** Inset of hero overlays from the photo's edges (and the status bar). */
val HeroOverlayInset = 16.dp

/**
 * A round, solid card-coloured button over a hero photo: Back, Share.
 *
 * Solid rather than translucent, with a soft shadow, so it stays legible
 * over a white wall or bright sky as well as a dark interior.
 *
 * The caller positions it (align, statusBarsPadding, padding) through
 * [modifier]; everything that makes it a circle is applied AFTER that here.
 * Putting an inset after clip()/background() is what used to stretch the
 * Place Details back button into a tall pill under the status bar with the
 * arrow pushed to its bottom.
 */
@Composable
fun HeroIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .size(HeroButtonSize)
            // Same pressed dip as the image viewer's glass buttons.
            .scale(if (pressed) 0.92f else 1f)
            .overlayShadow(elevation = 4.dp, shape = CircleShape)
            .clip(CircleShape)
            .background(CardSurface)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Primary,
            modifier = Modifier.size(OverlayControls.GlassIconSize)
        )
    }
}

/**
 * A compact informational pill over the bottom of a hero photo — a place's
 * category, a site's rating. Same card-coloured pill as the rating badge on
 * Site Information, so labels over photos look alike everywhere.
 */
@Composable
fun HeroInfoChip(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = Primary
) {
    Row(
        modifier = modifier
            .overlayShadow(elevation = 2.dp, shape = RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .background(CardSurface)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text = text,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
