package com.histoury.app.data.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.histoury.app.data.model.HistoricalSite
import com.histoury.app.data.model.ItineraryStop
import com.histoury.app.data.model.StopStatus
import com.histoury.app.data.model.toSiteShape
import com.histoury.app.data.repository.HistoricalSiteRepository
import com.histoury.app.data.repository.PlaceRepository
import com.histoury.app.data.repository.VisitHistoryRepository
import com.histoury.app.data.repository.ItineraryRepository
import com.histoury.app.data.itinerary.ActiveItineraryTrip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Shown when the visitor has reached a stop that IS part of their active
 * itinerary but is NOT the one it currently expects next — e.g. saved order
 * is Fort Santiago -> Manila Cathedral -> San Agustin and they reached
 * Manila Cathedral first. Geofence detection alone never reorders anything
 * (see ArrivalViewModel.markStopArrived); it only ever produces this prompt,
 * and the visitor's own choice (optimizeRouteFromArrival / keepOriginalOrder)
 * is what decides whether the saved order actually changes.
 */
data class OutOfOrderPrompt(
    val arrivedStop: ItineraryStop,
    val expectedStop: ItineraryStop
)

data class ArrivalUiState(
    val isLoading: Boolean = false,
    val site: HistoricalSite? = null,
    // True when this arrival resolved to a Place (café, restaurant, hotel,
    // etc.) rather than a real historical_sites document. Places don't
    // have historical content or a visit-history record, so the UI uses
    // this to skip the "View Historical Info" button and adjust copy.
    val isPlace: Boolean = false,
    val tripProgress: ActiveItineraryTrip.Progress? = null,
    val isResolvingStop: Boolean = false,
    // Set once "Complete Stop" or a confirmed skip has actually saved — the
    // value is StopStatus.COMPLETED or StopStatus.SKIPPED, and it's what
    // switches the screen over to the "stop resolved" transition summary.
    // tripProgress is deliberately left holding its PRE-advance snapshot
    // when this is set, so `tripProgress.nextStop` is still the right thing
    // to show as "Next: ...".
    val lastStopOutcome: String? = null,
    val showSkipConfirmation: Boolean = false,
    // Non-null while the "you arrived somewhere else on your itinerary"
    // dialog should be showing. See [OutOfOrderPrompt].
    val outOfOrderPrompt: OutOfOrderPrompt? = null,
    val isOptimizingRoute: Boolean = false,
    // One-shot confirmation banner after "Optimize Route" saves successfully.
    val routeOptimizedMessage: String? = null,
    // True after "Stay Here" on the stop-resolved summary. The stop is
    // finished, so the screen shows the plain visit actions (plus a way on
    // to the next stop) instead of offering Complete/Skip for it again.
    val stayedAfterResolve: Boolean = false,
    // Couldn't load the destination at all — the only case that replaces
    // the whole screen with an error page.
    val loadErrorMessage: String? = null,
    // A failed action (Complete, Skip, Explore, Optimize) — shown inline,
    // with the buttons still there to try again. These used to share one
    // field with load errors, so a Complete that failed on weak signal
    // replaced the entire screen with an error page and no retry.
    val errorMessage: String? = null
)

class ArrivalViewModel : ViewModel() {

    private val historicalSiteRepository = HistoricalSiteRepository()

    private val placeRepository = PlaceRepository()

    private val visitHistoryRepository = VisitHistoryRepository()
    private val itineraryRepository = ItineraryRepository()

    private val _uiState = MutableStateFlow(ArrivalUiState())
    val uiState: StateFlow<ArrivalUiState> = _uiState.asStateFlow()

    fun loadArrivedSite(siteId: String) {

        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                loadErrorMessage = null,
                errorMessage = null
            )

