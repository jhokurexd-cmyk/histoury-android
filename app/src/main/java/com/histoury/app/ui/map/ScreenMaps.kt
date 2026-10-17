package com.histoury.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import com.google.android.gms.maps.model.LatLng
import com.histoury.app.data.model.HistoricalSite
import com.histoury.app.data.model.ItineraryStop
import com.histoury.app.data.model.StopStatus
import com.histoury.app.data.routing.RoutePath
import com.histoury.app.data.viewmodel.CameraTarget
import com.histoury.app.data.viewmodel.MapUiState
import com.histoury.app.map.MapConfig
import com.histoury.app.theme.AccentGold
import com.histoury.app.theme.CompletedContentColor
import com.histoury.app.theme.Primary
import com.histoury.app.theme.Success

// MapLibre versions of the app's maps. Each one takes the same screen state
// the Google version used and only decides what to draw and where the
// camera goes; HistouryMapLibre does the rest. Zoom levels are written in
// the Google levels the screens were tuned with (converted internally).

/** Directions preview (Map screen, before "Navigate"). */
@Composable
internal fun RoutePreviewMapLibre(
    uiState: MapUiState,
    siteLatLng: LatLng?,
    modifier: Modifier = Modifier,
    topInsetDp: Float = 0f,
    bottomInsetDp: Float = 0f
) {
    val state = rememberHistouryMapState()
    val primary = Primary.toArgb()
    val green = Success.toArgb()

    val scene = remember(
        uiState.routePoints, uiState.isRouteEstimated, uiState.commuteSteps, uiState.finalWalkPoints, siteLatLng,
        uiState.routeTargetLatitude, uiState.routeTargetLongitude, uiState.entranceName, uiState.site, primary, green
    ) {
        val lines = mutableListOf<MapLine>()
        val commuteParts = uiState.commuteSteps.filter { it.points.size >= 2 }
        if (!uiState.isRouteEstimated) {
            if (commuteParts.isNotEmpty()) {
                // Commute: walking parts dotted, each ride solid in its
                // route's own colour (from OpenStreetMap), else Histoury coral.
                commuteParts.forEachIndexed { index, step ->
                    lines += if (step.isTransitRide) {
                        val rideColor = step.transit?.lineColor
                            ?.let { runCatching { android.graphics.Color.parseColor(it) }.getOrNull() }
                            ?: primary
                        MapLine("commute-$index", step.points, rideColor, widthDp = 5f, casingColor = android.graphics.Color.WHITE, casingWidthDp = 8f)
                    } else {
                        MapLine("commute-$index", step.points, primary, widthDp = 4f, pattern = LinePattern.DOTTED)
                    }
                }
            } else if (uiState.routePoints.size >= 2) {
                lines += MapLine(
                    id = "route",
                    points = uiState.routePoints,
                    color = primary,
                    widthDp = 5f,
                    casingColor = android.graphics.Color.WHITE,
                    casingWidthDp = 8f
                )
                // Car / motorcycle: the walk from the drop-off to the
                // entrance, dotted like the commute walks.
                if (uiState.finalWalkPoints.size >= 2) {
                    lines += MapLine("final-walk", uiState.finalWalkPoints, primary, widthDp = 4f, pattern = LinePattern.DOTTED)
                }
            }
        }
        val markers = mutableListOf<MapMarker>()
        if (siteLatLng != null) {
            markers += MapMarker(
                id = "site",
                position = siteLatLng,
                icon = sitePinIcon(uiState.site?.featuredImage, primary),
                title = uiState.site?.siteName ?: "Historical Site",
                subtitle = uiState.site?.location,
                zIndex = 1f
            )
        }
        val entranceLat = uiState.routeTargetLatitude
        val entranceLng = uiState.routeTargetLongitude
        if (entranceLat != null && entranceLng != null) {
            markers += MapMarker(
                id = "entrance",
                position = LatLng(entranceLat, entranceLng),
                icon = MarkerIcon.Pin(green),
                title = uiState.entranceName ?: "Visitor entrance",
                subtitle = "Verified visitor entrance",
                zIndex = 2f
            )
        }
        MapScene(lines = lines, markers = markers)
    }

    // One smooth fit to each new route, never again once the visitor has
    // moved the map themselves (same rules as the Google version).
    var previewFittedFor by rememberSaveable { mutableStateOf<String?>(null) }
    var previewTouched by remember { mutableStateOf(false) }

    LaunchedEffect(state) { state.userGestures.collect { previewTouched = true } }

    LaunchedEffect(siteLatLng) {
        if (siteLatLng != null && uiState.routePoints.size < 2 && previewFittedFor == null) {
            state.moveTo(siteLatLng, 16.5f)
        }
    }

    LaunchedEffect(uiState.routePoints) {
        val key = siteLatLng?.let { "${it.latitude},${it.longitude},${uiState.travelMode}" }
        if (uiState.routePoints.size >= 2 && !uiState.isNavigating && !previewTouched && previewFittedFor != key) {
            state.fitBounds(uiState.routePoints, paddingDp = 44, extraTopDp = topInsetDp.toInt(), extraBottomDp = bottomInsetDp.toInt())
            previewFittedFor = key
        }
    }

    HistouryMapLibre(
        state = state,
        scene = scene,
        modifier = modifier,
        initialTarget = siteLatLng ?: MapConfig.INTRAMUROS_CENTER,
        initialGoogleZoom = 16.5f,
        showUserLocation = uiState.hasLocationPermission,
        showMyLocationButton = true,
        uiInsets = MapInsets(top = topInsetDp, bottom = bottomInsetDp)
    )
}

