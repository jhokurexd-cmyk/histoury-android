package com.histoury.app.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.histoury.app.data.model.Geofence
import kotlinx.coroutines.tasks.await

/**
 * The answer to "does this site have an active geofence?", with a failed
 * read kept apart from a real "no". The AR gate needs the difference: a
 * site with no fence is allowed, but a lookup that failed must not be
 * mistaken for one, or AR would unlock anywhere whenever Firestore hiccups.
 */
sealed interface GeofenceLookup {
    data class Found(val geofence: Geofence) : GeofenceLookup

    /** The server confirmed there is no active fence for this id. */
    object NotFound : GeofenceLookup

    /** Couldn't tell: the read failed, or only an empty offline cache answered. */
    object Failed : GeofenceLookup
}

class GeofenceRepository {

    private val db = FirebaseFirestore.getInstance()

    /**
     * Like [getGeofenceForSite], but says when the lookup failed instead of
     * returning null for it. An empty answer from the offline cache counts
     * as Failed, not NotFound: it means "nothing cached", not "no fence".
     */
    suspend fun lookupGeofenceForSite(siteId: String): GeofenceLookup {

        if (siteId.isBlank()) {
            return GeofenceLookup.NotFound
        }

        return try {

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

            when {
                document != null && geofence != null -> GeofenceLookup.Found(
                    geofence.copy(id = if (geofence.id.isBlank()) document.id else geofence.id)
                )
                snapshot.metadata.isFromCache -> GeofenceLookup.Failed
                else -> GeofenceLookup.NotFound
            }

        } catch (e: Exception) {
            GeofenceLookup.Failed
        }
    }

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

    /**
     * The geofences to register on the phone: active ones whose site is
     * PUBLISHED, positioned on each site's canonical coordinates.
     *
     * The site document's latitude/longitude is the source of truth (the
     * Map screen and itinerary routing already use it). The geofence
     * document's own centre could drift from it when an admin moved a site,
     * which put arrival detection somewhere other than the pin the visitor
     * was being routed to. Radius still comes from the geofence.
     *
     * Returns NULL when the data couldn't be loaded — callers must then
     * leave the phone's current fences alone. This used to return an empty
     * list on any error, and registerGeofences() treats "empty" as "there
     * are no fences any more" and removes every one: a single failed fetch
     * (no signal, a timeout) wiped arrival detection off the phone.
     *
     * Only published sites count. Geofences of Draft (or archived) sites
     * used to be registered too, so a site still being prepared in the
     * admin panel sent "You've arrived" notifications.
     */
    suspend fun getAllActiveGeofencesAtSiteCoordinates(): List<Geofence>? {

        val geofences = loadActiveGeofences() ?: return null

        if (geofences.isEmpty()) return geofences

        // getHistoricalSites() only returns published sites, and returns an
        // empty list on failure — with active fences present, "no published
        // sites at all" is far more likely to be a failed read.
        val publishedSites = HistoricalSiteRepository().getHistoricalSites()
        if (publishedSites.isEmpty()) return null

        // A fence's siteId may be the site's slug or its document id.
        val sitesByKey = HashMap<String, com.histoury.app.data.model.HistoricalSite>()
        publishedSites.forEach { site ->
            if (site.siteId.isNotBlank()) sitesByKey[site.siteId] = site
            if (site.documentId.isNotBlank()) sitesByKey.putIfAbsent(site.documentId, site)
        }

        return geofences.mapNotNull { geofence ->
            val site = sitesByKey[geofence.siteId] ?: return@mapNotNull null
            val lat = site.latitude
            val lng = site.longitude
            if (lat != null && lng != null) geofence.copy(latitude = lat, longitude = lng) else geofence
        }
    }

    /** Active geofences, or null if they couldn't be read (see above). */
    private suspend fun loadActiveGeofences(): List<Geofence>? {

        return try {

            val snapshot = db
                .collection("geofences")
                .whereEqualTo("status", "active")
                .get()
                .await()

            // Offline, get() answers from the local cache; an EMPTY answer
            // from the cache means "nothing cached", not "no geofences".
            if (snapshot.isEmpty && snapshot.metadata.isFromCache) return null

            snapshot.documents.mapNotNull { document ->

                val geofence = document.toObject(Geofence::class.java)

                geofence?.copy(
                    id = if (geofence.id.isBlank()) document.id else geofence.id
                )
            }

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
