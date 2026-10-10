package com.histoury.app.data.repository

import com.google.android.gms.maps.model.LatLng
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

/**
 * One maneuver-to-maneuver segment of a route, as returned by the Routes
 * API's per-step breakdown.
 *
 * By convention, [maneuver]/[instructionText] describe what to do AT
 * [startLatitude]/[startLongitude] — having just finished the previous
 * step — before traveling this step's own path to
 * [endLatitude]/[endLongitude], where the *next* step's maneuver applies.
 */
data class RouteStep(
    val maneuver: String,
    val instructionText: String,
    val distanceMeters: Int,
    val startLatitude: Double,
    val startLongitude: Double,
    val endLatitude: Double,
    val endLongitude: Double,
    /** "WALK", "DRIVE", "TWO_WHEELER" or "TRANSIT"; blank from older servers. */
    val travelMode: String = ""
)

/** A verified access point the route was sent to (see historical_sites.entrances). */
data class RouteEntrance(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val accessType: String,
    val isPrimary: Boolean
)

/**
 * Where the route actually ends. With a verified visitor entrance that is
 * the entrance, NOT the site's marker; [entranceStatus] is "unverified"
 * when no verified entrance exists yet and the route ends at the site's own
 * coordinates instead.
 */
data class RouteDestination(
    val latitude: Double,
    val longitude: Double,
    val entranceStatus: String,
    val entrance: RouteEntrance?,
    val dropOff: RouteEntrance?
) {
    val isVerifiedEntrance: Boolean
        get() = entranceStatus == "verified" && entrance != null
}

data class DirectionsResult(
    val distanceMeters: Int,
    val durationSeconds: Int,
    val routePoints: List<LatLng>,
    // Empty when the backend hasn't been redeployed with step support yet,
    // or when a particular route genuinely returns none — callers must
    // tolerate an empty list rather than assume steps are always present.
    val steps: List<RouteStep> = emptyList(),
    /** "ors", "valhalla" or "google" — an internal detail, never a choice the visitor makes. */
    val provider: String = "google",
    /**
     * Credit line that must be shown with this route (OpenRouteService /
     * OpenStreetMap routes). Null for Google routes: the Google map itself
     * carries Google's attribution.
     */
    val attribution: String? = null,
    /** Notes worth showing, e.g. "the exact entrance hasn't been verified". */
    val warnings: List<String> = emptyList(),
    /** Null when the server predates getRoute (no entrance information). */
    val destination: RouteDestination? = null,
    /**
     * Car / motorcycle trips to a drop-off point: the walk from there to the
     * visitor entrance. Empty otherwise.
     */
    val finalWalkPoints: List<LatLng> = emptyList(),
    val finalWalkDistanceMeters: Int = 0,
    val finalWalkDurationSeconds: Int = 0
)

/**
 * Why a route couldn't be produced, so callers can say something true
 * instead of quietly drawing a straight line.
 */
enum class DirectionsFailure {
    /** The Routes API has no route between these points for this mode. */
    NO_ROUTE,
    /** No connection, or the service didn't answer. Worth retrying. */
    UNAVAILABLE,
    /** Still rate limited after retrying. */
    RATE_LIMITED
}

sealed interface DirectionsOutcome {
    data class Success(val result: DirectionsResult) : DirectionsOutcome
    data class Failure(val reason: DirectionsFailure) : DirectionsOutcome
}

enum class TravelMode(val wireValue: String) {
    WALKING("WALK"),
    DRIVING("DRIVE"),
    MOTORCYCLE("TWO_WHEELER")
}

class DirectionsRepository {

    // Same region as every other Histoury function (getDirections used to
    // be the only one left in the default us-central1).
    private val functions = FirebaseFunctions.getInstance("asia-southeast1")

    /** The route, or null for any failure. See [getDirectionsOutcome] for why. */
    suspend fun getDirections(
        origin: LatLng,
        destination: LatLng,
        mode: TravelMode = TravelMode.WALKING,
        destinationSiteId: String? = null
    ): DirectionsResult? =
        (getDirectionsOutcome(origin, destination, mode, destinationSiteId) as? DirectionsOutcome.Success)?.result

    /**
     * Fetches a route, retrying when the server says "too many requests".
     *
     * Rate limiting used to be the most common reason a leg failed: an
     * itinerary asks for several legs at once, the server allowed one call
     * per second, and every other leg came back rejected — and was drawn as a
     * straight line through the walls. A rejected call is now waited out
     * (the server says how long) and tried again before giving up.
     */
    suspend fun getDirectionsOutcome(
        origin: LatLng,
        destination: LatLng,
        mode: TravelMode = TravelMode.WALKING,
        /**
         * The historical site being navigated to, when there is one. Lets the
         * server send the route to the site's verified visitor entrance
         * instead of the nearest road to the site's centre.
         */
        destinationSiteId: String? = null,
        avoidHighways: Boolean = false
    ): DirectionsOutcome {

        var attempt = 0

        while (true) {

            val outcome = requestOnce(origin, destination, mode, destinationSiteId, avoidHighways)

            if (outcome !is RateLimited || attempt >= MAX_RATE_LIMIT_RETRIES) {
                return when (outcome) {
                    is RateLimited -> DirectionsOutcome.Failure(DirectionsFailure.RATE_LIMITED)
                    is Done -> outcome.outcome
                }
            }

            attempt++
            delay(outcome.retryAfterMillis.coerceIn(MIN_RETRY_DELAY_MS, MAX_RETRY_DELAY_MS))
        }
    }

