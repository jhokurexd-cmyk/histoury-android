package com.histoury.app.ui.screens.ar

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.ViewGroup
import android.view.View
import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.google.ar.core.Anchor
import com.google.ar.core.AugmentedImage
import com.google.ar.core.AugmentedImageDatabase
import com.google.ar.core.Config
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.histoury.app.data.offline.OfflineStore
import com.histoury.app.data.viewmodel.ArExperienceViewModel
import com.histoury.app.data.viewmodel.ArStage
import com.histoury.app.data.viewmodel.ArUiState
import com.histoury.app.theme.SurfaceSoft
import com.histoury.app.theme.AccentGold
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Primary
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.model.model
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import com.histoury.app.data.model.HistoricalSite
import com.histoury.app.data.model.ArReferenceImage
import com.histoury.app.data.model.referenceImages
import com.histoury.app.data.model.structureHeightFraction
import com.histoury.app.data.offline.ArModelCache
import com.histoury.app.data.viewmodel.GeoStatus
import com.histoury.app.data.viewmodel.PlacementSource
import com.google.ar.core.Earth
import com.google.ar.core.GeospatialPose
import com.google.ar.core.TrackingState
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.histoury.app.data.model.SiteContent
import com.histoury.app.data.viewmodel.SiteDetailsTab
import com.histoury.app.ui.components.rememberVoiceoverController
import com.histoury.app.ui.components.SiteContentTabBody
import com.histoury.app.ui.components.SiteContentTabRow
import com.histoury.app.data.repository.GeofenceRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.tasks.await
import io.github.sceneview.math.Scale

/**
 * The "Travel Back in Time" screen.
 *
 * Point the camera at a structure and the historical version of it appears
 * standing in the same place, at the same size, aligned to the real thing.
 *
 * Positioning uses whichever method the admin panel set for the site
 * ([HistoricalSite.arAnchorMode]):
 *
 *   Image recognition  ARCore matches the camera feed against the site's
 *                      reference photos (one or several — each with its own
 *                      placement) and reports where the photographed
 *                      structure is. Measured against the very thing the
 *                      model replaces, and indifferent to GPS quality.
 *
 *   Geospatial         The model is pinned to a latitude/longitude and
 *                      compass heading on a terrain anchor, once ARCore has
 *                      localised the phone accurately enough. No photo
 *                      needed, but it depends on a good fix.
 *
 *   Both               Whichever locks on first places the model.
 *
 * Geospatial scenes never scan: no reference photo is downloaded and no
 * image database is configured, so the camera goes straight to localising
 * and the model deploys itself once Earth is tracking.
 *
 * WORLD-LOCKED: whatever the method, the model is placed exactly once on a
 * world anchor (a session anchor for photos, a terrain anchor for
 * geospatial) and is never moved afterwards. Moving the phone only moves
 * the camera. See the comment above the placement code in ArCameraLayer.
 *
 * Accuracy measures: nothing is placed until the photo's size estimate has
 * settled; the model is levelled so it never leans with a tilted photo
 * estimate; geospatial waits for a precise fix (with a fallback so it still
 * deploys in narrow streets).
 *
 * The flow:
 *
 *   LOADING_SITE → SEARCHING → ANCHORING → PLACED
 *                      ↓
 *              NOT_RECOGNIZED → MANUAL_ALIGNING → PLACED
 *
 * Recognition depends on light, angle and an unobstructed view, and
 * geospatial on a clear view of the surroundings — none of which can be
 * guaranteed on any particular day. So when it doesn't fire,
 * the screen offers hand placement rather than an apology. It is visibly
 * cruder and it says so, but it always works.
 *
 * Written against the SceneView 2.x API: the scene owns a mutable
 * [rememberNodes] list and nodes are added to it imperatively, rather than
 * the declarative content DSL that later majors use. The library version is
 * pinned in app/build.gradle.kts — see the comment there before upgrading.
 */
