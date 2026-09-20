package com.histoury.app.data.itinerary

import android.location.Location
import com.histoury.app.data.model.HistoricalSite
import com.histoury.app.data.model.ItineraryStop
import com.histoury.app.data.model.Place
import com.histoury.app.data.routing.RoutePath
import kotlin.math.ceil

object ItineraryPlanner {

    // Same fallback walking speed MapViewModel uses for its own estimate.
    private const val WALKING_SPEED_METERS_PER_SECOND = 1.4f

    // Straight-line distance undershoots real walking distance through
    // streets/alleys. Used only for the instant estimate shown while the
    // real road path is still being fetched.
    private const val WALKING_DETOUR_FACTOR = 1.3

    // Recommendations only look within comfortable walking range of a
    // selected site — anything farther isn't a "nearby" suggestion.
    private const val RECOMMENDATION_RADIUS_METERS = 400f

    private const val MAX_RECOMMENDATIONS = 6

    // 2-opt runs until no swap helps. The bound is a safety net for
    // pathological inputs, never reached at realistic stop counts.
    private const val MAX_TWO_OPT_PASSES = 40

    // Half a metre — below this a "gain" is just floating point noise, and
    // acting on it would let 2-opt flip the same pair forever.
    private const val IMPROVEMENT_EPSILON = 0.5

    data class OptimizedRoute(
        val orderedStops: List<ItineraryStop>,
        val totalDistanceMeters: Double,
        val totalWalkingMinutes: Int,
        val totalVisitMinutes: Int
    )

    /**
     * One stop placed on a clock, for the timeline in the route overview.
     * Minutes are offsets from the itinerary's start time so the UI can
     * re-render instantly when the user moves the start time.
     */
    data class ScheduledStop(
        val stop: ItineraryStop,
        val travelMinutesToHere: Int,
        val travelMetersToHere: Int,
        val travelIsApproximate: Boolean,
        val arrivalMinutesFromStart: Int,
        val departureMinutesFromStart: Int
    )

    /** Options offered by the per-stop duration picker. */
    val visitMinuteOptions = listOf(15, 30, 45, 60, 90)

    /**
     * Orders stops for the shortest walk.
     *
     * Two phases. Nearest-neighbor from the user's starting point builds a
     * reasonable tour instantly, then 2-opt repeatedly reverses sub-paths
     * wherever doing so shortens the total — which is what removes the
     * crossings nearest-neighbor characteristically leaves behind. At the
     * stop counts a walking itinerary actually has, this lands on the
     * optimal or near-optimal order in well under a millisecond.
     *
     * Ordering runs on straight-line distance on purpose: a road-accurate
     * ordering would need a full distance matrix, meaning n² Routes API
     * calls before a single metre is drawn. Inside a district as compact and
     * as close to a grid as Intramuros, crow-flies and walking distance rank
     * pairs of stops almost identically, so the order barely changes — and
     * the real road path is fetched for the chosen order right after, which
     * is what the user actually sees and what the totals come from.
     */
    fun optimizeRoute(
        startLatitude: Double,
        startLongitude: Double,
        stops: List<ItineraryStop>
    ): OptimizedRoute {

        if (stops.isEmpty()) {
            return OptimizedRoute(emptyList(), 0.0, 0, 0)
        }

        val nearestFirst = nearestNeighborOrder(startLatitude, startLongitude, stops)

        val improved = twoOptImprove(startLatitude, startLongitude, nearestFirst)

        val ordered = improved.mapIndexed { index, stop -> stop.copy(order = index) }

        val totalDistanceMeters =
            pathLength(startLatitude, startLongitude, ordered) * WALKING_DETOUR_FACTOR

        return OptimizedRoute(
            orderedStops = ordered,
            totalDistanceMeters = totalDistanceMeters,
            totalWalkingMinutes = minutesToWalk(totalDistanceMeters),
            totalVisitMinutes = ordered.sumOf { it.estimatedVisitMinutes }
        )
    }

