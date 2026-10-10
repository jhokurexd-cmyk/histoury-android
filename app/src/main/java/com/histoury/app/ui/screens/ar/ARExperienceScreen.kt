package com.histoury.app.ui.screens.ar

import com.histoury.app.ui.tutorial.TutorialStep
import com.histoury.app.ui.tutorial.TutorialTour
import com.histoury.app.ui.tutorial.tutorialTarget
import com.histoury.app.navigation.safePopBackStack
import com.histoury.app.navigation.navigateSafely
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.graphics.Bitmap
import android.graphics.Canvas
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.BitmapShader
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.PixelCopy
import android.view.ViewGroup
import android.view.View
import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Switch
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.google.ar.core.Config
import com.google.android.filament.Viewport
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.histoury.app.data.viewmodel.ArExperienceViewModel
import com.histoury.app.data.viewmodel.ArStage
import com.histoury.app.data.viewmodel.ArUiState
import com.histoury.app.navigation.Routes
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
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMainLightNode
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Float4
import dev.romainguy.kotlin.math.Quaternion
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import kotlin.math.sqrt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import com.histoury.app.data.model.HistoricalSite
import com.histoury.app.data.offline.ArModelCache
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
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import io.github.sceneview.math.Scale

/**
 * The "Travel Back in Time" screen.
 *
 * Stand at a heritage site, point the camera at the ground, and place the
 * historical structure where you want it — then walk around it, step inside
 * it, and photograph it.
 *
 * TWO RESPONSIBILITIES, KEPT APART. This is the design rule the screen is
 * built around:
 *
 *   The geofence decides WHETHER this site's AR may run.
 *   The visitor decides WHERE the structure stands.
 *
 * Nothing crosses that line. The site's latitude and longitude are used to
 * confirm the visitor is actually at the site — checked against the geofence
 * belonging to that site's own id, so being inside Fort Santiago never
 * unlocks Manila Cathedral — and then they are done. No coordinate ever
 * reaches the model's transform.
 *
 * WHAT USED TO BE HERE. Placement was previously automatic, by ARCore
 * Geospatial (VPS, terrain anchors, latitude/longitude) or by Augmented
 * Image recognition of reference photos, with fallback logic between the two
 * and hand placement as a last resort. All of it has been removed. Both
 * methods depended on conditions nobody can guarantee on the day — GPS
 * quality in narrow streets, light and angle on a facade — and when they
 * were wrong they were wrong in the worst way: a structure that looked
 * placed but sat in the wrong spot, or drifted. Manual placement is cruder
 * and completely predictable, which on a real site visit is worth more.
 *
 * Do not reintroduce either method to solve a placement problem. If a
 * structure is in the wrong place, the visitor moves it.
 *
 * WORLD-LOCKED: the structure is placed exactly once, on an ordinary world
 * anchor from a real detected floor, and is never moved afterwards. Walking
 * toward it, around it, or through it moves the camera and nothing else. See
 * the comment above the placement code in ArCameraLayer.
 *
 * The flow:
 *
 *   LOADING_SITE → (geofence check) → MANUAL_ALIGNING → PLACED
 *                         ↓
 *                 OUTSIDE_GEOFENCE
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

    val ratingPrompt by viewModel.ratingPrompt.collectAsState()

    // Leaving after the model was placed asks for an optional review first.
    // Submitting, "Not now" or dismissing all pop back to the Site
    // Information page AR was opened from (it's the only entry point). With
    // no model placed, Back behaves exactly as before.
    androidx.activity.compose.BackHandler(
        enabled = ratingPrompt.modelPlaced && !ratingPrompt.handled && !ratingPrompt.isVisible
    ) {
        if (!viewModel.onExitRequested()) navController.safePopBackStack()
    }

    if (ratingPrompt.isVisible) {
        val existing = ratingPrompt.existingReview
        val promptContext = LocalContext.current
        com.histoury.app.ui.components.RateSiteDialog(
            title = if (existing != null) {
                "Update your review?"
            } else {
                "How was your visit to ${uiState.site?.siteName ?: "this site"}?"
            },
            subtitle = if (existing != null) {
                "You've already reviewed ${uiState.site?.siteName ?: "this site"}. " +
                    "Submitting updates that review."
            } else {
                "Your review appears in Site Rankings."
            },
            initialRating = existing?.rating?.toInt() ?: 0,
            initialText = existing?.reviewText.orEmpty(),
            isSubmitting = ratingPrompt.isSubmitting,
            errorMessage = ratingPrompt.errorMessage,
            submitLabel = if (existing != null) "Update" else "Submit",
            onSubmit = { rating, text ->
                viewModel.submitRating(rating, text) { message ->
                    android.widget.Toast.makeText(promptContext, message, android.widget.Toast.LENGTH_SHORT).show()
                    navController.safePopBackStack()
                }
            },
            onDismiss = {
                viewModel.declineRating()
                navController.safePopBackStack()
            }
        )
    }

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

    // True once this site's AR is cleared to arm — meaning the visitor has
    // been confirmed to be standing inside its own geofence. Nothing is
    // downloaded up front any more: the model loads inside the camera layer,
    // and there are no reference photos to fetch because nothing is being
    // recognised.
    var assetsReady by remember { mutableStateOf(false) }

    // Precise location is needed for exactly ONE thing: proving the visitor
    // is inside this site's geofence. It never positions the model — see the
    // note on ArStage. Visitors normally granted it for arrival detection
    // long before reaching the AR button, so this is usually already true
    // and the prompt never shows.
    var locationGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var locationAsked by remember { mutableStateOf(false) }
    // Refusals of the location prompt on this screen. After the second one
    // Android stops showing the prompt at all ("Don't ask again"), so "Try
    // again" then opens the app's settings instead of a prompt that
    // silently answers "denied".
    var locationDenials by remember { mutableStateOf(0) }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        locationGranted = granted
        if (!granted) locationDenials++
        locationAsked = true
    }

    // Coming back from the app's settings page: re-read the real permission
    // state (it may or may not have been granted there).
    val locationSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        locationGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        locationAsked = true
    }

    // Whether the device is physically inside THIS site's own geofence.
    //
    // This is now the ONLY gate in front of AR, so it carries weight it
    // didn't before. A site can be loaded here from a tapped notification, a
    // live arrival event, a deep link, or content filed under the wrong site
    // record — none of which prove the visitor is actually standing where
    // that site is. Location is the one signal not routed through any of
    // that, and it is checked against the fence belonging to this site's own
    // id, never against whichever fence happens to be nearest. Two sites a
    // few minutes' walk apart in Intramuros must never unlock each other.
    //
    // null = still checking; the camera only starts once this is true, and
    // false means the screen has already moved to ArStage.OUTSIDE_GEOFENCE
    // with an explanation the visitor can act on.
    var geofenceCheckPassed by remember { mutableStateOf<Boolean?>(null) }

    // Bumped by "Try again" on the outside-site screen, purely to re-key the
    // gate below so it runs again. Without it that effect's keys are all
    // unchanged after a refusal — same site, same permission — so nothing
    // would re-trigger it and the refusal would be final for the life of the
    // screen, which is precisely what made a single bad fix feel like a lock.
    var geofenceRetryToken by remember { mutableStateOf(0) }

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
            // Tapping outside used to just hide the dialog, leaving an
            // endless spinner with no camera and no explanation. It now
            // means the same as "Not now".
            onDismissRequest = {
                showCameraSettingsExplanation = false
                navController.safePopBackStack()
            },
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
                        navController.safePopBackStack()
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

    // The gate: is the visitor standing inside THIS site's own geofence?
    //
    // Keyed on locationAsked as well as locationGranted, which matters. The
    // earlier version keyed only on (site, locationGranted), so a visitor
    // who declined the permission prompt left geofenceCheckPassed at null
    // forever — the effect never re-ran, the camera never started, and the
    // screen sat on a spinner with no message and no way forward but the
    // back button. A refusal now resolves to a real answer.
    LaunchedEffect(
        site?.documentId, locationGranted, locationAsked, geofenceRetryToken,
        cameraGranted, cameraPermissionAsked
    ) {

        site ?: return@LaunchedEffect

        geofenceCheckPassed = null

        if (!locationGranted) {

            if (!locationAsked) {
                // Wait for the camera prompt to be answered first. Both
                // prompts used to fire at once; Android shows one permission
                // dialog at a time and answers a second concurrent request
                // with an automatic "denied", so location was refused
                // without the visitor ever seeing it. This effect re-runs
                // (it's keyed on the camera state) once that's settled.
                if (!cameraGranted && !cameraPermissionAsked) return@LaunchedEffect
                locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                return@LaunchedEffect
            }

            // Asked and refused. verifyGeofence(null) decides what that
            // means: a site with no fence is still allowed through, and a
            // fenced site is blocked with an explanation rather than a
            // spinner.
            geofenceCheckPassed = viewModel.verifyGeofence(null)
            return@LaunchedEffect
        }

        // Settle the fence before spending any fixes on it. An unfenced
        // site still fails open, exactly as before — there is nothing to be
        // outside of, so there is nothing worth asking the GPS.
        if (!viewModel.ensureGeofenceResolved()) {
            geofenceCheckPassed = true
            return@LaunchedEffect
        }

        // Several fixes, not one.
        //
        // This is the lock-out fix. Deciding from a single reading made the
        // gate a coin toss in exactly the conditions the app is used in: one
        // bad fix beside a stone wall sent a visitor standing in front of
        // the structure to the "you're outside" screen, which offered no way
        // back but leaving and re-entering — and then tossed the coin again.
        //
        // The first reading that puts them inside wins and the loop stops
        // there, so the common case still costs one fix and the extra ones
        // are only spent when the answer was going to be a refusal anyway.
        var lastUsableFix: Location? = null
        var inside = false

        for (attempt in 0 until GEOFENCE_GATE_ATTEMPTS) {

            if (attempt > 0) {
                // Spaced out on purpose. Fixes taken back to back come from
                // the same satellite geometry and the same reflections off
                // the same wall, so they repeat one another's error; a few
                // seconds apart gives the engine time to actually revise.
                kotlinx.coroutines.delay(GEOFENCE_GATE_RETRY_MS)
            }

            val fix = freshFix(fusedLocationClient)
                ?: recentLastLocation(fusedLocationClient)
                ?: continue

            lastUsableFix = fix

            if (viewModel.isWithinGeofence(fix)) {
                inside = true
                break
            }
        }

        geofenceCheckPassed = when {

            inside -> true

            // No fix at all is a GPS problem, not a location problem, and
            // saying "move closer" to somebody already standing there is
            // both wrong and maddening.
            lastUsableFix == null -> {
                viewModel.onLocationUnavailable()
                false
            }

            else -> {
                viewModel.onOutsideGeofence()
                false
            }
        }
    }

    // The boundary watch, for a visitor who walks out mid-session.
    //
    // Deliberately slow and deliberately forgiving. A single fix landing
    // outside the line means nothing — consumer GPS in a walled courtyard
    // wanders by tens of metres — so it takes several consecutive outside
    // readings before this concludes anything, and a failed or missing fix
    // is never counted as evidence of leaving. The cost of being wrong here
    // is snatching a structure away from someone who is standing right in
    // front of it, which is far worse than being slow to react.
    //
    // Even when it does fire, nothing already placed is disturbed: this only
    // blocks a NEW placement. See ArUiState.leftSiteArea.
    LaunchedEffect(geofenceCheckPassed, locationGranted) {

        if (geofenceCheckPassed != true || !locationGranted) return@LaunchedEffect
        if (!viewModel.hasResolvedGeofence()) return@LaunchedEffect

        var consecutiveOutsideFixes = 0

        // Timestamp of the reading the last decision was made from, so the
        // same one cannot be counted twice.
        var lastCountedFixNanos = 0L

        while (true) {

            kotlinx.coroutines.delay(GEOFENCE_WATCH_INTERVAL_MS)

            val fix = freshFix(fusedLocationClient)

            // No fix is not the same as being outside. Indoors, under stone
            // arches, or with the radio briefly busy, this happens routinely
            // and must not accumulate towards an exit.
            if (fix == null) continue

            // Nor is the SAME fix three times the same as three fixes.
            //
            // getCurrentLocation is allowed to hand back a recent cached
            // reading rather than computing a new one, so a single stale fix
            // taken while walking up to the site could be returned on every
            // pass and satisfy the confirmation count on its own. The
            // debounce below is only meaningful if each reading is genuinely
            // a new observation, so require the clock to have moved.
            if (fix.elapsedRealtimeNanos == lastCountedFixNanos) continue

            // A fix too vague to locate anyone cannot show they left. It is
            // still allowed to bring somebody back in below, because that
            // agrees with the benefit of the doubt the gate already gives.
            if (!viewModel.isFixTrustworthy(fix) && !viewModel.isWithinGeofence(fix)) continue

            lastCountedFixNanos = fix.elapsedRealtimeNanos

            if (viewModel.isWithinGeofence(fix)) {
                consecutiveOutsideFixes = 0
                viewModel.onGeofenceReentered()
            } else {
                consecutiveOutsideFixes++
                if (consecutiveOutsideFixes >= GEOFENCE_EXIT_CONFIRMATIONS) {
                    viewModel.onSustainedGeofenceExit()
                }
            }
        }
    }

    // Start the camera once this site's AR is cleared to arm. There is
    // nothing else to wait for — no photos to download, no localisation to
    // converge — so the geofence result is the whole condition.
    LaunchedEffect(site?.documentId, geofenceCheckPassed) {
        if (site == null || assetsReady) return@LaunchedEffect
        if (geofenceCheckPassed != true) return@LaunchedEffect

        assetsReady = true
        viewModel.onAssetsReady()
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
                    onBack = { navController.safePopBackStack() },
                    onRetry = if (uiState.unavailableRetryable) {
                        { viewModel.loadSite(siteId) }
                    } else {
                        null
                    }
                )
            }

            ArStage.OUTSIDE_GEOFENCE -> {
                OutsideSiteState(
                    siteName = site?.siteName.orEmpty(),
                    reason = uiState.outsideGeofenceReason,
                    onRetry = {
                        // Order matters: move the stage back to checking
                        // first so the spinner replaces this screen at once,
                        // then re-key the gate. The other way round leaves
                        // the refusal on display while the recheck runs,
                        // which reads as the button having done nothing.
                        viewModel.onGeofenceRecheckStarted()

                        locationGranted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED

                        if (!locationGranted) {
                            if (locationDenials >= 2) {
                                // Android won't show the prompt again; the
                                // settings page is the only way to grant it.
                                locationSettingsLauncher.launch(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                                return@OutsideSiteState
                            }
                            // "Try again" used to only re-run the check with
                            // the permission still refused, so it never
                            // asked again. Clearing this makes the gate
                            // show the location prompt once more.
                            locationAsked = false
                        }

                        geofenceRetryToken++
                    },
                    onViewOnMap = {
                        // The document id, which is what the Map screen
                        // loads by. Passing the short site id here opened a
                        // map saying "This destination could not be found".
                        // The blocked AR screen is replaced, so Back from
                        // the map doesn't land on it again.
                        val target = site?.documentId?.takeIf { it.isNotBlank() }
                        if (target == null) {
                            navController.safePopBackStack()
                        } else {
                            navController.navigateSafely(Routes.Map.createRoute(target)) {
                                popUpTo(Routes.ARExperience.route) { inclusive = true }
                            }
                        }
                    },
                    onClose = { navController.safePopBackStack() }
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

        // No floating back button.
        //
        // Leaving AR is already a system gesture — the edge swipe, or the
        // navigation bar on devices that still show one — and this screen
        // installs no BackHandler, so that path works untouched. A second
        // way out drawn on top of the camera bought nothing and cost the
        // corner of the frame, which in a photography screen is the most
        // valuable corner there is.
        //
        // The blocked states keep their own explicit exits (UnavailableState
        // and OutsideSiteState both carry a Close), because those screens
        // are not the camera and a dead end with no visible way out is a
        // different problem from a redundant control.

        // Capture result: auto-clears so it doesn't sit over the scene. It's
        // drawn in the overlay's bottom stack, just above the shutter.
        uiState.captureMessage?.let { message ->
            LaunchedEffect(message) {
                kotlinx.coroutines.delay(2600)
                viewModel.clearCaptureMessage()
            }
        }

        // The filter picker for the photo just taken. Covers everything else
        // on screen until the visitor saves or discards it.
        // Back on the photo review closes it (discarding the shot) and
        // returns to the camera — it used to fall through and close AR
        // altogether. Composed after the screen's own Back handler, so it
        // wins while the review is open.
        androidx.activity.compose.BackHandler(enabled = pendingCapture != null) {
            pendingCapture = null
        }

        pendingCapture?.let { captured ->
            CaptureReviewSheet(
                original = captured,
                isSaving = uiState.isSavingCapture,
                // Offered, never applied. Original stays selected — the
                // suggestion is a hint about which look suits this kind of
                // structure, not a decision taken on the visitor's behalf.
                suggestedFilter = remember(uiState.site) {
                    suggestedFilterFor(uiState.site)
                },
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
    onCapture: () -> Unit
) {

    val context = LocalContext.current

    val engine = rememberEngine()

    val modelLoader = rememberModelLoader(engine)

    // Positional ambience read from the model's own `SFX_` nodes — see
    // ArSpatialAudio. Paused with the screen so a backgrounded app goes
    // quiet, and released with it.
    val sceneAudio = remember { ArSpatialAudio(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> sceneAudio.pause()
                Lifecycle.Event.ON_RESUME -> sceneAudio.resume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            sceneAudio.release()
        }
    }

    // The sun. Same intensity SceneView would have used by default, so the
    // brightness the neutral environment was tuned against is unchanged —
    // only warmer, and casting shadows, which is what grounds the buildings,
    // carriages and people on the plaza instead of letting them float on it.
    // Its direction is set relative to the placed model further down. When
    // ARCore light estimation is on, the estimated light replaces this one.
    val sunLight = rememberMainLightNode(engine) {
        color = SUN_COLOR
        isShadowCaster = true
    }

    // ------------------------------------------------ image-based lighting
    //
    // This is what was making the reconstruction render far darker in AR
    // than in the admin preview, and it is not a tuning problem: an AR scene
    // in SceneView 2.x ships with NO image-based lighting whatsoever.
    //
    // A plain SceneView loads `environments/neutral/neutral_ibl.ktx` as its
    // default environment — that is the soft, even studio light the preview
    // renders under. ARSceneView deliberately does not. Its init runs
    // `environment = createAREnvironment(engine)`, which forwards to the
    // overload whose `indirectLight` parameter defaults to null, so the
    // neutral IBL the parent had just built is replaced by nothing at all.
    //
    // What is left lighting the model is the single default directional
    // main light. Every surface not facing it falls away to near black, and
    // the matte correction applied further down (metallic 0, roughness 0.92
    // — correct for stone and plaster) removes the specular highlights that
    // would otherwise have lifted those faces. Hence a brown smear with the
    // roof readable and everything beneath it crushed.
    //
    // Turning ARCore's light estimation off could never have fixed this:
    // there was no neutral environment underneath for it to fall back to.
    //
    // So supply one explicitly. It must be passed as the ARScene parameter
    // below rather than set in onViewCreated — the composable re-assigns
    // sceneView.environment from that parameter on every update, so anything
    // set on the view directly is overwritten on the next recomposition.
    val environmentLoader = rememberEnvironmentLoader(engine)

    // The KTX ships inside the SceneView AAR, so there is nothing to add to
    // the app's own assets and nothing to download at runtime.
    //
    // Skybox stays null, which is what the loader returns when no skybox
    // buffer is given: an AR scene draws the camera feed where a skybox
    // would go, and giving it one would black out the real world.
    //
    // Environments created through the loader are owned by it and destroyed
    // when it is, so this needs no DisposableEffect of its own.
    val arEnvironment = remember(environmentLoader) {
        runCatching {
            environmentLoader.createKTX1Environment(
                iblAssetFile = NEUTRAL_IBL_ASSET
            )
        }.getOrElse { error ->
            // Falling back to an environment with neither IBL nor skybox is
            // exactly the old behaviour, so a missing asset costs the fix
            // rather than the screen.
            Log.w("HistouryAR", "Neutral IBL unavailable; AR will be lit by the main light only", error)
            environmentLoader.createEnvironment()
        }
    }

    // The scene graph. In SceneView 2.x nodes are managed by mutating this
    // list, not by declaring composable children.
    val childNodes = rememberNodes()

    val site = uiState.site

    var modelInstance by remember { mutableStateOf<ModelInstance?>(null) }

    /**
     * Bumped to force a clean reload of the GLB. "Reset Placement" no
     * longer does (it keeps the loaded model — see resetManualPlacement);
     * kept as a key of the model-loading effect for a future retry path.
     */
    var modelReloadToken by remember { mutableStateOf(0) }

    // ----- anchoring bookkeeping -----

    /** What the model's size is derived from; set when the anchor is made. */
    var scaleBasis by remember { mutableStateOf<ScaleBasis?>(null) }

    var anchor by remember { mutableStateOf<Anchor?>(null) }


    // Prevents the per-frame callback from creating several anchors before
    // the first one has been attached.
    var anchorCreated by remember { mutableStateOf(false) }

    var placedNode by remember { mutableStateOf<AnchorNode?>(null) }

    // Kept separately so manual adjustments can be applied to it.
    var modelNode by remember { mutableStateOf<ModelNode?>(null) }

    // The model node taken off its anchor by "Reset Placement", waiting to
    // be attached to the next anchor (one node per loaded model, ever).
    var retainedModelNode by remember { mutableStateOf<ModelNode?>(null) }

    // The first time a model actually stands in the scene this session —
    // what makes the visitor eligible for the rating prompt on the way out.
    //
    // Keyed on the MODEL NODE being attached, not merely on an anchor: an
    // anchor is made on the tap even when the GLB never finished loading,
    // and the review prompt then asked visitors to rate a model they never
    // saw.
    LaunchedEffect(modelNode != null) {
        if (modelNode != null) viewModel.onModelPlaced()
    }

    // Manual alignment offsets, relative to the anchor.
    var manualPosition by remember { mutableStateOf(Position(0f, 0f, 0f)) }

    var manualYaw by remember { mutableStateOf(0f) }

    // Pinch-to-resize multiplier, layered on top of whatever base scale the
    // model would otherwise use. Kept separate from that base factor — see
    // baseScaleFactor below — so a live pinch doesn't have to re-run the
    // (comparatively heavy) model-measurement effect on every gesture tick.
    var manualScaleMultiplier by remember { mutableStateOf(1f) }

    /** The scale factor from ScaleBasis alone, before manualScaleMultiplier. */
    var baseScaleFactor by remember { mutableStateOf(0f) }

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

    /**
     * How a screen drag maps onto the anchor's own X/Z axes, as
     * [rightX, rightZ, forwardX, forwardZ].
     *
     * Drag used to be applied straight to the anchor's local axes, which are
     * fixed at the instant of placement. That is fine while the visitor
     * stands where they placed it and wrong everywhere else: walk around to
     * the far side and dragging right moves the structure left, because
     * "right" still means whatever right meant when the anchor was born.
     *
     * So the camera's own ground-plane right/forward are re-expressed in the
     * anchor's frame every frame, and drags are applied through that. Drag
     * right, it goes right, from wherever the visitor happens to be standing.
     *
     * A plain FloatArray rather than Compose state on purpose: this is
     * rewritten on every AR frame, and making it observable would recompose
     * the whole overlay 30-60 times a second for a value only a gesture
     * callback ever reads.
     */
    val dragBasis = remember { FloatArray(4) { 0f }.also { it[0] = 1f; it[3] = 1f } }

    /** The world-space pose the aim reticle is currently over, from a
     *  screen-center hit test against a tracked horizontal plane. Null
     *  whenever the phone isn't currently pointed at a valid surface. */
    var groundReticlePose by remember { mutableStateOf<Pose?>(null) }

    // The reticle and grid are rewritten on every AR frame (30–60 times a
    // second). They used to be plain state variables of this composable, and
    // since this composable also passed them down, every write recomposed
    // the WHOLE AR screen — camera layer, cards and all — each frame, which
    // showed up as stutter and battery drain. They now live in a small
    // holder that only PlacementGuideOverlay's Canvas reads, inside its draw
    // phase, so a new frame just redraws those lines.
    val placementGuide = remember { PlacementGuideState() }

    // Not Compose state: written and read only on the AR frame callback and
    // in the effect that creates it, never by composition.
    val smokeEffectHolder = remember { SmokeEffectHolder() }

    /** groundReticlePose (or, once placed, the model's current ground point)
     *  projected to screen pixels, for the center indicator. Null hides it. */
    var reticleScreenPos by placementGuide::reticle

    /** Ground grid tile lines, already projected to screen pixels. Only
     *  populated before the structure is placed. */
    var groundGridScreenLines by placementGuide::gridLines

    /** Set by a tap on the aim surface, consumed on the next AR frame (where
     *  the actual Session/Anchor calls have to happen — see onSessionUpdated
     *  below) rather than acted on directly from the gesture callback. */
    var pendingPlaceTap by remember { mutableStateOf(false) }

    // Set from sessionConfiguration, consumed by onViewUpdated. The depth
    // occlusion material must only be applied when the session actually has
    // a depth mode enabled — otherwise the camera stream is shaded with a
    // material that never receives a depth texture.
    var depthSupported by remember { mutableStateOf(false) }

    // The live ARCore session, captured from onSessionCreated below.
    var arCoreSession by remember { mutableStateOf<Session?>(null) }

    // Off by default, but a real toggle again — NOT permanently off.
    //
    // A previous pass here hard-disabled this, reasoning that occlusion can
    // only ever clip the model against real-world objects and make it look
    // like it's "floating." That reasoning was incomplete: depth-based
    // occlusion is also what makes the reconstruction read as a structure
    // actually standing in the scene, correctly receding behind whatever's
    // genuinely nearer the camera (a passer-by, a pillar, the visitor's own
    // surroundings) — see the "Depth is what makes the reconstruction read
    // as a structure standing in the scene rather than a decal painted over
    // the camera feed" comment on sessionConfiguration below. With
    // occlusion permanently off, EVERY model draws in front of EVERYTHING
    // regardless of real depth, all the time — which is exactly what made
    // placement look worse/more "floating" after that change, not better.
    //
    // So this reverts to the original, deliberate compromise: off by
    // default, but available per-visitor via the debug panel for scenes
    // where correct occlusion helps the model sit in the scene rather than
    // look pasted on top of it. Either way this is a rendering-only decision
    // — see onViewUpdated below — and never affects the model's anchor,
    // position, rotation, or scale.
    var occlusionEnabled by remember { mutableStateOf(false) }

    // ------------------------------------------------------- photo mode
    //
    // Digital zoom for composing a photograph, and nothing else.
    //
    // This is NOT a camera zoom and deliberately so: ARCore owns the capture
    // session and publishes intrinsics that assume an uncropped frame, so
    // there is no supported way to drive CONTROL_ZOOM_RATIO or a crop region
    // underneath it, and doing so anyway would leave the model floating
    // against a feed it no longer matches.
    //
    // Instead the Filament viewport is enlarged around the surface's centre.
    // The camera feed is a fullscreen triangle in clip space and the model is
    // projected through the same viewport, so both scale by exactly the same
    // amount and stay in register — the frame is cropped, not re-aimed. It
    // cannot move the structure, because it never touches a transform: the
    // anchor, the node, its position, rotation and scale are all untouched,
    // and the only thing that changes is which rectangle of the render
    // reaches the glass.
    var cameraZoom by remember { mutableStateOf(1f) }

    // Whether the non-essential chrome is on screen. The shutter ignores
    // this — a camera that hides its own shutter is a camera you cannot use.
    var chromeVisible by remember { mutableStateOf(true) }

    // Bumped by a tap to restart the idle countdown below.
    var chromeWake by remember { mutableStateOf(0) }

    // The lock/unlock notice currently showing, if any.
    var arNotice by remember { mutableStateOf<ArNotice?>(null) }

    // What the stage was the last time the effect below ran, so a transition
    // can be told apart from merely being in a state.
    //
    // MANUAL_ALIGNING is both where a session starts and where unlocking
    // returns to, and only the second is worth announcing — without this,
    // every visitor would be told the model was unlocked before they had
    // placed one.
    var previousStage by remember { mutableStateOf<ArStage?>(null) }

    LaunchedEffect(uiState.stage) {

        val cameFrom = previousStage
        previousStage = uiState.stage

        arNotice = when {
            uiState.stage == ArStage.PLACED -> ArNotice.LOCKED
            cameFrom == ArStage.PLACED &&
                uiState.stage == ArStage.MANUAL_ALIGNING -> ArNotice.UNLOCKED
            else -> null
        }

        if (arNotice != null) {
            kotlinx.coroutines.delay(AR_NOTICE_MS)
            arNotice = null
        }
    }

    // Once locked, the interface gets out of the way.
    //
    // Only in PLACED: while the visitor is still aligning, the controls ARE
    // the task and hiding them would be sabotage. Leaving that stage — an
    // unlock, a reset — brings everything straight back.
    LaunchedEffect(uiState.stage, chromeWake) {

        if (uiState.stage != ArStage.PLACED) {
            chromeVisible = true
            return@LaunchedEffect
        }

        chromeVisible = true
        kotlinx.coroutines.delay(PHOTO_MODE_CHROME_IDLE_MS)
        chromeVisible = false
    }

    // Zoom belongs to a locked composition, so unlocking drops it. Keeping a
    // 3x crop while somebody drags the structure around would mean dragging
    // against a view that no longer shows where they are putting it.
    LaunchedEffect(uiState.stage) {
        if (uiState.stage != ArStage.PLACED) cameraZoom = 1f
    }

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
    // Survives leaving and reopening AR, which it has to: the choice only
    // takes effect when a session is created, so a value scoped to this
    // composable would reset itself on the very reopen that was meant to
    // apply it.
    val studioLighting = ArLightingPreference.useNeutralLighting

    // How much to lift the reconstruction's own base colour — see
    // MODEL_BRIGHTNESS_STEPS. Separate from the lighting mode above and
    // from anything to do with placement: it changes a material constant,
    // never a transform, and takes effect on the next frame rather than the
    // next session.
    val modelBrightness = ArLightingPreference.brightnessBoost

    // Brightness starts at 1.0x on every visit to AR.
    //
    // Unlike the lighting mode above, which has to outlive this screen
    // because it only takes effect when the next session is configured,
    // brightness applies on the next frame and carrying it forward is not
    // wanted: a lift dialled in for one structure under one sky should not
    // silently become the starting point for the next one.
    //
    // Reset on the way OUT rather than on the way in, so a configuration
    // change — a rotation, a theme switch — recomposes the screen without
    // throwing away an adjustment the visitor just made.
    DisposableEffect(Unit) {
        onDispose { ArLightingPreference.brightnessBoost = DEFAULT_MODEL_BRIGHTNESS }
    }

    /** Measured from the GLB, kept for the placement panel. */
    var measuredModelHeight by remember { mutableStateOf(0f) }
    var appliedScaleFactor by remember { mutableStateOf(0f) }

    // Animation clock, taken when the model finishes loading rather than
    // when the screen opens, so a plume always starts from its first frame
    // however long the download took.
    var animationStartNanos by remember { mutableStateOf(0L) }

    var animationCount by remember { mutableStateOf(0) }

    var showPlacementSettings by remember { mutableStateOf(false) }

    // Anchor/node cleanup lives further down, right after the ARScene(...)
    // call — see the comment there for why the position matters.

    // Usually already on the phone: Site Details and the arrival screen
    // start downloading the model in the background (ArModelCache).
    LaunchedEffect(site?.arModelUrl, modelReloadToken) {

        val url = site?.arModelUrl

        if (!url.isNullOrBlank()) {
            modelInstance = try {
                // Local first: a model already on the phone (or downloaded
                // from Offline Downloads, even an older version) opens
                // straight away with no network. Otherwise it joins the
                // background download Site Details / arrival started, if
                // it's still running, instead of starting over.
                val cachedModel = ArModelCache.get(context, url, site?.siteId)
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
                Log.e("HistouryAR", "Could not load AR model for ${site?.siteName}", error)
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

        // Frustum culling off for this model's renderables. gltfio gives a
        // skinned mesh a bounding box measured where it stood when loaded,
        // but then carries that box along with the mesh's parent node — so
        // for anything rigged that also travels (the kalesa horses), the box
        // drifts away from the geometry and the renderer skips a horse that
        // is squarely in view. A tabletop reconstruction is almost always
        // entirely on screen, so culling saves next to nothing here anyway.
        runCatching {
            val rm = engine.renderableManager
            instance.entities.forEach { entity ->
                if (rm.hasComponent(entity)) rm.setCulling(rm.getInstance(entity), false)
            }
        }.onFailure {
            Log.w("HistouryAR", "Could not disable culling", it)
        }
    }

    // Base-colour lift, kept in its own effect rather than folded into the
    // matte correction above.
    //
    // That one is keyed on the model alone because it runs once per load;
    // this one has to re-run whenever the level changes. Merging them would
    // mean re-keying the matte effect on brightness too — and that effect
    // also takes the animation clock baseline, so every brightness change
    // would silently restart the GLB's animations from frame one.
    //
    // Rendering only. Nothing here reads or writes the anchor, the node, or
    // any position, rotation, or scale.
    LaunchedEffect(modelInstance, modelBrightness) {

        val instance = modelInstance ?: return@LaunchedEffect

        runCatching {
            instance.materialInstances.forEach { material ->
                // Same per-material guard as the matte correction: a
                // material without a baseColorFactor slot throws rather
                // than ignoring the call, and one unusual material should
                // not cost every other material its brightness.
                runCatching {
                    material.setParameter(
                        "baseColorFactor",
                        modelBrightness,
                        modelBrightness,
                        modelBrightness,
                        1.0f
                    )
                }
            }
        }.onFailure {
            Log.w("HistouryAR", "Could not apply model brightness", it)
        }
    }

    // Attaches the model the moment both halves exist: the anchor and the
    // loaded model.
    //
    // Keyed on exactly those two. It used to carry a third key that was
    // rewritten on every tracked frame, which cancelled and relaunched this
    // coroutine dozens of times a second for the whole session even though
    // the body is a no-op once placedNode exists.
    LaunchedEffect(anchor, modelInstance) {

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

            // Reuse the node from before a "Reset Placement" when it belongs
            // to this same model, instead of building a second node over the
            // same instance.
            val node = retainedModelNode
                ?.takeIf { it.modelInstance === currentModel }
                ?: ModelNode(modelInstance = currentModel)
            retainedModelNode = null
            node.position = Position(0f, 0f, 0f)
            node.rotation = Rotation(0f, 0f, 0f)

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

            // Every renderable casts and receives, so each building, kalesa
            // and person drops a shadow onto the model's own plaza floor.
            node.isShadowCaster = true
            node.isShadowReceiver = true

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
            // Keep the sun where the model's painted sky puts it — low in the
            // west-southwest — however the visitor has turned the structure.
            sunLight.lightDirection = rotateByQuaternion(node.worldQuaternion, SUN_DIRECTION_MODEL_SPACE)
        }
    }

    // Sound sources are read from the model once it is standing in the
    // scene, and dropped again on reset or when the model goes away.
    LaunchedEffect(modelNode, modelInstance) {
        val instance = modelInstance
        if (modelNode != null && instance != null) sceneAudio.bind(engine, instance) else sceneAudio.unbind()
    }

    // Uniform scale for the model — one factor on all three axes, so the
    // structure's authored proportions are never stretched or squashed.
    //
    //  1. An explicit Model Size from the admin panel wins. It is a real
    //     measurement of the real structure.
    //  2. Otherwise the model is normalised to a sensible human-scale
    //     starting size and the visitor pinches from there.
    //
    // That second case is new and it matters. The old code had no fallback
    // at all: with no admin Model Size it returned early, leaving
    // baseScaleFactor at 0f — and because the pinch effect below bails out
    // on a zero base, pinch-to-resize and the +/- buttons became silently
    // dead while the model rendered at raw authored GLB units (a scan
    // authored at 30 units stood 30 m tall). That was survivable while most
    // sites were placed by image recognition; with manual placement the only
    // route in, it would have hit every site without an explicit size.
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
            modelLongest > 0.0001f -> DEFAULT_MANUAL_MODEL_SIZE_METERS / modelLongest
            // Nothing measurable at all: authored units, which at least
            // leaves the pinch multiplier something to work with.
            else -> 1f
        }

        // The pinch multiplier is applied in the separate, lightweight effect
        // just below rather than here, so a live pinch gesture doesn't re-run
        // measureModelHeight()/measureModelLongestSide() on every tick.
        baseScaleFactor = factor

        measuredModelHeight = modelHeight
    }

    // Combines the base factor above with the manual pinch multiplier.
    // Split out from the effect above so pinching only ever does this cheap
    // multiply-and-assign, never the model measurement.
    LaunchedEffect(modelNode, baseScaleFactor, manualScaleMultiplier) {
        val node = modelNode ?: return@LaunchedEffect
        if (baseScaleFactor <= 0f) return@LaunchedEffect
        val finalFactor = baseScaleFactor * manualScaleMultiplier
        node.scale = Scale(finalFactor, finalFactor, finalFactor)
        appliedScaleFactor = finalFactor
    }

    // "Reset Placement": drop the anchor, clear every piece of local
    // placement state, and hand control back to aiming so the next tap
    // starts a completely fresh placement.
    //
    // The model itself is KEPT and re-attached on the next placement. Reset
    // used to throw the ModelInstance away and load the GLB again, and the
    // old copy (with its textures) stayed in memory until the screen
    // closed — every reset added another full copy, which could run a
    // low-memory phone out of memory. The original reason for reloading was
    // that building a SECOND ModelNode over the same instance left two nodes
    // owning one set of Filament entities; reusing the SAME ModelNode (see
    // retainedModelNode) avoids that without a reload, and its position,
    // rotation and scale are re-applied from the reset values below.
    val resetManualPlacement: () -> Unit = {
        anchor?.detach()
        val oldAnchorNode = placedNode
        val keptNode = modelNode
        if (oldAnchorNode != null && keptNode != null) {
            runCatching { oldAnchorNode.removeChildNode(keptNode) }
        }
        oldAnchorNode?.let(childNodes::remove)
        retainedModelNode = keptNode

        anchor = null
        placedNode = null
        modelNode = null
        anchorCreated = false
        scaleBasis = null
        manualPosition = Position(0f, 0f, 0f)
        manualYaw = 0f
        manualScaleMultiplier = 1f
        baseScaleFactor = 0f
        groundReticlePose = null
        reticleScreenPos = null
        groundGridScreenLines = emptyList()
        pendingPlaceTap = false
        viewModel.resetManualPlacement()
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

            // Both of these go together: passing our own loader keeps the
            // composable from quietly building a second one, and the
            // environment is the neutral IBL assembled above, replacing the
            // unlit default an AR scene would otherwise use.
            environmentLoader = environmentLoader,
            environment = arEnvironment,

            mainLightNode = sunLight,

            // Shadows are off by default in a Filament view; PCF keeps their
            // edges soft enough to read as sunlight rather than a cut-out.
            onViewCreated = {
                view.setShadowingEnabled(true)
                view.setShadowType(com.google.android.filament.View.ShadowType.PCF)
            },

            childNodes = childNodes,

            planeRenderer = false,

            sessionConfiguration = { session, config ->

                // The one and only way the structure gets positioned: the
                // visitor aims at a real detected floor and taps. Plane
                // finding is therefore no longer an optional extra for a
                // fallback path — it is the whole placement mechanism, so it
                // runs for every session from the first frame.
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL

                // Geospatial is explicitly OFF. Not merely unused — asked
                // for and disabled, so no session ever pays for VPS power,
                // network, or a round trip to Google's localisation service.
                // The site's own latitude/longitude still gate access to AR
                // via its geofence, but they never reach the model's
                // transform. See the note on ArStage.
                config.geospatialMode = Config.GeospatialMode.DISABLED

                // Autofocus still earns its place: a sharp frame gives
                // ARCore better features to track the floor with, which is
                // what keeps a placed structure from drifting.
                config.focusMode = Config.FocusMode.AUTO

                // See the studioLighting comment above. DISABLED does not
                // mean unlit — it leaves the neutral environment supplied
                // via the `environment` parameter above in place rather than
                // letting ARCore's estimate override it. That only holds
                // because that environment is now explicitly provided; with
                // the stock AR environment, DISABLED did mean an unlit scene.
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
            },

            // Configuring depthMode is only half of it. SceneView renders the
            // camera feed through ARCameraStream, and that stream ships with
            // isDepthOcclusionEnabled = false, so the occlusion material is
            // never applied unless it is switched on here. Set per frame
            // rather than once at creation because the flag is only known
            // after the session is configured; the setter is a no-op when the
            // value hasn't changed. Whichever way this reads, it is
            // rendering-only and never influences AR placement — the
            // anchor's position, rotation, and scale are set elsewhere and
            // nothing here touches them.
            onViewUpdated = {

                cameraStream?.isDepthOcclusionEnabled = depthSupported && occlusionEnabled

                // With ARCore light estimation on, SceneView lights the model
                // from its own estimated light instead of sunLight; that one
                // is created shadowless, so give it the same shadows.
                mainLightEstimatedNode?.isShadowCaster = true

                // The digital zoom described on cameraZoom's declaration.
                //
                // Applied here rather than from the frame callback because
                // SceneView only assigns the viewport on resize, so this
                // sticks — and because reading cameraZoom makes this run on
                // exactly the recompositions where it changed, rather than
                // costing work on every one of the sixty frames a second
                // where it did not.
                //
                // The aspect ratio is preserved, which is what lets the
                // camera projection stay untouched: widening and heightening
                // by the same factor crops the render without distorting it,
                // so nothing has to be reprojected and the feed and the model
                // cannot drift apart.
                val surfaceWidth = width
                val surfaceHeight = height

                if (surfaceWidth > 0 && surfaceHeight > 0) {

                    val zoomedWidth = (surfaceWidth * cameraZoom).toInt()
                    val zoomedHeight = (surfaceHeight * cameraZoom).toInt()

                    // A negative origin is the whole trick: the render is
                    // larger than the surface and centred on it, so the
                    // surface shows the middle of it.
                    view.viewport = Viewport(
                        (surfaceWidth - zoomedWidth) / 2,
                        (surfaceHeight - zoomedHeight) / 2,
                        zoomedWidth,
                        zoomedHeight
                    )
                }
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

                // Smoke plumes follow the ruins and face the camera; the
                // effect throttles itself to 30 updates a second.
                smokeEffectHolder.effect?.update(
                    frame.timestamp,
                    frame.camera.pose,
                    frame.camera.trackingState == TrackingState.TRACKING
                )

                // ----------------------------------------- animation driver

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

                // Ambient sound follows the (now animated) sources. Throttled
                // inside ArSpatialAudio, so calling it every frame is cheap.
                if (modelNode != null && frame.camera.trackingState == TrackingState.TRACKING) {
                    sceneAudio.update(frame.camera.pose, appliedScaleFactor)
                }

                // -------------------------------------------- drag basis

                // Re-express the camera's ground-plane right/forward in the
                // anchor's own frame, so a drag means the same thing from
                // wherever the visitor is standing. Cheap: six dot products
                // and two normalisations, no allocation beyond ARCore's own
                // axis arrays.
                val anchorPoseForBasis = anchor?.pose

                if (anchorPoseForBasis != null) {

                    val cameraPose = frame.camera.pose

                    val camRight = cameraPose.xAxis
                    val camBack = cameraPose.zAxis

                    // Flattened onto the ground, because the structure only
                    // ever moves across the floor — tilting the phone up
                    // must not turn a sideways drag into a vertical one.
                    var rx = camRight[0]
                    var rz = camRight[2]
                    val rLen = sqrt(rx * rx + rz * rz)

                    // Camera looks down -Z, so forward on the ground is the
                    // negated, flattened Z axis.
                    var fx = -camBack[0]
                    var fz = -camBack[2]
                    val fLen = sqrt(fx * fx + fz * fz)

                    if (rLen > 0.0001f && fLen > 0.0001f) {

                        rx /= rLen
                        rz /= rLen
                        fx /= fLen
                        fz /= fLen

                        val anchorX = anchorPoseForBasis.xAxis
                        val anchorZ = anchorPoseForBasis.zAxis

                        var ax = anchorX[0]
                        var az = anchorX[2]
                        val aLen = sqrt(ax * ax + az * az)

                        var bx = anchorZ[0]
                        var bz = anchorZ[2]
                        val bLen = sqrt(bx * bx + bz * bz)

                        if (aLen > 0.0001f && bLen > 0.0001f) {

                            ax /= aLen
                            az /= aLen
                            bx /= bLen
                            bz /= bLen

                            // Components of camera-right and camera-forward
                            // along the anchor's own X and Z.
                            dragBasis[0] = rx * ax + rz * az
                            dragBasis[1] = rx * bx + rz * bz
                            dragBasis[2] = fx * ax + fz * az
                            dragBasis[3] = fx * bx + fz * bz
                        }
                    }
                }

                // ------------------------------------------------ placement

                // PLACEMENT HAPPENS EXACTLY ONCE.
                //
                // The anchor below is an ordinary world anchor from
                // session.createAnchor on a real detected floor, created a
                // single time. The model is a child of that anchor's node
                // and nothing moves it afterwards, so walking, turning or
                // tilting the phone only changes the camera view — the
                // structure stays where it was put.
                //
                // Nothing in this frame callback reads the camera's position
                // and feeds it back into the model's transform. The drag
                // basis above reads the camera's ORIENTATION, and only to
                // decide which way a finger drag should push the structure;
                // it is never applied on its own.

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

                            hit?.hitPose
                        } else {
                            null
                        }

                        groundReticlePose = hitPose

                        // Drives the coaching card: "move your phone slowly"
                        // until there is real floor to aim at, then the grid.
                        viewModel.onGroundFound(hitPose != null)

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
                        // here, on the AR frame thread.
                        if (pendingPlaceTap) {
                            pendingPlaceTap = false

                            // The geofence has the final say right up to the
                            // moment of placement: someone who has walked
                            // out of the site does not get to deploy its
                            // structure from the pavement outside.
                            if (hitPose != null && !uiState.leftSiteArea) {
                                try {
                                    // Hand placement has nothing to measure
                                    // the structure against, so the scale
                                    // effect falls back to the admin's Model
                                    // Size when set and a normalised default
                                    // otherwise.
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
                    // Locked: nothing to aim at, so hide the overlay rather
                    // than leave it frozen on its last position.
                    reticleScreenPos = null
                    groundGridScreenLines = emptyList()
                    groundReticlePose = null
                }

                // The per-frame placement readout that used to live here is
                // gone along with the debug panel it fed.
                //
                // It was not free: it measured an anchor-to-camera distance,
                // walked every trackable to count planes, and then wrote a
                // fresh object into Compose state on EVERY tracked frame,
                // which recomposed the whole overlay dozens of times a
                // second for readouts nobody outside development read. The
                // three values the new placement panel shows — anchor,
                // model height, occlusion — all come from state that changes
                // a handful of times per session, so they are read straight
                // from it and cost nothing per frame.
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
            }
        }

        // Optional smoke plumes (admin panel: AR scene → Smoke), drawn from
        // the small effects/mobile-smoke.glb asset beside the ruins — see
        // MobileSmokeEffect. That class and the admin settings existed, but
        // nothing ever created the effect, so smoke never appeared.
        //
        // Keyed on the placed model node: created once the model stands in
        // the scene, destroyed on Reset Placement (the node goes away) and
        // on leaving the screen. Declared AFTER ARScene(...) for the same
        // reason as the anchor clean-up above — it must be torn down while
        // the engine and model loader still exist.
        val smokeSettings = site?.arSmoke
        DisposableEffect(modelNode, smokeSettings) {
            val ruins = modelNode
            val settings = smokeSettings?.sanitized()
            if (ruins != null && settings != null && settings.enabled && settings.emitters.isNotEmpty()) {
                smokeEffectHolder.effect = runCatching {
                    val smokeInstance = modelLoader.createModelInstance(SMOKE_ASSET_PATH)
                    MobileSmokeEffect(modelLoader, smokeInstance, ruins, settings)
                }.onFailure {
                    Log.w("HistouryAR", "Smoke effect unavailable", it)
                }.getOrNull()
            }
            onDispose {
                smokeEffectHolder.effect?.destroy()
                smokeEffectHolder.effect = null
            }
        }

        // Ground grid + center reticle. Purely decorative — a plain Canvas
        // with no pointerInput of its own, so it never intercepts the
        // gestures the Box below (or ArOverlay's buttons) need to receive.
        // Hidden automatically whenever there's nothing to show: both
        // lists/positions are cleared in onSessionUpdated the moment
        // placement is locked.
        PlacementGuideOverlay(placementGuide)

        // The GLB can take a while on a slow connection (it's usually
        // prefetched, but not always). Nothing used to say so: the camera
        // was up, a tap made an anchor, and no structure appeared. Shown
        // until the model is in memory; a load failure moves the screen to
        // its error state instead.
        val modelStillLoading = !site?.arModelUrl.isNullOrBlank() &&
            modelInstance == null &&
            (uiState.stage == ArStage.MANUAL_ALIGNING || uiState.stage == ArStage.PLACED)

        if (modelStillLoading) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Loading 3D model…",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Compass, only while there's an active placement decision to make —
        // gone the instant the structure is locked, so it never lingers over
        // the finished view.
        if (uiState.manualPlacementUnlocked) {
            CompassIndicator(
                headingDegrees = rememberCompassHeading(),
                modifier = Modifier
                    // statusBarsPadding, like the back and info buttons this
                    // now shares a row with. Without it the disc sat 16dp
                    // from the physical top of the screen, which on a phone
                    // with a centred cutout put it under the camera hole and
                    // left it at a different height from the two controls
                    // either side of it.
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 4.dp)
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
            placement = ArPlacementSummary(
                anchorAttached = anchor != null,
                // Model units times the factor actually applied to the node,
                // which is what makes this a real-world height rather than
                // an arbitrary number out of the GLB.
                modelHeightMeters = measuredModelHeight * appliedScaleFactor,
                occlusionSupported = depthSupported,
                occlusionOn = depthSupported && occlusionEnabled
            ),
            showPlacementSettings = showPlacementSettings,
            onTogglePlacementSettings = { showPlacementSettings = !showPlacementSettings },
            occlusionEnabled = occlusionEnabled,
            onToggleOcclusion = { occlusionEnabled = !occlusionEnabled },
            onShowInfo = { onShowSiteInfoChange(true) },
            chromeVisible = chromeVisible,
            onRevealChrome = { chromeWake++ },
            arNotice = arNotice,
            // Double tap is the unlock. It only ever reaches here from a
            // gesture that stayed still and lifted twice, so a pinch, a
            // drag or a twist cannot land on it, and the shutter consumes
            // its own taps before this surface sees them.
            onDoubleTap = {
                if (uiState.stage == ArStage.PLACED) {
                    viewModel.unlockManualPlacement()
                }
            },
            cameraZoom = cameraZoom,
            onCameraZoom = { factor ->
                // Clamped, and clamped here rather than in the gesture, so
                // the pinch can be pushed past the end and come straight
                // back without having to travel through the slack that an
                // unclamped accumulator would have banked.
                cameraZoom = (cameraZoom * factor)
                    .coerceIn(MIN_CAMERA_ZOOM, MAX_CAMERA_ZOOM)
            },
            modelBrightness = modelBrightness,
            onCycleBrightness = {
                // Wraps rather than stopping at the top, so the control
                // always comes back round to the untouched 1.0 look instead
                // of dead-ending at maximum with no way down. A level that
                // somehow isn't one of the steps also resolves to 1.0.
                val next = MODEL_BRIGHTNESS_STEPS.indexOf(modelBrightness) + 1
                ArLightingPreference.brightnessBoost = MODEL_BRIGHTNESS_STEPS[
                    if (next in MODEL_BRIGHTNESS_STEPS.indices) next else 0
                ]
            },
            onCapture = onCapture,
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
                // Mapped through the camera's current ground orientation
                // (dragBasis, recomputed each frame above) rather than
                // straight onto the anchor's fixed axes, so "right" keeps
                // meaning right after the visitor has walked around the
                // structure. Drag down pulls it toward the viewer.
                val forwardComponent = -dy

                manualPosition = Position(
                    manualPosition.x +
                        (dx * dragBasis[0] + forwardComponent * dragBasis[2]) *
                        MANUAL_DRAG_METERS_PER_PIXEL,
                    manualPosition.y,
                    manualPosition.z +
                        (dx * dragBasis[1] + forwardComponent * dragBasis[3]) *
                        MANUAL_DRAG_METERS_PER_PIXEL
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
                //
                // Uniform by construction — one multiplier, applied to X, Y
                // and Z alike in the scale effect above, so the structure
                // can never be stretched out of proportion.
                manualScaleMultiplier = (manualScaleMultiplier * zoomFactor)
                    .coerceIn(MANUAL_SCALE_MIN, MANUAL_SCALE_MAX)
            },
            onRotate = { deltaDegrees ->
                // Same accumulation principle as scale: deltaDegrees is the
                // rotation SINCE THE LAST tick, added onto the running yaw
                // rather than replacing it, so the model tracks the twist
                // gesture continuously and simply stays at whatever angle it
                // reaches when the fingers lift — no snapping, no reset, and
                // never any automatic turn to face the camera.
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
private const val SMOKE_ASSET_PATH = "effects/mobile-smoke.glb"

private class SmokeEffectHolder {
    var effect: MobileSmokeEffect? = null
}

@Stable
private class PlacementGuideState {
    var reticle by mutableStateOf<Offset?>(null)
    var gridLines by mutableStateOf<List<Pair<Offset, Offset>>>(emptyList())
}

@Composable
private fun PlacementGuideOverlay(state: PlacementGuideState) {

    ComposeCanvas(modifier = Modifier.fillMaxSize()) {

        // Read HERE, in the draw phase, not in composition: a new frame's
        // points only re-run this block. Nothing to show draws nothing.
        val reticle = state.reticle
        val gridLines = state.gridLines
        if (reticle == null && gridLines.isEmpty()) return@ComposeCanvas

        // Leaded glass, not tinted plastic.
        //
        // The guide used to be flat translucent white lines with a coral dot
        // at the centre, and flat translucent anything over a camera feed
        // reads as a sheet of plastic laid on the lens — there is no
        // thickness to it and no sense that light is passing through.
        //
        // What makes glass look like glass is the edge: a dark came holding
        // each pane, a bright rim where the light catches it, and a faint
        // halo bleeding into what is behind. So every line here is drawn
        // three times, widest and darkest first, and the coral is gone —
        // white glass takes its colour from the scene it is standing in
        // front of, which for a reconstruction is exactly right.
        gridLines.forEach { (start, end) ->

            // Halation: the glow around a lit pane.
            drawLine(
                color = Color.White.copy(alpha = 0.07f),
                start = start,
                end = end,
                strokeWidth = 9f,
                cap = StrokeCap.Round
            )

            // The came — the dark lead that holds the pane, and the reason
            // the bright line on top reads as glass rather than as paint.
            drawLine(
                color = Color.Black.copy(alpha = 0.30f),
                start = start,
                end = end,
                strokeWidth = 4.5f,
                cap = StrokeCap.Round
            )

            // The lit edge of the glass itself.
            drawLine(
                color = Color.White.copy(alpha = 0.80f),
                start = start,
                end = end,
                strokeWidth = 1.6f,
                cap = StrokeCap.Round
            )
        }

        reticle?.let { center ->

            // Same three-pass treatment on the ring, so the target reads as
            // part of the same glasswork rather than as a separate widget.
            drawCircle(
                color = Color.White.copy(alpha = 0.07f),
                radius = 27f,
                center = center,
                style = Stroke(width = 10f)
            )

            drawCircle(
                color = Color.Black.copy(alpha = 0.30f),
                radius = 26f,
                center = center,
                style = Stroke(width = 5f)
            )

            drawCircle(
                color = Color.White.copy(alpha = 0.88f),
                radius = 26f,
                center = center,
                style = Stroke(width = 2f)
            )

            // The one piece of colour, and it is dispersion rather than a
            // tint: a sweep of pale cool-to-warm whites around an inner ring,
            // the way a bevelled edge throws light. Kept under half opacity
            // and off the centre, so at a glance the guide is simply white
            // and the colour only appears if somebody looks for it.
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFFDCE9FF),
                        Color(0xFFEFFFF6),
                        Color(0xFFFFF6DE),
                        Color(0xFFFFE9EC),
                        Color(0xFFDCE9FF)
                    ),
                    center = center
                ),
                radius = 15f,
                center = center,
                style = Stroke(width = 1.6f),
                alpha = 0.45f
            )

            // Centre: clear glass with a bright core, in place of the solid
            // coral dot. It still marks the exact point — a white core on a
            // dark ring is if anything easier to find against a sunlit
            // facade than a warm dot was.
            drawCircle(
                color = Color.Black.copy(alpha = 0.28f),
                radius = 5.5f,
                center = center
            )

            drawCircle(
                color = Color.White,
                radius = 3.5f,
                center = center
            )

            // Crosshair ticks, came and lit edge.
            val tickInner = 14f
            val tickOuter = 30f
            listOf(
                Offset(-1f, 0f), Offset(1f, 0f), Offset(0f, -1f), Offset(0f, 1f)
            ).forEach { direction ->

                val tickStart = Offset(
                    center.x + direction.x * tickInner,
                    center.y + direction.y * tickInner
                )
                val tickEnd = Offset(
                    center.x + direction.x * tickOuter,
                    center.y + direction.y * tickOuter
                )

                drawLine(
                    color = Color.Black.copy(alpha = 0.30f),
                    start = tickStart,
                    end = tickEnd,
                    strokeWidth = 5f,
                    cap = StrokeCap.Round
                )

                drawLine(
                    color = Color.White.copy(alpha = 0.92f),
                    start = tickStart,
                    end = tickEnd,
                    strokeWidth = 2f,
                    cap = StrokeCap.Round
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
    placement: ArPlacementSummary,
    showPlacementSettings: Boolean,
    onTogglePlacementSettings: () -> Unit,
    occlusionEnabled: Boolean,
    onToggleOcclusion: () -> Unit,
    onShowInfo: () -> Unit,
    chromeVisible: Boolean,
    onRevealChrome: () -> Unit,
    arNotice: ArNotice?,
    onDoubleTap: () -> Unit,
    cameraZoom: Float,
    onCameraZoom: (Float) -> Unit,
    modelBrightness: Float,
    onCycleBrightness: () -> Unit,
    onCapture: () -> Unit,
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

    // ----- first-run tutorials, one per stage of the experience -----

    TutorialTour(
        id = "ar_intro",
        enabled = uiState.stage == ArStage.MANUAL_ALIGNING,
        steps = listOf(
            TutorialStep(
                target = null,
                title = "See the past in AR",
                body = "Your camera shows the site, and Histoury places a 3D model of how it once looked. Hold your phone up and move slowly."
            ),
            TutorialStep(
                target = null,
                title = "First, find the ground",
                body = "Point the camera at the ground a few steps ahead and move it gently until a grid appears. Then tap where the model should stand."
            ),
            TutorialStep(
                target = "ar_settings",
                title = "Settings",
                body = "Adjust how the model is shown, like brightness and whether people and objects can appear in front of it."
            )
        )
    )

    TutorialTour(
        id = "ar_adjust",
        enabled = uiState.stage == ArStage.MANUAL_ALIGNING && manualAnchorPlaced,
        steps = listOf(
            TutorialStep(
                target = "ar_align_card",
                title = "Move it into place",
                body = "Drag with one finger to slide the model, pinch to resize it, and twist with two fingers to turn it. Use Height to raise or lower it."
            ),
            TutorialStep(
                target = "ar_scale_bar",
                title = "Fine-tune",
                body = "Prefer buttons? Make it bigger or smaller, or turn it left or right a little at a time."
            ),
            TutorialStep(
                target = "ar_lock",
                title = "Lock it in place",
                body = "Once it lines up with the real site, lock it so it stays put while you walk around and take photos."
            )
        )
    )

    TutorialTour(
        id = "ar_placed",
        enabled = uiState.stage == ArStage.PLACED,
        steps = listOf(
            TutorialStep(
                target = "ar_capture",
                title = "Take a photo",
                body = "Capture the site with its reconstruction. Next you can pick a period filter, like Old Photograph, then save it to your phone and Gallery."
            ),
            TutorialStep(
                target = "ar_info",
                title = "About this site",
                body = "Read the site's history without leaving AR."
            ),
            TutorialStep(
                target = "ar_lock",
                title = "Need to move it again?",
                body = "Tap Unlock, or double-tap the screen, to adjust the model again."
            )
        )
    )

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
                        //
                        // It now reads two things out of the gestures it
                        // swallows, rather than only discarding them: the
                        // distance between two fingers, which drives the
                        // digital zoom, and whether a touch finished without
                        // ever becoming a drag, which is a tap and brings the
                        // hidden controls back.
                        //
                        // Written out by hand rather than layering
                        // detectTransformGestures and detectTapGestures on
                        // top, because those consume only once their own slop
                        // is exceeded — and the hole that leaves is exactly
                        // the one this shield exists to close: a thumb
                        // resting on the glass and creeping a few pixels
                        // while its owner walks closer. Here every change is
                        // consumed from the first frame of the gesture,
                        // whatever it turns out to be.
                        // Carried ACROSS gestures, which is what makes a
                        // double tap detectable at all: each awaitEachGesture
                        // body handles one touch from press to release, so
                        // the memory of the previous one has to live out here
                        // in the pointerInput block.
                        var lastTapUptime = 0L
                        var lastTapPosition = Offset.Zero

                        awaitEachGesture {

                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()

                            var movedBeyondTap = false
                            var previousSpan = 0f

                            while (true) {

                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }

                                if (pressed.isEmpty()) break

                                if (pressed.size >= 2) {

                                    val span = (pressed[0].position - pressed[1].position)
                                        .getDistance()

                                    // Ratio against the previous frame's
                                    // span, which is what makes the zoom
                                    // track the fingers continuously the way
                                    // a camera app does, instead of stepping.
                                    if (previousSpan > 0f && span > 0f) {
                                        val factor = span / previousSpan
                                        if (factor != 1f) onCameraZoom(factor)
                                        movedBeyondTap = true
                                    }

                                    previousSpan = span

                                } else {

                                    // A second finger lifting must not be
                                    // read as a huge pinch on the next frame.
                                    previousSpan = 0f

                                    if ((pressed[0].position - down.position).getDistance() >
                                        viewConfiguration.touchSlop
                                    ) {
                                        movedBeyondTap = true
                                    }
                                }

                                event.changes.forEach { it.consume() }
                            }

                            // Anything that became a drag or a pinch is not a
                            // tap and ends here. That is what keeps zooming,
                            // dragging and twisting from ever unlocking the
                            // structure: they have all set movedBeyondTap
                            // long before a finger lifts.
                            if (movedBeyondTap) return@awaitEachGesture

                            val now = down.uptimeMillis

                            val isDoubleTap =
                                lastTapUptime > 0L &&
                                    now - lastTapUptime <= viewConfiguration.doubleTapTimeoutMillis &&
                                    (down.position - lastTapPosition).getDistance() <=
                                    viewConfiguration.touchSlop * 2f

                            if (isDoubleTap) {

                                // Cleared rather than updated, so three taps
                                // in a row are one double tap and then a
                                // fresh single — not two overlapping
                                // unlocks off the middle tap.
                                lastTapUptime = 0L
                                onDoubleTap()

                            } else {

                                lastTapUptime = now
                                lastTapPosition = down.position

                                // Fired immediately rather than after waiting
                                // out the double-tap window. Revealing the
                                // controls is harmless and reversible, and
                                // holding every single tap for 300ms to find
                                // out whether a second one is coming would
                                // make the common gesture feel broken to
                                // save the rare one a redundant reveal.
                                onRevealChrome()
                            }
                        }
                    }
            )
        }

        // ------------------------------------------------- the status band
        //
        // One column, not five things each positioned by hand.
        //
        // They used to be independent children of the overlay's Box, every
        // one of them anchored at the same 90dp from the top: the coaching
        // card, the left-the-site warning, the align card, the locked pill
        // and the settings chip, plus the capture receipt from the layer
        // above. Whenever two were on screen together they were drawn on top
        // of one another, which is most of the time — the settings chip is
        // visible in every stage, so it collided with whichever card the
        // stage happened to be showing.
        //
        // Stacking them makes the overlap impossible rather than unlikely.
        // A hidden AnimatedVisibility measures to zero height and the column
        // closes up around it, so whatever is showing sits directly under
        // whatever is above it, in every combination, without any stage
        // needing to know what else might be on screen.
        //
        // Spacing goes INSIDE each block rather than on the column's
        // arrangement, because Arrangement.spacedBy would also space the
        // hidden ones and push the visible card down by a gap per absent
        // sibling.
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                // Measured from below the status bar rather than from the
                // physical top of the screen, so the gap is the same on a
                // phone with a tall cutout as on one without. 72dp clears
                // the row above it — the 64dp compass disc, and the back and
                // info buttons either side of it — with a little air left.
                .statusBarsPadding()
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                .padding(top = 72.dp, start = 20.dp, end = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Placement settings. Folded away with the rest of the chrome in
            // photo mode — adjusting occlusion or brightness is a setup-time
            // concern, and a visitor framing a photograph is past it.
            //
            // Start-aligned inside the centred column so it keeps the
            // top-left position it reads best in, without being anchored
            // independently of the cards it has to stay clear of.
            //
            // The panel now opens from the settings icon in the corner above
            // rather than carrying its own labelled chip. Keeping it as the
            // first item in this column rather than floating it under the
            // icon is what stops an open panel from covering the placement
            // card: the column pushes everything else down instead.
            AnimatedVisibility(
                visible = showPlacementSettings && chromeVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Start)
            ) {
                PlacementSettingsPanel(
                    placement = placement,
                    occlusionEnabled = occlusionEnabled,
                    onToggleOcclusion = onToggleOcclusion,
                    modelBrightness = modelBrightness,
                    onCycleBrightness = onCycleBrightness
                )
            }

            // Coaching while ARCore has no floor to offer yet. There is
            // nothing being recognised and nothing being localised any more,
            // so this is the only thing a visitor ever waits on.
            AnimatedVisibility(
                visible = uiState.stage == ArStage.MANUAL_ALIGNING &&
                    !manualAnchorPlaced && !uiState.groundFound
            ) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    FindingGroundCard()
                }
            }

            // Walked out of the site while nothing is placed yet. The
            // structure belongs to this place, so it doesn't get deployed
            // from outside it.
            AnimatedVisibility(
                visible = uiState.leftSiteArea && !manualAnchorPlaced
            ) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    HintCard(
                        title = "You've left the site",
                        body = "This structure can only be placed while you're at the " +
                            "historical site itself. Head back inside the site area to " +
                            "place it."
                    )
                }
            }

            AnimatedVisibility(
                visible = uiState.stage == ArStage.MANUAL_ALIGNING &&
                    !uiState.leftSiteArea &&
                    (manualAnchorPlaced || uiState.groundFound)
            ) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.tutorialTarget("ar_align_card")) {
                        ManualAlignCard(
                            hasPlaced = manualAnchorPlaced,
                            onReset = onResetManual,
                            onNudgeHeight = onNudgeHeight
                        )
                    }
                }
            }

        }

        // ------------------------------------------------ the bottom stack
        //
        // One column anchored to the bottom, above the system navigation bar,
        // with each control stacked on the one below it and a fixed gap
        // between them.
        //
        // These used to be separately positioned at fixed distances from the
        // physical bottom edge (44, 116, 176 and 236dp). Those numbers
        // assumed sizes that weren't true — the 74dp shutter reached 118dp,
        // so the Lock pill at 116dp sat on top of it; the capture receipt at
        // 170dp landed on the zoom readout at 176dp — and none of them
        // accounted for a three-button navigation bar, which covered the
        // shutter. A column can't overlap itself, and the insets move the
        // whole stack up on phones that need it.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Capture receipt ("Saved to your Gallery"), just above the
            // controls that produced it.
            AnimatedVisibility(
                visible = uiState.captureMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Box(
                    modifier = Modifier
                        .padding(bottom = BOTTOM_STACK_GAP)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = uiState.captureMessage.orEmpty(),
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Lock and unlock notices. Shown once, on the transition — this
            // is how a visitor learns the double tap without the screen
            // carrying a permanent instruction.
            AnimatedVisibility(
                visible = arNotice != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Box(Modifier.padding(bottom = BOTTOM_STACK_GAP)) {
                    ArNoticeCard(
                        title = arNotice?.title.orEmpty(),
                        body = arNotice?.body.orEmpty()
                    )
                }
            }

            // The zoom readout, and only while it is saying something: at
            // 1.0x it would be a permanent label reporting nothing.
            AnimatedVisibility(
                visible = uiState.stage == ArStage.PLACED && cameraZoom > MIN_CAMERA_ZOOM + 0.01f,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Box(Modifier.padding(bottom = BOTTOM_STACK_GAP)) {
                    GlassPill(label = String.format("%.1fx", cameraZoom))
                }
            }

            // Lock and unlock, as one floating glass control directly above
            // the shutter (or the resize bar) in both states, so it doesn't
            // appear to jump when the state changes.
            AnimatedVisibility(
                visible = chromeVisible && (
                    uiState.stage == ArStage.PLACED ||
                        (uiState.stage == ArStage.MANUAL_ALIGNING &&
                            manualAnchorPlaced &&
                            !uiState.leftSiteArea)
                    ),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                val locked = uiState.stage == ArStage.PLACED
                Box(Modifier.padding(bottom = BOTTOM_STACK_GAP)) {
                    Box(Modifier.tutorialTarget("ar_lock")) {
                        GlassPill(
                            icon = if (locked) Icons.Default.LockOpen else Icons.Default.Lock,
                            label = if (locked) "Unlock" else "Lock in Place",
                            onClick = if (locked) onUnlockManual else onLockManual
                        )
                    }
                }
            }

            // The bottom slot: the resize/rotate bar while adjusting, the
            // shutter once placed. The stages are exclusive, so the two never
            // show together.
            AnimatedVisibility(
                visible = uiState.stage == ArStage.MANUAL_ALIGNING && manualAnchorPlaced,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Box(Modifier.tutorialTarget("ar_scale_bar")) {
                    ManualScaleRotateBar(
                        onScaleDown = { onScale(1f / MANUAL_SCALE_STEP_FACTOR) },
                        onScaleUp = { onScale(MANUAL_SCALE_STEP_FACTOR) },
                        onRotateLeft = { onRotate(-MANUAL_ROTATE_STEP_DEGREES) },
                        onRotateRight = { onRotate(MANUAL_ROTATE_STEP_DEGREES) }
                    )
                }
            }

            // Visible only once the model is PLACED. Offering it earlier
            // would mean a photo of an empty facade.
            AnimatedVisibility(
                visible = uiState.stage == ArStage.PLACED,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Box(Modifier.tutorialTarget("ar_capture")) {
                    CaptureButton(
                        isSaving = uiState.isSavingCapture,
                        onClick = onCapture
                    )
                }
            }
        }

        // Settings, in the corner the back button used to hold.
        //
        // An icon rather than the "Placement settings" chip it replaces: a
        // permanent line of text naming a control is how an AR screen starts
        // reading as a configuration page, and a tuning glyph carries it
        // without spending a strip of the frame. The label survives as the
        // content description and the long-press hint, so it is still there
        // for anyone who needs it.
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Box(Modifier.tutorialTarget("ar_settings")) {
                GlassIconButton(
                    icon = Icons.Default.Tune,
                    label = "Placement settings",
                    onClick = onTogglePlacementSettings
                )
            }
        }

        // Site information, mirroring it in the opposite corner so the two
        // read as a deliberate pair.
        AnimatedVisibility(
            visible = uiState.stage == ArStage.PLACED && chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Box(Modifier.tutorialTarget("ar_info")) {
                GlassIconButton(
                    icon = Icons.Default.Info,
                    label = "Site information",
                    onClick = onShowInfo
                )
            }
        }

    }
}

