package com.histoury.app.ui.screens.arrival

import com.histoury.app.theme.overlayShadow
import com.histoury.app.ui.tutorial.TutorialStep
import com.histoury.app.ui.tutorial.TutorialTour
import com.histoury.app.ui.tutorial.tutorialTarget
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.histoury.app.R
import com.histoury.app.data.itinerary.ActiveItineraryTrip
import com.histoury.app.data.model.HistoricalSite
import com.histoury.app.data.model.ItineraryStop
import com.histoury.app.data.model.StopStatus
import com.histoury.app.data.offline.ArModelCache
import com.histoury.app.data.viewmodel.ArrivalUiState
import com.histoury.app.data.viewmodel.ArrivalViewModel
import com.histoury.app.navigation.Routes
import com.histoury.app.navigation.navigateSafely
import com.histoury.app.navigation.navigateToTab
import com.histoury.app.navigation.safePopBackStack
import com.histoury.app.theme.AccentGold
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Danger
import com.histoury.app.theme.Divider
import com.histoury.app.theme.Outline
import com.histoury.app.theme.Primary as PrimaryPink
import com.histoury.app.theme.PrimarySoft
import com.histoury.app.theme.Success
import com.histoury.app.theme.SuccessSurface
import com.histoury.app.theme.SurfaceSoft
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary
import com.histoury.app.theme.TextTertiary
import com.histoury.app.ui.components.PrimaryButton
import com.histoury.app.ui.components.rememberThemedMapColorScheme
import com.histoury.app.ui.components.rememberThemedMapProperties
import com.histoury.app.map.MapConfig
import com.histoury.app.ui.map.ArrivalMapLibre
import kotlin.math.ceil

// =============================================================================
// Arrival — redesigned
//
// Arrive → Explore Site (with AR inside it) → Continue Tour.
//
// The map stays on screen, centred on the site, with the visitor's own
// location; the arrival details live in a card that floats over the bottom
// of it and can be collapsed to a single line ("✓ San Agustin Church —
// Arrived") by tapping or swiping it down.
//
// One primary action (Explore Site, coral — Site Information, which also
// holds the AR button), and Continue to Next Site as a light, bordered row
// further down so it never competes with it. Skip and Pause
// sit in the card's ⋯ menu.
//
// Explore Site and AR push their own screens on top of this one, so Back
// returns here with the trip, the stop and its progress exactly as they
// were: the stop only finishes when Continue is pressed.
// =============================================================================

/** Average walking pace used for the "8 min walk" estimate to the next stop. */
private const val WALKING_METERS_PER_MINUTE = 80.0

