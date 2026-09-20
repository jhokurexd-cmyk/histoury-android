package com.histoury.app.data.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.histoury.app.data.model.HistoricalSite
import com.histoury.app.data.model.Place
import com.histoury.app.data.model.Tag
import com.histoury.app.data.repository.HistoricalSiteRepository
import com.histoury.app.data.repository.PlaceRepository
import com.histoury.app.data.repository.TagRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted

class HomeViewModel : ViewModel() {

    private val historicalSiteRepository = HistoricalSiteRepository()

    private val tagRepository = TagRepository()

    private val placeRepository = PlaceRepository()

    private val _allSites = MutableStateFlow<List<HistoricalSite>>(emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

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
    }

    fun loadHomeData() {

        viewModelScope.launch {

            _isLoading.value = true

            val fetchedSites = historicalSiteRepository.getHistoricalSites()

            val fetchedTags = tagRepository.getTags()

            _allPlaces.value = placeRepository.getPlaces()

            _allSites.value = fetchedSites

            _allTags.value = fetchedTags

            _tagsById.value = fetchedTags.associate { tag -> tag.id to tag.name }

            _isLoading.value = false
        }
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
