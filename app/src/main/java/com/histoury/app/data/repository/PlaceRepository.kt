package com.histoury.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.histoury.app.data.model.Place
import kotlinx.coroutines.tasks.await

class PlaceRepository {

    private val db = FirebaseFirestore.getInstance()

    /** All published places, for Home's Explore Intramuros section. */
    suspend fun getPlaces(): List<Place> {

        return try {

            val snapshot = db.collection("places")
                .whereEqualTo("status", "published")
                .get()
                .await()

            snapshot.documents.mapNotNull { document ->

                val place = document.toObject(Place::class.java)

                place?.copy(
                    documentId = document.id,
                    placeId = if (place.placeId.isBlank()) document.id else place.placeId
                )
            }

        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Lookup by Firestore document id — the Map screen's fallback path. */
    suspend fun getPlaceById(documentId: String): Place? {

        return try {

            val document = db.collection("places")
                .document(documentId)
                .get()
                .await()

            if (!document.exists()) return null

            document.toObject(Place::class.java)?.copy(
                documentId = document.id,
                placeId = document.toObject(Place::class.java)?.placeId
                    ?.ifBlank { document.id } ?: document.id
            )

        } catch (e: Exception) {
            null
        }
    }

    /** Lookup by slug — the Arrival screen's fallback path. */
    suspend fun getPlaceBySlug(placeId: String): Place? {

        val byField = try {

            val snapshot = db.collection("places")
                .whereEqualTo("placeId", placeId)
                .limit(1)
                .get()
                .await()

            val document = snapshot.documents.firstOrNull()

            document?.toObject(Place::class.java)?.copy(
                documentId = document.id,
                placeId = document.toObject(Place::class.java)?.placeId
                    ?.ifBlank { document.id } ?: document.id
            )

        } catch (e: Exception) {
            null
        }

        return byField ?: getPlaceById(placeId)
    }
}
