package com.histoury.app.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.histoury.app.data.model.Review
import kotlinx.coroutines.tasks.await

/** Why a visitor is reporting a review. [wireValue] must match REPORT_REASONS in reviewModeration.js. */
enum class ReviewReportReason(val wireValue: String, val label: String) {
    INAPPROPRIATE_CONTENT("inappropriate_content", "Inappropriate content"),
    SPAM("spam", "Spam"),
    OFFENSIVE_LANGUAGE("offensive_language", "Offensive language"),
    MISLEADING_INFORMATION("misleading_information", "Misleading information"),
    OTHER("other", "Other")
}

/** What the signed-in visitor has already done to a set of reviews. */
data class ReviewInteractions(
    val helpfulReviewIds: Set<String> = emptySet(),
    val reportedReviewIds: Set<String> = emptySet()
)

/** Where a review was written from. Stored on the review for moderation context. */
enum class ReviewSource(val wireValue: String) {
    VISIT("visit"),
    AR_PROMPT("ar_prompt"),
    SITE_DETAILS("site_details")
}

data class SavedReview(
    val reviewId: String,
    /** True when an existing review was updated rather than a new one created. */
    val updated: Boolean,
    /** False when the review had been hidden by a moderator (it stays hidden). */
    val isPublished: Boolean
)

sealed interface ReviewActionResult<out T> {
    data class Success<T>(val value: T) : ReviewActionResult<T>
    /** [alreadyDone] is true when the server says this was already recorded (e.g. a second report). */
    data class Failed(val message: String, val alreadyDone: Boolean = false) : ReviewActionResult<Nothing>
}

class ReviewRepository {

    private val db = FirebaseFirestore.getInstance()

    // Same region as the other custom Cloud Functions. Helpful votes and
    // reports go through functions (reviewModeration.js) because they
    // change someone else's review and must be unique per visitor, which
    // only the server can guarantee.
    private val functions = FirebaseFunctions.getInstance("asia-southeast1")

    /**
     * Every review of a site, newest first — including ones whose comment a
     * moderator hid (status "hidden"). Those still show, with their stars
     * and a "comment hidden" note, because the rating itself still counts;
     * their text was already removed server-side.
     *
     * [siteId] is the business slug (e.g. "fort_santiago") — the same value
     * submitReview writes and the admin panel / rating triggers key on.
     *
     * Uses equality-only filters + client-side sorting so no composite
     * Firestore index is needed (same approach as VisitHistoryRepository).
     */
    /**
     * Whether the last [getReviewsForSite] reached Firestore (or its cache).
     * Lets a caller tell "this site has no reviews" from "couldn't load".
     */
    var lastLoadSucceeded: Boolean = true
        private set

    suspend fun getReviewsForSite(siteId: String): List<Review> {

        return try {

            val snapshot = db.collection("reviews")
                .whereEqualTo("siteId", siteId)
                .whereIn("status", listOf("published", "hidden"))
                .get()
                .await()

            lastLoadSucceeded = true

            snapshot.documents
                .mapNotNull { document ->
                    document.toObject(Review::class.java)
                        ?.copy(id = document.id)
                }
                .sortedByDescending { review ->
                    (review.createdAt as? Timestamp)?.toDate()?.time ?: 0L
                }

        } catch (e: Exception) {
            lastLoadSucceeded = false
            emptyList()
        }
    }