    /**
     * Replaces the straight-line estimate with the measured road distance and
     * duration once [path] has come back. Stop order is untouched — this is
     * only about the numbers on screen matching the line on the map.
     */
    fun withMeasuredTotals(
        stops: List<ItineraryStop>,
        path: RoutePath
    ): OptimizedRoute {

        if (path.isEmpty) {
            return OptimizedRoute(
                orderedStops = stops,
                totalDistanceMeters = 0.0,
                totalWalkingMinutes = 0,
                totalVisitMinutes = stops.sumOf { it.estimatedVisitMinutes }
            )
        }

        return OptimizedRoute(
            orderedStops = stops,
            totalDistanceMeters = path.totalDistanceMeters.toDouble(),
            totalWalkingMinutes = path.totalDurationMinutes,
            totalVisitMinutes = stops.sumOf { it.estimatedVisitMinutes }
        )
    }

    /**
     * Lays the itinerary out on a clock: arrive, look around for the stop's
     * own visit time, travel to the next one, repeat.
     *
     * Falls back to a walking estimate for any leg [path] doesn't cover, so
     * the timeline is fully populated before the route finishes loading.
     */
    fun buildSchedule(
        stops: List<ItineraryStop>,
        path: RoutePath?
    ): List<ScheduledStop> {

        if (stops.isEmpty()) {
            return emptyList()
        }

        val scheduled = mutableListOf<ScheduledStop>()

        var clock = 0

        stops.forEachIndexed { index, stop ->

            var travelMinutes = 0
            var travelMeters = 0
            var approximate = false

            if (index > 0) {

                val leg = path?.legTo(index)

                if (leg != null) {

                    travelMinutes = leg.durationMinutes
                    travelMeters = leg.distanceMeters
                    approximate = leg.isStraightLineFallback

                } else {

                    val previous = stops[index - 1]

                    val straightLine = distanceBetween(
                        previous.latitude,
                        previous.longitude,
                        stop.latitude,
                        stop.longitude
                    ) * WALKING_DETOUR_FACTOR

                    travelMeters = straightLine.toInt()
                    travelMinutes = minutesToWalk(straightLine)
                    approximate = true
                }
            }

            clock += travelMinutes

            val arrival = clock

            clock += stop.estimatedVisitMinutes

            scheduled.add(
                ScheduledStop(
                    stop = stop,
                    travelMinutesToHere = travelMinutes,
                    travelMetersToHere = travelMeters,
                    travelIsApproximate = approximate,
                    arrivalMinutesFromStart = arrival,
                    departureMinutesFromStart = clock
                )
            )
        }

        return scheduled
    }

    /** Moves the stop at [fromIndex] to [toIndex], renumbering the rest. */
    fun moveStop(
        stops: List<ItineraryStop>,
        fromIndex: Int,
        toIndex: Int
    ): List<ItineraryStop> {

        if (fromIndex !in stops.indices || toIndex !in stops.indices || fromIndex == toIndex) {
            return stops
        }

        val reordered = stops.toMutableList()

        reordered.add(toIndex, reordered.removeAt(fromIndex))

        return reordered.mapIndexed { index, stop -> stop.copy(order = index) }
    }

    // ----- ordering internals -----

    private fun nearestNeighborOrder(
        startLatitude: Double,
        startLongitude: Double,
        stops: List<ItineraryStop>
    ): List<ItineraryStop> {

        val remaining = stops.toMutableList()
        val ordered = mutableListOf<ItineraryStop>()

        var currentLat = startLatitude
        var currentLng = startLongitude

        while (remaining.isNotEmpty()) {

            var nearestIndex = 0
            var nearestDistance = Double.MAX_VALUE

            for (i in remaining.indices) {

                val distance = distanceBetween(
                    currentLat,
                    currentLng,
                    remaining[i].latitude,
                    remaining[i].longitude
                )

                if (distance < nearestDistance) {
                    nearestDistance = distance
                    nearestIndex = i
                }
            }

            val next = remaining.removeAt(nearestIndex)

            currentLat = next.latitude
            currentLng = next.longitude

            ordered.add(next)
        }

        return ordered
    }

