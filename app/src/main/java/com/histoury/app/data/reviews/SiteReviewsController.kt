package com.histoury.app.data.reviews

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.histoury.app.data.model.Review
import com.histoury.app.data.repository.ReviewActionResult
import com.histoury.app.data.repository.ReviewReportReason
import com.histoury.app.data.repository.ReviewRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** How the review list is ordered. */
enum class ReviewSort(val label: String) {
    MOST_HELPFUL("Most helpful"),
    MOST_RECENT("Most recent"),
    MOST_FAVORABLE("Most favorable"),
    MOST_CRITICAL("Most critical")
}

data class SiteReviewsState(
    /** The site's business slug (historical_sites.siteId), or null before loading. */
    val siteId: String? = null,
    val reviews: List<Review> = emptyList(),
    /**
     * [reviews] in the chosen [sort] order — what the list shows. The order
     * is only worked out when the list loads or the sort changes, so a
     * Helpful tap updates the count without the card jumping away.
     */
    val displayedReviews: List<Review> = emptyList(),
    val sort: ReviewSort = ReviewSort.MOST_HELPFUL,
    /** Only reviews with this many stars (1..5), or null for all. */
    val starFilter: Int? = null,
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,

    val currentUserId: String? = null,

    val helpfulReviewIds: Set<String> = emptySet(),
    val reportedReviewIds: Set<String> = emptySet(),
    /** Reviews with a helpful toggle still on its way to the server; taps on them are ignored. */
    val pendingHelpfulIds: Set<String> = emptySet(),

    /** Non-null while the report dialog is open. */
    val reportTarget: Review? = null,
    val isSubmittingReport: Boolean = false,
    val reportError: String? = null,

    /** One-off message for a toast; cleared by [SiteReviewsController.consumeMessage]. */
    val message: String? = null
)

/**
 * Everything the Site Rankings > Reviews sheet needs: loading a site's
 * reviews, and the visitor's Helpful votes and reports on them.
 *
 * Runs in the owning ViewModel's scope rather than being one itself, so the
 * review logic stays in one reusable place instead of inside a screen.
 */
