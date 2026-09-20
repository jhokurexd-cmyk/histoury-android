package com.histoury.app.location

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence as PlayGeofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.histoury.app.data.model.Geofence
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * How long a visitor must remain inside a fence before it counts as an
 * arrival.
 *
 * A minute is long enough that a single inaccurate fix cannot trigger it,
 * and short enough that somebody who has actually walked up to the gate is
 * not left waiting.
 */
private const val DWELL_DELAY_MS = 60_000

class GeofenceManager(private val context: Context) {

    private val geofencingClient: GeofencingClient =
        LocationServices.getGeofencingClient(context)

    private val geofencePendingIntent: PendingIntent by lazy {

        val intent = Intent(context, GeofenceBroadcastReceiver::class.java).apply {
            action = ACTION_GEOFENCE_EVENT
        }

        PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    @SuppressLint("MissingPermission")
    suspend fun registerGeofences(geofences: List<Geofence>) {

        if (geofences.isEmpty()) {
            return
        }

        val playGeofences = geofences.mapNotNull { geofence ->

            if (geofence.siteId.isBlank()) {
                return@mapNotNull null
            }

            PlayGeofence.Builder()
                .setRequestId(geofence.siteId)
                .setCircularRegion(
                    geofence.latitude,
                    geofence.longitude,
                    geofence.radius.toFloat().coerceAtLeast(10f)
                )
                .setExpirationDuration(PlayGeofence.NEVER_EXPIRE)
                .setTransitionTypes(PlayGeofence.GEOFENCE_TRANSITION_DWELL)
                .setLoiteringDelay(DWELL_DELAY_MS)
                .build()
        }

        if (playGeofences.isEmpty()) {
            return
        }

        val request = GeofencingRequest.Builder()
            // No initial trigger, and DWELL rather than ENTER.
            //
            // INITIAL_TRIGGER_ENTER fires the moment a fence is registered
            // if Play Services *believes* the device is already inside it —
            // and that belief comes from its cached last-known location,
            // which can be hours old or a cell-tower fix accurate to
            // kilometres. Registering while sitting at home in Caloocan with
            // a stale Intramuros fix reported an arrival at Baluarte de San
            // Diego. Since the app re-registers whenever Home is opened,
            // that misfire could repeat indefinitely.
            //
            // Dropping it means a fence only fires on a real boundary
            // crossing. DWELL then requires the visitor to still be there
            // after the loitering delay, which a single bad fix cannot
            // satisfy — a wrong reading has to be wrong consistently for a
            // minute to get through.
            .setInitialTrigger(0)
            .addGeofences(playGeofences)
            .build()

        suspendCancellableCoroutine<Unit> { continuation ->

            geofencingClient.addGeofences(request, geofencePendingIntent)
                .addOnSuccessListener {
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }
                .addOnFailureListener { exception ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(exception)
                    }
                }
        }
    }

    suspend fun removeAllGeofences() {

        suspendCancellableCoroutine<Unit> { continuation ->

            geofencingClient.removeGeofences(geofencePendingIntent)
                .addOnSuccessListener {
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }
                .addOnFailureListener { exception ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(exception)
                    }
                }
        }
    }

    companion object {
        const val ACTION_GEOFENCE_EVENT = "com.histoury.app.ACTION_GEOFENCE_EVENT"
    }
}
