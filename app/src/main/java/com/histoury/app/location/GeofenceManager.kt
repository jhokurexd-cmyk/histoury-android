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
 * arrival (background detection; while the app is open [ArrivalWatcher]
 * answers sooner).
 *
 * Long enough that a single inaccurate fix cannot trigger it, short enough
 * that somebody who has walked up to the gate is not left waiting. Was a
 * minute, which on top of Android's own background delay felt broken.
 */
private const val DWELL_DELAY_MS = 30_000

/** Re-register unchanged fences after this long anyway, in case Play Services lost them. */
private const val MAX_REGISTRATION_AGE_MS = 12 * 60 * 60_000L

private const val PREFS_NAME = "histoury_geofences"
private const val KEY_SIGNATURE = "signature"
private const val KEY_REGISTERED_AT = "registered_at"

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

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    /**
     * Registers [geofences] with Play Services.
     *
     * Skipped when exactly the same fences were already registered recently,
     * unless [force] (after a reboot, or when Play Services dropped them).
     *
     * This matters more than it looks. Registering wipes Play Services'
     * memory of which fences the phone is inside, and a fence only fires on
     * a boundary crossing. Home used to re-register on nearly every visit,
     * so a visitor standing inside a site who opened Home reset the fence
     * and then got no arrival until they walked out and back in.
     */
    @SuppressLint("MissingPermission")
    suspend fun registerGeofences(geofences: List<Geofence>, force: Boolean = false) {

        val signature = signatureOf(geofences)
        val registeredAt = prefs.getLong(KEY_REGISTERED_AT, 0L)
        val fresh = System.currentTimeMillis() - registeredAt in 0 until MAX_REGISTRATION_AGE_MS
        if (!force && fresh && prefs.getString(KEY_SIGNATURE, null) == signature) {
            return
        }

        // Every caller passes the FULL current set of active geofences from
        // Firestore, never an incremental diff — so start from a clean
        // slate every time. addGeofences() below can only ADD a fence or
        // REPLACE one sharing the same requestId; it has no way to remove a
        // fence whose siteId Firestore no longer lists as active. Without
        // this, deactivating a site's geofence in the admin panel never
        // reached devices that already had it registered: the NEVER_EXPIRE
        // fence just stayed on the phone forever, still firing arrivals for
        // a site the team turned off, and each one left behind eats into
        // Android's fixed 100-geofence-per-app cap.
        removeAllGeofences()

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

        addGeofences(request)

        prefs.edit()
            .putString(KEY_SIGNATURE, signature)
            .putLong(KEY_REGISTERED_AT, System.currentTimeMillis())
            .apply()
    }

    /** Which fences, where and how big: a change in any of these means re-register. */
    private fun signatureOf(geofences: List<Geofence>): String =
        geofences
            .filter { it.siteId.isNotBlank() }
            .sortedBy { it.siteId }
            .joinToString("|") { "${it.siteId}@${it.latitude},${it.longitude},${it.radius}" }
            .let { "v2:$DWELL_DELAY_MS:$it" }

    @SuppressLint("MissingPermission")
    private suspend fun addGeofences(request: GeofencingRequest) {

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

        // Whatever was registered is gone, so the next register must not skip.
        prefs.edit().remove(KEY_SIGNATURE).remove(KEY_REGISTERED_AT).apply()

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
