package com.histoury.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.histoury.app.data.model.Itinerary
import kotlinx.coroutines.tasks.await
import java.util.Date

class ItineraryRepository {

    private val db = FirebaseFirestore.getInstance()

    suspend fun getItinerariesForCurrentUser(): List<Itinerary> {

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return emptyList()

        return try {

            val snapshot = db.collection("itineraries")
                .whereEqualTo("userId", userId)
                .get()
                .await()

            val loaded = snapshot.documents.mapNotNull { document ->
                document.toObject(Itinerary::class.java)?.copy(documentId = document.id)
            }

            // Backfill the deadline for itineraries completed before the
            // retention fields were introduced.
            val itineraries = loaded.map { itinerary ->
                if (itinerary.status == "completed" && itinerary.deleteAt == null) {
                    val completedAtMillis = timestampMillis(itinerary.completedAt)
                        ?: timestampMillis(itinerary.updatedAt)
                        ?: System.currentTimeMillis()
                    val completedAt = Date(completedAtMillis)
                    val deleteAt = Date(completedAtMillis + COMPLETED_RETENTION_MS)
                    db.collection("itineraries").document(itinerary.documentId).update(
                        mapOf("completedAt" to completedAt, "deleteAt" to deleteAt)
                    ).await()
                    itinerary.copy(completedAt = completedAt, deleteAt = deleteAt)
                } else {
                    itinerary
                }
            }

            val now = System.currentTimeMillis()
            val (expired, active) = itineraries.partition { itinerary ->
                itinerary.status == "completed" && timestampMillis(itinerary.deleteAt)?.let {
                    it <= now
                } == true
            }

            expired.forEach { itinerary ->
                db.collection("itineraries").document(itinerary.documentId).delete().await()
            }

            active

        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getItineraryById(documentId: String): Itinerary? {

        return try {

            val document = db.collection("itineraries")
                .document(documentId)
                .get()
                .await()

            document.toObject(Itinerary::class.java)?.copy(documentId = document.id)

        } catch (e: Exception) {
            null
        }
    }

    /** Creates a new itinerary owned by the current user. Returns its new document id, or null on failure. */
    suspend fun createItinerary(itinerary: Itinerary): String? {

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return null

        return try {

            val docRef = db.collection("itineraries").document()

            val toSave = itinerary.copy(
                documentId = docRef.id,
                userId = userId,
                createdAt = Date(),
                updatedAt = Date()
            )

            docRef.set(toSave).await()

            docRef.id

        } catch (e: Exception) {
            null
        }
    }

    suspend fun updateItinerary(documentId: String, itinerary: Itinerary): Boolean {

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return false

        return try {

            db.collection("itineraries")
                .document(documentId)
                .set(
                    itinerary.copy(
                        documentId = documentId,
                        userId = userId,
                        updatedAt = Date()
                    )
                )
                .await()

            true

        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteItinerary(documentId: String): Boolean {

        return try {
            db.collection("itineraries").document(documentId).delete().await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun markStopCompleted(
        documentId: String,
        stopId: String,
        itineraryCompleted: Boolean
    ): Boolean {
        if (documentId.isBlank() || stopId.isBlank()) return false

        return try {
            val now = Date()
            val updates = mutableMapOf<String, Any>(
                    "completedStopIds" to FieldValue.arrayUnion(stopId),
                    "status" to if (itineraryCompleted) "completed" else "in_progress",
                    "updatedAt" to now
            )

            if (itineraryCompleted) {
                updates["completedAt"] = now
                updates["deleteAt"] = Date(now.time + COMPLETED_RETENTION_MS)
            }

            db.collection("itineraries").document(documentId).update(updates).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun timestampMillis(value: Any?): Long? = when (value) {
        is com.google.firebase.Timestamp -> value.toDate().time
        is Date -> value.time
        else -> null
    }

    companion object {
        private const val COMPLETED_RETENTION_MS = 48L * 60L * 60L * 1000L
    }
}
