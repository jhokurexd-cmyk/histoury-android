package com.histoury.app.data.viewmodel

import com.histoury.app.data.offline.TripPrep
import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.histoury.app.data.itinerary.ItineraryPlanner
import com.histoury.app.data.itinerary.ActiveItineraryTrip
import com.histoury.app.data.model.HistoricalSite
import com.histoury.app.data.model.Itinerary
import com.histoury.app.data.model.ItineraryStatus
import com.histoury.app.data.model.ItineraryStop
import com.histoury.app.data.model.Place
import com.histoury.app.data.model.StopStatus
import com.histoury.app.data.repository.HistoricalSiteRepository
import com.histoury.app.data.repository.GeofenceRepository
import com.histoury.app.data.repository.ItineraryRepository
import com.histoury.app.data.repository.PlaceRepository
import com.histoury.app.data.repository.VisitHistoryRepository
import com.histoury.app.data.repository.TravelMode
import com.histoury.app.data.routing.RoutePath
import com.histoury.app.data.routing.RoutePathRepository
import com.histoury.app.util.VoiceGuidanceManager
import kotlinx.coroutines.Job
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

    /**
     * True when the phone had no internet the last time the saved list
     * loaded. An empty list then means "couldn't check", not "you have no
     * itineraries" — the screen says so instead of inviting the visitor to
     * start over.
     */
    val isOffline: Boolean = false,

    // The saved list couldn't be read at all (as opposed to "no trips").
    val savedListLoadFailed: Boolean = false,

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

    /**
     * Recommended places the visitor has opted INTO. Recommendations start
     * unselected — they're suggestions, and the visitor picks which ones
     * they actually want to go to.
     */
    val selectedRecommendationIds: Set<String> = emptySet(),

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

    val itineraryStatus: String = ItineraryStatus.PLANNED,

    val startedAt: Any? = null,

    val completedAt: Any? = null,

    val deleteAt: Any? = null,

    val schedule: List<ItineraryPlanner.ScheduledStop> = emptyList(),

    val isSaving: Boolean = false,
    val errorMessage: String? = null,

    /** Neutral feedback, e.g. the result of "Optimize Order". Auto-dismissed by the UI. */
    val infoMessage: String? = null,

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

class ItineraryViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val itineraryRepository = ItineraryRepository()
    private val historicalSiteRepository = HistoricalSiteRepository()
    private val geofenceRepository = GeofenceRepository()
    private val placeRepository = PlaceRepository()
    private val visitHistoryRepository = VisitHistoryRepository()
    private val routePathRepository = RoutePathRepository()

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)

    /** Cancelled and replaced whenever the stop order or travel mode changes. */
    private var routePathJob: Job? = null

    /**
     * What the open itinerary looked like when it was last loaded or saved —
     * name, travel mode, start time, stop order and visit lengths. Null for
     * a brand-new, never-saved route. Compared against the live state to
     * tell whether leaving the overview would lose anything.
     */
    private var savedFingerprint: String? = null

    private fun fingerprint(state: ItineraryUiState): String = buildString {
        append(state.itineraryName.trim()).append('|')
        append(state.travelMode.wireValue).append('|')
        append(state.startTimeMinutes)
        state.generatedStops.forEach { stop ->
            append('|').append(stop.refType).append(':').append(stop.refId)
            append('@').append(stop.estimatedVisitMinutes)
        }
    }

    /** True when the route overview holds edits (or a new route) that haven't been saved. */
    fun hasUnsavedChanges(): Boolean {
        val state = _uiState.value
        if (!state.isBuilderActive || state.builderStep != BuilderStep.ROUTE_OVERVIEW) return false
        if (state.generatedStops.isEmpty()) return false
        return savedFingerprint != fingerprint(state)
    }

    /**
     * Folds the latest per-stop progress from Firestore into [state].
     *
     * Stop statuses change outside this screen — Arrival marks a stop
     * completed, Map's "Skip" marks it skipped — while this ViewModel keeps
     * the copy it loaded earlier. Writing that stale copy back (Save Changes,
     * Resume Trip) used to silently undo that progress. Local edits (order,
     * visit lengths, name) are kept; only progress comes from the server.
     */
    private suspend fun withServerProgress(state: ItineraryUiState): ItineraryUiState {
        val id = state.editingItineraryId ?: return state
        val server = itineraryRepository.getItineraryById(id) ?: return state
        val serverStatus = server.stops.associate { "${it.refType}:${it.refId}" to it.status }
        return state.copy(
            generatedStops = state.generatedStops.map { stop ->
                serverStatus["${stop.refType}:${stop.refId}"]?.let { stop.copy(status = it) } ?: stop
            },
            itineraryStatus = server.status,
            startedAt = server.startedAt ?: state.startedAt,
            completedAt = server.completedAt,
            deleteAt = server.deleteAt
        )
    }

    /**
     * Called when the Itinerary tab is shown again: brings an open trip's
     * progress (Done badges, Resume vs Start) up to date with what happened
     * on the Map/Arrival screens in the meantime.
     */
    fun refreshOpenItineraryProgress() {
        val before = _uiState.value
        if (!before.isBuilderActive || before.builderStep != BuilderStep.ROUTE_OVERVIEW) return
        if (before.editingItineraryId == null || before.isSaving) return

        viewModelScope.launch {
            val merged = withServerProgress(_uiState.value)
            val current = _uiState.value
            // Only apply if the visitor hasn't switched itineraries meanwhile.
            if (current.editingItineraryId != merged.editingItineraryId) return@launch
            val statusByKey = merged.generatedStops.associate { "${it.refType}:${it.refId}" to it.status }
            val updatedStops = current.generatedStops.map { stop ->
                statusByKey["${stop.refType}:${stop.refId}"]?.let { stop.copy(status = it) } ?: stop
            }
            _uiState.value = current.copy(
                generatedStops = updatedStops,
                itineraryStatus = merged.itineraryStatus,
                startedAt = merged.startedAt,
                completedAt = merged.completedAt,
                deleteAt = merged.deleteAt,
                schedule = ItineraryPlanner.buildSchedule(updatedStops, current.routePath)
            )
        }
    }

    private val _uiState = MutableStateFlow(restoredWizardState())
    val uiState: StateFlow<ItineraryUiState> = _uiState.asStateFlow()

    init {
        // Mirror the wizard's selections into the saved state so a new,
        // not-yet-saved itinerary survives the app being killed in the
        // background — the visitor's picks are only dropped when they
        // cancel, save or start the trip themselves.
        viewModelScope.launch {
            _uiState.collect { state -> persistWizardState(state) }
        }
        if (_uiState.value.isBuilderActive) loadCatalogIfNeeded()
    }

    private fun persistWizardState(state: ItineraryUiState) {
        // Only an unsaved new build needs this; a saved itinerary is
        // reopened from Firestore.
        val keep = state.isBuilderActive && state.editingItineraryId == null
        savedStateHandle[KEY_WIZARD_ACTIVE] = keep
        if (!keep) return
        savedStateHandle[KEY_WIZARD_STEP] = state.builderStep.name
        savedStateHandle[KEY_WIZARD_SITES] = ArrayList(state.selectedSiteIds)
        savedStateHandle[KEY_WIZARD_INTERESTS] = ArrayList(state.selectedInterestCategories)
        savedStateHandle[KEY_WIZARD_RECOMMENDATIONS] = ArrayList(state.selectedRecommendationIds)
        savedStateHandle[KEY_WIZARD_NAME] = state.itineraryName
    }

    private fun restoredWizardState(): ItineraryUiState {
        if (savedStateHandle.get<Boolean>(KEY_WIZARD_ACTIVE) != true) return ItineraryUiState()
        val step = savedStateHandle.get<String>(KEY_WIZARD_STEP)
            ?.let { name -> BuilderStep.values().firstOrNull { it.name == name } }
            ?: BuilderStep.SELECT_SITES
        return ItineraryUiState(
            isBuilderActive = true,
            // Recommendations and the built route are derived from the
            // catalog, which isn't loaded yet after a restore, so resume at
            // the last step that only needs the saved selections.
            builderStep = if (step == BuilderStep.SELECT_SITES) step else BuilderStep.SELECT_INTERESTS,
            selectedSiteIds = savedStateHandle.get<ArrayList<String>>(KEY_WIZARD_SITES).orEmpty().toSet(),
            selectedInterestCategories = savedStateHandle.get<ArrayList<String>>(KEY_WIZARD_INTERESTS).orEmpty().toSet(),
            selectedRecommendationIds = savedStateHandle.get<ArrayList<String>>(KEY_WIZARD_RECOMMENDATIONS).orEmpty().toSet(),
            itineraryName = savedStateHandle.get<String>(KEY_WIZARD_NAME).orEmpty()
        )
    }

    // ----- Saved itineraries list -----

    /**
     * Opens a saved itinerary by id — for entry points (Home's "Resume Trip")
     * that know which trip but don't hold the loaded object.
     */
    fun openSavedItineraryById(documentId: String) {
        viewModelScope.launch {
            val itinerary = _uiState.value.savedItineraries.firstOrNull { it.documentId == documentId }
                ?: itineraryRepository.getItineraryById(documentId)
            if (itinerary != null) {
                openSavedItinerary(itinerary)
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "That trip couldn't be loaded. Check your connection and try again."
                )
            }
        }
    }

    /**
     * [autoResume]: if true, and the builder isn't already open, an
     * IN_PROGRESS or PAUSED itinerary is reopened straight into its route
     * overview instead of leaving the visitor on the plain saved list —
     * openSavedItinerary resumes at the first incomplete stop, never back
     * at Stop 1.
     *
     * NOT wired to opening the Itinerary tab (see ExploreScreen) — it used
     * to be, but that meant every tap of the tab's nav-bar icon jumped
     * straight past the saved list into whichever trip was IN_PROGRESS/
     * PAUSED, with no way to actually see or manage the list (bulk-select,
     * sort, delete) while a trip was active. Resuming a trip is reachable
     * instead through Home's "Continue Your Trip" card, or by tapping that
     * itinerary's own card in the list, both of which call
     * [openSavedItinerary] directly. Saving a draft still calls this (with
     * the default false) purely to refresh the list.
     */
    fun loadSavedItineraries(autoResume: Boolean = false) {
        viewModelScope.launch {
            // Full-screen spinner only when there's nothing to show yet;
            // returning to the tab refreshes the list in place.
            if (_uiState.value.savedItineraries.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoadingSavedList = true)
            }
            // null = couldn't be read at all (no connection and nothing
            // cached yet). Keep whatever is already listed rather than
            // blanking it — a failed refresh isn't "you have no trips".
            val loaded = itineraryRepository.loadItinerariesForCurrentUser()
            val itineraries = loaded ?: _uiState.value.savedItineraries
            _uiState.value = _uiState.value.copy(
                isLoadingSavedList = false,
                savedItineraries = itineraries,
                isOffline = !hasInternet(),
                savedListLoadFailed = loaded == null
            )

            if (autoResume && !_uiState.value.isBuilderActive) {
                itineraries.firstOrNull {
                    it.status == ItineraryStatus.IN_PROGRESS || it.status == ItineraryStatus.PAUSED
                }?.let { openSavedItinerary(it) }
            }
        }
    }

    private fun hasInternet(): Boolean {
        val manager = getApplication<Application>()
            .getSystemService(android.net.ConnectivityManager::class.java) ?: return true
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun deleteItinerary(documentId: String) {
        viewModelScope.launch {
            if (itineraryRepository.deleteItinerary(documentId)) {
                // Deleting the trip that's being navigated ends that session,
                // so Map/Arrival stop treating arrivals as part of it.
                if (ActiveItineraryTrip.isActiveFor(documentId)) ActiveItineraryTrip.clear()
                _uiState.value = _uiState.value.copy(
                    savedItineraries = _uiState.value.savedItineraries.filter {
                        it.documentId != documentId
                    }
                )
            }
        }
    }

    /**
     * Bulk delete from the saved-itineraries list's long-press multi-select
     * ("Select All" / individually checked items). Deletes run concurrently
     * rather than one at a time, and whichever ones actually succeed are
     * removed from the list — one failed delete doesn't block or roll back
     * the rest, same spirit as the single-item [deleteItinerary] just being
     * silently a no-op on failure.
     */
    fun deleteItineraries(documentIds: Set<String>) {
        if (documentIds.isEmpty()) return
        viewModelScope.launch {
            val deletedIds = coroutineScope {
                documentIds
                    .map { id -> async { if (itineraryRepository.deleteItinerary(id)) id else null } }
                    .awaitAll()
                    .filterNotNull()
                    .toSet()
            }
            if (deletedIds.isNotEmpty()) {
                deletedIds.forEach { id ->
                    if (ActiveItineraryTrip.isActiveFor(id)) ActiveItineraryTrip.clear()
                }
                _uiState.value = _uiState.value.copy(
                    savedItineraries = _uiState.value.savedItineraries.filter {
                        it.documentId !in deletedIds
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
            selectedRecommendationIds = emptySet(),
            generatedStops = emptyList(),
            totalDistanceMeters = 0.0,
            totalWalkingMinutes = 0,
            totalVisitMinutes = 0,
            travelMode = TravelMode.WALKING,
            routePath = null,
            isLoadingRoutePath = false,
            routeIsApproximate = false,
            startTimeMinutes = DEFAULT_START_MINUTES,
            itineraryStatus = ItineraryStatus.PLANNED,
            startedAt = null,
            completedAt = null,
            deleteAt = null,
            schedule = emptyList(),
            errorMessage = null,
            infoMessage = null,
            previewTarget = null
        )

        savedFingerprint = null

        loadCatalogIfNeeded()
    }

    /** Opens a saved itinerary straight into the route overview, ready to start, edit, or delete. */
    fun openSavedItinerary(itinerary: Itinerary) {

        val mode = TravelMode.values()
            .firstOrNull { it.wireValue == itinerary.travelMode }
            ?: TravelMode.WALKING

        // distinctBy guards the timeline's LazyColumn, whose keys must be
        // unique — an older document with a repeated stop would crash it.
        val stops = itinerary.stops
            .sortedBy { it.order }
            .distinctBy { "${it.refType}:${it.refId}" }

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
            itineraryStatus = itinerary.status,
            startedAt = itinerary.startedAt,
            completedAt = itinerary.completedAt,
            deleteAt = itinerary.deleteAt,
            schedule = ItineraryPlanner.buildSchedule(stops, null),
            errorMessage = null,
            infoMessage = null,
            previewTarget = null
        )

        savedFingerprint = fingerprint(_uiState.value)

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

        // Nothing is pre-selected. Picks made on an earlier visit to this
        // step are kept, as long as that place is still being recommended.
        val stillRecommended = recommendations.map { it.documentId }.toSet()

        _uiState.value = state.copy(
            builderStep = BuilderStep.RECOMMENDATIONS,
            recommendedPlaces = recommendations,
            selectedRecommendationIds = state.selectedRecommendationIds.intersect(stillRecommended)
        )
    }

    fun toggleRecommendationSelected(placeId: String) {
        val current = _uiState.value.selectedRecommendationIds
        _uiState.value = _uiState.value.copy(
            selectedRecommendationIds = if (placeId in current) current - placeId else current + placeId
        )
    }

    fun backToSites() {
        _uiState.value = _uiState.value.copy(builderStep = BuilderStep.SELECT_SITES)
    }

    fun backToInterests() {
        _uiState.value = _uiState.value.copy(builderStep = BuilderStep.SELECT_INTERESTS)
    }

    /**
     * One step back through the builder — what system Back and every
     * in-screen back arrow do. Selections are kept at every step; only the
     * first step (or a saved itinerary's overview) leaves the builder.
     *
     * Returns false when there's no earlier step, so the caller decides
     * how to leave (the overview asks first if there are unsaved edits).
     */
    fun stepBack(): Boolean {
        val state = _uiState.value
        if (!state.isBuilderActive) return false
        when (state.builderStep) {
            BuilderStep.SELECT_SITES -> return false
            BuilderStep.SELECT_INTERESTS -> backToSites()
            BuilderStep.RECOMMENDATIONS -> backToInterests()
            BuilderStep.ROUTE_OVERVIEW -> {
                // A saved itinerary opens straight at the overview; there
                // is no wizard behind it to go back to.
                if (state.editingItineraryId != null) return false
                routePathJob?.cancel()
                // Step 3 is only reachable with interests picked; without
                // any, the visitor came straight from step 2.
                if (state.selectedInterestCategories.isEmpty()) {
                    backToInterests()
                } else {
                    goToRecommendationsStep()
                }
            }
        }
        return true
    }

    // ----- Step 4: route generation -----

    @SuppressLint("MissingPermission")
    fun generateRoute() {

        val state = _uiState.value

        // "Continue Without Interests" has no spinner of its own, so a quick
        // double tap used to launch two route builds. Set the flag before
        // launching so the second tap is ignored.
        if (state.isGeneratingRoute) return

        _uiState.value = state.copy(isGeneratingRoute = true, errorMessage = null)

        viewModelScope.launch {

            val selectedSites = state.allSites.filter { it.documentId in state.selectedSiteIds }

            // Recommendations only apply when interests are still selected.
            // Visiting step 3, going back, and clearing every interest used
            // to leave the old recommendedPlaces in state, and they were
            // silently added to the route anyway.
            val keptRecommendations = if (state.selectedInterestCategories.isEmpty()) {
                emptyList()
            } else {
                state.recommendedPlaces.filter {
                    it.documentId in state.selectedRecommendationIds
                }
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

            val allStops = (siteStops + placeStops).distinctBy { "${it.refType}:${it.refId}" }

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

    fun clearInfoMessage() {
        _uiState.value = _uiState.value.copy(infoMessage = null)
    }

    /** Saves first, then starts or resumes at the first incomplete stop. */
    fun beginTrip(onReady: (String) -> Unit) {
        val initialState = _uiState.value
        if (initialState.generatedStops.isEmpty() || initialState.isSaving) return

        // Flag the save straight away so a double tap can't start (and
        // write) the trip twice before the coroutine gets going.
        _uiState.value = initialState.copy(isSaving = true, errorMessage = null)

        viewModelScope.launch {

            // Pick up any progress made since this screen loaded the trip.
            val currentState = withServerProgress(_uiState.value)

            // Starting an already-finished itinerary means taking it again —
            // every stop goes back to PENDING rather than staying resolved.
            val alreadyFinished = currentState.generatedStops.isNotEmpty() &&
                currentState.generatedStops.all { StopStatus.isResolved(it.status) }

            val resetState = if (alreadyFinished) {
                currentState.copy(
                    generatedStops = currentState.generatedStops.map { it.copy(status = StopStatus.PENDING) },
                    itineraryStatus = ItineraryStatus.PLANNED,
                    startedAt = null,
                    completedAt = null,
                    deleteAt = null
                )
            } else {
                currentState
            }

            // Resuming = some stops already done on a trip that isn't finished.
            val isResuming = !alreadyFinished &&
                currentState.generatedStops.any { it.status != StopStatus.PENDING }

            val state = resetState.copy(
                itineraryStatus = ItineraryStatus.IN_PROGRESS,
                startedAt = resetState.startedAt ?: Date()
            )

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

            // Created and started in one go: still offer the trip's AR downloads.
            if (state.editingItineraryId == null) {
                TripPrep.onItineraryCreated(
                    getApplication(),
                    state.generatedStops.filter { it.refType == "site" }.map { it.refId }
                )
            }

            // Only one itinerary is ever IN_PROGRESS at a time — a trip
            // already under way gets paused (not lost) rather than left to
            // silently conflict with this one.
            val otherActive = itineraryRepository.getActiveItinerary()
            if (otherActive != null && otherActive.documentId != documentId) {
                itineraryRepository.pauseItinerary(otherActive.documentId)
                if (ActiveItineraryTrip.isActiveFor(otherActive.documentId)) {
                    ActiveItineraryTrip.clear()
                }
            }

            _uiState.value = _uiState.value.copy(
                isSaving = false,
                editingItineraryId = documentId,
                itineraryStatus = ItineraryStatus.IN_PROGRESS
            )

            savedFingerprint = fingerprint(_uiState.value)

            val firstStop = ActiveItineraryTrip.start(
                documentId = documentId,
                name = state.itineraryName,
                orderedStops = state.generatedStops,
                startedAtMillis = timestampMillis(state.startedAt) ?: System.currentTimeMillis()
            ) ?: return@launch

            announceTripStart(state, firstStop, isResuming)

            onReady(firstStop.refId)
        }
    }

    /**
     * Spoken cue that the trip is now live, so the visitor knows it started
     * even with the phone already in their pocket. Uses the same process-wide
     * TTS engine as turn-by-turn guidance, which outlives the screen change
     * to the Map right after, so the line isn't cut off. Fails silently on a
     * device with no TTS engine.
     */
    private fun announceTripStart(
        state: ItineraryUiState,
        firstStop: ItineraryStop,
        isResuming: Boolean
    ) {
        val tripName = state.itineraryName.ifBlank { "your Intramuros trip" }
        val remaining = state.generatedStops.count { !StopStatus.isResolved(it.status) }
        val stopsText = if (remaining == 1) "1 stop" else "$remaining stops"

        val line = if (isResuming) {
            "Welcome back. Resuming $tripName. $stopsText to go. " +
                "Your next stop is ${firstStop.name}."
        } else {
            "Your trip has started. $tripName, with $stopsText planned. " +
                "Your first stop is ${firstStop.name}. Enjoy exploring Intramuros!"
        }

        VoiceGuidanceManager.getInstance(getApplication()).speak(line)
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
    /** "Retry" on the no-route notice. Failed legs aren't cached, so only they are fetched again. */
    fun retryRoutePath() = refreshRoutePath()

    private fun refreshRoutePath() {

        routePathJob?.cancel()

        val stops = _uiState.value.generatedStops

        if (stops.size < 2) {
            // No legs left to walk. The totals used to keep the previous
            // route's distance and time after removing stops down to one.
            _uiState.value = _uiState.value.copy(
                routePath = null,
                isLoadingRoutePath = false,
                routeIsApproximate = false,
                totalDistanceMeters = 0.0,
                totalWalkingMinutes = 0,
                totalVisitMinutes = stops.sumOf { it.estimatedVisitMinutes },
                schedule = ItineraryPlanner.buildSchedule(stops, null)
            )
            return
        }

        val mode = _uiState.value.travelMode

        routePathJob = viewModelScope.launch {

            val thisJob = coroutineContext[kotlinx.coroutines.Job]

            _uiState.value = _uiState.value.copy(isLoadingRoutePath = true)

            val path = try {
                routePathRepository.getPath(
                    orderedPoints = stops.map { LatLng(it.latitude, it.longitude) },
                    mode = mode,
                    siteIds = stops.map { if (it.refType == "site") it.refId else null }
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } finally {
                // The "loading route" pill used to stay on screen whenever
                // this ended any way other than the happy path — an error, a
                // reorder mid-request, or a cancel that no new request
                // replaced. Whoever finishes LAST clears it (a newer request
                // still running keeps it up).
                if (routePathJob === thisJob || routePathJob?.isActive != true) {
                    _uiState.value = _uiState.value.copy(isLoadingRoutePath = false)
                }
            }

            if (path == null) return@launch

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
                toggleRecommendationSelected(target.refId)
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
        completedStopIds = state.generatedStops.filter { it.status == StopStatus.COMPLETED }.map { it.refId },
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

        val initialState = _uiState.value

        // A double tap used to create the same new itinerary twice.
        if (initialState.isSaving) return

        if (initialState.generatedStops.isEmpty()) {
            _uiState.value = initialState.copy(errorMessage = "Add at least one stop before saving.")
            return
        }

        _uiState.value = initialState.copy(isSaving = true, errorMessage = null)

        viewModelScope.launch {

            // Don't overwrite progress recorded elsewhere since this loaded.
            val state = withServerProgress(_uiState.value)

            val itinerary = itineraryFrom(state)

            val savedId = if (state.editingItineraryId != null) {
                state.editingItineraryId.takeIf {
                    itineraryRepository.updateItinerary(it, itinerary)
                }
            } else {
                itineraryRepository.createItinerary(itinerary)
            }
            val success = savedId != null

            if (savedId != null) {
                // Show the saved trip in the list straight away. The refresh
                // below replaces it with the stored copy, but offline (or on a
                // slow connection) that can take a while, and the list used to
                // look as if nothing had been saved.
                val existing = _uiState.value.savedItineraries
                val previous = existing.firstOrNull { it.documentId == savedId }
                val now = Timestamp.now()
                val savedCopy = itinerary.copy(
                    documentId = savedId,
                    userId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty(),
                    createdAt = previous?.createdAt ?: now,
                    updatedAt = now
                )
                _uiState.value = _uiState.value.copy(
                    savedItineraries = if (previous != null) {
                        existing.map { if (it.documentId == savedId) savedCopy else it }
                    } else {
                        existing + savedCopy
                    }
                )
            }

            if (success) {
                // A new itinerary: offer to download the AR of its sites
                // that aren't on the phone yet ("Prepare for your trip").
                if (state.editingItineraryId == null) {
                    TripPrep.onItineraryCreated(
                        getApplication(),
                        state.generatedStops.filter { it.refType == "site" }.map { it.refId }
                    )
                }
                savedFingerprint = fingerprint(state)
                // A trip that's under way keeps navigating the saved order.
                val docId = state.editingItineraryId
                if (docId != null && ActiveItineraryTrip.isActiveFor(docId)) {
                    ActiveItineraryTrip.start(
                        documentId = docId,
                        name = state.itineraryName,
                        orderedStops = state.generatedStops,
                        startedAtMillis = timestampMillis(state.startedAt) ?: System.currentTimeMillis()
                    )
                }
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

private const val KEY_WIZARD_ACTIVE = "itinerary_wizard_active"
private const val KEY_WIZARD_STEP = "itinerary_wizard_step"
private const val KEY_WIZARD_SITES = "itinerary_wizard_sites"
private const val KEY_WIZARD_INTERESTS = "itinerary_wizard_interests"
private const val KEY_WIZARD_RECOMMENDATIONS = "itinerary_wizard_recommendations"
private const val KEY_WIZARD_NAME = "itinerary_wizard_name"
