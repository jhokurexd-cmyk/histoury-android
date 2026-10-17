package com.histoury.app.ui.screens.map

import com.histoury.app.theme.styledElevation
import com.histoury.app.ui.tutorial.TutorialStep
import com.histoury.app.ui.tutorial.TutorialTour
import com.histoury.app.ui.tutorial.tutorialTarget
import com.histoury.app.navigation.navigateSafely
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Directions
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import com.histoury.app.ui.components.rememberThemedMapColorScheme
import com.histoury.app.ui.components.rememberThemedMapProperties
import com.histoury.app.map.MapConfig
import com.histoury.app.ui.map.OverviewMapLibre
import com.histoury.app.data.viewmodel.MapOverviewViewModel
import com.histoury.app.navigation.Routes
import com.histoury.app.theme.Primary
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextTertiary
import com.histoury.app.ui.components.BottomNavScaffold
import com.histoury.app.ui.components.BottomNavBarInset
import androidx.compose.foundation.layout.PaddingValues
import com.histoury.app.theme.CardSurface

// Starting camera only — from here the map browses the whole world freely,
// exactly like Google Maps.
private val INTRAMUROS_CENTER = LatLng(14.5896, 120.9757)

// Hue of the app's coral (#FF5A66) for the standard Google pin.
private const val CORAL_PIN_HUE = 355f

/**
 * The Map tab: a free-browse map like Google Maps, with every Histoury
 * site pinned on it.
 *
 * The pins used to be left off on purpose, but a first-time visitor opening
 * the Map tab of a tourism app saw an empty map with a search bar and no
 * hint of where anything was. Tapping a pin shows the site's name; tapping
 * that label opens its directions map, same as choosing it from Home.
 *
 * The search bar still finds anything: Histoury sites (tap -> directions)
 * and any other place in the world (tap -> camera moves there).
 */
