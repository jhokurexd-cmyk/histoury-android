package com.histoury.app.data.model

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
    val isRecommended: Boolean = false
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

    // Persisted progress makes an in-progress trip resumable later.
    val completedStopIds: List<String> = emptyList(),

    // "planned", "in_progress", or "completed".
    val status: String = "planned",

    // Set once when the visitor starts the trip; retained across pauses.
    val startedAt: Any? = null,

    val completedAt: Any? = null,

    // Completed itineraries are removed after this timestamp.
    val deleteAt: Any? = null,

    val createdAt: Any? = null,

    val updatedAt: Any? = null
)
