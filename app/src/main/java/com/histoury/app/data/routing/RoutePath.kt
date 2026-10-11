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

    val isStraightLineFallback: Boolean,

    /** Credit line this leg's route needs on screen (OpenStreetMap-based routes); null for Google. */
    val attribution: String? = null,

    /** True when the leg ends at a verified visitor entrance rather than the site's centre point. */
    val endsAtVerifiedEntrance: Boolean = false,

    val warnings: List<String> = emptyList(),

    /**
     * Commute legs only: the walk / ride steps (which jeepney, bus or train,
     * where to get on and off), so the itinerary can show step-by-step
     * directions between stops and draw each ride in its route's colour.
     */
    val steps: List<com.histoury.app.data.repository.RouteStep> = emptyList()
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

    /** True if any leg couldn't be routed — totals are then approximate. */
    val hasApproximateLegs: Boolean
        get() = legs.any { it.isStraightLineFallback }

    /** How many legs have no real route (only an estimate, and no line on the map). */
    val unroutedLegCount: Int
        get() = legs.count { it.isStraightLineFallback }

    /** Every distinct credit line the drawn legs require, to show under the map. */
    val attributions: List<String>
        get() = legs.filter { !it.isStraightLineFallback }.mapNotNull { it.attribution }.distinct()

    val isEmpty: Boolean
        get() = legs.isEmpty()

    fun legTo(stopIndex: Int): RouteLeg? {
        return legs.firstOrNull { it.toIndex == stopIndex }
    }

    companion object {
        fun empty(mode: TravelMode) = RoutePath(emptyList(), mode)
    }
}