@SuppressLint("MissingPermission")
@Composable
fun ARExperienceScreen(
    siteId: String,
    navController: NavHostController,
    viewModel: ArExperienceViewModel = viewModel()
) {

    val uiState by viewModel.uiState.collectAsState()

    val context = LocalContext.current

    val activity = context as? Activity

    // Hides the overlay for the frame that PixelCopy reads.
    //
    // PixelCopy copies the window exactly as drawn, so a capture taken with
    // the interface up contains the back button, the shutter, the hint card
    // and the placement panel burned into the JPEG. A visitor's souvenir of
    // Fort Santiago should be the fort, not a screenshot of an app looking
    // at it.
    var isCapturing by remember { mutableStateOf(false) }

    var showSiteInfo by remember { mutableStateOf(false) }

    // The most recent screen capture, waiting on the review sheet for a
    // filter choice before it's uploaded. Null whenever that sheet isn't
    // showing — its presence is what puts the sheet on screen.
    var pendingCapture by remember { mutableStateOf<Bitmap?>(null) }

    val site = uiState.site

    // The reference photographs, in memory, keyed by their admin-panel id.
    // ARCore needs these before the session is configured, so unlike the
    // model they are a prerequisite for showing the camera at all.
    var referenceBitmaps by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }

    // True once everything the chosen anchoring method(s) need is ready:
    // photos downloaded for image recognition, location permission for
    // geospatial.
    var assetsReady by remember { mutableStateOf(false) }

    // Geospatial needs precise location. Visitors normally granted it for
    // arrival detection long before reaching the AR button, so this is
    // usually already true and the prompt never shows.
    var locationGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var locationAsked by remember { mutableStateOf(false) }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        locationGranted = granted
        locationAsked = true
    }

    // Whether the device is physically inside THIS site's own geofence.
    // Recognition is already scoped to this site's own reference photos, but
    // nothing else re-checks that this is even the right site to be looking
    // at — a site can get loaded here via a tapped notification, a live
    // arrival event, or simply mislabeled content, none of which is
    // re-verified against live location. null = still checking; the camera
    // only ever starts once this is true, and a false result means
    // [ArExperienceViewModel.verifyGeofence] has already moved the screen to
    // ArStage.UNAVAILABLE with an explanation.
    var geofenceCheckPassed by remember { mutableStateOf<Boolean?>(null) }

    val geofenceRepository = remember { GeofenceRepository() }

    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    // ARScene (SceneView) is only ever composed once this is true. Camera
    // access is gated here, in our own code, rather than leaning on
    // whatever ARCore/SceneView does internally when it hits a missing
    // permission — that path was landing visitors back on Site Details
    // instead of into the camera after they granted the permission from
    // Settings. Checking and re-checking it ourselves, and only mounting
    // ArCameraLayer once it is actually granted, means a trip to Settings
    // always resumes into the camera and never triggers a back navigation.
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var cameraPermissionAsked by remember { mutableStateOf(false) }
    var showCameraSettingsExplanation by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraGranted = granted
        cameraPermissionAsked = true
    }

    // Re-checks on return rather than trusting the result code: the visitor
    // may have granted it, left it denied, or backed out of Settings
    // entirely, and only the live permission state can tell those apart.
    val cameraSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        cameraGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(cameraGranted, cameraPermissionAsked) {
        if (cameraGranted) return@LaunchedEffect
        if (!cameraPermissionAsked) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            showCameraSettingsExplanation = true
        }
    }

    if (showCameraSettingsExplanation && !cameraGranted) {
        AlertDialog(
            onDismissRequest = { showCameraSettingsExplanation = false },
            title = { Text("Allow camera access") },
            text = {
                Text(
                    "Histoury needs the camera to show AR. On the next " +
                        "screen, open Permissions and allow Camera."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCameraSettingsExplanation = false
                        cameraSettingsLauncher.launch(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }
                ) {
                    Text("Open settings")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCameraSettingsExplanation = false
                        navController.popBackStack()
                    }
                ) {
                    Text("Not now")
                }
            }
        )
    }

    LaunchedEffect(siteId) {
        viewModel.loadSite(siteId)
    }

    // Reference photos, downloaded in parallel. A photo that fails is
    // skipped rather than failing the whole scene — the others can still be
    // recognised. Only when none load does image recognition drop out (and
    // geospatial carries on alone if it's set up).
    LaunchedEffect(site?.documentId, uiState.useImage) {

        val currentSite = site ?: return@LaunchedEffect
        if (!uiState.useImage || referenceBitmaps.isNotEmpty()) return@LaunchedEffect

        val references = currentSite.referenceImages

        val loaded = coroutineScope {
            references.map { reference ->
                async {
                    val bitmap = try {
                        downloadReferenceImage(context, reference.url)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        Log.e(
                            "HistouryAR",
                            "Could not load reference photo \"${reference.label}\" " +
                                "for ${currentSite.siteName}",
                            error
                        )
                        null
                    }
                    bitmap?.let { reference.id to it }
                }
            }.awaitAll().filterNotNull().toMap()
        }

        if (loaded.isEmpty()) {
            viewModel.onImageAnchoringUnavailable(
                "The reference photos for this site couldn't be loaded. " +
                    "Check your connection and try opening AR again."
            )
        } else {
            referenceBitmaps = loaded
        }
    }

    // Ask for precise location when geospatial is in play and it isn't
    // granted yet. Declining falls back to image recognition when that's
    // set up too.
    LaunchedEffect(site?.documentId, uiState.useGeospatial, locationGranted, locationAsked) {
        if (site == null || !uiState.useGeospatial || locationGranted) return@LaunchedEffect
        if (!locationAsked) {
            locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            viewModel.onGeospatialUnavailable(
                "Placing this structure by location needs precise location " +
                    "access. Allow it in Settings, then open AR again."
            )
        }
    }

    // Re-verifies, every time the loaded site changes, that the device is
    // actually standing inside that site's own geofence before AR is allowed
    // to arm for it. Shares locationAsked/locationLauncher with the
    // geospatial permission effect above — by the time a visitor reaches
    // this screen, location access was almost always already granted for
    // arrival detection, so this rarely has to prompt at all.
    LaunchedEffect(site?.documentId, locationGranted) {

        val currentSite = site ?: return@LaunchedEffect

        geofenceCheckPassed = null

        val geofence = try {
            geofenceRepository.getGeofenceForSite(currentSite.siteId)
        } catch (e: Exception) {
            null
        }

        if (geofence == null) {
            // Nothing to enforce yet for this site — fail open so sites
            // without a configured geofence keep working as before.
            geofenceCheckPassed = true
            return@LaunchedEffect
        }

        if (!locationGranted) {
            if (!locationAsked) {
                locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            return@LaunchedEffect
        }

        val location = try {
            fusedLocationClient.lastLocation.await()
        } catch (e: Exception) {
            null
        }

        geofenceCheckPassed = viewModel.verifyGeofence(location)
    }

    // Start the camera once every enabled method has what it needs.
    LaunchedEffect(
        site?.documentId,
        uiState.useImage,
        uiState.useGeospatial,
        referenceBitmaps,
        locationGranted,
        geofenceCheckPassed
    ) {
        if (site == null || assetsReady) return@LaunchedEffect
        if (!uiState.useImage && !uiState.useGeospatial) return@LaunchedEffect
        if (geofenceCheckPassed != true) return@LaunchedEffect

        val imageReady = !uiState.useImage || referenceBitmaps.isNotEmpty()
        val geoReady = !uiState.useGeospatial || locationGranted

        if (imageReady && geoReady) {
            assetsReady = true
            viewModel.onAssetsReady()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        when (uiState.stage) {

            ArStage.LOADING_SITE -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }

            ArStage.UNAVAILABLE -> {
                UnavailableState(
                    reason = uiState.unavailableReason,
                    onBack = { navController.popBackStack() }
                )
            }

            else -> {

                if (!cameraGranted) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }
                } else if (!assetsReady) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }
                } else {
                    ArCameraLayer(
                        uiState = uiState,
                        viewModel = viewModel,
                        referenceBitmaps = referenceBitmaps,
                        isCapturing = isCapturing,
                        showSiteInfo = showSiteInfo,
                        onShowSiteInfoChange = { showSiteInfo = it },
                        onCapture = {
                            // The sheet is its own window, so the overlay's
                            // capture fade does not cover it. Closing it
                            // first keeps it out of the photograph.
                            showSiteInfo = false
                            isCapturing = true
                        }
                    )
                }
            }
        }

        // Two frames, not one. The first is when Compose applies the state
        // change; the overlay is only guaranteed to be off-screen after the
        // frame that follows it has been drawn. Copying too early captures
        // the interface mid-fade.
        LaunchedEffect(isCapturing) {

            if (!isCapturing) return@LaunchedEffect

            withFrameNanos { }
            withFrameNanos { }

            val host = activity

            if (host == null) {
                isCapturing = false
                return@LaunchedEffect
            }

            captureWindow(host) { captured ->
                isCapturing = false
                if (captured != null) {
                    // Hand off to the review sheet instead of uploading right
                    // away, so the visitor can pick a filter — or none — before
                    // anything is saved.
                    pendingCapture = captured
                } else {
                    viewModel.onCaptureFailed()
                }
            }
        }

        if (uiState.stage != ArStage.UNAVAILABLE && !isCapturing && pendingCapture == null) {
            BackButton(onClick = { navController.popBackStack() })
        }

        // Capture result toast. Auto-clears so it doesn't sit over the scene.
        uiState.captureMessage?.let { message ->

            LaunchedEffect(message) {
                kotlinx.coroutines.delay(2600)
                viewModel.clearCaptureMessage()
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 90.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Text(
                    text = message,
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // The filter picker for the photo just taken. Covers everything else
        // on screen until the visitor saves or discards it.
        pendingCapture?.let { captured ->
            CaptureReviewSheet(
                original = captured,
                isSaving = uiState.isSavingCapture,
                onDiscard = { pendingCapture = null },
                onSave = { filtered ->
                    viewModel.saveCapture(filtered)
                    pendingCapture = null
                }
            )
        }
    }
}

// ------------------------------------------------------------------ camera

@Composable
private fun ArCameraLayer(
    uiState: ArUiState,
    isCapturing: Boolean,
    // Owned by ARExperienceScreen rather than held here, because the
    // shutter — which lives up there — has to be able to close the sheet
    // before the screen is copied.
    showSiteInfo: Boolean,
    onShowSiteInfoChange: (Boolean) -> Unit,
    viewModel: ArExperienceViewModel,
    // Reference photos keyed by their admin-panel id. Empty when the scene
    // is placed by geospatial only.
    referenceBitmaps: Map<String, Bitmap>,
    onCapture: () -> Unit
) {

    val context = LocalContext.current

    val engine = rememberEngine()

    val modelLoader = rememberModelLoader(engine)

    // The scene graph. In SceneView 2.x nodes are managed by mutating this
    // list, not by declaring composable children.
    val childNodes = rememberNodes()

    val site = uiState.site

    // The site's reference photos, primary first, by id — the same ids the
    // image database uses as names, so a recognised image maps straight
    // back to its own offsets and rotation.
    val references = remember(site) { site?.referenceImages.orEmpty() }
    val referencesById = remember(references) { references.associateBy { it.id } }
    val primaryReferenceId = references.firstOrNull()?.id

    var modelInstance by remember { mutableStateOf<ModelInstance?>(null) }

    // ----- anchoring bookkeeping -----

    /** What created the current anchor. */
    var anchorSource by remember { mutableStateOf<PlacementSource?>(null) }

    /** The reference photo the current image anchor was built from. */
    var anchoredImageId by remember { mutableStateOf<String?>(null) }

    /**
     * Consecutive frames since the placed structure's reference image last
     * had ANY tracking reading at all (not even extrapolated), for the
     * TRACKING_LOST debounce further down. A person walking past, a passing
     * shadow, or someone stepping close enough that the photo leaves the
     * frame all drop this to zero-tracking for a moment during completely
     * normal use; only a run of consecutive misses means recognition has
     * genuinely lapsed.
     */
    var trackingLostFrames by remember { mutableStateOf(0) }

    /** What the model's size is derived from; set when an anchor is made. */
    var scaleBasis by remember { mutableStateOf<ScaleBasis?>(null) }

    /** Photos ARCore accepted into the image database, and ones it refused. */
    var registeredImageIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var rejectedPhotoLabels by remember { mutableStateOf<List<String>>(emptyList()) }
    var imageSetupDone by remember { mutableStateOf(false) }
    var imageFailureReported by remember { mutableStateOf(false) }

    /** Per photo: the settled width estimate, once it has settled. */
    val lockedWidths = remember { mutableMapOf<String, Float>() }

    /**
     * Per photo: the settled physical HEIGHT estimate (ARCore's extentZ),
     * captured at the exact same moment [lockedWidths] locks or relocks.
     *
     * Riding on the existing width stabiliser rather than running a second
     * one: extentX and extentZ come from the same underlying ARCore size
     * estimate and settle together, so gating on the width's own stability
     * (unchanged below) is exactly as valid a signal for the height. This is
     * what the model's scale is now actually derived from — see the scale
     * LaunchedEffect further down.
     */
    val lockedHeights = remember { mutableMapOf<String, Float>() }

    /** Which photo the width stabiliser is currently following. */
    var stabilizerImageId by remember { mutableStateOf<String?>(null) }
    var fullTrackingFrames by remember { mutableStateOf(0) }
    var relockFrames by remember { mutableStateOf(0) }

    /** When Earth tracking started, for the geospatial placement fallback. */
    var earthTrackingSinceMillis by remember { mutableStateOf(0L) }

    /** True once a geospatial placement has been pinned in the local world frame. */
    var geoPosePinned by remember { mutableStateOf(false) }

    /**
     * Terrain anchor requested EARLY — as soon as Earth has any fix — so
     * Google's ground-height lookup runs while the app is still waiting for
     * a precise location, instead of after it. Held here, unplaced, until
     * the location is good enough; then it becomes the model's anchor.
     */
    var earlyTerrainAnchor by remember { mutableStateOf<Anchor?>(null) }
    var lastTerrainRequestMillis by remember { mutableStateOf(0L) }

    // Geospatial
    var geospatialActive by remember { mutableStateOf(false) }
    var geospatialUnsupported by remember { mutableStateOf(false) }
    var geoProblemReported by remember { mutableStateOf(false) }
    var geoGoodFrames by remember { mutableStateOf(0) }
    var terrainPending by remember { mutableStateOf(false) }
    var lastGeoStatusMillis by remember { mutableStateOf(0L) }
    var geoDebug by remember { mutableStateOf("off") }

    var anchor by remember { mutableStateOf<Anchor?>(null) }

    // Prevent the per-frame callback from creating several anchors before
    // the first one has been attached.
    var anchorCreated by remember { mutableStateOf(false) }

    var placedNode by remember { mutableStateOf<AnchorNode?>(null) }

    // Kept separately so manual adjustments can be applied to it.
    var modelNode by remember { mutableStateOf<ModelNode?>(null) }

    // Manual alignment offsets, relative to the anchor. Untouched when
    // recognition succeeds.
    var manualPosition by remember { mutableStateOf(Position(0f, 0f, 0f)) }

    var manualYaw by remember { mutableStateOf(0f) }

    // Pinch-to-resize multiplier for "Place It Myself", layered on top of
    // whatever base scale the model would otherwise use (admin Model Size,
    // same as any other manual placement). Kept separate from that base
    // factor — see baseScaleFactor below — so a live pinch doesn't have to
    // re-run the (comparatively heavy) model-measurement effect on every
    // gesture tick.
    var manualScaleMultiplier by remember { mutableStateOf(1f) }

    /** The scale factor from ScaleBasis alone, before manualScaleMultiplier. */
    var baseScaleFactor by remember { mutableStateOf(0f) }

    // ----- "Place It Myself" plane placement (see resetManualPlacement) -----
    //
    // This whole group is specific to PlacementSource.MANUAL. Geospatial and
    // Image placement never touch it, and it never touches their anchors.

    /** Size, in pixels, of the ARScene view — needed to project world points
     *  (the aim reticle, the ground grid) into screen space. Kept up to date
     *  via Modifier.onSizeChanged on the Box the scene and its overlays
     *  share, so the projection always matches what's actually on screen. */
    var arViewSizePx by remember { mutableStateOf(IntSize.Zero) }

    // Reused every frame rather than allocated fresh, same reasoning as any
    // other per-frame ARCore scratch buffer: these are populated in place by
    // Camera.getViewMatrix()/getProjectionMatrix() (which take an output
    // array), not returned, so one array reused across frames avoids a
    // FloatArray(16) allocation on every single AR frame.
    val scratchViewMatrix = remember { FloatArray(16) }
    val scratchProjMatrix = remember { FloatArray(16) }

    /** The world-space pose the aim reticle is currently over, from a
     *  screen-center hit test against a tracked horizontal plane. Null
     *  whenever the phone isn't currently pointed at a valid surface. */
    var groundReticlePose by remember { mutableStateOf<Pose?>(null) }

    /** The plane the reticle is currently over. The polygon-containment
     *  check that keeps the grid off of anything that isn't real floor
     *  happens inline against the plane found each frame; this is kept
     *  alongside groundReticlePose as the natural pairing for either
     *  debugging or future use (e.g. surfacing which plane was used). */
    var groundPlane by remember { mutableStateOf<Plane?>(null) }

    /** groundReticlePose (or, once placed, the model's current ground point)
     *  projected to screen pixels, for the center indicator. Null hides it. */
    var reticleScreenPos by remember { mutableStateOf<Offset?>(null) }

    /** Ground grid tile lines, already projected to screen pixels. Only
     *  populated before the structure is placed. */
    var groundGridScreenLines by remember { mutableStateOf<List<Pair<Offset, Offset>>>(emptyList()) }

    /** Set by a tap on the aim surface, consumed on the next AR frame (where
     *  the actual Session/Anchor calls have to happen — see onSessionUpdated
     *  below) rather than acted on directly from the gesture callback. */
    var pendingPlaceTap by remember { mutableStateOf(false) }

    // Set from sessionConfiguration, consumed by onViewUpdated. The depth
    // occlusion material must only be applied when the session actually has
    // a depth mode enabled — otherwise the camera stream is shaded with a
    // material that never receives a depth texture.
    var depthSupported by remember { mutableStateOf(false) }

    // Built once per session from the reference photo.
    var imageDatabaseReady by remember { mutableStateOf(false) }

    // The live ARCore session, captured from onSessionCreated below so the
    // image-database build (see the LaunchedEffect near the reference-photo
    // loading effect above) can run once it exists, off the main thread,
    // instead of inline inside sessionConfiguration.
    var arCoreSession by remember { mutableStateOf<Session?>(null) }

    // Off by default.
    //
    // Depth occlusion hides anything the depth map says is behind a real
    // surface. For a reconstruction anchored to the very wall it replaces,
    // that is a losing fight: the model and the masonry are at the same
    // depth, so the real wall wins and nothing draws. The experience then
    // only works if someone finds this toggle — which is not an experience.
    //
    // It earns its place when a model stands clearly in front of real
    // geometry and should be hidden by a passing visitor or a pillar, so it
    // stays available rather than being removed.
    var occlusionEnabled by remember { mutableStateOf(false) }

    var modelBytes by remember { mutableStateOf(0L) }

    // Whether to light the model from SceneView's own neutral environment
    // instead of from ARCore's reading of the room.
    //
    // ENVIRONMENTAL_HDR is faithful, and that is the problem: point the
    // phone at a dim room and it faithfully renders a dim model. A
    // photogrammetry scan already has its original lighting baked into the
    // base colour texture, so relighting it at a fraction of the intensity
    // it was captured under crushes it to a brown smear with no readable
    // detail — the same asset that looks correct in the admin preview,
    // which lights it with a fixed studio environment.
    //
    // Defaulting to the neutral environment means the reconstruction is
    // legible wherever it is viewed. The estimate can be switched back on
    // below, and is worth it outdoors in daylight, where matching the real
    // light genuinely helps the model sit in the scene.
    // Survives leaving and reopening AR, which it has to: the choice only
    // takes effect when a session is created, so a value scoped to this
    // composable would reset itself on the very reopen that was meant to
    // apply it.
    val studioLighting = ArLightingPreference.useNeutralLighting

    // How wide ARCore measures the recognised structure to be, in metres.
    //
    // Estimated from the camera feed rather than declared in the admin
    // panel, so the same reference photo works whether it is printed on a
    // leaflet, shown on a monitor, or being looked at as the building
    // itself. This is the only thing tying the camera feed to real-world
    // scale, which is why the model is sized against it.
    var imageExtentX by remember { mutableStateOf(0f) }

    /**
     * The structure height the model is scaled from, fixed once ARCore's
     * image-size estimate settles.
     *
     * Without this the model would resize on every frame. ARCore refines its
     * size estimate continuously while tracking, and feeding each new value
     * straight into the scale makes a building visibly breathe. Locking on
     * the first stable reading trades a little accuracy for a model that
     * holds still — which matters more, because a visitor cannot tell a 3%
     * size error but notices jitter immediately.
     */
    var lockedStructureHeight by remember { mutableStateOf<Float?>(null) }

    /** Consecutive readings agreeing within tolerance, for the lock. */
    var stableReadings by remember { mutableStateOf(0) }

    /** Measured from the GLB, kept for the placement panel. */
    var measuredModelHeight by remember { mutableStateOf(0f) }
    var appliedScaleFactor by remember { mutableStateOf(0f) }

    var imageExtentZ by remember { mutableStateOf(0f) }

    // Animation clock, taken when the model finishes loading rather than
    // when the screen opens, so a plume always starts from its first frame
    // however long the download took.
    var animationStartNanos by remember { mutableStateOf(0L) }

    var animationCount by remember { mutableStateOf(0) }

    var debug by remember { mutableStateOf<ArPlacementDebug?>(null) }

    var showDebug by remember { mutableStateOf(false) }

    // Anchor/node cleanup lives further down, right after the ARScene(...)
    // call — see the comment there for why the position matters.

    // Frame-clock baseline for the recognition timeout. Re-taken on retry so
    // a second attempt gets a full window instead of instantly timing out
    // against the first attempt's start time.
    val startedAtMillis = remember(uiState.retryToken) { System.currentTimeMillis() }

    LaunchedEffect(uiState.retryToken) {
        if (uiState.retryToken > 0) {
            anchorCreated = false
            anchorSource = null
            anchoredImageId = null
            trackingLostFrames = 0
            terrainPending = false
            geoGoodFrames = 0
            earthTrackingSinceMillis = 0L
            geoPosePinned = false
            lastTerrainRequestMillis = 0L
        }
    }

    // Usually already on the phone: Site Details and the arrival screen
    // start downloading the model in the background (ArModelCache). Either
    // way it loads in parallel with recognition / localisation.
    LaunchedEffect(site?.arModelUrl) {

        val url = site?.arModelUrl

        if (!url.isNullOrBlank()) {
            modelInstance = try {
                // Joins the background download Site Details / arrival
                // started, if it's still running, instead of starting over.
                val cachedModel = ArModelCache.get(context, url)
                modelBytes = cachedModel.length()
                // Bypass SceneView's file helper: it performs a single
                // InputStream.read() and can return a partially-filled buffer
                // for large models. readBytes() guarantees the complete GLB.
                val modelBuffer = withContext(Dispatchers.IO) {
                    ByteBuffer.wrap(cachedModel.readBytes())
                }
                modelLoader.createModelInstance(modelBuffer)
            } catch (error: CancellationException) {
                // Leaving/recomposing this screen cancels LaunchedEffect. That
                // is normal lifecycle behavior, not a failed GLB download.
                throw error
            } catch (error: Throwable) {
                Log.e("HistouryAR", "Could not load AR model for ${site.siteName}", error)
                null
            }

            if (modelInstance == null) {
                viewModel.onSessionFailed(
                    "The 3D model for this site couldn't be loaded. " +
                        "Check your connection and try opening AR again."
                )
            }
        }
    }

    // Correct the surface response as soon as the GLB is in memory, before
    // it is ever attached to an anchor, so the first frame it appears in is
    // already right.
    LaunchedEffect(modelInstance) {

        val instance = modelInstance ?: return@LaunchedEffect

        animationStartNanos = System.nanoTime()

        animationCount = runCatching { instance.animator.animationCount }.getOrDefault(0)

        runCatching {
            instance.materialInstances.forEach { material ->
                // Per-parameter, because a material that genuinely lacks one
                // of these throws rather than ignoring it, and losing the
                // roughness fix because a material had no metallic slot
                // would be a poor trade.
                runCatching { material.setParameter("metallicFactor", MATTE_METALLIC) }
                runCatching { material.setParameter("roughnessFactor", MATTE_ROUGHNESS) }
            }
        }.onFailure {
            Log.w("HistouryAR", "Could not apply matte surface correction", it)
        }
    }

    // Attaches the model the moment both halves exist: the anchor and the
    // loaded model.
    LaunchedEffect(anchor, modelInstance, imageExtentX) {

        val currentAnchor = anchor
        val currentModel = modelInstance

        if (currentAnchor != null && currentModel != null && placedNode == null) {

            val anchorNode = AnchorNode(engine = engine, anchor = currentAnchor)

            // WORLD-LOCK, part 1: no touch can move the anchor.
            //
            // SceneView 2.x makes every AnchorNode position-editable by
            // default (AnchorNode overrides isPositionEditable = true), and a
            // touch on the model — which is not editable itself — is passed
            // up to its parent anchor node. That drag handler DETACHES the
            // anchor and moves the node to whatever ARCore hit-tests under
            // the finger, frame after frame, until the finger lifts. A thumb
            // resting on the screen while walking therefore dragged the
            // structure along with the phone: the model followed the camera.
            //
            // Manual placement doesn't need this — it has its own drag
            // surface that moves the model node locally, never the anchor.
            anchorNode.isEditable = false
            anchorNode.isPositionEditable = false
            anchorNode.isRotationEditable = false
            anchorNode.isScaleEditable = false
            anchorNode.isTouchable = false

            // Placed at its authored size and scaled afterwards, once
            // ARCore's size estimate has settled.
            //
            // scaleToUnits is deliberately not used. It normalises the
            // model's *largest* dimension, so a tall structure would have its
            // height set to the image's width — a tower would come out as
            // wide as it is tall. The scale applied below divides by the
            // model's own measured width instead, which is the dimension the
            // reference photo actually corresponds to.
            val node = ModelNode(modelInstance = currentModel)

            // Not touchable, so SceneView's gesture detector never picks it
            // and has nothing to forward to the anchor node above. All three
            // sub-flags set explicitly and redundantly alongside isEditable,
            // same as anchorNode above — belt and suspenders against the
            // exact "model follows camera" bug the WORLD-LOCK comment above
            // describes, this time on the child node rather than the anchor.
            node.isEditable = false
            node.isPositionEditable = false
            node.isRotationEditable = false
            node.isScaleEditable = false
            node.isTouchable = false

            anchorNode.addChildNode(node)

            childNodes.add(anchorNode)

            placedNode = anchorNode
            modelNode = node
        }
    }

    // Manual adjustments are applied to the model's local transform rather
    // than by re-creating the anchor, so the world-space anchor stays put
    // while the visitor nudges the structure into place.
    LaunchedEffect(manualPosition, manualYaw, modelNode) {
        modelNode?.let { node ->
            node.position = manualPosition
            node.rotation = Rotation(0f, manualYaw, 0f)
        }
    }

    // Uniform scale for the model, from whichever basis the anchor set.
    //
    //  1. An explicit Model Size from the admin panel wins. It is a real
    //     measurement, where the camera's estimate is an estimate — and it is
    //     the only option for geospatial placement, which has no photo to
    //     measure against.
    //  2. Otherwise the model's own HEIGHT is matched to the physical height
    //     of the STRUCTURE inside the recognised photo — not the photo's
    //     full height, and not its width. ARCore only ever measures the
    //     whole printed/displayed photo; [ScaleBasis.structureHeightMeters]
    //     has already had the admin's structure-box fraction applied to it
    //     (see the anchor-creation block above), so the sky, ground, trees
    //     and margins the photo also shows never factor into the model's
    //     size. A photo saved before that box existed defaults to the full
    //     frame, matching this project's previous (whole-image) behaviour.
    //
    // Exactly one factor, applied to all three axes, so the model's authored
    // proportions are preserved rather than stretched or squashed — the
    // model naturally ends up as wide as it was modelled to be for that
    // height, even if that makes it wider than the structure in the photo or
    // wider than the current camera view. That is expected: a visitor can
    // step back to see all of it.
    LaunchedEffect(modelNode, scaleBasis, modelInstance) {

        val node = modelNode ?: return@LaunchedEffect
        val basis = scaleBasis ?: return@LaunchedEffect
        val instance = modelInstance ?: return@LaunchedEffect

        val explicitSize = (site?.arModelScale ?: 0.0).toFloat()
        val modelHeight = measureModelHeight(instance)
        val modelLongest = measureModelLongestSide(instance)

        val factor = when {
            explicitSize > 0f && modelLongest > 0.0001f -> explicitSize / modelLongest
            basis.structureHeightMeters != null && modelHeight > 0.0001f ->
                basis.structureHeightMeters / modelHeight
            else -> return@LaunchedEffect
        }

        // The pinch multiplier (manualScaleMultiplier, "Place It Myself"
        // only) is applied in the separate, lightweight effect just below
        // rather than here, so a live pinch gesture doesn't re-run
        // measureModelHeight()/measureModelLongestSide() on every tick.
        baseScaleFactor = factor

        measuredModelHeight = modelHeight
        lockedStructureHeight = basis.structureHeightMeters
    }

    // Combines the base factor above with the manual pinch multiplier.
    // Split out from the effect above so pinching only ever does this cheap
    // multiply-and-assign, never the model measurement. manualScaleMultiplier
    // stays at its default (1f) for Geospatial/Image placement, since only
    // the "Place It Myself" pinch gesture ever changes it — so this is a
    // no-op for those and never a second, competing writer of node.scale.
    LaunchedEffect(modelNode, baseScaleFactor, manualScaleMultiplier) {
        val node = modelNode ?: return@LaunchedEffect
        if (baseScaleFactor <= 0f) return@LaunchedEffect
        val finalFactor = baseScaleFactor * manualScaleMultiplier
        node.scale = Scale(finalFactor, finalFactor, finalFactor)
        appliedScaleFactor = finalFactor
    }

    // Builds the AugmentedImageDatabase and registers every reference photo
    // with ARCore, off the main thread.
    //
    // This used to happen inline inside sessionConfiguration below, which
    // ARScene calls synchronously on the thread that creates/resumes the AR
    // session — the main/UI thread. addImage() does real feature-extraction
    // work, a full pass over each photo's pixels, and with more than one
    // reference photo (or a large one) that easily runs past the 5 seconds
    // Android allows before declaring "Input dispatching timed out" and
    // killing the app with an ANR — exactly what on-device testing showed
    // right after the earlier native-crash fix stopped masking it.
    //
    // Deferring it to here, keyed on the session becoming available, moves
    // that work to Dispatchers.Default and hands the finished database to
    // the already-running session via session.configure() instead of
    // blocking session creation on it. Runs once per session: imageSetupDone
    // flips true at the end and short-circuits any re-trigger.
    LaunchedEffect(arCoreSession, referenceBitmaps, uiState.useImage) {

        val session = arCoreSession ?: return@LaunchedEffect
        if (!uiState.useImage || referenceBitmaps.isEmpty() || imageSetupDone) {
            return@LaunchedEffect
        }

        val (accepted, rejected, database) = withContext(Dispatchers.Default) {
            val db = AugmentedImageDatabase(session)
            val acceptedIds = mutableSetOf<String>()
            val rejectedLabels = mutableListOf<String>()

            for ((id, bitmap) in referenceBitmaps) {
                try {
                    db.addImage(id, bitmap)
                    acceptedIds += id
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    // ImageInsufficientQualityException is the usual case:
                    // not enough distinct detail to match. The other photos
                    // can still work.
                    val label = referencesById[id]?.label ?: id
                    Log.e("HistouryAR", "Reference photo \"$label\" rejected by ARCore", error)
                    rejectedLabels += label
                }
            }

            Triple(acceptedIds, rejectedLabels, db)
        }

        if (accepted.isNotEmpty()) {
            try {
                val liveConfig = session.config
                liveConfig.augmentedImageDatabase = database
                session.configure(liveConfig)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("HistouryAR", "Could not attach the image database to the AR session", error)
            }
        }

        registeredImageIds = accepted
        rejectedPhotoLabels = rejected
        imageDatabaseReady = accepted.isNotEmpty()
        imageSetupDone = true
    }

    // "Reset Placement" for "Place It Myself": detach whatever anchor/node
    // exists, clear every piece of local manual-placement state, and hand
    // control back to startManualPlacement() — the exact same ViewModel
    // entry point "Place it myself" itself uses — so the aiming grid/reticle
    // reappears and the next tap starts a completely fresh placement.
    val resetManualPlacement: () -> Unit = {
        anchor?.detach()
        placedNode?.let(childNodes::remove)
        anchor = null
        placedNode = null
        modelNode = null
        anchorCreated = false
        manualPosition = Position(0f, 0f, 0f)
        manualYaw = 0f
        manualScaleMultiplier = 1f
        baseScaleFactor = 0f
        groundReticlePose = null
        groundPlane = null
        reticleScreenPos = null
        groundGridScreenLines = emptyList()
        pendingPlaceTap = false
        viewModel.startManualPlacement()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { arViewSizePx = it }
    ) {

        ARScene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            childNodes = childNodes,

            planeRenderer = false,

            sessionConfiguration = { session, config ->

                // Placement normally comes from recognizing the structure or
                // from Geospatial, never from a visitor-selected horizontal
                // plane — but "Place It Myself" (PlacementSource.MANUAL) is
                // the fallback for when both of those fail, and it anchors
                // to a real detected floor/ground plane rather than a fixed
                // point in space. Plane finding costs a little CPU but runs
                // independently of image tracking and Geospatial, so leaving
                // it on for every session (rather than only once the manual
                // fallback is reached) means ARCore already has a head start
                // on detecting the ground by the time "Place it myself" is
                // tapped, instead of starting plane detection from zero.
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL

                // Geospatial only when this scene uses it: it costs power and
                // network, and asks Google's servers to localise the phone.
                val wantGeospatial = uiState.useGeospatial
                val geospatialSupported = wantGeospatial && runCatching {
                    session.isGeospatialModeSupported(Config.GeospatialMode.ENABLED)
                }.getOrDefault(false)

                config.geospatialMode = if (geospatialSupported) {
                    Config.GeospatialMode.ENABLED
                } else {
                    Config.GeospatialMode.DISABLED
                }

                geospatialActive = geospatialSupported
                geospatialUnsupported = wantGeospatial && !geospatialSupported

                // Autofocus materially improves image recognition — a
                // fixed-focus frame of a distant facade is often too soft for
                // feature matching to succeed.
                config.focusMode = Config.FocusMode.AUTO

                // See the studioLighting comment above. DISABLED does not mean
                // unlit: it leaves SceneView's default neutral environment in
                // place rather than replacing it with ARCore's estimate.
                config.lightEstimationMode = if (studioLighting) {
                    Config.LightEstimationMode.DISABLED
                } else {
                    Config.LightEstimationMode.ENVIRONMENTAL_HDR
                }

                // Depth is what makes the reconstruction read as a structure
                // standing in the scene rather than a decal painted over the
                // camera feed: without it the model draws in front of the
                // real walls, trees and people that should be hiding parts
                // of it.
                val supportsDepth = session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)

                config.depthMode = if (supportsDepth) {
                    Config.DepthMode.AUTOMATIC
                } else {
                    Config.DepthMode.DISABLED
                }

                depthSupported = supportsDepth

                // Every reference photo goes into one database, named by its
                // admin-panel id. ARCore then recognises whichever of them
                // the camera sees.
                //
                // Registered without a declared physical width: ARCore
                // estimates each photo's real size from the camera feed, so
                // nobody has to measure the structure with a tape. The
                // estimate needs the visitor to move the phone slightly
                // before it settles, which the width stabiliser below waits
                // for before placing anything.
                //
                // The actual database build (real feature-extraction work,
                // a full pass over each photo) does NOT happen here anymore.
                // sessionConfiguration runs synchronously on the thread that
                // creates/resumes the session — the main/UI thread — and
                // that work was slow enough to trip a genuine ANR on-device.
                // It's built off the main thread instead, once arCoreSession
                // is set below: see the LaunchedEffect keyed on arCoreSession
                // above the Box/ARScene call.
                if (!uiState.useImage) {
                    imageDatabaseReady = false
                    imageSetupDone = true
                }
            },

            // Configuring depthMode is only half of it. SceneView renders the
            // camera feed through ARCameraStream, and that stream ships with
            // isDepthOcclusionEnabled = false, so the occlusion material is
            // never applied unless it is switched on here. Set per frame
            // rather than once at creation because the flag is only known
            // after the session is configured; the setter is a no-op when the
            // value hasn't changed.
            onViewUpdated = {
                cameraStream?.isDepthOcclusionEnabled = depthSupported && occlusionEnabled
            },

            onSessionCreated = { session ->
                viewModel.onSessionCreated(session)
                arCoreSession = session
            },

            onSessionFailed = { error ->
                viewModel.onSessionFailed(
                    error.message ?: "AR couldn't start on this device."
                )
            },

            onSessionUpdated = { session, frame ->

                val nowMillis = System.currentTimeMillis()
                val elapsed = ((nowMillis - startedAtMillis) / 1000).toInt()

                viewModel.onSearchTick(elapsed)

                // ---------------------------------------------------- image

                // Every photo was rejected: image recognition can never fire.
                // Carry on with geospatial if it's set up, otherwise go
                // straight to the manual-placement offer.
                if (uiState.useImage && imageSetupDone && !imageDatabaseReady &&
                    !imageFailureReported
                ) {
                    imageFailureReported = true
                    val reason = "This site's reference photos don't have enough " +
                        "visible detail for the camera to recognize them."
                    if (uiState.useGeospatial) {
                        viewModel.onImageAnchoringUnavailable(reason)
                    } else if (uiState.stage == ArStage.SEARCHING) {
                        viewModel.onAnchorFailed(reason)
                    }
                }

                // getAllTrackables rather than getUpdatedTrackables: an image
                // tracked steadily doesn't necessarily appear in the
                // per-frame updated set, and losing sight of it needs to be
                // noticed too.
                val ourImages = if (imageDatabaseReady) {
                    runCatching {
                        session.getAllTrackables(AugmentedImage::class.java)
                            .filter { it.name in referencesById }
                    }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }

                // Only FULL_TRACKING counts. Under LAST_KNOWN_POSE ARCore is
                // extrapolating from an image it can no longer see.
                val fullyTracked = ourImages.filter {
                    it.trackingState == TrackingState.TRACKING &&
                        it.trackingMethod == AugmentedImage.TrackingMethod.FULL_TRACKING
                }

                // Stick with the photo the model is already anchored to while
                // it's in view, so two photos in frame don't fight.
                val trackedImage = fullyTracked.firstOrNull { it.name == anchoredImageId }
                    ?: fullyTracked.firstOrNull()

                // Width stabiliser, per photo.
                //
                // ARCore refines its size (and with it, distance) estimate
                // continuously for the first second or so. Anchoring on the
                // very first detection — which this screen used to do — fixed
                // the model at that early, often wrong, estimate. Now nothing
                // is placed until the estimate has held steady, and a photo
                // that keeps shifting still locks after a few seconds rather
                // than never.
                if (trackedImage != null) {

                    val name = trackedImage.name

                    if (name != stabilizerImageId) {
                        stabilizerImageId = name
                        stableReadings = 0
                        fullTrackingFrames = 0
                        relockFrames = 0
                        imageExtentX = 0f
                    }

                    fullTrackingFrames += 1

                    val reading = trackedImage.extentX
                    val locked = lockedWidths[name]

                    if (reading > 0.01f) {

                        if (locked == null) {

                            val previous = imageExtentX
                            stableReadings = if (
                                previous > 0.01f &&
                                kotlin.math.abs(reading - previous) / previous < SIZE_AGREEMENT
                            ) {
                                stableReadings + 1
                            } else {
                                0
                            }

                            if (stableReadings >= STABLE_READINGS_REQUIRED ||
                                fullTrackingFrames >= SIZE_SETTLE_FALLBACK_FRAMES
                            ) {
                                lockedWidths[name] = reading
                                lockedHeights[name] = trackedImage.extentZ
                            }
                        } else if (!anchorCreated) {

                            // Still searching and the estimate moved well away
                            // from the lock: the early lock was wrong. Adopt
                            // the new size before anything is placed. Once the
                            // model is placed its size never changes again —
                            // a rescale would read as the model moving.
                            relockFrames = if (kotlin.math.abs(reading - locked) / locked > SIZE_RELOCK_DIFFERENCE) {
                                relockFrames + 1
                            } else {
                                0
                            }

                            if (relockFrames >= SIZE_RELOCK_FRAMES) {
                                lockedWidths[name] = reading
                                lockedHeights[name] = trackedImage.extentZ
                                relockFrames = 0
                            }
                        }

                        imageExtentX = reading
                        imageExtentZ = trackedImage.extentZ
                    }
                }

                val trackedWidth = trackedImage?.let { lockedWidths[it.name] }
                val trackedHeight = trackedImage?.let { lockedHeights[it.name] }
                val trackedReference = trackedImage?.let { referencesById[it.name] }

                // ----------------------------------------------- geospatial

                if (geospatialUnsupported && !geoProblemReported) {
                    geoProblemReported = true
                    viewModel.onGeospatialUnavailable(
                        "This phone doesn't support placing AR by location."
                    )
                }

                var geoReadyToPlace = false
                var earthIsTracking = false
                var geoHorizontal = Double.MAX_VALUE
                val earth: Earth? = if (geospatialActive) {
                    runCatching { session.earth }.getOrNull()
                } else {
                    null
                }

                if (earth != null) {

                    val earthProblem = describeEarthProblem(earth.earthState)

                    if (earthProblem != null) {
                        geoDebug = earth.earthState.name
                        if (!geoProblemReported) {
                            geoProblemReported = true
                            viewModel.onGeospatialUnavailable(earthProblem)
                        }
                    } else if (earth.trackingState == TrackingState.TRACKING) {

                        val pose: GeospatialPose = earth.cameraGeospatialPose
                        geoHorizontal = pose.horizontalAccuracy
                        val headingError = pose.orientationYawAccuracy

                        if (earthTrackingSinceMillis == 0L) earthTrackingSinceMillis = nowMillis
                        earthIsTracking = true

                        // Earth is tracking. Deploy as soon as the fix is
                        // precise and has held for a moment (a single lucky
                        // frame isn't a fix). Because the model is never
                        // moved once placed, it's worth a few seconds' wait
                        // for a precise fix — but not forever: after
                        // GEO_FALLBACK_AFTER_MS of tracking, a usable fix is
                        // accepted so the model still deploys automatically
                        // in Intramuros' narrow streets.
                        val precise = geoHorizontal <= GEO_MAX_HORIZONTAL_ERROR_METERS &&
                            headingError <= GEO_MAX_HEADING_ERROR_DEGREES
                        val usable = geoHorizontal <= GEO_USABLE_HORIZONTAL_ERROR_METERS &&
                            headingError <= GEO_USABLE_HEADING_ERROR_DEGREES
                        val waitedLongEnough =
                            nowMillis - earthTrackingSinceMillis >= GEO_FALLBACK_AFTER_MS

                        geoGoodFrames = if (precise || (usable && waitedLongEnough)) {
                            geoGoodFrames + 1
                        } else {
                            0
                        }
                        geoReadyToPlace = geoGoodFrames >= GEO_GOOD_FRAMES_REQUIRED

                        geoDebug = String.format("±%.1f m, ±%.0f°", geoHorizontal, headingError)

                        // Throttled: the card only needs a couple of updates a
                        // second, and every update recomposes the overlay.
                        if (nowMillis - lastGeoStatusMillis > GEO_STATUS_INTERVAL_MS) {
                            lastGeoStatusMillis = nowMillis
                            viewModel.onGeoStatus(
                                GeoStatus(
                                    tracking = true,
                                    horizontalAccuracyMeters = geoHorizontal,
                                    headingAccuracyDegrees = headingError,
                                    goodEnough = geoReadyToPlace
                                )
                            )
                        }
                    } else {
                        geoGoodFrames = 0
                        earthTrackingSinceMillis = 0L
                        geoDebug = "localizing…"
                        if (nowMillis - lastGeoStatusMillis > GEO_STATUS_INTERVAL_MS) {
                            lastGeoStatusMillis = nowMillis
                            viewModel.onGeoStatus(GeoStatus(tracking = false))
                        }
                    }
                }

                // ------------------------------------------------ placement

                // PLACEMENT HAPPENS EXACTLY ONCE.
                //
                // Every anchor below is a world anchor (session.createAnchor
                // or an Earth terrain anchor), created a single time. The
                // model is a child of that anchor's node and nothing moves
                // it afterwards, so walking, turning or tilting the phone
                // only changes the camera view — the structure stays where
                // it was put.
                //
                // This is the camera-following fix. The model used to be
                // re-anchored while the visitor moved (image pose
                // "corrections", geospatial re-resolves, a geospatial-to-
                // photo hand-over), and earlier still it was attached to the
                // AugmentedImage trackable itself. A photo registered
                // without a known physical size has its distance estimated
                // from parallax, so as the phone moved that estimate slid
                // along the line of sight — and the model slid with it,
                // which looks exactly like it is following the camera.

                // 1) Image recognition — only for scenes configured to use it
                //    (the image database is empty otherwise, so trackedImage
                //    is always null for geospatial-only scenes).
                if (!anchorCreated && trackedImage != null && trackedWidth != null &&
                    trackedHeight != null &&
                    trackedReference != null && site != null &&
                    viewModel.beginAnchoring(PlacementSource.IMAGE)
                ) {
                    try {
                        anchor = session.createAnchor(
                            imageAnchorPose(trackedImage, trackedReference)
                        )

                        anchorCreated = true
                        anchorSource = PlacementSource.IMAGE
                        anchoredImageId = trackedImage.name
                        // The model's target size: not the photo's full
                        // physical height, but the slice of it the admin
                        // marked as the structure itself.
                        scaleBasis = ScaleBasis(
                            structureHeightMeters =
                                trackedHeight * trackedReference.structureHeightFraction
                        )

                        viewModel.onAnchorPlaced(PlacementSource.IMAGE, trackedReference.label)
                    } catch (error: Exception) {
                        Log.e("HistouryAR", "Image anchor creation failed", error)
                        anchorCreated = false
                        viewModel.onAnchorFailed(
                            error.message?.takeIf { it.isNotBlank() }
                                ?: "The structure was recognized but the model " +
                                "couldn't be attached to it."
                        )
                    }
                }

                // 2) Geospatial, in two overlapping steps.
                //
                //  a) As soon as Earth has ANY fix, ask Google for the terrain
                //     anchor (the ground height at the saved spot). That
                //     network round trip used to start only after the
                //     location was precise, adding its whole wait on top.
                //  b) When the location is precise enough, place the model
                //     on that anchor — usually already resolved by then, so
                //     it appears immediately.
                //
                // Same placement accuracy as before: the model still only
                // appears once the fix is good, and step (b) reads the
                // anchor's pose at that moment (a terrain anchor's pose
                // improves along with the location estimate).
                if (earth != null && site != null && earthIsTracking && !anchorCreated &&
                    !terrainPending && earlyTerrainAnchor == null &&
                    !uiState.placementIsManual &&
                    nowMillis - lastTerrainRequestMillis > TERRAIN_RETRY_INTERVAL_MS
                ) {
                    terrainPending = true
                    lastTerrainRequestMillis = nowMillis
                    resolveTerrainAnchor(earth, site) { resolved ->
                        terrainPending = false
                        if (resolved == null) return@resolveTerrainAnchor // retried later
                        // Something else placed the model meanwhile (a photo
                        // in "both" mode, or the visitor by hand): not needed.
                        // Read live: this callback lands frames later.
                        if (anchorCreated || viewModel.uiState.value.placementIsManual) {
                            resolved.detach()
                        } else {
                            earlyTerrainAnchor = resolved
                        }
                    }
                }

                val readyAnchor = earlyTerrainAnchor
                if (readyAnchor != null && !anchorCreated && geoReadyToPlace &&
                    uiState.stage == ArStage.SEARCHING &&
                    viewModel.beginAnchoring(PlacementSource.GEOSPATIAL)
                ) {
                    earlyTerrainAnchor = null // now owned by the placement
                    anchor = readyAnchor
                    anchorCreated = true
                    anchorSource = PlacementSource.GEOSPATIAL
                    scaleBasis = ScaleBasis(structureHeightMeters = null)
                    viewModel.onAnchorPlaced(PlacementSource.GEOSPATIAL)
                }

                // Placed some other way: release the unused terrain anchor.
                if (anchorCreated && earlyTerrainAnchor != null) {
                    earlyTerrainAnchor?.detach()
                    earlyTerrainAnchor = null
                }

                // WORLD-LOCK, part 2: pin a geospatial placement.
                //
                // A terrain anchor's pose is expressed through ARCore's
                // Earth-to-local transform, and ARCore keeps re-estimating
                // that transform — especially its heading — as the visitor
                // walks and turns. A 5° heading correction moves a structure
                // 20 m away by almost 2 m sideways, every time it happens.
                // Applied frame by frame, that reads as the model sliding
                // around with the phone.
                //
                // So the node stays attached to the terrain anchor (its
                // tracking state still drives visibility), but its pose is
                // taken ONCE, the first frame the anchor is tracking, and then
                // held fixed in ARCore's local world frame — the same frame
                // an ordinary world anchor lives in. From then on only the
                // camera moves.
                if (anchorSource == PlacementSource.GEOSPATIAL && !geoPosePinned) {
                    val node = placedNode
                    val geoAnchor = anchor
                    if (node != null && geoAnchor != null &&
                        geoAnchor.trackingState == TrackingState.TRACKING
                    ) {
                        node.pose = geoAnchor.pose
                        node.updateAnchorPose = false
                        // Keep it on screen if Earth tracking briefly pauses;
                        // the pinned pose doesn't depend on it any more.
                        node.visibleTrackingStates = setOf(
                            TrackingState.TRACKING,
                            TrackingState.PAUSED
                        )
                        geoPosePinned = true
                    }
                }

                // Is whatever placed the model still being tracked?
                when (anchorSource) {
                    PlacementSource.IMAGE -> {
                        // LAST_KNOWN_POSE counts as "still tracking" here: once the
                        // model is anchored, its position no longer depends on the
                        // image at all, and ARCore drops to LAST_KNOWN_POSE
                        // routinely — a person walking past the structure, a
                        // passing shadow, or someone stepping close enough that the
                        // photo leaves the frame all do this even though nothing is
                        // actually wrong. Only PAUSED/absent readings mean
                        // recognition has genuinely dropped, and even then only
                        // after a short grace period (TRACKING_LOST_GRACE_FRAMES).
                        val stillTracking = ourImages.any { it.trackingState == TrackingState.TRACKING }

                        trackingLostFrames = if (stillTracking) 0 else trackingLostFrames + 1

                        viewModel.onPlacementTracking(trackingLostFrames < TRACKING_LOST_GRACE_FRAMES)
                    }
                    PlacementSource.GEOSPATIAL -> viewModel.onPlacementTracking(
                        earth?.trackingState == TrackingState.TRACKING &&
                            anchor?.trackingState == TrackingState.TRACKING
                    )
                    else -> Unit
                }

                // Advance any animation the GLB carries.
                //
                // Loading a model does not play it — gltfio parses the
                // animation tracks but nothing samples them, so an animated
                // asset renders frozen on its first frame until something
                // drives the animator. This is that something.
                //
                // Every clip is applied each frame rather than just the
                // first. A smoke rig is naturally authored as several
                // independent plumes, and since each clip drives its own set
                // of nodes they layer rather than fight. Clips are looped on
                // their own durations, so plumes authored at different
                // lengths drift out of sync with each other over time, which
                // is what stops the whole effect pulsing in unison.
                if (animationCount > 0 && animationStartNanos > 0L) {
                    runCatching {
                        val animator = modelInstance?.animator

                        if (animator != null) {

                            val elapsedSeconds =
                                (System.nanoTime() - animationStartNanos) / 1_000_000_000f

                            for (index in 0 until animationCount) {
                                val duration = animator.getAnimationDuration(index)
                                val localTime = if (duration > 0f) {
                                    elapsedSeconds % duration
                                } else {
                                    0f
                                }
                                animator.applyAnimation(index, localTime)
                            }

                            // Only does anything for skinned meshes, but it
                            // is cheap and skipping it silently breaks any
                            // rigged element added later.
                            animator.updateBoneMatrices()
                        }
                    }
                }

                // Distance is measured anchor-to-camera rather than read off
                // the model, because it stays meaningful even when the model
                // never became visible — which is the whole point of this
                // readout.
                val cameraTranslation = frame.camera.pose.translation
                val anchorTranslation = anchor?.pose?.translation

                val distance = if (anchorTranslation != null) {
                    val dx = anchorTranslation[0] - cameraTranslation[0]
                    val dy = anchorTranslation[1] - cameraTranslation[1]
                    val dz = anchorTranslation[2] - cameraTranslation[2]
                    sqrt(dx * dx + dy * dy + dz * dz)
                } else {
                    0f
                }

                val debugReference = anchoredImageId?.let { referencesById[it] }

                debug = ArPlacementDebug(
                    modelLoaded = modelInstance != null,
                    modelBytes = modelBytes,
                    anchorAttached = anchor != null,
                    nodeInScene = placedNode != null,
                    distanceMeters = distance,
                    appliedScale = appliedScaleFactor.toDouble(),
                    structureHeightMeters = (lockedStructureHeight ?: 0f).toDouble(),
                    modelHeightUnits = measuredModelHeight.toDouble(),
                    scaleLocked = trackedHeight != null || lockedStructureHeight != null,
                    extentX = trackedImage?.extentX ?: imageExtentX,
                    extentZ = trackedImage?.extentZ ?: imageExtentZ,
                    offsetRight = debugReference?.offsetRightMeters ?: 0.0,
                    offsetUp = debugReference?.offsetUpMeters ?: 0.0,
                    offsetForward = debugReference?.offsetForwardMeters ?: 0.0,
                    occlusionOn = depthSupported && occlusionEnabled,
                    studioLighting = studioLighting,
                    materialCount = runCatching {
                        modelInstance?.materialInstances?.size ?: 0
                    }.getOrDefault(0),
                    animationCount = animationCount,
                    trackingMethod = trackedImage?.trackingMethod?.name ?: "none",
                    placedBy = when (anchorSource) {
                        PlacementSource.IMAGE -> "photo: ${debugReference?.label ?: "?"}"
                        PlacementSource.GEOSPATIAL -> "geospatial"
                        PlacementSource.MANUAL -> "by hand"
                        null -> if (terrainPending) "resolving…" else "not yet"
                    },
                    photos = if (uiState.useImage || references.isNotEmpty()) {
                        buildString {
                            append("${registeredImageIds.size} of ${references.size} usable")
                            if (rejectedPhotoLabels.isNotEmpty()) {
                                append(" (rejected: ${rejectedPhotoLabels.joinToString()})")
                            }
                        }
                    } else {
                        "not used"
                    },
                    geospatial = if (geospatialActive) geoDebug else "off",
                    explicitModelSize = site?.arModelScale ?: 0.0                )

                // "Place It Myself": aim a screen-center hit test at a
                // tracked horizontal plane every frame, so the grid/reticle
                // overlay always shows exactly where a tap would place the
                // structure, then place it there when the visitor taps — a
                // real ARCore anchor on the detected floor, not a fixed
                // point in space the way this used to work.
                if (uiState.manualPlacementUnlocked) {

                    if (anchor == null) {

                        // Aiming phase. viewMatrix/projMatrix are reused
                        // scratch buffers (declared above, near the other
                        // manual-placement state), not fresh arrays per
                        // frame.
                        val viewSize = arViewSizePx
                        val hitPose = if (viewSize.width > 0 && viewSize.height > 0) {

                            frame.camera.getViewMatrix(scratchViewMatrix, 0)
                            frame.camera.getProjectionMatrix(
                                scratchProjMatrix, 0, 0.01f, 100f
                            )

                            val centerX = viewSize.width / 2f
                            val centerY = viewSize.height / 2f

                            val hit = runCatching { frame.hitTest(centerX, centerY) }
                                .getOrDefault(emptyList())
                                .firstOrNull { result ->
                                    val plane = result.trackable as? Plane
                                    plane != null &&
                                        plane.trackingState == TrackingState.TRACKING &&
                                        plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
                                        plane.isPoseInPolygon(result.hitPose)
                                }

                            groundPlane = hit?.trackable as? Plane
                            hit?.hitPose
                        } else {
                            groundPlane = null
                            null
                        }

                        groundReticlePose = hitPose

                        if (hitPose != null && viewSize.width > 0) {
                            reticleScreenPos = worldToScreen(
                                hitPose.translation,
                                scratchViewMatrix,
                                scratchProjMatrix,
                                viewSize.width,
                                viewSize.height
                            )
                            groundGridScreenLines = buildGroundGridScreenLines(
                                hitPose,
                                scratchViewMatrix,
                                scratchProjMatrix,
                                viewSize.width,
                                viewSize.height
                            )
                        } else {
                            reticleScreenPos = null
                            groundGridScreenLines = emptyList()
                        }

                        // The tap itself is raised from the gesture layer
                        // (ArOverlay's onTapToPlace), which only sets a flag
                        // — every actual Session/Anchor call has to happen
                        // here, on the AR frame thread, same as every other
                        // anchor in this file.
                        if (pendingPlaceTap) {
                            pendingPlaceTap = false
                            if (hitPose != null) {
                                try {
                                    anchorSource = PlacementSource.MANUAL
                                    // Hand placement has nothing to measure
                                    // the size against, so it uses the
                                    // admin's Model Size when set and the
                                    // model's authored size otherwise.
                                    scaleBasis = ScaleBasis(structureHeightMeters = null)
                                    anchor = session.createAnchor(hitPose)
                                    anchorCreated = true
                                    groundGridScreenLines = emptyList()
                                } catch (error: Exception) {
                                    Log.e("HistouryAR", "Manual anchor creation failed", error)
                                }
                            }
                        }
                    } else {
                        // Placed but still unlocked: no grid — direct
                        // manipulation (drag/pinch/twist) takes over — but
                        // the center indicator stays up, now tracking the
                        // model's actual current ground point (the anchor
                        // plus whatever local X/Z offset dragging has
                        // applied) rather than the original aim point, so it
                        // stays accurate as the visitor repositions it.
                        groundGridScreenLines = emptyList()

                        val viewSize = arViewSizePx
                        val anchorPose = anchor?.pose

                        reticleScreenPos = if (anchorPose != null && viewSize.width > 0) {
                            frame.camera.getViewMatrix(scratchViewMatrix, 0)
                            frame.camera.getProjectionMatrix(
                                scratchProjMatrix, 0, 0.01f, 100f
                            )
                            val groundPoint = anchorPose.transformPoint(
                                floatArrayOf(manualPosition.x, 0f, manualPosition.z)
                            )
                            worldToScreen(
                                groundPoint,
                                scratchViewMatrix,
                                scratchProjMatrix,
                                viewSize.width,
                                viewSize.height
                            )
                        } else {
                            null
                        }
                    }
                } else if (reticleScreenPos != null || groundGridScreenLines.isNotEmpty()) {
                    // Locked, or manual mode never started: nothing to aim
                    // at, so hide the overlay rather than leave it frozen on
                    // its last position.
                    reticleScreenPos = null
                    groundGridScreenLines = emptyList()
                    groundReticlePose = null
                    groundPlane = null
                }
            }
        )

        // Detaching anchors here, after the ARScene(...) call above rather
        // than at the top of this composable, is deliberate and load-bearing.
        //
        // Compose tears down DisposableEffects in the reverse of the order
        // they entered composition. ARScene closes the ARCore Session as
        // part of its own internal cleanup when it leaves composition, and
        // Anchor.detach() crashes the process natively — a hard SIGSEGV, not
        // a catchable exception — if the Session it belongs to has already
        // been closed. This DisposableEffect was previously declared before
        // ARScene(...), which made it enter composition first and therefore
        // (LIFO) get torn down *after* ARScene's own session-closing cleanup
        // had already run: every anchor.detach() call below was reaching
        // into an already-closed session. That produced the exact repeating
        // native crash seen in on-device tombstones, always inside this
        // composable's onDispose. Declaring it after ARScene(...) makes it
        // the more-recently-entered effect, so it now disposes first —
        // anchors are detached while the session is still alive, and only
        // then does ARScene close it. Do not move this back above
        // ARScene(...).
        DisposableEffect(Unit) {
            onDispose {
                placedNode?.let(childNodes::remove)
                anchor?.detach()
                earlyTerrainAnchor?.detach()
            }
        }

        // "Place It Myself" ground grid + center reticle. Purely decorative
        // — a plain Canvas with no pointerInput of its own, so it never
        // intercepts the gestures the Box below (or ArOverlay's buttons)
        // need to receive. Hidden automatically whenever there's nothing to
        // show: both lists/positions are cleared in onSessionUpdated the
        // moment manual placement isn't active.
        PlacementGuideOverlay(
            reticle = reticleScreenPos,
            gridLines = groundGridScreenLines
        )

        // Compass, only while there's an active placement decision to make —
        // gone the instant the structure is locked, so it never lingers over
        // the finished view.
        if (uiState.manualPlacementUnlocked) {
            CompassIndicator(
                headingDegrees = rememberCompassHeading(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            )
        }

        if (showSiteInfo) {
            site?.let {
                ArSiteInfoSheet(
                    site = it,
                    content = uiState.siteContent,
                    selectedTab = uiState.infoTab,
                    onTabSelected = { tab -> viewModel.onInfoTabSelected(tab) },
                    onDismiss = { onShowSiteInfoChange(false) }
                )
            }
        }

        ArOverlay(
            uiState = uiState,
            isCapturing = isCapturing,
            debug = debug,
            showDebug = showDebug,
            onToggleDebug = { showDebug = !showDebug },
            occlusionEnabled = occlusionEnabled,
            onToggleOcclusion = { occlusionEnabled = !occlusionEnabled },
            onShowInfo = { onShowSiteInfoChange(true) },
            studioLighting = studioLighting,
            onToggleLighting = {
                ArLightingPreference.useNeutralLighting = !studioLighting
            },
            onCapture = onCapture,
            onRetry = viewModel::retrySearch,
            onStartManual = viewModel::startManualPlacement,
            onLockManual = viewModel::lockManualPlacement,
            onUnlockManual = viewModel::unlockManualPlacement,
            onResetManual = resetManualPlacement,
            manualAnchorPlaced = anchor != null,
            onTapToPlace = {
                // Consumed on the next AR frame, in onSessionUpdated — see
                // the comment on pendingPlaceTap's declaration for why this
                // can't create the anchor directly from here.
                pendingPlaceTap = true
            },
            onDrag = { dx, dy ->
                // Horizontal drag slides the structure left and right;
                // vertical drag pushes it away and pulls it closer. Both in
                // the anchor's local frame, which is levelled and yawed to
                // face the visitor, so the mapping stays intuitive as they
                // walk around.
                manualPosition = Position(
                    manualPosition.x + dx * MANUAL_DRAG_METERS_PER_PIXEL,
                    manualPosition.y,
                    manualPosition.z + dy * MANUAL_DRAG_METERS_PER_PIXEL
                )
            },
            onNudgeHeight = { delta ->
                manualPosition = Position(
                    manualPosition.x,
                    manualPosition.y + delta,
                    manualPosition.z
                )
            },
            onScale = { zoomFactor ->
                // Multiplicative and accumulated, never reset mid-gesture:
                // each detectTransformGestures tick reports the zoom SINCE
                // THE LAST tick (e.g. 1.02 = 2% bigger since a moment ago),
                // so multiplying it into the running total is what makes a
                // long, slow pinch add up smoothly instead of jumping.
                manualScaleMultiplier = (manualScaleMultiplier * zoomFactor)
                    .coerceIn(MANUAL_SCALE_MIN, MANUAL_SCALE_MAX)
            },
            onRotate = { deltaDegrees ->
                // Same accumulation principle as scale: deltaDegrees is the
                // rotation SINCE THE LAST tick, added onto the running yaw
                // rather than replacing it, so the model tracks the twist
                // gesture continuously and simply stays at whatever angle it
                // reaches when the fingers lift — no snapping, no reset.
                //
                // If this reads as turning the wrong way once tested
                // on-device, flip the sign here — this is the one line that
                // decides the direction.
                manualYaw = (manualYaw + deltaDegrees) % 360f
            }
        )
    }
}

