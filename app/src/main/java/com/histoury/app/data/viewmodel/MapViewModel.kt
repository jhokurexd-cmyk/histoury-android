package com.histoury.app.data.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.location.Location
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.histoury.app.data.model.Geofence
import com.histoury.app.data.model.HistoricalSite
import com.histoury.app.data.repository.DirectionsRepository
import com.histoury.app.data.model.toSiteShape
import com.histoury.app.data.repository.GeofenceRepository
import com.histoury.app.data.repository.HistoricalSiteRepository
import com.histoury.app.data.repository.PlaceRepository
import com.histoury.app.data.repository.TagRepository
import com.histoury.app.data.repository.TravelMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.ceil

data class MapUiState(
    val isLoading: Boolean = false,
    val isRouteLoading: Boolean = false,
    val site: HistoricalSite? = null,
    val geofence: Geofence? = null,
    val tagNames: List<String> = emptyList(),
    val userLatitude: Double? = null,
    val userLongitude: Double? = null,
    val userBearing: Float? = null,
    val routePoints: List<LatLng> = emptyList(),
    val isRouteEstimated: Boolean = false,
    val distanceMeters: Float? = null,
    val etaMinutes: Int? = null,
    val hasLocationPermission: Boolean = false,
    val errorMessage: String? = null,
    val isNavigating: Boolean = false,
    val hasArrived: Boolean = false,
    val travelMode: TravelMode = TravelMode.WALKING
)

