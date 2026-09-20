package com.histoury.app.data.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.histoury.app.data.model.HistoricalSite
import com.histoury.app.data.model.SiteContent
import com.histoury.app.data.repository.HistoricalSiteRepository
import com.histoury.app.data.repository.SiteContentRepository
import com.histoury.app.data.repository.TagRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

enum class SiteDetailsTab {
    OVERVIEW, TIMELINE, STORIES, SOURCES
}

data class SiteDetailsUiState(
    val isLoading: Boolean = false,
    val site: HistoricalSite? = null,
    val content: SiteContent? = null,
    val selectedTab: SiteDetailsTab = SiteDetailsTab.OVERVIEW,
    val distanceMeters: Float? = null,
    val isBookmarked: Boolean = false,
    val tagNames: List<String> = emptyList(),
    val errorMessage: String? = null
)

class SiteDetailsViewModel(application: Application) : AndroidViewModel(application) {

    private val historicalSiteRepository = HistoricalSiteRepository()

    private val siteContentRepository = SiteContentRepository()

    private val tagRepository = TagRepository()

    private val fusedLocationClient =
        LocationServices.getFusedLocationProviderClient(application)

    private val _uiState = MutableStateFlow(SiteDetailsUiState())
    val uiState: StateFlow<SiteDetailsUiState> = _uiState.asStateFlow()

    fun loadSite(documentId: String) {

        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null
            )

            val site = historicalSiteRepository.getHistoricalSiteById(documentId)

            if (site == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "This historical site could not be found."
                )
                return@launch
            }

            val content = siteContentRepository.getSiteContent(site.siteId)

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                site = site,
                content = content
            )

            loadDistanceToSite(site)
            loadTagNames(site)
        }
    }

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
            // Tags are a nice-to-have on this screen; ignore failures.
        }
    }

    // Best-effort, one-shot straight-line distance from the user's last
    // known location. Never prompts for permission itself — Home already
    // handles that flow — and silently no-ops if it's unavailable, so it
    // can't interfere with anything else on this screen.
    @SuppressLint("MissingPermission")
    private fun loadDistanceToSite(site: HistoricalSite) {

        val siteLatitude = site.latitude
        val siteLongitude = site.longitude

        if (siteLatitude == null || siteLongitude == null) {
            return
        }

        viewModelScope.launch {

            try {

                val lastLocation = fusedLocationClient.lastLocation.await() ?: return@launch

                val results = FloatArray(1)

                Location.distanceBetween(
                    lastLocation.latitude,
                    lastLocation.longitude,
                    siteLatitude,
                    siteLongitude,
                    results
                )

                _uiState.value = _uiState.value.copy(distanceMeters = results[0])

            } catch (e: SecurityException) {
                // Location permission not granted — leave distance blank.
            } catch (e: Exception) {
                // Best-effort only; ignore any other failure.
            }
        }
    }

    fun onTabSelected(tab: SiteDetailsTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    // Local-only save toggle. Histoury doesn't have a persisted "saved
    // sites" feature yet, so this intentionally doesn't touch Firestore —
    // it just drives the bookmark icon's state for this screen session.
    fun onBookmarkToggle() {
        _uiState.value = _uiState.value.copy(isBookmarked = !_uiState.value.isBookmarked)
    }
}