/**
 * The collapsible settings card, lifted out of the overlay so the status
 * band above can be a plain column of siblings rather than a pile of
 * separately anchored boxes.
 */
@Composable
private fun PlacementSettingsPanel(
    placement: ArPlacementSummary,
    occlusionEnabled: Boolean,
    onToggleOcclusion: () -> Unit,
    modelBrightness: Float,
    onCycleBrightness: () -> Unit
) {

    // Laid out like ordinary phone settings: a name, one short line saying
    // what it does, and the control. It used to be status readouts plus two
    // buttons whose labels were whole sentences ("Occlusion on: real objects
    // can hide the model — tap to disable") and a footnote.
    Column(
        modifier = Modifier
            .widthIn(max = 300.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .border(
                0.8.dp,
                Color.White.copy(alpha = 0.18f),
                RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {

        // One line of status the visitor can act on.
        Text(
            text = when {
                !placement.anchorAttached -> "Model not placed yet"
                placement.modelHeightMeters > 0f ->
                    String.format("Model placed \u00b7 %.1f m tall", placement.modelHeightMeters)
                else -> "Model placed"
            },
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.7f)
        )

        Spacer(Modifier.height(6.dp))

        ArSettingRow(
            title = "Occlusion",
            description = if (placement.occlusionSupported) {
                "Lets real objects appear in front of the model."
            } else {
                "Not supported on this phone."
            },
            onClick = if (placement.occlusionSupported) onToggleOcclusion else null
        ) {
            Switch(
                checked = placement.occlusionSupported && occlusionEnabled,
                onCheckedChange = if (placement.occlusionSupported) {
                    { onToggleOcclusion() }
                } else {
                    null
                },
                enabled = placement.occlusionSupported,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Primary,
                    uncheckedThumbColor = Color.White.copy(alpha = 0.9f),
                    uncheckedTrackColor = Color.White.copy(alpha = 0.25f),
                    uncheckedBorderColor = Color.Transparent
                )
            )
        }

        ArSettingRow(
            title = "Model brightness",
            description = "Tap to make the model lighter or darker.",
            onClick = onCycleBrightness
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.18f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = String.format("%.2fx", modelBrightness),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * One row of the AR settings panel: name, one short description, and the
 * control on the right. The whole row is the tap target (at least 48dp),
 * not just the small control.
 */
@Composable
private fun ArSettingRow(
    title: String,
    description: String,
    onClick: (() -> Unit)?,
    control: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                text = description,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
        Spacer(Modifier.width(12.dp))
        control()
    }
}

/**
 * The two things worth interrupting for, and only on the transition.
 *
 * Both exist to teach the double tap without the screen having to carry a
 * permanent instruction: a control that explains itself forever is a control
 * nobody reads after the first time, and this one is sitting on top of a
 * reconstruction somebody walked across a walled city to look at.
 */
private enum class ArNotice(val title: String, val body: String) {

    LOCKED(
        title = "AR Locked",
        body = "Double-tap the screen to unlock."
    ),

    UNLOCKED(
        title = "AR Unlocked",
        body = "You can move, rotate, or resize the model again."
    )
}

/**
 * The glass card those notices appear in.
 *
 * Two lines rather than one, because the title alone states what happened
 * and the body alone states what to do, and a visitor who has just locked a
 * structure needs both. Non-interactive by design — it carries no dismiss,
 * since anything that has to be dismissed is something else to deal with
 * while holding a phone up at a building.
 */
@Composable
private fun ArNoticeCard(title: String, body: String) {

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.42f))
            .border(
                width = 0.8.dp,
                color = Color.White.copy(alpha = 0.20f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = title,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(2.dp))

        Text(
            text = body,
            color = Color.White.copy(alpha = 0.78f),
            fontSize = 11.5.sp,
            textAlign = TextAlign.Center,
            lineHeight = 15.sp
        )
    }
}

/**
 * The compact translucent control used in photo mode.
 *
 * Glass rather than a solid card, because everything in this overlay is
 * sitting on top of the one thing the visitor came to look at. A dark,
 * lightly translucent fill with a hairline highlight along its edge reads as
 * a pane laid over the scene — the same trick iOS uses — and it stays
 * legible against both a bright noon facade and a dim arcade, which a plain
 * low-alpha scrim does not.
 *
 * No real backdrop blur: Compose's blur modifier cannot sample a SurfaceView
 * beneath it, which is exactly what the AR view is, so a blur here would
 * cost GPU time every frame and show nothing. The border and the layered
 * fill carry the effect instead, for free.
 */
@Composable
private fun GlassPill(
    label: String,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.32f))
            .border(
                width = 0.8.dp,
                color = Color.White.copy(alpha = 0.22f),
                shape = RoundedCornerShape(50)
            )
            // Two ways to be interactive, and they are not the same thing:
            // onClick makes the whole pill a button (lock, unlock), while
            // onAction hangs a secondary word off the end of a pill that is
            // otherwise a label. A pill never wants both, so whichever is
            // supplied is the one that reads.
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
        }

        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )

        if (actionLabel != null && onAction != null) {

            Spacer(Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(14.dp)
                    .background(Color.White.copy(alpha = 0.22f))
            )

            Spacer(Modifier.width(10.dp))

            Text(
                text = actionLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = AccentGold,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onAction
                )
            )
        }
    }
}

