package com.histoury.app.data.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.histoury.app.data.model.HistoricalSite
import com.histoury.app.data.repository.HistoricalSiteRepository
import com.histoury.app.data.repository.VisitHistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SiteLibraryEntry(
    val site: HistoricalSite,
    val isUnlocked: Boolean
)

data class SiteLibraryUiState(
    val isLoading: Boolean = false,
    val entries: List<SiteLibraryEntry> = emptyList()
)

/**
 * Backs the Menu > Site Information screen: every historical site, each
 * marked locked/unlocked based on whether the signed-in user has an actual
 * recorded visit there. Unlock is permanent — once visited, a site's info
 * stays viewable from anywhere, not just in person.
 */
class SiteLibraryViewModel : ViewModel() {

    private val historicalSiteRepository = HistoricalSiteRepository()

    private val visitHistoryRepository = VisitHistoryRepository()

    private val _uiState = MutableStateFlow(SiteLibraryUiState())
    val uiState: StateFlow<SiteLibraryUiState> = _uiState.asStateFlow()

    fun loadSites() {

        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(isLoading = true)

            val allSites = historicalSiteRepository.getHistoricalSites()

            val visits = visitHistoryRepository.getVisitHistoryForCurrentUser()

            val visitedSiteIds = visits.map { visit -> visit.siteId }.toSet()

            val entries = allSites
                .sortedByDescending { site -> site.siteId in visitedSiteIds }
                .map { site ->
                    SiteLibraryEntry(
                        site = site,
                        isUnlocked = site.siteId in visitedSiteIds
                    )
                }

            _uiState.value = SiteLibraryUiState(
                isLoading = false,
                entries = entries
            )
        }
    }
}
