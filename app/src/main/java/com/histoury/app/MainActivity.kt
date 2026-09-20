package com.histoury.app

import android.content.Intent
import android.os.Bundle
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
import androidx.lifecycle.Lifecycle
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.histoury.app.data.offline.OfflineContentCache
import com.histoury.app.data.repository.AccountStatus
import com.histoury.app.data.repository.AuthRepository
import com.histoury.app.data.repository.SuspensionInfoHolder
import com.histoury.app.location.ArrivalEventBus
import com.histoury.app.navigation.NavGraph
import com.histoury.app.navigation.Routes
import androidx.core.view.WindowCompat
import com.histoury.app.theme.Background
import com.histoury.app.theme.HistouryTheme
import com.histoury.app.theme.ThemePreference
import com.histoury.app.theme.isDarkTheme
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

        pendingArrivalSiteId = intent?.getStringExtra(EXTRA_ARRIVAL_SITE_ID)

        // Fills the Firestore and image caches in the background so site
        // text and photos stay readable after the signal drops. No-ops when
        // already offline or when the caches were refreshed recently, so
        // this is cheap to call on every launch.
        lifecycleScope.launch {
            OfflineContentCache.warmUp(applicationContext)
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

                    LaunchedEffect(pendingArrivalSiteId) {

                        val siteId = pendingArrivalSiteId

                        if (siteId != null) {

                            navController.navigate(
                                Routes.Arrival.createRoute(siteId)
                            )

                            pendingArrivalSiteId = null
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

                            if (isVisible && !alreadyOnArrival && onInterruptibleScreen) {
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

                                    FirebaseAuth.getInstance().signOut()
                                    SuspensionInfoHolder.set(status)

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
                            }
                    }

                    NavGraph(
                        navController = navController
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        setIntent(intent)

        pendingArrivalSiteId = intent.getStringExtra(EXTRA_ARRIVAL_SITE_ID)
    }

    companion object {
        const val EXTRA_ARRIVAL_SITE_ID = "arrival_site_id"
    }
}