/**
 * The only thing a visitor ever waits for now.
 *
 * Nothing is being recognised and nothing is being localised — the whole
 * wait is ARCore building enough of a picture of the floor to put a
 * structure on. So this coaches the one action that actually helps (move,
 * slowly, looking down) instead of counting down a budget the visitor can't
 * influence.
 */
@Composable
private fun FindingGroundCard() {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.94f))
            .padding(16.dp)
    ) {

        Row(verticalAlignment = Alignment.CenterVertically) {

            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = Primary
            )

            Spacer(Modifier.width(10.dp))

            Text(
                text = "Looking for the ground",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = "Point the camera at the ground a few steps ahead and move " +
                "the phone slowly from side to side. A grid appears as soon as " +
                "there's a surface to place the structure on.",
            fontSize = 12.5.sp,
            color = TextSecondary,
            lineHeight = 17.sp
        )
    }
}

@Composable
private fun ManualAlignCard(
    hasPlaced: Boolean,
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

            // Two rows, not one.
            //
            // These four controls used to share a single Row with fixed
            // widths and no weights: two 40dp buttons, a spacer, "Reset",
            // and "Lock in Place", which comes to roughly 290dp of content
            // before the card's own 16dp padding and the overlay's 20dp
            // margin are added. On a 360dp-wide phone \u2014 which is the most
            // common Android width there is \u2014 that does not fit, and a Row
            // that does not fit does not wrap: it runs off the edge and the
            // primary action is the thing that gets clipped.
            //
            // Height gets its own labelled row, and the two actions below
            // share the width by weight, so they fit at any screen size and
            // land on far bigger tap targets than they had.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = "Height",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )

                NudgeButton(label = "\u2193", onClick = { onNudgeHeight(-MANUAL_HEIGHT_STEP) })

                NudgeButton(label = "\u2191", onClick = { onNudgeHeight(MANUAL_HEIGHT_STEP) })
            }

            Spacer(Modifier.height(10.dp))

            // Reset alone now. Lock in Place moved out to the floating
            // control above the shutter — it is the action this whole card
            // builds towards and the one a thumb reaches for most, and it
            // was sitting at the top of the screen.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, TextSecondary.copy(alpha = 0.4f), RoundedCornerShape(50))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onReset
                    )
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Reset placement",
                    color = TextSecondary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun NudgeButton(label: String, onClick: () -> Unit) {

    Box(
        // 48dp, not 40 — the platform's minimum touch target. These drive a
        // precise adjustment (height, scale, rotation) one tap at a time, so
        // a tap that lands on the wrong side of a too-small circle nudges
        // the model the wrong way rather than just missing a press.
        modifier = Modifier
            .size(48.dp)
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
        dragHandle = null,
        // The sheet's default insets lift its content above the navigation
        // bar, and with a transparent container that left a strip of bare
        // camera under the glass. The glass now runs to the bottom edge and
        // the inset is applied inside it instead (navigationBarsPadding
        // below), so text still clears the gesture bar.
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(CardSurface.copy(alpha = 0.72f))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                )
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
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
                    // 48dp, the platform's minimum touch target — the 30dp
                    // circle this replaces left a third of a typical
                    // fingertip's contact area missing the button entirely.
                    modifier = Modifier
                        .size(48.dp)
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
                        modifier = Modifier.size(20.dp)
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
                    com.histoury.app.util.LinkOpener.openWebLink(context, url)
                }
            )
        }
    }
}