/** The Map tab: every site as a pin. */
@Composable
internal fun OverviewMapLibre(
    sites: List<HistoricalSite>,
    cameraTarget: CameraTarget?,
    showUserLocation: Boolean,
    topInsetDp: Float,
    bottomInsetDp: Float,
    onSiteClick: (HistoricalSite) -> Unit,
    onMapClick: () -> Unit,
    onMapLoaded: () -> Unit,
    modifier: Modifier = Modifier,
    onCameraTargetHandled: () -> Unit = {}
) {
    val state = rememberHistouryMapState()
    val primary = Primary.toArgb()

    val scene = remember(sites, primary) {
        MapScene(
            markers = sites.mapNotNull { site ->
                val lat = site.latitude ?: return@mapNotNull null
                val lng = site.longitude ?: return@mapNotNull null
                MapMarker(
                    id = site.documentId,
                    position = LatLng(lat, lng),
                    icon = sitePinIcon(site.featuredImage, primary),
                    title = site.siteName
                )
            }
        )
    }

    LaunchedEffect(cameraTarget) {
        val target = cameraTarget ?: return@LaunchedEffect
        state.animateTo(target.latLng, 15f)
        // Cleared once there, so coming back to the tab doesn't fly back.
        onCameraTargetHandled()
    }

    HistouryMapLibre(
        state = state,
        scene = scene,
        modifier = modifier,
        initialTarget = MapConfig.INTRAMUROS_CENTER,
        initialGoogleZoom = 15.5f,
        showUserLocation = showUserLocation,
        showMyLocationButton = true,
        uiInsets = MapInsets(top = topInsetDp, bottom = bottomInsetDp),
        onMapClick = onMapClick,
        onMarkerClick = { marker ->
            val site = sites.firstOrNull { it.documentId == marker.id }
            if (site != null) onSiteClick(site)
            site != null
        },
        onMapLoaded = onMapLoaded
    )
}

