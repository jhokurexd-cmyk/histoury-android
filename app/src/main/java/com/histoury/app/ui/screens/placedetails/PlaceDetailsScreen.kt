package com.histoury.app.ui.screens.placedetails

import com.histoury.app.navigation.safePopBackStack
import com.histoury.app.navigation.navigateSafely
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Facebook
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.histoury.app.ui.components.rememberThemedMapProperties
import com.histoury.app.map.MapConfig
import com.histoury.app.ui.map.PlacePreviewMapLibre
import androidx.compose.material.icons.filled.Map
import com.histoury.app.data.model.Place
import com.histoury.app.data.viewmodel.PlaceDetailsViewModel
import com.histoury.app.navigation.Routes
import com.histoury.app.theme.SurfaceSoft
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Primary
import com.histoury.app.theme.PrimarySoft
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary
import com.histoury.app.ui.components.WrapRow
import com.histoury.app.ui.components.ImageLightbox
import androidx.compose.ui.layout.layout
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Storefront
import com.histoury.app.theme.Outline
import com.histoury.app.theme.TextTertiary
import com.histoury.app.ui.components.HeroCardOverlap
import com.histoury.app.ui.components.HeroIconButton
import com.histoury.app.ui.components.HeroInfoChip
import com.histoury.app.ui.components.HeroOverlayInset
import com.histoury.app.ui.components.PrimaryButton

/**
 * Showcase screen for non-historical places (cafés, restaurants, hotels,
 * souvenir shops, schools, banks) — reached by tapping a place card from
 * Home or Explore.
 *
 * Unlike SiteDetailsScreen, this is freely viewable any time: no arrival
 * gate, no AR, no tabs. It's a business listing, not unlockable
 * educational content.
 */
