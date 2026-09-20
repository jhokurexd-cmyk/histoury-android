package com.histoury.app.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.histoury.app.data.model.Review
import kotlinx.coroutines.tasks.await

class ReviewRepository {

    private val db = FirebaseFirestore.getInstance()

    /**
     * All published reviews for a site, newest first.
     *
     * [siteId] is the business slug (e.g. "fort_santiago") — the same value
     * submitReview writes and the admin panel / rating triggers key on.
     *
     * Uses equality-only filters + client-side sorting so no composite
     * Firestore index is needed (same approach as VisitHistoryRepository).
     */
    suspend fun getReviewsForSite(siteId: String): List<Review> {

        return try {

            val snapshot = db.collection("reviews")
                .whereEqualTo("siteId", siteId)
                .whereEqualTo("status", "published")
                .get()
                .await()

            snapshot.documents
                .mapNotNull { document ->
                    document.toObject(Review::class.java)
                        ?.copy(id = document.id)
                }
                .sortedByDescending { review ->
                    (review.createdAt as? Timestamp)?.toDate()?.time ?: 0L
                }

        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun submitReview(
        siteId: String,
        visitId: String,
        arExperienceRating: Int,
        reviewText: String
    ): Boolean {

        val user = FirebaseAuth.getInstance().currentUser ?: return false

        return try {

            // Prefer the real profile name (users/{uid} firstName/lastName)
            // over FirebaseAuth's displayName, which registration never
            // sets — that's why old reviews all say "Tourist User".
            val profileName = try {
                val userDoc = db.collection("users").document(user.uid).get().await()
                val first = userDoc.getString("firstName") ?: ""
                val last = userDoc.getString("lastName") ?: ""
                "$first $last".trim().ifBlank { null }
            } catch (e: Exception) {
                null
            }

            val review = hashMapOf(
                "siteId" to siteId,
                "userId" to user.uid,
                "userName" to (profileName
                    ?: user.displayName?.ifBlank { null }
                    ?: "Tourist User"),
                "rating" to arExperienceRating.toDouble(),
                "arExperienceRating" to arExperienceRating.toDouble(),
                "reviewText" to reviewText,
                "likes" to 0,
                "status" to "published",
                "visitId" to visitId,
                "sceneId" to "",
                "edited" to false,
                // Server time, not the phone's clock.
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            db.collection("reviews").add(review).await()

            true

        } catch (e: Exception) {
            false
        }
    }
}
