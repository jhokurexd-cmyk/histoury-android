package com.histoury.app.ui.screens.favorites

import com.histoury.app.theme.softShadow
import com.histoury.app.navigation.safePopBackStack
import com.histoury.app.navigation.navigateSafely
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.histoury.app.data.viewmodel.FavoriteSiteEntry
import com.histoury.app.data.viewmodel.FavoritesViewModel
import com.histoury.app.navigation.Routes
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Primary
import com.histoury.app.theme.PrimarySoft
import com.histoury.app.theme.RatingStar
import com.histoury.app.theme.SurfaceSoft
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary
import com.histoury.app.theme.TextTertiary
import com.histoury.app.ui.components.FavoriteButtonStyle
import com.histoury.app.ui.components.SiteFavoriteButton

/**
 * Menu > Favorites: every historical site the visitor has hearted, newest
 * first. The hearts here are the same ones as everywhere else, so removing
 * one makes the card disappear at once and un-hearts the site on Home and
 * Site Information too.
 */
@Composable
fun FavoritesScreen(
    navController: NavHostController,
    favoritesViewModel: FavoritesViewModel = viewModel()
) {

    val uiState by favoritesViewModel.uiState.collectAsState()

    // For the "1.2 km away" on each card.
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.histoury.app.util.UserDistance.refresh(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceSoft)
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .softShadow(elevation = 2.dp, shape = CircleShape)
                    .clip(CircleShape)
                    .background(CardSurface)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { navController.safePopBackStack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column {
                Text(
                    text = "Favorites",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Historical sites you've saved",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }

        when {

            uiState.isLoading && uiState.entries.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            }

            uiState.entries.isEmpty() && uiState.errorMessage != null -> {
                CenteredMessage(
                    title = "Couldn't load Favorites",
                    body = uiState.errorMessage ?: "",
                    actionLabel = "Try again",
                    onAction = { favoritesViewModel.load() }
                )
            }

            uiState.entries.isEmpty() -> {
                CenteredMessage(
                    title = "No favorites yet",
                    body = "Tap the heart on any historical site to save it here.",
                    actionLabel = "Explore sites",
                    onAction = { navController.popBackStack(Routes.Home.route, inclusive = false) }
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.entries, key = { it.site.documentId }) { entry ->
                        FavoriteSiteCard(
                            entry = entry,
                            onOpenInfo = {
                                navController.navigateSafely(
                                    Routes.SiteDetails.createRoute(entry.site.documentId, showAr = false)
                                )
                            },
                            onDirections = {
                                navController.navigateSafely(Routes.Map.createRoute(entry.site.documentId))
                            }
                        )
                    }
                    item { Spacer(Modifier.navigationBarsPadding()) }
                }
            }
        }
    }
}

@Composable
private fun FavoriteSiteCard(
    entry: FavoriteSiteEntry,
    onOpenInfo: () -> Unit,
    onDirections: () -> Unit
) {

    val site = entry.site

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .softShadow(elevation = 2.dp, shape = RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(CardSurface)
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                // The card itself opens directions for a locked site and the
                // full information for an unlocked one — the richest thing
                // the visitor is allowed to see.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = if (entry.isUnlocked) onOpenInfo else onDirections
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceSoft)
            ) {
                if (site.featuredImage.isNotBlank()) {
                    AsyncImage(
                        model = site.featuredImage,
                        contentDescription = site.siteName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {

                Text(
                    text = site.siteName,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(3.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    val userPosition by com.histoury.app.util.UserDistance.position.collectAsState()
                    val distance = com.histoury.app.util.UserDistance
                        .labelFor(site.latitude, site.longitude, userPosition)
                    Text(
                        text = listOfNotNull(
                            distance?.let { if (it == "Nearby") it else "$it away" },
                            site.location.takeIf { it.isNotBlank() }
                        ).joinToString(" \u00b7 "),
                        fontSize = 12.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (site.reviewCount > 0) {
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = RatingStar,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = "%.1f (%d)".format(site.averageRating, site.reviewCount),
                            fontSize = 11.5.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            SiteFavoriteButton(
                siteDocumentId = site.documentId,
                siteName = site.siteName,
                style = FavoriteButtonStyle.TONAL
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            CardAction(
                icon = if (entry.isUnlocked) Icons.Default.MenuBook else Icons.Default.Lock,
                label = if (entry.isUnlocked) "Site info" else "Visit to unlock",
                enabled = entry.isUnlocked,
                onClick = onOpenInfo,
                modifier = Modifier.weight(1f)
            )

            CardAction(
                icon = Icons.Default.Directions,
                label = "Directions",
                enabled = true,
                onClick = onDirections,
                modifier = Modifier.weight(1f),
                filled = true
            )
        }
    }
}

@Composable
private fun CardAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false
) {
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (filled) Primary else PrimarySoft.copy(alpha = if (enabled) 1f else 0.5f))
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tint = when {
            filled -> com.histoury.app.theme.OnColor
            enabled -> Primary
            else -> TextTertiary
        }
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text = label, color = tint, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun CenteredMessage(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(PrimarySoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.FavoriteBorder,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(text = body, fontSize = 13.sp, color = TextSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onAction) {
            Text(actionLabel, color = Primary, fontWeight = FontWeight.Bold)
        }
    }
}
