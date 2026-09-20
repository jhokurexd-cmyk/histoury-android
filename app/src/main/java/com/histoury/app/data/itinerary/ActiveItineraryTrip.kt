package com.histoury.app.data.itinerary

import com.histoury.app.data.model.ItineraryStop

/**
 * Process-scoped progress for the itinerary currently being followed.
 * Navigation destinations have separate ViewModels, so this small session
 * keeps the ordered stops intact while Map and Arrival screens are opened.
 */
object ActiveItineraryTrip {

    data class Progress(
        val itineraryName: String,
        val itineraryDocumentId: String,
        val startedAtMillis: Long,
        val currentNumber: Int,
        val totalStops: Int,
        val currentStop: ItineraryStop,
        val nextStop: ItineraryStop?
    )

    private var itineraryName: String = ""
    private var itineraryDocumentId: String = ""
    private var startedAtMillis: Long = 0L
    private var stops: List<ItineraryStop> = emptyList()
    private var completedStopIds: Set<String> = emptySet()
    private var currentIndex: Int = 0

    fun start(
        documentId: String,
        name: String,
        orderedStops: List<ItineraryStop>,
        completedStopIds: Set<String>,
        startedAtMillis: Long
    ): ItineraryStop? {
        stops = orderedStops.sortedBy { it.order }
        itineraryName = name.ifBlank { "My Intramuros Trip" }
        itineraryDocumentId = documentId
        this.startedAtMillis = startedAtMillis
        this.completedStopIds = completedStopIds
        currentIndex = stops.indexOfFirst { it.refId !in completedStopIds }
            .takeIf { it >= 0 }
            ?: 0
        return stops.getOrNull(currentIndex)
    }

    /** Returns progress only when this is the stop the active trip expects. */
    fun progressFor(refId: String): Progress? {
        val current = stops.getOrNull(currentIndex) ?: return null
        if (current.refId != refId) return null

        return Progress(
            itineraryName = itineraryName,
            itineraryDocumentId = itineraryDocumentId,
            startedAtMillis = startedAtMillis,
            currentNumber = currentIndex + 1,
            totalStops = stops.size,
            currentStop = current,
            nextStop = stops.drop(currentIndex + 1)
                .firstOrNull { it.refId !in completedStopIds }
        )
    }

    /** Completes [refId] and returns the next destination, or null at the end. */
    fun advanceFrom(refId: String): ItineraryStop? {
        val current = stops.getOrNull(currentIndex) ?: return null
        if (current.refId != refId) return null

        completedStopIds = completedStopIds + refId
        currentIndex = (currentIndex + 1 until stops.size)
            .firstOrNull { stops[it].refId !in completedStopIds }
            ?: stops.size
        val next = stops.getOrNull(currentIndex)
        if (next == null) clear()
        return next
    }

    fun clear() {
        itineraryName = ""
        itineraryDocumentId = ""
        startedAtMillis = 0L
        stops = emptyList()
        completedStopIds = emptySet()
        currentIndex = 0
    }
}
