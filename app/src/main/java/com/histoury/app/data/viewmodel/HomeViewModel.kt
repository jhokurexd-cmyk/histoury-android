package com.histoury.app.data.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.histoury.app.data.model.HistoricalSite
import com.histoury.app.data.model.Itinerary
import com.histoury.app.data.model.ItineraryStatus
import com.histoury.app.data.model.Place
import com.histoury.app.data.model.StopStatus
import com.histoury.app.data.model.Tag
import com.histoury.app.data.repository.HistoricalSiteRepository
import com.histoury.app.data.repository.ItineraryRepository
import com.histoury.app.data.repository.PlaceRepository
import com.histoury.app.data.repository.TagRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted

class HomeViewModel : ViewModel() {

    private val historicalSiteRepository = HistoricalSiteRepository()

    private val tagRepository = TagRepository()

    private val placeRepository = PlaceRepository()

    private val itineraryRepository = ItineraryRepository()

    private val _allSites = MutableStateFlow<List<HistoricalSite>>(emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    /**
     * True while a pull-to-refresh is running. Kept apart from [isLoading]
     * so the list stays on screen under the spinner instead of being
     * replaced by the full-page loading state.
     */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _tagsById = MutableStateFlow<Map<String, String>>(emptyMap())
    val tagsById: StateFlow<Map<String, String>> = _tagsById

    private val _allTags = MutableStateFlow<List<Tag>>(emptyList())
    val allTags: StateFlow<List<Tag>> = _allTags

    private val _selectedTagIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedTagIds: StateFlow<Set<String>> = _selectedTagIds

    private val _allPlaces = MutableStateFlow<List<Place>>(emptyList())

    // "all", or one of: food, park, hotel, souvenir_shop, school, bank
    // (plus legacy "cafe"/"restaurant" for older entries)
    private val _selectedPlaceCategory = MutableStateFlow("all")
    val selectedPlaceCategory: StateFlow<String> = _selectedPlaceCategory

    // Which pinned Home tab is active: "sites" (default) or "places"
    private val _selectedHomeSection = MutableStateFlow("sites")
    val selectedHomeSection: StateFlow<String> = _selectedHomeSection

    // The visitor's in-progress or paused trip, if any — drives Home's
    // "Continue Your Trip" card. Null means no such itinerary exists, not
    // that it hasn't loaded yet; see [refreshResumableItinerary].
    private val _resumableItinerary = MutableStateFlow<Itinerary?>(null)
    val resumableItinerary: StateFlow<Itinerary?> = _resumableItinerary

    // Trips the visitor has saved but not started yet (PLANNED), newest
    // first — for the bell's "You have N saved itineraries".
    private val _plannedItineraries = MutableStateFlow<List<Itinerary>>(emptyList())
    val plannedItineraries: StateFlow<List<Itinerary>> = _plannedItineraries

    val sites: StateFlow<List<HistoricalSite>> = combine(
        _allSites,
        _searchQuery,
        _selectedTagIds
    ) { sites, query, selectedTagIds ->

        sites.filter { site ->

            val matchesQuery = query.isBlank() ||
                site.siteName.contains(query, ignoreCase = true) ||
                site.location.contains(query, ignoreCase = true) ||
                site.description.contains(query, ignoreCase = true)

            val matchesTags = selectedTagIds.isEmpty() ||
                site.tagIds.any { tagId -> tagId in selectedTagIds }

            matchesQuery && matchesTags
        }

    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val places: StateFlow<List<Place>> = combine(
        _allPlaces,
        _searchQuery,
        _selectedPlaceCategory
    ) { places, query, category ->

        places.filter { place ->

            val matchesQuery = query.isBlank() ||
                place.name.contains(query, ignoreCase = true) ||
                place.location.contains(query, ignoreCase = true) ||
                place.description.contains(query, ignoreCase = true)

            val matchesCategory = when (category) {
                "all" -> true
                // The admin panel's Places Management now writes "food"
                // directly for its unified Food & Drink category. Legacy
                // "cafe"/"restaurant" values (from before that module
                // existed) still match too, so older entries don't drop
                // out of this filter.
                "food" -> place.category.equals("food", ignoreCase = true) ||
                    place.category.equals("cafe", ignoreCase = true) ||
                    place.category.equals("restaurant", ignoreCase = true)
                else -> place.category.equals(category, ignoreCase = true)
            }

            matchesQuery && matchesCategory
        }

    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        loadHomeData()
        refreshResumableItinerary()
    }

    /**
     * Re-checks for an in-progress or paused trip. Called on Home's initial
     * composition and again whenever the app resumes to the foreground —
     * covers a trip paused elsewhere (Arrival's "Pause & Save for Later")
     * or finished elsewhere without Home having been recreated in between.
     */
    fun refreshResumableItinerary() {
        viewModelScope.launch { loadTrips() }
    }

    /**
     * One read of the visitor's itineraries feeds both bell items (the trip
     * to resume and the saved-trips count), instead of a query each. If the
     * read fails outright, what's already shown is kept rather than cleared.
     */
    private suspend fun loadTrips() {
        val all = itineraryRepository.loadItinerariesForCurrentUser() ?: return

        _resumableItinerary.value = all
            .filter { it.status == ItineraryStatus.IN_PROGRESS || it.status == ItineraryStatus.PAUSED }
            .maxByOrNull { updatedMillis(it) }

        _plannedItineraries.value = all
            .filter { it.status == ItineraryStatus.PLANNED && it.stops.isNotEmpty() }
            .sortedByDescending { updatedMillis(it) }
    }

    private fun updatedMillis(itinerary: Itinerary): Long =
        when (val value = itinerary.updatedAt ?: itinerary.createdAt) {
            is com.google.firebase.Timestamp -> value.toDate().time
            is java.util.Date -> value.time
            is Number -> value.toLong()
            else -> 0L
        }

    /** "Stop 2 of 5" for the Continue Trip card — resolved stops plus one, capped at the total. */
    fun resumableStopProgressText(itinerary: Itinerary): String {
        val resolvedCount = itinerary.stops.count { StopStatus.isResolved(it.status) }
        val currentNumber = (resolvedCount + 1).coerceAtMost(itinerary.stops.size)
        return "Stop $currentNumber of ${itinerary.stops.size}"
    }

    fun loadHomeData() {

        // Ignore a second tap on "Try again" while a load is running.
        if (_isLoading.value) return

        viewModelScope.launch {

            _isLoading.value = true

            fetchHomeData()

            _isLoading.value = false
        }
    }

    /**
     * Pull-to-refresh: reloads sites, places and tags and re-checks the trip
     * to resume, keeping what's already shown until the new data arrives.
     */
    fun refresh() {

        if (_isRefreshing.value || _isLoading.value) return

        viewModelScope.launch {

            _isRefreshing.value = true

            try {
                val trips = async { loadTrips() }
                fetchHomeData()
                trips.await()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private suspend fun fetchHomeData() = coroutineScope {

        // The three reads are independent, so they run together — they
        // used to run one after another, tripling Home's first load.
        val sitesDeferred = async { historicalSiteRepository.getHistoricalSites() }
        val tagsDeferred = async { tagRepository.getTags() }
        val placesDeferred = async { placeRepository.getPlaces() }

        val fetchedSites = sitesDeferred.await()

        val fetchedTags = tagsDeferred.await()

        _allPlaces.value = placesDeferred.await()

        _allSites.value = fetchedSites

        // The Historical Sites filter only offers tags that describe
        // sites. Food & dining has its own category chip (Food & Drink)
        // for places, so a "Food & Dining" tag here only led to an empty
        // or confusing site list.
        _allTags.value = fetchedTags.filterNot { tag -> isFoodAndDiningTag(tag.name) }

        _tagsById.value = fetchedTags.associate { tag -> tag.id to tag.name }
    }

    // Matches "Food & Dining" and its obvious variants only — a site tag
    // that merely starts with "Food" (e.g. "Food Heritage") is kept.
    private fun isFoodAndDiningTag(name: String): Boolean {
        val normalized = name.lowercase().replace(Regex("\\s+"), " ").trim()
        return "dining" in normalized || normalized in setOf(
            "food", "foods", "food & drink", "food & drinks", "food and drink", "food and drinks"
        )
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onApplyTagFilters(tagIds: Set<String>) {
        _selectedTagIds.value = tagIds
    }

    fun onResetTagFilters() {
        _selectedTagIds.value = emptySet()
    }

    fun onPlaceCategorySelected(category: String) {
        _selectedPlaceCategory.value = category
    }

    fun onHomeSectionSelected(section: String) {
        _selectedHomeSection.value = section
    }

    fun tagNamesFor(site: HistoricalSite): List<String> {

        val currentTagsById = _tagsById.value

        return site.tagIds.mapNotNull { tagId -> currentTagsById[tagId] }
    }
}
