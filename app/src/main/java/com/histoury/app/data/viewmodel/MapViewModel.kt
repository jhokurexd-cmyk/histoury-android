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
import com.histoury.app.data.repository.RouteStep
import com.histoury.app.data.repository.TagRepository
import com.histoury.app.data.repository.TravelMode
import com.histoury.app.location.NavigationForegroundService
import com.histoury.app.util.BearingSmoother
import com.histoury.app.util.NavigationPipState
import com.histoury.app.util.RouteStepTracker
import com.histoury.app.util.VoiceGuidanceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sqrt

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
    /**
     * True when no real route could be found. There is then NO route line
     * at all (never a straight one through buildings); distance and ETA are
     * straight-line estimates and the screen says so.
     */
    val isRouteEstimated: Boolean = false,
    val distanceMeters: Float? = null,
    val etaMinutes: Int? = null,
    val hasLocationPermission: Boolean = false,
    val errorMessage: String? = null,
    val isNavigating: Boolean = false,
    val hasArrived: Boolean = false,
    val travelMode: TravelMode = TravelMode.WALKING,

    /** The destination is a place (café, shop…), not a historical site. */
    val isPlaceDestination: Boolean = false,

    // ----- live navigation (Waze-style screen) -----

    /** The part of the route still ahead of the visitor — the line shrinks as they walk it. */
    val remainingRoutePoints: List<LatLng> = emptyList(),

    /** What the top banner shows: the next turn and how far away it is. */
    val nextInstruction: NavInstruction? = null,

    /**
     * Current movement speed in km/h, shown on the navigation screen in every
     * travel mode (walking included). Smoothed so it doesn't jitter between
     * fixes; null until the first live fix of a navigation session.
     */
    val speedKmh: Double? = null,

    val isVoiceMuted: Boolean = false,

    /** True while a new route is being fetched because the visitor left the old one. */
    val isRerouting: Boolean = false,

    // ----- where the route ends (verified visitor entrances) -----

    /**
     * The point the current route actually ends at: the site's verified
     * visitor entrance when it has one, otherwise null (the geofence centre
     * is used, as before). Arrival during in-app navigation is judged
     * against this point.
     */
    val routeTargetLatitude: Double? = null,
    val routeTargetLongitude: Double? = null,

    /** Name of the verified entrance the route goes to, e.g. "Main Gate". */
    val entranceName: String? = null,

    /** Short note to show with the route, e.g. that the exact entrance isn't verified yet. */
    val routeNotice: String? = null,

    /** Credit line the current route needs on screen (OpenStreetMap-based routes); null for Google. */
    val routeAttribution: String? = null
)

/**
 * One upcoming turn for the navigation banner.
 *
 * [maneuver] is the Routes API maneuver name (TURN_LEFT, ...), or
 * [NavInstruction.DESTINATION] once every turn has been passed and only the
 * final approach is left.
 */
data class NavInstruction(
    val maneuver: String,
    val text: String,
    val distanceMeters: Float?,
    val thenManeuver: String?
) {
    companion object {
        const val DESTINATION = "DESTINATION"
    }
}

// Rough fallback speeds used only when the Directions API call fails and we
// draw a straight line instead. Real ETAs otherwise come from the API.
private const val AVERAGE_WALKING_SPEED_METERS_PER_SECOND = 1.4f

// Speed indicator: how much each new reading moves the shown value (0..1),
// and the speed below which the visitor counts as standing still.
private const val SPEED_SMOOTHING = 0.35
private const val STANDING_STILL_MPS = 0.3
private const val AVERAGE_DRIVING_SPEED_METERS_PER_SECOND = 11.1f // ~40 km/h
private const val AVERAGE_MOTORCYCLE_SPEED_METERS_PER_SECOND = 9.7f // ~35 km/h
private const val ARRIVAL_THRESHOLD_METERS = 25f
// How often an ACTIVE navigation re-fetches its route although nothing went
// wrong. It used to be every 20 s — about 45 paid calls for a 15-minute
// drive. Every refresh is a billed / quota-counted server call, and the
// ETA already counts down locally between refreshes, so: every 5 minutes
// (driving ETAs then still pick up traffic changes). Leaving the route
// triggers an immediate reroute anyway, and an estimated (failed) route is
// retried sooner.
private const val ROUTE_REFRESH_WALKING_MS = 5 * 60_000L
private const val ROUTE_REFRESH_VEHICLE_MS = 5 * 60_000L
private const val ROUTE_RETRY_WHEN_ESTIMATED_MS = 20_000L