class SiteReviewsController(
    private val scope: CoroutineScope,
    private val repository: ReviewRepository = ReviewRepository()
) {

    private val _state = MutableStateFlow(SiteReviewsState())
    val state: StateFlow<SiteReviewsState> = _state.asStateFlow()

    private var loadJob: Job? = null

    /** Loads (or reloads) the reviews for [siteId]. Cancels any load already running. */
    fun load(siteId: String) {

        if (siteId.isBlank()) return

        val sameSite = _state.value.siteId == siteId

        _state.value = _state.value.copy(
            siteId = siteId,
            // A refresh of the same site keeps what's on screen instead of
            // blanking it behind a spinner.
            reviews = if (sameSite) _state.value.reviews else emptyList(),
            displayedReviews = if (sameSite) _state.value.displayedReviews else emptyList(),
            // A star filter is about this site's reviews; another site starts on all.
            starFilter = if (sameSite) _state.value.starFilter else null,
            isLoading = !sameSite || _state.value.reviews.isEmpty(),
            loadFailed = false,
            currentUserId = FirebaseAuth.getInstance().currentUser?.uid,
            helpfulReviewIds = if (sameSite) _state.value.helpfulReviewIds else emptySet(),
            reportedReviewIds = if (sameSite) _state.value.reportedReviewIds else emptySet(),
            pendingHelpfulIds = emptySet()
        )

        loadJob?.cancel()
        loadJob = scope.launch {

            val reviews = repository.getReviewsForSite(siteId)
            if (_state.value.siteId != siteId) return@launch

            _state.value = _state.value.copy(
                reviews = reviews,
                displayedReviews = reviews.shownFor(_state.value.starFilter, _state.value.sort),
                isLoading = false,
                loadFailed = reviews.isEmpty() && !repository.lastLoadSucceeded
            )

            // Which of these the visitor already voted on or reported, so
            // the buttons open in the right state. Fetched after the list is
            // on screen rather than holding it back.
            val interactions = repository.getMyInteractions(reviews.map { it.id })
            if (_state.value.siteId != siteId) return@launch
            val latest = _state.value
            _state.value = latest.copy(
                // A vote made while this was loading wins over the fetch.
                helpfulReviewIds = interactions.helpfulReviewIds
                    .filterNot { it in latest.pendingHelpfulIds }.toSet() + latest.helpfulReviewIds,
                reportedReviewIds = interactions.reportedReviewIds + latest.reportedReviewIds
            )
        }
    }

    /** Re-orders the list. Keeps the choice when switching to another site. */
    fun setSort(sort: ReviewSort) {
        val state = _state.value
        if (state.sort == sort) return
        _state.value = state.copy(sort = sort, displayedReviews = state.reviews.shownFor(state.starFilter, sort))
    }

    /**
     * Shows only the reviews with [star] stars (a tap on that row of the
     * 5..1 bars), or all of them again with null.
     */
    fun setStarFilter(star: Int?) {
        val state = _state.value
        if (state.starFilter == star) return
        _state.value = state.copy(starFilter = star, displayedReviews = state.reviews.shownFor(star, state.sort))
    }

    private fun List<Review>.shownFor(star: Int?, sort: ReviewSort): List<Review> =
        (if (star == null) this else filter { it.rating.toInt() == star }).sortedFor(sort)

    fun refresh() {
        _state.value.siteId?.let { load(it) }
    }

    fun clear() {
        loadJob?.cancel()
        _state.value = SiteReviewsState(sort = _state.value.sort)
    }

    // ----------------------------------------------------------- helpful

    /**
     * Flips the visitor's helpful vote straight away, then confirms with the
     * server. One request per review at a time: further taps are ignored
     * until it answers, so rapid tapping can't queue up votes. On failure
     * the card goes back to how it was.
     */
    fun toggleHelpful(review: Review) {

        val state = _state.value
        val reviewId = review.id
        if (reviewId.isBlank() || reviewId in state.pendingHelpfulIds) return
        if (state.currentUserId == null) {
            _state.value = state.copy(message = "Sign in to mark reviews as helpful.")
            return
        }
        if (review.userId == state.currentUserId) return

        val wasHelpful = reviewId in state.helpfulReviewIds
        val originalLikes = review.likes
        val optimisticLikes = (originalLikes + if (wasHelpful) -1 else 1).coerceAtLeast(0)

        _state.value = state.copy(
            pendingHelpfulIds = state.pendingHelpfulIds + reviewId,
            helpfulReviewIds = if (wasHelpful) state.helpfulReviewIds - reviewId else state.helpfulReviewIds + reviewId,
            reviews = state.reviews.withLikes(reviewId, optimisticLikes),
            displayedReviews = state.displayedReviews.withLikes(reviewId, optimisticLikes)
        )

        scope.launch {

            val result = repository.toggleHelpful(reviewId)
            val current = _state.value

            _state.value = when (result) {
                // The server's count is the truth: it includes other
                // visitors' votes made since the list loaded.
                is ReviewActionResult.Success -> {
                    val (helpful, likes) = result.value
                    current.copy(
                        pendingHelpfulIds = current.pendingHelpfulIds - reviewId,
                        helpfulReviewIds = if (helpful) current.helpfulReviewIds + reviewId else current.helpfulReviewIds - reviewId,
                        reviews = current.reviews.withLikes(reviewId, likes),
                        displayedReviews = current.displayedReviews.withLikes(reviewId, likes)
                    )
                }
                is ReviewActionResult.Failed -> current.copy(
                    pendingHelpfulIds = current.pendingHelpfulIds - reviewId,
                    helpfulReviewIds = if (wasHelpful) current.helpfulReviewIds + reviewId else current.helpfulReviewIds - reviewId,
                    reviews = current.reviews.withLikes(reviewId, originalLikes),
                    displayedReviews = current.displayedReviews.withLikes(reviewId, originalLikes),
                    message = result.message
                )
            }
        }
    }

    private fun List<Review>.withLikes(reviewId: String, likes: Int) =
        map { if (it.id == reviewId) it.copy(likes = likes) else it }

    private fun Review.createdMillis(): Long = (createdAt as? Timestamp)?.toDate()?.time ?: 0L

    /**
     * - Most helpful: most Helpful votes, then the more detailed review.
     * - Most recent: newest first.
     * - Most favorable: highest stars, the most helpful of those first.
     * - Most critical: lowest stars, the most helpful of those first, so the
     *   useful criticism comes before one-line complaints.
     * Ties always fall back to newest first.
     */
    private fun List<Review>.sortedFor(sort: ReviewSort): List<Review> = when (sort) {
        ReviewSort.MOST_HELPFUL -> sortedWith(
            compareByDescending<Review> { it.likes }
                .thenByDescending { it.reviewText.length }
                .thenByDescending { it.createdMillis() }
        )
        ReviewSort.MOST_RECENT -> sortedByDescending { it.createdMillis() }
        ReviewSort.MOST_FAVORABLE -> sortedWith(
            compareByDescending<Review> { it.rating }
                .thenByDescending { it.likes }
                .thenByDescending { it.createdMillis() }
        )
        ReviewSort.MOST_CRITICAL -> sortedWith(
            compareBy<Review> { it.rating }
                .thenByDescending { it.likes }
                .thenByDescending { it.createdMillis() }
        )
    }

    // ----------------------------------------------------------- reporting

    fun openReport(review: Review) {
        val state = _state.value
        if (state.currentUserId == null) {
            _state.value = state.copy(message = "Sign in to report reviews.")
            return
        }
        if (review.id in state.reportedReviewIds) {
            _state.value = state.copy(message = "You've already reported this review.")
            return
        }
        _state.value = state.copy(reportTarget = review, reportError = null)
    }

    fun dismissReport() {
        if (_state.value.isSubmittingReport) return
        _state.value = _state.value.copy(reportTarget = null, reportError = null)
    }

    fun submitReport(reason: ReviewReportReason, details: String) {

        val state = _state.value
        val review = state.reportTarget ?: return
        if (state.isSubmittingReport) return

        if (reason == ReviewReportReason.OTHER && details.isBlank()) {
            _state.value = state.copy(reportError = "Please tell us briefly what's wrong.")
            return
        }

        _state.value = state.copy(isSubmittingReport = true, reportError = null)

        scope.launch {

            val result = repository.reportReview(review.id, reason, details)
            val current = _state.value

            _state.value = when (result) {
                is ReviewActionResult.Success -> current.copy(
                    isSubmittingReport = false,
                    reportTarget = null,
                    reportedReviewIds = current.reportedReviewIds + review.id,
                    message = "Thanks for letting us know. Our team will review it."
                )
                is ReviewActionResult.Failed -> if (result.alreadyDone) {
                    current.copy(
                        isSubmittingReport = false,
                        reportTarget = null,
                        reportedReviewIds = current.reportedReviewIds + review.id,
                        message = result.message
                    )
                } else {
                    current.copy(isSubmittingReport = false, reportError = result.message)
                }
            }
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
