package com.histoury.app.location

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.histoury.app.data.model.Geofence
import com.histoury.app.data.repository.GeofenceRepository
import com.histoury.app.data.repository.VisitHistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Detects arrivals while Histoury is OPEN, from the app's own GPS fixes.
 *
 * Android's geofencing (see [GeofenceManager]) only reacts to the phone
 * CROSSING a fence's edge, and in the background it can take minutes to
 * notice. So a visitor who opened the app while already standing inside a
 * site, or whose fence was registered while they were inside, got no
 * Arrival screen until they walked out and back in — it felt like you had
 * to be right next to the site. While the app is on screen this checks
 * every fix against every site's fence directly: being inside is enough,
 * no crossing needed.
 *
 * An arrival needs two trustworthy fixes inside the same fence at least
 * [CONFIRM_MS] apart, so one bad GPS jump can't trigger it. It then goes
 * through the same path as a geofence event: the visit is recorded (once
 * per site per 30 minutes, shared with the background geofence, so the two
 * never double up), the arrival notification is shown and the Arrival
 * screen opens.
 */
object ArrivalWatcher {

    private const val TAG = "ArrivalWatcher"

    /** How often to ask for a fix while the app is open. */
    private const val FIX_INTERVAL_MS = 8_000L

    /** Inside for at least this long (two fixes or more) before it counts. */
    private const val CONFIRM_MS = 12_000L

    /** Fences are re-read at most this often, so admin edits still arrive. */
    private const val FENCE_REFRESH_MS = 5 * 60_000L

    /** Leaving by this much beyond the radius re-arms a site. */
    private const val EXIT_MARGIN_METERS = 30f

    private var fences: List<Geofence> = emptyList()
    private var fencesLoadedAt = 0L

    // siteId -> when the visitor was first seen inside (elapsed realtime).
    private val insideSince = HashMap<String, Long>()

    // Sites already handled this time inside, until the visitor leaves.
    private val handled = HashSet<String>()

    /**
     * Runs until cancelled. Call while the app is visible (MainActivity uses
     * repeatOnLifecycle(STARTED)); it stops with the screen, so it never
     * uses GPS in the background.
     */
    suspend fun watch(context: Context) {

        val appContext = context.applicationContext

        // Signed out, or location not allowed yet (Home asks for it): check
        // again shortly rather than giving up for the whole visit.
        while (true) {
            if (FirebaseAuth.getInstance().currentUser != null && SiteProximity.hasPermission(appContext)) {
                refreshFences()
                watchFixes(appContext)
            }
            delay(RECHECK_MS)
        }
    }

    /** Older fixes say where the phone was, not where it is. */
    private const val MAX_FIX_AGE_MS = 2 * 60_000L

    /** How often to look again for sign-in / permission. */
    private const val RECHECK_MS = 5_000L

    private suspend fun watchFixes(appContext: Context) {

        SiteProximity.updates(appContext, FIX_INTERVAL_MS).collect { location ->

            if (FirebaseAuth.getInstance().currentUser == null) return@collect
            if (SystemClock.elapsedRealtime() - fencesLoadedAt > FENCE_REFRESH_MS) refreshFences()
            if (!SiteProximity.isTrustworthy(location)) return@collect

            val now = SystemClock.elapsedRealtime()

            // The first value can be Android's cached last fix, which may be
            // hours old. Only fixes from the last two minutes count.
            if (now - location.elapsedRealtimeNanos / 1_000_000L > MAX_FIX_AGE_MS) return@collect

            fences.forEach { fence ->

                val siteId = fence.siteId
                if (siteId.isBlank()) return@forEach

                if (SiteProximity.isInside(location, fence.latitude, fence.longitude, fence.radius)) {
                    val since = insideSince.getOrPut(siteId) { now }
                    if (siteId !in handled && now - since >= CONFIRM_MS) {
                        handled += siteId
                        arrive(appContext, siteId)
                    }
                } else {
                    insideSince.remove(siteId)
                    val radius = fence.radius.toFloat().coerceAtLeast(SiteProximity.MIN_RADIUS_METERS)
                    val distance = SiteProximity.distanceMeters(location, fence.latitude, fence.longitude)
                    if (distance > radius + EXIT_MARGIN_METERS) handled -= siteId
                }
            }
        }
    }

    private suspend fun refreshFences() {
        val loaded = runCatching {
            withContext(Dispatchers.IO) { GeofenceRepository().getAllActiveGeofencesAtSiteCoordinates() }
        }.getOrNull()
        // null = couldn't load (offline): keep the last good list.
        if (loaded != null) fences = loaded
        fencesLoadedAt = SystemClock.elapsedRealtime()
    }

    private suspend fun arrive(context: Context, siteId: String) {
        val isNewArrival = withContext(Dispatchers.IO) {
            try {
                VisitHistoryRepository().recordVisitIfNotRecent(siteId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record visit", e)
                false
            }
        }
        // false = already arrived here within the last 30 minutes (the
        // background geofence may have just done it): nothing to repeat.
        if (!isNewArrival) return
        GeofenceBroadcastReceiver.showArrivalNotification(context, siteId)
        ArrivalEventBus.emit(siteId)
    }
}
