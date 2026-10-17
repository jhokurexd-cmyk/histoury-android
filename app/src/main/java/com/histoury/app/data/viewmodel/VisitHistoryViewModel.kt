package com.histoury.app.data.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.histoury.app.data.model.HistoricalSite
import com.histoury.app.data.model.VisitHistory
import com.histoury.app.data.repository.HistoricalSiteRepository
import com.histoury.app.data.repository.AuthRepository
import com.histoury.app.data.repository.VisitHistoryEvents
import com.histoury.app.data.repository.VisitHistoryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

data class VisitedSiteEntry(
    val visit: VisitHistory,
    val site: HistoricalSite
)

data class VisitHistoryUiState(
    val isLoading: Boolean = false,
    val visitedEntries: List<VisitedSiteEntry> = emptyList(),
    val yetToExploreSites: List<HistoricalSite> = emptyList(),
    /** Set when a refresh failed; the list keeps what it last showed. */
    val refreshFailed: Boolean = false
)

class VisitHistoryViewModel : ViewModel() {

    private val visitHistoryRepository = VisitHistoryRepository()

    private val historicalSiteRepository = HistoricalSiteRepository()

    private val _uiState = MutableStateFlow(VisitHistoryUiState())
    val uiState: StateFlow<VisitHistoryUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    /** The account the list on screen belongs to. */
    private var loadedForUid: String? = null

    init {
        // Load for whoever Firebase Auth says is signed in — including the
        // moment a saved session finishes restoring after an update or
        // restart — and start over if the account changes.
        viewModelScope.launch {
            AuthRepository().authStateUidFlow()
                .distinctUntilChanged()
                .collect { uid ->
                    if (uid != loadedForUid) {
                        // Drop a load still running for the previous account.
                        loadJob?.cancel()
                        loadedForUid = uid
                        _uiState.value = VisitHistoryUiState()
                    }
                    if (uid != null) loadVisitHistory()
                }
        }

        // A visit recorded anywhere in the app (arrival, geofence, AR)
        // refreshes the list.
        viewModelScope.launch {
            VisitHistoryEvents.changes.collect { loadVisitHistory() }
        }
    }

    fun loadVisitHistory() {

        // One load at a time; a newer request replaces an older one.
        loadJob?.cancel()

        loadJob = viewModelScope.launch {

            // Spinner only on the first load; later refreshes happen in place
            // instead of blanking the list.
            val hasContent = _uiState.value.visitedEntries.isNotEmpty() ||
                _uiState.value.yetToExploreSites.isNotEmpty()
            if (!hasContent) {
                _uiState.value = _uiState.value.copy(isLoading = true)
            }

            val visitsDeferred = async { visitHistoryRepository.loadVisitHistory() }
            val sitesDeferred = async { historicalSiteRepository.getHistoricalSites() }

            val visitsResult = visitsDeferred.await()
            val allSites = sitesDeferred.await()

            // Couldn't read the visits, or the sites to show them against:
            // keep what's on screen instead of replacing it with "nothing
            // visited yet".
            val visits = visitsResult.getOrNull()
            // (Sites missing while there ARE visits is a failed read too —
            // there's always at least one site to show a visit against.)
            if (visits == null || (allSites.isEmpty() && (hasContent || visits.isNotEmpty()))) {
                _uiState.value = _uiState.value.copy(isLoading = false, refreshFailed = true)
                return@launch
            }

            // A visit's siteId is the site's slug on most records and its
            // document id on sites saved without one; match either.
            val sitesByKey = HashMap<String, HistoricalSite>()
            allSites.forEach { site ->
                if (site.documentId.isNotBlank()) sitesByKey[site.documentId] = site
                if (site.siteId.isNotBlank()) sitesByKey[site.siteId] = site
            }

            // Most recent visit per site only, so revisiting the same place
            // doesn't show duplicate "Your Journey" rows.
            val latestVisitPerSite = visits
                .mapNotNull { visit -> sitesByKey[visit.siteId]?.let { site -> site to visit } }
                .groupBy({ it.first.documentId }, { it })
                .mapValues { (_, pairs) ->
                    val latest = pairs.maxByOrNull { (_, visit) ->
                        (visit.visitDate as? Timestamp)?.toDate()?.time ?: 0L
                    }
                    // Reviewed on ANY visit counts: a revisit used to bring
                    // "Leave a Review" back for a site already reviewed.
                    val reviewedBefore = pairs.any { (_, visit) -> visit.reviewSubmitted }
                    latest?.let { (site, visit) ->
                        site to if (reviewedBefore) visit.copy(reviewSubmitted = true) else visit
                    }
                }

            val visitedEntries = latestVisitPerSite.values
                .filterNotNull()
                .map { (site, visit) -> VisitedSiteEntry(visit = visit, site = site) }
                .sortedByDescending { entry ->
                    (entry.visit.visitDate as? Timestamp)?.toDate()?.time ?: 0L
                }

            val visitedDocIds = latestVisitPerSite.keys

            val yetToExploreSites = allSites.filter { site ->
                site.documentId !in visitedDocIds
            }

            _uiState.value = VisitHistoryUiState(
                isLoading = false,
                visitedEntries = visitedEntries,
                yetToExploreSites = yetToExploreSites
            )
        }
    }
}
