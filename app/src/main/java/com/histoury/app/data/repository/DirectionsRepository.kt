package com.histoury.app.data.repository

import com.google.android.gms.maps.model.LatLng
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await

data class DirectionsResult(
    val distanceMeters: Int,
    val durationSeconds: Int,
    val routePoints: List<LatLng>
)

enum class TravelMode(val wireValue: String) {
    WALKING("WALK"),
    DRIVING("DRIVE"),
    MOTORCYCLE("TWO_WHEELER")
}

class DirectionsRepository {

    private val functions = FirebaseFunctions.getInstance()

    suspend fun getDirections(
        origin: LatLng,
        destination: LatLng,
        mode: TravelMode = TravelMode.WALKING
    ): DirectionsResult? {

        return try {

            val requestData = hashMapOf(
                "origin" to hashMapOf(
                    "lat" to origin.latitude,
                    "lng" to origin.longitude
                ),
                "destination" to hashMapOf(
                    "lat" to destination.latitude,
                    "lng" to destination.longitude
                ),
                "mode" to mode.wireValue
            )

            val callable = functions.getHttpsCallable("getDirections")

            val result = callable.call(requestData).await()

            val resultData = result.data as? Map<*, *> ?: return null

            val distanceMeters = (resultData["distanceMeters"] as? Number)?.toInt()
                ?: return null

            val durationSeconds = (resultData["durationSeconds"] as? Number)?.toInt()
                ?: return null

            val encodedPolyline = resultData["polyline"] as? String ?: return null

            DirectionsResult(
                distanceMeters = distanceMeters,
                durationSeconds = durationSeconds,
                routePoints = decodePolyline(encodedPolyline)
            )

        } catch (e: FirebaseFunctionsException) {
            null
        } catch (e: Exception) {
            null
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
