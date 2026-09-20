package com.histoury.app.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.histoury.app.data.repository.GeofenceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {

            try {

                val geofenceRepository = GeofenceRepository()

                val geofenceManager = GeofenceManager(context.applicationContext)

                val geofences = geofenceRepository.getAllActiveGeofences()

                geofenceManager.registerGeofences(geofences)

            } catch (e: Exception) {
                // Best-effort; re-registration will also happen the next
                // time the app itself is opened.
            } finally {
                pendingResult.finish()
            }
        }
    }
}
