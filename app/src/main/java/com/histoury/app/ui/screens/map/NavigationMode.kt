package com.histoury.app.ui.screens.map

import com.histoury.app.theme.SheetSurface
import com.histoury.app.theme.overlayShadow
import com.histoury.app.theme.styledElevation
import com.histoury.app.ui.tutorial.tutorialTarget
import kotlin.math.roundToInt
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.ForkLeft
import androidx.compose.material.icons.filled.ForkRight
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.RampLeft
import androidx.compose.material.icons.filled.RampRight
import androidx.compose.material.icons.filled.RoundaboutLeft
import androidx.compose.material.icons.filled.RoundaboutRight
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSharpLeft
import androidx.compose.material.icons.filled.TurnSharpRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material.icons.filled.UTurnRight
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Dot
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.histoury.app.data.itinerary.ActiveItineraryTrip
import com.histoury.app.data.repository.TravelMode
import com.histoury.app.data.viewmodel.MapUiState
import com.histoury.app.data.viewmodel.NavInstruction
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Danger
import com.histoury.app.theme.Primary
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary
import com.histoury.app.theme.TextTertiary
import com.histoury.app.ui.components.rememberThemedMapColorScheme
import com.histoury.app.ui.components.rememberThemedMapProperties
import com.histoury.app.map.MapConfig
import com.histoury.app.ui.map.NavigationMapLibre
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** How long the position arrow glides between GPS fixes — matches the camera follow. */
internal const val NAV_GLIDE_MS = 1200

/**
 * Full-screen, map-first turn-by-turn navigation, Waze style:
 *
 *  - a big instruction banner up top (next turn, distance to it, and the
 *    turn after that),
 *  - the map filling everything else, tilted and heading-up, with a
 *    rotating arrow for the visitor and the route shrinking as it's walked,
 *  - a slim bar at the bottom with arrival time, time left and distance.
 *
 * In picture-in-picture ([isInPip]) the same map shrinks to a floating
 * window, so everything but the map and a compact next-turn chip is hidden.
 */