@Composable
fun PlaceDetailsScreen(
    placeId: String,
    navController: NavHostController,
    placeDetailsViewModel: PlaceDetailsViewModel = viewModel()
) {

    val uiState by placeDetailsViewModel.uiState.collectAsState()

    val context = LocalContext.current

    var lightboxIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(placeId) {
        placeDetailsViewModel.loadPlace(placeId)
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
                // No hero image (and so no back button) on this path — give
                // the error state its own retry and way out.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
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
                    androidx.compose.material3.TextButton(onClick = { placeDetailsViewModel.loadPlace(placeId) }) {
                        Text("Try again", color = Primary, fontWeight = FontWeight.Bold)
                    }
                    androidx.compose.material3.TextButton(onClick = { navController.safePopBackStack() }) {
                        Text("Go back", color = TextSecondary)
                    }
                }
            }

            uiState.place != null -> {

                val place = uiState.place!!

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {

                    // --- Hero image ---
                    //
                    // Sized from the screen width (4:3) rather than a fixed
                    // 280dp, so the crop looks the same on a narrow phone and
                    // a wide one, clamped so it never swallows a short screen
                    // or shrinks to a strip. Full-bleed under the status bar;
                    // only the controls step inside the safe area.
                    val heroHeight = (LocalConfiguration.current.screenWidthDp * 0.75f)
                        .coerceIn(240f, 360f)
                        .dp

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(heroHeight)
                            // Shown while the photo loads, or if it can't.
                            .background(SurfaceSoft)
                    ) {

                        if (place.featuredImage.isNotBlank()) {
                            AsyncImage(
                                model = place.featuredImage,
                                contentDescription = place.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = placeCategoryIcon(place.category),
                                contentDescription = null,
                                tint = TextTertiary,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(48.dp)
                            )
                        }

                        // Two scrims sized to the photo (the old one started
                        // at a fixed 380px, below the image on dense screens):
                        // a light one behind the Back button, a stronger one
                        // behind the category chip.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        0f to Color.Black.copy(alpha = 0.28f),
                                        0.28f to Color.Transparent,
                                        0.62f to Color.Transparent,
                                        1f to Color.Black.copy(alpha = 0.34f)
                                    )
                                )
                        )

                        // Insets first, then the shared button — which draws
                        // its own circle — so the safe-area padding moves the
                        // button instead of stretching it.
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

                        // Informational, not a button. Lifted clear of the
                        // card's overlap so the card never half-covers it,
                        // and lined up with the text below.
                        HeroInfoChip(
                            text = place.categoryLabel,
                            icon = placeCategoryIcon(place.category),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(
                                    start = CONTENT_MARGIN,
                                    end = CONTENT_MARGIN,
                                    bottom = HeroCardOverlap + 14.dp
                                )
                        )
                    }

                    // --- Details card, overlapping the hero ---
                    // The same card as Site Information: 28dp top corners,
                    // a 24dp overlap and a grab handle, so the two detail
                    // screens read as one family.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = -HeroCardOverlap)
                            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                            .background(CardSurface)
                            .padding(horizontal = CONTENT_MARGIN)
                            .padding(top = 12.dp, bottom = 8.dp)
                    ) {

                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .size(width = 38.dp, height = 4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Outline)
                        )

                        Spacer(Modifier.height(16.dp))

                        Text(
                            text = place.name,
                            fontSize = 24.sp,
                            lineHeight = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        val addressText = place.address.ifBlank { place.location }

                        if (addressText.isNotBlank()) {

                            Spacer(Modifier.height(8.dp))

                            // Top-aligned so a long address wraps under
                            // itself with the pin beside its first line.
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
                                Text(
                                    text = addressText,
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        if (place.openingHours.isNotBlank() || place.entranceFee.isNotBlank()) {

                            Spacer(Modifier.height(12.dp))

                            // Wraps instead of running off a narrow screen
                            // when both pills are long.
                            WrapRow(
                                horizontalSpacing = 6.dp,
                                verticalSpacing = 6.dp
                            ) {

                                if (place.openingHours.isNotBlank()) {
                                    InfoPill(
                                        icon = Icons.Default.Schedule,
                                        text = place.openingHours
                                    )
                                }

                                if (place.entranceFee.isNotBlank()) {
                                    InfoPill(
                                        icon = Icons.Default.ConfirmationNumber,
                                        text = place.entranceFee
                                    )
                                }
                            }
                        }

                        val descriptionText = place.fullDescription.ifBlank { place.description }

                        if (descriptionText.isNotBlank()) {

                            SectionHeading("About")

                            Text(
                                text = descriptionText,
                                color = TextSecondary,
                                fontSize = 14.sp,
                                lineHeight = 22.sp
                            )
                        }

                        // Where it is: a small, still map with the place's
                        // pin. Tapping it opens the full map with directions.
                        val placeLatLng = remember(place.latitude, place.longitude) {
                            val lat = place.latitude
                            val lng = place.longitude
                            if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0 &&
                                !(lat == 0.0 && lng == 0.0)
                            ) LatLng(lat, lng) else null
                        }

                        if (placeLatLng != null) {

                            SectionHeading("Location")

                            PlaceMapPreview(
                                position = placeLatLng,
                                title = place.name,
                                onOpen = {
                                    navController.navigateSafely(
                                        Routes.Map.createRoute(place.documentId)
                                    )
                                }
                            )

                            val addressLine = place.address.ifBlank { place.location }
                            if (addressLine.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = addressLine,
                                    color = TextSecondary,
                                    fontSize = 12.5.sp,
                                    lineHeight = 17.sp
                                )
                            }
                        }

                        // Hotels with a booking link: send the visitor to the
                        // hotel's own booking page (Histoury takes no bookings).
                        if (place.bookingUrl.isNotBlank()) {
                            Spacer(Modifier.height(16.dp))
                            com.histoury.app.ui.components.BookStayButton(place.bookingUrl)
                        }

                        if (place.galleryImages.isNotEmpty()) {

                            SectionHeading("Gallery")

                            // Runs to the screen edges while scrolling, but
                            // starts and ends on the same margin as the text
                            // above it.
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .layout { measurable, constraints ->
                                        val bleed = CONTENT_MARGIN.roundToPx()
                                        val placeable = measurable.measure(
                                            constraints.copy(
                                                minWidth = constraints.maxWidth + bleed * 2,
                                                maxWidth = constraints.maxWidth + bleed * 2
                                            )
                                        )
                                        layout(constraints.maxWidth, placeable.height) {
                                            placeable.place(-bleed, 0)
                                        }
                                    },
                                contentPadding = PaddingValues(horizontal = CONTENT_MARGIN),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                itemsIndexed(place.galleryImages) { index, imageUrl ->
                                    AsyncImage(
                                        model = imageUrl,
                                        contentDescription = "${place.name}, photo ${index + 1} of ${place.galleryImages.size}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(width = 132.dp, height = 104.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(SurfaceSoft)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                lightboxIndex = index
                                            }
                                    )
                                }
                            }
                        }

                        // Google Reviews: opens the place's Google Maps
                        // listing (app if installed, else the browser),
                        // where its ratings and reviews are. Built from the
                        // name + address/coordinates, so it needs no extra
                        // field from the admin panel.
                        googleReviewsUri(place)?.let { reviewsUri ->

                            SectionHeading("Reviews")

                            ContactRow(
                                icon = Icons.Default.Star,
                                label = "See Google Reviews"
                            ) {
                                launchOrExplain(context) {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, reviewsUri))
                                }
                            }
                        }

                        val hasContactInfo = place.contactPhone.isNotBlank() ||
                            place.contactWebsite.isNotBlank() ||
                            place.contactFacebook.isNotBlank() ||
                            place.contactEmail.isNotBlank()

                        if (hasContactInfo) {

                            SectionHeading("Contact")

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                                if (place.contactPhone.isNotBlank()) {
                                    ContactRow(
                                        icon = Icons.Default.Phone,
                                        label = place.contactPhone
                                    ) {
                                        launchOrExplain(context) {
                                            context.startActivity(
                                                Intent(Intent.ACTION_DIAL).apply {
                                                    data = Uri.parse("tel:${place.contactPhone}")
                                                }
                                            )
                                        }
                                    }
                                }

                                if (place.contactWebsite.isNotBlank()) {
                                    ContactRow(
                                        icon = Icons.Default.Language,
                                        label = place.contactWebsite
                                    ) {
                                        launchOrExplain(context) {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, webUri(place.contactWebsite))
                                            )
                                        }
                                    }
                                }

                                if (place.contactFacebook.isNotBlank()) {
                                    ContactRow(
                                        icon = Icons.Default.Facebook,
                                        label = place.contactFacebook
                                    ) {
                                        launchOrExplain(context) {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, webUri(place.contactFacebook, facebook = true))
                                            )
                                        }
                                    }
                                }

                                if (place.contactEmail.isNotBlank()) {
                                    ContactRow(
                                        icon = Icons.Default.Email,
                                        label = place.contactEmail
                                    ) {
                                        launchOrExplain(context) {
                                            context.startActivity(
                                                Intent(Intent.ACTION_SENDTO).apply {
                                                    data = Uri.parse("mailto:${place.contactEmail}")
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(28.dp))

                        PrimaryButton(
                            text = "Navigate",
                            onClick = {
                                navController.navigateSafely(
                                    Routes.Map.createRoute(place.documentId)
                                )
                            }
                        )

                        // The card is pulled up by the overlap, which would
                        // otherwise leave that much empty space at the end of
                        // the scroll; the gesture bar is cleared on top.
                        Spacer(Modifier.height(16.dp))
                        Spacer(Modifier.navigationBarsPadding())
                    }
                }
            }
        }
    }

    val currentLightboxIndex = lightboxIndex

    if (currentLightboxIndex != null) {
        ImageLightbox(
            images = uiState.place?.galleryImages ?: emptyList(),
            startIndex = currentLightboxIndex,
            onDismiss = { lightboxIndex = null }
        )
    }
}

