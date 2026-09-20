package com.histoury.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.histoury.app.navigation.Routes
import com.histoury.app.theme.Background
import com.histoury.app.theme.GlassEdge
import com.histoury.app.theme.GlassBottom
import com.histoury.app.theme.GlassRim
import com.histoury.app.theme.GlassShadow
import com.histoury.app.theme.GlassSheen
import com.histoury.app.theme.GlassTop
import com.histoury.app.theme.OnColor
import com.histoury.app.theme.Primary
import com.histoury.app.theme.PrimarySoftStrong
import com.histoury.app.theme.TextSecondary

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

// Two flanking tabs each side of the raised centre action. Order is fixed
// here rather than at each call site so every screen shows the same footer.
private val LEADING_ITEMS = listOf(
    BottomNavItem(Routes.Home.route, "Home", Icons.Default.Home),
    BottomNavItem(Routes.MapOverview.route, "Map", Icons.Default.Map)
)

private val TRAILING_ITEMS = listOf(
    BottomNavItem(Routes.History.route, "Visit History", Icons.Default.History),
    BottomNavItem(Routes.Gallery.route, "Gallery", Icons.Default.PhotoLibrary)
)

/** One shape for the shadow, the clip, the fill and the rim — they have
 * to agree exactly or the rim detaches from the edge it's tracing. */
private val BAR_SHAPE = RoundedCornerShape(50)

private val BAR_HEIGHT = 62.dp
private val BAR_TOP_PADDING = 8.dp
private val BAR_BOTTOM_PADDING = 12.dp
private val CENTER_BUTTON_SIZE = 60.dp
private val TAB_WIDTH = 48.dp

/**
 * How much vertical room the floating bar occupies, gesture inset included.
 *
 * The bar deliberately does NOT reserve this space in the layout — content
 * runs underneath it, which is the whole point of floating it. Scrollable
 * content instead adds this to its bottom *contentPadding*, so the last
 * card can still be scrolled clear of the bar without a dead strip sitting
 * under it when the list is short.
 */
val BottomNavBarInset: Dp
    @Composable get() = BAR_HEIGHT + BAR_TOP_PADDING + BAR_BOTTOM_PADDING +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/**
 * Scaffold for the five top-level tabs.
 *
 * A floating bar in Scaffold's own `bottomBar` slot stops floating: the
 * slot reserves its height, so the screen's background is cut off in a flat
 * strip behind the pill and the content ends above it. Here the bar is
 * overlaid instead, and the inner Scaffold's insets are trimmed to the top
 * and sides so content genuinely runs to the bottom edge of the display.
 *
 * Screens pass [BottomNavBarInset] as extra bottom contentPadding on
 * whatever scrolls.
 */
