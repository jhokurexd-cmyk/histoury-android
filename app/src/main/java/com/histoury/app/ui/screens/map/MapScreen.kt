@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.histoury.app.ui.screens.map

import com.histoury.app.ui.tutorial.TutorialStep
import com.histoury.app.ui.tutorial.TutorialTour
import com.histoury.app.ui.tutorial.tutorialTarget
import com.histoury.app.navigation.safePopBackStack
import com.histoury.app.navigation.navigateSafely
import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.histoury.app.ui.components.FavoriteButtonStyle
import com.histoury.app.ui.components.OverlayControls
import com.histoury.app.ui.components.HeroIconButton
import com.histoury.app.ui.components.HeroOverlayInset
import com.histoury.app.ui.components.RatingBadge
import com.histoury.app.ui.components.RatingBadgeStyle
import com.histoury.app.ui.components.SiteFavoriteButton
import com.histoury.app.theme.DangerSurface
import com.histoury.app.theme.SuccessSurface
import com.histoury.app.theme.Success
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.histoury.app.ui.components.rememberThemedMapColorScheme
import com.histoury.app.ui.components.rememberThemedMapProperties
import com.histoury.app.ui.components.TurnOnLocationDialog
import com.histoury.app.ui.components.rememberDeviceLocationEnabled
import com.histoury.app.ui.components.rememberTurnOnLocation
import com.histoury.app.data.repository.TravelMode
import com.histoury.app.data.repository.ItineraryRepository
import com.histoury.app.data.itinerary.ActiveItineraryTrip
import com.histoury.app.data.model.StopStatus
import com.histoury.app.data.viewmodel.MapUiState
import com.histoury.app.data.viewmodel.MapViewModel
import com.histoury.app.MainActivity
import com.histoury.app.navigation.Routes
import com.histoury.app.util.NavigationPipState
import com.histoury.app.theme.SurfaceSoft
import com.histoury.app.theme.Danger
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Primary
import com.histoury.app.theme.PrimarySoft
import com.histoury.app.theme.RatingStar
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary
import com.histoury.app.ui.components.PrimaryButton
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.exp

// Roughly matches (and stays a little under) the live location update
// cadence set in MapViewModel's LocationRequest (2-3s), so the camera is
// still gliding toward the previous fix's target when the next one
// arrives, instead of finishing quickly and sitting still in between.
private const val PREVIEW_FIT_ANIMATION_MS = 700

/** Per-frame easing factor for a time constant of [tauSeconds]. */
private fun ease(dtSeconds: Float, tauSeconds: Float): Float =
    1f - exp(-dtSeconds / tauSeconds)

