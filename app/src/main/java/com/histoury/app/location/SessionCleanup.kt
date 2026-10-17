package com.histoury.app.location

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.histoury.app.data.itinerary.ActiveItineraryTrip

/**
 * Everything that must stop when nobody is signed in any more — logout from
 * any screen, a suspension, a deleted account.
 *
 * Arrival geofences are registered with Google Play Services, outside the
 * app, and kept firing after logout: the phone went on showing "You've
 * arrived" notifications, and tapping one opened the Arrival screen with no
 * session behind it. MainActivity calls this whenever Firebase's auth state
 * becomes signed-out, and GeofenceBroadcastReceiver checks the session too
 * (for fences that fire while the app isn't running).
 */
object SessionCleanup {

    suspend fun onSignedOut(context: Context) {
        val appContext = context.applicationContext

        runCatching { GeofenceManager(appContext).removeAllGeofences() }
            .onFailure { Log.w("SessionCleanup", "Couldn't remove geofences", it) }

        // Arrival notifications already on screen belong to the old session.
        runCatching { NotificationManagerCompat.from(appContext).cancelAll() }

        NavigationForegroundService.stop(appContext)

        ActiveItineraryTrip.clear()
    }
}