// ------------------------------------------------ "Place It Myself" guides

/**
 * The ground grid and center reticle for manual placement, drawn as a plain
 * 2D overlay from screen-space points already computed in onSessionUpdated
 * (see worldToScreen/buildGroundGridScreenLines). No pointerInput, so it
 * never competes with the gesture surface or the control cards for touches.
 */
@Composable
private fun PlacementGuideOverlay(
    reticle: Offset?,
    gridLines: List<Pair<Offset, Offset>>
) {

    if (reticle == null && gridLines.isEmpty()) return

    // Resolved here, in the composable body, and captured by the draw
    // lambda below rather than read inside it: AccentGold is a
    // @Composable/@ReadOnlyComposable property (it comes from
    // LocalHistouryColors.current), and Canvas's draw lambda is a plain
    // DrawScope.() -> Unit — not itself a composable context — so reading
    // a @Composable color getter directly inside it fails to compile.
    val centerDotColor = AccentGold

    ComposeCanvas(modifier = Modifier.fillMaxSize()) {

        gridLines.forEach { (start, end) ->
            drawLine(
                color = Color.White.copy(alpha = 0.35f),
                start = start,
                end = end,
                strokeWidth = 2f
            )
        }

        reticle?.let { center ->

            // Outer ring, a small solid center dot, and four short crosshair
            // ticks — a plain, minimal "target" reticle rather than anything
            // elaborate, so it reads instantly as "this exact point" without
            // competing visually with the camera feed behind it.
            drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                radius = 26f,
                center = center,
                style = Stroke(width = 3f)
            )

            drawCircle(
                color = centerDotColor,
                radius = 6f,
                center = center
            )

            val tickInner = 14f
            val tickOuter = 30f
            listOf(
                Offset(-1f, 0f), Offset(1f, 0f), Offset(0f, -1f), Offset(0f, 1f)
            ).forEach { direction ->
                drawLine(
                    color = Color.White.copy(alpha = 0.9f),
                    start = Offset(
                        center.x + direction.x * tickInner,
                        center.y + direction.y * tickInner
                    ),
                    end = Offset(
                        center.x + direction.x * tickOuter,
                        center.y + direction.y * tickOuter
                    ),
                    strokeWidth = 3f
                )
            }
        }
    }
}