/**
 * The round glass control used in the AR camera's corners.
 *
 * Replaces the pair of flat black discs that used to sit there. Same
 * treatment as [GlassPill] — a lightly translucent fill with a hairline
 * highlight along the rim — so every control floating over the camera feed
 * belongs to one set rather than each being its own shade of dark.
 *
 * The label is a content description rather than visible text. A camera
 * screen with its functions written out in words reads as a settings page,
 * and these two icons are conventional enough not to need it; a long press
 * surfaces the label for anyone who does.
 */
@Composable
private fun GlassIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {

    Box(
        // 14dp of inner padding around a 20dp icon lands the tappable circle
        // at 48dp — the platform's minimum touch target. At the old 9dp the
        // circle was only 38dp, on a screen held up one-handed while framing
        // a shot, which is exactly where a corner control needs its full
        // target, not less than it.
        modifier = Modifier
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
            .padding(16.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.32f))
            .border(0.8.dp, Color.White.copy(alpha = 0.22f), CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = label,
                onClick = onClick
            )
            .padding(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
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
    suggestedFilter: CaptureFilter?,
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
            // 14dp of inner padding around a 20dp icon: 48dp tappable
            // circle, the platform minimum, in place of the 40dp this had.
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
                .padding(14.dp)
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

            // Scrollable, because there are now seven looks and a fixed row
            // of seven would either squeeze each thumbnail past the point of
            // being a useful preview or quietly clip the last of them off a
            // narrow screen.
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                CaptureFilter.entries.forEach { filter ->
                    FilterOption(
                        filter = filter,
                        isSuggested = filter == suggestedFilter,
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
    isSuggested: Boolean,
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

        // Marked, not chosen. The site's own name says what kind of
        // structure this is and some looks genuinely suit some buildings
        // better, but which photograph somebody wants is not something the
        // app gets to decide — so this points, and Original stays selected
        // until a finger says otherwise.
        if (isSuggested) {
            Text(
                text = "Suggested",
                color = Primary,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp
            )
        }
    }
}


// ----------------------------------------------------------------- helpers

// ----- geofence gating -----

/**
 * How often the boundary watch asks for a fresh fix while AR is open.
 *
 * Deliberately slow. This is a courtesy check, not a security perimeter —
 * the real gate already ran before the camera started — and a tight polling
 * loop on PRIORITY_HIGH_ACCURACY would cost battery all session for no gain.
 */
private const val GEOFENCE_WATCH_INTERVAL_MS = 10_000L

/**
 * Consecutive outside-the-fence fixes before the visitor is treated as
 * having actually left (so roughly half a minute at the interval above).
 *
 * A single stray fix means nothing: consumer GPS beside a stone wall
 * routinely wanders tens of metres, which is exactly the situation this app
 * is used in. Failed or missing fixes never count towards this at all. The
 * cost of calling it wrong is taking a structure away from somebody standing
 * in front of it, so the bar is set high and the consequence is mild —
 * placement is blocked, nothing already placed is disturbed.
 */
private const val GEOFENCE_EXIT_CONFIRMATIONS = 3

/**
 * How many fixes the entry gate may spend before refusing.
 *
 * The loop stops at the first reading that puts the visitor inside, so this
 * is a ceiling on the bad case rather than a cost every visitor pays.
 */
private const val GEOFENCE_GATE_ATTEMPTS = 3

// ----- photo mode -----

/**
 * How long the chrome stays up after the structure is locked, or after a tap
 * brings it back.
 *
 * Long enough to read the pill and reach the unlock, short enough that
 * somebody lining up a photograph is not waiting on it.
 */
private const val PHOTO_MODE_CHROME_IDLE_MS = 3_500L

/**
 * How long a lock or unlock notice stays up.
 *
 * Two lines at this size take a beat over two seconds to read without
 * hurrying, and this one has to be read by somebody whose attention is on a
 * building rather than on a phone. Past about three seconds it stops being a
 * confirmation and starts being something in the way.
 */
private const val AR_NOTICE_MS = 2_800L

/** No zoom. Also the floor: this crops the render, it cannot widen it. */
private const val MIN_CAMERA_ZOOM = 1f

/**
 * The ceiling, chosen for how the result looks rather than for a round
 * number.
 *
 * This is a crop of an already-rendered frame, so every step costs real
 * detail in both the camera feed and the reconstruction. Past roughly three
 * times the image is visibly soft, and offering more would be offering a
 * worse photograph with a bigger number attached.
 */
private const val MAX_CAMERA_ZOOM = 3f

/** Spacing between those attempts, so each one is a genuinely new reading. */
private const val GEOFENCE_GATE_RETRY_MS = 2_500L

/**
 * How old a cached `lastLocation` may be and still be allowed to decide
 * whether somebody is standing at a historical site.
 *
 * The gate prefers a fresh fix and only reaches for the cache when the
 * engine produced nothing at all. That fallback used to accept whatever was
 * there — a reading from hours ago and possibly a different city, handed
 * full authority to lock the visitor out. Two minutes is about as stale as a
 * position can be while still describing where somebody is standing now.
 */
private const val GEOFENCE_CACHED_FIX_MAX_AGE_MS = 2 * 60 * 1_000L

/**
 * A fresh fix from the location engine, or null if it could not produce one.
 *
 * Deliberately not `lastLocation`: that returns whatever the device last
 * happened to record, which may be hours old and far away if nothing has
 * asked for a position since. `getCurrentLocation` asks for where the phone
 * is now.
 */
private suspend fun freshFix(client: FusedLocationProviderClient): Location? =
    try {
        client.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            CancellationTokenSource().token
        ).await()
    } catch (e: Exception) {
        null
    }

/**
 * The cached fix, but only if it is recent enough to still describe where
 * the phone is — see [GEOFENCE_CACHED_FIX_MAX_AGE_MS].
 *
 * Age is measured with `elapsedRealtimeNanos` against the monotonic clock
 * rather than with `time` against the wall clock, because the wall clock can
 * be wrong, adjusted, or in another time zone, and a fix that merely looks
 * recent is exactly what this is here to reject.
 */
private suspend fun recentLastLocation(client: FusedLocationProviderClient): Location? {

    val cached = try {
        client.lastLocation.await()
    } catch (e: Exception) {
        null
    } ?: return null

    val ageMs = (SystemClock.elapsedRealtimeNanos() - cached.elapsedRealtimeNanos) / 1_000_000L

    return cached.takeIf { ageMs in 0..GEOFENCE_CACHED_FIX_MAX_AGE_MS }
}

// ----- manual placement tuning -----

/** How far the structure slides per pixel of drag during manual alignment. */
private const val MANUAL_DRAG_METERS_PER_PIXEL = 0.02f

private const val MANUAL_HEIGHT_STEP = 0.25f

/**
 * The size a model is normalised to when the admin panel has no explicit
 * Model Size for the site — its longest side, in metres.
 *
 * Photogrammetry GLBs arrive in wildly different authored units; one scan's
 * "30" might be centimetres and another's metres. Normalising to a known,
 * human-scale starting point means every site opens at a sane size and the
 * visitor pinches from there, rather than being handed a structure that is
 * either microscopic or several storeys tall on the first frame.
 */
private const val DEFAULT_MANUAL_MODEL_SIZE_METERS = 2.5f

/**
 * Pinch-to-resize limits, as a multiple of the model's base size.
 *
 * Wider than they used to be, because manual placement is now the only way
 * a structure gets placed and it has to cover every way people want to look
 * at one: a tabletop model to walk around, something room-sized, or the
 * full-height reconstruction to stand inside. The old 0.4–2.5 range was
 * sized for nudging an already-correct automatic placement, which no longer
 * exists.
 */
private const val MANUAL_SCALE_MIN = 0.1f
private const val MANUAL_SCALE_MAX = 10f

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
 * What the placement panel shows.
 *
 * Small on purpose. The panel it feeds used to be a live debug readout
 * rebuilt on every tracked frame; each of these three values changes a
 * handful of times in a whole session, so the summary is assembled from
 * ordinary screen state at composition instead of being pushed from the
 * render loop.
 */
private data class ArPlacementSummary(
    /** Whether the structure has been pinned to the ground yet. */
    val anchorAttached: Boolean,
    /** The reconstruction's real-world height, in metres; 0 while measuring. */
    val modelHeightMeters: Float,
    /** Whether this device can do depth occlusion at all. */
    val occlusionSupported: Boolean,
    /** Whether it is currently switched on. */
    val occlusionOn: Boolean
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

    /**
     * Multiplier applied to the reconstruction's base colour — see
     * MODEL_BRIGHTNESS_STEPS below. Held here for the same reason as the
     * lighting mode, so a visitor's choice survives leaving and reopening
     * AR, even though unlike the lighting mode this one applies at once.
     */
    var brightnessBoost by mutableStateOf(DEFAULT_MODEL_BRIGHTNESS)
}

private const val MATTE_METALLIC = 0.0f
private const val MATTE_ROUGHNESS = 0.92f

/**
 * The neutral studio IBL that a plain SceneView uses by default and an
 * ARSceneView does not — see the environment block in the screen above.
 *
 * Packaged inside the SceneView AAR rather than this app, so it needs no
 * entry under `app/src/main/assets`. Should a future SceneView move or drop
 * it, the loader throws, the fallback logs, and AR renders as it did before
 * this was added rather than failing to open.
 */
private const val NEUTRAL_IBL_ASSET = "environments/neutral/neutral_ibl.ktx"

/**
 * Brightness lift for the reconstruction itself, independent of how the
 * scene around it is lit.
 *
 * Neutral lighting keeps a model legible in a dim room, but it is a fixed,
 * moderate environment — it does not brighten to match where the phone
 * actually is. Outdoors the camera feed is very bright while the model is
 * still lit by that same fixed environment, so the reconstruction reads as
 * dark against its own surroundings even though nothing about it is
 * technically wrong. Photogrammetry compounds this: a scan captured under
 * overcast light or in shade carries that dimness in its base colour
 * texture, and the matte surface response above — deliberately, correctly —
 * removes the specular highlights that would otherwise lift it.
 *
 * So this multiplies `baseColorFactor`, the texture's own multiplier,
 * rather than touching any light. That brightens the reconstruction
 * wherever it is viewed, applies the instant it changes rather than at the
 * next session, and cannot affect anchor, position, rotation, or scale: it
 * is a per-material shader constant and nothing more.
 *
 * Written as an absolute value because a Filament material is write-only
 * from here — there is no read-back to multiply against. A scan's authored
 * factor is effectively always (1,1,1,1), so writing (b,b,b,1) is a plain
 * boost, and the 1.0 step writes back exactly that default, which is why
 * cycling round to it restores the original look rather than approximating
 * it. Alpha is held at 1.0 rather than scaled, so brightening can never
 * turn the structure translucent.
 */
private const val DEFAULT_MODEL_BRIGHTNESS = 1.0f

/**
 * The steps the brightness control cycles through.
 *
 * 1.0 is both the default and the true one: it writes back a material's own
 * authored baseColorFactor, so starting here means AR shows the scan exactly
 * as the admin preview does, and any lift from there is the visitor's
 * deliberate choice rather than something the app decided for them.
 *
 * Lower steps sit below 1.0 as well, because with the neutral IBL now in
 * place a bright noon facade can want taking down rather than lifting.
 */
private val MODEL_BRIGHTNESS_STEPS = listOf(1.0f, 1.25f, 1.5f, 1.8f, 0.85f)

/** What a placement's size is measured from. Null = no photo to measure (geospatial/manual). */
private data class ScaleBasis(val structureHeightMeters: Float?)

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



@Composable
private fun UnavailableState(
    reason: String,
    onBack: () -> Unit,
    onRetry: (() -> Unit)? = null
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

        if (onRetry != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onRetry
                    )
                    .padding(horizontal = 22.dp, vertical = 11.dp)
            ) {
                Text(
                    text = "Try again",
                    color = Color.Black,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(10.dp))
        }

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
 * Shown when the visitor isn't standing inside this site's own geofence.
 *
 * Deliberately not an error. Nothing is broken and nothing failed — the
 * experience is waiting where it belongs, and the visitor simply isn't there
 * yet. So it reads as directions rather than an apology, and the primary
 * action is the one that actually helps: show them where to go.
 */
@Composable
private fun OutsideSiteState(
    siteName: String,
    reason: String,
    onRetry: () -> Unit,
    onViewOnMap: () -> Unit,
    onClose: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            tint = AccentGold,
            modifier = Modifier.size(42.dp)
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "You're outside this historical site",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = reason.ifBlank {
                "Travel Back in Time is only available when you are at " +
                    siteName.ifBlank { "this location" }.plus(". ") +
                    "Move closer to the historical site and try again."
            },
            color = Color.White.copy(alpha = 0.82f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(Modifier.height(26.dp))

        // The primary action, and deliberately so.
        //
        // This screen used to offer only a way to leave it, which meant a
        // visitor standing at the site whose phone had simply produced one
        // poor fix had no way to say "look again" — the refusal was final
        // until they backed out of AR entirely. Given that a wrong refusal
        // is now the likeliest reason anybody is reading this screen at all,
        // trying again belongs above the two exits rather than below them.
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Primary)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onRetry
                )
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Try Again",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.16f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onViewOnMap
                )
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text(
                text = "View on Map",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.16f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose
                )
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Close",
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
 * How tall the model is in its own units, from its bounding box's vertical
 * axis.
 *
 * glTF (and therefore every GLB this app loads — the admin panel only
 * accepts glTF 2.0) mandates +Y as up, so the vertical extent is the
 * bounding box's Y half-extent, doubled. This is what the model's height is
 * matched against the structure's real-world height for.
 */
