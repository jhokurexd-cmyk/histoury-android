package com.histoury.app.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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

                // Retries once online if the fences can't be loaded yet.
                GeofenceSync.resyncOrScheduleRetry(context)

            } catch (e: Exception) {
                // Best-effort; re-registration will also happen the next
                // time the app itself is opened.
            } finally {
                pendingResult.finish()
            }
        }
    }
}