package com.histoury.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.histoury.app.data.model.HistoricalSite
import com.histoury.app.data.repository.GeofenceRepository
import com.histoury.app.location.SiteProximity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Used when a site has no geofence of its own. */
private const val DEFAULT_SITE_RADIUS_METERS = 100.0

/** Leaving by this much beyond the radius counts as having left (no flicker at the edge). */
private const val EXIT_MARGIN_METERS = 30f

/**
 * Whether the visitor is standing at [site] right now, kept up to date from
 * GPS while the screen is visible (and only then).
 *
 * Lets Site Details offer AR whenever you're at the site, however you got
 * to the page — Menu > Site Information, Favorites, the Map — not only
 * straight after the Arrival screen. Uses the site's geofence radius and
 * the same "inside" rule as arrival detection and the AR screen itself.
 * Without location permission it simply stays false.
 */
@Composable
fun rememberIsAtSite(site: HistoricalSite?, enabled: Boolean = true): State<Boolean> {

    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val atSite = remember(site?.documentId) { mutableStateOf(false) }

    LaunchedEffect(site?.documentId, enabled, lifecycleOwner) {

        if (!enabled || site == null) return@LaunchedEffect

        val repository = GeofenceRepository()
        val fence = withContext(Dispatchers.IO) {
            runCatching {
                repository.getGeofenceForSite(site.siteId)
                    ?: repository.getGeofenceForSite(site.documentId)
            }.getOrNull()
        }

        // The site's own coordinates first: the same point arrival detection
        // and the AR screen check against.
        val latitude = site.latitude ?: fence?.latitude ?: return@LaunchedEffect
        val longitude = site.longitude ?: fence?.longitude ?: return@LaunchedEffect
        val radius = fence?.radius?.takeIf { it > 0.0 } ?: DEFAULT_SITE_RADIUS_METERS
        val exitDistance = radius.toFloat().coerceAtLeast(SiteProximity.MIN_RADIUS_METERS) + EXIT_MARGIN_METERS

        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                if (SiteProximity.hasPermission(context)) {
                    SiteProximity.updates(context, intervalMs = 5_000L).collect { location ->
                        if (!SiteProximity.isTrustworthy(location)) return@collect
                        // Skip Android's cached last fix if it's old.
                        val ageMs = android.os.SystemClock.elapsedRealtime() - location.elapsedRealtimeNanos / 1_000_000L
                        if (ageMs > 2 * 60_000L) return@collect
                        if (SiteProximity.isInside(location, latitude, longitude, radius)) {
                            atSite.value = true
                        } else if (SiteProximity.distanceMeters(location, latitude, longitude) > exitDistance) {
                            atSite.value = false
                        }
                    }
                }
                // No permission yet (or it was revoked): look again shortly.
                delay(5_000L)
            }
        }
    }

    return atSite
}
