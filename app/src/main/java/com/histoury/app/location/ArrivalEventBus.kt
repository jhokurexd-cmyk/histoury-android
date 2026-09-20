package com.histoury.app.location

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Hands geofence arrivals from [GeofenceBroadcastReceiver] to the running
 * UI, so entering a site's radius opens the Arrival screen in-app — not
 * just the notification.
 *
 * Works because manifest-registered receivers run in the app's own process:
 * when the app is open, MainActivity is collecting and navigates; when the
 * process was woken only for the broadcast, there's no collector and the
 * notification remains the (only) entry point, exactly as before.
 */
object ArrivalEventBus {

    // tryEmit-friendly: buffered so the receiver never needs a coroutine,
    // and events are dropped harmlessly when nobody is collecting.
    private val _arrivals = MutableSharedFlow<String>(extraBufferCapacity = 4)

    val arrivals: SharedFlow<String> = _arrivals.asSharedFlow()

    fun emit(siteId: String) {
        _arrivals.tryEmit(siteId)
    }
}