private fun measureModelHeight(instance: ModelInstance): Float =
    runCatching { measureStructureHalfExtent(instance)[1] * 2f }.getOrDefault(0f)

/**
 * The model's longest side in its own units. The admin panel's Model Size is
 * the real-world length of this same side.
 */
private fun measureModelLongestSide(instance: ModelInstance): Float =
    runCatching {
        val half = measureStructureHalfExtent(instance)
        maxOf(half[0], half[1], half[2]) * 2f
    }.getOrDefault(0f)

/**
 * Nodes that are scenery or life rather than the structure itself. A painted
 * sky strip 95 m tall, or a dove circling 70 m up, would otherwise count
 * toward the model's height and longest side, and the structure would be
 * scaled down to make room for them.
 */
private val NON_STRUCTURE_NODE_PREFIXES = listOf("Backdrop_", "Dove_", ArSpatialAudio.SFX_PREFIX)

/**
 * Half-extents of the model's bounding box, counting only the renderables
 * that belong to the structure (see NON_STRUCTURE_NODE_PREFIXES), in the
 * model's own units. Falls back to the whole asset's box for a model with
 * nothing to exclude or nothing measurable.
 */
private fun measureStructureHalfExtent(instance: ModelInstance): FloatArray {
    val asset = instance.model
    val fallback = asset.boundingBox.halfExtent
    val engine = asset.engine
    val tm = engine.transformManager
    val rm = engine.renderableManager

    // Positions are taken relative to the model's own root so the result
    // doesn't depend on where, or how large, the model has been placed.
    val rootWorld = FloatArray(16)
    val rootInverse = FloatArray(16)
    tm.getWorldTransform(tm.getInstance(instance.root), rootWorld)
    if (!android.opengl.Matrix.invertM(rootInverse, 0, rootWorld, 0)) return fallback

    val world = FloatArray(16)
    val local = FloatArray(16)
    val corner = FloatArray(4)
    val out = FloatArray(4)
    val min = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
    val max = floatArrayOf(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
    val box = com.google.android.filament.Box()
    var excludedAny = false
    var counted = 0

    for (entity in instance.entities) {
        if (!rm.hasComponent(entity)) continue
        if (belongsToNonStructure(asset, tm, entity)) {
            excludedAny = true
            continue
        }
        rm.getAxisAlignedBoundingBox(rm.getInstance(entity), box)
        tm.getWorldTransform(tm.getInstance(entity), world)
        android.opengl.Matrix.multiplyMM(local, 0, rootInverse, 0, world, 0)
        val c = box.center
        val h = box.halfExtent
        for (i in 0 until 8) {
            corner[0] = c[0] + if (i and 1 == 0) -h[0] else h[0]
            corner[1] = c[1] + if (i and 2 == 0) -h[1] else h[1]
            corner[2] = c[2] + if (i and 4 == 0) -h[2] else h[2]
            corner[3] = 1f
            android.opengl.Matrix.multiplyMV(out, 0, local, 0, corner, 0)
            for (a in 0..2) {
                if (out[a] < min[a]) min[a] = out[a]
                if (out[a] > max[a]) max[a] = out[a]
            }
        }
        counted++
    }

    if (!excludedAny || counted == 0) return fallback
    return floatArrayOf((max[0] - min[0]) / 2f, (max[1] - min[1]) / 2f, (max[2] - min[2]) / 2f)
}

/** Whether this entity, or any node above it, is named as scenery or life. */
private fun belongsToNonStructure(
    asset: com.google.android.filament.gltfio.FilamentAsset,
    tm: com.google.android.filament.TransformManager,
    entity: Int
): Boolean {
    var current = entity
    repeat(16) {
        val name = asset.getName(current)
        if (name != null && NON_STRUCTURE_NODE_PREFIXES.any { name.startsWith(it) }) return true
        val ti = tm.getInstance(current)
        if (ti == 0) return false
        val parent = tm.getParent(ti)
        if (parent == 0) return false
        current = parent
    }
    return false
}

/** Rotates [v] by [q] (v' = v + 2·q.xyz × (q.xyz × v + q.w·v)). */
private fun rotateByQuaternion(q: Quaternion, v: Float3): Float3 {
    val tx = q.y * v.z - q.z * v.y + q.w * v.x
    val ty = q.z * v.x - q.x * v.z + q.w * v.y
    val tz = q.x * v.y - q.y * v.x + q.w * v.z
    return Float3(
        v.x + 2f * (q.y * tz - q.z * ty),
        v.y + 2f * (q.z * tx - q.x * tz),
        v.z + 2f * (q.x * ty - q.y * tx)
    )
}

/** Warm late-afternoon sunlight. */
private val SUN_COLOR = Float4(1.0f, 0.88f, 0.72f, 1.0f)

/**
 * Direction the sunlight travels, in the model's own glTF space (+Y up,
 * +X east, −Z north): from a sun about 28° up in the west-southwest, which
 * is where the reconstruction's painted backdrop puts its golden glow.
 */
private val SUN_DIRECTION_MODEL_SPACE = Float3(0.83f, -0.47f, -0.30f)

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
 *
 * Built around what these photographs actually contain: a reconstructed
 * stone building standing in present-day Intramuros. That rules out most of
 * what a social filter does. Heavy grading, crushed blacks and strong tints
 * all work by throwing away detail, and the detail they throw away here is
 * the masonry somebody walked across a walled city to look at. So every
 * grade below is deliberately restrained, and each one is built from the
 * same three cheap primitives — a colour matrix, a tiled grain, a radial
 * wash — so a full-resolution photo costs a handful of draw calls rather
 * than a per-pixel loop.
 */
private enum class CaptureFilter(val label: String) {

    NONE("Original") {
        override fun apply(bitmap: Bitmap): Bitmap = bitmap
    },

    /**
     * A wartime press photograph: colour mostly gone but not entirely,
     * a flatter tonal range, smoke-coloured haze and visible film grain.
     *
     * Saturation is left at 0.2 rather than 0 on purpose. Period photographs
     * of Manila are monochrome, but a fully desaturated AR capture reads as
     * "black and white filter" instead of "old photograph", and the faint
     * surviving colour is what keeps the rubble looking like rubble.
     */
    WAR("War · 1940s") {
        override fun apply(bitmap: Bitmap): Bitmap {
            var out = applyColorMatrix(bitmap, warMatrix())
            out = applyHaze(out, strength = 0.10f, color = HAZE_SMOKE)
            out = applyGrain(out, strength = 0.14f)
            return applyVignette(out, strength = 0.22f)
        }
    },

    /** Warm, faded and softly grained — a print that has sat in a drawer. */
    OLD_PHOTOGRAPH("Old Photograph") {
        override fun apply(bitmap: Bitmap): Bitmap {
            val out = applyColorMatrix(bitmap, oldPhotographMatrix())
            return applyVignette(applyGrain(out, strength = 0.08f), strength = 0.26f)
        }
    },

    /**
     * For churches, chapels and cathedrals: warm light, a gentle bloom
     * around anything already bright, and a single soft shaft from above.
     *
     * The bloom is what does most of the work, and it is deliberately
     * additive rather than a brightness lift — adding light only where light
     * already is leaves the shaded stone exactly as dark as it was, which is
     * the difference between a sunlit interior and a washed-out photograph.
     */
    SACRED("Holy · Sacred") {
        override fun apply(bitmap: Bitmap): Bitmap {
            var out = applyColorMatrix(bitmap, sacredMatrix())
            out = applyBloom(out, strength = 0.30f)
            return applyLightShaft(out, strength = 0.16f)
        }
    },

    /** Late-afternoon sun: warm highlights, cool shadows left alone. */
    GOLDEN_HOUR("Golden Hour") {
        override fun apply(bitmap: Bitmap): Bitmap {
            val out = applyColorMatrix(bitmap, goldenHourMatrix())
            return applyBloom(out, strength = 0.16f)
        }
    },

    /** Deeper shadows and more contrast, for forts, walls and ruins. */
    DRAMATIC("Dramatic History") {
        override fun apply(bitmap: Bitmap): Bitmap =
            applyVignette(applyColorMatrix(bitmap, dramaticMatrix()), strength = 0.34f)
    },

    /**
     * Neutral and faithful, for anybody who wants the architecture recorded
     * rather than interpreted: a whisper of contrast to counter the softness
     * of a screen capture, and nothing else.
     */
    MUSEUM("Museum") {
        override fun apply(bitmap: Bitmap): Bitmap =
            applyColorMatrix(bitmap, museumMatrix())
    };

    abstract fun apply(bitmap: Bitmap): Bitmap
}

/**
 * The filter that suits this site, offered rather than imposed.
 *
 * Matched on the site's own name and description, because those are the only
 * fields that describe what a structure IS — tagIds are Firestore document
 * ids, which say nothing without a second lookup, and the admin panel has no
 * "kind of building" field to read.
 *
 * Both Filipino and Spanish forms are matched, since Intramuros signage and
 * Firestore records use all three languages interchangeably: a record may
 * say Fort Santiago, Fuerza de Santiago or Baluarte, and all three mean a
 * fortification.
 *
 * Returns null when nothing matches, which is most sites and is fine — the
 * sheet simply offers no suggestion rather than inventing one.
 */
private fun suggestedFilterFor(site: HistoricalSite?): CaptureFilter? {

    val haystack = listOfNotNull(site?.siteName, site?.description)
        .joinToString(" ")
        .lowercase()

    if (haystack.isBlank()) return null

    fun mentions(vararg words: String) = words.any { it in haystack }

    return when {

        mentions("church", "cathedral", "chapel", "basilica", "shrine",
            "convent", "monastery", "simbahan", "iglesia", "ermita") -> CaptureFilter.SACRED

        // Checked before fortifications, because a ruined fort is a
        // photograph about the ruin rather than about the fort.
        mentions("ruin", "ruins", "bombed", "destroyed", "rubble",
            "world war", "wwii", "liberation", "battle") -> CaptureFilter.WAR

        mentions("fort", "fuerza", "baluarte", "bastion", "rampart",
            "wall", "muralla", "citadel", "gate", "puerta") -> CaptureFilter.DRAMATIC

        mentions("house", "casa", "bahay", "mansion", "residence",
            "heritage house", "ancestral") -> CaptureFilter.OLD_PHOTOGRAPH

        else -> null
    }
}

// ----- grades -----

/**
 * Builds a contrast adjustment as a colour matrix.
 *
 * Contrast is a scale about mid-grey rather than about zero, so the scale
 * has to be paired with a translation that puts 128 back where it started —
 * without it, raising contrast also raises brightness, and every "contrast"
 * filter quietly becomes an exposure filter.
 */
private fun contrastMatrix(contrast: Float): ColorMatrix {
    val translate = (1f - contrast) / 2f * 255f
    return ColorMatrix(
        floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        )
    )
}