@Composable
fun BottomNavScaffold(
    navController: NavHostController,
    containerColor: Color = Background,
    onCenterReselected: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {

    Box(modifier = Modifier.fillMaxSize()) {

        Scaffold(
            containerColor = containerColor,
            // The bottom inset belongs to the floating bar, which applies
            // it itself. Leaving it here too would pad the content away
            // from an edge it is supposed to reach.
            contentWindowInsets = WindowInsets.systemBars.only(
                WindowInsetsSides.Top + WindowInsetsSides.Horizontal
            )
        ) { padding ->
            content(padding)
        }

        BottomNavBar(
            navController = navController,
            onCenterReselected = onCenterReselected,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/**
 * Shared bottom navigation for the app's top-level screens - a floating
 * pill rather than an edge-to-edge Material bar, with Itinerary promoted to
 * a raised coral action in the middle.
 *
 * Planning a trip is the thing this app exists for, so it gets the one
 * control the thumb reaches without aiming. The four browsing destinations
 * sit either side of it as plain icons; the active one is marked with a
 * coral capsule.
 *
 * Selection is still derived from the nav back stack itself, so screens
 * just call BottomNavBar(navController) and can't get the highlight wrong.
 * Tab switches use the standard state-preserving pattern: back always
 * returns to Home, re-selecting a tab doesn't stack duplicates, and each
 * tab's scroll/UI state survives switching away and back.
 *
 * [onCenterReselected] lets the Itinerary screen reuse the centre button as
 * its "new itinerary" action once you're already there - a second floating
 * plus on that screen would sit directly above this one.
 */
@Composable
fun BottomNavBar(
    navController: NavHostController,
    onCenterReselected: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {

    val backStackEntry by navController.currentBackStackEntryAsState()

    val currentRoute = backStackEntry?.destination?.route

    fun switchTo(route: String) {
        if (currentRoute != route) {
            navController.navigate(route) {
                popUpTo(Routes.Home.route) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = BAR_TOP_PADDING,
                bottom = BAR_BOTTOM_PADDING
            ),
        contentAlignment = Alignment.Center
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAR_HEIGHT)
                // Light mode's only proof that the bar is floating: it and
                // the page beneath are both near-white, so without a cast
                // shadow the pill dissolves into the screen. GlassShadow is
                // transparent in dark mode, where being lighter than the
                // page already does that job.
                .shadow(
                    elevation = 22.dp,
                    shape = BAR_SHAPE,
                    ambientColor = GlassShadow,
                    spotColor = GlassShadow
                )
                .clip(BAR_SHAPE)
                // Layer 1 — the body. Denser at the top where glass catches
                // light, thinner at the bottom where content shows through.
                .background(
                    Brush.verticalGradient(
                        colors = listOf(GlassTop, GlassBottom)
                    )
                )
                // Layer 2 — the specular sweep. A diagonal highlight across
                // the upper-left, angled rather than vertical because a
                // straight top-down fade reads as a gradient and a diagonal
                // one reads as a reflection. This is the layer that sells
                // the panel as a physical surface.
                .background(
                    Brush.linearGradient(
                        colors = listOf(GlassSheen, Color.Transparent),
                        start = Offset.Zero,
                        end = Offset(x = 420f, y = 170f)
                    )
                )
                // Layer 3 — the rim, run as a gradient rather than a flat
                // stroke: bright white along the top edge where light hits,
                // fading to a cool refracted edge underneath. A uniform
                // border would look drawn on; this looks lit.
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(GlassRim, GlassEdge)
                    ),
                    shape = BAR_SHAPE
                )
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {

            LEADING_ITEMS.forEach { item ->
                NavIconTab(
                    item = item,
                    isSelected = currentRoute == item.route,
                    onClick = { switchTo(item.route) }
                )
            }

            // Reserves the slot the centre button floats over. The button
            // lives outside this Row so it can overhang the pill's edges
            // without being clipped by the rounded shape.
            Spacer(Modifier.width(CENTER_BUTTON_SIZE + 8.dp))

            TRAILING_ITEMS.forEach { item ->
                NavIconTab(
                    item = item,
                    isSelected = currentRoute == item.route,
                    onClick = { switchTo(item.route) }
                )
            }
        }

        CenterItineraryButton(
            isSelected = currentRoute == Routes.Itinerary.route,
            onClick = {
                if (currentRoute == Routes.Itinerary.route) {
                    // Already here - the plus means what it looks like it
                    // means, and starts a new trip.
                    onCenterReselected?.invoke()
                } else {
                    switchTo(Routes.Itinerary.route)
                }
            }
        )
    }
}

/**
 * One browsing destination. The capsule fades in behind the icon rather
 * than appearing instantly, which keeps the eye on the tab that just became
 * active instead of on the tab that stopped being active.
 *
 * Its footprint is deliberately a fixed size: the centre button is
 * positioned against the bar's midpoint, so a tab that grew when selected
 * would shift the four tabs asymmetrically and knock the plus off centre.
 * Selection is expressed in colour only.
 */
@Composable
private fun NavIconTab(
    item: BottomNavItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {

    // Solid rather than the theme's soft tint: the capsule sits on a
    // translucent bar, so a low-opacity fill would pick up whatever card is
    // passing underneath and lose its shape.
    val capsuleColor by animateColorAsState(
        targetValue = if (isSelected) PrimarySoftStrong else Color.Transparent,
        animationSpec = tween(durationMillis = 180),
        label = "navCapsuleColor"
    )

    val iconTint by animateColorAsState(
        // Inactive icons are a step darker than they were on the old opaque
        // bar. They now have to stay readable over photographs, not just
        // over white.
        targetValue = if (isSelected) Primary else TextSecondary,
        animationSpec = tween(durationMillis = 180),
        label = "navIconTint"
    )

    Box(
        modifier = Modifier
            .width(TAB_WIDTH)
            .height(44.dp)
            .clip(RoundedCornerShape(50))
            .background(capsuleColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {

        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = iconTint,
            modifier = Modifier
                .size(22.dp)
                .semantics { selected = isSelected }
        )
    }
}

/**
 * The raised centre action. Overhangs the pill by a couple of dp on each
 * side, the way the reference layout does, so it reads as sitting on top of
 * the bar rather than inside it.
 *
 * The icon describes the tap, not the tab. From anywhere else it shows the
 * route glyph, because tapping takes you to your itineraries. Once you are
 * already on that tab there is nowhere left to go, so it becomes a plus:
 * the button's job there is to start a new trip.
 */
@Composable
private fun CenterItineraryButton(
    isSelected: Boolean,
    onClick: () -> Unit
) {

    val haloScale by animateFloatAsState(
        targetValue = if (isSelected) 1.04f else 1f,
        animationSpec = tween(durationMillis = 180),
        label = "navCenterScale"
    )

    Box(contentAlignment = Alignment.Center) {

        // Soft coral halo, the centre button's equivalent of the capsule
        // the other tabs get.
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(CENTER_BUTTON_SIZE + 10.dp)
                    .clip(CircleShape)
                    .background(PrimarySoftStrong)
            )
        }

        Box(
            modifier = Modifier
                .size(CENTER_BUTTON_SIZE)
                .scale(haloScale)
                .shadow(elevation = 10.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(Primary)
                // A soft gloss over the top half, matching the sheen on
                // the panel behind it. Without this the coral circle reads
                // as flat plastic sitting on glass.
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.22f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = 110f
                    )
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {

            Icon(
                imageVector = if (isSelected) Icons.Default.Add else Icons.Default.AltRoute,
                contentDescription = if (isSelected) "New itinerary" else "Itinerary",
                tint = OnColor,
                modifier = Modifier
                    .size(26.dp)
                    .semantics { selected = isSelected }
            )
        }
    }
}
