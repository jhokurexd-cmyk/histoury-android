package com.histoury.app.data.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.histoury.app.data.model.HistoricalSite
import com.histoury.app.data.repository.HistoricalSiteRepository
import com.histoury.app.data.repository.ReviewRepository
import com.histoury.app.data.repository.VisitHistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RateReviewUiState(
    val isLoading: Boolean = false,
    val site: HistoricalSite? = null,
    val rating: Int = 0,
    val reviewText: String = "",
    val isSubmitting: Boolean = false,
    val submitSucceeded: Boolean = false,
    val errorMessage: String? = null
)

private const val MAX_REVIEW_LENGTH = 500

class RateReviewViewModel : ViewModel() {

    private val historicalSiteRepository = HistoricalSiteRepository()

    private val reviewRepository = ReviewRepository()

    private val visitHistoryRepository = VisitHistoryRepository()

    private val _uiState = MutableStateFlow(RateReviewUiState())
    val uiState: StateFlow<RateReviewUiState> = _uiState.asStateFlow()

    fun loadSite(siteDocumentId: String) {

        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(isLoading = true)

            val site = historicalSiteRepository.getHistoricalSiteById(siteDocumentId)

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                site = site
            )
        }
    }

    fun onRatingChanged(rating: Int) {
        _uiState.value = _uiState.value.copy(rating = rating, errorMessage = null)
    }

    fun onReviewTextChanged(text: String) {
        if (text.length <= MAX_REVIEW_LENGTH) {
            _uiState.value = _uiState.value.copy(reviewText = text)
        }
    }

    fun submitReview(visitId: String) {

        val currentState = _uiState.value

        // One submission at a time: a double tap must not create two
        // reviews. (The server also removes duplicates — see contentGuard.js.)
        if (currentState.isSubmitting) return

        val site = currentState.site

        if (site == null) {
            _uiState.value = currentState.copy(
                errorMessage = "Site information isn't loaded yet."
            )
            return
        }

        if (currentState.rating == 0) {
            _uiState.value = currentState.copy(
                errorMessage = "Please select a star rating."
            )
            return
        }

        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(
                isSubmitting = true,
                errorMessage = null
            )

            val succeeded = reviewRepository.submitReview(
                siteId = site.siteId,
                visitId = visitId,
                arExperienceRating = currentState.rating,
                reviewText = currentState.reviewText
            )

            if (succeeded) {

                visitHistoryRepository.markReviewSubmitted(visitId)

                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    submitSucceeded = true
                )

            } else {

                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    errorMessage = "Couldn't submit your review. Please try again."
                )
            }
        }
    }
}