/** Arrival screen background map: this stop, and the next one once this is done. */
@Composable
internal fun ArrivalMapLibre(
    siteLatLng: LatLng?,
    siteTitle: String,
    siteImageUrl: String? = null,
    nextLatLng: LatLng?,
    nextTitle: String?,
    showNext: Boolean,
    showUserLocation: Boolean,
    topInsetDp: Float,
    bottomInsetDp: Float,
    modifier: Modifier = Modifier
) {
    val state = rememberHistouryMapState()
    val primary = Primary.toArgb()
    val next = AccentGold.toArgb()

    val scene = remember(siteLatLng, siteTitle, siteImageUrl, nextLatLng, nextTitle, showNext, primary, next) {
        val markers = mutableListOf<MapMarker>()
        if (siteLatLng != null) {
            markers += MapMarker("site", siteLatLng, sitePinIcon(siteImageUrl, primary), title = siteTitle, zIndex = 2f)
        }
        if (nextLatLng != null && showNext) {
            markers += MapMarker("next", nextLatLng, MarkerIcon.Pin(next), title = "Next: ${nextTitle.orEmpty()}", zIndex = 1f, alpha = 0.85f)
        }
        MapScene(markers = markers)
    }

    LaunchedEffect(siteLatLng) {
        if (siteLatLng != null) state.moveTo(siteLatLng, 17.5f)
    }

    LaunchedEffect(showNext, nextLatLng) {
        if (showNext && siteLatLng != null && nextLatLng != null) {
            state.fitBounds(listOf(siteLatLng, nextLatLng), paddingDp = 60, extraTopDp = topInsetDp.toInt(), extraBottomDp = bottomInsetDp.toInt())
        }
    }

    HistouryMapLibre(
        state = state,
        scene = scene,
        modifier = modifier,
        initialTarget = siteLatLng ?: MapConfig.INTRAMUROS_CENTER,
        initialGoogleZoom = 17.5f,
        showUserLocation = showUserLocation,
        showMyLocationButton = false,
        uiInsets = MapInsets(top = topInsetDp, bottom = bottomInsetDp)
    )
}