    private sealed interface Attempt
    private data class Done(val outcome: DirectionsOutcome) : Attempt
    private data class RateLimited(val retryAfterMillis: Long) : Attempt

    private suspend fun requestOnce(
        origin: LatLng,
        destination: LatLng,
        mode: TravelMode,
        destinationSiteId: String?,
        avoidHighways: Boolean
    ): Attempt {

        // getRoute (hybrid providers + verified entrances) first. Until it is
        // deployed the callable doesn't exist; that is remembered for the
        // rest of the process and the original getDirections is used, so the
        // app works the same against either backend.
        if (!getRouteMissing) {
            when (val attempt = callGetRoute(origin, destination, mode, destinationSiteId, avoidHighways)) {
                null -> getRouteMissing = true
                else -> return attempt
            }
        }

        return callLegacyGetDirections(origin, destination, mode)
    }

    private fun point(p: LatLng) = hashMapOf("lat" to p.latitude, "lng" to p.longitude)

    /** Null only when the getRoute function isn't deployed. */
    private suspend fun callGetRoute(
        origin: LatLng,
        destination: LatLng,
        mode: TravelMode,
        destinationSiteId: String?,
        avoidHighways: Boolean
    ): Attempt? {
        return try {
            val requestData = hashMapOf<String, Any>(
                "origin" to point(origin),
                "destination" to point(destination),
                "mode" to mode.wireValue
            )
            if (!destinationSiteId.isNullOrBlank()) requestData["destinationSiteId"] = destinationSiteId
            if (avoidHighways) requestData["options"] = hashMapOf("avoidHighways" to true)

            val data = functions.getHttpsCallable("getRoute").call(requestData).await().data as? Map<*, *>
            Done(parseResult(data))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: FirebaseFunctionsException) {
            val reason = (e.details as? Map<*, *>)?.get("reason") as? String
            when {
                // A missing function answers NOT_FOUND with no details; a
                // real "no route" from getRoute always carries a reason.
                e.code == FirebaseFunctionsException.Code.NOT_FOUND && reason == null -> null
                e.code == FirebaseFunctionsException.Code.NOT_FOUND ->
                    Done(DirectionsOutcome.Failure(DirectionsFailure.NO_ROUTE))
                e.code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> {
                    val seconds = ((e.details as? Map<*, *>)?.get("retryAfterSeconds") as? Number)?.toLong()
                    RateLimited(retryAfterMillis = (seconds ?: 1L) * 1000L)
                }
                else -> Done(DirectionsOutcome.Failure(DirectionsFailure.UNAVAILABLE))
            }
        } catch (e: Exception) {
            Done(DirectionsOutcome.Failure(DirectionsFailure.UNAVAILABLE))
        }
    }

    private suspend fun callLegacyGetDirections(
        origin: LatLng,
        destination: LatLng,
        mode: TravelMode
    ): Attempt {

        return try {

            val requestData = hashMapOf(
                "origin" to point(origin),
                "destination" to point(destination),
                "mode" to mode.wireValue
            )

            val result = functions.getHttpsCallable("getDirections").call(requestData).await()

            Done(parseResult(result.data as? Map<*, *>))

        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: FirebaseFunctionsException) {
            when (e.code) {
                FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> {
                    val seconds = ((e.details as? Map<*, *>)?.get("retryAfterSeconds") as? Number)?.toLong()
                    RateLimited(retryAfterMillis = (seconds ?: 1L) * 1000L)
                }
                FirebaseFunctionsException.Code.NOT_FOUND ->
                    Done(DirectionsOutcome.Failure(DirectionsFailure.NO_ROUTE))
                else -> Done(DirectionsOutcome.Failure(DirectionsFailure.UNAVAILABLE))
            }
        } catch (e: Exception) {
            Done(DirectionsOutcome.Failure(DirectionsFailure.UNAVAILABLE))
        }
    }