/**
 * Reads the device's rotation-vector sensor and returns a smoothed compass
 * heading in degrees (0 = magnetic north, clockwise), independent of ARCore:
 * Geospatial (the one placement mode with a true-north-referenced pose) is
 * exactly what's unavailable whenever "Place It Myself" is needed, so the
 * compass has to come from the phone's own magnetometer/accelerometer fusion
 * instead. Registers the listener only while composed and unregisters it on
 * dispose, same lifecycle pattern as any other Android sensor/callback used
 * from Compose in this file.
 */
@Composable
private fun rememberCompassHeading(): Float {

    val context = LocalContext.current
    var heading by remember { mutableStateOf(0f) }

    DisposableEffect(Unit) {

        val sensorManager = context.getSystemService(android.content.Context.SENSOR_SERVICE)
            as? SensorManager
        val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        val listener = object : SensorEventListener {

            private val rotationMatrix = FloatArray(9)
            private val orientation = FloatArray(3)

            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientation)
                val azimuthDegrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
                val normalized = (azimuthDegrees + 360f) % 360f
                // Light smoothing so ordinary sensor noise doesn't make the
                // needle twitch, without adding enough lag to feel laggy
                // behind an actual turn of the phone.
                heading = smoothAngle(heading, normalized, 0.2f)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        if (sensorManager != null && rotationSensor != null) {
            sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        }

        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    return heading
}

/** Shortest-path angular interpolation, so e.g. 359° -> 1° eases forward
 *  through 0° rather than spinning the long way around through 180°. */
private fun smoothAngle(current: Float, target: Float, factor: Float): Float {
    val delta = (target - current + 540f) % 360f - 180f
    return (current + delta * factor + 360f) % 360f
}

/**
 * Small "N/E/S/W" compass rose. The card (the four letters and their tick
 * marks) rotates by -headingDegrees — the standard Android compass formula,
 * the same one behind essentially every rotating-card compass view — while
 * a small fixed pointer at the top of the (non-rotating) disc always marks
 * "the direction the phone is currently facing." Whichever letter the
 * pointer currently sits over names that real-world direction. Letters
 * rotating with the card is normal for this style of compass (a physical
 * compass card does the same) and keeps the whole thing to one simple,
 * easy-to-verify rotation rather than per-letter counter-rotation math.
 */
@Composable
private fun CompassIndicator(headingDegrees: Float, modifier: Modifier = Modifier) {

    Box(
        modifier = modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.4f))
            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
        contentAlignment = Alignment.Center
    ) {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = -headingDegrees }
        ) {
            Text(
                text = "N",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp)
            )
            Text(
                text = "S",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 9.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)
            )
            Text(
                text = "W",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 9.sp,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp)
            )
            Text(
                text = "E",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 9.sp,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)
            )
        }

        // Fixed pointer — never rotates — marking the phone's current
        // facing direction, i.e. whatever letter sits under it right now.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 2.dp)
                .size(width = 6.dp, height = 8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(AccentGold)
        )
    }
}