/** Itinerary route overview: numbered stops and each routed leg. */
@Composable
internal fun ItineraryMapLibre(
    routePath: RoutePath?,
    stops: List<ItineraryStop>,
    modifier: Modifier = Modifier,
    // From where the visitor is now to stop 1, when they aren't there yet.
    approachLeg: com.histoury.app.data.routing.RouteLeg? = null,
    showUserLocation: Boolean = false
) {
    val state = rememberHistouryMapState()
    val primary = Primary.toArgb()
    val done = CompletedContentColor.toArgb()
    val recommended = AccentGold.toArgb()

    val scene = remember(routePath, stops, approachLeg, primary, done, recommended) {
        val lines = mutableListOf<MapLine>()

        // Getting there: commute rides in their own colours with dotted
        // walks; any other mode as a dashed line (it isn't part of the day's
        // stops, so it stays visually apart from them).
        approachLeg?.takeIf { !it.isStraightLineFallback && it.points.size >= 2 }?.let { leg ->
            val parts = leg.steps.filter { it.points.size >= 2 }
            if (parts.isNotEmpty()) {
                parts.forEachIndexed { i, step ->
                    lines += if (step.isTransitRide) {
                        val rideColor = step.transit?.lineColor
                            ?.let { runCatching { android.graphics.Color.parseColor(it) }.getOrNull() }
                            ?: primary
                        MapLine("approach-$i", step.points, rideColor, widthDp = 4.5f,
                            casingColor = android.graphics.Color.WHITE, casingWidthDp = 7.5f, zIndex = 1.5f)
                    } else {
                        MapLine("approach-$i", step.points, primary, widthDp = 3.5f, pattern = LinePattern.DOTTED, zIndex = 1.5f)
                    }
                }
            } else {
                lines += MapLine("approach", leg.points, primary, widthDp = 4f, pattern = LinePattern.DASHED,
                    casingColor = android.graphics.Color.WHITE, casingWidthDp = 6.5f, zIndex = 1.5f)
            }
        }

        routePath?.legs?.forEach { leg ->
            // No line for an unrouted leg: a straight line through walls
            // looks like a walking route and isn't one.
            if (leg.isStraightLineFallback || leg.points.size < 2) return@forEach
            val completed = stops.getOrNull(leg.toIndex)?.status == StopStatus.COMPLETED
            // Commute: each walk dotted, each ride solid in its route's own
            // colour (from OpenStreetMap), like the Map screen's directions.
            val parts = leg.steps.filter { it.points.size >= 2 }
            if (!completed && parts.isNotEmpty()) {
                parts.forEachIndexed { i, step ->
                    val id = "leg-${leg.fromIndex}-${leg.toIndex}-$i"
                    lines += if (step.isTransitRide) {
                        val rideColor = step.transit?.lineColor
                            ?.let { runCatching { android.graphics.Color.parseColor(it) }.getOrNull() }
                            ?: primary
                        MapLine(id, step.points, rideColor, widthDp = 4.5f,
                            casingColor = android.graphics.Color.WHITE, casingWidthDp = 7.5f, zIndex = 2f)
                    } else {
                        MapLine(id, step.points, primary, widthDp = 3.5f, pattern = LinePattern.DOTTED, zIndex = 2f)
                    }
                }
                return@forEach
            }
            lines += if (completed) {
                MapLine("leg-${leg.fromIndex}-${leg.toIndex}", leg.points, done, widthDp = 4f, zIndex = 1f)
            } else {
                MapLine(
                    "leg-${leg.fromIndex}-${leg.toIndex}", leg.points, primary, widthDp = 4f,
                    casingColor = android.graphics.Color.WHITE, casingWidthDp = 7f, zIndex = 2f
                )
            }
        }
        val markers = stops.mapIndexed { index, stop ->
            val isCompleted = stop.status == StopStatus.COMPLETED
            val color = when {
                isCompleted -> done
                stop.isRecommended -> recommended
                else -> primary
            }
            MapMarker(
                id = "stop-${stop.refType}-${stop.refId}",
                position = LatLng(stop.latitude, stop.longitude),
                icon = MarkerIcon.NumberedPin(index + 1, color),
                title = "${index + 1}. ${stop.name}${if (isCompleted) " · Done" else ""}",
                zIndex = 3f
            )
        }
        MapScene(lines = lines, markers = markers)
    }

    // Frame the drawn route, not just the stops — and the way there from
    // the visitor's position, so the whole trip is in view.
    LaunchedEffect(stops, routePath, approachLeg) {
        val dayPoints = routePath?.points?.takeIf { it.isNotEmpty() }
            ?: stops.map { LatLng(it.latitude, it.longitude) }
        val framePoints = dayPoints + approachLeg?.points?.takeIf { !approachLeg.isStraightLineFallback }.orEmpty()
        when {
            framePoints.size == 1 -> state.moveTo(framePoints.first(), 17f)
            framePoints.size > 1 -> state.fitBounds(framePoints, paddingDp = 44, extraTopDp = 40)
        }
    }

    HistouryMapLibre(
        state = state,
        scene = scene,
        modifier = modifier,
        initialTarget = stops.firstOrNull()?.let { LatLng(it.latitude, it.longitude) } ?: MapConfig.INTRAMUROS_CENTER,
        initialGoogleZoom = 16f,
        showUserLocation = showUserLocation,
        uiInsets = MapInsets(top = 64f)
    )
}

/** Small, non-interactive preview with one pin; a tap opens the full map. */
@Composable
internal fun PlacePreviewMapLibre(
    position: LatLng,
    title: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = rememberHistouryMapState()
    val primary = Primary.toArgb()
    val scene = remember(position, title, primary) {
        MapScene(markers = listOf(MapMarker("place", position, MarkerIcon.Pin(primary), title = title)))
    }
    LaunchedEffect(position) { state.moveTo(position, 17f) }
    HistouryMapLibre(
        state = state,
        scene = scene,
        modifier = modifier,
        initialTarget = position,
        initialGoogleZoom = 17f,
        interactive = false,
        textureMode = true,
        onMapClick = onOpen
    )
}

/**
 * A historical site's pin: its own photo in a circle when it has one,
 * otherwise the plain teardrop pin.
 */
internal fun sitePinIcon(imageUrl: String?, color: Int): MarkerIcon =
    if (!imageUrl.isNullOrBlank()) MarkerIcon.Photo(imageUrl, color) else MarkerIcon.Pin(color)