@Composable
private fun InfoPill(icon: ImageVector, text: String) {

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = PrimarySoft
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = text,
                color = Primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * A small non-interactive map centred on [position] with a pin. Lite mode
 * draws it as a single image, so it scrolls smoothly inside the page and
 * doesn't swallow scroll gestures; the whole card is one tap target that
 * opens the full map.
 */
@Composable
private fun PlaceMapPreview(
    position: LatLng,
    title: String,
    onOpen: () -> Unit
) {
    val pin = position
    val cameraPositionState = rememberCameraPositionState(key = pin.toString()) {
        this.position = CameraPosition.fromLatLngZoom(pin, 17f)
    }
    val markerState = remember(position) { MarkerState(position = position) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceSoft)
    ) {
        if (MapConfig.useMapLibre) {
            PlacePreviewMapLibre(
                position = position,
                title = title,
                onOpen = onOpen,
                modifier = Modifier.fillMaxSize()
            )
        } else GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) },
            properties = rememberThemedMapProperties(isMyLocationEnabled = false, liteMode = true),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                mapToolbarEnabled = false,
                myLocationButtonEnabled = false,
                scrollGesturesEnabled = false,
                zoomGesturesEnabled = false,
                rotationGesturesEnabled = false,
                tiltGesturesEnabled = false
            ),
            onMapClick = { onOpen() }
        ) {
            Marker(
                state = markerState,
                title = title,
                icon = BitmapDescriptorFactory.defaultMarker(PLACE_PIN_HUE)
            )
        }

        // One tap target over the whole preview (lite maps otherwise hand
        // taps to the Google Maps app).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "Open map",
                    onClick = onOpen
                )
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
                .clip(RoundedCornerShape(50))
                .background(CardSurface)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Map,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Open map",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/** The app's coral, as a marker hue. */