    /**
     * The signed-in visitor's own published review of [siteId], if any —
     * used to prefill the rating form so submitting updates it rather than
     * adding a second one.
     */
    suspend fun getMyReviewForSite(siteId: String): Review? {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return null
        return try {
            db.collection("reviews")
                .whereEqualTo("siteId", siteId)
                .whereEqualTo("userId", uid)
                .whereEqualTo("status", "published")
                .get()
                .await()
                .documents
                .mapNotNull { it.toObject(Review::class.java)?.copy(id = it.id) }
                .maxByOrNull { (it.updatedAt as? Timestamp)?.toDate()?.time ?: 0L }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Creates the visitor's review of a site, or updates the one they
     * already have. The single way a site review is written, whether it
     * comes from Visit History, Site Information or the post-AR prompt, so
     * every path gets the same one-review-per-site rule and feeds the same
     * rating statistics.
     *
     * Goes through the submitSiteReview Cloud Function, which enforces that
     * rule (and the 1–5 rating, daily limit and suspensions) on the server.
     * There is deliberately no direct-write fallback: the Firestore rules
     * refuse client review writes, because a direct write skipped all of it.
     */
    suspend fun saveSiteReview(
        siteId: String,
        rating: Int,
        reviewText: String,
        visitId: String = "",
        source: ReviewSource = ReviewSource.VISIT
    ): ReviewActionResult<SavedReview> {

        if (FirebaseAuth.getInstance().currentUser == null) {
            return ReviewActionResult.Failed("Sign in to leave a review.")
        }

        return try {
            val data = functions.getHttpsCallable("submitSiteReview")
                .call(
                    mapOf(
                        "siteId" to siteId,
                        "rating" to rating,
                        "reviewText" to reviewText.trim(),
                        "visitId" to visitId,
                        "source" to source.wireValue
                    )
                )
                .await()
                .data as? Map<*, *>
            ReviewActionResult.Success(
                SavedReview(
                    reviewId = data?.get("reviewId") as? String ?: "",
                    updated = data?.get("updated") as? Boolean ?: false,
                    isPublished = (data?.get("status") as? String ?: "published") == "published"
                )
            )
        } catch (e: FirebaseFunctionsException) {
            failure(e, "Couldn't save your review. Please try again.")
        } catch (e: Exception) {
            ReviewActionResult.Failed("Couldn't save your review. Check your connection and try again.")
        }
    }

    /** Marks or unmarks a review as helpful. Returns the new state and count. */
    suspend fun toggleHelpful(reviewId: String): ReviewActionResult<Pair<Boolean, Int>> = try {
        val data = functions.getHttpsCallable("toggleReviewHelpful")
            .call(mapOf("reviewId" to reviewId))
            .await()
            .data as? Map<*, *>
        val helpful = data?.get("helpful") as? Boolean
        val likes = (data?.get("likes") as? Number)?.toInt()
        if (helpful != null && likes != null) {
            ReviewActionResult.Success(helpful to likes)
        } else {
            ReviewActionResult.Failed("Couldn't update that. Please try again.")
        }
    } catch (e: Exception) {
        failure(e, "Couldn't update that. Please try again.")
    }

    suspend fun reportReview(
        reviewId: String,
        reason: ReviewReportReason,
        details: String
    ): ReviewActionResult<Unit> = try {
        functions.getHttpsCallable("reportReview")
            .call(mapOf("reviewId" to reviewId, "reason" to reason.wireValue, "details" to details.trim()))
            .await()
        ReviewActionResult.Success(Unit)
    } catch (e: Exception) {
        failure(e, "Couldn't send the report. Please try again.")
    }

    /** Best effort: on failure nothing is marked, and the server still blocks repeats. */
    suspend fun getMyInteractions(reviewIds: List<String>): ReviewInteractions {
        if (reviewIds.isEmpty() || FirebaseAuth.getInstance().currentUser == null) return ReviewInteractions()
        return try {
            val data = functions.getHttpsCallable("getMyReviewInteractions")
                .call(mapOf("reviewIds" to reviewIds.take(200)))
                .await()
                .data as? Map<*, *>
            fun ids(key: String) = (data?.get(key) as? List<*>).orEmpty().filterIsInstance<String>().toSet()
            ReviewInteractions(ids("helpfulReviewIds"), ids("reportedReviewIds"))
        } catch (e: Exception) {
            ReviewInteractions()
        }
    }

    // The functions' own messages are written for visitors; anything else
    // (no connection, a crash) gets the generic fallback.
    private fun failure(e: Exception, fallback: String): ReviewActionResult.Failed {
        val fe = e as? FirebaseFunctionsException ?: return ReviewActionResult.Failed(fallback)
        val readable = when (fe.code) {
            FirebaseFunctionsException.Code.ALREADY_EXISTS,
            FirebaseFunctionsException.Code.FAILED_PRECONDITION,
            FirebaseFunctionsException.Code.NOT_FOUND,
            FirebaseFunctionsException.Code.INVALID_ARGUMENT,
            FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED,
            FirebaseFunctionsException.Code.UNAUTHENTICATED,
            FirebaseFunctionsException.Code.PERMISSION_DENIED -> fe.message
            FirebaseFunctionsException.Code.UNAVAILABLE -> "You're offline. Check your connection and try again."
            else -> null
        }
        return ReviewActionResult.Failed(
            message = readable?.takeIf { it.isNotBlank() } ?: fallback,
            alreadyDone = fe.code == FirebaseFunctionsException.Code.ALREADY_EXISTS
        )
    }
}