@Composable
fun ArrivalScreen(
    siteId: String,
    navController: NavHostController,
    arrivalViewModel: ArrivalViewModel = viewModel()
) {

    val uiState by arrivalViewModel.uiState.collectAsState()

    val context = LocalContext.current

    // Arrival chime, once, when the site has loaded. Remembered across
    // coming back from Site Details or AR, and rotation, so it doesn't play
    // again every time the screen reappears.
    val loadedSiteId = uiState.site?.documentId
    var chimed by rememberSaveable(siteId) { mutableStateOf(false) }

    DisposableEffect(loadedSiteId) {

        var mediaPlayer: MediaPlayer? = null

        if (loadedSiteId != null && !chimed) {
            chimed = true
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            mediaPlayer = MediaPlayer.create(context, R.raw.arrival_chime, audioAttributes, 0)?.apply {
                setVolume(1f, 1f)
                setOnCompletionListener { player ->
                    player.release()
                    mediaPlayer = null
                }
                start()
            }
        }

        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    LaunchedEffect(siteId) {
        arrivalViewModel.loadArrivedSite(siteId)
    }

    // The visitor is at the site: start downloading its AR model now, in the
    // background, so AR opens without waiting for it (see ArModelCache).
    LaunchedEffect(uiState.site?.documentId) {
        ArModelCache.prefetch(context, uiState.site)
    }

    val hasLocationPermission = remember {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    }

    // Expanded by default; survives going to Site Information / AR and back.
    var expanded by rememberSaveable { mutableStateOf(true) }

    val site = uiState.site
    val tripProgress = uiState.tripProgress

    val siteLatLng = remember(site, tripProgress) {
        val lat = site?.latitude ?: tripProgress?.currentStop?.latitude?.takeIf { it != 0.0 }
        val lng = site?.longitude ?: tripProgress?.currentStop?.longitude?.takeIf { it != 0.0 }
        if (lat != null && lng != null) LatLng(lat, lng) else null
    }

    val nextStop = tripProgress?.nextStop
    val nextLatLng = nextStop
        ?.takeIf { it.latitude != 0.0 || it.longitude != 0.0 }
        ?.let { LatLng(it.latitude, it.longitude) }

    Box(modifier = Modifier.fillMaxSize().background(SurfaceSoft)) {

        // ------------------------------------------------------------- map

        val cameraPositionState = rememberCameraPositionState()

        LaunchedEffect(siteLatLng) {
            if (siteLatLng != null) {
                cameraPositionState.position = CameraPosition.fromLatLngZoom(siteLatLng, 17.5f)
            }
        }

        // After a stop is finished, frame the way on to the next one.
        LaunchedEffect(uiState.lastStopOutcome, nextLatLng) {
            // The MapLibre map frames this itself (ArrivalMapLibre).
            if (MapConfig.useMapLibre) return@LaunchedEffect
            if (uiState.lastStopOutcome != null && siteLatLng != null && nextLatLng != null) {
                runCatching {
                    val bounds = com.google.android.gms.maps.model.LatLngBounds.builder()
                        .include(siteLatLng)
                        .include(nextLatLng)
                        .build()
                    cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 180), 700)
                }
            }
        }

        if (MapConfig.useMapLibre) {
            ArrivalMapLibre(
                siteLatLng = siteLatLng,
                siteTitle = site?.siteName ?: "You've arrived",
                siteImageUrl = site?.featuredImage,
                nextLatLng = nextLatLng,
                nextTitle = nextStop?.name,
                showNext = uiState.lastStopOutcome != null,
                showUserLocation = hasLocationPermission,
                topInsetDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value,
                bottomInsetDp = if (expanded) 300f else 96f,
                modifier = Modifier.fillMaxSize()
            )
        } else GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            // Keeps the map's logo / controls clear of the status bar and of
            // the card over the bottom of it.
            contentPadding = PaddingValues(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                bottom = if (expanded) 300.dp else 96.dp
            ),
            properties = rememberThemedMapProperties(isMyLocationEnabled = hasLocationPermission),
            mapColorScheme = rememberThemedMapColorScheme(),
            uiSettings = MapUiSettings(
                myLocationButtonEnabled = false,
                zoomControlsEnabled = false,
                mapToolbarEnabled = false
            )
        ) {
            if (siteLatLng != null) {
                // The current destination, in the brand colour.
                Marker(
                    state = remember(siteLatLng) { MarkerState(position = siteLatLng) },
                    title = site?.siteName ?: "You've arrived",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED),
                    zIndex = 2f
                )
            }
            if (nextLatLng != null && uiState.lastStopOutcome != null) {
                Marker(
                    state = remember(nextLatLng) { MarkerState(position = nextLatLng) },
                    title = "Next: ${nextStop?.name.orEmpty()}",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE),
                    alpha = 0.85f,
                    zIndex = 1f
                )
            }
        }

        // Back: to wherever the visitor came from (the trip stays active).
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp)
                .size(44.dp)
                .overlayShadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(CardSurface)
                .clickable {
                    if (!navController.safePopBackStack()) {
                        navController.navigateToTab(Routes.Home.route)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = PrimaryPink
            )
        }

        // ----------------------------------------------------------- card

        ArrivalCard(
            uiState = uiState,
            expanded = expanded,
            onExpandedChange = { expanded = it },
            navController = navController,
            arrivalViewModel = arrivalViewModel,
            siteId = siteId,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // --------------------------------------------------------- dialogs

        if (uiState.showSkipConfirmation) {
            AlertDialog(
                onDismissRequest = { arrivalViewModel.cancelSkip() },
                title = { Text("Skip this stop?") },
                text = { Text("You won't be able to come back to it during this trip.") },
                confirmButton = {
                    TextButton(onClick = { arrivalViewModel.confirmSkip() }) {
                        Text("Skip", color = Danger, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { arrivalViewModel.cancelSkip() }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            )
        }

        val outOfOrderPrompt = uiState.outOfOrderPrompt

        if (outOfOrderPrompt != null) {
            // Not dismissable by tapping outside — either answer changes what
            // "next" means for the rest of the trip.
            AlertDialog(
                onDismissRequest = { },
                title = { Text("You arrived at a different itinerary stop") },
                text = {
                    Text(
                        "${outOfOrderPrompt.arrivedStop.name} is Stop " +
                            "${outOfOrderPrompt.arrivedStop.order + 1} of your itinerary, but " +
                            "${outOfOrderPrompt.expectedStop.name} is still your next planned stop.\n\n" +
                            "Optimize Route recalculates the rest of your trip starting from here. " +
                            "Keep Original Order leaves your saved plan exactly as it was."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { arrivalViewModel.optimizeRouteFromArrival() },
                        enabled = !uiState.isOptimizingRoute
                    ) {
                        Text("Optimize Route", color = PrimaryPink, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { arrivalViewModel.keepOriginalOrder() },
                        enabled = !uiState.isOptimizingRoute
                    ) {
                        Text("Keep Original Order", color = TextSecondary)
                    }
                }
            )
        }
    }
}

// =============================================================================
// The floating card
// =============================================================================

@Composable
private fun ArrivalCard(
    uiState: ArrivalUiState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    navController: NavHostController,
    arrivalViewModel: ArrivalViewModel,
    siteId: String,
    modifier: Modifier = Modifier
) {

    val site = uiState.site
    val maxCardHeight = (LocalConfiguration.current.screenHeightDp * 0.72f).dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxCardHeight)
            .overlayShadow(16.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(CardSurface)
            .navigationBarsPadding()
            .animateContentSize()
    ) {

        // Handle + collapsed summary. Tap or swipe to expand / collapse.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(expanded) {
                    var dragged = 0f
                    detectVerticalDragGestures(
                        onDragStart = { dragged = 0f },
                        onVerticalDrag = { _, amount -> dragged += amount },
                        onDragEnd = {
                            if (dragged > 40f && expanded) onExpandedChange(false)
                            if (dragged < -40f && !expanded) onExpandedChange(true)
                        }
                    )
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onExpandedChange(!expanded) }
                .padding(horizontal = 24.dp)
                .padding(top = 10.dp, bottom = if (expanded) 4.dp else 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Divider)
            )

            AnimatedVisibility(
                visible = !expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                CollapsedSummary(uiState)
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp, bottom = 22.dp)
            ) {
                when {
                    uiState.isLoading && site == null -> LoadingContent()

                    site == null -> LoadErrorContent(
                        message = uiState.loadErrorMessage ?: "Couldn't load details for this destination.",
                        belongsToActiveTrip = ActiveItineraryTrip.progressFor(siteId) != null,
                        navController = navController
                    )

                    else -> AnimatedContent(
                        targetState = uiState.lastStopOutcome != null,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "arrivalState"
                    ) { resolved ->
                        // AnimatedContent lays its content out in a Box, so
                        // without this Column every line of the card was
                        // drawn on top of the others.
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (resolved) {
                                StopResolvedContent(
                                    uiState = uiState,
                                    site = site,
                                    navController = navController,
                                    onStayLonger = { arrivalViewModel.dismissStopResolvedBanner() }
                                )
                            } else {
                                ArrivedContent(
                                    uiState = uiState,
                                    site = site,
                                    navController = navController,
                                    arrivalViewModel = arrivalViewModel
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollapsedSummary(uiState: ArrivalUiState) {

    val name = uiState.site?.siteName.orEmpty()

    val text = when {
        uiState.site == null && uiState.isLoading -> "Loading…"
        uiState.site == null -> "Couldn't load this destination"
        uiState.lastStopOutcome == StopStatus.SKIPPED -> "$name — Skipped"
        uiState.lastStopOutcome != null -> "$name — Completed"
        else -> "$name — Arrived"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Success,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Default.KeyboardArrowUp,
            contentDescription = "Expand",
            tint = TextSecondary
        )
    }
}

// =============================================================================
// Arrived: the main state
// =============================================================================

@Composable
private fun ArrivedContent(
    uiState: ArrivalUiState,
    site: HistoricalSite,
    navController: NavHostController,
    arrivalViewModel: ArrivalViewModel
) {

    val tripProgress = uiState.tripProgress
    // An itinerary stop that's still open (not finished and "Stay Here"-d).
    val activeStop = tripProgress != null && !uiState.stayedAfterResolve
    val isFinalStop = tripProgress?.nextStop == null

    var menuOpen by remember { mutableStateOf(false) }

    TutorialTour(
        id = "arrival",
        steps = listOf(
            TutorialStep(
                target = null,
                title = "You've arrived!",
                body = "Histoury noticed you reached the site. Its full story is now unlocked for you to read."
            ),
            TutorialStep(
                target = "arrival_explore",
                title = "Explore the site",
                body = "Opens its history, timeline and audio guide, plus AR if the site has it."
            )
        )
    )

    // On an itinerary: how to move on to the next stop.
    TutorialTour(
        id = "arrival_trip",
        enabled = activeStop,
        steps = listOf(
            TutorialStep(
                target = "arrival_continue",
                title = "Done here?",
                body = "Marks this stop as finished and gets your next stop ready."
            ),
            TutorialStep(
                target = "arrival_more",
                title = "More options",
                body = "Skip this stop, or pause the whole trip and continue another day."
            )
        )
    )

    // ✓ You've Arrived  ·  ⋯
    Row(verticalAlignment = Alignment.CenterVertically) {

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(SuccessSurface)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Success,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "You've Arrived",
                color = Success,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.weight(1f))

        if (activeStop) {
            Box {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = TextSecondary,
                    modifier = Modifier
                        .tutorialTarget("arrival_more")
                        .clip(CircleShape)
                        .clickable { menuOpen = true }
                        .padding(6.dp)
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Skip this stop") },
                        onClick = {
                            menuOpen = false
                            arrivalViewModel.requestSkip()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Pause trip & save for later") },
                        onClick = {
                            menuOpen = false
                            arrivalViewModel.pauseTrip()
                            navController.navigateSafely(Routes.Home.route) {
                                popUpTo(Routes.Home.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    // [thumbnail]  San Agustin Church
    //              Stop 2 of 5 · My Intramuros Trip
    Row(verticalAlignment = Alignment.CenterVertically) {

        if (site.featuredImage.isNotBlank()) {
            AsyncImage(
                model = site.featuredImage,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceSoft)
            )
            Spacer(Modifier.width(14.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(PrimarySoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = PrimaryPink)
            }
            Spacer(Modifier.width(14.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = site.siteName,
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 27.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (tripProgress != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "Stop ${tripProgress.currentNumber} of ${tripProgress.totalStops}",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            } else if (site.location.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = site.location,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    Spacer(Modifier.height(14.dp))

    Text(
        text = if (tripProgress != null && !uiState.stayedAfterResolve) {
            "Take some time to explore this site before continuing your journey."
        } else {
            "Take some time to explore this site."
        },
        color = TextSecondary,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )

    uiState.routeOptimizedMessage?.let { message ->
        Spacer(Modifier.height(10.dp))
        Text(
            text = "✓ $message",
            color = AccentGold,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { arrivalViewModel.dismissRouteOptimizedMessage() }
        )
    }

    Spacer(Modifier.height(22.dp))

    // --- 1. Explore Site (primary) ---------------------------------------
    Box(Modifier.tutorialTarget("arrival_explore")) {
        PrimaryButton(
            onClick = {
                arrivalViewModel.noteExploring()
                navController.navigateSafely(
                    if (uiState.isPlace) {
                        Routes.PlaceDetails.createRoute(site.documentId)
                    } else {
                        Routes.SiteDetails.createRoute(site.documentId)
                    }
                )
            }
        ) {
            Icon(
                imageVector = Icons.Default.MenuBook,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (uiState.isPlace) "Explore Place" else "Explore Site",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    // AR isn't offered here: the Site Information screen that Explore Site
    // opens already has its own AR button, so a second one here was a
    // duplicate.

    // A failed Continue / Skip (e.g. weak signal): inline, buttons stay.
    uiState.errorMessage?.let { message ->
        Spacer(Modifier.height(14.dp))
        Text(
            text = message,
            color = Danger,
            fontSize = 12.5.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { arrivalViewModel.clearError() }
        )
    }

    Spacer(Modifier.height(24.dp))

    // --- 3. Continue (light) ---------------------------------------------
    when {
        activeStop -> {
            Box(Modifier.tutorialTarget("arrival_continue")) {
                ContinueRow(
                    label = if (isFinalStop) "Finish Itinerary" else "Continue to Next Site",
                    hint = if (isFinalStop) {
                        "Completes this stop and your trip."
                    } else {
                        "Completes this stop and moves your itinerary forward."
                    },
                    isLoading = uiState.isResolvingStop,
                    onClick = { arrivalViewModel.completeCurrentStop() }
                )
            }
        }

        // "Stay Here" after finishing it: a way on, no second Complete.
        tripProgress?.nextStop != null -> {
            val next = tripProgress.nextStop!!
            ContinueRow(
                label = "Start Walking to ${next.name}",
                hint = null,
                isLoading = false,
                onClick = {
                    navController.navigateSafely(Routes.Map.createRoute(next.refId, autoStart = true)) {
                        popUpTo(Routes.Map.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        // Walk-in arrival, not part of an itinerary.
        else -> {
            Text(
                text = "Explore Other Sites",
                color = PrimaryPink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        navController.navigateSafely(Routes.Home.route) {
                            popUpTo(Routes.Home.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                    .padding(vertical = 12.dp)
            )
        }
    }
}

/** The light "Continue →" action: a slim bordered row, never a big filled button. */
@Composable
private fun ContinueRow(
    label: String,
    hint: String?,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, Outline, RoundedCornerShape(14.dp))
                .clickable(enabled = !isLoading, onClick = onClick)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = PrimaryPink,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Text(
                    text = label,
                    color = TextPrimary,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = PrimaryPink,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        if (hint != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = hint,
                color = TextTertiary,
                fontSize = 11.5.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

// =============================================================================
// After Continue: completed → next stop
// =============================================================================

@Composable
private fun StopResolvedContent(
    uiState: ArrivalUiState,
    site: HistoricalSite,
    navController: NavHostController,
    onStayLonger: () -> Unit
) {

    val tripProgress = uiState.tripProgress ?: return
    val next = tripProgress.nextStop
    val skipped = uiState.lastStopOutcome == StopStatus.SKIPPED

    // ✓ San Agustin Church completed
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (skipped) SurfaceSoft else SuccessSurface)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = if (skipped) TextSecondary else Success,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (skipped) "${site.siteName} skipped" else "${site.siteName} completed",
            color = if (skipped) TextSecondary else Success,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    Spacer(Modifier.height(20.dp))

    if (next != null) {

        Text(
            text = "NEXT STOP",
            color = TextTertiary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = next.name,
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 27.sp
        )

        walkEstimate(tripProgress.currentStop, next)?.let { estimate ->
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.DirectionsWalk,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(text = estimate, color = TextSecondary, fontSize = 13.5.sp)
            }
        }

        Spacer(Modifier.height(22.dp))

        PrimaryButton(
            onClick = {
                // Replace this stop's Map + Arrival with the next stop's Map,
                // straight into navigation.
                navController.navigateSafely(Routes.Map.createRoute(next.refId, autoStart = true)) {
                    popUpTo(Routes.Map.route) { inclusive = true }
                    launchSingleTop = true
                }
            }
        ) {
            Icon(
                imageVector = Icons.Default.DirectionsWalk,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(text = "Start Walking", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = "Stay here a little longer",
            color = TextSecondary,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onStayLonger)
                .padding(vertical = 12.dp)
        )

    } else {

        Text(
            text = "That was the last stop!",
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "${tripProgress.itineraryName} — ${formatElapsedTrip(tripProgress.startedAtMillis)} well spent.",
            color = TextSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )

        Spacer(Modifier.height(22.dp))

        PrimaryButton(
            text = "Finish Itinerary",
            onClick = {
                navController.navigateSafely(Routes.TripComplete.createRoute(tripProgress.itineraryDocumentId)) {
                    popUpTo(Routes.Home.route)
                    launchSingleTop = true
                }
            }
        )
    }
}

// =============================================================================
// Loading / error
// =============================================================================

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = PrimaryPink)
    }
}

@Composable
private fun LoadErrorContent(
    message: String,
    belongsToActiveTrip: Boolean,
    navController: NavHostController
) {
    Text(
        text = message,
        color = TextSecondary,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    )
    PrimaryButton(
        text = if (belongsToActiveTrip) "Back to Itinerary" else "Back to Home",
        onClick = {
            navController.navigateToTab(
                if (belongsToActiveTrip) Routes.Itinerary.route else Routes.Home.route
            )
        }
    )
}

// =============================================================================
// Helpers
// =============================================================================

/** "8 min walk · 650 m" from one stop to the next, or null without coordinates. */
private fun walkEstimate(from: ItineraryStop, to: ItineraryStop): String? {
    if ((from.latitude == 0.0 && from.longitude == 0.0) || (to.latitude == 0.0 && to.longitude == 0.0)) {
        return null
    }
    val result = FloatArray(1)
    Location.distanceBetween(from.latitude, from.longitude, to.latitude, to.longitude, result)
    // Straight-line distance, stretched a little for streets.
    val meters = result[0] * 1.25
    val minutes = ceil(meters / WALKING_METERS_PER_MINUTE).toInt().coerceAtLeast(1)
    val distance = if (meters < 1000) {
        "${(meters / 10).toInt() * 10} m"
    } else {
        String.format(java.util.Locale.US, "%.1f km", meters / 1000)
    }
    return "$minutes min walk · $distance"
}

private fun formatElapsedTrip(startedAtMillis: Long): String {

    val minutes = ((System.currentTimeMillis() - startedAtMillis) / 60_000L)
        .coerceAtLeast(1L)
        .toInt()

    val hours = minutes / 60
    val remainder = minutes % 60

    return when {
        hours == 0 -> "$minutes min"
        remainder == 0 -> "${hours}h"
        else -> "${hours}h ${remainder}m"
    }
}
