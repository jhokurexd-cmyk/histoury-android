package com.histoury.app.data.itinerary

import com.google.firebase.Timestamp
import com.histoury.app.data.model.ItineraryStop
import com.histoury.app.data.model.StopStatus
import com.histoury.app.data.repository.ItineraryRepository
import java.util.Date

/**
 * Process-scoped progress for the itinerary currently being followed.
 * Navigation destinations have separate ViewModels, so this small session
 * keeps the ordered stops intact while Map and Arrival screens are opened.
 *
 * This is a cache, not the source of truth: [ItineraryStop.status] on each
 * stop here is only ever a mirror of what's already been written to
 * Firestore (see ItineraryRepository's markStop* methods), kept around so
 * Map/Arrival don't have to re-fetch the whole itinerary on every screen.
 * Nothing in this object writes to Firestore itself.
 *
 * Because it's a plain in-memory singleton, it's empty after a process
 * death — an app kill mid-trip, or a cold launch straight into a geofence
 * notification tap. [rehydrate] is what recovers from that: it re-derives
 * this session from whichever itinerary Firestore has marked IN_PROGRESS,
 * so a killed-and-reopened app still recognizes an arrival as part of the
 * trip the visitor already started instead of treating it as a walk-in.
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
    private var currentIndex: Int = 0

    // Out-of-order-arrival bookkeeping. See [noteArrival] — dwell geofences
    // fire once per stay, but the Arrival screen itself can still be
    // re-entered (back-and-forth navigation, a cold-launch rehydrate) while
    // the visitor never actually left the site, and a saved "Keep Original"
    // choice shouldn't be re-litigated every time that happens.
    private var lastArrivalRefId: String? = null
    private var dismissedOutOfOrderRefId: String? = null

    private fun isOpen(stop: ItineraryStop) = !StopStatus.isResolved(stop.status)

    fun start(
        documentId: String,
        name: String,
        orderedStops: List<ItineraryStop>,
        startedAtMillis: Long
    ): ItineraryStop? {
        stops = orderedStops.sortedBy { it.order }
        itineraryName = name.ifBlank { "My Intramuros Trip" }
        itineraryDocumentId = documentId
        this.startedAtMillis = startedAtMillis
        currentIndex = stops.indexOfFirst(::isOpen).takeIf { it >= 0 } ?: stops.size
        return stops.getOrNull(currentIndex)
    }

    /** True when a session is already loaded for [documentId] — used to skip a redundant [rehydrate]. */
    fun isActiveFor(documentId: String) = itineraryDocumentId == documentId && stops.isNotEmpty()

    /**
     * Loads the visitor's IN_PROGRESS itinerary from Firestore if this
     * session doesn't already have one in memory. Safe to call from any
     * screen that needs trip context before it can know whether one exists
     * (Arrival on a cold launch, Home's "Continue Trip" card) — a no-op
     * when a session is already live, and non-fatal if there's no active
     * trip or the fetch fails.
     */
    suspend fun rehydrate(itineraryRepository: ItineraryRepository): Boolean {

        if (stops.isNotEmpty()) return true

        val active = itineraryRepository.getActiveItinerary() ?: return false

        start(
            documentId = active.documentId,
            name = active.name,
            orderedStops = active.stops,
            startedAtMillis = timestampMillis(active.startedAt) ?: System.currentTimeMillis()
        )

        return true
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
            nextStop = stops.drop(currentIndex + 1).firstOrNull(::isOpen)
        )
    }

    /**
     * The stop matching [refId] in the active trip, in whatever status it
     * currently holds — unlike [progressFor], this matches regardless of
     * whether it's the stop the trip currently expects. Null when [refId]
     * isn't part of this itinerary at all (an ordinary walk-in). This is
     * what lets an arrival be recognised as "on the itinerary, but out of
     * order" instead of collapsing that case into either "expected" or
     * "not part of the trip."
     */
    fun stopFor(refId: String): ItineraryStop? = stops.firstOrNull { it.refId == refId }

    /** True when [refId] is exactly the stop the trip is currently pointed at. */
    fun isExpectedNext(refId: String): Boolean = stops.getOrNull(currentIndex)?.refId == refId

    /** The stop the trip is currently pointed at, or null once every stop is resolved. */
    fun expectedNextStop(): ItineraryStop? = stops.getOrNull(currentIndex)

    /** The active trip's document id, or null when no trip is loaded. */
    fun activeDocumentId(): String? = itineraryDocumentId.takeIf { stops.isNotEmpty() }

    /**
     * Records that the visitor's most recent arrival was at [refId]. A dwell
     * geofence only fires once per continuous stay (see GeofenceManager), so
     * arriving somewhere ELSE is the clearest signal available that the
     * visitor actually left wherever an out-of-order prompt was last
     * dismissed for — there's no EXIT transition registered to key off of
     * instead. Call this before checking [isOutOfOrderDismissed].
     */
    fun noteArrival(refId: String) {
        if (lastArrivalRefId != refId) {
            lastArrivalRefId = refId
            if (dismissedOutOfOrderRefId != refId) {
                dismissedOutOfOrderRefId = null
            }
        }
    }

    /** True if the visitor already chose "Keep Original Order" for [refId] and hasn't left since. */
    fun isOutOfOrderDismissed(refId: String): Boolean = dismissedOutOfOrderRefId == refId

    /** Records "Keep Original Order" for [refId] so the dialog doesn't reappear while still there. */
    fun dismissOutOfOrderPrompt(refId: String) {
        dismissedOutOfOrderRefId = refId
    }

    /**
     * Adopts a freshly reordered stop list from "Optimize Route" and points
     * the trip at [focusRefId] as the new current stop. The reordering
     * itself (resolved stops first, [focusRefId] next, the remaining open
     * stops re-optimized from there) is done by
     * [com.histoury.app.data.repository.ItineraryRepository.optimizeFromCurrentStop]
     * and already durably saved by the time this is called — this just
     * mirrors it into the in-memory session, the same way [start] does.
     */
    fun reorderAndFocus(reorderedStops: List<ItineraryStop>, focusRefId: String) {
        stops = reorderedStops.sortedBy { it.order }
        currentIndex = stops.indexOfFirst { it.refId == focusRefId }.takeIf { it >= 0 }
            ?: stops.indexOfFirst(::isOpen).takeIf { it >= 0 }
            ?: stops.size
    }

    /**
     * Mirrors a status change already written to Firestore into this
     * session, without moving [currentIndex] — e.g. ARRIVED or EXPLORING,
     * which don't finish a stop. Call [advanceFrom] instead once a stop is
     * actually done (COMPLETED or SKIPPED).
     */
    fun reflectStatus(refId: String, status: String) {
        val index = stops.indexOfFirst { it.refId == refId }
        if (index == -1) return
        stops = stops.toMutableList().also { it[index] = it[index].copy(status = status) }
    }

    /**
     * Call once [refId] is durably COMPLETED or SKIPPED in Firestore, with
     * that [resolvedStatus]. Returns the next open destination, or null once
     * every stop is resolved (and clears the session, since there's nothing
     * left to track).
     *
     * The finished stop's status is updated here too. It used to be left as
     * ARRIVED/PENDING in this session, so the trip went on treating a stop
     * the visitor had already finished as unfinished — coming back past it
     * raised a wrong "you arrived at a different stop" prompt.
     */
    fun advanceFrom(refId: String, resolvedStatus: String = StopStatus.COMPLETED): ItineraryStop? {
        val current = stops.getOrNull(currentIndex) ?: return null
        if (current.refId != refId) return null

        stops = stops.toMutableList().also { it[currentIndex] = current.copy(status = resolvedStatus) }

        currentIndex = (currentIndex + 1 until stops.size)
            .firstOrNull { isOpen(stops[it]) }
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
        currentIndex = 0
        lastArrivalRefId = null
        dismissedOutOfOrderRefId = null
    }

    private fun timestampMillis(value: Any?): Long? = when (value) {
        is Timestamp -> value.toDate().time
        is Date -> value.time
        else -> null
    }
}
