package com.histoury.app.data.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.histoury.app.data.repository.GeofenceRepository
import com.histoury.app.location.GeofenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GeofenceSetupViewModel(application: Application) : AndroidViewModel(application) {

    private val geofenceRepository = GeofenceRepository()

    private val geofenceManager = GeofenceManager(application)

    private val _isRegistered = MutableStateFlow(false)
    val isRegistered: StateFlow<Boolean> = _isRegistered.asStateFlow()

    private var lastRegisteredAtMs = 0L

    private var registrationInFlight = false

    /**
     * Re-fetches every active geofence from Firestore and re-registers them
     * with Play Services.
     *
     * This used to be a run-once-per-process latch, which meant an admin
     * editing a geofence's radius or location had no way to reach a phone
     * that already had the Histoury app open — [GeofenceManager]'s Play
     * Services registration was set up exactly once on the first Home
     * launch and never touched again for the rest of that app session.
     *
     * Calling this again is safe and cheap: [GeofenceManager.registerGeofences]
     * replaces any existing fence sharing the same requestId (siteId)
     * outright, so re-running it is exactly how an admin's edit reaches the
     * device. The throttle below exists only so that every Home resume
     * doesn't hammer Firestore and Play Services back-to-back — it is not a
     * "run once ever" guard.
     */
    fun registerAllSiteGeofences() {

        val now = System.currentTimeMillis()

        if (registrationInFlight || now - lastRegisteredAtMs < REFRESH_INTERVAL_MS) {
            return
        }

        registrationInFlight = true

        viewModelScope.launch {

            try {

                val geofences = geofenceRepository.getAllActiveGeofences()

                geofenceManager.registerGeofences(geofences)

                _isRegistered.value = true
                lastRegisteredAtMs = System.currentTimeMillis()

            } catch (e: Exception) {
                // Registration failed (permission revoked mid-flight, Play
                // Services unavailable, etc). lastRegisteredAtMs is left
                // untouched so the next call (e.g. the next Home resume)
                // retries rather than waiting out the full throttle window.
            } finally {
                registrationInFlight = false
            }
        }
    }

    companion object {
        // How often a foreground resume is allowed to actually hit Firestore
        // and Play Services again. Short enough that an admin's edit shows
        // up the next time a visitor switches back to the app; long enough
        // that bouncing between Home and another screen repeatedly doesn't
        // spam re-registration.
        private const val REFRESH_INTERVAL_MS = 30_000L
    }
}
