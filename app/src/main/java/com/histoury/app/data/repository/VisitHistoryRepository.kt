package com.histoury.app.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.histoury.app.data.model.VisitHistory
import kotlinx.coroutines.tasks.await

private const val DUPLICATE_VISIT_WINDOW_MS = 30 * 60 * 1000L

class VisitHistoryRepository {

    private val db = FirebaseFirestore.getInstance()

    /**
     * Records an arrival at [siteId] (the site's business slug, matching
     * the geofence/site_content convention) for the signed-in user, unless
     * a visit for the same site was already recorded in the last 30
     * minutes — this keeps a single real-world arrival (which can trigger
     * through both the background geofence receiver and the in-app live
     * navigation path) from creating duplicate history entries.
     *
     * Silently does nothing if no one is signed in.
     */
    suspend fun recordVisitIfNotRecent(siteId: String): Boolean {

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return false

        if (siteId.isBlank()) {
            return false
        }

        return try {

            val existingSnapshot = db.collection("visit_history")
                .whereEqualTo("userId", userId)
                .whereEqualTo("siteId", siteId)
                .get()
                .await()

            val recentCutoffMillis = System.currentTimeMillis() - DUPLICATE_VISIT_WINDOW_MS

            val hasRecentVisit = existingSnapshot.documents.any { document ->
                val visitTimestamp = document.getTimestamp("visitDate")
                visitTimestamp != null && visitTimestamp.toDate().time > recentCutoffMillis
            }

            if (hasRecentVisit) {
                return false
            }

            val visit = hashMapOf(
                "userId" to userId,
                "siteId" to siteId,
                "sceneId" to "",
                "visitDate" to Timestamp.now(),
                "duration" to 0L,
                "arActivated" to false,
                "photosCaptured" to 0,
                "reviewEligible" to true,
                "reviewSubmitted" to false,
                "status" to "completed"
            )

            db.collection("visit_history").add(visit).await()

            true

        } catch (e: Exception) {
            false
        }
    }

    suspend fun getVisitHistoryForCurrentUser(): List<VisitHistory> {

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return emptyList()

        return try {

            val snapshot = db.collection("visit_history")
                .whereEqualTo("userId", userId)
                .get()
                .await()

            snapshot.documents.mapNotNull { document ->

                val visit = document.toObject(VisitHistory::class.java)

                visit?.copy(id = document.id)

            }.sortedByDescending { visit ->
                (visit.visitDate as? com.google.firebase.Timestamp)?.toDate()?.time ?: 0L
            }

        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun markReviewSubmitted(visitId: String) {

        if (visitId.isBlank()) {
            return
        }

        try {

            db.collection("visit_history").document(visitId)
                .update("reviewSubmitted", true)
                .await()

        } catch (e: Exception) {
            // Non-critical; the review itself already succeeded.
        }
    }
}
