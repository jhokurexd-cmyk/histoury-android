package com.histoury.app

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.lifecycle.Lifecycle
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.histoury.app.data.offline.OfflineContentCache
import com.histoury.app.data.offline.OfflineSiteManager
import com.histoury.app.data.repository.AccountStatus
import com.histoury.app.data.repository.AuthRepository
import com.histoury.app.data.repository.SuspensionInfoHolder
import com.histoury.app.location.ArrivalEventBus
import com.histoury.app.location.ArrivalWatcher
import androidx.lifecycle.repeatOnLifecycle
import com.histoury.app.location.SessionCleanup
import com.histoury.app.navigation.NavGraph
import com.histoury.app.navigation.Routes
import androidx.core.view.WindowCompat
import com.histoury.app.theme.Background
import com.histoury.app.theme.HistouryTheme
import com.histoury.app.theme.ThemePreference
import com.histoury.app.theme.isDarkTheme
import com.histoury.app.util.NavigationPipState
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Screens an arrival may interrupt.
 *
 * An allow-list rather than a list of screens to avoid: a new screen added
 * later should default to *not* being interrupted, because the cost of
 * wrongly interrupting someone mid-task is far higher than the cost of
 * making them tap a notification.
 */
private val ARRIVAL_INTERRUPTIBLE_ROUTES = setOf(
    Routes.Home.route,
    Routes.MapOverview.route,
    Routes.History.route,
    Routes.Gallery.route,
    Routes.SiteLibrary.route
)

class MainActivity : ComponentActivity() {

    private var pendingArrivalSiteId by mutableStateOf<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        // Read before the first frame so the app never flashes light while
        // a stored dark preference loads. It's one string off disk.
        ThemePreference.load(applicationContext)

        // Only on a FRESH launch. When the activity is recreated (dark mode
        // or another configuration change) Android hands it the same launch
        // intent again, and reading the extra again re-opened the Arrival
        // screen from a notification tapped long ago.
        if (savedInstanceState == null) {
            pendingArrivalSiteId = consumeArrivalExtra(intent)
        }

        // Fills the Firestore and image caches in the background so site
        // text and photos stay readable after the signal drops. No-ops when
        // already offline or when the caches were refreshed recently, so
        // this is cheap to call on every launch.
        lifecycleScope.launch {
            OfflineContentCache.warmUp(applicationContext)
        }

        // Offline Downloads: continue downloads that were queued, waiting or
        // paused when the app was last closed, and look for newer versions
        // of downloaded AR resources (queued only if automatic updates are
        // on; each waits for the allowed connection by itself).
        OfflineSiteManager.restore(applicationContext)
        // "Auto-download on Wi-Fi": saves missing AR sites whenever on Wi-Fi.
        com.histoury.app.data.offline.AutoDownloader.start(applicationContext)
        // Each time the phone is online (now, and whenever it comes back):
        // remove sites taken down in the admin panel, then look for updates.
        OfflineSiteManager.startMaintenance(applicationContext)
        lifecycleScope.launch {
            OfflineSiteManager.retired.collect { names ->
                val what = if (names.size == 1) names.first() else "${names.size} sites"
                android.widget.Toast.makeText(
                    applicationContext,
                    "$what removed from Offline Downloads — no longer available in Histoury.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }

        // In-app arrival detection while Histoury is on screen: being inside
        // a site's radius opens its Arrival screen even if the phone never
        // "crossed" the fence (e.g. the app was opened at the site). Stops
        // when the app leaves the screen; background arrivals stay with
        // Android's geofencing.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ArrivalWatcher.watch(this@MainActivity)
            }
        }

        // Android 12+: let the system shrink the app into the floating mini
        // map by itself when the visitor leaves mid-navigation (Home,
        // gesture, recents) — smoother than doing it from onUserLeaveHint.
        lifecycleScope.launch {
            NavigationPipState.isNavigating.collect {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && supportsNavigationPip()) {
                    setPictureInPictureParams(navigationPipParams())
                }
            }
        }

