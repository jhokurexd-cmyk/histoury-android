package com.histoury.app.data.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.histoury.app.data.itinerary.ItineraryPlanner
import com.histoury.app.data.itinerary.ActiveItineraryTrip
import com.histoury.app.data.model.HistoricalSite
import com.histoury.app.data.model.Itinerary
import com.histoury.app.data.model.ItineraryStop
import com.histoury.app.data.model.Place
import com.histoury.app.data.repository.HistoricalSiteRepository
import com.histoury.app.data.repository.GeofenceRepository
import com.histoury.app.data.repository.ItineraryRepository
import com.histoury.app.data.repository.PlaceRepository
import com.histoury.app.data.repository.VisitHistoryRepository
import com.histoury.app.data.repository.TravelMode
import com.histoury.app.data.routing.RoutePath
import com.histoury.app.data.routing.RoutePathRepository
import kotlinx.coroutines.Job
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date

enum class BuilderStep {
    SELECT_SITES,
    SELECT_INTERESTS,
    RECOMMENDATIONS,
    ROUTE_OVERVIEW
}

/**
 * What the preview sheet's action button should do, which depends entirely
 * on where the visitor opened it from.
 */
enum class StopPreviewMode {

    /** Step 1 — the button adds or removes the site from the selection. */
    SELECTION,

    /** Step 3 — the button includes or leaves out a recommended place. */
    RECOMMENDATION,

    /** Add-a-Stop sheet — the button appends the stop to the built route. */
    ADD_TO_ROUTE,

    /** Opened from a stop already on the timeline — read-only. */
    VIEW_ONLY
}

/**
 * The stop whose details are currently open. Held as an id rather than the
 * object so the sheet always renders the freshest catalog entry.
 */
data class StopPreviewTarget(
    val refId: String,
    /** "site" or "place" — which catalog list to resolve [refId] against. */
    val refType: String,
    val mode: StopPreviewMode
)

// Default Intramuros center — used as a route-generation fallback if
// location permission isn't granted, so route building never just fails.
private const val FALLBACK_LATITUDE = 14.5896
private const val FALLBACK_LONGITUDE = 120.9760

// 9:00 AM, in minutes past midnight.
private const val DEFAULT_START_MINUTES = 9 * 60

data class ItineraryUiState(

    val isLoadingSavedList: Boolean = false,
    val savedItineraries: List<Itinerary> = emptyList(),

    val isBuilderActive: Boolean = false,
    val builderStep: BuilderStep = BuilderStep.SELECT_SITES,
    val editingItineraryId: String? = null,
    val itineraryName: String = "",

    val isLoadingCatalog: Boolean = false,
    val allSites: List<HistoricalSite> = emptyList(),
    val allPlaces: List<Place> = emptyList(),

    val selectedSiteIds: Set<String> = emptySet(),
    val selectedInterestCategories: Set<String> = emptySet(),

    /**
     * Site slugs the signed-in user has actually arrived at. Used only to
     * word the preview sheet's unlock notice correctly — the arrival gate
     * itself still lives in SiteDetails and the Site Library.
     */
    val visitedSiteSlugs: Set<String> = emptySet(),

    /** Non-null while the details sheet is open over the builder. */
    val previewTarget: StopPreviewTarget? = null,

    val recommendedPlaces: List<Place> = emptyList(),
    val dismissedRecommendationIds: Set<String> = emptySet(),

    val isGeneratingRoute: Boolean = false,
    val generatedStops: List<ItineraryStop> = emptyList(),
    val totalDistanceMeters: Double = 0.0,
    val totalWalkingMinutes: Int = 0,
    val totalVisitMinutes: Int = 0,
    val usedFallbackLocation: Boolean = false,

    // ----- routing -----

    val travelMode: TravelMode = TravelMode.WALKING,

    /** Road-following path for the current stop order, or null before it loads. */
    val routePath: RoutePath? = null,

    val isLoadingRoutePath: Boolean = false,

    /** At least one leg couldn't be routed and is a straight-line estimate. */
    val routeIsApproximate: Boolean = false,

    // ----- schedule -----

    val startTimeMinutes: Int = DEFAULT_START_MINUTES,

    val completedStopIds: Set<String> = emptySet(),

    val itineraryStatus: String = "planned",

    val startedAt: Any? = null,

    val completedAt: Any? = null,

    val deleteAt: Any? = null,

    val schedule: List<ItineraryPlanner.ScheduledStop> = emptyList(),

    val isSaving: Boolean = false,
    val errorMessage: String? = null,

    val showAddStopSheet: Boolean = false
) {

    /** Walking plus time on site — what the trip actually costs the visitor. */
    val totalTripMinutes: Int
        get() = totalWalkingMinutes + totalVisitMinutes

    val finishTimeMinutes: Int
        get() = startTimeMinutes + (schedule.lastOrNull()?.departureMinutesFromStart ?: 0)

    val actualTripMinutes: Int?
        get() {
            fun millis(value: Any?): Long? = when (value) {
                is Timestamp -> value.toDate().time
                is Date -> value.time
                else -> null
            }
            val start = millis(startedAt) ?: return null
            val finish = millis(completedAt) ?: return null
            if (finish < start) return null
            return ((finish - start) / 60_000L).coerceAtLeast(1L).toInt()
        }
}

