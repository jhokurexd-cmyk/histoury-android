package com.histoury.app.data.routing

import android.location.Location
import com.google.android.gms.maps.model.LatLng
import com.histoury.app.data.repository.DirectionsRepository
import com.histoury.app.data.repository.TravelMode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Turns an ordered list of stops into a path that follows real streets.
 *
 * The itinerary map used to draw a Polyline straight through the stop
 * coordinates, which cut across walls, moats and blocks — inside a walled
 * city that reads as obviously wrong. This asks the existing getDirections
 * Cloud Function for each consecutive pair and stitches the decoded
 * polylines together.
 *
 * Cost control, since Routes API calls are billed:
 *
 *  - n stops means n-1 calls, not n². Ordering is decided first, on
 *    straight-line distance, and only the chosen order is routed.
 *  - Legs are cached per (origin, destination, mode) for the process
 *    lifetime, so reordering, re-optimizing, or switching back to a mode
 *    already fetched costs nothing.
 *  - Legs are fetched a few at a time rather than all at once.
 */
class RoutePathRepository(
    private val directionsRepository: DirectionsRepository = DirectionsRepository()
) {

    /** Shared across instances — the same leg is often requested by several screens. */
    private val legCache = ConcurrentHashMap<String, RouteLeg>()

    suspend fun getPath(
        orderedPoints: List<LatLng>,
        mode: TravelMode = TravelMode.WALKING
    ): RoutePath {

        if (orderedPoints.size < 2) {
            return RoutePath.empty(mode)
        }

        val pairs = orderedPoints.indices.drop(1).map { index ->
            index to (orderedPoints[index - 1] to orderedPoints[index])
        }

        val legs = mutableListOf<RouteLeg>()

        coroutineScope {

            pairs.chunked(PARALLEL_LEGS).forEach { batch ->

                val fetched = batch.map { (toIndex, endpoints) ->
                    async {
                        fetchLeg(
                            fromIndex = toIndex - 1,
                            toIndex = toIndex,
                            origin = endpoints.first,
                            destination = endpoints.second,
                            mode = mode
                        )
                    }
                }.awaitAll()

                legs.addAll(fetched)
            }
        }

        return RoutePath(legs = legs.sortedBy { it.toIndex }, mode = mode)
    }

    private suspend fun fetchLeg(
        fromIndex: Int,
        toIndex: Int,
        origin: LatLng,
        destination: LatLng,
        mode: TravelMode
    ): RouteLeg {

        val key = cacheKey(origin, destination, mode)

        legCache[key]?.let { cached ->
            return cached.copy(fromIndex = fromIndex, toIndex = toIndex)
        }

        val result = directionsRepository.getDirections(origin, destination, mode)

        val leg = if (result != null && result.routePoints.size >= 2) {

            RouteLeg(
                fromIndex = fromIndex,
                toIndex = toIndex,
                points = result.routePoints,
                distanceMeters = result.distanceMeters,
                durationSeconds = result.durationSeconds,
                isStraightLineFallback = false
            )

        } else {

            // Offline, quota exhausted, or no route for this mode. A dashed
            // straight line with an honest estimate beats an empty map.
            straightLineLeg(fromIndex, toIndex, origin, destination, mode)
        }

        if (!leg.isStraightLineFallback) {
            legCache[key] = leg
        }

        return leg
    }

    private fun straightLineLeg(
        fromIndex: Int,
        toIndex: Int,
        origin: LatLng,
        destination: LatLng,
        mode: TravelMode
    ): RouteLeg {

        val results = FloatArray(1)

        Location.distanceBetween(
            origin.latitude,
            origin.longitude,
            destination.latitude,
            destination.longitude,
            results
        )

        // Streets never run door to door in a straight line, so the raw
        // crow-flies figure is padded before it is shown to anyone.
        val estimatedMeters = (results[0] * STREET_DETOUR_FACTOR).roundToInt()

        val speed = when (mode) {
            TravelMode.WALKING -> WALKING_METERS_PER_SECOND
            TravelMode.DRIVING -> DRIVING_METERS_PER_SECOND
            TravelMode.MOTORCYCLE -> MOTORCYCLE_METERS_PER_SECOND
        }

        return RouteLeg(
            fromIndex = fromIndex,
            toIndex = toIndex,
            points = listOf(origin, destination),
            distanceMeters = estimatedMeters,
            durationSeconds = ceil(estimatedMeters / speed).toInt().coerceAtLeast(1),
            isStraightLineFallback = true
        )
    }

    private fun cacheKey(origin: LatLng, destination: LatLng, mode: TravelMode): String {

        // ~1m precision. Stop coordinates are fixed values from Firestore, so
        // rounding only guards against float noise in the string form.
        fun format(value: Double) = String.format("%.5f", value)

        return "${mode.wireValue}|${format(origin.latitude)},${format(origin.longitude)}" +
            "|${format(destination.latitude)},${format(destination.longitude)}"
    }

    companion object {

        private const val PARALLEL_LEGS = 4

        private const val STREET_DETOUR_FACTOR = 1.3f

        private const val WALKING_METERS_PER_SECOND = 1.4f

        private const val DRIVING_METERS_PER_SECOND = 6.5f

        private const val MOTORCYCLE_METERS_PER_SECOND = 7.5f
    }
}