private const val PLACE_PIN_HUE = 355f

@Composable
private fun ContactRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceSoft)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .heightIn(min = 52.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(PrimarySoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text = label,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

/**
 * Admin-entered links are often typed without a scheme ("www.cafe.ph",
 * "facebook.com/cafe", or just a page name). Uri.parse() of those gives an
 * intent no browser accepts, so tapping the row silently did nothing.
 */
private fun webUri(raw: String, facebook: Boolean = false): Uri {
    val value = raw.trim()
    val withHost = when {
        value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true) -> value
        facebook && !value.contains('.') -> "https://www.facebook.com/${value.removePrefix("@")}"
        else -> "https://$value"
    }
    return Uri.parse(withHost)
}

/**
 * A Google Maps search link for [place] — it lands on the place's listing,
 * whose Reviews tab has its Google rating and reviews. Null when there's
 * nothing to search for.
 *
 * The query is the name plus the address (or, failing that, the location
 * text), always anchored to Intramuros so a common name like "Starbucks"
 * finds this branch rather than the nearest one to the phone.
 */
private fun googleReviewsUri(place: com.histoury.app.data.model.Place): Uri? {
    val name = place.name.trim()
    if (name.isBlank()) return null
    val where = place.address.trim().ifBlank { place.location.trim() }
    val query = buildString {
        append(name)
        if (where.isNotBlank()) append(", ").append(where)
        if (!contains("intramuros", ignoreCase = true)) append(", Intramuros, Manila")
    }
    return Uri.parse("https://www.google.com/maps/search/")
        .buildUpon()
        .appendQueryParameter("api", "1")
        .appendQueryParameter("query", query)
        .build()
}

/**
 * Runs an outside-app action (dial, browser, email). These used to be
 * wrapped in a bare runCatching, so on a phone with no app for the job the
 * row simply did nothing when tapped; now the visitor is told why.
 */
private inline fun launchOrExplain(context: android.content.Context, block: () -> Unit) {
    try {
        block()
    } catch (e: Exception) {
        android.widget.Toast
            .makeText(context, "No app on this phone can open that.", android.widget.Toast.LENGTH_SHORT)
            .show()
    }
}

/** Horizontal margin for everything in the details card (matches Site Information). */
private val CONTENT_MARGIN = 20.dp

/** Section title with the same spacing above and below every time. */
@Composable
private fun SectionHeading(text: String) {
    Spacer(Modifier.height(24.dp))
    Text(
        text = text,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary
    )
    Spacer(Modifier.height(10.dp))
}

/**
 * A Material icon for a place's category, used by the hero chip. Replaces
 * the emoji it used to show: emoji sit off the text's baseline and look
 * different on every phone's font, so the chip never lined up.
 */
private fun placeCategoryIcon(category: String): ImageVector = when (category.lowercase()) {
    "food", "cafe" -> Icons.Default.LocalCafe
    "restaurant" -> Icons.Default.Restaurant
    "park" -> Icons.Default.Park
    "hotel" -> Icons.Default.Hotel
    "souvenir_shop" -> Icons.Default.Storefront
    "school" -> Icons.Default.School
    "bank" -> Icons.Default.AccountBalanceWallet
    else -> Icons.Default.Place
}