    /**
     * Reads either backend's answer: getRoute's response is a superset of
     * getDirections', so the same parser serves both and every extra field
     * is optional.
     */
    private fun parseResult(resultData: Map<*, *>?): DirectionsOutcome {

        val malformed = DirectionsOutcome.Failure(DirectionsFailure.UNAVAILABLE)

        if (resultData == null) return malformed

        val distanceMeters = (resultData["distanceMeters"] as? Number)?.toInt() ?: return malformed

        val durationSeconds = (resultData["durationSeconds"] as? Number)?.toInt() ?: return malformed

        val encodedPolyline = resultData["polyline"] as? String ?: return malformed

        val points = decodePolyline(encodedPolyline)

        if (points.size < 2) {
            return DirectionsOutcome.Failure(DirectionsFailure.NO_ROUTE)
        }

        val finalWalk = resultData["finalWalk"] as? Map<*, *>

        return DirectionsOutcome.Success(
            DirectionsResult(
                distanceMeters = distanceMeters,
                durationSeconds = durationSeconds,
                routePoints = points,
                steps = parseSteps(resultData["steps"]),
                provider = resultData["provider"] as? String ?: "google",
                attribution = (resultData["attribution"] as? String)?.takeIf { it.isNotBlank() },
                warnings = (resultData["warnings"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
                destination = parseDestination(resultData["destination"]),
                finalWalkPoints = (finalWalk?.get("polyline") as? String)?.let { decodePolyline(it) }.orEmpty(),
                finalWalkDistanceMeters = (finalWalk?.get("distanceMeters") as? Number)?.toInt() ?: 0,
                finalWalkDurationSeconds = (finalWalk?.get("durationSeconds") as? Number)?.toInt() ?: 0
            )
        )
    }

    private fun parseEntrance(raw: Any?): RouteEntrance? {
        val map = raw as? Map<*, *> ?: return null
        val lat = (map["lat"] as? Number)?.toDouble() ?: return null
        val lng = (map["lng"] as? Number)?.toDouble() ?: return null
        return RouteEntrance(
            id = map["id"] as? String ?: "",
            name = map["name"] as? String ?: "Visitor entrance",
            latitude = lat,
            longitude = lng,
            accessType = map["accessType"] as? String ?: "pedestrian",
            isPrimary = map["isPrimary"] as? Boolean ?: false
        )
    }

    private fun parseDestination(raw: Any?): RouteDestination? {
        val map = raw as? Map<*, *> ?: return null
        val lat = (map["lat"] as? Number)?.toDouble() ?: return null
        val lng = (map["lng"] as? Number)?.toDouble() ?: return null
        return RouteDestination(
            latitude = lat,
            longitude = lng,
            entranceStatus = map["entranceStatus"] as? String ?: "unverified",
            entrance = parseEntrance(map["entrance"]),
            dropOff = parseEntrance(map["dropOff"])
        )
    }

    private companion object {
        /** Set once getRoute turns out not to be deployed; legacy getDirections is used from then on. */
        @Volatile
        var getRouteMissing = false

        const val MAX_RATE_LIMIT_RETRIES = 3
        const val MIN_RETRY_DELAY_MS = 800L
        const val MAX_RETRY_DELAY_MS = 5_000L
    }

    // Tolerant on purpose: an older deployed Cloud Function (before step
    // support was added) simply won't include "steps" at all, and this
    // should degrade to "no turn-by-turn voice guidance" rather than break
    // the rest of the route response.
    private fun parseSteps(rawSteps: Any?): List<RouteStep> {

        val stepList = rawSteps as? List<*> ?: return emptyList()

        return stepList.mapNotNull { rawStep ->

            val stepMap = rawStep as? Map<*, *> ?: return@mapNotNull null

            val maneuver = stepMap["maneuver"] as? String ?: "STRAIGHT"
            val instructionText = stepMap["instructions"] as? String ?: ""
            val stepDistanceMeters = (stepMap["distanceMeters"] as? Number)?.toInt() ?: 0

            val startMap = stepMap["startLocation"] as? Map<*, *>
            val endMap = stepMap["endLocation"] as? Map<*, *>

            val startLat = (startMap?.get("lat") as? Number)?.toDouble()
            val startLng = (startMap?.get("lng") as? Number)?.toDouble()
            val endLat = (endMap?.get("lat") as? Number)?.toDouble()
            val endLng = (endMap?.get("lng") as? Number)?.toDouble()

            if (startLat == null || startLng == null || endLat == null || endLng == null) {
                return@mapNotNull null
            }

            RouteStep(
                maneuver = maneuver,
                instructionText = instructionText,
                distanceMeters = stepDistanceMeters,
                startLatitude = startLat,
                startLongitude = startLng,
                endLatitude = endLat,
                endLongitude = endLng,
                travelMode = stepMap["travelMode"] as? String ?: ""
            )
        }
    }

    private fun decodePolyline(encoded: String): List<LatLng> {

        val points = mutableListOf<LatLng>()

        var index = 0

        val length = encoded.length

        var lat = 0

        var lng = 0

        while (index < length) {

            var result = 0

            var shift = 0

            var b: Int

            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)

            val deltaLat = if (result and 1 != 0) (result shr 1).inv() else (result shr 1)

            lat += deltaLat

            result = 0

            shift = 0

            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)

            val deltaLng = if (result and 1 != 0) (result shr 1).inv() else (result shr 1)

            lng += deltaLng

            points.add(
                LatLng(
                    lat / 1E5,
                    lng / 1E5
                )
            )
        }

        return points
    }
}
