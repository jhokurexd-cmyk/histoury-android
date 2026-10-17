package com.histoury.app.data.itinerary

/**
 * A one-shot "open this itinerary" hand-off into the Itinerary tab.
 *
 * Home's "Continue Your Trip → Resume Trip" used to just switch to the
 * Itinerary tab, leaving the visitor on the saved list to find and tap the
 * same trip a second time. The tab's ViewModel is scoped to its own back
 * stack entry, so the request is parked here and consumed once by
 * ExploreScreen as it appears.
 */
object ItineraryOpenRequest {

    private var pendingDocumentId: String? = null

    fun request(documentId: String) {
        pendingDocumentId = documentId.takeIf { it.isNotBlank() }
    }

    /** Returns the pending id (if any) and clears it, so it's acted on once. */
    fun consume(): String? {
        val id = pendingDocumentId
        pendingDocumentId = null
        return id
    }
}
