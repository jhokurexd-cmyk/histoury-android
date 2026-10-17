package com.histoury.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import com.google.android.gms.maps.model.LatLng
import com.histoury.app.data.repository.TravelMode
import com.histoury.app.data.viewmodel.MapUiState
import com.histoury.app.map.MapConfig
import com.histoury.app.theme.Primary
import com.histoury.app.theme.Success
import com.histoury.app.theme.TextTertiary
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.exp

/** How long the arrow (and the camera with it) glides from one GPS fix to the next. Matches the Google map. */
private const val GLIDE_MS = 1200

/** Per-frame easing factor for a time constant of [tauSeconds]. */
private fun ease(dtSeconds: Float, tauSeconds: Float): Float = 1f - exp(-dtSeconds / tauSeconds)

/**
 * Live turn-by-turn map on MapLibre, behaving like the Google version:
 * heading-up, tilted, the visitor's arrow gliding between fixes, the route
 * still ahead drawn bold over the faint full route, "follow" until the
 * visitor pans the map, and "show whole route" on demand.
 *
 * One frame loop moves both the arrow and (while following) the camera, so
 * the arrow stays pinned in place on screen instead of jittering against
 * the map.
 */
@Composable
internal fun NavigationMapLibre(
    uiState: MapUiState,
    isInPip: Boolean,
    isFollowing: Boolean,
    isOverview: Boolean,
    onUserGesture: () -> Unit,
    modifier: Modifier = Modifier,
    // Keep north at the top instead of turning the map with the visitor.
    northUp: Boolean = false,
    // The map's current rotation (degrees), for the screen's compass button.
    onMapBearing: (Float) -> Unit = {}
) {
    val state = rememberHistouryMapState()
    val northUpState = rememberUpdatedState(northUp)
    val bearingCallback = rememberUpdatedState(onMapBearing)
    val reportedBearing = remember { floatArrayOf(Float.NaN) }
    val density = LocalDensity.current.density

    val primary = Primary.toArgb()
    val faint = TextTertiary.copy(alpha = 0.55f).toArgb()
    val green = Success.toArgb()

    // ---- what to draw (everything but the arrow) ------------------------
    val ahead = uiState.remainingRoutePoints.takeIf { it.size >= 2 } ?: uiState.routePoints
    val scene = remember(uiState.routePoints, ahead, uiState.finalWalkPoints, uiState.isRouteEstimated, uiState.geofence, uiState.site,
        uiState.routeTargetLatitude, uiState.routeTargetLongitude, uiState.entranceName, primary, faint, green) {
        val lines = mutableListOf<MapLine>()
        // Never a straight line: with no real route only the pins show.
        if (!uiState.isRouteEstimated) {
            if (uiState.routePoints.size >= 2) {
                lines += MapLine("route-full", uiState.routePoints, faint, widthDp = 4.5f, zIndex = 1f)
            }
            if (ahead.size >= 2) {
                lines += MapLine(
                    "route-ahead", ahead, primary, widthDp = 6.5f,
                    casingColor = android.graphics.Color.WHITE, casingWidthDp = 10f, zIndex = 2f
                )
            }
            // Car / motorcycle: the walk from the drop-off to the entrance,
            // dotted so it reads as "on foot" after the drive line ends.
            if (uiState.finalWalkPoints.size >= 2) {
                lines += MapLine(
                    "final-walk", uiState.finalWalkPoints, primary, widthDp = 5f,
                    pattern = LinePattern.DOTTED, zIndex = 2f
                )
            }
        }
        val markers = mutableListOf<MapMarker>()
        uiState.geofence?.let { destination ->
            markers += MapMarker(
                "destination", LatLng(destination.latitude, destination.longitude), MarkerIcon.Pin(primary),
                title = uiState.site?.siteName ?: "Destination", zIndex = 4f
            )
        }
        val entranceLat = uiState.routeTargetLatitude
        val entranceLng = uiState.routeTargetLongitude
        if (entranceLat != null && entranceLng != null) {
            markers += MapMarker(
                "entrance", LatLng(entranceLat, entranceLng), MarkerIcon.Pin(green),
                title = uiState.entranceName ?: "Visitor entrance", subtitle = "Verified visitor entrance", zIndex = 5f
            )
        }
        MapScene(lines = lines, markers = markers)
    }

    // ---- live inputs read by the frame loop -----------------------------
    val userLatLng = remember(uiState.userLatitude, uiState.userLongitude) {
        val lat = uiState.userLatitude
        val lng = uiState.userLongitude
        if (lat != null && lng != null) LatLng(lat, lng) else null
    }
    val targetState = rememberUpdatedState(userLatLng)
    val bearingState = rememberUpdatedState(uiState.userBearing)
    val followingState = rememberUpdatedState(isFollowing || isInPip)
    val pipState = rememberUpdatedState(isInPip)
    // Closer in for walking, further out at vehicle speeds; flatter and
    // further out in the small floating window. (Google zoom levels.)
    val zoomState = rememberUpdatedState(
        googleZoomToMapLibre(
            when {
                isInPip -> 16.25f
                uiState.travelMode == TravelMode.WALKING -> 18.5f
                uiState.travelMode == TravelMode.MOTORCYCLE -> 17.5f
                else -> 17f
            }
        )
    )
    val tiltState = rememberUpdatedState(if (isInPip) 30.0 else 55.0)
    val gestureCallback = rememberUpdatedState(onUserGesture)

    // A finger on the map stops follow mode (not possible in the floating window).
    LaunchedEffect(state) {
        state.userGestures.collect { if (!pipState.value) gestureCallback.value() }
    }

    // The floating window is too small to pan; taps there expand it.
    LaunchedEffect(state.map, isInPip) {
        state.map?.uiSettings?.setAllGesturesEnabled(!isInPip)
    }

    // "Show whole route": everything still ahead, plus the visitor.
    LaunchedEffect(isOverview) {
        if (!isOverview) return@LaunchedEffect
        val points = (uiState.remainingRoutePoints.ifEmpty { uiState.routePoints }.ifEmpty {
            listOfNotNull(uiState.geofence?.let { LatLng(it.latitude, it.longitude) })
        }) + listOfNotNull(userLatLng)
        if (points.isEmpty()) return@LaunchedEffect
        state.resetPadding()
        state.fitBounds(points, paddingDp = 48, durationMs = 900, extraTopDp = 230, extraBottomDp = 90)
    }

    // ---- the frame loop: arrow glide + follow camera --------------------
    val styleLoaded = state.isStyleLoaded
    LaunchedEffect(styleLoaded) {
        if (!styleLoaded) return@LaunchedEffect

        var puckShown: LatLng? = null
        var glideFrom: LatLng? = null
        var glideTo: LatLng? = null
        var glideStartNanos = 0L
        var lastPuckBearing = Float.NaN
        var lastPuckDrawn: LatLng? = null

        val start = state.currentCamera()
        var bearing = start?.bearing?.toFloat() ?: 0f
        var zoom = start?.zoom ?: zoomState.value
        var tilt = start?.tilt ?: 0.0
        var wasFollowing = false
        var cameraFrom: LatLng? = null
        var cameraGlideStart = 0L
        var lastFrameNanos = 0L

        // Tells the compass button how far the map is turned (only when it
        // has moved noticeably, so the button isn't redrawn every frame).
        fun reportBearing(value: Float) {
            val last = reportedBearing[0]
            if (last.isNaN() || abs(((value - last + 540f) % 360f) - 180f) > 0.5f) {
                reportedBearing[0] = value
                bearingCallback.value(value)
            }
        }

        while (isActive) {
            withFrameNanos { now ->

                val dt = if (lastFrameNanos == 0L) 0.016f
                    else ((now - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
                lastFrameNanos = now

                val target = targetState.value
                if (target == null) {
                    if (lastPuckDrawn != null) {
                        state.setPuck(null, 0f, primary)
                        lastPuckDrawn = null
                    }
                    return@withFrameNanos
                }

                // A new fix: glide the arrow from wherever it is now.
                if (puckShown == null) {
                    puckShown = target; glideFrom = target; glideTo = target; glideStartNanos = 0L
                } else if (target != glideTo) {
                    glideFrom = puckShown; glideTo = target; glideStartNanos = now
                }
                val from = glideFrom!!
                val to = glideTo!!
                val t = if (glideStartNanos == 0L) 1f
                    else ((now - glideStartNanos) / 1_000_000f / GLIDE_MS).coerceIn(0f, 1f)
                val shown = LatLng(
                    from.latitude + (to.latitude - from.latitude) * t,
                    from.longitude + (to.longitude - from.longitude) * t
                )
                puckShown = shown

                val heading = bearingState.value ?: (if (lastPuckBearing.isNaN()) 0f else lastPuckBearing)
                if (shown != lastPuckDrawn || heading != lastPuckBearing) {
                    state.setPuck(shown, heading, primary)
                    lastPuckDrawn = shown
                    lastPuckBearing = heading
                }

                val following = followingState.value
                if (!following || state.isGestureInProgress) {
                    // Keep the eased values in step with whatever the visitor
                    // did, so returning to follow mode glides from there.
                    state.currentCamera()?.let { bearing = it.bearing.toFloat(); zoom = it.zoom; tilt = it.tilt }
                    reportBearing(bearing)
                    wasFollowing = false
                    return@withFrameNanos
                }

                // Re-center: glide the camera from where the map was left.
                if (!wasFollowing) {
                    cameraFrom = state.currentCamera()?.target
                    cameraGlideStart = now
                    wasFollowing = true
                }
                val cameraT = ((now - cameraGlideStart) / 1_000_000f / GLIDE_MS).coerceIn(0f, 1f)
                val cf = cameraFrom
                val cameraTarget = if (cf != null && cameraT < 1f) {
                    LatLng(
                        cf.latitude + (shown.latitude - cf.latitude) * cameraT,
                        cf.longitude + (shown.longitude - cf.longitude) * cameraT
                    )
                } else {
                    shown
                }

                // Unknown heading (first fix): keep what the camera shows.
                // North-up: always 0, whichever way the visitor faces.
                val targetBearing = if (northUpState.value) 0f else (bearingState.value ?: bearing)
                val bearingDelta = ((targetBearing - bearing + 540f) % 360f) - 180f
                val nextBearing = (bearing + bearingDelta * ease(dt, 0.30f) + 360f) % 360f
                val nextZoom = zoom + (zoomState.value - zoom) * ease(dt, 0.35f)
                val nextTilt = tilt + (tiltState.value - tilt) * ease(dt, 0.35f)

                val changed = abs(nextBearing - bearing) > 0.02f ||
                    abs(nextZoom - zoom) > 0.0005 ||
                    abs(nextTilt - tilt) > 0.02 ||
                    t < 1f || cameraT < 1f

                bearing = nextBearing
                reportBearing(bearing)
                zoom = nextZoom
                tilt = nextTilt

                if (changed) {
                    // Pushes the followed position into the lower part of the
                    // screen, under the banner, so most of the map shows the
                    // road ahead.
                    val padding = if (pipState.value) null else doubleArrayOf(
                        0.0, (300 * density).toDouble(), 0.0, (110 * density).toDouble()
                    )
                    state.moveFrame(MapCamera(cameraTarget, nextZoom, nextBearing.toDouble(), nextTilt), padding)
                }
            }
        }
    }

    val initial = userLatLng
        ?: uiState.geofence?.let { LatLng(it.latitude, it.longitude) }
        ?: MapConfig.INTRAMUROS_CENTER

    HistouryMapLibre(
        state = state,
        scene = scene,
        modifier = modifier,
        initialTarget = initial,
        initialGoogleZoom = 17f,
        // The arrow replaces the blue dot while navigating.
        showUserLocation = false,
        showMyLocationButton = false,
        // The screen has its own compass button beside the other controls;
        // MapLibre's floated alone at the top corner, next to the banner.
        showCompass = false,
        interactive = true,
        uiInsets = if (isInPip) MapInsets() else MapInsets(top = 0f, bottom = 96f)
    )
}
