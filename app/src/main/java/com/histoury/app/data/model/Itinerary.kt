package com.histoury.app.data.model

import com.google.firebase.firestore.PropertyName

/**
 * Where an itinerary sits as a whole. Kept as plain strings (like
 * [StopStatus] below and [ItineraryStop.refType]) rather than a Kotlin enum
 * because this is what actually gets written to and read from Firestore —
 * matches the existing convention for `travelMode`, which stores
 * `TravelMode.wireValue` rather than the enum itself.
 *
 * PLANNED -> IN_PROGRESS -> COMPLETED is the normal path. PAUSED is a
 * deliberate visitor action (see ItineraryRepository.pauseItinerary) that
 * only leaves IN_PROGRESS, never PLANNED or COMPLETED, and always returns to
 * IN_PROGRESS on resume — it's a detour, not a fifth stage of the pipeline.
 */
object ItineraryStatus {
    const val PLANNED = "planned"
    const val IN_PROGRESS = "in_progress"
    const val PAUSED = "paused"
    const val COMPLETED = "completed"
}

/**
 * Where a single stop sits within an active trip. Arrival and completion are
 * deliberately different states: walking into a site's geofence (or simply
 * reaching it while navigating in-app) can only ever produce ARRIVED, never
 * COMPLETED — completing a stop is always an explicit visitor action (see
 * ItineraryRepository.markStopCompleted / markStopSkipped). This is the
 * single source of truth for a stop's progress; nothing else (not
 * `Itinerary.completedStopIds`, not a screen's own local state) should be
 * treated as authoritative over it.
 *
 * EXPLORING sits between ARRIVED and COMPLETED for screens that want to
 * distinguish "just walked up" from "actively looking around" (e.g. to know
 * whether it's safe to show a completion prompt) — PENDING/ARRIVED/COMPLETED
 * remain sufficient for anything that only cares about trip progression.
 * "Navigating to this stop" is deliberately NOT one of these values: it's a
 * transient fact about whichever stop [ActiveItineraryTrip] currently points
 * at plus MapViewModel's own `isNavigating` flag, not something durable
 * enough to persist per stop.
 */
object StopStatus {
    const val PENDING = "pending"
    const val ARRIVED = "arrived"
    const val EXPLORING = "exploring"
    const val COMPLETED = "completed"
    const val SKIPPED = "skipped"

    /** True once a stop is done and the trip has moved past it, one way or another. */
    fun isResolved(status: String) = status == COMPLETED || status == SKIPPED
}

/**
 * One stop in an itinerary. Denormalized (name/image/coordinates copied in
 * at build time) rather than just storing a reference id, so the
 * itinerary still reads and displays correctly even if the source site or
 * place is later renamed, re-categorized, or removed.
 */
data class ItineraryStop(
    val refId: String = "",

    // "site" or "place" — which repository/screen this stop originally
    // came from, used to route "View Details" / navigation correctly.
    val refType: String = "site",

    val name: String = "",

    val subtitle: String = "",

    val imageUrl: String = "",

    val latitude: Double = 0.0,

    val longitude: Double = 0.0,

    // "historical" for sites, or the place's own category (food/park/etc).
    val category: String = "",

    val estimatedVisitMinutes: Int = 30,

    val order: Int = 0,

    // True if this stop was added by Smart Recommendations rather than
    // picked directly — shown with a small "Recommended" badge in the UI.
    //
    // Firestore names a Kotlin "isX" Boolean after its getter, so it is
    // SAVED as "recommended", but on reading back it looked for a field
    // called "isRecommended", never matched, and left it false — the gold
    // markers vanished once a trip was saved. The annotations tie both
    // directions to "recommended" (the name already in saved trips).
    @get:PropertyName("recommended")
    @field:PropertyName("recommended")
    val isRecommended: Boolean = false,

    // See StopStatus. Defaults to PENDING, which is also what an older saved
    // document (from before this field existed) reads back as — correct for
    // any stop that isn't in that document's legacy completedStopIds.
    val status: String = StopStatus.PENDING
)

data class Itinerary(
    val documentId: String = "",

    val userId: String = "",

    val name: String = "",

    val stops: List<ItineraryStop> = emptyList(),

    val totalDistanceMeters: Double = 0.0,

    val totalWalkingMinutes: Int = 0,

    val totalVisitMinutes: Int = 0,

    // "WALK" / "DRIVE" / "TWO_WHEELER" — matches TravelMode.wireValue so the
    // saved route redraws in the mode it was planned for. Older documents
    // without the field fall back to walking.
    val travelMode: String = "WALK",

    // Minutes past midnight for the planned start, e.g. 540 = 9:00 AM.
    // Drives the arrival times shown on the timeline.
    val startTimeMinutes: Int = 540,

    // DERIVED display convenience only — refId of every stop whose
    // ItineraryStop.status is COMPLETED, recomputed and rewritten by
    // ItineraryRepository on every status change. The per-stop `status`
    // field above (StopStatus) is the actual source of truth for progress;
    // nothing reads this list to decide what's done, only to show counts
    // (e.g. "2/4 stops complete") without a caller having to re-derive it.
    val completedStopIds: List<String> = emptyList(),

    // See ItineraryStatus.
    val status: String = ItineraryStatus.PLANNED,

    // Set once when the visitor starts the trip; retained across pauses.
    val startedAt: Any? = null,

    val completedAt: Any? = null,

    // Completed itineraries are removed after this timestamp.
    val deleteAt: Any? = null,

    val createdAt: Any? = null,

    val updatedAt: Any? = null
)

/**
 * Documents saved before per-stop [ItineraryStop.status] existed recorded
 * progress only in [Itinerary.completedStopIds]; their stops all read back
 * as PENDING. Lift that legacy list into the stop statuses so progress isn't
 * lost (and then overwritten) the first time such a trip is loaded.
 */
fun Itinerary.withLegacyStopStatuses(): Itinerary {
    if (completedStopIds.isEmpty()) return this
    val completed = completedStopIds.toSet()
    if (stops.none { it.status == StopStatus.PENDING && it.refId in completed }) return this
    return copy(
        stops = stops.map { stop ->
            if (stop.status == StopStatus.PENDING && stop.refId in completed) {
                stop.copy(status = StopStatus.COMPLETED)
            } else {
                stop
            }
        }
    )
}
