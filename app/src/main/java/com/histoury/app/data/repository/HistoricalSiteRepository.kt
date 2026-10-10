package com.histoury.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.histoury.app.data.model.HistoricalSite
import kotlinx.coroutines.tasks.await

class HistoricalSiteRepository {

    private val db = FirebaseFirestore.getInstance()

    suspend fun getHistoricalSites(): List<HistoricalSite> {

        return try {

            val snapshot = db
                .collection("historical_sites")
                .whereEqualTo("status", "published")
                .get()
                .await()

            snapshot.documents.mapNotNull { document ->

                val site = document.toObject(HistoricalSite::class.java)

                site?.copy(
                    documentId = document.id,
                    siteId = if (site.siteId.isBlank()) document.id else site.siteId
                )
            }

        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * The published sites as the SERVER has them right now, or null when the
     * server couldn't be reached.
     *
     * Unlike [getHistoricalSites] this never answers from the phone's cache,
     * so an empty or shorter list really means sites were unpublished or
     * deleted. Offline Downloads uses it to tidy up files for sites that are
     * gone; it must not act on a cached (possibly out-of-date) list.
     */
    suspend fun getPublishedSitesFromServer(): List<HistoricalSite>? {

        return try {

            val snapshot = db
                .collection("historical_sites")
                .whereEqualTo("status", "published")
                .get(Source.SERVER)
                .await()

            snapshot.documents.mapNotNull { document ->

                val site = document.toObject(HistoricalSite::class.java)

                site?.copy(
                    documentId = document.id,
                    siteId = if (site.siteId.isBlank()) document.id else site.siteId
                )
            }

        } catch (e: Exception) {
            null
        }
    }

    suspend fun getHistoricalSiteById(documentId: String): HistoricalSite? {

        return try {

            val document = db
                .collection("historical_sites")
                .document(documentId)
                .get()
                .await()

            val site = document.toObject(HistoricalSite::class.java)

            site?.copy(
                documentId = document.id,
                siteId = if (site.siteId.isBlank()) document.id else site.siteId
            )

        } catch (e: Exception) {
            null
        }
    }

    /**
     * Resolves a site from whatever identifier the caller happens to hold.
     *
     * Geofences, notifications, and navigation routes all carry "a site id",
     * but historically that value has been the `siteId` FIELD for some
     * documents and the Firestore DOCUMENT id for others — and a site whose
     * `siteId` field was never filled in can only be found by document id.
     * Arrival used to query the field alone, so those sites resolved to
     * null and the Arrival screen showed "Couldn't load details" even
     * though the site exists.
     *
     * So: try the field match first, then fall back to treating the value
     * as a document id. Elsewhere the codebase already assumes this duality
     * (`siteId.ifBlank { documentId }` in the content lookups); this makes
     * the reverse direction just as forgiving.
     */
    suspend fun getHistoricalSiteBySiteId(siteId: String): HistoricalSite? {

        if (siteId.isBlank()) {
            return null
        }

        val byField = try {

            val snapshot = db
                .collection("historical_sites")
                .whereEqualTo("siteId", siteId)
                .limit(1)
                .get()
                .await()

            val document = snapshot.documents.firstOrNull()

            val site = document?.toObject(HistoricalSite::class.java)

            site?.copy(
                documentId = document.id,
                siteId = if (site.siteId.isBlank()) document.id else site.siteId
            )

        } catch (e: Exception) {
            null
        }

        if (byField != null) {
            return byField
        }

        return getHistoricalSiteById(siteId)
    }
}