/** Per-channel gain and offset — the usual way to express a colour cast. */
private fun tintMatrix(
    redGain: Float, greenGain: Float, blueGain: Float,
    redShift: Float = 0f, greenShift: Float = 0f, blueShift: Float = 0f
): ColorMatrix = ColorMatrix(
    floatArrayOf(
        redGain, 0f, 0f, 0f, redShift,
        0f, greenGain, 0f, 0f, greenShift,
        0f, 0f, blueGain, 0f, blueShift,
        0f, 0f, 0f, 1f, 0f
    )
)

private fun warMatrix(): ColorMatrix {
    val matrix = ColorMatrix()
    matrix.setSaturation(0.20f)
    matrix.postConcat(contrastMatrix(0.94f))
    // A cold, slightly green-grey cast, the way wartime stock aged.
    matrix.postConcat(tintMatrix(0.98f, 1.0f, 0.96f, redShift = 4f, blueShift = 2f))
    return matrix
}

private fun oldPhotographMatrix(): ColorMatrix {
    val matrix = ColorMatrix()
    matrix.setSaturation(0.62f)
    matrix.postConcat(contrastMatrix(0.90f))
    matrix.postConcat(tintMatrix(1.07f, 1.01f, 0.88f, redShift = 10f, greenShift = 5f, blueShift = -4f))
    return matrix
}