        setContent {

            val themeMode by ThemePreference.mode.collectAsState()

            HistouryTheme(themeMode = themeMode) {

                // enableEdgeToEdge() draws behind the status bar, so the
                // system's own icons sit on the app's background and have
                // to be flipped with it — otherwise dark mode gets black
                // icons on a near-black bar.
                val darkSurface = isDarkTheme

                LaunchedEffect(darkSurface) {
                    WindowCompat
                        .getInsetsController(window, window.decorView)
                        .apply {
                            isAppearanceLightStatusBars = !darkSurface
                            isAppearanceLightNavigationBars = !darkSurface
                        }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Background
                ) {

                    val navController =
                        rememberNavController()

                    // Which screen is open, for the crash report.
                    androidx.compose.runtime.DisposableEffect(navController) {
                        val listener = androidx.navigation.NavController.OnDestinationChangedListener { _, destination, _ ->
                            com.histoury.app.util.CrashReport.currentScreen = destination.route ?: "?"
                        }
                        navController.addOnDestinationChangedListener(listener)
                        onDispose { navController.removeOnDestinationChangedListener(listener) }
                    }

                    // Debug builds: if the app closed unexpectedly last time,
                    // say why (copyable), instead of leaving no trace.
                    CrashReportDialog()

                    LaunchedEffect(pendingArrivalSiteId) {

                        val siteId = pendingArrivalSiteId

                        if (siteId != null) {

                            pendingArrivalSiteId = null

                            // A notification from a session that has since
                            // ended (logout/suspension) opens the app
                            // normally instead of an Arrival screen with no
                            // one signed in.
                            if (FirebaseAuth.getInstance().currentUser != null) {
                                navController.navigate(
                                    Routes.Arrival.createRoute(siteId)
                                )
                            }
                        }
                    }

                    // Live geofence arrivals while the app is OPEN: show the
                    // Arrival screen directly, not just the notification.
                    LaunchedEffect(Unit) {

                        ArrivalEventBus.arrivals.collect { siteId ->

                            // Only steer the UI when actually visible —
                            // backgrounded arrivals stay notification-only.
                            val isVisible = lifecycle.currentState
                                .isAtLeast(Lifecycle.State.STARTED)

                            // Don't stack a duplicate if we're already on
                            // the Arrival screen (e.g. live navigation's own
                            // auto-arrival fired at the same moment).
                            val alreadyOnArrival =
                                navController.currentDestination?.route ==
                                    Routes.Arrival.route

                            // Only steer the UI from a screen the visitor is
                            // browsing from, never mid-task.
                            //
                            // An arrival firing while someone is inside AR,
                            // filling in a review, or adjusting an itinerary
                            // used to yank them to a different screen with no
                            // warning — indistinguishable from the app going
                            // back on its own, and it discarded whatever they
                            // were doing. The notification still fires, so
                            // nothing is lost; it just waits to be tapped.
                            val onInterruptibleScreen =
                                navController.currentDestination?.route in
                                    ARRIVAL_INTERRUPTIBLE_ROUTES

                            val signedIn = FirebaseAuth.getInstance().currentUser != null

                            if (signedIn && isVisible && !alreadyOnArrival && onInterruptibleScreen) {
                                navController.navigate(
                                    Routes.Arrival.createRoute(siteId)
                                ) {
                                    launchSingleTop = true
                                }
                            }
                        }
                    }

                    // Live suspension check for the whole session: if an
                    // admin suspends this account while the app is open (on
                    // any screen), sign out immediately and show the
                    // blocking Suspended screen — don't make them wait for
                    // a restart to find out. Restarts the listener whenever
                    // Firebase's own auth state changes (login/logout).
                    // Whenever the session ends — however it ends — stop
                    // arrival geofences, notifications and navigation that
                    // belonged to it (see SessionCleanup).
                    LaunchedEffect(Unit) {
                        var hadUser = false
                        AuthRepository().authStateUidFlow().collect { uid ->
                            if (uid == null && hadUser) {
                                SessionCleanup.onSignedOut(applicationContext)
                            }
                            // Signed in (again): saved downloads stay on the
                            // phone across accounts; check them against the
                            // server now rather than at the next launch.
                            if (uid != null && !hadUser) {
                                OfflineSiteManager.refreshNow(applicationContext)
                            }
                            hadUser = uid != null
                        }
                    }

                    LaunchedEffect(Unit) {

                        val authRepository = AuthRepository()

                        authRepository.authStateUidFlow()
                            .flatMapLatest { uid ->
                                if (uid == null) {
                                    flowOf(AccountStatus.Active)
                                } else {
                                    authRepository.observeAccountStatus(uid)
                                }
                            }
                            .collect { status ->

                                if (status is AccountStatus.Suspended) {

                                    // Keep a token for the appeal before
                                    // signing out — afterwards there's no
                                    // current user to read one from.
                                    val appealToken = authRepository.appealTokenOrNull()
                                    com.histoury.app.data.itinerary.ActiveItineraryTrip.clear()
                                    FirebaseAuth.getInstance().signOut()
                                    SuspensionInfoHolder.set(status.copy(appealToken = appealToken))

                                    val alreadyOnSuspended =
                                        navController.currentDestination?.route ==
                                            Routes.Suspended.route

                                    if (!alreadyOnSuspended) {
                                        navController.navigate(Routes.Suspended.route) {
                                            popUpTo(0) {
                                                inclusive = true
                                            }
                                        }
                                    }
                                }

                                // The server says the profile is gone (an
                                // admin deleted the account): end the session
                                // instead of leaving it signed in until the
                                // token expires. Login already handles its own
                                // NoProfile case, so don't reset that screen.
                                if (status is AccountStatus.NoProfile) {

                                    com.histoury.app.data.itinerary.ActiveItineraryTrip.clear()
                                    FirebaseAuth.getInstance().signOut()

                                    val alreadyOnLogin =
                                        navController.currentDestination?.route ==
                                            Routes.Login.route

                                    if (!alreadyOnLogin) {
                                        navController.navigate(Routes.Login.route) {
                                            popUpTo(0) {
                                                inclusive = true
                                            }
                                        }
                                    }
                                }
                            }
                    }

                    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {

                        // Tab screens leave the bottom bar to the single
                        // shared one below, so it stays put while switching.
                        androidx.compose.runtime.CompositionLocalProvider(
                            com.histoury.app.ui.components.LocalSharedNavBar provides true
                        ) {
                            NavGraph(
                                navController = navController
                            )
                        }

                        val isInPip by NavigationPipState.isInPip.collectAsState()

                        if (!isInPip) {
                            com.histoury.app.ui.components.SharedBottomNavBar(
                                navController = navController,
                                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
                            )

                            // "You're offline" / "Back online", over every
                            // screen (not in the tiny floating map).
                            com.histoury.app.ui.components.ConnectivityBanner(
                                modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter)
                            )

                            // "Prepare for your trip" after an itinerary is created.
                            com.histoury.app.ui.components.TripPrepPrompt()

                            // Quick loading line on each page change.
                            com.histoury.app.ui.components.NavLoadingIndicator(
                                navController = navController,
                                modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter)
                            )
                        }

                        // First-run spotlight tutorials, drawn over every
                        // screen and the bottom bar. Last, so it's on top.
                        if (!isInPip) {
                            com.histoury.app.ui.tutorial.TutorialOverlay()
                        }

                    }
                }
            }
        }
    }

    // ----- floating mini map (picture-in-picture) during navigation -----

    fun supportsNavigationPip(): Boolean =
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private fun navigationPipParams(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            // Portrait-ish: a navigation map reads better tall than wide.
            .setAspectRatio(Rational(3, 4))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(NavigationPipState.isNavigating.value)
            // A map isn't video — a crossfade resize looks better than stretching.
            builder.setSeamlessResizeEnabled(false)
        }
        return builder.build()
    }

    /** Shrinks the app into the floating mini map. Returns false if the device doesn't allow it. */
    fun enterNavigationPip(): Boolean {
        if (!supportsNavigationPip()) return false
        return try {
            enterPictureInPictureMode(navigationPipParams())
        } catch (e: IllegalStateException) {
            // PiP disabled for this app in system settings.
            false
        }
    }

    /** Brings the app back to full screen from the floating window (e.g. on arrival). */
    fun expandFromPip() {
        if (isInPictureInPictureMode) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Location or notification permission may have changed in Settings
        // while away; re-check the "Location is off" notification.
        com.histoury.app.location.LocationStatusNotifier.refresh(this)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Android 8-11 have no auto-enter, so do it by hand when the visitor
        // presses Home while navigating.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && NavigationPipState.isNavigating.value) {
            enterNavigationPip()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)

        NavigationPipState.isInPip.value = isInPictureInPictureMode

        // Leaving PiP while the activity is only CREATED means the visitor
        // closed the window (X), not expanded it back to full screen.
        if (!isInPictureInPictureMode && lifecycle.currentState == Lifecycle.State.CREATED) {
            NavigationPipState.notifyPipDismissed()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        setIntent(intent)

        pendingArrivalSiteId = consumeArrivalExtra(intent)
    }

    /** Reads the arrival extra once and removes it, so nothing can re-read it later. */
    private fun consumeArrivalExtra(intent: Intent?): String? {
        val siteId = intent?.getStringExtra(EXTRA_ARRIVAL_SITE_ID)
        intent?.removeExtra(EXTRA_ARRIVAL_SITE_ID)
        return siteId
    }

    companion object {
        const val EXTRA_ARRIVAL_SITE_ID = "arrival_site_id"
    }
}

