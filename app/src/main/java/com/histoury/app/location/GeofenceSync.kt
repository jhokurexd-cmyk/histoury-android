package com.histoury.app.location

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.histoury.app.data.repository.GeofenceRepository
import com.histoury.app.util.DeviceLocation

/**
 * Re-registers every active site geofence from Firestore, for the paths that
 * run without the UI: after a reboot (Android drops all fences) and after
 * Play Services reports GEOFENCE_NOT_AVAILABLE (it drops them when location
 * is switched off). Home also re-registers whenever it is opened.
 */
object GeofenceSync {

    private const val RETRY_WORK_NAME = "geofence-resync"

    /** Returns false when the fences couldn't be loaded, so nothing was registered. */
    suspend fun resync(context: Context): Boolean {

        val geofenceManager = GeofenceManager(context.applicationContext)

        // Signed out: no arrival notifications for nobody.
        if (FirebaseAuth.getInstance().currentUser == null) {
            geofenceManager.removeAllGeofences()
            return true
        }

        // null = couldn't load (e.g. no signal right after boot):
        // keep whatever fences are registered rather than wipe them.
        val geofences = GeofenceRepository().getAllActiveGeofencesAtSiteCoordinates()
            ?: return false

        // Forced: this runs after a reboot or after Play Services dropped
        // every fence, so "already registered" can't be trusted.
        geofenceManager.registerGeofences(geofences, force = true)
        return true
    }

    /**
     * Resyncs now, and if that fails schedules a retry for once the device
     * is online. After a reboot Android has already dropped every fence, so
     * "keep whatever is registered" means none until the app is opened.
     */
    suspend fun resyncOrScheduleRetry(context: Context) {
        val synced = try {
            resync(context)
        } catch (e: Exception) {
            false
        }
        if (!synced) scheduleRetry(context)
    }

    /**
     * Play Services dropped every fence (GEOFENCE_NOT_AVAILABLE), usually
     * because location was switched off. Re-registering while it is still
     * off just fails, and the old retry waited for *network* (not the
     * problem) and gave up after 5 tries, so fences stayed gone until Home
     * was next opened. Now: if location is on, resync as usual; if it's off,
     * a worker with no network constraint keeps checking until it's back.
     * While the app process is alive, LocationStatusNotifier also resyncs
     * the moment location is switched back on.
     */
    suspend fun resyncWhenLocationAvailable(context: Context) {
        if (DeviceLocation.isEnabled(context.applicationContext)) {
            resyncOrScheduleRetry(context)
        } else {
            scheduleLocationRetry(context)
        }
    }

    private fun scheduleLocationRetry(context: Context) {
        // No constraints: the thing being waited for is location, not network.
        val request = OneTimeWorkRequestBuilder<GeofenceLocationResyncWorker>().build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(LOCATION_RETRY_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private const val LOCATION_RETRY_WORK_NAME = "geofence-resync-after-location"

    private fun scheduleRetry(context: Context) {
        val request = OneTimeWorkRequestBuilder<GeofenceResyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(RETRY_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}

class GeofenceResyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val synced = try {
            GeofenceSync.resync(applicationContext)
        } catch (e: Exception) {
            false
        }
        // Give up after a few tries; Home re-registers whenever it's opened.
        return when {
            synced -> Result.success()
            runAttemptCount < 5 -> Result.retry()
            else -> Result.failure()
        }
    }
}

/**
 * Waits for location to come back on, then re-registers the fences. Never
 * gives up while location is off (WorkManager's own backoff spaces the
 * checks out). Once location is on, a failed resync is handed to the normal
 * network-constrained retry above.
 */
class GeofenceLocationResyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!DeviceLocation.isEnabled(applicationContext)) return Result.retry()
        GeofenceSync.resyncOrScheduleRetry(applicationContext)
        return Result.success()
    }
}
