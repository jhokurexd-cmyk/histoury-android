package com.histoury.app.data.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.histoury.app.data.model.HistoricalSite
import com.histoury.app.data.model.VisitHistory
import com.histoury.app.data.repository.HistoricalSiteRepository
import com.histoury.app.data.repository.VisitHistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VisitedSiteEntry(
    val visit: VisitHistory,
    val site: HistoricalSite
)

data class VisitHistoryUiState(
    val isLoading: Boolean = false,
    val visitedEntries: List<VisitedSiteEntry> = emptyList(),
    val yetToExploreSites: List<HistoricalSite> = emptyList()
)

class VisitHistoryViewModel : ViewModel() {

    private val visitHistoryRepository = VisitHistoryRepository()

    private val historicalSiteRepository = HistoricalSiteRepository()

    private val _uiState = MutableStateFlow(VisitHistoryUiState())
    val uiState: StateFlow<VisitHistoryUiState> = _uiState.asStateFlow()

    fun loadVisitHistory() {

        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(isLoading = true)

            val visits = visitHistoryRepository.getVisitHistoryForCurrentUser()

            val allSites = historicalSiteRepository.getHistoricalSites()

            val sitesBySlug = allSites.associateBy { site -> site.siteId }

            // Most recent visit per site only, so revisiting the same place
            // doesn't show duplicate "Your Journey" rows.
            val latestVisitPerSite = visits
                .groupBy { visit -> visit.siteId }
                .mapValues { (_, visitsForSite) ->
                    visitsForSite.maxByOrNull { visit ->
                        (visit.visitDate as? Timestamp)?.toDate()?.time ?: 0L
                    }
                }

            val visitedEntries = latestVisitPerSite.values
                .filterNotNull()
                .mapNotNull { visit ->
                    val site = sitesBySlug[visit.siteId] ?: return@mapNotNull null
                    VisitedSiteEntry(visit = visit, site = site)
                }
                .sortedByDescending { entry ->
                    (entry.visit.visitDate as? Timestamp)?.toDate()?.time ?: 0L
                }

            val visitedSiteIds = latestVisitPerSite.keys

            val yetToExploreSites = allSites.filter { site ->
                site.siteId !in visitedSiteIds
            }

            _uiState.value = VisitHistoryUiState(
                isLoading = false,
                visitedEntries = visitedEntries,
                yetToExploreSites = yetToExploreSites
            )
        }
    }
}
