package com.histoury.app.data.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.histoury.app.data.model.HistoricalSite
import com.histoury.app.data.model.Review
import com.histoury.app.data.repository.HistoricalSiteRepository
import com.histoury.app.data.repository.ReviewRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class RankingSortMode {
    TOP_RATED, MOST_REVIEWED
}

data class RankingsUiState(
    val isLoading: Boolean = false,
    val sites: List<HistoricalSite> = emptyList(),
    val sortMode: RankingSortMode = RankingSortMode.TOP_RATED,
    val totalReviews: Int = 0,
    val averageRating: Double = 0.0,
    val totalSites: Int = 0,
    // Reviews bottom sheet: non-null site means the sheet is open
    val reviewsSite: HistoricalSite? = null,
    val siteReviews: List<Review> = emptyList(),
    val isLoadingReviews: Boolean = false
)

class RankingsViewModel : ViewModel() {

    private val historicalSiteRepository = HistoricalSiteRepository()

    private val reviewRepository = ReviewRepository()

    private var allSites: List<HistoricalSite> = emptyList()

    private val _uiState = MutableStateFlow(RankingsUiState())
    val uiState: StateFlow<RankingsUiState> = _uiState.asStateFlow()

    fun loadRankings() {

        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(isLoading = true)

            allSites = historicalSiteRepository.getHistoricalSites()

            val totalReviews = allSites.sumOf { it.reviewCount }

            val averageRating = if (allSites.isNotEmpty()) {
                allSites.map { it.averageRating }.average()
            } else {
                0.0
            }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                sites = sortSites(allSites, _uiState.value.sortMode),
                totalReviews = totalReviews,
                averageRating = averageRating,
                totalSites = allSites.size
            )
        }
    }

    fun onSortModeChanged(mode: RankingSortMode) {

        _uiState.value = _uiState.value.copy(
            sortMode = mode,
            sites = sortSites(allSites, mode)
        )
    }

    /** Opens the reviews sheet for a site and fetches its reviews. */
    fun openSiteReviews(site: HistoricalSite) {

        _uiState.value = _uiState.value.copy(
            reviewsSite = site,
            siteReviews = emptyList(),
            isLoadingReviews = true
        )

        viewModelScope.launch {

            val reviews = reviewRepository.getReviewsForSite(site.siteId)

            // Only apply if the sheet is still open for this same site.
            if (_uiState.value.reviewsSite?.siteId == site.siteId) {
                _uiState.value = _uiState.value.copy(
                    siteReviews = reviews,
                    isLoadingReviews = false
                )
            }
        }
    }

    fun closeSiteReviews() {
        _uiState.value = _uiState.value.copy(
            reviewsSite = null,
            siteReviews = emptyList(),
            isLoadingReviews = false
        )
    }

    private fun sortSites(
        sites: List<HistoricalSite>,
        mode: RankingSortMode
    ): List<HistoricalSite> {

        return when (mode) {
            RankingSortMode.TOP_RATED ->
                sites.sortedByDescending { it.averageRating }

            RankingSortMode.MOST_REVIEWED ->
                sites.sortedByDescending { it.reviewCount }
        }
    }
}
