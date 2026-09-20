package com.histoury.app.ui.screens.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
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
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import com.histoury.app.ui.components.rememberThemedMapProperties
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

/**
 * The Map tab: a generic, free-browse map like Google Maps.
 *
 * The search bar finds anything: Histoury sites (tap -> site directions
 * map, same as selecting from Home) and any other place in the world
 * (tap -> camera moves there). No pins are placed automatically; pinned
 * navigation only happens on the site-specific MapScreen.
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

    val context = LocalContext.current

    // Home already runs the full permission flow; here we only check what's
    // already granted so the blue "my location" dot can show.
    val hasLocationPermission = remember {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(Unit) {
        mapOverviewViewModel.loadSites()
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(INTRAMUROS_CENTER, 15.5f)
    }

    // Selecting a generic place moves the camera there, Google-Maps style.
    LaunchedEffect(cameraTarget) {

        val target = cameraTarget ?: return@LaunchedEffect

        try {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(target.latLng, 15f)
            )
        } catch (e: Exception) {
            // Map not laid out yet.
        }
    }

    BottomNavScaffold(navController = navController) { padding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = rememberThemedMapProperties(
                    isMyLocationEnabled = hasLocationPermission
                ),
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
            )

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
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
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

                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = TextTertiary,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable {
                                        mapOverviewViewModel.clearSearch()
                                    }
                            )
                        }
                    }
                }

                if (siteResults.isNotEmpty() || placeResults.isNotEmpty()) {

                    Spacer(Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = CardSurface,
                        shadowElevation = 6.dp,
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
                                        mapOverviewViewModel.clearSearch()
                                        navController.navigate(
                                            Routes.Map.createRoute(site.documentId)
                                        )
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