// ----------------------------------------------------------------- overlay

@Composable
private fun ArOverlay(
    uiState: ArUiState,
    isCapturing: Boolean,
    debug: ArPlacementDebug?,
    showDebug: Boolean,
    onToggleDebug: () -> Unit,
    occlusionEnabled: Boolean,
    onToggleOcclusion: () -> Unit,
    onShowInfo: () -> Unit,
    studioLighting: Boolean,
    onToggleLighting: () -> Unit,
    onCapture: () -> Unit,
    onRetry: () -> Unit,
    onStartManual: () -> Unit,
    onLockManual: () -> Unit,
    onUnlockManual: () -> Unit,
    onResetManual: () -> Unit,
    manualAnchorPlaced: Boolean,
    onTapToPlace: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onNudgeHeight: (Float) -> Unit,
    onScale: (Float) -> Unit,
    onRotate: (Float) -> Unit
) {

    // The whole overlay drops to zero alpha for the shutter frame rather
    // than being removed from composition: unmounting it would tear down the
    // drag gesture and the debug panel's state, and the visitor would come
    // back to a reset interface after every photo.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = if (isCapturing) 0f else 1f }
    ) {

        // Gesture surface, live only while the visitor is aligning by hand.
        // Placed under the control cards so buttons still receive taps.
        //
        // Two different gestures share this one surface, keyed on whether
        // the structure has been placed yet: before placement, a plain tap
        // (anywhere — the reticle/grid already show where it'll land) drops
        // it on the currently aimed-at surface; once placed, the surface
        // switches to detectTransformGestures, which reports pan, pinch-zoom
        // and two-finger rotation together from ongoing multi-touch — the
        // combination this needs for smooth, simultaneous move/scale/rotate
        // without any hand-rolled touch-tracking math. Keying pointerInput
        // on manualAnchorPlaced restarts the gesture detector coroutine
        // exactly when the surface needs to switch from one gesture type to
        // the other.
        if (uiState.manualPlacementUnlocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(manualAnchorPlaced) {
                        if (manualAnchorPlaced) {
                            detectTransformGestures { _, pan, zoom, rotation ->
                                if (pan.x != 0f || pan.y != 0f) onDrag(pan.x, pan.y)
                                if (zoom != 1f) onScale(zoom)
                                if (rotation != 0f) onRotate(rotation)
                            }
                        } else {
                            detectTapGestures { onTapToPlace() }
                        }
                    }
            )
        } else {
            // Touch shield — same position, same "buttons on top still get
            // their taps" pattern as the gesture surface above, just with
            // nothing behind it but a swallowed touch.
            //
            // Every AR node already has isPositionEditable/isTouchable set
            // false (see the WORLD-LOCK comments on anchorNode/modelNode
            // where they're placed), which should be enough on its own. This
            // is the second, independent layer: whenever nothing on screen
            // is meant to be draggable — a locked structure, or Geospatial/
            // Image placement, which never unlock at all — a stray touch
            // (a thumb resting on the glass while walking closer, exactly
            // the scenario that used to drag the structure along with the
            // camera) is caught and discarded here, in Compose, before it
            // can ever reach the ARSceneView or any node beneath it.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        // Explicit consume-everything loop rather than an
                        // empty-bodied gesture detector, so there's no
                        // dependence on some other detector's internal
                        // consumption behavior: every change, on every
                        // pointer, every frame of the gesture, is consumed
                        // here directly.
                        awaitEachGesture {
                            do {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                        }
                    }
            )
        }

        AnimatedVisibility(
            visible = uiState.stage == ArStage.SEARCHING ||
                uiState.stage == ArStage.ANCHORING,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 90.dp, start = 20.dp, end = 20.dp)
        ) {
            SearchingCard(uiState = uiState)
        }

        AnimatedVisibility(
            visible = uiState.stage == ArStage.TRACKING_LOST,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 90.dp, start = 20.dp, end = 20.dp)
        ) {
            HintCard(
                title = if (uiState.placedBy == PlacementSource.GEOSPATIAL) {
                    "Location tracking paused"
                } else {
                    "Lost sight of the structure"
                },
                body = if (uiState.placedBy == PlacementSource.GEOSPATIAL) {
                    "The model is still where it was placed. Point the camera " +
                        "at the surrounding buildings to keep it accurate."
                } else {
                    "The model is still where it was last seen. Point the " +
                        "camera back at the structure to re-align it."
                }
            )
        }

        AnimatedVisibility(
            visible = uiState.stage == ArStage.NOT_RECOGNIZED,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 90.dp, start = 20.dp, end = 20.dp)
        ) {
            HintCard(
                title = if (uiState.useImage) {
                    "Couldn't recognize the structure"
                } else {
                    "Couldn't find your exact location"
                },
                body = uiState.unavailableReason.ifBlank {
                    if (uiState.useImage) {
                        "Stand facing the structure so it fills most of the screen, " +
                            "with nothing blocking it. Strong shadow, glare or a " +
                            "steep angle can all prevent a match."
                    } else {
                        "Your location couldn't be pinned down precisely enough. " +
                            "Step into a more open spot and point the camera at " +
                            "nearby buildings, then try again."
                    }
                },
                actionLabel = "Try again",
                onAction = onRetry,
                secondaryActionLabel = "Place it myself",
                onSecondaryAction = onStartManual
            )
        }

        AnimatedVisibility(
            visible = uiState.stage == ArStage.MANUAL_ALIGNING,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 90.dp, start = 20.dp, end = 20.dp)
        ) {
            ManualAlignCard(
                hasPlaced = manualAnchorPlaced,
                onLock = onLockManual,
                onReset = onResetManual,
                onNudgeHeight = onNudgeHeight
            )
        }

        AnimatedVisibility(
            visible = uiState.stage == ArStage.PLACED && uiState.placementIsManual,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 90.dp, start = 20.dp, end = 20.dp)
        ) {
            HintCard(
                title = "Placed by hand",
                body = "This structure was positioned manually, so it may not " +
                    "line up exactly with the real one.",
                actionLabel = "Unlock to reposition",
                onAction = onUnlockManual
            )
        }

        // Explicit resize/rotate buttons for "Place It Myself", alongside
        // (not instead of) the pinch/twist gestures on the manipulate
        // surface above — some people find precise button taps easier than
        // a two-finger gesture, especially for fine adjustments. Bottom
        // placement, mirroring the shutter's spot once the structure is
        // placed: the two never show at once (this is MANUAL_ALIGNING only,
        // the shutter is PLACED/TRACKING_LOST only), so they can share the
        // same reserved space without a layout conflict.
        AnimatedVisibility(
            visible = uiState.stage == ArStage.MANUAL_ALIGNING && manualAnchorPlaced,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 44.dp)
        ) {
            ManualScaleRotateBar(
                onScaleDown = { onScale(1f / MANUAL_SCALE_STEP_FACTOR) },
                onScaleUp = { onScale(MANUAL_SCALE_STEP_FACTOR) },
                onRotateLeft = { onRotate(-MANUAL_ROTATE_STEP_DEGREES) },
                onRotateRight = { onRotate(MANUAL_ROTATE_STEP_DEGREES) }
            )
        }

        // Shutter, centred on its own. The info shortcut used to sit beside
        // it, balanced by an invisible spacer on the other side — a fragile
        // trick that only stayed centred as long as the label under the Info
        // icon never wrapped. It now lives in the top-right corner, mirroring
        // the back button, which is both simpler and reads less awkwardly:
        // two small round controls bookend the top of the screen the way
        // they do in most camera apps, and the shutter row is just the
        // shutter.
        //
        // Visible only once the model is PLACED. Offering it earlier would
        // mean a photo of an empty facade. TRACKING_LOST counts as placed
        // here: ARCore drops to LAST_KNOWN_POSE constantly in normal use —
        // step close enough to look at the model and the reference photo
        // leaves the frame — and gating on PLACED alone made the shutter
        // blink out exactly when a visitor was near enough to want it.
        AnimatedVisibility(
            visible = uiState.stage == ArStage.PLACED ||
                uiState.stage == ArStage.TRACKING_LOST,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 44.dp)
        ) {
            CaptureButton(
                isSaving = uiState.isSavingCapture,
                onClick = onCapture
            )
        }

        // Info shortcut, mirroring the back button in the opposite corner.
        AnimatedVisibility(
            visible = uiState.stage == ArStage.PLACED ||
                uiState.stage == ArStage.TRACKING_LOST,
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            InfoButton(onClick = onShowInfo)
        }

        // Diagnostic chip. Deliberately small and unlabelled-looking rather
        // than hidden behind a build flag: the failure it explains only
        // reproduces in front of the real structure, on a release build, in
        // the hands of whoever is doing the site walk.
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 90.dp, start = 20.dp)
        ) {
            Column {

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onToggleDebug
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (showDebug) "Hide placement info" else "Placement info",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }

                if (showDebug && debug != null) {

                    Spacer(Modifier.height(6.dp))

                    Column(
                        modifier = Modifier
                            .widthIn(max = 250.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.72f))
                            .padding(12.dp)
                    ) {

                        DebugRow("Model loaded", if (debug.modelLoaded) "yes" else "NO")
                        DebugRow("Model file", "${debug.modelBytes / 1024} KB")
                        DebugRow("Anchor", if (debug.anchorAttached) "attached" else "NO")
                        DebugRow("Placed by", debug.placedBy)
                        DebugRow("Photos", debug.photos)
                        DebugRow("Geospatial", debug.geospatial)
                        DebugRow(
                            "Model size",
                            if (debug.explicitModelSize > 0) {
                                String.format("%.1f m (admin)", debug.explicitModelSize)
                            } else {
                                "from photo"
                            }
                        )
                        DebugRow("Node in scene", if (debug.nodeInScene) "yes" else "NO")
                        DebugRow("Distance", String.format("%.2f m", debug.distanceMeters))
                        // The three numbers that now explain the size, in
                        // the order they are used: what the model measures,
                        // what the structure inside the photo measures, and
                        // the factor between them. If a model is the wrong
                        // size on site, one of these says why.
                        DebugRow(
                            "Model height",
                            if (debug.modelHeightUnits > 0) {
                                String.format("%.2f units", debug.modelHeightUnits)
                            } else {
                                "measuring…"
                            }
                        )
                        DebugRow(
                            "Structure height",
                            if (debug.scaleLocked) {
                                String.format("%.2f m (locked)", debug.structureHeightMeters)
                            } else {
                                String.format("%.2f m (settling)", debug.extentZ)
                            }
                        )
                        DebugRow(
                            "Scale factor",
                            if (debug.appliedScale > 0) {
                                String.format("x %.3f", debug.appliedScale)
                            } else {
                                "not applied yet"
                            }
                        )
                        DebugRow(
                            "Image extent",
                            String.format("%.2f x %.2f m", debug.extentX, debug.extentZ)
                        )
                        DebugRow(
                            "Offsets R/U/F",
                            String.format(
                                "%.1f / %.1f / %.1f",
                                debug.offsetRight,
                                debug.offsetUp,
                                debug.offsetForward
                            )
                        )
                        DebugRow("Tracking", debug.trackingMethod)
                        DebugRow("Occlusion", if (debug.occlusionOn) "on" else "off")
                        DebugRow(
                            "Lighting",
                            if (debug.studioLighting) "neutral" else "room estimate"
                        )
                        DebugRow("Materials", "${debug.materialCount} (matte)")
                        DebugRow(
                            "Animations",
                            if (debug.animationCount == 0) {
                                "none"
                            } else {
                                "${debug.animationCount} playing"
                            }
                        )

                        Spacer(Modifier.height(8.dp))

                        // The first thing to try when the structure is
                        // recognised and nothing draws. If the model appears
                        // the moment this goes off, it was behind the real
                        // wall the whole time and Offset Forward is too small.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.16f))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onToggleOcclusion
                                )
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (occlusionEnabled) {
                                    "Occlusion on — tap to disable"
                                } else {
                                    "Occlusion off — tap to enable"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Spacer(Modifier.height(6.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.16f))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onToggleLighting
                                )
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (studioLighting) {
                                    "Use room lighting"
                                } else {
                                    "Use neutral lighting"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Spacer(Modifier.height(5.dp))

                        Text(
                            text = "Lighting applies when AR is reopened.",
                            fontSize = 9.5.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugRow(label: String, value: String) {
    Row(
        modifier = Modifier.padding(bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 10.5.sp,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun SearchingCard(uiState: ArUiState) {

    val geo = uiState.geoStatus

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.94f))
            .padding(16.dp)
    ) {

        Row(verticalAlignment = Alignment.CenterVertically) {

            Icon(
                imageVector = if (uiState.useImage) {
                    Icons.Default.CenterFocusStrong
                } else {
                    Icons.Default.LocationOn
                },
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(17.dp)
            )

            Spacer(Modifier.width(8.dp))

            Text(
                text = when {
                    uiState.stage == ArStage.ANCHORING -> "Placing the structure\u2026"
                    uiState.useImage -> "Looking for the structure"
                    else -> "Finding your exact location"
                },
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = when {
                uiState.useImage && uiState.useGeospatial ->
                    "Face the structure so it fills most of the screen, or pan " +
                        "slowly across the buildings around you. Either will " +
                        "place it \u2014 the photo match is the more precise."
                uiState.useImage ->
                    "Stand facing it and move back until it fills most of the " +
                        "screen. Move the phone a little side to side while " +
                        "holding it on the structure \u2014 that lets the camera " +
                        "judge its size."
                else ->
                    "Point the camera at the buildings and walls around you " +
                        "\u2014 not the ground or sky \u2014 and pan slowly. " +
                        "Your location is matched against the surroundings."
            },
            color = TextSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )

        // A later, secondary hint rather than something shown from the first
        // frame — most searches never need it, and leading with "don't worry
        // about interference" before any interference has happened reads as
        // an odd thing to bring up unprompted.
        if (uiState.useImage && uiState.searchingSeconds >= SEARCH_HINT_DELAY_SECONDS) {

            Spacer(Modifier.height(6.dp))

            Text(
                text = "If people or passing shadows keep crossing in front of " +
                    "it, that's fine — just hold the camera steady and it " +
                    "will keep trying.",
                color = TextSecondary,
                fontSize = 11.5.sp,
                lineHeight = 15.sp
            )
        }

        if (uiState.useGeospatial) {

            Spacer(Modifier.height(8.dp))

            val accuracy = geo.horizontalAccuracyMeters
            val heading = geo.headingAccuracyDegrees

            Text(
                text = when {
                    !geo.tracking || accuracy == null || heading == null ->
                        "Location: localizing\u2026"
                    geo.goodEnough ->
                        String.format("Location locked (\u00B1%.1f m)", accuracy)
                    else ->
                        String.format(
                            "Location \u00B1%.1f m, heading \u00B1%.0f\u00B0 \u2014 keep panning",
                            accuracy,
                            heading
                        )
                },
                color = if (geo.goodEnough) Primary else TextSecondary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(12.dp))

        LinearProgressIndicator(
            progress = { uiState.searchProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(50)),
            color = Primary,
            trackColor = Color(0xFFEDEDF0)
        )
    }
}

@Composable
private fun ManualAlignCard(
    hasPlaced: Boolean,
    onLock: () -> Unit,
    onReset: () -> Unit,
    onNudgeHeight: (Float) -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.94f))
            .padding(16.dp)
    ) {

        if (!hasPlaced) {

            // Aiming phase: the grid/reticle overlay is doing the real work
            // here, this card is just naming what it means.
            Text(
                text = "Find a flat surface",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(5.dp))

            Text(
                text = "Move your phone slowly until the grid settles on the " +
                    "ground, then tap anywhere on screen to place the " +
                    "structure at the center marker.",
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        } else {

            Text(
                text = "Adjust the placement",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(5.dp))

            Text(
                text = "Drag to slide it, pinch to resize, and twist with two " +
                    "fingers to turn it. Use the arrows to raise or lower it. " +
                    "Tap Lock in Place when it looks right.",
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                NudgeButton(label = "\u2193", onClick = { onNudgeHeight(-MANUAL_HEIGHT_STEP) })

                NudgeButton(label = "\u2191", onClick = { onNudgeHeight(MANUAL_HEIGHT_STEP) })

                Spacer(Modifier.width(4.dp))

                // Outline style \u2014 a visibly lighter weight than Lock, since
                // this discards the current placement rather than confirming
                // it, and shouldn't read as the default/primary action.
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, TextSecondary.copy(alpha = 0.4f), RoundedCornerShape(50))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onReset
                        )
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = "Reset",
                        color = TextSecondary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Primary)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onLock
                        )
                        .padding(horizontal = 18.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = "Lock in Place",
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun NudgeButton(label: String, onClick: () -> Unit) {

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(SurfaceSoft)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Explicit resize/rotate controls for "Place It Myself" — a floating pill of
 * four buttons at the bottom of the screen, alongside the pinch/twist
 * gestures on the manipulate surface rather than replacing them. Each tap
 * calls straight into onScale/onRotate, the exact same callbacks the gesture
 * surface uses, with one fixed step per tap instead of a live gesture delta
 * — so a button tap and a small pinch/twist move the model by comparable,
 * predictable amounts, and nothing about the underlying scale/rotation
 * state cares which one triggered it.
 */
@Composable
private fun ManualScaleRotateBar(
    onScaleDown: () -> Unit,
    onScaleUp: () -> Unit,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit
) {

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.94f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NudgeButton(label = "−", onClick = onScaleDown)
        NudgeButton(label = "+", onClick = onScaleUp)

        Box(
            modifier = Modifier
                .width(1.dp)
                .height(24.dp)
                .background(TextSecondary.copy(alpha = 0.25f))
        )

        NudgeButton(label = "↺", onClick = onRotateLeft)
        NudgeButton(label = "↻", onClick = onRotateRight)
    }
}

@Composable
private fun HintCard(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.94f))
            .padding(16.dp)
    ) {

        Text(
            text = title,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(5.dp))

        Text(
            text = body,
            color = TextSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )

        if (actionLabel != null && onAction != null) {

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Primary)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onAction
                        )
                        .padding(horizontal = 20.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = actionLabel,
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (secondaryActionLabel != null && onSecondaryAction != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(SurfaceSoft)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onSecondaryAction
                            )
                            .padding(horizontal = 20.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = secondaryActionLabel,
                            color = TextPrimary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Site information, read without leaving the AR session.
 *
 * A sheet rather than navigation to Site Details. Backing out of AR tears
 * down the ARCore session, so returning means pointing the phone at the
 * structure and waiting for it to be recognised all over again — a heavy
 * price for reading two paragraphs. This keeps the camera alive behind it.
 *
 * Styled as glass rather than as a normal opaque sheet: the whole point of
 * reading this over the shutter row instead of on the Site Details page is
 * that the structure is standing right there through the camera, and an
 * opaque card would hide the very thing the visitor opened it to read
 * about. The system sheet's own scrim is dropped to almost nothing and its
 * container made transparent so the drag handle, swipe-to-dismiss and
 * back-press behaviour it provides are kept; the frosted panel underneath
 * is drawn by hand on top of that.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArSiteInfoSheet(
    site: HistoricalSite,
    content: SiteContent?,
    selectedTab: SiteDetailsTab,
    onTabSelected: (SiteDetailsTab) -> Unit,
    onDismiss: () -> Unit
) {

    val context = LocalContext.current

    // Scoped to the sheet, so closing it stops the narration. A voice
    // continuing over a live camera feed after the panel is gone would be
    // baffling, and there is no visible control left to stop it.
    val voiceover = rememberVoiceoverController()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = 0.18f),
        tonalElevation = 0.dp,
        dragHandle = null
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(CardSurface.copy(alpha = 0.72f))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(top = 14.dp, bottom = 28.dp)
        ) {

            // Hand-drawn in place of the system drag handle so it sits on
            // the glass panel itself rather than floating over bare camera
            // above it — the sheet is still draggable by this same gesture,
            // this just replaces how the handle looks.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(TextSecondary.copy(alpha = 0.35f))
            )

            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.Top) {

                Column(modifier = Modifier.weight(1f)) {

                    Text(
                        text = "HISTORICAL SITE",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp,
                        color = AccentGold
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = site.siteName,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        lineHeight = 26.sp
                    )
                }

                Spacer(Modifier.width(12.dp))

                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(TextPrimary.copy(alpha = 0.08f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (site.location.isNotBlank()) {

                Spacer(Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = site.location,
                        fontSize = 12.5.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // The same tabs the Site Details page renders, from the same
            // component. Previously this sheet showed a short summary while
            // the real content lived on another screen, so a visitor with
            // the model in front of them got less than one sitting at home.
            SiteContentTabRow(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected
            )

            Spacer(Modifier.height(18.dp))

            SiteContentTabBody(
                selectedTab = selectedTab,
                content = content,
                fallbackOverview = site.description,
                voiceover = voiceover,
                onSourceClick = { url ->
                    if (url.isNotBlank()) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                }
            )
        }
    }
}

/**
 * Info shortcut, styled to match [BackButton] exactly and placed in the
 * opposite top corner so the two read as a deliberate pair rather than one
 * control sitting wherever there happened to be room.
 */
@Composable
private fun InfoButton(onClick: () -> Unit) {

    Box(
        modifier = Modifier
            .statusBarsPadding()
            .padding(16.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(10.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "Site information",
            tint = Color.White
        )
    }
}

@Composable
private fun CaptureButton(
    isSaving: Boolean,
    onClick: () -> Unit
) {

    Box(
        modifier = Modifier
            .size(74.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.28f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !isSaving,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {

        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(if (isSaving) AccentGold else Color.White),
            contentAlignment = Alignment.Center
        ) {

            if (isSaving) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Take a photo",
                    tint = Primary,
                    modifier = Modifier.size(25.dp)
                )
            }
        }
    }
}

/**
 * Shown full-screen right after the shutter, before anything is uploaded.
 *
 * [original] already has the camera feed and the AR model composited into it
 * (that's what [captureWindow] copies) — this sheet only offers a color
 * grade on top of that finished photo. Deliberately not a live camera
 * filter: ARCore matches and tracks against the real, unmodified feed either
 * way, so tinting the viewfinder itself would only risk visitors lining up a
 * shot against colors the app isn't actually using for anything, for a look
 * that only matters once in the saved photo anyway.
 */
@Composable
private fun CaptureReviewSheet(
    original: Bitmap,
    isSaving: Boolean,
    onDiscard: () -> Unit,
    onSave: (Bitmap) -> Unit
) {

    var selected by remember { mutableStateOf(CaptureFilter.NONE) }

    // A capped-size copy for the big preview and the filter thumbnails alike.
    // Regrading the full-resolution capture on every tap would be real work
    // for a difference the eye can't see at preview size; the full-resolution
    // bitmap is only graded once, when Save is actually pressed.
    val previewSource = remember(original) { scaledCopy(original, maxDimension = 900) }

    val thumbSource = remember(original) { scaledCopy(original, maxDimension = 160) }

    var preview by remember(previewSource) { mutableStateOf(previewSource) }

    // Off the main thread so picking a heavier look (Vintage adds a vignette
    // pass on top of the color grade) never stutters the tap that chose it.
    LaunchedEffect(selected, previewSource) {
        preview = withContext(Dispatchers.Default) { selected.apply(previewSource) }
    }

    val scope = rememberCoroutineScope()
    var confirming by remember { mutableStateOf(false) }
    val busy = confirming || isSaving

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        Image(
            bitmap = preview.asImageBitmap(),
            contentDescription = "Captured photo",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = !busy,
                    onClick = onDiscard
                )
                .padding(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Discard photo",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(16.dp)
        ) {

            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                CaptureFilter.entries.forEach { filter ->
                    FilterOption(
                        filter = filter,
                        thumbSource = thumbSource,
                        isSelected = filter == selected,
                        enabled = !busy,
                        onClick = { selected = filter }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(if (busy) AccentGold else Primary)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !busy,
                        onClick = {
                            confirming = true
                            // Graded from [original] at full resolution — the
                            // downscaled preview never touches the saved file.
                            scope.launch {
                                val graded = withContext(Dispatchers.Default) {
                                    selected.apply(original)
                                }
                                onSave(graded)
                            }
                        }
                    )
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Text(
                        text = "Save Photo",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/** One filter thumbnail, regraded from a shared small source bitmap. */
@Composable
private fun FilterOption(
    filter: CaptureFilter,
    thumbSource: Bitmap,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {

    // Cheap at thumbnail size, and it means every option shows what it will
    // actually look like rather than a generic swatch or icon.
    val thumb = remember(filter, thumbSource) { filter.apply(thumbSource) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
    ) {

        Image(
            bitmap = thumb.asImageBitmap(),
            contentDescription = filter.label,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(
                    width = if (isSelected) 2.dp else 0.dp,
                    color = if (isSelected) AccentGold else Color.Transparent,
                    shape = RoundedCornerShape(10.dp)
                )
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = filter.label,
            color = if (isSelected) AccentGold else Color.White,
            fontSize = 10.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {

    Box(
        modifier = Modifier
            // The camera feed is deliberately full-bleed; the control is not.
            // Before this, on a phone with a centred cutout the back button
            // sat directly under it and was unreachable.
            .statusBarsPadding()
            .padding(16.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(10.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = Color.White
        )
    }
}

// ----------------------------------------------------------------- helpers

// ----- image recognition tuning -----

/** Width readings within this fraction of each other count as agreeing. */
private const val SIZE_AGREEMENT = 0.015f

/**
 * Agreeing frames before a photo's size counts as settled (~0.6 s at 30 fps).
 * Four used to be enough to lock — too few: ARCore is still refining its
 * estimate at that point, and the early number was what the model got
 * stuck with.
 */
private const val STABLE_READINGS_REQUIRED = 18

/** Lock anyway after this many fully tracked frames (~3 s), so a wobbly estimate can't block placement forever. */
private const val SIZE_SETTLE_FALLBACK_FRAMES = 90

/** A later estimate this far from the lock, for [SIZE_RELOCK_FRAMES], replaces it. */
private const val SIZE_RELOCK_DIFFERENCE = 0.07f

private const val SIZE_RELOCK_FRAMES = 30

/**
 * Consecutive frames with NO tracking reading at all — not even
 * LAST_KNOWN_POSE — before the overlay says the structure was lost
 * (~1.5 s at 30 fps).
 *
 * Below this, a placed structure counts as still tracked even without a
 * single FULL_TRACKING frame. Once anchored, the model's position no longer
 * depends on the image at all, so LAST_KNOWN_POSE — ARCore extrapolating
 * from an image it can't currently see — is a perfectly good signal that
 * everything is fine; forcing FULL_TRACKING here just re-litigates a
 * question that was already answered when the anchor was made. A grace
 * period on top of that is what stops a person walking past the structure,
 * a passing shadow, or a moment of glare from flashing "lost sight of the
 * structure" on and off the screen for something that resolves itself a
 * second later.
 */
private const val TRACKING_LOST_GRACE_FRAMES = 45

/**
 * Seconds of searching before [SearchingCard] adds the "obstructions are
 * fine" hint. Held back this long so the coaching only appears once it's
 * plausibly relevant, rather than pre-emptively on every search.
 */
private const val SEARCH_HINT_DELAY_SECONDS = 6

// ----- geospatial tuning -----

/**
 * Accuracy required before placing by location. Tighter numbers place more
 * precisely but may never be reached in Intramuros' narrow streets; these
 * are the loosest values that still put a structure on the right spot.
 */
private const val GEO_MAX_HORIZONTAL_ERROR_METERS = 3.0

private const val GEO_MAX_HEADING_ERROR_DEGREES = 8.0

/**
 * Looser fix accepted after [GEO_FALLBACK_AFTER_MS] of Earth tracking.
 * Heading is kept fairly tight even here: because the pose is pinned once
 * placed, a heading error at placement becomes a permanent sideways offset
 * (roughly distance × error in radians).
 */
private const val GEO_USABLE_HORIZONTAL_ERROR_METERS = 5.0

private const val GEO_USABLE_HEADING_ERROR_DEGREES = 12.0

private const val GEO_FALLBACK_AFTER_MS = 10_000L

/** Consecutive good frames required (~0.5 s). */
private const val GEO_GOOD_FRAMES_REQUIRED = 15

private const val GEO_STATUS_INTERVAL_MS = 500L

/** Wait before asking again when a terrain anchor request fails. */
private const val TERRAIN_RETRY_INTERVAL_MS = 3_000L

/** How far the structure slides per pixel of drag during manual alignment. */
private const val MANUAL_DRAG_METERS_PER_PIXEL = 0.02f

private const val MANUAL_HEIGHT_STEP = 0.25f

/** Pinch-to-resize limits for "Place It Myself" — never smaller than 40% or
 *  larger than 250% of the model's normal (admin/authored) size, so a fast
 *  or clumsy pinch can't accidentally leave it comically tiny or huge. */
private const val MANUAL_SCALE_MIN = 0.4f
private const val MANUAL_SCALE_MAX = 2.5f

/** Per-tap step for the explicit +/- resize buttons (ManualScaleRotateBar),
 *  as a multiplicative factor — 15% bigger/smaller per tap, comparable to a
 *  small deliberate pinch rather than a jump. */
private const val MANUAL_SCALE_STEP_FACTOR = 1.15f

/** Per-tap step, in degrees, for the explicit rotate-left/right buttons. */
private const val MANUAL_ROTATE_STEP_DEGREES = 15f

/** Ground grid half-extent and tile spacing, in metres, for the aiming grid
 *  drawn under the reticle before the structure is placed. 1m half-extent /
 *  0.4m tiles gives a 2m x 2m grid — big enough to read as a placement area,
 *  small enough to stay legible at typical aiming distance. */
private const val MANUAL_GRID_HALF_EXTENT_METERS = 1f
private const val MANUAL_GRID_STEP_METERS = 0.4f

/**
 * Builds the pose the model should occupy, given where ARCore says the
 * photographed structure is.
 *
 * ARCore reports an augmented image with its own axis convention: +X runs
 * left to right across the photo, +Z runs top to bottom down it, and +Y
 * points out of the photo toward the viewer. For a photo of a wall that
 * means the frame is standing on its edge — so a model authored Y-up has to
 * be rotated a quarter turn about X before it stands upright in the world.
 *
 * The authored offsets are applied first, in the photo's own frame, which is
 * why the admin panel can express them as plain "right", "up" and "forward"
 * distances from the centre of the structure.
 */
/**
 * Live placement readout, for the case where the structure is recognised
 * but no model is visible on screen.
 *
 * That symptom has several causes that look identical through the camera —
 * the model occluded by the real wall, placed inside the visitor, scaled to
 * a speck, or scaled so large they are standing inside it. None of them can
 * be told apart by looking, and all of them are settled instantly by four
 * numbers. This is the cheapest way to get those numbers while standing in
 * front of the actual structure, which is the only place the problem
 * reproduces.
 */
private data class ArPlacementDebug(
    val modelLoaded: Boolean,
    val modelBytes: Long,
    val anchorAttached: Boolean,
    val nodeInScene: Boolean,
    /** Camera to anchor, in metres. The single most diagnostic number here. */
    val distanceMeters: Float,
    /** The uniform factor actually applied to the model. */
    val appliedScale: Double,
    /** The locked structure height (photo height × structure fraction) the scale was derived from. */
    val structureHeightMeters: Double,
    /** The model's own height, measured from its bounding box. */
    val modelHeightUnits: Double,
    /** Whether the size estimate has settled and the scale is fixed. */
    val scaleLocked: Boolean,
    /** What ARCore measured the structure as, once recognised. */
    val extentX: Float,
    val extentZ: Float,
    val offsetRight: Double,
    val offsetUp: Double,
    val offsetForward: Double,
    val occlusionOn: Boolean,
    val studioLighting: Boolean,
    val materialCount: Int,
    val animationCount: Int,
    val trackingMethod: String,
    /** What placed the model: which photo, geospatial, or by hand. */
    val placedBy: String,
    /** How many reference photos ARCore accepted. */
    val photos: String,
    /** Geospatial accuracy, or "off". */
    val geospatial: String,
    /** The admin panel's Model Size; 0 when not set. */
    val explicitModelSize: Double
)

/**
 * Surface correction applied to every material in a loaded reconstruction.
 *
 * glTF defaults `metallicFactor` to 1.0 when a material doesn't state it,
 * and plenty of photogrammetry exporters simply don't state it. A fully
 * metallic surface has no diffuse response at all — its base colour becomes
 * reflectance rather than paint — so a scan of weathered adobe renders as
 * black chrome that mirrors whatever is around it. That is the glossy
 * plastic look, and no amount of light tuning fixes it, because the
 * material is behaving exactly as a mirror should.
 *
 * Stone and plaster are dielectric and close to fully rough, so forcing
 * these two values is not an artistic liberty — it is what the material
 * should have said in the first place. The baked-in colour and detail of
 * the scan come through untouched; only the response to light changes.
 */
/**
 * How the reconstruction should be lit, held outside the AR screen.
 *
 * ARCore's light estimation can only be chosen when the session is
 * configured, and it is not symmetrically reversible: switching it on
 * mid-session works, switching it back off does not. ARCore has by then
 * pushed its estimated environment and light into the renderer, and
 * disabling estimation merely stops further updates — nothing re-supplies
 * the neutral environment that was replaced, so the model stays stuck
 * looking however the last estimate left it.
 *
 * So the toggle records a preference and the next session applies it. That
 * only works if the preference outlives the screen, hence this object
 * rather than a remembered value inside it.
 *
 * Neutral by default: a reconstruction that reads clearly in any room beats
 * one that is faithful to a dark one. The estimate is worth turning on
 * outdoors in daylight, where matching real light is what stops the model
 * looking pasted onto the scene.
 */
private object ArLightingPreference {
    var useNeutralLighting by mutableStateOf(true)
}

private const val MATTE_METALLIC = 0.0f
private const val MATTE_ROUGHNESS = 0.92f

/** What a placement's size is measured from. Null = no photo to measure (geospatial/manual). */
private data class ScaleBasis(val structureHeightMeters: Float?)

/**
 * The pose the model should occupy, given where ARCore says a reference
 * photo is and that photo's own placement from the admin panel.
 *
 * ARCore reports an augmented image with +X running left to right across
 * the photo, +Z running down it, and +Y pointing out of it toward the
 * viewer.
 *
 * The pose is LEVELLED. The model keeps only the photo's facing direction
 * (its +Y, flattened onto the ground) and stands with its up axis on true
 * vertical. Previously the whole image pose was used, so a photo estimated a
 * couple of degrees off vertical tipped the entire structure by the same
 * amount — invisible on a gate, but metres of lean at the top of a tower,
 * and the main reason models looked sunk into or floating off the ground.
 *
 * In the levelled frame +X is the visitor's right, +Y is up and +Z points
 * toward the visitor — exactly the admin panel's "right", "up" and
 * "forward" — so the offsets apply directly, followed by the photo-relative
 * yaw.
 */
private fun imageAnchorPose(image: AugmentedImage, reference: ArReferenceImage): Pose {

    val center = image.centerPose
    val normal = center.yAxis // out of the photo, toward the viewer

    val flatLength = sqrt(normal[0] * normal[0] + normal[2] * normal[2])

    // A photo lying almost flat (a floor plaque) has no meaningful facing
    // direction; fall back to the image's own frame, stood upright.
    if (flatLength < 0.2f) {
        return legacyImageAnchorPose(image, reference)
    }

    val facing = atan2(normal[0] / flatLength, normal[2] / flatLength)
    val level = Pose(
        center.translation,
        floatArrayOf(0f, sin(facing / 2f), 0f, cos(facing / 2f))
    )

    val offset = Pose.makeTranslation(
        reference.offsetRightMeters.toFloat(),
        reference.offsetUpMeters.toFloat(),
        reference.offsetForwardMeters.toFloat()
    )

    return level.compose(offset).compose(yawPose(reference.headingDegrees))
}

/** The old, unlevelled construction — only for near-horizontal photos. */
private fun legacyImageAnchorPose(image: AugmentedImage, reference: ArReferenceImage): Pose {

    // +Z runs down the image, so "up" is negative Z.
    val translation = Pose.makeTranslation(
        reference.offsetRightMeters.toFloat(),
        reference.offsetForwardMeters.toFloat(),
        -reference.offsetUpMeters.toFloat()
    )

    // -90 degrees about X: takes the model's +Y (up) onto the image frame's
    // -Z (up the photo).
    val upright = Pose.makeRotation(-SQRT_HALF, 0f, 0f, SQRT_HALF)

    return image.centerPose
        .compose(translation)
        .compose(upright)
        .compose(yawPose(reference.headingDegrees))
}

private fun yawPose(degrees: Double): Pose {
    val half = Math.toRadians(degrees).toFloat() / 2f
    return Pose.makeRotation(0f, sin(half), 0f, cos(half))
}

/**
 * Resolves the site's geospatial placement as a terrain anchor.
 *
 * Terrain anchors take their altitude from Google's terrain model at the
 * pin, so the admin only sets a height OFFSET above the ground rather than
 * an absolute altitude nobody knows.
 *
 * The rotation is about the vertical axis in ARCore's East-Up-South frame:
 * (180 - heading) turns the model's front (+Z) to face the compass bearing
 * set in the admin panel, 0 = north.
 */
private fun resolveTerrainAnchor(
    earth: Earth,
    site: HistoricalSite,
    onResult: (Anchor?) -> Unit
) {
    val latitude = site.arLatitude
    val longitude = site.arLongitude

    if (latitude == null || longitude == null) {
        onResult(null)
        return
    }

    val half = Math.toRadians(180.0 - site.arGeoHeadingDegrees) / 2.0

    try {
        earth.resolveAnchorOnTerrainAsync(
            latitude,
            longitude,
            site.arAltitudeOffsetMeters,
            0f,
            kotlin.math.sin(half).toFloat(),
            0f,
            kotlin.math.cos(half).toFloat()
        ) { anchor, state ->
            if (state == Anchor.TerrainAnchorState.SUCCESS && anchor != null) {
                onResult(anchor)
            } else {
                Log.w("HistouryAR", "Terrain anchor failed: $state")
                anchor?.detach()
                onResult(null)
            }
        }
    } catch (error: Exception) {
        Log.e("HistouryAR", "Terrain anchor request failed", error)
        onResult(null)
    }
}

/**
 * A visitor-readable reason Earth can't run, or null when it's fine.
 * Compared by name so a constant missing from an older ARCore can't break
 * the build.
 */
private fun describeEarthProblem(state: Earth.EarthState): String? = when (state.name) {
    "ENABLED" -> null
    "ERROR_NOT_AUTHORIZED" ->
        "Location-based AR isn't authorised for this app. The ARCore API key " +
            "needs the Geospatial API enabled in Google Cloud."
    "ERROR_RESOURCE_EXHAUSTED" ->
        "Location-based AR is busy right now. Try again in a few minutes."
    "ERROR_APK_VERSION_TOO_OLD" ->
        "Update Google Play Services for AR to place this structure by location."
    else -> "Location-based AR couldn't start on this phone."
}

/**
 * Projects a world-space point to screen pixels using the camera's current
 * view and projection matrices, both as ARCore fills them: column-major 4x4,
 * OpenGL convention. Returns null when the point is behind the camera (a
 * non-positive clip-space w), which a caller should treat as "not visible"
 * rather than draw at some nonsensical flipped position.
 *
 * Standard technique (the same math behind e.g. gluProject/glm::project) —
 * not anything ARCore- or SceneView-specific — used here so the ground grid
 * and center reticle can be drawn as a plain 2D Compose Canvas overlay
 * rather than as extra 3D scene geometry.
 */
private fun worldToScreen(
    point: FloatArray,
    viewMatrix: FloatArray,
    projMatrix: FloatArray,
    viewportWidth: Int,
    viewportHeight: Int
): Offset? {

    val (x, y, z) = Triple(point[0], point[1], point[2])

    val vx = viewMatrix[0] * x + viewMatrix[4] * y + viewMatrix[8] * z + viewMatrix[12]
    val vy = viewMatrix[1] * x + viewMatrix[5] * y + viewMatrix[9] * z + viewMatrix[13]
    val vz = viewMatrix[2] * x + viewMatrix[6] * y + viewMatrix[10] * z + viewMatrix[14]
    val vw = viewMatrix[3] * x + viewMatrix[7] * y + viewMatrix[11] * z + viewMatrix[15]

    val cx = projMatrix[0] * vx + projMatrix[4] * vy + projMatrix[8] * vz + projMatrix[12] * vw
    val cy = projMatrix[1] * vx + projMatrix[5] * vy + projMatrix[9] * vz + projMatrix[13] * vw
    val cw = projMatrix[3] * vx + projMatrix[7] * vy + projMatrix[11] * vz + projMatrix[15] * vw

    if (cw <= 0.0001f) return null

    val ndcX = cx / cw
    val ndcY = cy / cw

    return Offset(
        x = (ndcX * 0.5f + 0.5f) * viewportWidth,
        y = (1f - (ndcY * 0.5f + 0.5f)) * viewportHeight
    )
}

/**
 * A small square grid of tile lines centred on [groundPose] (a plane hit
 * pose: its local Y axis is the surface normal), each corner transformed
 * into world space via [Pose.transformPoint] and then to screen pixels via
 * [worldToScreen]. A line is only included when both its endpoints project
 * successfully — comfortably enough for a small, mostly-in-view aiming grid
 * without needing full clipping against the screen or the plane's polygon.
 */
private fun buildGroundGridScreenLines(
    groundPose: Pose,
    viewMatrix: FloatArray,
    projMatrix: FloatArray,
    viewportWidth: Int,
    viewportHeight: Int
): List<Pair<Offset, Offset>> {

    val half = MANUAL_GRID_HALF_EXTENT_METERS
    val step = MANUAL_GRID_STEP_METERS
    val offsets = generateSequence(-half) { it + step }.takeWhile { it <= half + 0.001f }.toList()

    fun project(localX: Float, localZ: Float): Offset? {
        val world = groundPose.transformPoint(floatArrayOf(localX, 0f, localZ))
        return worldToScreen(world, viewMatrix, projMatrix, viewportWidth, viewportHeight)
    }

    val lines = mutableListOf<Pair<Offset, Offset>>()

    // Lines running along Z, at each X offset.
    offsets.forEach { x ->
        val start = project(x, -half)
        val end = project(x, half)
        if (start != null && end != null) lines += start to end
    }

    // Lines running along X, at each Z offset.
    offsets.forEach { z ->
        val start = project(-half, z)
        val end = project(half, z)
        if (start != null && end != null) lines += start to end
    }

    return lines
}

private const val SQRT_HALF = 0.70710678f

/**
 * Downloads the reference photograph and returns it in the ARGB_8888 form
 * ARCore's image database requires. Cached alongside the models, keyed by
 * URL, so replacing the photo in the admin panel produces a fresh entry.
 */
private suspend fun downloadReferenceImage(
    context: android.content.Context,
    url: String
): Bitmap? = withContext(Dispatchers.IO) {

    val cacheDirectory = OfflineStore.referencesDir(context)
    val cacheKey = OfflineStore.keyFor(url)
    val cachedFile = File(cacheDirectory, "$cacheKey.img")

    if (!cachedFile.isFile || cachedFile.length() <= 0L) {

        val temporaryFile = File(cacheDirectory, "$cacheKey.download")
        temporaryFile.delete()

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }

        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                error("Reference image download returned HTTP ${connection.responseCode}.")
            }

            connection.inputStream.use { input ->
                temporaryFile.outputStream().buffered().use { output ->
                    input.copyTo(output)
                }
            }

            if (cachedFile.exists()) cachedFile.delete()
            if (!temporaryFile.renameTo(cachedFile)) {
                temporaryFile.copyTo(cachedFile, overwrite = true)
                temporaryFile.delete()
            }
        } finally {
            connection.disconnect()
            temporaryFile.delete()
        }
    }

    val decoded = BitmapFactory.decodeFile(cachedFile.absolutePath) ?: return@withContext null

    // ARCore's addImage requires ARGB_8888. Storage may well hand back a
    // JPEG that decodes to RGB_565 on some devices.
    if (decoded.config == Bitmap.Config.ARGB_8888) {
        decoded
    } else {
        decoded.copy(Bitmap.Config.ARGB_8888, false)
    }
}

@Composable
private fun UnavailableState(
    reason: String,
    onBack: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Icon(
            imageVector = Icons.Default.CameraAlt,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(42.dp)
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = reason.ifBlank { "AR isn't available right now." },
            color = Color.White,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.16f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBack
                )
                .padding(horizontal = 22.dp, vertical = 11.dp)
        ) {
            Text(
                text = "Go back",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Grabs whatever is currently on screen, including the AR camera feed.
 *
 * A SurfaceView's contents don't exist in the Compose/View draw tree, so
 * drawing the hierarchy to a Canvas returns a hole where the camera should
 * be. PixelCopy reads the actual composited window buffer instead, which is
 * both correct and independent of the AR library's own screenshot support.
 */
/**
 * How wide the model is in its own units, from its bounding box.
 *
 * Read from the loaded asset rather than typed into the admin panel. The
 * number was never knowable from a desk — it depends on what the modeller
 * set as one unit in Blender — so asking an admin for it was asking them to
 * guess, and a wrong guess scaled the structure wrongly.
 *
 * Filament reports the box as a centre and a half-extent, so the full width
 * is twice the X half-extent.
 */
private fun measureModelWidth(instance: ModelInstance): Float =
    runCatching { instance.model.boundingBox.halfExtent[0] * 2f }.getOrDefault(0f)

/**
 * How tall the model is in its own units, from its bounding box's vertical
 * axis.
 *
 * glTF (and therefore every GLB this app loads — the admin panel only
 * accepts glTF 2.0) mandates +Y as up, so the vertical extent is the
 * bounding box's Y half-extent, doubled. This is what the model's height is
 * matched against the structure's real-world height for.
 */
private fun measureModelHeight(instance: ModelInstance): Float =
    runCatching { instance.model.boundingBox.halfExtent[1] * 2f }.getOrDefault(0f)

/**
 * The model's longest side in its own units. The admin panel's Model Size is
 * the real-world length of this same side.
 */
private fun measureModelLongestSide(instance: ModelInstance): Float =
    runCatching {
        val half = instance.model.boundingBox.halfExtent
        maxOf(half[0], half[1], half[2]) * 2f
    }.getOrDefault(0f)

private fun findSurfaceView(root: View): SurfaceView? {

    if (root is SurfaceView) return root

    if (root is ViewGroup) {
        for (i in 0 until root.childCount) {
            findSurfaceView(root.getChildAt(i))?.let { return it }
        }
    }

    return null
}

private fun captureWindow(
    activity: Activity,
    onResult: (Bitmap?) -> Unit
) {

    val decor = activity.window.decorView

    if (decor.width <= 0 || decor.height <= 0) {
        onResult(null)
        return
    }

    val handler = Handler(Looper.getMainLooper())

    // Copy from the AR SurfaceView, not from the window.
    //
    // The window-level overload is documented to composite SurfaceViews,
    // but in practice that depends on the device's compositor: SceneView
    // puts its surface on a separate hardware layer, and on many phones
    // that layer is simply absent from the window buffer PixelCopy reads —
    // which is why captures came back as a fully black frame with the
    // correct dimensions rather than failing outright.
    //
    // Reading the surface directly is both more reliable and exactly what
    // is wanted here: the camera feed and the model are drawn into it,
    // while the Compose overlay is not, so the interface is excluded by
    // construction instead of by hiding it for a frame.
    val surfaceView = findSurfaceView(decor)

    if (surfaceView != null && surfaceView.width > 0 && surfaceView.height > 0) {

        val bitmap = Bitmap.createBitmap(
            surfaceView.width,
            surfaceView.height,
            Bitmap.Config.ARGB_8888
        )

        try {
            PixelCopy.request(
                surfaceView,
                bitmap,
                { copyResult ->
                    if (copyResult == PixelCopy.SUCCESS) {
                        onResult(bitmap)
                    } else {
                        Log.w("HistouryCapture", "Surface copy failed: $copyResult")
                        captureFromWindow(activity, decor, handler, onResult)
                    }
                },
                handler
            )
            return

        } catch (e: IllegalArgumentException) {
            Log.w("HistouryCapture", "Surface copy rejected", e)
            // Falls through to the window attempt below.
        }
    }

    captureFromWindow(activity, decor, handler, onResult)
}

/**
 * Window-level fallback, for the case where no SurfaceView is found or the
 * surface refuses the copy. Produces a black frame on some devices, which is
 * still better than returning nothing.
 */
private fun captureFromWindow(
    activity: Activity,
    decor: View,
    handler: Handler,
    onResult: (Bitmap?) -> Unit
) {

    val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)

    try {
        PixelCopy.request(
            activity.window,
            bitmap,
            { copyResult ->
                onResult(if (copyResult == PixelCopy.SUCCESS) bitmap else null)
            },
            handler
        )
    } catch (e: IllegalArgumentException) {
        Log.w("HistouryCapture", "Window copy rejected", e)
        onResult(null)
    }
}

// ----------------------------------------------------------- capture filters

/**
 * The looks offered on [CaptureReviewSheet], applied to the finished photo
 * rather than the live camera — see the doc comment there for why.
 */
private enum class CaptureFilter(val label: String) {

    NONE("Original") {
        override fun apply(bitmap: Bitmap): Bitmap = bitmap
    },

    VINTAGE("Vintage") {
        override fun apply(bitmap: Bitmap): Bitmap =
            applyVignette(applyColorMatrix(bitmap, vintageMatrix()), strength = 0.35f)
    },

    SEPIA("Sepia") {
        override fun apply(bitmap: Bitmap): Bitmap = applyColorMatrix(bitmap, sepiaMatrix())
    },

    NOIR("Noir") {
        override fun apply(bitmap: Bitmap): Bitmap = applyColorMatrix(bitmap, noirMatrix())
    };

    abstract fun apply(bitmap: Bitmap): Bitmap
}

/**
 * A faded, warm-toned, low-contrast grade with a soft vignette — the
 * standard "old photograph" recipe: less saturated, a little flatter, and
 * warmer than the original.
 */
private fun vintageMatrix(): ColorMatrix {

    val matrix = ColorMatrix()
    matrix.setSaturation(0.75f)

    val contrast = 0.88f
    val contrastTranslate = (1f - contrast) / 2f * 255f
    matrix.postConcat(
        ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, contrastTranslate,
                0f, contrast, 0f, 0f, contrastTranslate,
                0f, 0f, contrast, 0f, contrastTranslate,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )

    // A warm cast: a little more red, a touch more green, less blue —
    // like a print that's aged.
    matrix.postConcat(
        ColorMatrix(
            floatArrayOf(
                1.08f, 0f, 0f, 0f, 8f,
                0f, 1.02f, 0f, 0f, 4f,
                0f, 0f, 0.88f, 0f, -6f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )

    return matrix
}

/** The textbook luminance-to-brown-tone sepia matrix. */
private fun sepiaMatrix(): ColorMatrix = ColorMatrix(
    floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 0f,
        0.349f, 0.686f, 0.168f, 0f, 0f,
        0.272f, 0.534f, 0.131f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
)

/** Black & white with a bit more contrast than a flat desaturation gives. */
private fun noirMatrix(): ColorMatrix {

    val matrix = ColorMatrix()
    matrix.setSaturation(0f)

    val contrast = 1.15f
    val contrastTranslate = (1f - contrast) / 2f * 255f
    matrix.postConcat(
        ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, contrastTranslate,
                0f, contrast, 0f, 0f, contrastTranslate,
                0f, 0f, contrast, 0f, contrastTranslate,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )

    return matrix
}

/**
 * Runs [matrix] over every pixel in one draw call — the standard, fast
 * way to color-grade a bitmap on Android. Always returns a fresh bitmap;
 * [source] itself is never touched, so "Original" can still hand back the
 * untouched capture.
 */
private fun applyColorMatrix(source: Bitmap, matrix: ColorMatrix): Bitmap {

    val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(matrix)
    }

    Canvas(output).drawBitmap(source, 0f, 0f, paint)

    return output
}

/**
 * Darkens the corners so the eye settles on the middle of the frame, the way
 * an old lens's natural falloff would. [strength] is how dark the very
 * corner gets, from 0 (no effect) to 1 (black).
 */
private fun applyVignette(source: Bitmap, strength: Float): Bitmap {

    val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    canvas.drawBitmap(source, 0f, 0f, null)

    val radius = kotlin.math.hypot(output.width / 2f, output.height / 2f)

    val shader = RadialGradient(
        output.width / 2f,
        output.height / 2f,
        radius,
        intArrayOf(
            android.graphics.Color.TRANSPARENT,
            android.graphics.Color.TRANSPARENT,
            android.graphics.Color.argb((strength * 255).toInt(), 0, 0, 0)
        ),
        floatArrayOf(0f, 0.6f, 1f),
        Shader.TileMode.CLAMP
    )

    canvas.drawRect(
        0f,
        0f,
        output.width.toFloat(),
        output.height.toFloat(),
        Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
    )

    return output
}

/**
 * A copy of [source] no larger than [maxDimension] on its long side, for
 * work (previews, thumbnails) where the full-resolution capture would be
 * more pixels than the result is ever shown at. Returns [source] itself,
 * unscaled, if it's already within the cap.
 */
private fun scaledCopy(source: Bitmap, maxDimension: Int): Bitmap {

    val longSide = maxOf(source.width, source.height)
    if (longSide <= maxDimension) return source

    val scale = maxDimension.toFloat() / longSide
    return Bitmap.createScaledBitmap(
        source,
        (source.width * scale).toInt().coerceAtLeast(1),
        (source.height * scale).toInt().coerceAtLeast(1),
        true
    )
}
