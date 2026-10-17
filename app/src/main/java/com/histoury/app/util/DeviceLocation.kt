package com.histoury.app.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Whether the phone's location service (the Location toggle in quick
 * settings) is on. This is separate from the app's location PERMISSION:
 * a visitor can allow Histoury location access and still have the whole
 * service switched off, and then no fix ever arrives: no route, no distance,
 * no ETA, no arrival detection.
 */
object DeviceLocation {

    /**
     * Home offers the "turn on location" pop-up at most once per app launch;
     * any more and it nags on every trip back to Home.
     */
    @Volatile
    var homePromptShown: Boolean = false

    fun isEnabled(context: Context): Boolean {
        val manager = context.getSystemService(LocationManager::class.java) ?: return false
        return LocationManagerCompat.isLocationEnabled(manager)
    }

    /**
     * The current state first, then every change: the visitor flipping the
     * toggle in quick settings, or turning it on from the prompt.
     */
    fun observe(context: Context): Flow<Boolean> = callbackFlow {
        val appContext = context.applicationContext

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(isEnabled(appContext))
            }
        }

        val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION).apply {
            addAction(LocationManager.MODE_CHANGED_ACTION)
        }

        trySend(isEnabled(appContext))

        // System broadcasts still arrive with NOT_EXPORTED; it only shuts
        // out other apps.
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        awaitClose { runCatching { appContext.unregisterReceiver(receiver) } }
    }.distinctUntilChanged()
}
