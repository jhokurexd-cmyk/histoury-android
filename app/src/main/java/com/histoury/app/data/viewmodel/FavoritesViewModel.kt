package com.histoury.app.data.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.histoury.app.data.model.HistoricalSite
import com.histoury.app.data.repository.FavoritesRepository
import com.histoury.app.data.repository.HistoricalSiteRepository
import com.histoury.app.data.repository.VisitHistoryRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FavoriteSiteEntry(
    val site: HistoricalSite,
    /** Visited in person, so Site Information is unlocked (same rule as the Site Library). */
    val isUnlocked: Boolean
)

data class FavoritesUiState(
    val isLoading: Boolean = true,
    val entries: List<FavoriteSiteEntry> = emptyList(),
    val errorMessage: String? = null
)

/**
 * Menu > Favorites. The list itself comes live from [FavoritesRepository],
 * so removing a heart here (or anywhere else) updates it immediately; the
 * site catalog and visit history are loaded once to render it.
 */
class FavoritesViewModel : ViewModel() {

    private val historicalSiteRepository = HistoricalSiteRepository()
    private val visitHistoryRepository = VisitHistoryRepository()

    private data class Catalog(
        val sites: Map<String, HistoricalSite> = emptyMap(),
        val visitedSiteIds: Set<String> = emptySet(),
        val isLoading: Boolean = true,
        val error: String? = null
    )

    private val catalog = MutableStateFlow(Catalog())

    val uiState: StateFlow<FavoritesUiState> = combine(
        FavoritesRepository.favoriteIds,
        FavoritesRepository.isLoaded,
        catalog
    ) { ids, favoritesLoaded, cat ->
        FavoritesUiState(
            isLoading = cat.isLoading || !favoritesLoaded,
            // Favorites whose site was unpublished or deleted are skipped,
            // not shown as broken cards.
            entries = ids.mapNotNull { id ->
                cat.sites[id]?.let { site ->
                    FavoriteSiteEntry(site = site, isUnlocked = site.siteId in cat.visitedSiteIds)
                }
            },
            errorMessage = cat.error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FavoritesUiState())

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            catalog.value = catalog.value.copy(isLoading = true, error = null)
            val sitesDeferred = async { historicalSiteRepository.getHistoricalSites() }
            val visitsDeferred = async { visitHistoryRepository.getVisitHistoryForCurrentUser() }
            val sites = sitesDeferred.await()
            val visits = visitsDeferred.await()
            catalog.value = Catalog(
                sites = sites.associateBy { it.documentId },
                visitedSiteIds = visits.map { it.siteId }.toSet(),
                isLoading = false,
                // getHistoricalSites() returns an empty list when it can't
                // reach the server and has nothing cached.
                error = if (sites.isEmpty()) "Couldn't load your favorites. Check your connection." else null
            )
        }
    }
}