// Rough fallback speeds used only when the Directions API call fails and we
// draw a straight line instead. Real ETAs otherwise come from the API.
private const val AVERAGE_WALKING_SPEED_METERS_PER_SECOND = 1.4f
private const val AVERAGE_DRIVING_SPEED_METERS_PER_SECOND = 11.1f // ~40 km/h
private const val AVERAGE_MOTORCYCLE_SPEED_METERS_PER_SECOND = 9.7f // ~35 km/h
private const val ARRIVAL_THRESHOLD_METERS = 25f
private const val ROUTE_REFRESH_INTERVAL_MS = 20_000L

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val historicalSiteRepository = HistoricalSiteRepository()

    private val geofenceRepository = GeofenceRepository()

    private val placeRepository = PlaceRepository()

    private val directionsRepository = DirectionsRepository()

    private val tagRepository = TagRepository()

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(application)

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private var lastRouteRefreshAtMs = 0L

    private var routeRequestJob: Job? = null

    private val locationCallback = object : LocationCallback() {

        override fun onLocationResult(result: LocationResult) {

            val location = result.lastLocation ?: return

            onLiveLocationUpdate(location)
        }
    }

    fun loadSiteData(siteDocumentId: String) {

        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null
            )

            // Historical site first; if the document isn't a site, it may be
            // a Place (café/restaurant/park/school) — adapt it into the same
            // shape so directions/travel modes/navigation work unchanged.
            val site = historicalSiteRepository.getHistoricalSiteById(siteDocumentId)
                ?: placeRepository.getPlaceById(siteDocumentId)?.toSiteShape()

            if (site == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "This destination could not be found."
                )
                return@launch
            }

            val geofence = geofenceRepository.getGeofenceForSite(site.siteId)

            // Site coordinates (historical_sites.latitude/longitude) are the
            // canonical location when present; the geofence is the fallback
            // for older sites, and still supplies the trigger radius.
            val effectiveGeofence = if (site.latitude != null && site.longitude != null) {
                (geofence ?: Geofence(siteId = site.siteId))
                    .copy(latitude = site.latitude, longitude = site.longitude)
            } else {
                geofence
            }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                site = site,
                geofence = effectiveGeofence,
                errorMessage = if (effectiveGeofence == null) {
                    "No location data is available for this site yet."
                } else {
                    null
                }
            )

            fetchWalkingRouteIfReady()
            loadTagNames(site)
        }
    }

    // Places (cafés/restaurants/parks/schools) don't carry tagIds, so this
    // simply resolves to an empty list for them — no tag row shows for a
    // Place destination, which is expected.
    private suspend fun loadTagNames(site: HistoricalSite) {

        if (site.tagIds.isEmpty()) {
            return
        }

        try {
            val allTags = tagRepository.getTags()
            val tagsById = allTags.associate { tag -> tag.id to tag.name }
            val names = site.tagIds.mapNotNull { tagId -> tagsById[tagId] }

            _uiState.value = _uiState.value.copy(tagNames = names)
        } catch (e: Exception) {
            // Tags are a nice-to-have here; ignore failures.
        }
    }

    fun setTravelMode(mode: TravelMode) {

        if (_uiState.value.travelMode == mode) {
            return
        }

        _uiState.value = _uiState.value.copy(travelMode = mode)

        fetchWalkingRouteIfReady()
    }

    fun onLocationPermissionResult(granted: Boolean) {

        _uiState.value = _uiState.value.copy(hasLocationPermission = granted)

        if (granted) {
            fetchDeviceLocation()
        }
    }

    @SuppressLint("MissingPermission")
    fun fetchDeviceLocation() {

        if (!_uiState.value.hasLocationPermission) {
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->

            if (location != null) {

                _uiState.value = _uiState.value.copy(
                    userLatitude = location.latitude,
                    userLongitude = location.longitude
                )

                fetchWalkingRouteIfReady()
            }
        }
    }

    /**
     * Starts in-app turn-by-turn tracking: the map follows the user's live
     * position and distance/ETA keep updating, instead of handing off to an
     * external maps app.
     */
    fun startNavigation() {

        _uiState.value = _uiState.value.copy(
            isNavigating = true,
            hasArrived = false
        )

        startLiveLocationUpdates()
    }

    fun stopNavigation() {

        _uiState.value = _uiState.value.copy(isNavigating = false)

        stopLiveLocationUpdates()
    }

    @SuppressLint("MissingPermission")
    private fun startLiveLocationUpdates() {

        if (!_uiState.value.hasLocationPermission) {
            return
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            3_000L
        )
            .setMinUpdateIntervalMillis(2_000L)
            .build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun stopLiveLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun onLiveLocationUpdate(location: Location) {

        _uiState.value = _uiState.value.copy(
            userLatitude = location.latitude,
            userLongitude = location.longitude,
            userBearing = if (location.hasBearing()) location.bearing else null
        )

        updateRemainingDistanceLive()

        val now = System.currentTimeMillis()

        if (now - lastRouteRefreshAtMs > ROUTE_REFRESH_INTERVAL_MS) {
            lastRouteRefreshAtMs = now
            fetchWalkingRouteIfReady()
        }
    }

    private fun updateRemainingDistanceLive() {

        val currentState = _uiState.value

        val geofence = currentState.geofence ?: return

        val userLatitude = currentState.userLatitude ?: return

        val userLongitude = currentState.userLongitude ?: return

        val results = FloatArray(1)

        Location.distanceBetween(
            userLatitude,
            userLongitude,
            geofence.latitude,
            geofence.longitude,
            results
        )

        val distanceMeters = results[0]

        val hasArrived = currentState.isNavigating &&
            distanceMeters <= ARRIVAL_THRESHOLD_METERS

        _uiState.value = currentState.copy(
            distanceMeters = distanceMeters,
            hasArrived = hasArrived,
            isNavigating = if (hasArrived) false else currentState.isNavigating
        )

        if (hasArrived) {
            stopLiveLocationUpdates()
        }
    }

    private fun fetchWalkingRouteIfReady() {

        val currentState = _uiState.value

        val geofence = currentState.geofence ?: return

        val userLatitude = currentState.userLatitude ?: return

        val userLongitude = currentState.userLongitude ?: return

        // Only the newest location/mode request may update the map. Without
        // cancellation, a slower older response can overwrite fresh state.
        routeRequestJob?.cancel()
        routeRequestJob = viewModelScope.launch {

            _uiState.value = _uiState.value.copy(isRouteLoading = true)

            val origin = LatLng(userLatitude, userLongitude)

            val destination = LatLng(geofence.latitude, geofence.longitude)

            val directionsResult = directionsRepository.getDirections(
                origin = origin,
                destination = destination,
                mode = currentState.travelMode
            )

            if (directionsResult != null) {

                _uiState.value = _uiState.value.copy(
                    isRouteLoading = false,
                    distanceMeters = directionsResult.distanceMeters.toFloat(),
                    etaMinutes = ceil(directionsResult.durationSeconds / 60.0)
                        .toInt()
                        .coerceAtLeast(1),
                    routePoints = directionsResult.routePoints,
                    isRouteEstimated = false,
                    errorMessage = null
                )

            } else {

                applyStraightLineFallback(origin, destination, currentState.travelMode)
            }
        }
    }

    private fun applyStraightLineFallback(
        origin: LatLng,
        destination: LatLng,
        mode: TravelMode
    ) {

        val results = FloatArray(1)

        Location.distanceBetween(
            origin.latitude,
            origin.longitude,
            destination.latitude,
            destination.longitude,
            results
        )

        val distanceMeters = results[0]

        val speedMetersPerSecond = when (mode) {
            TravelMode.WALKING -> AVERAGE_WALKING_SPEED_METERS_PER_SECOND
            TravelMode.DRIVING -> AVERAGE_DRIVING_SPEED_METERS_PER_SECOND
            TravelMode.MOTORCYCLE -> AVERAGE_MOTORCYCLE_SPEED_METERS_PER_SECOND
        }

        val etaMinutes = ceil(
            distanceMeters / (speedMetersPerSecond * 60)
        ).toInt().coerceAtLeast(1)

        val modeLabel = when (mode) {
            TravelMode.WALKING -> "walking"
            TravelMode.DRIVING -> "driving"
            TravelMode.MOTORCYCLE -> "motorcycle"
        }

        _uiState.value = _uiState.value.copy(
            isRouteLoading = false,
            distanceMeters = distanceMeters,
            etaMinutes = etaMinutes,
            routePoints = listOf(origin, destination),
            isRouteEstimated = true,
            errorMessage = "Couldn't calculate a $modeLabel route right now. " +
                "Showing a straight-line estimate instead."
        )
    }

    override fun onCleared() {
        super.onCleared()
        routeRequestJob?.cancel()
        stopLiveLocationUpdates()
    }
}