class ItineraryViewModel(application: Application) : AndroidViewModel(application) {

    private val itineraryRepository = ItineraryRepository()
    private val historicalSiteRepository = HistoricalSiteRepository()
    private val geofenceRepository = GeofenceRepository()
    private val placeRepository = PlaceRepository()
    private val visitHistoryRepository = VisitHistoryRepository()
    private val routePathRepository = RoutePathRepository()

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)

    /** Cancelled and replaced whenever the stop order or travel mode changes. */
    private var routePathJob: Job? = null

    private val _uiState = MutableStateFlow(ItineraryUiState())
    val uiState: StateFlow<ItineraryUiState> = _uiState.asStateFlow()

    // ----- Saved itineraries list -----

    fun loadSavedItineraries() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingSavedList = true)
            val itineraries = itineraryRepository.getItinerariesForCurrentUser()
            _uiState.value = _uiState.value.copy(
                isLoadingSavedList = false,
                savedItineraries = itineraries
            )
        }
    }

    fun deleteItinerary(documentId: String) {
        viewModelScope.launch {
            if (itineraryRepository.deleteItinerary(documentId)) {
                _uiState.value = _uiState.value.copy(
                    savedItineraries = _uiState.value.savedItineraries.filter {
                        it.documentId != documentId
                    }
                )
            }
        }
    }

    // ----- Entry points -----

    fun startNewItinerary() {

        routePathJob?.cancel()

        _uiState.value = _uiState.value.copy(
            isBuilderActive = true,
            builderStep = BuilderStep.SELECT_SITES,
            editingItineraryId = null,
            itineraryName = "",
            selectedSiteIds = emptySet(),
            selectedInterestCategories = emptySet(),
            recommendedPlaces = emptyList(),
            dismissedRecommendationIds = emptySet(),
            generatedStops = emptyList(),
            totalDistanceMeters = 0.0,
            totalWalkingMinutes = 0,
            totalVisitMinutes = 0,
            travelMode = TravelMode.WALKING,
            routePath = null,
            isLoadingRoutePath = false,
            routeIsApproximate = false,
            startTimeMinutes = DEFAULT_START_MINUTES,
            completedStopIds = emptySet(),
            itineraryStatus = "planned",
            startedAt = null,
            completedAt = null,
            deleteAt = null,
            schedule = emptyList(),
            errorMessage = null,
            previewTarget = null
        )

        loadCatalogIfNeeded()
    }

    /** Opens a saved itinerary straight into the route overview, ready to start, edit, or delete. */
    fun openSavedItinerary(itinerary: Itinerary) {

        val mode = TravelMode.values()
            .firstOrNull { it.wireValue == itinerary.travelMode }
            ?: TravelMode.WALKING

        val stops = itinerary.stops.sortedBy { it.order }

        _uiState.value = _uiState.value.copy(
            isBuilderActive = true,
            builderStep = BuilderStep.ROUTE_OVERVIEW,
            editingItineraryId = itinerary.documentId,
            itineraryName = itinerary.name,
            generatedStops = stops,
            totalDistanceMeters = itinerary.totalDistanceMeters,
            totalWalkingMinutes = itinerary.totalWalkingMinutes,
            totalVisitMinutes = itinerary.totalVisitMinutes,
            travelMode = mode,
            routePath = null,
            routeIsApproximate = false,
            startTimeMinutes = itinerary.startTimeMinutes,
            completedStopIds = itinerary.completedStopIds.toSet(),
            itineraryStatus = itinerary.status,
            startedAt = itinerary.startedAt,
            completedAt = itinerary.completedAt,
            deleteAt = itinerary.deleteAt,
            schedule = ItineraryPlanner.buildSchedule(stops, null),
            errorMessage = null,
            previewTarget = null
        )

        loadCatalogIfNeeded()

        // The saved document stores totals, not the path itself — polylines
        // are large and go stale when the routing data changes. Re-fetching
        // is cheap after the first time thanks to the leg cache.
        refreshRoutePath()
    }

    fun closeBuilder() {
        routePathJob?.cancel()
        _uiState.value = _uiState.value.copy(isBuilderActive = false)
    }

    private fun loadCatalogIfNeeded() {

        val current = _uiState.value

        if (current.allSites.isNotEmpty() || current.allPlaces.isNotEmpty() || current.isLoadingCatalog) {
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingCatalog = true)
            val sites = historicalSiteRepository.getHistoricalSites()
            val places = placeRepository.getPlaces()

            // Same source of truth the Site Library unlocks from, so the
            // preview sheet can never claim a site is locked when the
            // visitor has already been there.
            val visitedSlugs = visitHistoryRepository
                .getVisitHistoryForCurrentUser()
                .map { visit -> visit.siteId }
                .toSet()

            // Older site documents keep their usable coordinates only in
            // geofences. Enrich them here so selection, recommendations and
            // route generation all operate on the same complete catalog.
            val latestGeofencesBySite = geofenceRepository.getAllActiveGeofences()
                .groupBy { it.siteId }
                .mapValues { (_, geofences) ->
                    geofences.maxByOrNull { geofence ->
                        (geofence.updatedAt as? Timestamp)?.seconds
                            ?: (geofence.createdAt as? Timestamp)?.seconds
                            ?: Long.MIN_VALUE
                    }
                }

            val routableSites = sites.map { site ->
                if (site.latitude != null && site.longitude != null) {
                    site
                } else {
                    val geofence = latestGeofencesBySite[site.siteId]
                    if (geofence != null) {
                        site.copy(
                            latitude = geofence.latitude,
                            longitude = geofence.longitude
                        )
                    } else {
                        site
                    }
                }
            }

            _uiState.value = _uiState.value.copy(
                isLoadingCatalog = false,
                allSites = routableSites,
                allPlaces = places,
                visitedSiteSlugs = visitedSlugs
            )
        }
    }

    // ----- Step 1: sites -----

    fun toggleSiteSelected(siteId: String) {
        val current = _uiState.value.selectedSiteIds
        _uiState.value = _uiState.value.copy(
            selectedSiteIds = if (siteId in current) current - siteId else current + siteId
        )
    }

    fun goToInterestsStep() {
        if (_uiState.value.selectedSiteIds.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Pick at least one historical site to build around."
            )
            return
        }
        _uiState.value = _uiState.value.copy(
            builderStep = BuilderStep.SELECT_INTERESTS,
            errorMessage = null
        )
    }

    // ----- Step 2: interests -----

    fun toggleInterestCategory(category: String) {
        val current = _uiState.value.selectedInterestCategories
        _uiState.value = _uiState.value.copy(
            selectedInterestCategories = if (category in current) current - category else current + category
        )
    }

    fun goToRecommendationsStep() {

        val state = _uiState.value

        val selectedSites = state.allSites.filter { it.documentId in state.selectedSiteIds }

        val recommendations = ItineraryPlanner.recommendPlaces(
            selectedSites = selectedSites,
            selectedCategories = state.selectedInterestCategories,
            allPlaces = state.allPlaces,
            excludeIds = state.selectedSiteIds
        )

        _uiState.value = state.copy(
            builderStep = BuilderStep.RECOMMENDATIONS,
            recommendedPlaces = recommendations,
            dismissedRecommendationIds = emptySet()
        )
    }

    fun toggleRecommendationDismissed(placeId: String) {
        val current = _uiState.value.dismissedRecommendationIds
        _uiState.value = _uiState.value.copy(
            dismissedRecommendationIds = if (placeId in current) current - placeId else current + placeId
        )
    }

    fun backToSites() {
        _uiState.value = _uiState.value.copy(builderStep = BuilderStep.SELECT_SITES)
    }

    fun backToInterests() {
        _uiState.value = _uiState.value.copy(builderStep = BuilderStep.SELECT_INTERESTS)
    }

    // ----- Step 4: route generation -----

    @SuppressLint("MissingPermission")
    fun generateRoute() {

        val state = _uiState.value

        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(isGeneratingRoute = true, errorMessage = null)

            val selectedSites = state.allSites.filter { it.documentId in state.selectedSiteIds }

            val keptRecommendations = state.recommendedPlaces.filter {
                it.documentId !in state.dismissedRecommendationIds
            }

            val siteStops = selectedSites.mapNotNull { site ->
                val lat = site.latitude ?: return@mapNotNull null
                val lng = site.longitude ?: return@mapNotNull null
                ItineraryStop(
                    refId = site.documentId,
                    refType = "site",
                    name = site.siteName,
                    subtitle = site.location,
                    imageUrl = site.featuredImage,
                    latitude = lat,
                    longitude = lng,
                    category = "historical",
                    estimatedVisitMinutes = ItineraryPlanner.defaultVisitMinutes("site"),
                    isRecommended = false
                )
            }

            val placeStops = keptRecommendations.mapNotNull { place ->
                val lat = place.latitude ?: return@mapNotNull null
                val lng = place.longitude ?: return@mapNotNull null
                ItineraryStop(
                    refId = place.documentId,
                    refType = "place",
                    name = place.name,
                    subtitle = place.location,
                    imageUrl = place.featuredImage,
                    latitude = lat,
                    longitude = lng,
                    category = place.category,
                    estimatedVisitMinutes = ItineraryPlanner.defaultVisitMinutes("place"),
                    isRecommended = true
                )
            }

            val allStops = siteStops + placeStops

            if (allStops.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    isGeneratingRoute = false,
                    errorMessage = "The selected stops don't have usable map coordinates yet."
                )
                return@launch
            }

            val start = resolveStartPoint()

            val optimized = ItineraryPlanner.optimizeRoute(start.latitude, start.longitude, allStops)

            _uiState.value = _uiState.value.copy(
                isGeneratingRoute = false,
                builderStep = BuilderStep.ROUTE_OVERVIEW,
                generatedStops = optimized.orderedStops,
                totalDistanceMeters = optimized.totalDistanceMeters,
                totalWalkingMinutes = optimized.totalWalkingMinutes,
                totalVisitMinutes = optimized.totalVisitMinutes,
                usedFallbackLocation = start.isFallback,
                schedule = ItineraryPlanner.buildSchedule(optimized.orderedStops, null),
                itineraryName = state.itineraryName.ifBlank {
                    "Intramuros Trip \u2014 ${selectedSites.firstOrNull()?.siteName ?: "My Route"}"
                }
            )

            refreshRoutePath()
        }
    }

    /**
     * Re-runs the optimizer over the current stops. Exposed as "Optimize
     * order" on the overview — a deliberate action, never automatic, so a
     * user who has hand-arranged their day doesn't have it silently undone.
     */
    fun optimizeStopOrder() {

        val state = _uiState.value

        if (state.generatedStops.size < 2) return

        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(isGeneratingRoute = true)

            val start = resolveStartPoint()

            val optimized = ItineraryPlanner.optimizeRoute(
                start.latitude,
                start.longitude,
                state.generatedStops
            )

            _uiState.value = _uiState.value.copy(
                isGeneratingRoute = false,
                generatedStops = optimized.orderedStops,
                totalDistanceMeters = optimized.totalDistanceMeters,
                totalWalkingMinutes = optimized.totalWalkingMinutes,
                totalVisitMinutes = optimized.totalVisitMinutes,
                usedFallbackLocation = start.isFallback,
                routePath = null,
                schedule = ItineraryPlanner.buildSchedule(optimized.orderedStops, null)
            )

            refreshRoutePath()
        }
    }

    /** Kept for older call sites; same behaviour as [optimizeStopOrder]. */
    fun regenerateRoute() = optimizeStopOrder()

    /** Saves first, then starts or resumes at the first incomplete stop. */
    fun beginTrip(onReady: (String) -> Unit) {
        val currentState = _uiState.value
        if (currentState.generatedStops.isEmpty() || currentState.isSaving) return

        // Starting an already completed itinerary means taking it again.
        val resetState = if (
            currentState.generatedStops.all { it.refId in currentState.completedStopIds }
        ) {
            currentState.copy(
                completedStopIds = emptySet(),
                itineraryStatus = "planned",
                startedAt = null,
                completedAt = null,
                deleteAt = null
            )
        } else {
            currentState
        }

        val state = resetState.copy(
            itineraryStatus = "in_progress",
            startedAt = resetState.startedAt ?: Date()
        )

        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true, errorMessage = null)

            val itinerary = itineraryFrom(state)
            val documentId = if (state.editingItineraryId != null) {
                if (itineraryRepository.updateItinerary(state.editingItineraryId, itinerary)) {
                    state.editingItineraryId
                } else {
                    null
                }
            } else {
                itineraryRepository.createItinerary(itinerary)
            }

            if (documentId == null) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = "Couldn't save this itinerary before starting."
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                isSaving = false,
                editingItineraryId = documentId,
                itineraryStatus = "in_progress"
            )

            ActiveItineraryTrip.start(
                documentId = documentId,
                name = state.itineraryName,
                orderedStops = state.generatedStops,
                completedStopIds = state.completedStopIds,
                startedAtMillis = timestampMillis(state.startedAt) ?: System.currentTimeMillis()
            )?.refId?.let(onReady)
        }
    }

    // ----- Manual editing on the overview -----

    fun moveStopUp(index: Int) {
        if (index <= 0) return
        applyReorder(ItineraryPlanner.moveStop(_uiState.value.generatedStops, index, index - 1))
    }

    fun moveStopDown(index: Int) {
        val stops = _uiState.value.generatedStops
        if (index >= stops.lastIndex) return
        applyReorder(ItineraryPlanner.moveStop(stops, index, index + 1))
    }

    /**
     * Commits a drag-to-reorder in one step.
     *
     * The timeline previews the new order locally while a stop is being
     * dragged and only calls this on release, because every reorder throws
     * away the road-following path and refetches it — doing that on each
     * position the finger passes over would fire a burst of Routes API
     * calls for orders the visitor never actually chose.
     */
    fun moveStop(fromIndex: Int, toIndex: Int) {

        val stops = _uiState.value.generatedStops

        if (fromIndex !in stops.indices || toIndex !in stops.indices) return
        if (fromIndex == toIndex) return

        applyReorder(ItineraryPlanner.moveStop(stops, fromIndex, toIndex))
    }

    fun setVisitMinutes(refId: String, minutes: Int) {

        val updated = _uiState.value.generatedStops.map { stop ->
            if (stop.refId == refId) stop.copy(estimatedVisitMinutes = minutes) else stop
        }

        // Only time on site changed, so the path and its distances still
        // hold — no refetch, just new arrival times.
        _uiState.value = _uiState.value.copy(
            generatedStops = updated,
            totalVisitMinutes = updated.sumOf { it.estimatedVisitMinutes },
            schedule = ItineraryPlanner.buildSchedule(updated, _uiState.value.routePath)
        )
    }

    fun setTravelMode(mode: TravelMode) {

        if (_uiState.value.travelMode == mode) return

        _uiState.value = _uiState.value.copy(travelMode = mode, routePath = null)

        refreshRoutePath()
    }

    fun setStartTimeMinutes(minutes: Int) {
        _uiState.value = _uiState.value.copy(
            startTimeMinutes = minutes.coerceIn(0, 23 * 60 + 59)
        )
    }

    private fun applyReorder(reordered: List<ItineraryStop>) {

        _uiState.value = _uiState.value.copy(
            generatedStops = reordered,
            routePath = null,
            schedule = ItineraryPlanner.buildSchedule(reordered, null)
        )

        refreshRoutePath()
    }

    // ----- Road-following path -----

    /**
     * Asks the Routes API for the real path between consecutive stops and
     * folds the measured distance and duration back into the totals.
     *
     * Everything on screen is already usable before this returns — the
     * straight-line estimate fills in immediately — so this only ever
     * upgrades the display, and a failure leaves the estimate in place.
     */
    private fun refreshRoutePath() {

        routePathJob?.cancel()

        val stops = _uiState.value.generatedStops

        if (stops.size < 2) {
            _uiState.value = _uiState.value.copy(
                routePath = null,
                isLoadingRoutePath = false,
                routeIsApproximate = false,
                schedule = ItineraryPlanner.buildSchedule(stops, null)
            )
            return
        }

        val mode = _uiState.value.travelMode

        routePathJob = viewModelScope.launch {

            _uiState.value = _uiState.value.copy(isLoadingRoutePath = true)

            val path = routePathRepository.getPath(
                orderedPoints = stops.map { LatLng(it.latitude, it.longitude) },
                mode = mode
            )

            val current = _uiState.value

            // The user may have reordered while this was in flight.
            if (current.generatedStops != stops || current.travelMode != mode) {
                return@launch
            }

            val measured = ItineraryPlanner.withMeasuredTotals(stops, path)

            _uiState.value = current.copy(
                isLoadingRoutePath = false,
                routePath = path,
                routeIsApproximate = path.hasApproximateLegs,
                totalDistanceMeters = measured.totalDistanceMeters,
                totalWalkingMinutes = measured.totalWalkingMinutes,
                totalVisitMinutes = measured.totalVisitMinutes,
                schedule = ItineraryPlanner.buildSchedule(stops, path)
            )
        }
    }

    private data class StartPoint(
        val latitude: Double,
        val longitude: Double,
        val isFallback: Boolean
    )

    @SuppressLint("MissingPermission")
    private suspend fun resolveStartPoint(): StartPoint {

        return try {

            val lastLocation = fusedLocationClient.lastLocation.await()

            if (lastLocation != null) {
                StartPoint(lastLocation.latitude, lastLocation.longitude, false)
            } else {
                StartPoint(FALLBACK_LATITUDE, FALLBACK_LONGITUDE, true)
            }

        } catch (e: Exception) {
            StartPoint(FALLBACK_LATITUDE, FALLBACK_LONGITUDE, true)
        }
    }

    fun onNameChange(name: String) {
        _uiState.value = _uiState.value.copy(itineraryName = name)
    }

    fun removeStop(refId: String) {

        val updated = _uiState.value.generatedStops
            .filter { it.refId != refId }
            .mapIndexed { index, stop -> stop.copy(order = index) }

        applyReorder(updated)

        _uiState.value = _uiState.value.copy(
            totalVisitMinutes = updated.sumOf { it.estimatedVisitMinutes }
        )
    }

    // ----- Stop details preview -----

    /**
     * Opens the details sheet for a site or place without disturbing the
     * step the visitor is on — picking stops shouldn't mean navigating away
     * and losing a half-made selection.
     */
    fun openStopPreview(refId: String, refType: String, mode: StopPreviewMode) {

        if (refId.isBlank()) return

        _uiState.value = _uiState.value.copy(
            previewTarget = StopPreviewTarget(
                refId = refId,
                refType = refType,
                mode = mode
            ),
            // Two modal sheets stacked on top of each other fight over
            // dismissal gestures, so the Add-a-Stop sheet steps aside while
            // the preview is up and comes back when it closes.
            showAddStopSheet = false
        )
    }

    fun closeStopPreview() {

        val mode = _uiState.value.previewTarget?.mode

        _uiState.value = _uiState.value.copy(
            previewTarget = null,
            // Backing out of a preview should return the visitor to the list
            // they were browsing, not to the timeline.
            showAddStopSheet = mode == StopPreviewMode.ADD_TO_ROUTE
        )
    }

    /**
     * Runs the sheet's action button. Each mode maps onto the same function
     * the underlying card's tap already calls, so previewing a stop and
     * picking it from the grid stay in lockstep.
     */
    fun confirmStopPreviewAction() {

        val state = _uiState.value
        val target = state.previewTarget ?: return

        when (target.mode) {

            StopPreviewMode.SELECTION -> {
                toggleSiteSelected(target.refId)
            }

            StopPreviewMode.RECOMMENDATION -> {
                toggleRecommendationDismissed(target.refId)
            }

            StopPreviewMode.ADD_TO_ROUTE -> {

                if (target.refType == "site") {
                    state.allSites
                        .firstOrNull { it.documentId == target.refId }
                        ?.let { addStopFromSite(it) }
                } else {
                    state.allPlaces
                        .firstOrNull { it.documentId == target.refId }
                        ?.let { addStopFromPlace(it) }
                }

                // The Add-a-Stop sheet has served its purpose once a stop is
                // added; leaving it open would hide the timeline the visitor
                // just changed.
                _uiState.value = _uiState.value.copy(showAddStopSheet = false)
            }

            StopPreviewMode.VIEW_ONLY -> Unit
        }

        _uiState.value = _uiState.value.copy(previewTarget = null)
    }

    fun openAddStopSheet() {
        _uiState.value = _uiState.value.copy(showAddStopSheet = true)
        loadCatalogIfNeeded()
    }

    fun closeAddStopSheet() {
        _uiState.value = _uiState.value.copy(showAddStopSheet = false)
    }

    fun addStopFromSite(site: HistoricalSite) {

        val lat = site.latitude ?: return
        val lng = site.longitude ?: return

        if (_uiState.value.generatedStops.any { it.refId == site.documentId }) return

        val newStop = ItineraryStop(
            refId = site.documentId,
            refType = "site",
            name = site.siteName,
            subtitle = site.location,
            imageUrl = site.featuredImage,
            latitude = lat,
            longitude = lng,
            category = "historical",
            estimatedVisitMinutes = ItineraryPlanner.defaultVisitMinutes("site"),
            order = _uiState.value.generatedStops.size
        )

        addStop(newStop)
    }

    fun addStopFromPlace(place: Place) {

        val lat = place.latitude ?: return
        val lng = place.longitude ?: return

        if (_uiState.value.generatedStops.any { it.refId == place.documentId }) return

        val newStop = ItineraryStop(
            refId = place.documentId,
            refType = "place",
            name = place.name,
            subtitle = place.location,
            imageUrl = place.featuredImage,
            latitude = lat,
            longitude = lng,
            category = place.category,
            estimatedVisitMinutes = ItineraryPlanner.defaultVisitMinutes("place"),
            order = _uiState.value.generatedStops.size,
            isRecommended = false
        )

        addStop(newStop)
    }

    private fun addStop(newStop: ItineraryStop) {

        val updated = _uiState.value.generatedStops + newStop

        _uiState.value = _uiState.value.copy(
            generatedStops = updated,
            totalVisitMinutes = updated.sumOf { it.estimatedVisitMinutes },
            routePath = null,
            schedule = ItineraryPlanner.buildSchedule(updated, null),
            showAddStopSheet = false
        )

        // A new stop is appended at the end, which is rarely where it
        // belongs geographically — but reordering is the user's call, so we
        // only route what they currently have.
        refreshRoutePath()
    }

    // ----- Saving -----

    private fun itineraryFrom(state: ItineraryUiState) = Itinerary(
        name = state.itineraryName.ifBlank { "My Intramuros Trip" },
        stops = state.generatedStops,
        totalDistanceMeters = state.totalDistanceMeters,
        totalWalkingMinutes = state.totalWalkingMinutes,
        totalVisitMinutes = state.totalVisitMinutes,
        travelMode = state.travelMode.wireValue,
        startTimeMinutes = state.startTimeMinutes,
        completedStopIds = state.completedStopIds.toList(),
        status = state.itineraryStatus,
        startedAt = state.startedAt,
        completedAt = state.completedAt,
        deleteAt = state.deleteAt
    )

    private fun timestampMillis(value: Any?): Long? = when (value) {
        is Timestamp -> value.toDate().time
        is Date -> value.time
        else -> null
    }

    fun saveItinerary(onSaved: () -> Unit) {

        val state = _uiState.value

        if (state.generatedStops.isEmpty()) {
            _uiState.value = state.copy(errorMessage = "Add at least one stop before saving.")
            return
        }

        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)

            val itinerary = itineraryFrom(state)

            val success = if (state.editingItineraryId != null) {
                itineraryRepository.updateItinerary(state.editingItineraryId, itinerary)
            } else {
                itineraryRepository.createItinerary(itinerary) != null
            }

            if (success) {
                _uiState.value = _uiState.value.copy(isSaving = false, isBuilderActive = false)
                loadSavedItineraries()
                onSaved()
            } else {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = "Couldn't save this itinerary. Please try again."
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