private fun sacredMatrix(): ColorMatrix {
    val matrix = ColorMatrix()
    matrix.setSaturation(0.95f)
    matrix.postConcat(contrastMatrix(0.96f))
    // Warm, and lifted only in the shadows, so the stone keeps its weight.
    matrix.postConcat(tintMatrix(1.05f, 1.01f, 0.95f, redShift = 6f, greenShift = 4f))
    return matrix
}

private fun goldenHourMatrix(): ColorMatrix {
    val matrix = ColorMatrix()
    matrix.setSaturation(1.08f)
    matrix.postConcat(contrastMatrix(1.04f))
    matrix.postConcat(tintMatrix(1.09f, 1.02f, 0.90f, redShift = 8f))
    return matrix
}

private fun dramaticMatrix(): ColorMatrix {
    val matrix = ColorMatrix()
    matrix.setSaturation(0.88f)
    matrix.postConcat(contrastMatrix(1.22f))
    // A faint cool cast in what's left of the shadows.
    matrix.postConcat(tintMatrix(1.0f, 1.0f, 1.03f, blueShift = 2f))
    return matrix
}

private fun museumMatrix(): ColorMatrix {
    val matrix = ColorMatrix()
    matrix.setSaturation(1.02f)
    matrix.postConcat(contrastMatrix(1.06f))
    return matrix
}

