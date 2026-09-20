package com.histoury.app.ui.screens.debug

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.google.android.gms.location.LocationServices
import com.google.ar.core.Config
import com.google.ar.core.Earth
import com.google.ar.core.Session
import com.google.ar.core.StreetscapeGeometry
import com.google.ar.core.TrackingState
import com.google.ar.core.VpsAvailability
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Danger
import com.histoury.app.theme.Success
import com.histoury.app.theme.SurfaceSoft as BackgroundGray
import com.histoury.app.theme.TextPrimary as TextMain
import com.histoury.app.theme.TextSecondary
import com.histoury.app.theme.Primary as PrimaryPink
import io.github.sceneview.ar.ARScene
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes

/**
 * ============================================================================
 *  TEMPORARY ON-SITE TEST TOOL — SAFE TO DELETE AFTER TESTING
 * ============================================================================
 *
 * This screen exists for exactly one purpose: to find out, while physically
 * standing at Fort Santiago and Baluarte de San Diego, whether ARCore's
 * Streetscape Geometry feature can actually "see" the shape of the buildings
 * at those two sites (as opposed to only the ground).
 *
 * It is intentionally kept completely separate from the real AR experience:
 *   - It does NOT touch ARExperienceScreen.kt or ArExperienceViewModel.kt.
 *   - It does NOT place, scale, or anchor any 3D model.
 *   - It does NOT read or write any site/reference-photo/geofence data.
 *   - It opens its own throwaway ARCore session and closes it when you leave.
 *
 * Nothing about production AR placement changes because this file exists.
 * Once on-site testing is done and a decision has been made about
 * Streetscape Geometry, this entire file — plus its one route in Routes.kt /
 * NavGraph.kt and its one menu entry in MenuScreen.kt — can be deleted with
 * no effect on the rest of the app.
 */
