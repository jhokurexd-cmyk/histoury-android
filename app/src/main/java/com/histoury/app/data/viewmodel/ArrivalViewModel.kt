package com.histoury.app.data.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.histoury.app.data.model.HistoricalSite
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

data class ArrivalUiState(
    val isLoading: Boolean = false,
    val site: HistoricalSite? = null,
    // True when this arrival resolved to a Place (café, restaurant, hotel,
    // etc.) rather than a real historical_sites document. Places don't
    // have historical content or a visit-history record, so the UI uses
    // this to skip the "View Historical Info" button and adjust copy.
    val isPlace: Boolean = false,
    val tripProgress: ActiveItineraryTrip.Progress? = null,
    val errorMessage: String? = null
)

class ArrivalViewModel : ViewModel() {

    private val historicalSiteRepository = HistoricalSiteRepository()

    private val placeRepository = PlaceRepository()

    private val visitHistoryRepository = VisitHistoryRepository()
    private val itineraryRepository = ItineraryRepository()

    private suspend fun markItineraryStopReached(documentId: String) {
        val progress = ActiveItineraryTrip.progressFor(documentId) ?: return
        _uiState.value = _uiState.value.copy(tripProgress = progress)
        itineraryRepository.markStopCompleted(
            documentId = progress.itineraryDocumentId,
            stopId = documentId,
            itineraryCompleted = progress.nextStop == null
        )
    }

    private val _uiState = MutableStateFlow(ArrivalUiState())
    val uiState: StateFlow<ArrivalUiState> = _uiState.asStateFlow()

    fun loadArrivedSite(siteId: String) {

        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null
            )

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
                markItineraryStopReached(site.documentId)

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
                    errorMessage = "Couldn't load details for this destination."
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                site = place.toSiteShape(),
                isPlace = true
            )
            markItineraryStopReached(place.documentId)
        }
    }
}
