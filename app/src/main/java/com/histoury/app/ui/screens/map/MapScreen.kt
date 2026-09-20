@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.histoury.app.ui.screens.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.histoury.app.ui.components.rememberThemedMapProperties
import com.histoury.app.data.repository.TravelMode
import com.histoury.app.data.itinerary.ActiveItineraryTrip
import com.histoury.app.data.viewmodel.MapUiState
import com.histoury.app.data.viewmodel.MapViewModel
import com.histoury.app.navigation.Routes
import com.histoury.app.theme.SurfaceSoft
import com.histoury.app.theme.Danger
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Primary
import com.histoury.app.theme.PrimarySoft
import com.histoury.app.theme.RatingStar
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary
import com.histoury.app.ui.components.PrimaryButton

@Composable
fun MapScreen(
    siteId: String,
    navController: NavHostController,
    mapViewModel: MapViewModel = viewModel()
) {

    val uiState by mapViewModel.uiState.collectAsState()

    val context = LocalContext.current

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        mapViewModel.onLocationPermissionResult(isGranted)
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

    val geofence = uiState.geofence

    val siteLatLng = remember(geofence) {
        if (geofence != null) {
            LatLng(geofence.latitude, geofence.longitude)
        } else {
            null
        }
    }

    val cameraPositionState = rememberCameraPositionState()

    LaunchedEffect(siteLatLng) {
        if (siteLatLng != null && !uiState.isNavigating) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(siteLatLng, 17f)
        }
    }

    LaunchedEffect(uiState.routePoints) {

        val routePoints = uiState.routePoints

        if (routePoints.size >= 2 && !uiState.isNavigating) {

            val boundsBuilder = LatLngBounds.Builder()

            routePoints.forEach { point -> boundsBuilder.include(point) }

            try {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120)
                )
            } catch (e: Exception) {
                // Map not laid out yet; the initial marker zoom above still applies.
            }
        }
    }

    // While navigating, the camera follows the user's live position instead
    // of showing the whole route.
    LaunchedEffect(uiState.userLatitude, uiState.userLongitude, uiState.isNavigating) {

        val userLatitude = uiState.userLatitude

        val userLongitude = uiState.userLongitude

        if (uiState.isNavigating && userLatitude != null && userLongitude != null) {

            try {
                cameraPositionState.animate(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder()
                            .target(LatLng(userLatitude, userLongitude))
                            .zoom(18.5f)
                            .bearing(uiState.userBearing ?: 0f)
                            .tilt(45f)
                            .build()
                    )
                )
            } catch (e: Exception) {
                // Map not laid out yet.
            }
        }
    }

    // Reaching the destination while actively navigating in-app takes the
    // person straight to the arrival celebration screen.
    LaunchedEffect(uiState.hasArrived) {

        val arrivedSite = uiState.site
        val arrivedDestinationId = arrivedSite?.documentId

        if (uiState.hasArrived && !arrivedDestinationId.isNullOrBlank()) {
            navController.navigate(Routes.Arrival.createRoute(arrivedDestinationId)) {
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

    BottomSheetScaffold(
        scaffoldState = sheetScaffoldState,
        sheetContainerColor = CardSurface,
        sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        sheetPeekHeight = if (uiState.isNavigating) 140.dp else 340.dp,
        sheetContent = {

            if (uiState.isNavigating) {
                NavigatingBottomSheetContent(
                    uiState = uiState,
                    onEndNavigationClick = {
                        mapViewModel.stopNavigation()
                    }
                )
            } else {
                MapBottomSheetContent(
                    uiState = uiState,
                    onStartNavigationClick = {
                        mapViewModel.startNavigation()
                    },
                    onTravelModeChange = { mode ->
                        mapViewModel.setTravelMode(mode)
                    }
                )
            }
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
                properties = rememberThemedMapProperties(
                    isMyLocationEnabled = uiState.hasLocationPermission
                ),
                uiSettings = MapUiSettings(
                    myLocationButtonEnabled = uiState.hasLocationPermission &&
                        !uiState.isNavigating,
                    zoomControlsEnabled = false
                )
            ) {

                if (uiState.routePoints.size >= 2) {
                    Polyline(
                        points = uiState.routePoints,
                        color = Primary,
                        width = 10f
                    )
                }

                if (siteLatLng != null) {
                    Marker(
                        state = MarkerState(position = siteLatLng),
                        title = uiState.site?.siteName ?: "Historical Site",
                        snippet = uiState.site?.location ?: ""
                    )
                }
            }

            if (!uiState.isNavigating) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.TopStart)
                        .clip(RoundedCornerShape(50))
                        .background(CardSurface)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Primary
                    )
                }
            } else {
                IconButton(
                    onClick = { mapViewModel.stopNavigation() },
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.TopStart)
                        .clip(RoundedCornerShape(50))
                        .background(CardSurface)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "End navigation",
                        tint = Primary
                    )
                }
            }

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
}

@Composable
private fun NavigatingBottomSheetContent(
    uiState: MapUiState,
    onEndNavigationClick: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column {

                Text(
                    text = uiState.site?.siteName ?: "Historical Site",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {

                    Icon(
                        imageVector = travelModeIcon(uiState.travelMode),
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(16.dp)
                    )

                    Spacer(Modifier.width(4.dp))

                    Text(
                        text = "${formatDistance(uiState)} \u2022 ${formatEta(uiState)} left",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            TextButton(onClick = onEndNavigationClick) {
                Text(
                    text = "End",
                    color = Danger,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun MapBottomSheetContent(
    uiState: MapUiState,
    onStartNavigationClick: () -> Unit,
    onTravelModeChange: (TravelMode) -> Unit
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
                        fontSize = 13.sp
                    )
                }
            }

            if (site != null && site.averageRating > 0.0) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = PrimarySoft
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = RatingStar,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = "%.1f".format(site.averageRating),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = TextPrimary
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
                maxLines = 3
            )
        }

        Spacer(Modifier.height(16.dp))

        TravelModeSelector(
            selectedMode = uiState.travelMode,
            onModeSelected = onTravelModeChange
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
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
        } else if (uiState.errorMessage != null) {

            Spacer(Modifier.height(12.dp))

            Text(
                text = uiState.errorMessage,
                color = if (uiState.isRouteEstimated) TextSecondary else Danger,
                fontSize = 13.sp
            )
        } else if (!uiState.hasLocationPermission) {

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Enable location access to see live distance and travel time.",
                color = TextSecondary,
                fontSize = 13.sp
            )
        }

        Spacer(Modifier.height(20.dp))

        PrimaryButton(
            text = if (uiState.isRouteLoading) "Calculating Route" else "Navigate",
            onClick = onStartNavigationClick,
            enabled = uiState.geofence != null,
            isLoading = uiState.isRouteLoading
        )
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

    return "$etaMinutes min"
}