@SuppressLint("MissingPermission")
@Composable
fun StreetscapeDebugScreen(
    navController: NavHostController
) {
    val context = LocalContext.current

    // ---------------------------------------------------------- permissions
    // Same two permissions the real AR screen needs: camera to run ARCore,
    // precise location because Geospatial/Streetscape Geometry is location
    // based. Requested together, once, since this is a short-lived test
    // screen rather than something a visitor uses every day.

    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var locationGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionsAsked by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        cameraGranted = results[Manifest.permission.CAMERA] ?: cameraGranted
        locationGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] ?: locationGranted
        permissionsAsked = true
    }

    LaunchedEffect(Unit) {
        if (!cameraGranted || !locationGranted) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION)
            )
        }
    }

    // ------------------------------------------------------------- AR state
    // Everything below is read straight off the live ARCore session each
    // frame. Nothing here is cached from a previous visit — reopening this
    // screen always starts a fresh check.

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val childNodes = rememberNodes()

    var sessionRef by remember { mutableStateOf<Session?>(null) }
    var startupError by remember { mutableStateOf<String?>(null) }
    var geospatialSupported by remember { mutableStateOf(true) }

    // "GOOD" / "NOT READY"
    var earthTrackingGood by remember { mutableStateOf(false) }
    var earthStateRaw by remember { mutableStateOf("—") }

    // Terrain / building counts, read live from Streetscape Geometry.
    var terrainCount by remember { mutableStateOf(0) }
    var buildingCount by remember { mutableStateOf(0) }

    // Last known camera location (from Earth, once it's tracking), used for
    // the manual VPS check below.
    var currentLat by remember { mutableStateOf<Double?>(null) }
    var currentLng by remember { mutableStateOf<Double?>(null) }

    // Fallback coarse location grabbed once on open, in case Earth tracking
    // never locks on — VPS availability can still be checked from this.
    var fallbackLat by remember { mutableStateOf<Double?>(null) }
    var fallbackLng by remember { mutableStateOf<Double?>(null) }

    LaunchedEffect(locationGranted) {
        if (!locationGranted) return@LaunchedEffect
        runCatching {
            val client = LocationServices.getFusedLocationProviderClient(context)
            client.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    fallbackLat = location.latitude
                    fallbackLng = location.longitude
                }
            }
        }
    }

    // "YES" / "NO" / not checked yet
    var vpsAvailable by remember { mutableStateOf<Boolean?>(null) }
    var vpsChecking by remember { mutableStateOf(false) }
    var vpsNote by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            // ARScene owns closing the Session itself; nothing extra to
            // release here since this screen creates no anchors or models.
        }
    }

    val terrainFound = terrainCount > 0
    val buildingFound = buildingCount > 0

    val overallStatus = when {
        startupError != null ->
            "AR couldn't start on this device for this test."
        !geospatialSupported ->
            "This phone doesn't support location-based AR, so this test can't run here."
        !earthTrackingGood ->
            "Still locating — hold the phone up, slowly look around at the sky and nearby buildings, and wait a few seconds."
        buildingFound ->
            "Building shapes were found here. Streetscape Geometry looks usable at this spot."
        terrainFound ->
            "Only ground shapes were found so far — no building shapes yet at this spot."
        else ->
            "No shapes found here yet. Try moving closer to a building, or waiting a bit longer."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGray)
            .statusBarsPadding()
    ) {

        // --- Header ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Column(modifier = Modifier.align(Alignment.Center)) {
                Text(
                    text = "Site AR Test (Temporary)",
                    color = TextMain,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "For on-site testing only — not shown to visitors",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(CardSurface)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        navController.popBackStack()
                    }
                    .align(Alignment.CenterStart),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = PrimaryPink,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {

            if (!cameraGranted || !locationGranted) {

                InfoBanner(
                    text = if (permissionsAsked) {
                        "Camera and location access are needed to run this test. " +
                            "Please allow them in phone Settings, then reopen this screen."
                    } else {
                        "Waiting for camera and location permission…"
                    }
                )
            } else {

                // --- Live camera preview (small — this screen is about the
                // numbers below, not about looking through the camera) ---
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .padding(vertical = 12.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black
                ) {
                    ARScene(
                        modifier = Modifier.fillMaxSize(),
                        engine = engine,
                        modelLoader = modelLoader,
                        childNodes = childNodes,

                        // Nothing is ever placed on screen — this test only
                        // reads what ARCore reports, so plane detection and
                        // image recognition both stay off.
                        planeRenderer = false,

                        sessionConfiguration = { session, config ->

                            config.planeFindingMode = Config.PlaneFindingMode.DISABLED
                            config.focusMode = Config.FocusMode.AUTO

                            val supported = runCatching {
                                session.isGeospatialModeSupported(Config.GeospatialMode.ENABLED)
                            }.getOrDefault(false)

                            geospatialSupported = supported

                            if (supported) {
                                // Streetscape Geometry requires Geospatial
                                // mode to be enabled at the same time — this
                                // is the one thing this whole test is meant
                                // to check, so both are turned on together.
                                config.geospatialMode = Config.GeospatialMode.ENABLED
                                config.streetscapeGeometryMode = Config.StreetscapeGeometryMode.ENABLED
                            } else {
                                config.geospatialMode = Config.GeospatialMode.DISABLED
                                config.streetscapeGeometryMode = Config.StreetscapeGeometryMode.DISABLED
                            }
                        },

                        onSessionCreated = { session ->
                            sessionRef = session
                        },

                        onSessionFailed = { error ->
                            startupError = error.message ?: "Unknown error"
                        },

                        onSessionUpdated = { session, _ ->

                            val earth: Earth? = if (geospatialSupported) {
                                runCatching { session.earth }.getOrNull()
                            } else {
                                null
                            }

                            if (earth != null) {
                                earthStateRaw = earth.earthState.name

                                if (earth.earthState == Earth.EarthState.ENABLED &&
                                    earth.trackingState == TrackingState.TRACKING
                                ) {
                                    earthTrackingGood = true
                                    val pose = earth.cameraGeospatialPose
                                    currentLat = pose.latitude
                                    currentLng = pose.longitude
                                } else {
                                    earthTrackingGood = false
                                }
                            } else {
                                earthTrackingGood = false
                                earthStateRaw = "—"
                            }

                            if (geospatialSupported) {
                                val geometries = runCatching {
                                    session.getAllTrackables(StreetscapeGeometry::class.java)
                                }.getOrDefault(emptyList())

                                terrainCount = geometries.count {
                                    it.type == StreetscapeGeometry.Type.TERRAIN
                                }
                                buildingCount = geometries.count {
                                    it.type == StreetscapeGeometry.Type.BUILDING
                                }
                            }
                        }
                    )
                }

                InfoBanner(
                    text = "Point the phone up and around slowly, like you're taking a panorama, " +
                        "so ARCore has a chance to see nearby buildings and the ground."
                )

                Spacer(Modifier.height(4.dp))

                // --- The six results the user asked to see, in plain language ---
                ResultsCard {

                    ResultRow(
                        label = "VPS Available",
                        value = when (vpsAvailable) {
                            true -> "YES"
                            false -> "NO"
                            null -> if (vpsChecking) "Checking…" else "Not checked yet"
                        },
                        tone = when (vpsAvailable) {
                            true -> RowTone.GOOD
                            false -> RowTone.BAD
                            null -> RowTone.NEUTRAL
                        }
                    )

                    Divider()

                    ResultRow(
                        label = "Earth Tracking",
                        value = if (earthTrackingGood) "GOOD" else "NOT READY",
                        tone = if (earthTrackingGood) RowTone.GOOD else RowTone.NEUTRAL
                    )

                    Divider()

                    ResultRow(
                        label = "Terrain Geometry Found",
                        value = if (terrainFound) "YES" else "NO",
                        tone = if (terrainFound) RowTone.GOOD else RowTone.NEUTRAL
                    )

                    Divider()

                    ResultRow(
                        label = "Building Geometry Found",
                        value = if (buildingFound) "YES" else "NO",
                        tone = if (buildingFound) RowTone.GOOD else RowTone.BAD
                    )

                    Divider()

                    ResultRow(
                        label = "Buildings Detected",
                        value = buildingCount.toString(),
                        tone = if (buildingFound) RowTone.GOOD else RowTone.NEUTRAL
                    )
                }

                Spacer(Modifier.height(16.dp))

                // --- Overall status, spelled out in one plain sentence ---
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = CardSurface
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Streetscape Geometry Status",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = overallStatus,
                            color = TextMain,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        startupError?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(text = it, color = Danger, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // --- Manual VPS check ---
                val checkLat = currentLat ?: fallbackLat
                val checkLng = currentLng ?: fallbackLng

                Button(
                    onClick = {
                        val session = sessionRef
                        val lat = checkLat
                        val lng = checkLng
                        if (session == null || lat == null || lng == null) {
                            vpsNote = "Location isn't available yet — wait a moment and try again."
                            return@Button
                        }
                        vpsChecking = true
                        vpsNote = null
                        runCatching {
                            session.checkVpsAvailabilityAsync(lat, lng) { result ->
                                vpsChecking = false
                                when (result) {
                                    VpsAvailability.AVAILABLE -> {
                                        vpsAvailable = true
                                        vpsNote = null
                                    }
                                    VpsAvailability.UNAVAILABLE -> {
                                        vpsAvailable = false
                                        vpsNote = "Google reports no coverage at this exact spot."
                                    }
                                    else -> {
                                        vpsAvailable = null
                                        vpsNote = "Couldn't check right now (${result.name}). " +
                                            "Check your internet connection and try again."
                                    }
                                }
                            }
                        }.onFailure {
                            vpsChecking = false
                            vpsNote = "Couldn't check right now: ${it.message}"
                        }
                    },
                    enabled = !vpsChecking
                ) {
                    if (vpsChecking) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Check VPS Availability")
                }

                vpsNote?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(text = it, color = TextSecondary, fontSize = 12.sp)
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Location used for the VPS check: " + if (checkLat != null && checkLng != null) {
                        "%.5f, %.5f".format(checkLat, checkLng)
                    } else {
                        "not available yet"
                    },
                    color = TextSecondary,
                    fontSize = 11.sp
                )

                Text(
                    text = "Earth state (advanced): $earthStateRaw   ·   Terrain shapes seen: $terrainCount",
                    color = TextSecondary.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

private enum class RowTone { GOOD, BAD, NEUTRAL }

@Composable
private fun ResultsCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = CardSurface
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp), content = content)
    }
}

@Composable
private fun ResultRow(label: String, value: String, tone: RowTone) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextMain,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )

        val color = when (tone) {
            RowTone.GOOD -> Success
            RowTone.BAD -> Danger
            RowTone.NEUTRAL -> TextSecondary
        }

        Text(
            text = value,
            color = color,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = BackgroundGray,
        thickness = 0.5.dp
    )
}

@Composable
private fun InfoBanner(text: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = CardSurface
    ) {
        Text(
            text = text,
            color = TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(14.dp)
        )
    }
}