            // A cold launch (the app was killed, or a notification tap is
            // the very first screen this process opens) starts with no
            // in-memory trip session — ActiveItineraryTrip is a plain
            // singleton, wiped along with everything else on process death.
            // Rehydrating from Firestore here is what lets an arrival still
            // be recognized as part of the itinerary the visitor already
            // started, instead of falling through to the ordinary walk-in
            // path below.
            ActiveItineraryTrip.rehydrate(itineraryRepository)

            val site = historicalSiteRepository.getHistoricalSiteBySiteId(siteId)

            if (site != null) {

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    site = site,
                    isPlace = false
                )

                // Also covers the geofence-broadcast arrival path (which records
                // independently, since it can fire without this screen ever
                // opening) — recordVisitIfNotRecent() de-duplicates either way.
                visitHistoryRepository.recordVisitIfNotRecent(
                    site.siteId.ifBlank { site.documentId }
                )
                markStopArrived(site.documentId)

                return@launch
            }

            // Not a heritage site — maybe a Place (café/restaurant/park/
            // school) navigated to from Explore Intramuros. Show the same
            // arrival screen, but DON'T record a visit: "Your Journey" and
            // reviews are for heritage sites only.
            val place = placeRepository.getPlaceBySlug(siteId)
                ?: placeRepository.getPlaceById(siteId)

            if (place == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    loadErrorMessage = "Couldn't load details for this destination."
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                site = place.toSiteShape(),
                isPlace = true
            )
            markStopArrived(place.documentId)
        }
    }

    /**
     * Records ARRIVED only — reaching a stop, by geofence or by simply
     * navigating there in-app, never completes it on its own. Only
     * [completeCurrentStop] does that, from an explicit visitor action.
     *
     * Also where out-of-order arrivals are caught: [refId] can be (a) not
     * part of the active itinerary at all (an ordinary walk-in — unchanged
     * below), (b) exactly the stop the trip currently expects (the normal
     * path, unchanged), (c) a stop already resolved earlier in this same
     * trip (a revisit — nothing to ask, nothing to write), or (d) an
     * unresolved itinerary stop that ISN'T the expected one, which is the
     * new case: surface [OutOfOrderPrompt] and wait for the visitor's own
     * choice rather than silently reordering or silently ignoring it.
     */
    private suspend fun markStopArrived(refId: String) {

        val matched = ActiveItineraryTrip.stopFor(refId)

        // Tracks "where the visitor is right now" regardless of outcome, so
        // a stale "Keep Original" dismissal for a DIFFERENT stop gets
        // cleared once they've clearly moved on from it (see
        // ActiveItineraryTrip.noteArrival — DWELL geofences never fire an
        // EXIT, so this is the only "they left" signal available).
        ActiveItineraryTrip.noteArrival(refId)

        if (matched == null) {
            // Not part of any active itinerary — ordinary walk-in, unchanged.
            return
        }

        if (ActiveItineraryTrip.isExpectedNext(refId)) {

            val progress = ActiveItineraryTrip.progressFor(refId) ?: return

            _uiState.value = _uiState.value.copy(tripProgress = progress)

            if (progress.currentStop.status != StopStatus.PENDING) {
                // Already ARRIVED/EXPLORING from an earlier visit to this
                // screen (e.g. backing out and returning) — nothing to write.
                return
            }

            itineraryRepository.markStopArrived(progress.itineraryDocumentId, refId)
            ActiveItineraryTrip.reflectStatus(refId, StopStatus.ARRIVED)
            _uiState.value = _uiState.value.copy(tripProgress = ActiveItineraryTrip.progressFor(refId))
            return
        }

        if (StopStatus.isResolved(matched.status)) {
            // Already completed/skipped earlier in this trip — a revisit,
            // not a reroute case. Show it like an ordinary walk-in.
            return
        }

        if (ActiveItineraryTrip.isOutOfOrderDismissed(refId)) {
            // The visitor already picked "Keep Original Order" for this
            // exact stop and hasn't arrived anywhere else since — don't
            // re-ask on every recomposition/re-entry while still here.
            return
        }

        val expected = ActiveItineraryTrip.expectedNextStop() ?: return

        _uiState.value = _uiState.value.copy(
            outOfOrderPrompt = OutOfOrderPrompt(arrivedStop = matched, expectedStop = expected)
        )
    }

    /**
     * "Keep Original Order" on the out-of-order dialog. The saved itinerary
     * is untouched and the expected stop stays next; this stop is simply
     * viewed the same way an ordinary walk-in would be (tripProgress stays
     * null, so "View Historical Info" is offered instead of the itinerary
     * actions).
     */
    fun keepOriginalOrder() {
        val prompt = _uiState.value.outOfOrderPrompt ?: return
        ActiveItineraryTrip.dismissOutOfOrderPrompt(prompt.arrivedStop.refId)
        _uiState.value = _uiState.value.copy(outOfOrderPrompt = null)
    }

    /**
     * "Optimize Route" on the out-of-order dialog. Recalculates the
     * remaining stops starting from here (see
     * [ItineraryRepository.optimizeFromCurrentStop]), adopts that order,
     * and then proceeds exactly like an ordinary arrival at the (now
     * current) stop.
     */
    fun optimizeRouteFromArrival() {

        val prompt = _uiState.value.outOfOrderPrompt ?: return
        if (_uiState.value.isOptimizingRoute) return

        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(isOptimizingRoute = true, errorMessage = null)

            val documentId = ActiveItineraryTrip.activeDocumentId()

            val reordered = documentId?.let {
                itineraryRepository.optimizeFromCurrentStop(it, prompt.arrivedStop.refId)
            }

            if (documentId == null || reordered == null) {
                _uiState.value = _uiState.value.copy(
                    isOptimizingRoute = false,
                    errorMessage = "Couldn't update your itinerary. Please check your connection and try again."
                )
                return@launch
            }

            ActiveItineraryTrip.reorderAndFocus(reordered, prompt.arrivedStop.refId)

            _uiState.value = _uiState.value.copy(
                isOptimizingRoute = false,
                outOfOrderPrompt = null,
                routeOptimizedMessage = "Itinerary updated — starting from ${prompt.arrivedStop.name}."
            )

            markStopArrived(prompt.arrivedStop.refId)
        }
    }

    /** Dismisses the one-shot "itinerary updated" banner. */
    fun dismissRouteOptimizedMessage() {
        _uiState.value = _uiState.value.copy(routeOptimizedMessage = null)
    }

    /** "Explore This Site" — the primary post-arrival action. */
    fun beginExploring() {

        val progress = _uiState.value.tripProgress ?: return
        if (progress.currentStop.status != StopStatus.ARRIVED) return

        if (_uiState.value.isResolvingStop) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isResolvingStop = true, errorMessage = null)
            val saved = itineraryRepository.markStopExploring(progress.itineraryDocumentId, progress.currentStop.refId)
            if (!saved) {
                _uiState.value = _uiState.value.copy(
                    isResolvingStop = false,
                    errorMessage = "Couldn't save your progress. Please check your connection and try again."
                )
                return@launch
            }
            ActiveItineraryTrip.reflectStatus(progress.currentStop.refId, StopStatus.EXPLORING)
            _uiState.value = _uiState.value.copy(
                isResolvingStop = false,
                tripProgress = ActiveItineraryTrip.progressFor(progress.currentStop.refId)
            )
        }
    }

    /**
     * "Explore Site" / "Experience in AR" on the redesigned arrival card.
     * Records that the visitor is exploring (ARRIVED -> EXPLORING) in the
     * background — purely informational, so it never blocks the button or
     * shows an error (it simply stays ARRIVED if it can't be saved, e.g.
     * offline). The stop is only finished by [completeCurrentStop].
     */
    fun noteExploring() {
        val progress = _uiState.value.tripProgress ?: return
        if (_uiState.value.stayedAfterResolve) return
        if (progress.currentStop.status != StopStatus.ARRIVED) return
        viewModelScope.launch {
            if (itineraryRepository.markStopExploring(progress.itineraryDocumentId, progress.currentStop.refId)) {
                ActiveItineraryTrip.reflectStatus(progress.currentStop.refId, StopStatus.EXPLORING)
                val refreshed = ActiveItineraryTrip.progressFor(progress.currentStop.refId)
                // Only while this is still the stop on screen.
                if (refreshed != null && _uiState.value.lastStopOutcome == null) {
                    _uiState.value = _uiState.value.copy(tripProgress = refreshed)
                }
            }
        }
    }

    /**
     * "Continue to Next Site" — the only action that finishes a stop. Works
     * straight from ARRIVED as well as EXPLORING: the visitor decides when
     * they're done, not the screen.
     */
    fun completeCurrentStop() {

        val progress = _uiState.value.tripProgress ?: return
        if (_uiState.value.isResolvingStop) return

        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(isResolvingStop = true, errorMessage = null)

            val saved = itineraryRepository.markStopCompleted(
                progress.itineraryDocumentId,
                progress.currentStop.refId
            )

            if (saved != null) {
                ActiveItineraryTrip.advanceFrom(progress.currentStop.refId, StopStatus.COMPLETED)
                _uiState.value = _uiState.value.copy(
                    isResolvingStop = false,
                    lastStopOutcome = StopStatus.COMPLETED
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isResolvingStop = false,
                    errorMessage = "Couldn't save your progress. Please check your connection and try again."
                )
            }
        }
    }

    /** Opens the "Skip this site?" confirmation — skipping is deliberate, never a stray tap. */
    fun requestSkip() {
        _uiState.value = _uiState.value.copy(showSkipConfirmation = true)
    }

    fun cancelSkip() {
        _uiState.value = _uiState.value.copy(showSkipConfirmation = false)
    }

    fun confirmSkip() {

        val progress = _uiState.value.tripProgress ?: return

        // A second tap on "Skip" while the first is saving used to fire a
        // second write (and a second advance of the trip).
        if (_uiState.value.isResolvingStop) return

        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(
                showSkipConfirmation = false,
                isResolvingStop = true,
                errorMessage = null
            )

            val saved = itineraryRepository.markStopSkipped(
                progress.itineraryDocumentId,
                progress.currentStop.refId
            )

            if (saved != null) {
                ActiveItineraryTrip.advanceFrom(progress.currentStop.refId, StopStatus.SKIPPED)
                _uiState.value = _uiState.value.copy(
                    isResolvingStop = false,
                    lastStopOutcome = StopStatus.SKIPPED
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isResolvingStop = false,
                    errorMessage = "Couldn't skip this stop. Please check your connection and try again."
                )
            }
        }
    }

    /** "Stay Here" on the post-resolution transition — dismiss it without navigating away. */
    fun dismissStopResolvedBanner() {
        val state = _uiState.value
        val outcome = state.lastStopOutcome ?: return
        // Keep the pre-advance snapshot (its nextStop is still "up next"),
        // but record that its current stop is finished, and switch the
        // screen out of the Complete/Skip actions for it.
        _uiState.value = state.copy(
            lastStopOutcome = null,
            stayedAfterResolve = true,
            tripProgress = state.tripProgress?.let {
                it.copy(currentStop = it.currentStop.copy(status = outcome))
            }
        )
    }

    /** "Pause & Save for Later" — the itinerary's own progress is already durable; this only flips its status. */
    fun pauseTrip() {
        val progress = _uiState.value.tripProgress ?: return
        // The paused trip stops being "the active trip" in this session too.
        // Left loaded, Map kept showing "Stop X of Y" and later arrivals were
        // still matched against a trip the visitor had put away.
        if (ActiveItineraryTrip.isActiveFor(progress.itineraryDocumentId)) {
            ActiveItineraryTrip.clear()
        }
        viewModelScope.launch {
            itineraryRepository.pauseItinerary(progress.itineraryDocumentId)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
