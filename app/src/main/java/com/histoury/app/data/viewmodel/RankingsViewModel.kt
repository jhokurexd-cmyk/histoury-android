package com.histoury.app.data.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.histoury.app.data.model.HistoricalSite
import com.histoury.app.data.repository.HistoricalSiteRepository
import com.histoury.app.data.reviews.SiteReviewsController
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
    // Reviews bottom sheet: non-null site means the sheet is open. The
    // reviews themselves live in [RankingsViewModel.reviews].
    val reviewsSite: HistoricalSite? = null
)

class RankingsViewModel : ViewModel() {

    private val historicalSiteRepository = HistoricalSiteRepository()

    private var allSites: List<HistoricalSite> = emptyList()

    private val _uiState = MutableStateFlow(RankingsUiState())
    val uiState: StateFlow<RankingsUiState> = _uiState.asStateFlow()

    /**
     * The open sheet's reviews, Helpful votes and reports — the same shared
     * implementation Site Information uses.
     */
    val reviews = SiteReviewsController(viewModelScope)

    fun loadRankings() {

        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(isLoading = true)

            allSites = historicalSiteRepository.getHistoricalSites()

            val totalReviews = allSites.sumOf { it.reviewCount }

            // Average over every published review, not over sites: a site
            // nobody has rated yet has averageRating 0.0, and averaging
            // per site counted each of those as a 0-star rating, dragging
            // the figure down. Weighting by review count also keeps one
            // single-review site from counting as much as a busy one.
            val ratedSites = allSites.filter { it.reviewCount > 0 && it.averageRating > 0.0 }
            val ratedReviewCount = ratedSites.sumOf { it.reviewCount }
            val averageRating = if (ratedReviewCount > 0) {
                ratedSites.sumOf { it.averageRating * it.reviewCount } / ratedReviewCount
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
        _uiState.value = _uiState.value.copy(reviewsSite = site)
        reviews.load(site.siteId)
    }

    fun closeSiteReviews() {
        _uiState.value = _uiState.value.copy(reviewsSite = null)
        reviews.clear()
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
