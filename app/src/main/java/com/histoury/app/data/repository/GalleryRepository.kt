package com.histoury.app.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.histoury.app.data.model.Gallery
import kotlinx.coroutines.tasks.await

class GalleryRepository {

    private val db = FirebaseFirestore.getInstance()

    /**
     * Fetches the signed-in user's captured photos, newest first.
     *
     * Sorting happens client-side (same approach as VisitHistoryRepository)
     * so no composite Firestore index is needed for userId + capturedAt.
     *
     * Returns an empty list if no one is signed in.
     */
    suspend fun getGalleryForCurrentUser(): List<Gallery> {

        val userId = FirebaseAuth.getInstance().currentUser?.uid
            ?: return emptyList()

        return try {

            val snapshot = db.collection("gallery")
                .whereEqualTo("userId", userId)
                .get()
                .await()

            snapshot.documents
                .mapNotNull { document ->
                    document.toObject(Gallery::class.java)
                        ?.copy(id = document.id)
                }
                .sortedByDescending { entry ->
                    (entry.capturedAt as? Timestamp)?.toDate()?.time ?: 0L
                }

        } catch (e: Exception) {
            emptyList()
        }
    }
}