@Composable
internal fun NavigationModeContent(
    uiState: MapUiState,
    cameraPositionState: CameraPositionState,
    isInPip: Boolean,
    isFollowing: Boolean,
    isOverview: Boolean,
    tripProgress: ActiveItineraryTrip.Progress?,
    canUsePip: Boolean,
    onRecenter: () -> Unit,
    onToggleOverview: () -> Unit,
    onToggleMute: () -> Unit,
    onEnterPip: () -> Unit,
    onEnd: () -> Unit,
    onSkipStop: () -> Unit,
    /** The visitor panned / zoomed the map themselves (MapLibre map only; Google reports it through the camera state). */
    onUserGesture: () -> Unit = {}
) {

    // Compass: how far the map is turned, and whether it's held north-up.
    val mapBearing = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var northUp by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

    // Commute: the walk / ride steps for this leg of the trip, on demand.
    var showCommuteSteps by remember { mutableStateOf(false) }
    if (showCommuteSteps && !isInPip && uiState.commuteSteps.isNotEmpty()) {
        CommuteStepsNavSheet(
            uiState = uiState,
            onDismiss = { showCommuteSteps = false }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {

        if (MapConfig.useMapLibre) {
            NavigationMapLibre(
                uiState = uiState,
                isInPip = isInPip,
                isFollowing = isFollowing,
                isOverview = isOverview,
                onUserGesture = onUserGesture,
                northUp = northUp,
                onMapBearing = { mapBearing.floatValue = it },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            NavigationMap(
                uiState = uiState,
                cameraPositionState = cameraPositionState,
                isInPip = isInPip
            )
        }

        if (isInPip) {

            // Floating window: map plus the one thing worth reading at a glance.
            uiState.nextInstruction?.let { instruction ->
                PipManeuverChip(
                    instruction = instruction,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(6.dp)
                )
            }

        } else {

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {

            uiState.nextInstruction?.let { instruction ->
                Box(Modifier.tutorialTarget("nav_banner")) {
                    ManeuverBanner(instruction = instruction)
                }
            }

            AnimatedVisibility(
                visible = uiState.isRerouting,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                StatusPill(
                    text = "Rerouting…",
                    showSpinner = true,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (uiState.isRouteEstimated && !uiState.isRerouting) {
                // No line is drawn when there's no real route; this says why,
                // and a tap hands the trip to Google Maps.
                val context = androidx.compose.ui.platform.LocalContext.current
                StatusPill(
                    text = "No route found \u00b7 Tap to open Google Maps",
                    showSpinner = false,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable { openInGoogleMaps(context, uiState) }
                )
            }
        }

        // Right-hand controls, Waze-style round buttons.
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // With the other map controls instead of floating on its own at
            // the top corner. Tap: north-up <-> turn with me.
            if (MapConfig.useMapLibre) {
                CompassButton(
                    bearing = { mapBearing.floatValue },
                    northUp = northUp,
                    onClick = { northUp = !northUp }
                )
            }
            Box(Modifier.tutorialTarget("nav_mute")) {
                RoundMapButton(
                    icon = if (uiState.isVoiceMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = if (uiState.isVoiceMuted) "Unmute voice guidance" else "Mute voice guidance",
                    tint = if (uiState.isVoiceMuted) Danger else Primary,
                    onClick = onToggleMute
                )
            }
            Box(Modifier.tutorialTarget("nav_overview")) {
                RoundMapButton(
                    icon = Icons.Default.Route,
                    contentDescription = if (isOverview) "Back to navigation view" else "Show whole route",
                    tint = if (isOverview) CardSurface else Primary,
                    background = if (isOverview) Primary else CardSurface,
                    onClick = onToggleOverview
                )
            }
            if (canUsePip) {
                Box(Modifier.tutorialTarget("nav_pip")) {
                    RoundMapButton(
                        icon = Icons.Default.PictureInPictureAlt,
                        contentDescription = "Float mini map",
                        tint = Primary,
                        onClick = onEnterPip
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {

            // OpenStreetMap-based routes must carry their credit line.
            uiState.routeAttribution?.let { credit ->
                Text(
                    text = credit,
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .background(
                            androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f),
                            androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom
            ) {

                // Current speed, in every travel mode — walking included —
                // for normal and itinerary navigation alike (both run here).
                // Sits bottom-left, clear of the route, the instruction
                // banner and the Recenter button.
                val speed = uiState.speedKmh
                if (speed != null) {
                    SpeedBubble(
                        speedKmh = speed,
                        showDecimal = uiState.travelMode == TravelMode.WALKING
                    )
                    Spacer(Modifier.width(10.dp))
                }

                Spacer(Modifier.weight(1f))

                // Appears once the visitor pans the map away from their
                // position, exactly like Waze's "Recenter".
                AnimatedVisibility(
                    visible = !isFollowing,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .overlayShadow(6.dp, RoundedCornerShape(50))
                            .clip(RoundedCornerShape(50))
                            .background(CardSurface)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onRecenter
                            )
                            .padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Re-center", color = Primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }

            TripStatusBar(
                uiState = uiState,
                tripProgress = tripProgress,
                onEnd = onEnd,
                onSkipStop = onSkipStop,
                onShowCommuteSteps = { showCommuteSteps = true }
            )
        }
        }
    }
}

@Composable
private fun NavigationMap(
    uiState: MapUiState,
    cameraPositionState: CameraPositionState,
    isInPip: Boolean
) {

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        // The arrow marker below replaces the blue dot while navigating.
        properties = rememberThemedMapProperties(isMyLocationEnabled = false),
        mapColorScheme = rememberThemedMapColorScheme(),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            compassEnabled = false,
            mapToolbarEnabled = false,
            // A floating window is too small to pan; taps there should
            // expand it, which Android handles itself.
            scrollGesturesEnabled = !isInPip,
            zoomGesturesEnabled = !isInPip,
            rotationGesturesEnabled = !isInPip,
            tiltGesturesEnabled = !isInPip
        ),
        // Pushes the followed position into the lower part of the screen,
        // under the banner, so most of the map shows the road AHEAD.
        contentPadding = if (isInPip) {
            PaddingValues(0.dp)
        } else {
            PaddingValues(top = 300.dp, bottom = 110.dp)
        }
    ) {

        // The full route, faint — shows the part already walked.
        if (uiState.routePoints.size >= 2 && !uiState.isRouteEstimated) {
            Polyline(
                points = uiState.routePoints,
                color = TextTertiary.copy(alpha = 0.55f),
                width = 12f,
                jointType = JointType.ROUND,
                zIndex = 1f
            )
        }

        val ahead = uiState.remainingRoutePoints.takeIf { it.size >= 2 } ?: uiState.routePoints

        // Never a straight line: with no real route there are no points,
        // and only the destination marker shows.
        if (ahead.size >= 2 && !uiState.isRouteEstimated) {

            run {
                // White casing under the coral line lifts it off the map.
                Polyline(
                    points = ahead,
                    color = Color.White,
                    width = 28f,
                    jointType = JointType.ROUND,
                    startCap = RoundCap(),
                    endCap = RoundCap(),
                    zIndex = 2f
                )
                Polyline(
                    points = ahead,
                    color = Primary,
                    width = 18f,
                    jointType = JointType.ROUND,
                    startCap = RoundCap(),
                    endCap = RoundCap(),
                    zIndex = 3f
                )
            }
        }

        // Car / motorcycle: the walk from the drop-off to the entrance,
        // dotted so it reads as "on foot" after the drive line ends.
        if (uiState.finalWalkPoints.size >= 2 && !uiState.isRouteEstimated) {
            Polyline(
                points = uiState.finalWalkPoints,
                color = Primary,
                width = 12f,
                pattern = listOf(Dot(), Gap(16f)),
                zIndex = 3f
            )
        }

        uiState.geofence?.let { destination ->
            val destinationState = remember(destination.latitude, destination.longitude) {
                MarkerState(position = LatLng(destination.latitude, destination.longitude))
            }
            Marker(
                state = destinationState,
                title = uiState.site?.siteName ?: "Destination",
                zIndex = 4f
            )
        }

        // Where the route actually ends: the verified visitor entrance.
        val entranceLat = uiState.routeTargetLatitude
        val entranceLng = uiState.routeTargetLongitude
        if (entranceLat != null && entranceLng != null) {
            val entranceState = remember(entranceLat, entranceLng) {
                MarkerState(position = LatLng(entranceLat, entranceLng))
            }
            Marker(
                state = entranceState,
                title = uiState.entranceName ?: "Visitor entrance",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN),
                zIndex = 4f
            )
        }

        val lat = uiState.userLatitude
        val lng = uiState.userLongitude

        if (lat != null && lng != null) {
            NavigationArrow(
                target = LatLng(lat, lng),
                bearing = uiState.userBearing ?: 0f
            )
        }
    }
}

/**
 * The visitor's position as a heading arrow that glides between GPS fixes
 * rather than jumping, in step with the camera's own glide.
 */
@Composable
private fun NavigationArrow(target: LatLng, bearing: Float) {

    val arrowColor = Primary.toArgb()
    val arrowIcon = remember(arrowColor) { navigationArrowBitmap(arrowColor) }

    val markerState = remember { MarkerState(position = target) }

    var lastShown by remember { mutableStateOf(target) }

    LaunchedEffect(target) {
        val from = lastShown
        val progress = Animatable(0f)
        progress.animateTo(1f, tween(NAV_GLIDE_MS, easing = LinearEasing)) {
            val position = LatLng(
                from.latitude + (target.latitude - from.latitude) * value,
                from.longitude + (target.longitude - from.longitude) * value
            )
            markerState.position = position
            lastShown = position
        }
    }

    Marker(
        state = markerState,
        icon = arrowIcon,
        anchor = Offset(0.5f, 0.5f),
        // Flat + rotation = the arrow lies on the map and points along the
        // direction of travel, turning with the map as it rotates.
        flat = true,
        rotation = bearing,
        zIndex = 10f
    )
}

private fun navigationArrowBitmap(fillColor: Int): BitmapDescriptor {

    val size = 96
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val center = size / 2f

    // Soft halo so the arrow stays visible over busy map areas.
    paint.color = android.graphics.Color.argb(60, 0, 0, 0)
    canvas.drawCircle(center, center + 2f, 40f, paint)

    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(center, center, 38f, paint)

    paint.color = fillColor
    canvas.drawCircle(center, center, 32f, paint)

    // Chevron pointing "up" — rotated by the marker to the heading.
    val arrow = Path().apply {
        moveTo(center, center - 20f)
        lineTo(center + 15f, center + 16f)
        lineTo(center, center + 8f)
        lineTo(center - 15f, center + 16f)
        close()
    }
    paint.color = android.graphics.Color.WHITE
    canvas.drawPath(arrow, paint)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

// ---------------------------------------------------------------- banner

@Composable
private fun ManeuverBanner(instruction: NavInstruction) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .overlayShadow(10.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(Primary)
    ) {

        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Icon(
                imageVector = maneuverIcon(instruction.maneuver),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(52.dp)
            )

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {

                instruction.distanceMeters?.let { meters ->
                    Text(
                        text = formatNavDistance(meters),
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 32.sp
                    )
                }

                Text(
                    text = instruction.text,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        instruction.thenManeuver?.let { then ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.18f))
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Then", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = maneuverIcon(then),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun PipManeuverChip(instruction: NavInstruction, modifier: Modifier = Modifier) {

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Primary)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = maneuverIcon(instruction.maneuver),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
        instruction.distanceMeters?.let { meters ->
            Spacer(Modifier.width(5.dp))
            Text(
                text = formatNavDistance(meters),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun StatusPill(text: String, showSpinner: Boolean, modifier: Modifier = Modifier) {

    Row(
        modifier = modifier
            .overlayShadow(4.dp, RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .background(CardSurface)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showSpinner) {
            CircularProgressIndicator(
                color = Primary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(text = text, color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------- controls

/**
 * A compass needle that turns with the map (red end = north). Tapping it
 * switches between keeping north at the top and turning the map with the
 * visitor; it's filled coral while north-up is on.
 */
@Composable
private fun CompassButton(
    bearing: () -> Float,
    northUp: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(50.dp)
            .overlayShadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(if (northUp) com.histoury.app.theme.PrimarySoft else CardSurface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = if (northUp) "Turn the map with me" else "Keep north at the top",
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        val north = Primary
        val south = TextSecondary.copy(alpha = 0.55f)
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .size(26.dp)
                // Read inside the layer, so a turning map only re-draws this.
                .graphicsLayer { rotationZ = -bearing() }
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val half = size.width * 0.18f
            val northPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(cx, 0f); lineTo(cx + half, cy); lineTo(cx - half, cy); close()
            }
            val southPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(cx, size.height); lineTo(cx + half, cy); lineTo(cx - half, cy); close()
            }
            drawPath(northPath, north)
            drawPath(southPath, south)
        }
    }
}

@Composable
private fun RoundMapButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    background: Color = CardSurface
) {
    Box(
        modifier = Modifier
            .size(50.dp)
            .overlayShadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun SpeedBubble(speedKmh: Double, showDecimal: Boolean) {
    // Walking speeds (≈3–6 km/h) need the decimal to show any change at
    // all; vehicle speeds read better as whole numbers.
    val value = if (showDecimal) {
        String.format(java.util.Locale.US, "%.1f", speedKmh)
    } else {
        speedKmh.roundToInt().toString()
    }
    Column(
        modifier = Modifier
            .size(62.dp)
            .overlayShadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(CardSurface)
            .border(3.dp, TextPrimary.copy(alpha = 0.12f), CircleShape),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            value,
            color = TextPrimary,
            fontSize = if (value.length > 3) 17.sp else 20.sp,
            fontWeight = FontWeight.ExtraBold,
            lineHeight = 21.sp
        )
        Text("km/h", color = TextSecondary, fontSize = 10.sp, lineHeight = 11.sp)
    }
}

@Composable
private fun TripStatusBar(
    uiState: MapUiState,
    tripProgress: ActiveItineraryTrip.Progress?,
    onEnd: () -> Unit,
    onSkipStop: () -> Unit,
    onShowCommuteSteps: () -> Unit = {}
) {

    Surface(
        color = CardSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        shadowElevation = styledElevation(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {

        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)
        ) {

            if (tripProgress != null) {
                Text(
                    text = "Stop ${tripProgress.currentNumber} of ${tripProgress.totalStops} · ${tripProgress.itineraryName}",
                    color = Primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {

                Column(modifier = Modifier.weight(1f).tutorialTarget("nav_status")) {

                    Text(
                        text = uiState.etaMinutes?.let { formatMinutes(it) } ?: "--",
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 26.sp
                    )

                    Text(
                        text = listOfNotNull(
                            uiState.etaMinutes?.let { "Arrive ${arrivalClock(it)}" },
                            uiState.distanceMeters?.let { formatNavDistance(it) }
                        ).joinToString(" · "),
                        color = TextSecondary,
                        fontSize = 13.sp
                    )

                    Text(
                        text = uiState.site?.siteName ?: "Destination",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {

                    Box(
                        modifier = Modifier
                            .tutorialTarget("nav_end")
                            .clip(RoundedCornerShape(50))
                            .background(Danger)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onEnd
                            )
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text("End", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    if (tripProgress != null) {
                        Text(
                            text = "Skip stop",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onSkipStop
                                )
                                .padding(4.dp)
                        )
                    }
                }
            }

            // Commute: which rides this leg uses; tap for the full steps
            // (where to walk, which jeepney / bus / train, where to get off).
            if (uiState.travelMode == TravelMode.COMMUTE && uiState.commuteSteps.isNotEmpty()) {
                val rides = com.histoury.app.ui.components.commuteRideSummary(uiState.commuteSteps)
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(com.histoury.app.theme.PrimarySoft)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClickLabel = "Show commute steps",
                            onClick = onShowCommuteSteps
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsTransit,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = rides ?: "Walking is quickest",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Steps",
                        color = Primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/** The commute plan for the stop being navigated to: every walk and ride, in order. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CommuteStepsNavSheet(uiState: MapUiState, onDismiss: () -> Unit) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SheetSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "Getting to ${uiState.site?.siteName ?: "your destination"}",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            com.histoury.app.ui.components.CommuteTimeline(uiState.commuteSteps, uiState.site?.siteName)
        }
    }
}

// ---------------------------------------------------------------- formatting

internal fun maneuverIcon(maneuver: String): ImageVector = when (maneuver.uppercase()) {
    "TURN_LEFT" -> Icons.Default.TurnLeft
    "TURN_RIGHT" -> Icons.Default.TurnRight
    "TURN_SHARP_LEFT" -> Icons.Default.TurnSharpLeft
    "TURN_SHARP_RIGHT" -> Icons.Default.TurnSharpRight
    "TURN_SLIGHT_LEFT" -> Icons.Default.TurnSlightLeft
    "TURN_SLIGHT_RIGHT" -> Icons.Default.TurnSlightRight
    "FORK_LEFT" -> Icons.Default.ForkLeft
    "FORK_RIGHT" -> Icons.Default.ForkRight
    "UTURN_LEFT" -> Icons.Default.UTurnLeft
    "UTURN_RIGHT" -> Icons.Default.UTurnRight
    "ROUNDABOUT_LEFT" -> Icons.Default.RoundaboutLeft
    "ROUNDABOUT_RIGHT" -> Icons.Default.RoundaboutRight
    "RAMP_LEFT" -> Icons.Default.RampLeft
    "RAMP_RIGHT" -> Icons.Default.RampRight
    "MERGE" -> Icons.Default.Merge
    "FERRY", "FERRY_TRAIN" -> Icons.Default.DirectionsBoat
    "TRANSIT" -> Icons.Default.DirectionsTransit
    NavInstruction.DESTINATION -> Icons.Default.Flag
    else -> Icons.Default.Straight
}

/** Waze-style rounding: exact-ish up close, coarser further out. */
internal fun formatNavDistance(meters: Float): String = when {
    meters < 10f -> "Now"
    meters < 100f -> "${(meters / 5).toInt() * 5} m"
    meters < 1000f -> "${(meters / 10).toInt() * 10} m"
    else -> String.format(Locale.US, "%.1f km", meters / 1000f)
}

private fun formatMinutes(minutes: Int): String =
    if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"

private fun arrivalClock(etaMinutes: Int): String =
    SimpleDateFormat("h:mm a", Locale.getDefault())
        .format(Date(System.currentTimeMillis() + etaMinutes * 60_000L))
