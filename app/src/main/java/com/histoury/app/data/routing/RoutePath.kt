package com.histoury.app.data.routing

import com.google.android.gms.maps.model.LatLng
import com.histoury.app.data.repository.TravelMode

/**
 * One hop between two consecutive itinerary stops.
 *
 * [points] is the decoded path the Routes API returned — the actual line
 * through Intramuros' streets, not the two endpoints. When the API can't be
 * reached, the leg degrades to a two-point straight line and
 * [isStraightLineFallback] flips, so the UI can draw it dashed and label the
 * numbers as estimates rather than quietly presenting a guess as a route.
 */
data class RouteLeg(

    val fromIndex: Int,

    val toIndex: Int,

    val points: List<LatLng>,

    val distanceMeters: Int,

    val durationSeconds: Int,

    val isStraightLineFallback: Boolean
) {

    val durationMinutes: Int
        get() = Math.max(1, Math.round(durationSeconds / 60.0).toInt())
}

/**
 * The full path across an itinerary: one [RouteLeg] per consecutive pair of
 * stops, in visiting order.
 *
 * Deliberately does NOT include a leg from the user's live location to the
 * first stop. A saved itinerary outlives the GPS fix that produced it, so
 * "get me to stop 1" belongs to the Map screen at trip time, not to the
 * stored route.
 */
data class RoutePath(

    val legs: List<RouteLeg>,

    val mode: TravelMode
) {

    /**
     * Every leg's points end to end, for a single Polyline. The shared point
     * between two legs appears twice, which the renderer collapses.
     */
    val points: List<LatLng>
        get() = legs.flatMap { it.points }

    val totalDistanceMeters: Int
        get() = legs.sumOf { it.distanceMeters }

    val totalDurationSeconds: Int
        get() = legs.sumOf { it.durationSeconds }

    val totalDurationMinutes: Int
        get() = Math.max(1, Math.round(totalDurationSeconds / 60.0).toInt())

    /** True if any leg fell back to a straight line — totals are then approximate. */
    val hasApproximateLegs: Boolean
        get() = legs.any { it.isStraightLineFallback }

    val isEmpty: Boolean
        get() = legs.isEmpty()

    fun legTo(stopIndex: Int): RouteLeg? {
        return legs.firstOrNull { it.toIndex == stopIndex }
    }

    companion object {
        fun empty(mode: TravelMode) = RoutePath(emptyList(), mode)
    }
}
