package com.histoury.app.ui.screens.sitedetails

import com.histoury.app.data.offline.ArModelCache
import com.histoury.app.data.model.isArReady
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.histoury.app.ui.components.rememberVoiceoverController
import com.histoury.app.ui.components.SiteContentTabRow
import com.histoury.app.ui.components.SiteContentTabBody
import com.histoury.app.data.viewmodel.SiteDetailsViewModel
import com.histoury.app.navigation.Routes
import com.histoury.app.theme.OnAccentGold
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.AccentGold
import com.histoury.app.theme.Primary
import com.histoury.app.theme.PrimarySoft
import com.histoury.app.theme.RatingStar
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.graphics.vector.ImageVector
import com.histoury.app.theme.Outline
import com.histoury.app.theme.TextTertiary
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

@Composable
fun SiteDetailsScreen(
    siteId: String,
    navController: NavHostController,
    showArButton: Boolean = true,
    siteDetailsViewModel: SiteDetailsViewModel = viewModel()
) {

    val uiState by siteDetailsViewModel.uiState.collectAsState()

    val context = LocalContext.current

    
    // One controller for the whole screen. Sharing it is what makes the
    // sections mutually exclusive — starting a story stops the overview
    // instead of both reading at once.
    val voiceover = rememberVoiceoverController()

    LaunchedEffect(siteId) {
        siteDetailsViewModel.loadSite(siteId)
    }

    // Start downloading the AR model in the background as soon as this
    // site's details are showing, so it's usually already on the phone by
    // the time the visitor taps AR. Does nothing for sites without AR or
    // whose model is already downloaded.
    LaunchedEffect(uiState.site?.documentId) {
        ArModelCache.prefetch(context, uiState.site)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CardSurface)
    ) {

        when {

            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Primary)
                }
            }

            uiState.errorMessage != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.errorMessage ?: "",
                        color = TextSecondary
                    )
                }
            }

            uiState.site != null -> {

                val site = uiState.site!!

                val content = uiState.content

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {

                    // --- Hero image ---
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                    ) {

                        AsyncImage(
                            model = site.featuredImage,
                            contentDescription = site.siteName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.32f)),
                                        startY = 420f
                                    )
                                )
                        )

                        // Back button
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                // Order matters here, and getting it wrong is
                                // what stretched this button into a pill: an
                                // inset applied after background() pads the
                                // icon *inside* the circle instead of moving
                                // the circle down. Insets first, then shape,
                                // then the icon's own padding.
                                .statusBarsPadding()
                                .padding(16.dp)
                                .clip(CircleShape)
                                .background(CardSurface)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    navController.popBackStack()
                                }
                                .padding(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Primary
                            )
                        }

                        // Share and favourite, paired as in the design.
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {

                            HeroCircleButton(
                                icon = Icons.Default.Share,
                                description = "Share this site",
                                onClick = {
                                    val text = buildString {
                                        append(site.siteName)
                                        if (site.location.isNotBlank()) {
                                            append(" — ")
                                            append(site.location)
                                        }
                                        append("\n\nDiscover it on Histoury.")
                                    }
                                    // A plain text share rather than a deep
                                    // link: the app has no public web page to
                                    // point at, and a link that opens nothing
                                    // is worse than a name somebody can search.
                                    context.startActivity(
                                        Intent.createChooser(
                                            Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_SUBJECT, site.siteName)
                                                putExtra(Intent.EXTRA_TEXT, text)
                                            },
                                            "Share ${site.siteName}"
                                        )
                                    )
                                }
                            )

                            HeroCircleButton(
                                icon = if (uiState.isBookmarked) {
                                    Icons.Default.Favorite
                                } else {
                                    Icons.Default.FavoriteBorder
                                },
                                description = "Bookmark this site",
                                onClick = { siteDetailsViewModel.onBookmarkToggle() }
                            )
                        }

                        // Rating badge, floating over the bottom edge of the image
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 20.dp, bottom = 26.dp),
                            shape = RoundedCornerShape(50),
                            color = CardSurface
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = RatingStar,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "%.1f".format(site.averageRating),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = " (${site.reviewCount})",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                    }

                    // --- Rounded bottom sheet, overlapping the hero image ---
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = (-24).dp)
                            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                            .background(CardSurface)
                            .padding(20.dp)
                    ) {

                        // Grab handle. Purely a signal — the sheet does not
                        // drag — but it is the shape that tells a visitor the
                        // panel is a layer over the photograph rather than
                        // where the photograph happens to stop.
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .size(width = 38.dp, height = 4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Outline)
                        )

                        Spacer(Modifier.height(14.dp))

                        // The district, set small and wide above the name.
                        // Intramuros is the walled city every one of these
                        // sites sits inside, so it belongs as a label rather
                        // than repeated inside each title.
                        if (site.location.isNotBlank()) {
                            Text(
                                text = site.location.substringBefore(",").uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.4.sp,
                                color = TextTertiary,
                                modifier = Modifier.align(Alignment.End)
                            )

                            Spacer(Modifier.height(2.dp))
                        }

                        Text(
                            text = site.siteName,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        Spacer(Modifier.height(6.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {

                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(16.dp)
                            )

                            Spacer(Modifier.width(4.dp))

                            Text(
                                text = site.location,
                                color = TextSecondary,
                                fontSize = 13.sp
                            )

                            val distanceLabel = formatDistance(uiState.distanceMeters)

                            if (distanceLabel != null) {
                                Text(
                                    text = "  •  $distanceLabel",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        if (site.entranceFee.isNotBlank()) {

                            Spacer(Modifier.height(8.dp))

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = PrimarySoft
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ConfirmationNumber,
                                        contentDescription = null,
                                        tint = Primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Entrance Fee: ${site.entranceFee}",
                                        color = Primary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        if (uiState.tagNames.isNotEmpty()) {

                            Spacer(Modifier.height(12.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                uiState.tagNames.take(3).forEach { tag ->
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = PrimarySoft
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(
                                                horizontal = 10.dp,
                                                vertical = 6.dp
                                            ),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = iconForTag(tag),
                                                contentDescription = null,
                                                tint = Primary,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(Modifier.width(5.dp))
                                            Text(
                                                text = tag,
                                                color = Primary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        SiteContentTabRow(
                            selectedTab = uiState.selectedTab,
                            onTabSelected = { tab ->
                                siteDetailsViewModel.onTabSelected(tab)
                            }
                        )

                        Spacer(Modifier.height(20.dp))

                        SiteContentTabBody(
                            selectedTab = uiState.selectedTab,
                            content = content,
                            fallbackOverview = site.description,
                            voiceover = voiceover,
                            onSourceClick = { url ->
                                if (url.isNotBlank()) {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    )
                                }
                            }
                        )

                        Spacer(Modifier.height(28.dp))

                        // Two conditions gate the AR button, and they are
                        // deliberately separate. showArButton is the location
                        // gate — you have arrived. arEnabled is the content
                        // gate — this site actually has a model built AND at
                        // least one complete anchoring method (reference
                        // photos or a geospatial placement), by the same rule
                        // the admin panel uses for "Live in app".
                        val hasArContent = site.isArReady

                        if (showArButton && hasArContent) {

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(AccentGold)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        navController.navigate(
                                            Routes.ARExperience.createRoute(siteId)
                                        )
                                    }
                                    .padding(vertical = 16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {

                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    tint = OnAccentGold
                                )

                                Spacer(Modifier.width(8.dp))

                                Text(
                                    text = "Travel Back in Time (AR)",
                                    color = OnAccentGold,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                        } else {

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(PrimarySoft)
                                    .padding(vertical = 14.dp, horizontal = 16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {

                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )

                                Spacer(Modifier.width(8.dp))

                                Text(
                                    text = if (!hasArContent) {
                                        "AR experience coming soon for this site"
                                    } else {
                                        "Visit this site in person to unlock AR"
                                    },
                                    color = TextSecondary,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

/**
 * Turns a distance into a label, or nothing.
 *
 * Deliberately not the `formatDistance` in ExploreScreen: that one takes a
 * non-null Double and always returns a string, because the itinerary builder
 * only ever asks once it has a fix. Here the distance is Float? and is null
 * until location permission is granted and a reading arrives — so the null
 * has to survive the call, and the caller shows nothing rather than "0 m".
 *
 * Under fifty metres it reads as "nearby" rather than a figure. A phone GPS
 * is accurate to a handful of metres at best, so "3 m away" while standing
 * at the gate is a precision the reading does not have.
 */
private fun formatDistance(distanceMeters: Float?): String? {

    val metres = distanceMeters ?: return null

    return when {
        metres < 50f -> "nearby"
        metres < 1000f -> "${metres.roundToInt()} m away"
        else -> "%.1f km away".format(metres / 1000f)
    }
}

/**
 * A circular control floating over the hero photograph.
 *
 * Extracted because back, share and favourite are the same button with a
 * different glyph, and three copies of the same modifier chain is how one of
 * them ends up subtly the wrong size.
 */
@Composable
private fun HeroCircleButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit
) {

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(CardSurface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = Primary
        )
    }
}

/**
 * Picks a glyph for a category tag.
 *
 * Matched on keywords rather than an exact list, because tags are typed by
 * hand in the admin panel — "Photography", "photo spot" and "Great for
 * photos" should all get the camera. Anything unrecognised falls back to a
 * neutral marker instead of no icon, so the chips stay the same height.
 */
private fun iconForTag(tag: String): ImageVector {

    val name = tag.lowercase()

    return when {
        "photo" in name -> Icons.Default.PhotoCamera
        "museum" in name || "exhibit" in name || "art" in name -> Icons.Default.AccountBalance
        "cultur" in name || "heritage" in name -> Icons.Default.AccountBalance
        "sight" in name || "view" in name || "tour" in name -> Icons.Default.Visibility
        "food" in name || "dining" in name || "cafe" in name -> Icons.Default.Restaurant
        "learn" in name || "histor" in name -> Icons.Default.MenuBook
        else -> Icons.Default.LocalOffer
    }
}
