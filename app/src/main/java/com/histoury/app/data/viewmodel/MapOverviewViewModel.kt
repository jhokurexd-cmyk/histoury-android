package com.histoury.app.data.viewmodel

import android.app.Application
import android.location.Geocoder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.histoury.app.data.model.HistoricalSite
import com.histoury.app.data.repository.HistoricalSiteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A generic place found via geocoding — behaves like a Google Maps result. */
data class PlaceResult(
    val name: String,
    val latLng: LatLng
)

/**
 * One-shot camera move request. The counter makes each request unique so
 * searching the same place twice still re-triggers the animation.
 */
data class CameraTarget(
    val latLng: LatLng,
    val requestId: Long
)

private const val GEOCODE_DEBOUNCE_MS = 500L

/**
 * Backs the Map tab: a free-browse, Google-Maps-like map.
 *
 * Search returns two kinds of results:
 *  - Histoury sites (matched locally) — selecting one opens the
 *    site-specific directions map, same as picking it from Home.
 *  - Any other place in the world (via the platform Geocoder, so no
 *    Places SDK or extra API key is needed) — selecting one just moves
 *    the camera there, exactly like Google Maps.
 */
class MapOverviewViewModel(application: Application) : AndroidViewModel(application) {

    private val historicalSiteRepository = HistoricalSiteRepository()

    private val _allSites = MutableStateFlow<List<HistoricalSite>>(emptyList())

    /** Every published site, for the pins on the Map tab. */
    val sites: StateFlow<List<HistoricalSite>> = _allSites.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _siteResults = MutableStateFlow<List<HistoricalSite>>(emptyList())
    val siteResults: StateFlow<List<HistoricalSite>> = _siteResults.asStateFlow()

    private val _placeResults = MutableStateFlow<List<PlaceResult>>(emptyList())
    val placeResults: StateFlow<List<PlaceResult>> = _placeResults.asStateFlow()

    private val _cameraTarget = MutableStateFlow<CameraTarget?>(null)
    val cameraTarget: StateFlow<CameraTarget?> = _cameraTarget.asStateFlow()

    private val _selectedSite = MutableStateFlow<HistoricalSite?>(null)

    /** The site whose card is showing after its pin was tapped. */
    val selectedSite: StateFlow<HistoricalSite?> = _selectedSite.asStateFlow()

    private val _visitedSiteIds = MutableStateFlow<Set<String>>(emptySet())

    /** Sites visited in person — their Site Information is unlocked (same rule as the Site Library). */
    val visitedSiteIds: StateFlow<Set<String>> = _visitedSiteIds.asStateFlow()

    private var geocodeJob: Job? = null

    private var cameraRequestCounter = 0L

    fun loadSites() {

        if (_allSites.value.isNotEmpty()) {
            return
        }

        viewModelScope.launch {
            _allSites.value = historicalSiteRepository.getHistoricalSites()
        }
    }

    /** Re-read on every visit to the tab: a visit may have just unlocked a site. */
    fun refreshVisitedSites() {
        viewModelScope.launch {
            _visitedSiteIds.value = com.histoury.app.data.repository.VisitHistoryRepository()
                .getVisitHistoryForCurrentUser()
                .map { it.siteId }
                .toSet()
        }
    }

    fun selectSite(site: HistoricalSite?) {
        _selectedSite.value = site
    }

    fun onSearchQueryChange(query: String) {

        _searchQuery.value = query

        // Histoury sites filter instantly — it's a small local list.
        _siteResults.value = if (query.isBlank()) {
            emptyList()
        } else {
            // Same word-start matching and ordering as Home's search.
            com.histoury.app.util.SearchMatcher.rank(
                items = _allSites.value,
                query = query,
                name = { it.siteName },
                location = { it.location }
            )
        }

        // Generic places are debounced so we don't geocode every keystroke.
        geocodeJob?.cancel()

        if (query.isBlank() || query.length < 3) {
            _placeResults.value = emptyList()
            return
        }

        geocodeJob = viewModelScope.launch {

            delay(GEOCODE_DEBOUNCE_MS)

            _placeResults.value = geocodePlaces(query)
        }
    }

    /** The map has moved to the selected place; don't fly there again on return. */
    fun onCameraTargetHandled() {
        _cameraTarget.value = null
    }

    fun onPlaceSelected(place: PlaceResult) {

        cameraRequestCounter += 1

        _cameraTarget.value = CameraTarget(
            latLng = place.latLng,
            requestId = cameraRequestCounter
        )

        clearSearch()
    }

    fun clearSearch() {
        geocodeJob?.cancel()
        _searchQuery.value = ""
        _siteResults.value = emptyList()
        _placeResults.value = emptyList()
    }

    @Suppress("DEPRECATION")
    private suspend fun geocodePlaces(query: String): List<PlaceResult> {

        if (!Geocoder.isPresent()) {
            return emptyList()
        }

        return withContext(Dispatchers.IO) {

            try {

                val geocoder = Geocoder(getApplication())

                val addresses = geocoder.getFromLocationName(query, 4)
                    ?: emptyList()

                addresses.mapNotNull { address ->

                    val label = address.getAddressLine(0)
                        ?: address.featureName
                        ?: return@mapNotNull null

                    PlaceResult(
                        name = label,
                        latLng = LatLng(address.latitude, address.longitude)
                    )
                }

            } catch (e: Exception) {
                // No network / geocoder backend unavailable — just show
                // no generic-place results rather than failing the search.
                emptyList()
            }
        }
    }
}