@Composable
fun MapScreen(
    siteId: String,
    navController: NavHostController,
    autoStartNavigation: Boolean = false,
    mapViewModel: MapViewModel = viewModel()
) {

    val uiState by mapViewModel.uiState.collectAsState()

    val context = LocalContext.current

    // Set when "Navigate" was tapped without location access, so navigation
    // starts by itself as soon as the visitor grants it.
    var startNavigationWhenPermitted by remember { mutableStateOf(false) }

    // The phone's Location toggle, separate from the app's permission. With
    // it off no fix ever arrives, so there is no route, distance or ETA.
    val locationServicesOn = rememberDeviceLocationEnabled()

    val turnOnLocation = rememberTurnOnLocation()

    var showLocationOffDialog by remember { mutableStateOf(false) }

    // Offered once by itself when the screen opens with location off; after
    // that only when the visitor taps Navigate (or it goes off mid-route).
    var locationPromptShown by rememberSaveable { mutableStateOf(false) }

    // Set when "Navigate" was tapped with location off, so navigation
    // starts by itself once it's turned on.
    var startNavigationWhenLocationOn by remember { mutableStateOf(false) }

    // Whether location has been seen off on this screen: when it comes back
    // on, the screen fetches a fix itself instead of waiting for a reload.
    var locationWasOff by remember { mutableStateOf(false) }

    // autoStart runs once per screen, and survives rotation / PiP resizes so
    // ending navigation doesn't immediately restart it.
    var autoStartHandled by rememberSaveable { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        mapViewModel.onLocationPermissionResult(isGranted)
        if (startNavigationWhenPermitted) {
            startNavigationWhenPermitted = false
            if (isGranted && !com.histoury.app.util.DeviceLocation.isEnabled(context)) {
                // Allowed, but the phone's location is off: start once it's on.
                startNavigationWhenLocationOn = true
                showLocationOffDialog = true
            } else if (isGranted) {
                mapViewModel.startNavigation()
            } else {
                android.widget.Toast.makeText(
                    context,
                    "Turn-by-turn navigation needs location access. You can allow it in Settings.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    LaunchedEffect(siteId) {

        mapViewModel.loadSiteData(siteId)

        val alreadyGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (alreadyGranted) {
            mapViewModel.onLocationPermissionResult(true)
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    LaunchedEffect(uiState.hasLocationPermission, locationServicesOn) {
        if (!uiState.hasLocationPermission) return@LaunchedEffect

        if (locationServicesOn) {
            showLocationOffDialog = false
            if (locationWasOff) {
                // Location just came back on: get a fix now so the route,
                // distance and ETA fill in without reopening the screen.
                locationWasOff = false
                mapViewModel.fetchDeviceLocation()
            }
            if (startNavigationWhenLocationOn) {
                startNavigationWhenLocationOn = false
                mapViewModel.startNavigation()
            }
        } else {
            locationWasOff = true
        }

        if (!locationServicesOn && (!locationPromptShown || uiState.isNavigating)) {
            locationPromptShown = true
            showLocationOffDialog = true
        }
    }

    LaunchedEffect(autoStartNavigation, uiState.geofence, uiState.hasLocationPermission, locationServicesOn) {
        if (
            autoStartNavigation &&
            !autoStartHandled &&
            uiState.geofence != null &&
            uiState.hasLocationPermission &&
            locationServicesOn &&
            !uiState.isNavigating
        ) {
            autoStartHandled = true
            mapViewModel.startNavigation()
        }
    }

    // Surfaces "Stop X of Y" while navigating as part of an active
    // itinerary, and backs "Skip This Stop" below — skipping is meant to be
    // usable before ever reaching the site, not just from the Arrival
    // screen. rehydrate() is a no-op once a session is already live, and
    // recovers one from Firestore after an app kill — otherwise navigating
    // straight back into a killed-and-reopened trip would show no trip
    // context at all until the next arrival.
    val itineraryRepository = remember { ItineraryRepository() }

    val coroutineScope = rememberCoroutineScope()

    var tripProgress by remember { mutableStateOf<ActiveItineraryTrip.Progress?>(null) }

    var showSkipConfirmation by remember { mutableStateOf(false) }

    var isSkipping by remember { mutableStateOf(false) }

    LaunchedEffect(siteId) {
        ActiveItineraryTrip.rehydrate(itineraryRepository)
        tripProgress = ActiveItineraryTrip.progressFor(siteId)
    }

    fun confirmSkipStop() {

        val progress = tripProgress ?: return
        if (isSkipping) return

        isSkipping = true

        coroutineScope.launch {

            val saved = itineraryRepository.markStopSkipped(
                progress.itineraryDocumentId,
                progress.currentStop.refId
            )

            isSkipping = false
            showSkipConfirmation = false

            if (saved == null) {
                // Used to fail silently (typically offline): the dialog just
                // closed and nothing happened.
                android.widget.Toast.makeText(
                    context,
                    "Couldn't skip this stop. Check your connection and try again.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            val nextStop = ActiveItineraryTrip.advanceFrom(progress.currentStop.refId, StopStatus.SKIPPED)

            if (nextStop != null) {

                navController.navigateSafely(Routes.Map.createRoute(nextStop.refId, autoStart = true)) {
                    popUpTo(Routes.Map.route) { inclusive = true }
                    launchSingleTop = true
                }

            } else {

                navController.navigateSafely(
                    Routes.TripComplete.createRoute(progress.itineraryDocumentId)
                ) {
                    popUpTo(Routes.Home.route)
                    launchSingleTop = true
                }
            }
        }
    }

    val geofence = uiState.geofence

    val siteLatLng = remember(geofence) {
        if (geofence != null) {
            LatLng(geofence.latitude, geofence.longitude)
        } else {
            null
        }
    }

    val cameraPositionState = rememberCameraPositionState()

    // ----- immersive navigation + floating mini map -----

    // Navigation gets its own camera. The route-preview map and the
    // navigation map are different GoogleMap instances, and one
    // CameraPositionState can't be attached to both while they swap.
    val navCameraPositionState = rememberCameraPositionState()

    val activity = remember(context) { context.findActivity() as? MainActivity }
    val canUsePip = remember(activity) { activity?.supportsNavigationPip() == true }

    val isInPip by NavigationPipState.isInPip.collectAsState()

    // Waze behaviour: the camera follows the visitor until they pan the map
    // themselves; then a "Re-center" button brings it back.
    var isFollowing by remember { mutableStateOf(true) }
    var isOverview by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isNavigating) {
        if (uiState.isNavigating) {
            isFollowing = true
            isOverview = false
            // Start from what the preview showed, then glide into follow mode.
            navCameraPositionState.position = cameraPositionState.position
        }
    }

    // Tells MainActivity when leaving the app should shrink navigation into
    // the floating window rather than just backgrounding it.
    DisposableEffect(uiState.isNavigating) {
        NavigationPipState.isNavigating.value = uiState.isNavigating
        onDispose { NavigationPipState.isNavigating.value = false }
    }

    // The screen mustn't sleep mid-route.
    val view = LocalView.current
    DisposableEffect(uiState.isNavigating) {
        view.keepScreenOn = uiState.isNavigating
        onDispose { view.keepScreenOn = false }
    }

    // Closing the floating window ends navigation (see NavigationPipState).
    LaunchedEffect(Unit) {
        // The End button on the ongoing navigation notification. Closing the
        // floating mini map no longer ends navigation — the foreground
        // service keeps it running, like Google Maps.
        NavigationPipState.endNavigationRequests.collect { mapViewModel.stopNavigation() }
    }

    // Only a pan/zoom by the visitor's own finger stops follow mode. Read
    // through snapshotFlow rather than as a LaunchedEffect key: the follow
    // loop below moves the camera every frame, and keying on isMoving would
    // recompose the whole screen that often (and could miss a gesture that
    // starts while the camera is already moving).
    val isNavigatingNow by rememberUpdatedState(uiState.isNavigating)
    LaunchedEffect(navCameraPositionState) {
        snapshotFlow {
            navCameraPositionState.isMoving to navCameraPositionState.cameraMoveStartedReason
        }.collect { (moving, reason) ->
            if (isNavigatingNow && moving && reason == CameraMoveStartedReason.GESTURE) {
                isFollowing = false
                isOverview = false
            }
        }
    }

    // "Show whole route": frame everything still ahead, plus the visitor.
    LaunchedEffect(isOverview) {
        if (!isOverview) return@LaunchedEffect
        // With no route, frame the visitor and the destination instead.
        val points = uiState.remainingRoutePoints.ifEmpty { uiState.routePoints }.ifEmpty {
            listOfNotNull(uiState.geofence?.let { LatLng(it.latitude, it.longitude) })
        }
        if (points.isEmpty()) return@LaunchedEffect
        val bounds = LatLngBounds.Builder().apply {
            points.forEach { include(it) }
            val lat = uiState.userLatitude
            val lng = uiState.userLongitude
            if (lat != null && lng != null) include(LatLng(lat, lng))
        }.build()
        try {
            navCameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 160), 900)
        } catch (e: Exception) {
            // Map not laid out yet.
        }
    }

    // Route preview camera. It used to snap to the site at zoom 17 and then
    // zoom back out to the route a moment later, and re-fit on every route
    // refresh — the map "jumped" under the visitor's finger. Now: one
    // smooth glide to the site, one smooth fit to the route when it
    // arrives, and never again for the same destination once the visitor
    // has moved the map themselves.
    var previewFittedFor by rememberSaveable { mutableStateOf<String?>(null) }
    var previewTouched by remember { mutableStateOf(false) }

    LaunchedEffect(cameraPositionState) {
        snapshotFlow {
            cameraPositionState.isMoving to cameraPositionState.cameraMoveStartedReason
        }.collect { (moving, reason) ->
            if (moving && reason == CameraMoveStartedReason.GESTURE) previewTouched = true
        }
    }

    LaunchedEffect(siteLatLng) {
        if (siteLatLng != null && !uiState.isNavigating && uiState.routePoints.size < 2 && previewFittedFor == null) {
            // First frame: place it without animation (there's nothing to glide from).
            cameraPositionState.position = CameraPosition.fromLatLngZoom(siteLatLng, 16.5f)
        }
    }

    LaunchedEffect(uiState.routePoints) {

        val routePoints = uiState.routePoints
        // Includes the travel mode: switching Walking -> Driving gives a
        // different route, which should be framed again.
        val key = siteLatLng?.let { "${it.latitude},${it.longitude},${uiState.travelMode}" }

        if (routePoints.size >= 2 && !uiState.isNavigating && !previewTouched && previewFittedFor != key) {

            val boundsBuilder = LatLngBounds.Builder()

            routePoints.forEach { point -> boundsBuilder.include(point) }

            try {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120),
                    PREVIEW_FIT_ANIMATION_MS
                )
                previewFittedFor = key
            } catch (e: Exception) {
                // Map not laid out yet, or the visitor grabbed the map mid-glide.
            }
        }
    }

    // While navigating, the camera follows the visitor.
    //
    // It used to start a new 1.2 s camera animation on every GPS fix AND
    // every bearing update. Each new one cancelled the one in flight, so the
    // camera stopped dead and lurched off again several times a second —
    // the "jumping" map. Now one loop runs per frame for as long as follow
    // mode is on and eases the camera toward the latest target:
    //  - position glides linearly over NAV_GLIDE_MS, exactly like the
    //    arrow marker does, so the arrow stays pinned in place on screen;
    //  - bearing, zoom and tilt ease exponentially, so heading changes turn
    //    the map smoothly instead of in steps.
    val followLatLng = remember(uiState.userLatitude, uiState.userLongitude) {
        val lat = uiState.userLatitude
        val lng = uiState.userLongitude
        if (lat != null && lng != null) LatLng(lat, lng) else null
    }
    val followTargetState = rememberUpdatedState(followLatLng)
    val followBearingState = rememberUpdatedState(uiState.userBearing)
    // Closer in for walking, further out at vehicle speeds so the road ahead
    // stays in view; flatter and further out in the small floating window.
    val followZoomState = rememberUpdatedState(
        when {
            isInPip -> 16.25f
            uiState.travelMode == TravelMode.WALKING -> 18.5f
            uiState.travelMode == TravelMode.MOTORCYCLE -> 17.5f
            else -> 17f
        }
    )
    val followTiltState = rememberUpdatedState(if (isInPip) 30f else 55f)

    LaunchedEffect(uiState.isNavigating, isFollowing, isInPip) {

        if (!uiState.isNavigating || !(isFollowing || isInPip)) return@LaunchedEffect

        val startPosition = navCameraPositionState.position
        var shown = startPosition.target
        var glideFrom = shown
        var glideTo = shown
        var glideStartNanos = 0L
        var bearing = startPosition.bearing
        var zoom = startPosition.zoom
        var tilt = startPosition.tilt
        var lastFrameNanos = 0L
        var leftoverSwipe = navCameraPositionState.isMoving &&
            navCameraPositionState.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE

        while (isActive) {
            withFrameNanos { now ->

                val dt = if (lastFrameNanos == 0L) 0.016f
                    else ((now - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
                lastFrameNanos = now

                // A finger on the map always wins: stop following at once
                // rather than moving the camera out from under it.
                // (A swipe still gliding when Re-center was tapped doesn't
                // count: it's ignored until this loop's own moves take over.)
                val gestureNow = navCameraPositionState.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE
                if (!gestureNow) leftoverSwipe = false
                if (!leftoverSwipe && gestureNow && navCameraPositionState.isMoving) {
                    isFollowing = false
                    isOverview = false
                    return@withFrameNanos
                }

                val target = followTargetState.value ?: return@withFrameNanos

                // A new fix: glide from wherever the camera is now.
                if (target != glideTo) {
                    glideFrom = shown
                    glideTo = target
                    glideStartNanos = now
                }

                val t = if (glideStartNanos == 0L) 1f
                    else ((now - glideStartNanos) / 1_000_000f / NAV_GLIDE_MS).coerceIn(0f, 1f)

                val next = LatLng(
                    glideFrom.latitude + (glideTo.latitude - glideFrom.latitude) * t,
                    glideFrom.longitude + (glideTo.longitude - glideFrom.longitude) * t
                )

                // Unknown heading (first fix): keep what the camera shows.
                val targetBearing = followBearingState.value ?: bearing
                val bearingDelta = ((targetBearing - bearing + 540f) % 360f) - 180f
                val nextBearing = (bearing + bearingDelta * ease(dt, 0.30f) + 360f) % 360f
                val nextZoom = zoom + (followZoomState.value - zoom) * ease(dt, 0.35f)
                val nextTilt = tilt + (followTiltState.value - tilt) * ease(dt, 0.35f)

                val changed = abs(next.latitude - shown.latitude) > 1e-8 ||
                    abs(next.longitude - shown.longitude) > 1e-8 ||
                    abs(nextBearing - bearing) > 0.02f ||
                    abs(nextZoom - zoom) > 0.0005f ||
                    abs(nextTilt - tilt) > 0.02f

                shown = next
                bearing = nextBearing
                zoom = nextZoom
                tilt = nextTilt

                if (changed) {
                    try {
                        navCameraPositionState.move(
                            CameraUpdateFactory.newCameraPosition(
                                CameraPosition.Builder()
                                    .target(next)
                                    .zoom(nextZoom)
                                    .bearing(nextBearing)
                                    .tilt(nextTilt)
                                    .build()
                            )
                        )
                    } catch (e: Exception) {
                        // Map not attached yet (e.g. mid-swap into PiP).
                    }
                }
            }
        }
    }

    // Reaching the destination while actively navigating in-app takes the
    // person straight to the arrival celebration screen.
    LaunchedEffect(uiState.hasArrived) {

        val arrivedSite = uiState.site
        val arrivedDestinationId = arrivedSite?.documentId

        if (uiState.hasArrived && !arrivedDestinationId.isNullOrBlank()) {
            // Arriving while floating: bring the app back full screen for
            // the arrival celebration instead of showing it in a tiny window.
            if (NavigationPipState.isInPip.value) activity?.expandFromPip()

            // Consumed before navigating, so coming Back to this Map doesn't
            // fire the arrival again (the Map ↔ Arrival loop).
            mapViewModel.onArrivalHandled()

            navController.navigateSafely(Routes.Arrival.createRoute(arrivedDestinationId)) {
                // Keep the Itinerary destination alive so Arrival can send
                // the visitor to the next stop. A standalone map trip keeps
                // the original behavior of returning to Home.
                if (ActiveItineraryTrip.progressFor(arrivedSite?.documentId.orEmpty()) == null) {
                    popUpTo(Routes.Home.route)
                }
                launchSingleTop = true
            }
        }
    }

    val sheetScaffoldState = rememberBottomSheetScaffoldState()

    if (uiState.isNavigating) {

        NavigationModeContent(
            uiState = uiState,
            cameraPositionState = navCameraPositionState,
            isInPip = isInPip,
            isFollowing = isFollowing || isInPip,
            isOverview = isOverview,
            tripProgress = tripProgress,
            canUsePip = canUsePip,
            onRecenter = {
                isOverview = false
                isFollowing = true
            },
            onToggleOverview = {
                if (isOverview) {
                    isOverview = false
                    isFollowing = true
                } else {
                    isOverview = true
                    isFollowing = false
                }
            },
            onToggleMute = { mapViewModel.toggleVoiceMuted() },
            onEnterPip = { activity?.enterNavigationPip() },
            onEnd = { mapViewModel.stopNavigation() },
            onSkipStop = { showSkipConfirmation = true }
        )

    } else BottomSheetScaffold(
        scaffoldState = sheetScaffoldState,
        sheetContainerColor = CardSurface,
        sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        // Lower than before so the route preview gets most of the screen;
        // drag the sheet up for the site's details.
        sheetPeekHeight = 300.dp,
        sheetContent = {
            MapBottomSheetContent(
                uiState = uiState,
                onStartNavigationClick = {
                    // Without location, "Navigate" used to open a navigation
                    // screen that could never move. Ask first instead.
                    if (uiState.hasLocationPermission && !locationServicesOn) {
                        // Permission alone isn't enough: with the phone's
                        // location off, navigation would never move.
                        startNavigationWhenLocationOn = true
                        showLocationOffDialog = true
                    } else if (uiState.hasLocationPermission) {
                        mapViewModel.startNavigation()
                    } else {
                        startNavigationWhenPermitted = true
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                },
                onTravelModeChange = { mode ->
                    mapViewModel.setTravelMode(mode)
                },
                onRetryRoute = { mapViewModel.retryRoute() },
                onOpenInGoogleMaps = { openInGoogleMaps(context, uiState) },
                locationServicesOn = locationServicesOn,
                onTurnOnLocation = turnOnLocation,
                // Places (cafés, shops) aren't historical sites and can't be
                // favorited; only a real site shows the heart.
                canFavorite = uiState.site != null && !uiState.isPlaceDestination
            )
        }
    ) { padding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                // Keeps the map's own My Location button and logo out from
                // under the status bar / cutout on this edge-to-edge screen.
                contentPadding = WindowInsets.safeDrawing
                    .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                    .asPaddingValues(),
                properties = rememberThemedMapProperties(
                    isMyLocationEnabled = uiState.hasLocationPermission
                ),
                mapColorScheme = rememberThemedMapColorScheme(),
                uiSettings = MapUiSettings(
                    myLocationButtonEnabled = uiState.hasLocationPermission,
                    zoomControlsEnabled = false
                )
            ) {

                // Only a real route is drawn. When none could be found the
                // points are empty (see MapViewModel.applyStraightLineFallback),
                // so a straight line through buildings can never appear here.
                if (uiState.routePoints.size >= 2 && !uiState.isRouteEstimated) {
                    Polyline(
                        points = uiState.routePoints,
                        color = Primary,
                        width = 10f
                    )
                }

                if (siteLatLng != null) {
                    // Remembered: a new MarkerState on every recomposition
                    // made the pin flicker as it was removed and re-added.
                    val siteMarkerState = remember(siteLatLng) { MarkerState(position = siteLatLng) }
                    Marker(
                        state = siteMarkerState,
                        title = uiState.site?.siteName ?: "Historical Site",
                        snippet = uiState.site?.location ?: ""
                    )
                }

                // The verified visitor entrance the route goes to, when it
                // isn't the site's own pin (e.g. Fort Santiago's gate).
                val entranceLat = uiState.routeTargetLatitude
                val entranceLng = uiState.routeTargetLongitude
                if (entranceLat != null && entranceLng != null) {
                    val entranceLatLng = LatLng(entranceLat, entranceLng)
                    val entranceMarkerState = remember(entranceLatLng) { MarkerState(position = entranceLatLng) }
                    Marker(
                        state = entranceMarkerState,
                        title = uiState.entranceName ?: "Visitor entrance",
                        snippet = "Verified visitor entrance",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                    )
                }
            }

            // The shared hero button: same size, shadow and inset rules as
            // the Back button on Site Information and Place Details.
            HeroIconButton(
                icon = Icons.Default.ArrowBack,
                contentDescription = "Back",
                onClick = { navController.safePopBackStack() },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                    .padding(HeroOverlayInset)
            )

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Primary)
                }
            }
        }
    }

    // First directions screen: what the panel shows and how to start.
    TutorialTour(
        id = "directions",
        enabled = !uiState.isNavigating && !uiState.isLoading && !showLocationOffDialog,
        steps = listOf(
            TutorialStep(
                target = null,
                title = "Directions",
                body = "The map shows the way from where you are to the site. Drag the panel at the bottom up to see more about the site."
            ),
            TutorialStep(
                target = "directions_mode",
                title = "Walk, drive or ride",
                body = "Pick how you're getting there. The route, distance and time change to match."
            ),
            TutorialStep(
                target = "directions_stats",
                title = "Distance and arrival",
                body = "How far the site is and about when you'll get there. This needs your phone's location turned on."
            ),
            TutorialStep(
                target = "directions_navigate",
                title = "Navigate",
                body = "Starts step-by-step guidance with voice. When you reach the site, Histoury tells you and opens its story."
            )
        )
    )

    // Live navigation: the controls on the guidance screen.
    TutorialTour(
        id = "navigation",
        enabled = uiState.isNavigating,
        steps = listOf(
            TutorialStep(
                target = "nav_banner",
                title = "Your next turn",
                body = "The next direction and how far away it is. Instructions are also spoken out loud."
            ),
            TutorialStep(
                target = "nav_mute",
                title = "Voice on or off",
                body = "Tap to mute or unmute the spoken directions."
            ),
            TutorialStep(
                target = "nav_overview",
                title = "See the whole route",
                body = "Zooms out to show your entire way there. Tap again to go back to following you."
            ),
            TutorialStep(
                target = "nav_pip",
                title = "Use other apps",
                body = "Shrinks the map into a small floating window so you can use your phone while it keeps guiding you."
            ),
            TutorialStep(
                target = "nav_status",
                title = "Time left",
                body = "Your arrival time, minutes left and distance, updating as you walk."
            ),
            TutorialStep(
                target = "nav_end",
                title = "End navigation",
                body = "Stops the guidance whenever you want."
            )
        )
    )

    if (showLocationOffDialog) {
        TurnOnLocationDialog(
            message = if (uiState.isNavigating) {
                "Location was turned off, so navigation can't follow you. Turn it back on to keep going."
            } else {
                "Your phone's location is off. Turn it on to get directions, live distance and arrival time."
            },
            onDismiss = { showLocationOffDialog = false }
        )
    }

    if (showSkipConfirmation) {

        AlertDialog(
            onDismissRequest = { if (!isSkipping) showSkipConfirmation = false },
            title = { Text("Skip this stop?") },
            text = { Text("You won't be able to come back to it during this trip.") },
            confirmButton = {
                TextButton(onClick = { confirmSkipStop() }, enabled = !isSkipping) {
                    Text("Skip", color = Danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSkipConfirmation = false },
                    enabled = !isSkipping
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun MapBottomSheetContent(
    uiState: MapUiState,
    onStartNavigationClick: () -> Unit,
    onTravelModeChange: (TravelMode) -> Unit,
    onRetryRoute: () -> Unit,
    onOpenInGoogleMaps: () -> Unit,
    canFavorite: Boolean,
    locationServicesOn: Boolean = true,
    onTurnOnLocation: () -> Unit = {}
) {

    val site = uiState.site

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
    ) {

        // The BottomSheetScaffold already renders its own drag handle above
        // this content — no need for a second one here.

        Spacer(Modifier.height(6.dp))

        Row(verticalAlignment = Alignment.Top) {

            Column(modifier = Modifier.weight(1f)) {

                Text(
                    text = site?.siteName ?: "Historical Site",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {

                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(15.dp)
                    )

                    Spacer(Modifier.width(4.dp))

                    Text(
                        text = site?.location ?: "",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Heart and rating as one group: same 34dp height, centred on
            // each other, a fixed gap, and nudged up so their centre lines up
            // with the title's first line. The title column takes the rest
            // of the width and wraps, so a long name never pushes them off.
            val showFavorite = canFavorite && site != null
            val showRating = site != null && site.averageRating > 0.0

            if (showFavorite || showRating) {

                Spacer(Modifier.width(OverlayControls.ControlGap))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.offset(y = -(OverlayControls.CardTouchSlack + 2.dp))
                ) {

                    if (showFavorite) {
                        SiteFavoriteButton(
                            siteDocumentId = site!!.documentId,
                            siteName = site.siteName,
                            style = FavoriteButtonStyle.TONAL
                        )
                    }

                    if (showFavorite && showRating) {
                        // The heart's tap area already adds its slack on
                        // this side; together they make the standard gap.
                        Spacer(Modifier.width(OverlayControls.ControlGap - OverlayControls.CardTouchSlack))
                    }

                    if (showRating) {
                        RatingBadge(
                            rating = site!!.averageRating,
                            style = RatingBadgeStyle.TONAL
                        )
                    }
                }
            }
        }

        if (!site?.entranceFee.isNullOrBlank()) {

            Spacer(Modifier.height(10.dp))

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
                        text = "Entrance Fee: ${site?.entranceFee}",
                        color = Primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (uiState.tagNames.isNotEmpty()) {

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                uiState.tagNames.forEach { tag ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = PrimarySoft
                    ) {
                        Text(
                            text = tag,
                            color = Primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        if (!site?.description.isNullOrBlank()) {

            Spacer(Modifier.height(12.dp))

            Text(
                text = site?.description ?: "",
                color = TextSecondary,
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(16.dp))

        Box(Modifier.tutorialTarget("directions_mode")) {
            TravelModeSelector(
                selectedMode = uiState.travelMode,
                onModeSelected = onTravelModeChange
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .tutorialTarget("directions_stats")
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(PrimarySoft)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            Column {
                Text(
                    text = "Distance",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Text(
                    text = formatDistance(uiState),
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = travelModeIcon(uiState.travelMode),
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Est. arrival",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
                Text(
                    text = formatEta(uiState),
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        }

        val isNearby = (uiState.distanceMeters ?: Float.MAX_VALUE) < NEARBY_DISTANCE_METERS

        if (uiState.isRouteLoading) {

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    color = Primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Calculating route...",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        } else if (isNearby) {

            // A few steps away: a routing failure (common at this range —
            // the start and end snap to the same road) is noise, not news.
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SuccessSurface)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = Success,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "You're almost there — ${site?.siteName ?: "the site"} should be in sight.",
                    color = TextPrimary,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp
                )
            }
        } else if (uiState.isRouteEstimated && uiState.errorMessage != null) {

            // No real route: say so plainly and offer two ways forward.
            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DangerSurface)
                    .padding(start = 14.dp, top = 12.dp, end = 6.dp, bottom = 4.dp)
            ) {
                Text(
                    text = uiState.errorMessage,
                    color = TextPrimary,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Row {
                    TextButton(onClick = onRetryRoute) {
                        Text("Try again", color = Primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    TextButton(onClick = onOpenInGoogleMaps) {
                        Text("Open in Google Maps", color = Primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        } else if (uiState.errorMessage != null) {

            Spacer(Modifier.height(12.dp))

            Text(
                text = uiState.errorMessage,
                color = Danger,
                fontSize = 13.sp
            )
        } else if (!uiState.hasLocationPermission) {

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Enable location access to see live distance and travel time.",
                color = TextSecondary,
                fontSize = 13.sp
            )
        } else if (!locationServicesOn) {

            // Permission granted but the phone's Location is off: say so,
            // with the fix one tap away.
            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DangerSurface)
                    .padding(start = 14.dp, top = 12.dp, end = 6.dp, bottom = 4.dp)
            ) {
                Text(
                    text = "Your phone's location is off, so there's no route, distance or arrival time yet.",
                    color = TextPrimary,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
                TextButton(onClick = onTurnOnLocation) {
                    Text("Turn On Location", color = Primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        if (!uiState.isRouteLoading && uiState.routePoints.size >= 2 && !uiState.isRouteEstimated) {
            RouteDestinationNotes(
                entranceName = uiState.entranceName,
                notice = uiState.routeNotice,
                attribution = uiState.routeAttribution
            )
        }

        Spacer(Modifier.height(20.dp))

        Box(Modifier.tutorialTarget("directions_navigate")) {
            PrimaryButton(
                text = if (uiState.isRouteLoading) "Calculating Route" else "Navigate",
                onClick = onStartNavigationClick,
                enabled = uiState.geofence != null,
                isLoading = uiState.isRouteLoading
            )
        }
    }
}

@Composable
private fun TravelModeSelector(
    selectedMode: TravelMode,
    onModeSelected: (TravelMode) -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceSoft)
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {

        TravelMode.entries.forEach { mode ->

            val isSelected = mode == selectedMode

            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) CardSurface else Color.Transparent)
                    .clickable { onModeSelected(mode) }
                    .semantics {
                        selected = isSelected
                        role = Role.RadioButton
                    }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Icon(
                    imageVector = travelModeIcon(mode),
                    contentDescription = travelModeLabel(mode),
                    tint = if (isSelected) Primary else TextSecondary,
                    modifier = Modifier.size(18.dp)
                )

                Spacer(Modifier.width(6.dp))

                Text(
                    text = travelModeLabel(mode),
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) Primary else TextSecondary
                )
            }
        }
    }
}

/**
 * Where the route ends and who it comes from: the verified entrance's name,
 * an honest note when the exact entrance isn't verified yet, and the credit
 * line OpenStreetMap-based routes must carry.
 */
@Composable
private fun RouteDestinationNotes(entranceName: String?, notice: String?, attribution: String?) {
    if (entranceName == null && notice == null && attribution == null) return

    Spacer(Modifier.height(10.dp))

    if (entranceName != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = Success, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Route ends at: $entranceName",
                color = TextPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    } else if (notice != null) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Info, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(text = notice, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }

    if (attribution != null) {
        Spacer(Modifier.height(4.dp))
        Text(text = attribution, color = TextSecondary, fontSize = 10.sp, lineHeight = 13.sp)
    }
}

private fun travelModeIcon(mode: TravelMode) = when (mode) {
    TravelMode.WALKING -> Icons.Default.DirectionsWalk
    TravelMode.DRIVING -> Icons.Default.DirectionsCar
    TravelMode.MOTORCYCLE -> Icons.Default.TwoWheeler
}

private fun travelModeLabel(mode: TravelMode) = when (mode) {
    TravelMode.WALKING -> "Walk"
    TravelMode.DRIVING -> "Drive"
    TravelMode.MOTORCYCLE -> "Motorcycle"
}

private fun formatDistance(uiState: MapUiState): String {

    val distanceMeters = uiState.distanceMeters ?: return "--"

    return if (distanceMeters < 1000f) {
        "${distanceMeters.toInt()} m"
    } else {
        "%.1f km".format(distanceMeters / 1000f)
    }
}

private fun formatEta(uiState: MapUiState): String {

    val etaMinutes = uiState.etaMinutes ?: return "--"

    return when {
        etaMinutes < 1 -> "< 1 min"
        etaMinutes < 60 -> "$etaMinutes min"
        etaMinutes % 60 == 0 -> "${etaMinutes / 60} hr"
        else -> "${etaMinutes / 60} hr ${etaMinutes % 60} min"
    }
}

/** Closer than this, the sheet says "almost there" instead of a route error. */
private const val NEARBY_DISTANCE_METERS = 60f

/**
 * The fallback when Histoury can't find a route itself: hand the trip to
 * Google Maps with the same destination and travel mode. Falls back to a
 * plain geo: link if Google Maps isn't installed.
 */
internal fun openInGoogleMaps(context: Context, uiState: MapUiState) {
    val destination = uiState.geofence ?: return
    val mode = when (uiState.travelMode) {
        TravelMode.WALKING -> "w"
        TravelMode.DRIVING -> "d"
        TravelMode.MOTORCYCLE -> "l"
    }
    val navigation = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        android.net.Uri.parse("google.navigation:q=${destination.latitude},${destination.longitude}&mode=$mode")
    ).setPackage("com.google.android.apps.maps")
    val generic = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        android.net.Uri.parse(
            "geo:${destination.latitude},${destination.longitude}?q=${destination.latitude},${destination.longitude}"
        )
    )
    try {
        context.startActivity(navigation)
    } catch (e: android.content.ActivityNotFoundException) {
        try {
            context.startActivity(generic)
        } catch (e: android.content.ActivityNotFoundException) {
            android.widget.Toast.makeText(context, "No maps app is installed.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
