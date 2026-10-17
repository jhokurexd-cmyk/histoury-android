package com.histoury.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * "Is the visitor at this site?" — one answer shared by in-app arrival
 * detection ([ArrivalWatcher]) and the Site Details AR button, using the
 * same rules as the AR screen's own fence check, so the three never
 * disagree about whether someone is standing at a site.
 */
object SiteProximity {

    /** No consumer GPS can satisfy a fence of a few metres. */
    const val MIN_RADIUS_METERS = 15f

    /** Most of a fix's uncertainty that may count towards being inside. */
    private const val MAX_ACCURACY_ALLOWANCE_METERS = 50f

    /** Allowance for a fix that reports no accuracy at all. */
    private const val UNKNOWN_ACCURACY_ALLOWANCE_METERS = 20f

    /** Past this a fix is a guess (cell-tower level) and proves nothing. */
    private const val MAX_TRUSTED_ACCURACY_METERS = 75f

    fun hasPermission(context: Context): Boolean =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    fun isTrustworthy(location: Location): Boolean =
        !location.hasAccuracy() || location.accuracy <= MAX_TRUSTED_ACCURACY_METERS

    fun distanceMeters(location: Location, latitude: Double, longitude: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(location.latitude, location.longitude, latitude, longitude, results)
        return results[0]
    }

    /**
     * True when the fix's uncertainty circle reaches the fence — the same
     * reading of a fix that Android's own geofencing uses. In Intramuros,
     * between high stone walls, 20–50 m of reported error is routine, and
     * comparing only the centre point locked people out of sites they were
     * standing in.
     */
    fun isInside(location: Location, latitude: Double, longitude: Double, radiusMeters: Double): Boolean {
        val allowance = if (location.hasAccuracy()) {
            location.accuracy.coerceIn(0f, MAX_ACCURACY_ALLOWANCE_METERS)
        } else {
            UNKNOWN_ACCURACY_ALLOWANCE_METERS
        }
        val radius = radiusMeters.toFloat().coerceAtLeast(MIN_RADIUS_METERS)
        return distanceMeters(location, latitude, longitude) - allowance <= radius
    }

    /**
     * Live GPS fixes while collected (stops when the collector is
     * cancelled), starting with the last known fix if there is one.
     * Emits nothing without location permission.
     */
    @SuppressLint("MissingPermission")
    fun updates(context: Context, intervalMs: Long): Flow<Location> = callbackFlow {

        if (!hasPermission(context)) {
            close()
            return@callbackFlow
        }

        val client = LocationServices.getFusedLocationProviderClient(context)

        runCatching {
            client.lastLocation.addOnSuccessListener { last -> if (last != null) trySend(last) }
        }

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it) }
            }
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .build()

        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: Exception) {
            // Permission revoked between the check and the request.
            close()
            return@callbackFlow
        }

        awaitClose { client.removeLocationUpdates(callback) }
    }
}
