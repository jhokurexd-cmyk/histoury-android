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
import com.histoury.app.map.TravelModes
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
import kotlinx.coroutines.flow.drop
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

/** Closer than this to the first stop and there's no "getting there" to show. */
private const val APPROACH_MIN_METERS = 300f

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

    /**
     * How to get from where the visitor is right now to the first stop,
     * for when they aren't in Intramuros yet. Live only: never saved with
     * the itinerary (it depends on where the phone is). Null when they're
     * already near the first stop, or their location isn't known.
     */
    val approachLeg: com.histoury.app.data.routing.RouteLeg? = null,

    val isLoadingApproach: Boolean = false,

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

    val showAddStopSheet: Boolean = false,

    /**
     * Back was pressed on a new, unsaved route the visitor has edited (added,
     * removed or moved stops). Going back regenerates the route, so ask first.
     */
    val showDiscardEditsDialog: Boolean = false
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

    private var approachJob: Job? = null

    /**
     * What the open itinerary looked like when it was last loaded or saved —
     * name, travel mode, start time, stop order and visit lengths. Null for
     * a brand-new, never-saved route. Compared against the live state to
     * tell whether leaving the overview would lose anything.
     */
    private var savedFingerprint: String? = null

    /**
     * A saved itinerary's travel mode string when this build doesn't know it
     * at all (a value written by a newer app version). The overview treats
     * such a trip as Walking, the app's default, but saving writes the
     * original value back unchanged so it is never lost. Cleared as soon as
     * the visitor picks a mode themselves.
     *
     * Known modes that just can't be routed yet (TWO_WHEELER, TRANSIT on the
     * OpenStreetMap map) don't need this: they stay in [ItineraryUiState.travelMode]
     * as they are, and the screen shows "Coming Soon" for them.
     */
    private var preservedSavedTravelMode: String? = null

    /**
     * The stops part of [savedFingerprint] (order and visit lengths). Kept
     * on its own so we can tell "the visitor edited the stops here" apart
     * from other unsaved edits such as a rename.
     */
    private var savedStopsFingerprint: String? = null

    /**
     * The stops of a new route exactly as [generateRoute] built them. Null
     * for a saved itinerary. Back from the overview compares against it to
     * know whether stepping back would throw away the visitor's own edits.
     */
    private var generatedStopsSnapshot: List<String>? = null

    private fun stopsFingerprint(stops: List<ItineraryStop>): String = buildString {
        stops.forEach { stop ->
            append('|').append(stop.refType).append(':').append(stop.refId)
            append('@').append(stop.estimatedVisitMinutes)
        }
    }

    // Header first, stops last: rebaseSavedStops relies on the stops part
    // being the suffix.
    private fun fingerprint(state: ItineraryUiState): String = buildString {
        append(state.itineraryName.trim()).append('|')
        append(state.travelMode.wireValue).append('|')
        append(state.startTimeMinutes)
        append(stopsFingerprint(state.generatedStops))
    }

    /** Records [state] as what's saved (after a load or a successful save). */
    private fun markSaved(state: ItineraryUiState) {
        savedFingerprint = fingerprint(state)
        savedStopsFingerprint = stopsFingerprint(state.generatedStops)
    }

    /**
     * The saved stop list changed on the server (Arrival's "Optimize Route")
     * and we took it over: move the "saved" baseline to it, so the new order
     * doesn't count as an unsaved edit. Only the stops part is replaced; a
     * local rename still counts as unsaved.
     */
    private fun rebaseSavedStops(serverStops: List<ItineraryStop>) {
        val oldStops = savedStopsFingerprint ?: return
        val newStops = stopsFingerprint(serverStops)
        savedFingerprint = savedFingerprint?.removeSuffix(oldStops)?.plus(newStops)
        savedStopsFingerprint = newStops
    }

    /** True when the route overview holds edits (or a new route) that haven't been saved. */
    fun hasUnsavedChanges(): Boolean {
        val state = _uiState.value
        if (!state.isBuilderActive || state.builderStep != BuilderStep.ROUTE_OVERVIEW) return false
        if (state.generatedStops.isEmpty()) return false
        return savedFingerprint != fingerprint(state)
    }

    /**
     * Folds the latest saved state from Firestore into [state]. The Boolean
     * is true when the server's stop list was taken over whole.
     *
     * Stop statuses change outside this screen — Arrival marks a stop
     * completed, Map's "Skip" marks it skipped — and Arrival's "Optimize
     * Route" even re-orders the stops, while this ViewModel keeps the copy
     * it loaded earlier. Writing that stale copy back (Save Changes, Resume
     * Trip) used to silently undo that progress and that new order.
     *
     * - The visitor hasn't touched the stops here since loading: the saved
     *   list is the newer one, so it's taken as it is (order included).
     * - The visitor did edit the stops here: their edits win, and only each
     *   stop's progress comes from the server.
     */
    private suspend fun serverProgress(state: ItineraryUiState): Pair<ItineraryUiState, Boolean> {
        val id = state.editingItineraryId ?: return state to false
        val server = itineraryRepository.getItineraryById(id) ?: return state to false
        val withStatus = state.copy(
            itineraryStatus = server.status,
            startedAt = server.startedAt ?: state.startedAt,
            completedAt = server.completedAt,
            deleteAt = server.deleteAt
        )

        // Same clean-up as openSavedItinerary (unique keys for the timeline).
        val serverStops = server.stops
            .sortedBy { it.order }
            .distinctBy { "${it.refType}:${it.refId}" }

        // Checked against the live state, after the read: the visitor may
        // have switched trips or moved a stop while it was in flight.
        val live = _uiState.value
        val stopsUntouched = live.editingItineraryId == id &&
            savedStopsFingerprint != null &&
            stopsFingerprint(live.generatedStops) == savedStopsFingerprint

        if (stopsUntouched && serverStops.isNotEmpty()) {
            rebaseSavedStops(serverStops)
            return withStatus.copy(generatedStops = serverStops) to true
        }

        val serverStatus = server.stops.associate { "${it.refType}:${it.refId}" to it.status }
        return withStatus.copy(
            generatedStops = state.generatedStops.map { stop ->
                serverStatus["${stop.refType}:${stop.refId}"]?.let { stop.copy(status = it) } ?: stop
            }
        ) to false
    }

    /**
     * Called when the Itinerary tab is shown again: brings an open trip's
     * progress (Done badges, Resume vs Start) — and its stop order, if it
     * was re-optimized on the Arrival screen — up to date with what happened
     * on the Map/Arrival screens in the meantime.
     */
    fun refreshOpenItineraryProgress() {
        val before = _uiState.value
        if (!before.isBuilderActive || before.builderStep != BuilderStep.ROUTE_OVERVIEW) return
        if (before.editingItineraryId == null || before.isSaving) return

        viewModelScope.launch {
            val (merged, tookServerStops) = serverProgress(_uiState.value)
            val current = _uiState.value
            // Only apply if the visitor hasn't switched itineraries meanwhile.
            if (current.editingItineraryId != merged.editingItineraryId) return@launch

            if (tookServerStops) {
                val orderChanged = merged.generatedStops.routeKey() != current.generatedStops.routeKey()
                // A new order makes the old road path wrong; it's fetched again below.
                val path = if (orderChanged) null else current.routePath
                _uiState.value = current.copy(
                    generatedStops = merged.generatedStops,
                    itineraryStatus = merged.itineraryStatus,
                    startedAt = merged.startedAt,
                    completedAt = merged.completedAt,
                    deleteAt = merged.deleteAt,
                    routePath = path,
                    schedule = ItineraryPlanner.buildSchedule(merged.generatedStops, path)
                )
                // refreshRoutePath also refreshes "Getting there".
                if (orderChanged) refreshRoutePath() else refreshApproach()
                return@launch
            }

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
            // "Getting there" is from where the visitor is NOW, and to the
            // next unfinished stop, both of which may have changed.
            refreshApproach()
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
        // A car/motorcycle trip's legs are re-planned when "Avoid
        // expressways and tolls" is switched (drop(1): not the initial value).
        viewModelScope.launch {
            com.histoury.app.data.repository.DriveOptions.avoidExpressways
                .drop(1)
                .collect {
                    val state = _uiState.value
                    if (state.isBuilderActive &&
                        com.histoury.app.data.repository.DriveOptions.appliesTo(state.travelMode)
                    ) {
                        refreshRoutePath()
                    }
                }
        }
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
        approachJob?.cancel()

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
            previewTarget = null,
            // Nothing from the previous build carries over.
            approachLeg = null,
            isLoadingApproach = false,
            usedFallbackLocation = false,
            showAddStopSheet = false,
            showDiscardEditsDialog = false
        )

        savedFingerprint = null
        savedStopsFingerprint = null
        generatedStopsSnapshot = null
        preservedSavedTravelMode = null

        loadCatalogIfNeeded()
    }

    /** Opens a saved itinerary straight into the route overview, ready to start, edit, or delete. */
    fun openSavedItinerary(itinerary: Itinerary) {

        // The saved mode is kept as it is, even one that can't be routed yet.
        val mode = TravelModes.fromWire(itinerary.travelMode)
        preservedSavedTravelMode = itinerary.travelMode.takeIf { wire ->
            wire.isNotBlank() && TravelMode.entries.none { it.wireValue == wire }
        }

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
            previewTarget = null,
            // Nothing from the trip that was open before carries over.
            approachLeg = null,
            isLoadingApproach = false,
            usedFallbackLocation = false,
            showAddStopSheet = false,
            showDiscardEditsDialog = false
        )

        markSaved(_uiState.value)
        // Not a freshly generated route: Back leaves instead of stepping back.
        generatedStopsSnapshot = null

        loadCatalogIfNeeded()

        // The saved document stores totals, not the path itself — polylines
        // are large and go stale when the routing data changes. Re-fetching
        // is cheap after the first time thanks to the leg cache.
        refreshRoutePath()
    }

    fun closeBuilder() {
        routePathJob?.cancel()
        approachJob?.cancel()
        _uiState.value = _uiState.value.copy(isBuilderActive = false, showDiscardEditsDialog = false)
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
        // step are kept, as long as the place still exists: the step lists
        // EVERY place (suggestions first), so a pick no longer has to be a
        // suggestion.
        val stillListed = state.allPlaces.map { it.documentId }.toSet()

        _uiState.value = state.copy(
            builderStep = BuilderStep.RECOMMENDATIONS,
            recommendedPlaces = recommendations,
            selectedRecommendationIds = state.selectedRecommendationIds.intersect(stillListed)
        )
    }

    fun toggleRecommendationSelected(placeId: String) {
        val current = _uiState.value.selectedRecommendationIds
        _uiState.value = _uiState.value.copy(
            selectedRecommendationIds = if (placeId in current) current - placeId else current + placeId
        )
    }

    fun backToSites() {
        // An error from a later step doesn't belong on step 1.
        _uiState.value = _uiState.value.copy(builderStep = BuilderStep.SELECT_SITES, errorMessage = null)
    }

    fun backToInterests() {
        _uiState.value = _uiState.value.copy(builderStep = BuilderStep.SELECT_INTERESTS, errorMessage = null)
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
                // Going back and forward regenerates the route, so stops the
                // visitor added, removed or moved here would be lost without
                // a word. Ask first; nothing changed means step back as usual.
                val snapshot = generatedStopsSnapshot
                if (snapshot != null && state.generatedStops.stopKeys() != snapshot) {
                    _uiState.value = state.copy(showDiscardEditsDialog = true)
                    return true
                }
                stepBackFromNewRoute()
            }
        }
        return true
    }

    /** "Discard" in the discard-edits dialog: drop the edits and step back. */
    fun confirmDiscardEdits() {
        _uiState.value = _uiState.value.copy(showDiscardEditsDialog = false)
        stepBackFromNewRoute()
    }

    /** "Keep editing" (or dismissing the dialog): stay on the overview. */
    fun dismissDiscardEdits() {
        _uiState.value = _uiState.value.copy(showDiscardEditsDialog = false)
    }

    private fun stepBackFromNewRoute() {
        routePathJob?.cancel()
        // Step 3 is only reachable with interests picked; without any, the
        // visitor came straight from step 2.
        if (_uiState.value.selectedInterestCategories.isEmpty()) {
            backToInterests()
        } else {
            goToRecommendationsStep()
        }
    }

    /** Which stops, in which order — what Add a Stop, remove and drag change. */
    private fun List<ItineraryStop>.stopKeys(): List<String> =
        map { "${it.refType}:${it.refId}" }

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
                // Any place the visitor picked in step 3, suggested or not.
                state.allPlaces.filter {
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
                    // "Suggested" only for places Histoury actually suggested.
                    isRecommended = state.recommendedPlaces.any { it.documentId == place.documentId }
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
                // The previous build's lines and leg times don't belong to
                // this route; the new path loads just below.
                routePath = null,
                approachLeg = null,
                isLoadingApproach = false,
                schedule = ItineraryPlanner.buildSchedule(optimized.orderedStops, null),
                itineraryName = state.itineraryName.ifBlank {
                    "Intramuros Trip \u2014 ${selectedSites.firstOrNull()?.siteName ?: "My Route"}"
                },
                showDiscardEditsDialog = false
            )

            // What the route looked like as built, so Back can tell whether
            // the visitor has changed it since.
            generatedStopsSnapshot = optimized.orderedStops.stopKeys()

            refreshRoutePath()
        }
    }

    fun clearInfoMessage() {
        _uiState.value = _uiState.value.copy(infoMessage = null)
    }

    /** Saves first, then starts or resumes at the first incomplete stop. */
    fun beginTrip(onReady: (String) -> Unit) {
        val initialState = _uiState.value
        if (initialState.isSaving) return
        if (initialState.generatedStops.isEmpty()) {
            // The button used to do nothing at all once every stop was removed.
            _uiState.value = initialState.copy(errorMessage = "Add at least one stop before starting.")
            return
        }

        // Flag the save straight away so a double tap can't start (and
        // write) the trip twice before the coroutine gets going.
        _uiState.value = initialState.copy(isSaving = true, errorMessage = null)

        viewModelScope.launch {

            // Pick up any progress (and re-ordering) made since this screen
            // loaded the trip.
            val (currentState, tookServerStops) = serverProgress(_uiState.value)

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

            // The saved order changed elsewhere and was taken over: the road
            // path drawn for the old order no longer fits, so fetch it again
            // (ready for when the visitor comes back to this screen).
            if (tookServerStops && state.generatedStops.routeKey() != initialState.generatedStops.routeKey()) {
                _uiState.value = _uiState.value.copy(
                    routePath = null,
                    schedule = ItineraryPlanner.buildSchedule(state.generatedStops, null)
                )
                refreshRoutePath()
            }

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

            // Only one itinerary is ever IN_PROGRESS at a time — every other
            // trip already under way gets paused (not lost) rather than left
            // to silently conflict with this one. All of them, not just the
            // first match: that match was often this very trip.
            itineraryRepository.pauseOtherActiveItineraries(documentId).forEach { pausedId ->
                if (ActiveItineraryTrip.isActiveFor(pausedId)) {
                    ActiveItineraryTrip.clear()
                }
            }

            _uiState.value = _uiState.value.copy(
                isSaving = false,
                editingItineraryId = documentId,
                itineraryStatus = ItineraryStatus.IN_PROGRESS
            )

            markSaved(_uiState.value)
            // Saved now: Back from here leaves rather than stepping back.
            generatedStopsSnapshot = null

            val firstStop = ActiveItineraryTrip.start(
                documentId = documentId,
                name = state.itineraryName,
                orderedStops = state.generatedStops,
                startedAtMillis = timestampMillis(state.startedAt) ?: System.currentTimeMillis(),
                travelModeWire = state.travelMode.wireValue
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

        // Only routable modes can be newly chosen; a saved coming-soon mode
        // stays selected until the visitor picks another one.
        if (!TravelModes.isAvailable(mode)) return

        // The visitor chose a mode: that is what gets saved from now on.
        preservedSavedTravelMode = null

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

        // The way there from the visitor's current position follows the
        // same first stop and travel mode, so it refreshes alongside.
        refreshApproach()

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

        if (!TravelModes.isAvailable(mode)) {
            // Coming soon: no route is fetched, and none is fetched for a
            // different mode in its place. Times stay as straight-line
            // estimates, marked approximate; the screen explains why.
            // Totals follow the stops too, so removing or reordering stops
            // in this mode doesn't leave (and save) the old distance.
            val estimate = ItineraryPlanner.buildSchedule(stops, null)
            _uiState.value = _uiState.value.copy(
                routePath = null,
                isLoadingRoutePath = false,
                routeIsApproximate = true,
                totalDistanceMeters = estimate.sumOf { it.travelMetersToHere }.toDouble(),
                totalWalkingMinutes = estimate.sumOf { it.travelMinutesToHere },
                totalVisitMinutes = stops.sumOf { it.estimatedVisitMinutes },
                schedule = estimate
            )
            return
        }

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

            // The user may have reordered while this was in flight. Only the
            // stops and their positions matter: a stop's status changing
            // meanwhile (trip started, progress synced) used to throw away
            // a perfectly good route.
            if (current.generatedStops.routeKey() != stops.routeKey() || current.travelMode != mode) {
                return@launch
            }

            val latestStops = current.generatedStops
            val measured = ItineraryPlanner.withMeasuredTotals(latestStops, path)

            _uiState.value = current.copy(
                isLoadingRoutePath = false,
                routePath = path,
                routeIsApproximate = path.hasApproximateLegs,
                totalDistanceMeters = measured.totalDistanceMeters,
                totalWalkingMinutes = measured.totalWalkingMinutes,
                totalVisitMinutes = measured.totalVisitMinutes,
                schedule = ItineraryPlanner.buildSchedule(latestStops, path)
            )
        }
    }

    /**
     * Routes the visitor from where they are now to the first stop (in the
     * trip's travel mode), unless they're already close to it. Shown on the
     * itinerary map and as "Getting there" above the stops.
     */
    private fun refreshApproach() {

        approachJob?.cancel()

        val stops = _uiState.value.generatedStops
        val mode = _uiState.value.travelMode
        // The first stop still to visit: a resumed trip whose first stop is
        // already done continues from the next one.
        val first = firstOpenStop(stops)

        if (first == null || !TravelModes.isAvailable(mode)) {
            _uiState.value = _uiState.value.copy(approachLeg = null, isLoadingApproach = false)
            return
        }

        approachJob = viewModelScope.launch {

            val start = resolveStartPoint()
            if (start.isFallback) {
                _uiState.value = _uiState.value.copy(approachLeg = null, isLoadingApproach = false)
                return@launch
            }

            val origin = LatLng(start.latitude, start.longitude)
            val destination = LatLng(first.latitude, first.longitude)

            val gap = FloatArray(1)
            android.location.Location.distanceBetween(
                origin.latitude, origin.longitude, destination.latitude, destination.longitude, gap
            )
            if (gap[0] < APPROACH_MIN_METERS) {
                // Already there: nothing to direct.
                _uiState.value = _uiState.value.copy(approachLeg = null, isLoadingApproach = false)
                return@launch
            }

            _uiState.value = _uiState.value.copy(isLoadingApproach = true)

            val leg = try {
                routePathRepository.getLeg(
                    origin = origin,
                    destination = destination,
                    mode = mode,
                    destinationSiteId = first.refId.takeIf { first.refType == "site" }
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }

            val current = _uiState.value
            // The first stop or the mode may have changed while this ran.
            // (Compared by identity, not the whole stop, so a status update
            // meanwhile doesn't discard the result and leave the spinner on.)
            val nowFirst = firstOpenStop(current.generatedStops)
            if (nowFirst?.let { "${it.refType}:${it.refId}" } != "${first.refType}:${first.refId}" ||
                current.travelMode != mode
            ) {
                _uiState.value = current.copy(isLoadingApproach = false)
                return@launch
            }

            _uiState.value = current.copy(approachLeg = leg, isLoadingApproach = false)
        }
    }

    /** Stop identity and position: what a route depends on (not its status). */
    private fun List<ItineraryStop>.routeKey(): List<String> =
        map { "${it.refType}:${it.refId}@${it.latitude},${it.longitude}" }

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
        travelMode = preservedSavedTravelMode ?: state.travelMode.wireValue,
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

            // Don't overwrite progress (or a re-ordering) recorded elsewhere
            // since this loaded.
            val (state, tookServerStops) = serverProgress(_uiState.value)

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
                markSaved(state)
                generatedStopsSnapshot = null
                // A trip that's under way keeps navigating the saved order.
                val docId = state.editingItineraryId
                if (docId != null && ActiveItineraryTrip.isActiveFor(docId)) {
                    ActiveItineraryTrip.start(
                        documentId = docId,
                        name = state.itineraryName,
                        orderedStops = state.generatedStops,
                        startedAtMillis = timestampMillis(state.startedAt) ?: System.currentTimeMillis(),
                        travelModeWire = state.travelMode.wireValue
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
                // The server's stop list was taken over (and the "saved"
                // baseline moved to it), so show it here too; otherwise the
                // screen and the baseline would disagree on the order.
                if (tookServerStops) {
                    val orderChanged = state.generatedStops.routeKey() != _uiState.value.generatedStops.routeKey()
                    _uiState.value = _uiState.value.copy(
                        generatedStops = state.generatedStops,
                        itineraryStatus = state.itineraryStatus,
                        startedAt = state.startedAt,
                        completedAt = state.completedAt,
                        deleteAt = state.deleteAt,
                        routePath = if (orderChanged) null else _uiState.value.routePath,
                        schedule = ItineraryPlanner.buildSchedule(
                            state.generatedStops,
                            if (orderChanged) null else _uiState.value.routePath
                        )
                    )
                    if (orderChanged) refreshRoutePath()
                }
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

/** The first stop not yet completed or skipped (the trip resumes there). */
internal fun firstOpenStop(stops: List<ItineraryStop>): ItineraryStop? =
    stops.firstOrNull { !StopStatus.isResolved(it.status) }