@Composable
fun MapOverviewScreen(
    navController: NavHostController,
    mapOverviewViewModel: MapOverviewViewModel = viewModel()
) {

    val searchQuery by mapOverviewViewModel.searchQuery.collectAsState()

    val siteResults by mapOverviewViewModel.siteResults.collectAsState()

    val placeResults by mapOverviewViewModel.placeResults.collectAsState()

    val cameraTarget by mapOverviewViewModel.cameraTarget.collectAsState()

    val sites by mapOverviewViewModel.sites.collectAsState()

    val selectedSite by mapOverviewViewModel.selectedSite.collectAsState()

    val visitedSiteIds by mapOverviewViewModel.visitedSiteIds.collectAsState()

    // Back closes the site card before it leaves the tab.
    androidx.activity.compose.BackHandler(enabled = selectedSite != null) {
        mapOverviewViewModel.selectSite(null)
    }

    val context = LocalContext.current

    // Home already runs the full permission flow; here we only check what's
    // already granted so the blue "my location" dot can show.
    //
    // Re-checked every time the tab comes back to the foreground. It used
    // to be read once, so granting location in Settings (or on another
    // screen) left this map without the dot until the app was restarted.
    fun locationGranted() = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    var hasLocationPermission by remember { mutableStateOf(locationGranted()) }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasLocationPermission = locationGranted()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val distanceContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        com.histoury.app.util.UserDistance.refresh(distanceContext)
    }

    LaunchedEffect(Unit) {
        mapOverviewViewModel.loadSites()
        mapOverviewViewModel.refreshVisitedSites()
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(INTRAMUROS_CENTER, 15.5f)
    }

    // Selecting a generic place moves the camera there, Google-Maps style.
    LaunchedEffect(cameraTarget) {

        val target = cameraTarget ?: return@LaunchedEffect
        // The MapLibre map moves its own camera (OverviewMapLibre).
        if (MapConfig.useMapLibre) return@LaunchedEffect

        try {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(target.latLng, 15f)
            )
            mapOverviewViewModel.onCameraTargetHandled()
        } catch (e: Exception) {
            // Map not laid out yet.
        }
    }

    // The map's tiles take a moment on first open; a soft cover fades out
    // once they're drawn instead of the grey grid popping in.
    var mapLoaded by remember { mutableStateOf(false) }
    // Offline, onMapLoaded may never come (no tiles to finish) — never
    // leave the cover up for more than a moment.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1_500)
        mapLoaded = true
    }

    BottomNavScaffold(navController = navController) { padding ->

        TutorialTour(
            id = "map_overview",
            steps = listOf(
                TutorialStep(
                    target = null,
                    title = "The map of Intramuros",
                    body = "Every historical site is a coral pin. Pinch to zoom and drag to move around. The round button on the map jumps back to where you are."
                ),
                TutorialStep(
                    target = "map_search",
                    title = "Search the map",
                    body = "Type a site's name to find it and jump straight to its pin."
                ),
                TutorialStep(
                    target = null,
                    title = "Tap a pin",
                    body = "Tapping a pin opens that site's card, with directions and its story. Tap empty map to close it."
                )
            )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            if (MapConfig.useMapLibre) {
                OverviewMapLibre(
                    sites = sites,
                    cameraTarget = cameraTarget,
                    onCameraTargetHandled = { mapOverviewViewModel.onCameraTargetHandled() },
                    showUserLocation = hasLocationPermission,
                    topInsetDp = 74f,
                    bottomInsetDp = BottomNavBarInset.value,
                    // A tap opens the site card below (no callout).
                    onSiteClick = { site -> mapOverviewViewModel.selectSite(site) },
                    // Tapping empty map dismisses the site card.
                    onMapClick = { mapOverviewViewModel.selectSite(null) },
                    onMapLoaded = { mapLoaded = true },
                    modifier = Modifier.fillMaxSize()
                )
            } else GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                onMapLoaded = { mapLoaded = true },
                // Tapping empty map dismisses the site card.
                onMapClick = { mapOverviewViewModel.selectSite(null) },
                properties = rememberThemedMapProperties(
                    isMyLocationEnabled = hasLocationPermission
                ),
                mapColorScheme = rememberThemedMapColorScheme(),
                uiSettings = MapUiSettings(
                    myLocationButtonEnabled = hasLocationPermission,
                    zoomControlsEnabled = false
                ),
                // Google draws its own controls — the my-location button and
                // the Google wordmark — inside the map's own bounds, with no
                // knowledge of what is overlaid on top. Content padding is
                // how the SDK is told where the usable area actually is, so
                // the button drops below the floating search bar instead of
                // sitting behind it.
                contentPadding = PaddingValues(
                    top = 74.dp,
                    bottom = BottomNavBarInset
                )
            ) {

                // Coral pins, matching the app's accent.
                val pinIcon = remember {
                    BitmapDescriptorFactory.defaultMarker(CORAL_PIN_HUE)
                }

                sites.forEach { site ->

                    val lat = site.latitude ?: return@forEach
                    val lng = site.longitude ?: return@forEach

                    // Keyed by site so a list refresh moves nothing: pins
                    // used to be matched up by position and briefly
                    // swapped places when the list changed.
                    key(site.documentId) {

                    val markerState = remember(site.documentId, lat, lng) {
                        MarkerState(position = LatLng(lat, lng))
                    }

                    // A tap opens the site card below instead of the small
                    // info window, whose "Tap for directions" was a second,
                    // tiny target on top of the pin and the only action on
                    // offer.
                    Marker(
                        state = markerState,
                        title = site.siteName,
                        icon = pinIcon,
                        onClick = {
                            mapOverviewViewModel.selectSite(site)
                            true
                        }
                    )
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = !mapLoaded,
                enter = androidx.compose.animation.EnterTransition.None,
                exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(350))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(com.histoury.app.theme.Background),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = com.histoury.app.theme.Primary,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Floating search bar + results, overlaid Google-Maps style.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter)
            ) {

                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = CardSurface,
                    shadowElevation = styledElevation(6.dp),
                    modifier = Modifier.tutorialTarget("map_search").fillMaxWidth()
                ) {

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .heightIn(min = 52.dp)
                            .padding(start = 16.dp, end = 4.dp)
                    ) {

                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(19.dp)
                        )

                        Spacer(Modifier.width(10.dp))

                        Box(modifier = Modifier.weight(1f)) {

                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search places...",
                                    color = TextTertiary,
                                    fontSize = 14.sp
                                )
                            }

                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { query ->
                                    mapOverviewViewModel.onSearchQueryChange(query)
                                },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontSize = 14.sp,
                                    color = TextPrimary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (searchQuery.isNotEmpty()) {

                            Spacer(Modifier.width(10.dp))

                            // 44dp target; the bare 18dp icon was easy to miss.
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clickable { mapOverviewViewModel.clearSearch() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    tint = TextTertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                if (siteResults.isNotEmpty() || placeResults.isNotEmpty()) {

                    Spacer(Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = CardSurface,
                        shadowElevation = styledElevation(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        Column {

                            // Histoury sites first — tapping opens the
                            // site's directions map.
                            siteResults.forEach { site ->

                                SearchResultRow(
                                    icon = Icons.Default.LocationOn,
                                    iconTint = Primary,
                                    title = site.siteName,
                                    subtitle = site.location,
                                    onClick = {
                                        // Same as tapping its pin: fly there
                                        // and show its card, with directions
                                        // one tap away.
                                        mapOverviewViewModel.clearSearch()
                                        mapOverviewViewModel.selectSite(site)
                                        val lat = site.latitude
                                        val lng = site.longitude
                                        if (lat != null && lng != null) {
                                            mapOverviewViewModel.onPlaceSelected(
                                                com.histoury.app.data.viewmodel.PlaceResult(site.siteName, LatLng(lat, lng))
                                            )
                                        }
                                    }
                                )
                            }

                            // Then anywhere else in the world — tapping
                            // moves the camera there.
                            placeResults.forEach { place ->

                                SearchResultRow(
                                    icon = Icons.Default.Place,
                                    iconTint = TextTertiary,
                                    title = place.name,
                                    subtitle = null,
                                    onClick = {
                                        mapOverviewViewModel.onPlaceSelected(place)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            selectedSite?.let { _ ->
                // First time a site card opens: what its two buttons do.
                TutorialTour(
                    id = "map_site_card",
                    steps = listOf(
                        TutorialStep(
                            target = "map_card_directions",
                            title = "Get directions",
                            body = "Shows the walking (or driving) route from where you are, with distance and arrival time. Tap Navigate there for turn-by-turn guidance."
                        ),
                        TutorialStep(
                            target = "map_card_info",
                            title = "Site info",
                            body = "The site's full history, timeline and audio guide. It stays locked until you've visited the site in person."
                        )
                    )
                )
            }

            selectedSite?.let { site ->
                MapSiteCard(
                    site = site,
                    isUnlocked = site.siteId in visitedSiteIds,
                    onClose = { mapOverviewViewModel.selectSite(null) },
                    onOpenInfo = {
                        navController.navigateSafely(Routes.SiteDetails.createRoute(site.documentId, showAr = false))
                    },
                    onDirections = {
                        navController.navigateSafely(Routes.Map.createRoute(site.documentId))
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 16.dp, end = 16.dp, bottom = BottomNavBarInset + 12.dp)
                )
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String?,
    onClick: () -> Unit
) {

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(18.dp)
        )

        Spacer(Modifier.width(10.dp))

        Column {

            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1
            )

            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = TextTertiary,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * The card a tapped pin opens: what the site is, the Favorites heart, and
 * its two actions. Site info is only offered once the site has been visited
 * (the same unlock rule as Menu > Site Information).
 */
@Composable
private fun MapSiteCard(
    site: com.histoury.app.data.model.HistoricalSite,
    isUnlocked: Boolean,
    onClose: () -> Unit,
    onOpenInfo: () -> Unit,
    onDirections: () -> Unit,
    modifier: Modifier = Modifier
) {

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = CardSurface,
        shadowElevation = styledElevation(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {

        Column(modifier = Modifier.padding(12.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {

                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(com.histoury.app.theme.SurfaceSoft)
                ) {
                    if (site.featuredImage.isNotBlank()) {
                        coil.compose.AsyncImage(
                            model = site.featuredImage,
                            contentDescription = site.siteName,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = site.siteName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    val userPosition by com.histoury.app.util.UserDistance.position.collectAsState()
                    val distance = com.histoury.app.util.UserDistance
                        .labelFor(site.latitude, site.longitude, userPosition)
                        ?.let { if (it == "Nearby") it else "$it away" }
                    Text(
                        text = listOfNotNull(
                            if (site.reviewCount > 0) "\u2605 %.1f".format(site.averageRating) else null,
                            distance,
                            site.location.takeIf { it.isNotBlank() }
                        ).joinToString(" \u00b7 "),
                        fontSize = 12.sp,
                        color = com.histoury.app.theme.TextSecondary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                com.histoury.app.ui.components.SiteFavoriteButton(
                    siteDocumentId = site.documentId,
                    siteName = site.siteName,
                    style = com.histoury.app.ui.components.FavoriteButtonStyle.TONAL
                )

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .tutorialTarget("map_card_info")
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(com.histoury.app.theme.PrimarySoft.copy(alpha = if (isUnlocked) 1f else 0.5f))
                        .clickable(enabled = isUnlocked, onClick = onOpenInfo),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isUnlocked) Icons.Default.MenuBook else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (isUnlocked) Primary else TextTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isUnlocked) "Site info" else "Visit to unlock",
                        color = if (isUnlocked) Primary else TextTertiary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .tutorialTarget("map_card_directions")
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Primary)
                        .clickable(onClick = onDirections),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Directions,
                        contentDescription = null,
                        tint = com.histoury.app.theme.OnColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Directions",
                        color = com.histoury.app.theme.OnColor,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