// ----- effects -----

/** The grey-brown of smoke and dust, for [applyHaze]. */
private val HAZE_SMOKE = android.graphics.Color.rgb(186, 178, 166)

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
 * Film grain, as a small tile of noise repeated across the frame.
 *
 * Generating noise at full resolution would mean millions of random values
 * per photograph and a visible pause on the review sheet. A 96-pixel tile
 * drawn through a REPEAT shader is one draw call at any output size, and at
 * the scale grain is actually looked at the repetition is invisible —
 * nobody finds the seam in a texture whose whole character is being
 * structureless.
 */
private fun applyGrain(source: Bitmap, strength: Float): Bitmap {

    val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    canvas.drawBitmap(source, 0f, 0f, null)

    val tileSize = 96
    val tile = Bitmap.createBitmap(tileSize, tileSize, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(tileSize * tileSize)
    val random = java.util.Random(GRAIN_SEED)
    val alpha = (strength * 255).toInt().coerceIn(0, 255)

    for (index in pixels.indices) {
        // Monochrome noise: grain is a property of the film, not of the
        // colour in front of it, so coloured speckle would read as sensor
        // noise rather than as emulsion.
        val value = random.nextInt(256)
        pixels[index] = android.graphics.Color.argb(alpha, value, value, value)
    }

    tile.setPixels(pixels, 0, tileSize, 0, 0, tileSize, tileSize)

    val paint = Paint().apply {
        shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        // Overlay keeps mid-tones roughly where they were and lets the noise
        // live in the texture rather than fogging the whole image, which is
        // what plain alpha compositing would do.
        xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
    }

    canvas.drawRect(0f, 0f, output.width.toFloat(), output.height.toFloat(), paint)

    tile.recycle()

    return output
}

/**
 * A flat wash of [color] across the frame — smoke, dust, or sea haze.
 *
 * Uniform rather than graded on purpose: atmospheric haze in a photograph of
 * a single nearby building is essentially constant across the frame, and
 * faking a depth gradient would put the haze in the wrong place as soon as
 * the visitor photographed the structure from the other side.
 */
private fun applyHaze(source: Bitmap, strength: Float, color: Int): Bitmap {

    val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    canvas.drawBitmap(source, 0f, 0f, null)

    canvas.drawRect(
        0f, 0f, output.width.toFloat(), output.height.toFloat(),
        Paint().apply {
            this.color = color
            this.alpha = (strength * 255).toInt().coerceIn(0, 255)
        }
    )

    return output
}

/**
 * Bloom: light spilling out of the brightest parts of the frame.
 *
 * The blur is done by throwing the image away and getting it back — a
 * downscale to an eighth and a filtered upscale is a box blur that the GPU
 * performs in two draw calls, where a real gaussian at full resolution would
 * need RenderScript (deprecated) or a per-pixel convolution (far too slow to
 * sit behind a live filter strip).
 *
 * Composited with ADD and through a matrix that keeps only the top of the
 * tonal range, so it brightens what is already bright and leaves shaded
 * stone untouched. That is what makes it read as light rather than as a
 * washed-out exposure.
 */
private fun applyBloom(source: Bitmap, strength: Float): Bitmap {

    val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    canvas.drawBitmap(source, 0f, 0f, null)

    val smallWidth = (source.width / 8).coerceAtLeast(1)
    val smallHeight = (source.height / 8).coerceAtLeast(1)

    val small = Bitmap.createScaledBitmap(source, smallWidth, smallHeight, true)

    // Steep gain with a large negative offset: everything below roughly
    // two-thirds brightness lands at or under zero and contributes nothing.
    val highlightsOnly = ColorMatrix(
        floatArrayOf(
            2.2f, 0f, 0f, 0f, -150f,
            0f, 2.2f, 0f, 0f, -150f,
            0f, 0f, 2.2f, 0f, -150f,
            0f, 0f, 0f, 1f, 0f
        )
    )

    val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(highlightsOnly)
        alpha = (strength * 255).toInt().coerceIn(0, 255)
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }

    canvas.drawBitmap(
        small,
        null,
        Rect(0, 0, output.width, output.height),
        paint
    )

    small.recycle()

    return output
}

/**
 * One soft shaft of light entering from above, for the Sacred grade.
 *
 * A single wide cone rather than the fan of rays a "god rays" effect would
 * draw: a cathedral photographed from the floor has one window doing the
 * work at any given moment, and a symmetrical fan reads as a lens artefact
 * pasted over the picture instead of as light in the room.
 *
 * Drawn with ADD so it can only lighten. Nothing in the architecture is
 * hidden by it — at the strengths used here the stone stays fully legible
 * through the brightest part of the shaft.
 */
private fun applyLightShaft(source: Bitmap, strength: Float): Bitmap {

    val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    canvas.drawBitmap(source, 0f, 0f, null)

    val originX = output.width * 0.68f
    val originY = output.height * 0.04f
    val radius = kotlin.math.hypot(output.width.toFloat(), output.height.toFloat()) * 0.8f

    val warmLight = android.graphics.Color.argb(
        (strength * 255).toInt().coerceIn(0, 255),
        255, 241, 214
    )

    val shader = RadialGradient(
        originX,
        originY,
        radius,
        intArrayOf(warmLight, android.graphics.Color.TRANSPARENT),
        floatArrayOf(0f, 1f),
        Shader.TileMode.CLAMP
    )

    canvas.drawRect(
        0f, 0f, output.width.toFloat(), output.height.toFloat(),
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = shader
            xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        }
    )

    return output
}

/**
 * Fixed so grain is identical between a thumbnail and the full-size photo it
 * previews, and between one preview and the next. A random seed would make
 * the strip shimmer on every recomposition.
 */
private const val GRAIN_SEED = 20261001L

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

/** Space between controls in the AR screen's bottom stack. */
private val BOTTOM_STACK_GAP = 14.dp