/**
 * Shows the last crash once, on the next launch, in debug builds only, with
 * Copy and Share so the error can be sent on. Never shown to real users.
 */
@androidx.compose.runtime.Composable
private fun CrashReportDialog() {
    if (!BuildConfig.DEBUG) return
    val context = androidx.compose.ui.platform.LocalContext.current
    var report by androidx.compose.runtime.remember {
        mutableStateOf(com.histoury.app.util.CrashReport.takePending(context))
    }
    val text = report ?: return
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { report = null },
        title = { androidx.compose.material3.Text("Histoury closed unexpectedly") },
        text = {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .crashReportMaxHeight()
                    .crashReportScroll()
            ) {
                androidx.compose.material3.Text(
                    text = text,
                    fontSize = androidx.compose.ui.unit.TextUnit(11f, androidx.compose.ui.unit.TextUnitType.Sp),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "Histoury crash report")
                    .putExtra(Intent.EXTRA_TEXT, text)
                runCatching { context.startActivity(Intent.createChooser(send, "Share crash report")) }
            }) { androidx.compose.material3.Text("Share") }
        },
        dismissButton = {
            androidx.compose.foundation.layout.Row {
                androidx.compose.material3.TextButton(onClick = {
                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Histoury crash", text))
                }) { androidx.compose.material3.Text("Copy") }
                androidx.compose.material3.TextButton(onClick = { report = null }) {
                    androidx.compose.material3.Text("Close")
                }
            }
        }
    )
}

@androidx.compose.runtime.Composable
private fun Modifier.crashReportScroll(): Modifier =
    this.verticalScroll(androidx.compose.foundation.rememberScrollState())

private fun Modifier.crashReportMaxHeight(): Modifier =
    this.heightIn(max = androidx.compose.ui.unit.Dp(420f))