// Consecutive off-route fixes needed before rerouting, so one GPS jump
// between tall walls doesn't trigger a new route.
private const val OFF_ROUTE_CONFIRMATIONS = 2

// GPS course (Location.bearing) is only meaningful while actually moving —
// at low speed or standing still it's computed from a couple of noisy
// fixes and can swing almost randomly between updates. Below this speed,
// the last smoothed bearing is kept as-is instead of feeding in a fresh,
// unreliable reading. 0.6 m/s is well under a normal walking pace
// (~1.4 m/s) so real walking is never mistaken for "standing still."
private const val MIN_BEARING_SPEED_METERS_PER_SECOND = 0.6f

// How far from the drawn route counts as having left it. Wider for
// vehicles, whose GPS fix lags further behind at speed.
private const val OFF_ROUTE_METERS_WALKING = 35f
private const val OFF_ROUTE_METERS_VEHICLE = 55f

// Minimum gap between two off-route reroutes, so a bad GPS patch can't
// fire a burst of Routes API calls.
private const val REROUTE_COOLDOWN_MS = 10_000L

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val historicalSiteRepository = HistoricalSiteRepository()

    private val geofenceRepository = GeofenceRepository()

    private val placeRepository = PlaceRepository()

    private val directionsRepository = DirectionsRepository()

    private val tagRepository = TagRepository()

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(application)

    // Smooths the raw bearing reported by GPS into the value actually fed
    // to the map camera. Held as a field so its eased state carries over
    // continuously between location updates for the whole navigation
    // session, instead of being rebuilt (and losing that continuity) each
    // time the UI recomposes.
    private val bearingSmoother = BearingSmoother()

    // Decides what turn-by-turn instruction, if any, to speak next as the
    // user's location moves along the current route's steps.
    private val routeStepTracker = RouteStepTracker()

    private val voiceGuidanceManager = VoiceGuidanceManager.getInstance(application)

    // The maneuver sequence last handed to routeStepTracker. Used to tell a
    // genuine reroute (a different sequence of turns) apart from a routine
    // periodic refresh that happens to return the same route again — the
    // latter must NOT reset tracking, or an instruction already announced
    // would be spoken a second time a few seconds later.
    private var lastAppliedManeuverSignature: List<String> = emptyList()

    // The steps behind the signature above — kept alongside it so
    // startNavigation() can (re)prime the tracker from whichever route was
    // most recently applied, without having to re-derive it from state.
    private var lastAppliedManeuverSteps: List<RouteStep> = emptyList()

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private var lastRouteRefreshAtMs = 0L

    private var routeRequestJob: Job? = null

    // Seconds per metre on the current route, from the last Routes API
    // answer. Lets the ETA count down smoothly between route refreshes
    // instead of only changing every 20 seconds.
    private var secondsPerMeter: Double? = null

    private var lastRerouteAtMs = 0L

    // Off-route fixes in a row (see OFF_ROUTE_CONFIRMATIONS).
    private var offRouteStreak = 0

    // Travel mode of the route currently on screen, so a failed refresh can
    // keep showing it — but never a route for a different mode.
    private var lastRoutedMode: TravelMode? = null

    // ----- speed indicator -----
    private var smoothedSpeedMps: Double? = null
    private var lastSpeedFix: Location? = null

    private val locationCallback = object : LocationCallback() {

        override fun onLocationResult(result: LocationResult) {

            val location = result.lastLocation ?: return

            onLiveLocationUpdate(location)
        }
    }

    fun loadSiteData(siteDocumentId: String) {

        // A different destination: nothing on screen belongs to it yet.
        lastRoutedMode = null
        offRouteStreak = 0

        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                routeTargetLatitude = null,
                routeTargetLongitude = null,
                entranceName = null,
                routeNotice = null,
                routeAttribution = null
            )

            // Historical site first; if the document isn't a site, it may be
            // a Place (café/restaurant/park/school) — adapt it into the same
            // shape so directions/travel modes/navigation work unchanged.
            // By document id first; getHistoricalSiteBySiteId also accepts
            // the short site id, so a caller passing either still resolves.
            val historicalSite = historicalSiteRepository.getHistoricalSiteBySiteId(siteDocumentId)
            val site = historicalSite
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
                isPlaceDestination = historicalSite == null,
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

        fun useLocation(location: Location) {
            _uiState.value = _uiState.value.copy(
                userLatitude = location.latitude,
                userLongitude = location.longitude
            )
            fetchWalkingRouteIfReady()
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->

            if (location != null) {
                useLocation(location)
            } else {
                // No cached fix (fresh boot, location just turned on): the
                // route preview used to stay on "--" forever. Ask for a
                // current fix instead.
                fusedLocationClient
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { current: Location? ->
                        if (current != null) useLocation(current)
                    }
            }
        }
    }

    /**
     * The screen has acted on [MapUiState.hasArrived] (opened Arrival).
     * Clearing it here is what stops the loop on itinerary trips: the Map
     * stays on the back stack under Arrival, and when the visitor pressed
     * Back, the Map's LaunchedEffect ran again with hasArrived still true
     * and sent them straight back to Arrival.
     */
    fun onArrivalHandled() {
        if (_uiState.value.hasArrived) {
            _uiState.value = _uiState.value.copy(hasArrived = false)
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
            hasArrived = false,
            // No stale speed from the last trip before the first new fix.
            speedKmh = null
        )

        // Fresh session: ease the camera in from wherever it currently is
        // rather than from a stale bearing left over from an earlier trip,
        // and walk the current route's steps from the very first maneuver.
        bearingSmoother.reset()
        routeStepTracker.setRoute(lastAppliedManeuverSteps, _uiState.value.travelMode)

        refreshNavInstruction()

        startLiveLocationUpdates()

        // Keeps location live if the visitor leaves the app mid-route.
        if (_uiState.value.hasLocationPermission) {
            NavigationForegroundService.start(
                getApplication(),
                _uiState.value.site?.siteName.orEmpty()
            )
        }
    }

    private fun stopForegroundNavigation() {
        NavigationPipState.navigationStatus.value = null
        NavigationForegroundService.stop(getApplication())
    }

    fun toggleVoiceMuted() {
        val muted = !_uiState.value.isVoiceMuted
        _uiState.value = _uiState.value.copy(isVoiceMuted = muted)
        if (muted) voiceGuidanceManager.stop()
    }

    private fun speak(text: String) {
        if (!_uiState.value.isVoiceMuted) voiceGuidanceManager.speak(text)
    }

    fun stopNavigation() {

        _uiState.value = _uiState.value.copy(
            isNavigating = false,
            nextInstruction = null,
            isRerouting = false,
            speedKmh = null
        )

        stopLiveLocationUpdates()

        stopForegroundNavigation()

        // Cuts off anything still being spoken the moment navigation ends.
        voiceGuidanceManager.stop()
    }

    @SuppressLint("MissingPermission")
    private fun startLiveLocationUpdates() {

        // Each navigation session's speed starts fresh.
        smoothedSpeedMps = null
        lastSpeedFix = null


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

    /**
     * Movement speed in m/s for the speed indicator.
     *
     * GPS reports its own speed on most fixes; when it doesn't (common at
     * walking pace), it's worked out from the distance and time since the
     * previous fix. Either way it's eased (exponential smoothing) so a
     * single noisy fix doesn't make the number jump, and anything under a
     * slow shuffle reads as standing still.
     */
    private fun updateSpeed(location: Location): Double? {
        val previous = lastSpeedFix
        lastSpeedFix = location

        val raw: Double? = when {
            location.hasSpeed() -> location.speed.toDouble()
            previous != null -> {
                val seconds = (location.elapsedRealtimeNanos - previous.elapsedRealtimeNanos) / 1e9
                if (seconds in 0.5..30.0) previous.distanceTo(location) / seconds else null
            }
            else -> null
        }

        if (raw == null || raw.isNaN() || raw < 0) return smoothedSpeedMps

        val current = smoothedSpeedMps
        val eased = if (current == null) raw else current + SPEED_SMOOTHING * (raw - current)
        smoothedSpeedMps = if (eased < STANDING_STILL_MPS) 0.0 else eased
        return smoothedSpeedMps
    }

    private fun onLiveLocationUpdate(location: Location) {

        val smoothedBearing = updateSmoothedBearing(location)

        _uiState.value = _uiState.value.copy(
            userLatitude = location.latitude,
            userLongitude = location.longitude,
            userBearing = smoothedBearing,
            speedKmh = updateSpeed(location)?.let { it * 3.6 }
        )

        val progress = projectOntoRoute(
            _uiState.value.routePoints,
            location.latitude,
            location.longitude
        )

        if (progress != null) {
            _uiState.value = _uiState.value.copy(remainingRoutePoints = progress.remainingPoints)
        }

        updateRemainingDistanceLive(progress?.remainingMeters)
        updateVoiceGuidance(location)
        refreshNavInstruction()

        val now = System.currentTimeMillis()

        offRouteStreak = if (isOffRoute(location, progress)) offRouteStreak + 1 else 0

        if (offRouteStreak >= OFF_ROUTE_CONFIRMATIONS && now - lastRerouteAtMs > REROUTE_COOLDOWN_MS) {

            // Left the route: fetch a new one from here straight away
            // instead of waiting for the next 20-second refresh.
            lastRerouteAtMs = now
            lastRouteRefreshAtMs = now
            offRouteStreak = 0
            _uiState.value = _uiState.value.copy(isRerouting = true)
            speak("Rerouting.")
            fetchWalkingRouteIfReady()

        } else if (now - lastRouteRefreshAtMs > refreshIntervalMs()) {
            lastRouteRefreshAtMs = now
            fetchWalkingRouteIfReady()
        }
    }

    private fun refreshIntervalMs(): Long {
        val state = _uiState.value
        return when {
            state.isRouteEstimated -> ROUTE_RETRY_WHEN_ESTIMATED_MS
            state.travelMode == TravelMode.WALKING -> ROUTE_REFRESH_WALKING_MS
            else -> ROUTE_REFRESH_VEHICLE_MS
        }
    }

    private fun isOffRoute(location: Location, progress: RouteProgress?): Boolean {

        val state = _uiState.value

        if (!state.isNavigating || state.isRouteEstimated || progress == null) return false

        val limit = if (state.travelMode == TravelMode.WALKING) {
            OFF_ROUTE_METERS_WALKING
        } else {
            OFF_ROUTE_METERS_VEHICLE
        }

        // A fix this imprecise can't tell on-route from off-route.
        if (location.hasAccuracy() && location.accuracy > limit) return false

        return progress.offRouteMeters > limit
    }

    /** Rebuilds the banner's "next turn" from the step tracker and the current position. */
    private fun refreshNavInstruction() {

        val state = _uiState.value

        if (!state.isNavigating) return

        val lat = state.userLatitude
        val lng = state.userLongitude

        val upcoming = routeStepTracker.upcomingManeuver()

        val instruction = if (upcoming != null) {

            val distance = if (lat != null && lng != null) {
                distanceBetween(lat, lng, upcoming.startLatitude, upcoming.startLongitude)
            } else {
                null
            }

            NavInstruction(
                maneuver = upcoming.maneuver,
                text = cleanInstruction(upcoming.instructionText)
                    .ifBlank { RouteStepTracker.describeManeuver(upcoming.maneuver).capitalizeFirst() },
                distanceMeters = distance,
                thenManeuver = routeStepTracker.maneuverAfterUpcoming()?.maneuver
            )

        } else {

            // Every turn passed (or no turn data at all): just the final approach.
            NavInstruction(
                maneuver = NavInstruction.DESTINATION,
                text = "Continue to ${state.site?.siteName ?: "your destination"}",
                distanceMeters = state.distanceMeters,
                thenManeuver = null
            )
        }

        _uiState.value = _uiState.value.copy(nextInstruction = instruction)
    }

    private fun cleanInstruction(raw: String): String =
        raw.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()

    private fun String.capitalizeFirst(): String =
        replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    private fun distanceBetween(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, results)
        return results[0]
    }

    private data class RouteProgress(
        val remainingPoints: List<LatLng>,
        val remainingMeters: Float,
        val offRouteMeters: Float
    )

    /**
     * Snaps the visitor onto the nearest point of [route] and measures what's
     * left from there. Uses a flat local projection, which is accurate to
     * well under a metre over the few hundred metres an Intramuros walk
     * covers.
     */
    private fun projectOntoRoute(route: List<LatLng>, lat: Double, lng: Double): RouteProgress? {

        if (route.size < 2) return null

        val metersPerDegLat = 110_540.0
        val metersPerDegLng = 111_320.0 * cos(Math.toRadians(lat))

        fun x(p: LatLng) = (p.longitude - lng) * metersPerDegLng
        fun y(p: LatLng) = (p.latitude - lat) * metersPerDegLat

        var bestIndex = 0
        var bestT = 0.0
        var bestDistance = Double.MAX_VALUE

        for (i in 0 until route.size - 1) {

            val ax = x(route[i])
            val ay = y(route[i])
            val dx = x(route[i + 1]) - ax
            val dy = y(route[i + 1]) - ay
            val lengthSquared = dx * dx + dy * dy

            val t = if (lengthSquared == 0.0) 0.0 else ((-ax * dx - ay * dy) / lengthSquared).coerceIn(0.0, 1.0)

            val px = ax + t * dx
            val py = ay + t * dy
            val distance = sqrt(px * px + py * py)

            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = i
                bestT = t
            }
        }

        val a = route[bestIndex]
        val b = route[bestIndex + 1]

        val snapped = LatLng(
            a.latitude + (b.latitude - a.latitude) * bestT,
            a.longitude + (b.longitude - a.longitude) * bestT
        )

        val remaining = listOf(snapped) + route.subList(bestIndex + 1, route.size)

        var remainingMeters = 0f
        for (i in 0 until remaining.size - 1) {
            remainingMeters += distanceBetween(
                remaining[i].latitude, remaining[i].longitude,
                remaining[i + 1].latitude, remaining[i + 1].longitude
            )
        }

        return RouteProgress(remaining, remainingMeters, bestDistance.toFloat())
    }

    /**
     * Feeds the raw GPS bearing through [bearingSmoother], but only when
     * that raw reading is actually trustworthy (see
     * [MIN_BEARING_SPEED_METERS_PER_SECOND]). This — plus the smoother's
     * own shortest-path easing and noise deadband — is what turns the
     * previous "snap straight to whatever GPS just reported, including a
     * hard reset to 0° whenever it briefly had no bearing at all" behavior
     * into a map that rotates smoothly and only when the user is actually
     * moving in a clear direction.
     */
    private fun updateSmoothedBearing(location: Location): Float? {

        val hasReliableBearing = location.hasBearing() &&
            location.hasSpeed() &&
            location.speed >= MIN_BEARING_SPEED_METERS_PER_SECOND

        if (!hasReliableBearing) {
            return bearingSmoother.current()
        }

        return bearingSmoother.update(location.bearing)
    }

    private fun updateVoiceGuidance(location: Location) {

        val currentState = _uiState.value

        if (!currentState.isNavigating) {
            return
        }

        val instruction = routeStepTracker.onLocationUpdate(
            location.latitude,
            location.longitude
        )

        if (instruction != null) {
            speak(instruction)
        }
    }

    private fun updateRemainingDistanceLive(routeRemainingMeters: Float? = null) {

        val currentState = _uiState.value

        val geofence = currentState.geofence ?: return

        val userLatitude = currentState.userLatitude ?: return

        val userLongitude = currentState.userLongitude ?: return

        // The route's real end point (a verified entrance) when known; the
        // geofence centre otherwise. Fort Santiago's centre is well inside
        // the walls, so judging arrival there would never fire at the gate.
        val targetLatitude = currentState.routeTargetLatitude ?: geofence.latitude
        val targetLongitude = currentState.routeTargetLongitude ?: geofence.longitude

        val results = FloatArray(1)

        Location.distanceBetween(
            userLatitude,
            userLongitude,
            targetLatitude,
            targetLongitude,
            results
        )

        val straightLineMeters = results[0]

        // Arrival is judged on the straight-line distance to the site, but
        // what's shown is the distance left along the route — the number the
        // visitor will actually walk.
        val hasArrived = currentState.isNavigating &&
            straightLineMeters <= ARRIVAL_THRESHOLD_METERS

        val justArrived = hasArrived && !currentState.hasArrived

        val shownMeters = routeRemainingMeters ?: straightLineMeters

        val liveEta = secondsPerMeter
            ?.let { ceil(shownMeters * it / 60.0).toInt().coerceAtLeast(1) }
            ?: currentState.etaMinutes

        _uiState.value = currentState.copy(
            distanceMeters = shownMeters,
            etaMinutes = if (currentState.isNavigating) liveEta else currentState.etaMinutes,
            hasArrived = hasArrived,
            isNavigating = if (hasArrived) false else currentState.isNavigating
        )

        if (justArrived) {
            speak(routeStepTracker.announceArrival())
            stopForegroundNavigation()
        } else if (currentState.isNavigating) {
            // The ongoing notification's "4 min · 300 m" line.
            val etaText = (if (currentState.isNavigating) liveEta else null)?.let { "$it min" }
            val distanceText = if (shownMeters < 1000f) {
                "${shownMeters.toInt()} m"
            } else {
                String.format(java.util.Locale.US, "%.1f km", shownMeters / 1000f)
            }
            NavigationPipState.navigationStatus.value =
                listOfNotNull(etaText, distanceText).joinToString(" · ")
        }

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

            val thisJob = coroutineContext[Job]

            try {

            _uiState.value = _uiState.value.copy(isRouteLoading = true)

            val origin = LatLng(userLatitude, userLongitude)

            val destination = LatLng(geofence.latitude, geofence.longitude)

            val outcome = directionsRepository.getDirectionsOutcome(
                origin = origin,
                destination = destination,
                mode = currentState.travelMode,
                destinationSiteId = currentState.site?.siteId
                    ?.takeIf { it.isNotBlank() && !currentState.isPlaceDestination }
            )

            val directionsResult = (outcome as? com.histoury.app.data.repository.DirectionsOutcome.Success)?.result

            if (directionsResult != null) {

                secondsPerMeter = if (directionsResult.distanceMeters > 0) {
                    directionsResult.durationSeconds.toDouble() / directionsResult.distanceMeters
                } else {
                    null
                }

                _uiState.value = _uiState.value.copy(
                    isRouteLoading = false,
                    isRerouting = false,
                    remainingRoutePoints = directionsResult.routePoints,
                    distanceMeters = directionsResult.distanceMeters.toFloat(),
                    etaMinutes = ceil(directionsResult.durationSeconds / 60.0)
                        .toInt()
                        .coerceAtLeast(1),
                    routePoints = directionsResult.routePoints,
                    isRouteEstimated = false,
                    errorMessage = null,
                    routeTargetLatitude = directionsResult.destination
                        ?.takeIf { it.isVerifiedEntrance }?.latitude,
                    routeTargetLongitude = directionsResult.destination
                        ?.takeIf { it.isVerifiedEntrance }?.longitude,
                    entranceName = directionsResult.destination
                        ?.takeIf { it.isVerifiedEntrance }?.entrance?.name,
                    routeNotice = directionsResult.warnings.firstOrNull(),
                    routeAttribution = directionsResult.attribution
                )

                lastRoutedMode = currentState.travelMode

                applyRouteStepsForVoiceGuidance(directionsResult.steps, currentState.travelMode)

                refreshNavInstruction()

            } else if (
                lastRoutedMode == currentState.travelMode &&
                _uiState.value.routePoints.isNotEmpty() &&
                !_uiState.value.isRouteEstimated
            ) {

                // A refresh or reroute failed (weak signal, provider hiccup)
                // but a real route for this mode is already on screen: keep
                // it and keep navigating, instead of throwing it away for a
                // straight-line estimate. The next refresh tries again.
                _uiState.value = _uiState.value.copy(
                    isRouteLoading = false,
                    isRerouting = false
                )

            } else {

                applyStraightLineFallback(
                    origin,
                    destination,
                    currentState.travelMode,
                    (outcome as? com.histoury.app.data.repository.DirectionsOutcome.Failure)?.reason
                        ?: com.histoury.app.data.repository.DirectionsFailure.UNAVAILABLE
                )

                // No real maneuver data for an estimated straight-line leg —
                // don't let voice guidance speak turns that don't exist.
                applyRouteStepsForVoiceGuidance(emptyList(), currentState.travelMode)

                refreshNavInstruction()
            }

            } finally {
                // Never leave "Calculating Route" up: an exception, or a
                // cancel not followed by a newer request, used to strand it.
                if (routeRequestJob === thisJob || routeRequestJob?.isActive != true) {
                    if (_uiState.value.isRouteLoading) {
                        _uiState.value = _uiState.value.copy(isRouteLoading = false)
                    }
                }
            }
        }
    }

    /**
     * Hands a freshly fetched route's steps to [routeStepTracker], but only
     * resets tracked progress when the maneuver sequence actually changed
     * from what's already tracked. [fetchWalkingRouteIfReady] re-runs every
     * ~20s while navigating, and most of those refreshes return the exact
     * same route — resetting on every one of them would re-announce an
     * instruction that was already spoken moments earlier. A genuinely
     * different sequence (a real reroute after leaving the expected path)
     * still resets tracking so voice guidance follows the new route.
     */
    private fun applyRouteStepsForVoiceGuidance(steps: List<RouteStep>, travelMode: TravelMode) {

        // Applies even when the maneuver sequence itself hasn't changed, so
        // switching travel mode mid-route (e.g. walking to driving) doesn't
        // leave the previous mode's announce distances in effect.
        routeStepTracker.updateTravelMode(travelMode)

        val newSignature = steps.map { it.maneuver }

        if (newSignature == lastAppliedManeuverSignature) {
            return
        }

        lastAppliedManeuverSignature = newSignature
        lastAppliedManeuverSteps = steps

        routeStepTracker.setRoute(steps, travelMode)
    }

    /** "Try again" on the no-route message. */
    fun retryRoute() {
        lastRouteRefreshAtMs = System.currentTimeMillis()
        fetchWalkingRouteIfReady()
    }

    /**
     * No route could be found. Estimates distance and ETA from the straight
     * line, but draws nothing: a straight line across walls and buildings
     * looks exactly like a walking route and isn't one. The route is tried
     * again on the regular 20-second refresh while navigating.
     */
    private fun applyStraightLineFallback(
        origin: LatLng,
        destination: LatLng,
        mode: TravelMode,
        reason: com.histoury.app.data.repository.DirectionsFailure
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

        secondsPerMeter = 1.0 / speedMetersPerSecond

        _uiState.value = _uiState.value.copy(
            isRouteLoading = false,
            isRerouting = false,
            distanceMeters = distanceMeters,
            etaMinutes = etaMinutes,
            routePoints = emptyList(),
            remainingRoutePoints = emptyList(),
            isRouteEstimated = true,
            routeTargetLatitude = null,
            routeTargetLongitude = null,
            entranceName = null,
            routeAttribution = null,
            errorMessage = when (reason) {
                com.histoury.app.data.repository.DirectionsFailure.NO_ROUTE ->
                    "No $modeLabel route could be found to this place. " +
                        "Distance and time are straight-line estimates."
                else ->
                    "Couldn't load a $modeLabel route. Check your connection and try again. " +
                        "Distance and time are straight-line estimates."
            }
        )
    }

    override fun onCleared() {
        super.onCleared()
        // The map screen is gone for good, so nothing is navigating any more.
        if (_uiState.value.isNavigating) stopForegroundNavigation()
        routeRequestJob?.cancel()
        stopLiveLocationUpdates()
        // voiceGuidanceManager is a process-wide singleton, not owned by this
        // ViewModel — do not stop() or shutdown() it here. Reaching the
        // destination clears this ViewModel (via the nav pop to the Arrival
        // screen) in the same instant the "you have arrived" line is queued;
        // stopping the engine here would cut that announcement off.
    }
}
