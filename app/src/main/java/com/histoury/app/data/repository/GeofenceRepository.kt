package com.histoury.app.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.histoury.app.data.model.Geofence
import kotlinx.coroutines.tasks.await

class GeofenceRepository {

    private val db = FirebaseFirestore.getInstance()

    suspend fun getGeofenceForSite(siteId: String): Geofence? {

        if (siteId.isBlank()) {
            return null
        }

        return try {

            // Intentionally not limit(1): if a site ever ends up with more
            // than one geofence marked "active" (e.g. a stale test entry
            // never deactivated), taking an arbitrary doc here can silently
            // route users to the wrong, possibly far-away, point. Fetch all
            // active candidates and pick the most recently updated one.
            val snapshot = db
                .collection("geofences")
                .whereEqualTo("siteId", siteId)
                .whereEqualTo("status", "active")
                .get()
                .await()

            val document = snapshot.documents.maxByOrNull { doc ->
                (doc.get("updatedAt") as? Timestamp)?.seconds
                    ?: (doc.get("createdAt") as? Timestamp)?.seconds
                    ?: Long.MIN_VALUE
            }

            val geofence = document?.toObject(Geofence::class.java)

            geofence?.copy(
                id = if (geofence.id.isBlank()) document.id else geofence.id
            )

        } catch (e: Exception) {
            null
        }
    }

    suspend fun getAllActiveGeofences(): List<Geofence> {

        return try {

            val snapshot = db
                .collection("geofences")
                .whereEqualTo("status", "active")
                .get()
                .await()

            snapshot.documents.mapNotNull { document ->

                val geofence = document.toObject(Geofence::class.java)

                geofence?.copy(
                    id = if (geofence.id.isBlank()) document.id else geofence.id
                )
            }

        } catch (e: Exception) {
            emptyList()
        }
    }
}
