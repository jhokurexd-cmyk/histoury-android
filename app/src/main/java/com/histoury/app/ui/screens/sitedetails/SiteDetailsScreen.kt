package com.histoury.app.ui.screens.sitedetails

import com.histoury.app.ui.tutorial.TutorialStep
import com.histoury.app.ui.tutorial.TutorialTour
import com.histoury.app.ui.tutorial.tutorialTarget
import com.histoury.app.navigation.safePopBackStack
import com.histoury.app.navigation.navigateSafely
import com.histoury.app.data.offline.ArModelCache
import com.histoury.app.data.model.isArReady
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
import androidx.compose.material3.TextButton
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
import com.histoury.app.ui.components.WrapRow
import com.histoury.app.ui.components.rememberVoiceoverController
import com.histoury.app.ui.components.rememberIsAtSite
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.graphics.vector.ImageVector
import com.histoury.app.theme.Outline
import com.histoury.app.theme.TextTertiary
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextOverflow
import com.histoury.app.theme.SurfaceSoft
import com.histoury.app.ui.components.ImageLightbox
import com.histoury.app.ui.components.SiteFavoriteButton
import com.histoury.app.ui.components.HeroIconButton
import com.histoury.app.ui.components.HeroOverlayInset
import com.histoury.app.ui.components.HeroCardOverlap
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
    val voiceover = rememberVoiceoverController(siteName = uiState.site?.siteName.orEmpty())

    LaunchedEffect(siteId) {
        siteDetailsViewModel.loadSite(siteId)
    }

    // AR is offered whenever the visitor is actually at the site, not only
    // when they came here from the Arrival screen. Opening the page from
    // Menu > Site Information (or Favorites, the Map, History) while
    // standing there used to hide AR, so they had to go round through the
    // Arrival screen again just to open the camera. Only watched when the
    // caller didn't already unlock it.
    val isAtSite by rememberIsAtSite(uiState.site, enabled = !showArButton)
    val arUnlocked = showArButton || isAtSite

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
                // The hero (and its back button) never renders on this path,
                // so the error state carries its own way out and a retry.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = uiState.errorMessage ?: "",
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { siteDetailsViewModel.loadSite(siteId) }) {
                        Text("Try again", color = Primary, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { navController.safePopBackStack() }) {
                        Text("Go back", color = TextSecondary)
                    }
                }
            }

            uiState.site != null -> {

                val site = uiState.site!!

                val content = uiState.content

                // The photo is cropped to fit the header, so a tap opens it
                // whole. Saveable so a rotation doesn't close it.
                var showHeroImage by rememberSaveable { mutableStateOf(false) }

                if (showHeroImage && site.featuredImage.isNotBlank()) {
                    ImageLightbox(
                        images = listOf(site.featuredImage),
                        startIndex = 0,
                        onDismiss = { showHeroImage = false }
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {

                    // --- Hero image ---
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            // Shown while the photo loads (or if it can't),
                            // instead of a blank white block.
                            .background(SurfaceSoft)
                    ) {

                        AsyncImage(
                            model = site.featuredImage,
                            contentDescription = site.siteName,
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    enabled = site.featuredImage.isNotBlank(),
                                    onClickLabel = "View photo",
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { showHeroImage = true },
                            contentScale = ContentScale.Crop
                        )

                        // Two scrims, sized to the header rather than set in
                        // raw pixels (the old 420px start sat below the
                        // image on dense screens, so there was no scrim at
                        // all): one behind the round buttons at the top,
                        // one behind the rating badge at the bottom, so both
                        // stay legible over a bright sky or a white wall.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        0f to Color.Black.copy(alpha = 0.28f),
                                        0.3f to Color.Transparent,
                                        0.65f to Color.Transparent,
                                        1f to Color.Black.copy(alpha = 0.38f)
                                    )
                                )
                        )

                        // Back button. Insets go on the modifier; the
                        // shared button draws its own circle after them.
                        HeroIconButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            onClick = { navController.safePopBackStack() },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .statusBarsPadding()
                                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                                .padding(HeroOverlayInset)
                        )

                        // Favourite (the Share button was removed).
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                                .padding(HeroOverlayInset),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {

                            // The real Favorites toggle, shared with Home,
                            // the map and Menu > Favorites.
                            Box(Modifier.tutorialTarget("site_favorite")) {
                                SiteFavoriteButton(
                                    siteDocumentId = site.documentId,
                                    siteName = site.siteName,
                                    visualSize = 44.dp
                                )
                            }
                        }

                        // Rating badge, floating over the bottom edge of the image
                        // (hidden until someone has rated it — "0.0 (0)" reads as a bad score)
                        if (site.reviewCount > 0) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    // Clear of the card's overlap, like Place Details' chip.
                                    .padding(start = 20.dp, bottom = HeroCardOverlap + 14.dp),
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
                            // Left-aligned with the name it labels. Pinned to
                            // the right edge it read as a stray caption rather
                            // than as the heading's eyebrow.
                            Text(
                                text = site.location.substringBefore(",").uppercase(),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.4.sp,
                                color = TextTertiary
                            )

                            Spacer(Modifier.height(4.dp))
                        }

                        Text(
                            text = site.siteName,
                            fontSize = 24.sp,
                            lineHeight = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        Spacer(Modifier.height(8.dp))

                        Row(verticalAlignment = Alignment.Top) {

                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier
                                    .padding(top = 1.dp)
                                    .size(16.dp)
                            )

                            Spacer(Modifier.width(4.dp))

                            // One text, so a long address wraps as a whole
                            // instead of squeezing the distance onto a
                            // column of its own at the right edge.
                            val distanceLabel = formatDistance(uiState.distanceMeters)

                            Text(
                                text = if (distanceLabel != null) {
                                    "${site.location}  \u2022  $distanceLabel"
                                } else {
                                    site.location
                                },
                                color = TextSecondary,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
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

                            // Wraps onto a second line on narrow phones or at
                            // large font sizes, where a fixed row clipped the
                            // third chip off the edge.
                            WrapRow(
                                horizontalSpacing = 6.dp,
                                verticalSpacing = 6.dp
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

                        Spacer(Modifier.height(24.dp))

                        TutorialTour(
                            id = "site_details",
                            steps = listOf(
                                TutorialStep(
                                    target = null,
                                    title = "A site's story",
                                    body = "This page has everything about the site: photos, its history and practical info like the entrance fee."
                                ),
                                TutorialStep(
                                    target = "site_tabs",
                                    title = "Read it your way",
                                    body = "Overview gives the summary, Timeline the key dates, Stories the people and events, and Sources where the facts come from. If a story has narration, tap play to listen."
                                ),
                                TutorialStep(
                                    target = "site_favorite",
                                    title = "Save to Favorites",
                                    body = "Tap the heart to keep this site in Menu > Favorites."
                                )
                            )
                        )

                        TutorialTour(
                            id = "site_ar_button",
                            enabled = arUnlocked && site.isArReady,
                            steps = listOf(
                                TutorialStep(
                                    target = "site_ar",
                                    title = "Travel back in time",
                                    body = "You're at the site, so its AR is unlocked. Tap here, point your camera at the area and see the site as it once looked."
                                )
                            )
                        )

                        Box(Modifier.tutorialTarget("site_tabs")) {
                            SiteContentTabRow(
                                selectedTab = uiState.selectedTab,
                                onTabSelected = { tab ->
                                    siteDetailsViewModel.onTabSelected(tab)
                                }
                            )
                        }

                        Spacer(Modifier.height(20.dp))

                        SiteContentTabBody(
                            selectedTab = uiState.selectedTab,
                            content = content,
                            fallbackOverview = site.description,
                            voiceover = voiceover,
                            onSourceClick = { url ->
                                // Adds a missing https:// and shows a message
                                // instead of crashing when nothing can open it.
                                com.histoury.app.util.LinkOpener.openWebLink(context, url)
                            }
                        )

                        Spacer(Modifier.height(28.dp))

                        // Two conditions gate the AR button, and they are
                        // deliberately separate.
                        //
                        //   arUnlocked     the location gate — you have
                        //                  arrived at this site, or GPS
                        //                  shows you're there now.
                        //   isArReady      the content gate — this site has
                        //                  AR switched on and a model to
                        //                  place.
                        //
                        // The content gate no longer asks for a reference
                        // photo or coordinates. Placement is manual, so
                        // there is nothing for the app to be missing; the
                        // exact-site geofence check that decides whether AR
                        // may actually run lives in the AR screen itself.
                        val hasArContent = site.isArReady

                        if (arUnlocked && hasArContent) {

                            Row(
                                modifier = Modifier
                                    .tutorialTarget("site_ar")
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(AccentGold)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        navController.navigateSafely(
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

                        // The last thing on the page; keep it clear of the
                        // gesture bar on edge-to-edge phones.
                        Spacer(Modifier.navigationBarsPadding())
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