    /**
     * Classic 2-opt on an open path: the start point is pinned, the end is
     * free. Reversing the segment between two positions swaps at most two
     * edges, so each candidate is scored by comparing just those edges
     * instead of re-measuring the whole tour.
     */
    private fun twoOptImprove(
        startLatitude: Double,
        startLongitude: Double,
        stops: List<ItineraryStop>
    ): List<ItineraryStop> {

        if (stops.size < 3) {
            return stops
        }

        val route = stops.toMutableList()

        // Position -1 is the fixed starting point.
        fun latOf(index: Int) = if (index < 0) startLatitude else route[index].latitude
        fun lngOf(index: Int) = if (index < 0) startLongitude else route[index].longitude

        fun edge(a: Int, b: Int): Double {
            if (b >= route.size) return 0.0
            return distanceBetween(latOf(a), lngOf(a), latOf(b), lngOf(b))
        }

        var passes = 0
        var improved = true

        while (improved && passes < MAX_TWO_OPT_PASSES) {

            improved = false
            passes++

            for (i in route.indices) {

                for (j in i + 1 until route.size) {

                    // The edges reversing [i..j] would remove, against the
                    // ones that would replace them. When j is the last stop
                    // the trailing edge does not exist and edge() returns 0
                    // on both sides, which is what keeps the open end free.
                    val removed = edge(i - 1, i) + edge(j, j + 1)
                    val added = edge(i - 1, j) + edge(i, j + 1)

                    if (added + IMPROVEMENT_EPSILON < removed) {

                        var left = i
                        var right = j

                        while (left < right) {
                            val temp = route[left]
                            route[left] = route[right]
                            route[right] = temp
                            left++
                            right--
                        }

                        improved = true
                    }
                }
            }
        }

        return route
    }

    private fun pathLength(
        startLatitude: Double,
        startLongitude: Double,
        stops: List<ItineraryStop>
    ): Double {

        var total = 0.0
        var currentLat = startLatitude
        var currentLng = startLongitude

        stops.forEach { stop ->
            total += distanceBetween(currentLat, currentLng, stop.latitude, stop.longitude)
            currentLat = stop.latitude
            currentLng = stop.longitude
        }

        return total
    }

    private fun distanceBetween(
        fromLatitude: Double,
        fromLongitude: Double,
        toLatitude: Double,
        toLongitude: Double
    ): Double {

        val results = FloatArray(1)

        Location.distanceBetween(
            fromLatitude,
            fromLongitude,
            toLatitude,
            toLongitude,
            results
        )

        return results[0].toDouble()
    }

    private fun minutesToWalk(meters: Double): Int {
        return ceil(meters / (WALKING_SPEED_METERS_PER_SECOND * 60))
            .toInt()
            .coerceAtLeast(1)
    }

    /**
     * Places matching one of [selectedCategories], within walking distance
     * of at least one of [selectedSites], nearest first. Mirrors
     * HomeViewModel's "food" bucket (cafe/restaurant both count) so
     * recommendations behave the same way Home's category filter does.
     */
    fun recommendPlaces(
        selectedSites: List<HistoricalSite>,
        selectedCategories: Set<String>,
        allPlaces: List<Place>,
        excludeIds: Set<String>
    ): List<Place> {

        if (selectedCategories.isEmpty() || selectedSites.isEmpty()) {
            return emptyList()
        }

        val siteCoordinates = selectedSites.mapNotNull { site ->
            val lat = site.latitude ?: return@mapNotNull null
            val lng = site.longitude ?: return@mapNotNull null
            lat to lng
        }

        if (siteCoordinates.isEmpty()) {
            return emptyList()
        }

        return allPlaces
            .asSequence()
            .filter { place -> place.documentId !in excludeIds }
            .filter { place -> place.latitude != null && place.longitude != null }
            .filter { place -> matchesAnyCategory(place.category, selectedCategories) }
            .mapNotNull { place ->

                val nearestDistance = siteCoordinates.minOf { (siteLat, siteLng) ->
                    distanceBetween(siteLat, siteLng, place.latitude!!, place.longitude!!)
                }

                if (nearestDistance <= RECOMMENDATION_RADIUS_METERS) {
                    place to nearestDistance
                } else {
                    null
                }
            }
            .sortedBy { (_, distance) -> distance }
            .take(MAX_RECOMMENDATIONS)
            .map { (place, _) -> place }
            .toList()
    }

    private fun matchesAnyCategory(placeCategory: String, selectedCategories: Set<String>): Boolean {

        for (category in selectedCategories) {

            val matches = when (category) {
                "food" -> placeCategory.equals("food", true) ||
                    placeCategory.equals("cafe", true) ||
                    placeCategory.equals("restaurant", true)
                else -> placeCategory.equals(category, true)
            }

            if (matches) return true
        }

        return false
    }

    /** Default time-on-site used when building a stop, before the user can adjust it. */
    fun defaultVisitMinutes(refType: String): Int {
        return if (refType == "site") 30 else 20
    }
}
