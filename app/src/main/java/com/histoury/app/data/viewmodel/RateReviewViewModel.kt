package com.histoury.app.data.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.histoury.app.data.model.HistoricalSite
import com.histoury.app.data.repository.HistoricalSiteRepository
import com.histoury.app.data.repository.ReviewRepository
import com.histoury.app.data.repository.VisitHistoryRepository
import com.histoury.app.util.ReviewTextFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RateReviewUiState(
    val isLoading: Boolean = false,
    val site: HistoricalSite? = null,
    val rating: Int = 0,
    val reviewText: String = "",
    /** The visitor already reviewed this site: submitting updates that review. */
    val isUpdatingExisting: Boolean = false,
    val isSubmitting: Boolean = false,
    val submitSucceeded: Boolean = false,
    val errorMessage: String? = null,
    /** Profanity, a link or contact details in the text — shown under the field as they type. */
    val textError: String? = null
)

private const val MAX_REVIEW_LENGTH = 500

class RateReviewViewModel : ViewModel() {

    private val historicalSiteRepository = HistoricalSiteRepository()

    private val reviewRepository = ReviewRepository()

    private val _uiState = MutableStateFlow(RateReviewUiState())
    val uiState: StateFlow<RateReviewUiState> = _uiState.asStateFlow()

    fun loadSite(siteDocumentId: String) {

        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(isLoading = true)

            val site = historicalSiteRepository.getHistoricalSiteById(siteDocumentId)

            // One review per visitor per site: if they've reviewed it before
            // (on another visit, or from the post-AR prompt), start from that
            // review so submitting updates it instead of adding another.
            val existing = site?.let { reviewRepository.getMyReviewForSite(it.siteId) }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                site = site,
                isUpdatingExisting = existing != null,
                rating = existing?.rating?.toInt()?.coerceIn(0, 5) ?: _uiState.value.rating,
                reviewText = existing?.reviewText ?: _uiState.value.reviewText,
                textError = ReviewTextFilter.check(existing?.reviewText ?: _uiState.value.reviewText)?.message
            )
        }
    }

    fun onRatingChanged(rating: Int) {
        _uiState.value = _uiState.value.copy(rating = rating, errorMessage = null)
    }

    fun onReviewTextChanged(text: String) {
        if (text.length <= MAX_REVIEW_LENGTH) {
            _uiState.value = _uiState.value.copy(
                reviewText = text,
                textError = ReviewTextFilter.check(text)?.message
            )
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

        // Same check the server makes; the message is already under the field.
        ReviewTextFilter.check(currentState.reviewText)?.let { problem ->
            _uiState.value = currentState.copy(textError = problem.message)
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

            // The server marks this visit as reviewed.
            val result = reviewRepository.saveSiteReview(
                siteId = site.siteId,
                rating = currentState.rating,
                reviewText = currentState.reviewText,
                visitId = visitId,
                source = com.histoury.app.data.repository.ReviewSource.VISIT
            )

            _uiState.value = when (result) {
                is com.histoury.app.data.repository.ReviewActionResult.Success -> _uiState.value.copy(
                    isSubmitting = false,
                    submitSucceeded = true
                )
                is com.histoury.app.data.repository.ReviewActionResult.Failed -> _uiState.value.copy(
                    isSubmitting = false,
                    errorMessage = result.message
                )
            }
        }
    }
}
