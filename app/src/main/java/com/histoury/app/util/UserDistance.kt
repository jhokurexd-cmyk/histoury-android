package com.histoury.app.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * The visitor's approximate position, for "how far is this?" labels on
 * site and place cards and for Home's "Nearest To You".
 *
 * One shared value so Home and the "View all" lists agree. It's only ever
 * read from the phone (last known fix, or one quick balanced-power fix
 * when there's none) — no tracking, nothing stored or uploaded — and stays
 * null without location permission, in which case cards simply show no
 * distance.
 */
object UserDistance {

    private val _position = MutableStateFlow<LatLng?>(null)
    val position: StateFlow<LatLng?> = _position.asStateFlow()

    private fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Updates [position] from the phone. Safe to call often (e.g. on every
     * resume).
     *
     * By default it takes the phone's last known fix, which is instant but
     * can be old: after the visitor has moved, it still says where they
     * were. [fresh] (pull-to-refresh) asks for a new fix instead, and only
     * falls back to the last known one if none arrives in time.
     */
    @SuppressLint("MissingPermission")
    suspend fun refresh(context: Context, fresh: Boolean = false) {
        if (!hasPermission(context)) return
        val client = LocationServices.getFusedLocationProviderClient(context)

        suspend fun currentFix(priority: Int): Location? = withTimeoutOrNull(10_000) {
            runCatching { client.getCurrentLocation(priority, null).await() }.getOrNull()
        }

        val location = if (fresh) {
            currentFix(Priority.PRIORITY_HIGH_ACCURACY)
                ?: runCatching { client.lastLocation.await() }.getOrNull()
        } else {
            runCatching { client.lastLocation.await() }.getOrNull()
                ?: currentFix(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
        } ?: return

        _position.value = LatLng(location.latitude, location.longitude)
    }

    /** Straight-line metres from the visitor to a point, or null when either is unknown. */
    fun metersTo(latitude: Double?, longitude: Double?, from: LatLng? = _position.value): Float? {
        if (from == null || latitude == null || longitude == null) return null
        if (latitude == 0.0 && longitude == 0.0) return null
        val result = FloatArray(1)
        Location.distanceBetween(from.latitude, from.longitude, latitude, longitude, result)
        return result[0]
    }

    /** "350 m", "1.2 km", "15 km" — short enough for a card. */
    fun label(meters: Float): String = when {
        meters < 50f -> "Nearby"
        meters < 1_000f -> "${(meters / 10f).toInt() * 10} m"
        meters < 10_000f -> String.format(Locale.US, "%.1f km", meters / 1_000f)
        else -> "${(meters / 1_000f).toInt()} km"
    }

    /** The card label for a point, or null when there's no distance to show. */
    fun labelFor(latitude: Double?, longitude: Double?, from: LatLng? = _position.value): String? =
        metersTo(latitude, longitude, from)?.let(::label)
}
