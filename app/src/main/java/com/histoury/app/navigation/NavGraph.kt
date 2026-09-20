package com.histoury.app.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.histoury.app.data.viewmodel.ForgotPasswordViewModel
import com.histoury.app.ui.screens.downloads.DownloadsScreen
import com.histoury.app.ui.screens.debug.StreetscapeDebugScreen
import com.histoury.app.ui.screens.ar.ARExperienceScreen
import com.histoury.app.ui.screens.arrival.ArrivalScreen
import com.histoury.app.ui.screens.auth.LoginScreen
import com.histoury.app.ui.screens.auth.RegisterScreen
import com.histoury.app.ui.screens.auth.SuspendedScreen
import com.histoury.app.ui.screens.auth.ForgotPasswordScreen
import com.histoury.app.ui.screens.auth.OtpVerificationScreen
import com.histoury.app.ui.screens.auth.ResetPasswordScreen
import com.histoury.app.ui.screens.explore.ExploreScreen
import com.histoury.app.ui.screens.gallery.GalleryScreen
import com.histoury.app.ui.screens.history.VisitHistoryScreen
import com.histoury.app.ui.screens.home.HomeScreen
import com.histoury.app.ui.screens.legal.LegalScreen
import com.histoury.app.ui.screens.map.MapOverviewScreen
import com.histoury.app.ui.screens.map.MapScreen
import com.histoury.app.ui.screens.menu.MenuScreen
import com.histoury.app.ui.screens.onboarding.OnboardingScreen
import com.histoury.app.ui.screens.profile.ProfileScreen
import com.histoury.app.ui.screens.rankings.RankingsScreen
import com.histoury.app.ui.screens.review.RateReviewScreen
import com.histoury.app.ui.screens.sitedetails.SiteDetailsScreen
import com.histoury.app.ui.screens.sitelibrary.SiteLibraryScreen
import com.histoury.app.ui.screens.placedetails.PlaceDetailsScreen
import com.histoury.app.ui.screens.support.SupportScreen

/**
 * The five destinations reachable from the floating footer.
 *
 * They share that footer, so each one draws its own copy of it. A sliding
 * screen transition slides that copy too: for the length of the animation
 * there are two bars on screen at slightly different offsets, which reads
 * as the whole footer twitching sideways every time a tab is tapped.
 *
 * Switching between siblings isn't forward or backward motion anyway —
 * there's no direction for the slide to express — so tab-to-tab changes cut
 * instantly and the footer simply stays put. Every other navigation keeps
 * the slide, where it does mean something.
 */
private val TOP_LEVEL_ROUTES = setOf(
    Routes.Home.route,
    Routes.MapOverview.route,
    Routes.Itinerary.route,
    Routes.History.route,
    Routes.Gallery.route
)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean {
    val from = initialState.destination.route
    val to = targetState.destination.route
    return from in TOP_LEVEL_ROUTES && to in TOP_LEVEL_ROUTES
}

@Composable
fun NavGraph(
    navController: NavHostController
) {

    NavHost(
        navController = navController,
        startDestination = Routes.AuthGate.route,
        // Consistent, subtle forward/back motion for every screen change:
        // new screens fade in sliding slightly from the right; going back
        // reverses the direction. Short (260ms) so navigation never feels
        // sluggish — replaces the abrupt default cut.
        enterTransition = {
            if (isTabSwitch()) {
                EnterTransition.None
            } else {
                fadeIn(animationSpec = tween(260)) +
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> fullWidth / 8 },
                        animationSpec = tween(260)
                    )
            }
        },
        exitTransition = {
            if (isTabSwitch()) {
                ExitTransition.None
            } else {
                fadeOut(animationSpec = tween(200)) +
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> -fullWidth / 8 },
                        animationSpec = tween(260)
                    )
            }
        },
        // Tabs restore rather than push, so a tab change can arrive through
        // the pop transitions too — both need the same exemption or the
        // twitch just moves to the return trip.
        popEnterTransition = {
            if (isTabSwitch()) {
                EnterTransition.None
            } else {
                fadeIn(animationSpec = tween(260)) +
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> -fullWidth / 8 },
                        animationSpec = tween(260)
                    )
            }
        },
        popExitTransition = {
            if (isTabSwitch()) {
                ExitTransition.None
            } else {
                fadeOut(animationSpec = tween(200)) +
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> fullWidth / 8 },
                        animationSpec = tween(260)
                    )
            }
        }
    ) {

        composable(Routes.AuthGate.route) {
            AuthGate(navController)
        }

        composable(Routes.Onboarding.route) {
            OnboardingScreen(navController)
        }

        composable(Routes.Login.route) {
            LoginScreen(navController)
        }

        composable(Routes.Register.route) {
            RegisterScreen(navController)
        }

        composable(Routes.Suspended.route) {
            SuspendedScreen(navController)
        }

        // The Forgot Password wizard (email -> OTP -> new password) as a
        // nested graph: navigating to Routes.ForgotPassword.route from
        // Login/Register enters it exactly as before, but internally its
        // three screens share one ForgotPasswordViewModel scoped to this
        // graph's own back stack entry, so the email and verified code
        // survive moving between them. See ForgotPasswordViewModel's
        // kdoc and Routes.kt.
        navigation(
            startDestination = Routes.ForgotPassword.route,
            route = Routes.ForgotPasswordFlow.route
        ) {

            composable(Routes.ForgotPassword.route) { backStackEntry ->
                val flowEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(Routes.ForgotPasswordFlow.route)
                }
                val forgotPasswordViewModel: ForgotPasswordViewModel = viewModel(flowEntry)
                ForgotPasswordScreen(
                    navController = navController,
                    forgotPasswordViewModel = forgotPasswordViewModel
                )
            }

            composable(Routes.OtpVerification.route) { backStackEntry ->
                val flowEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(Routes.ForgotPasswordFlow.route)
                }
                val forgotPasswordViewModel: ForgotPasswordViewModel = viewModel(flowEntry)
                OtpVerificationScreen(
                    navController = navController,
                    forgotPasswordViewModel = forgotPasswordViewModel
                )
            }

            composable(Routes.ResetPassword.route) { backStackEntry ->
                val flowEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(Routes.ForgotPasswordFlow.route)
                }
                val forgotPasswordViewModel: ForgotPasswordViewModel = viewModel(flowEntry)
                ResetPasswordScreen(
                    navController = navController,
                    forgotPasswordViewModel = forgotPasswordViewModel
                )
            }
        }

        composable(Routes.Home.route) {
            HomeScreen(navController = navController)
        }

        composable(Routes.Itinerary.route) {
            ExploreScreen(navController = navController)
        }

        composable(Routes.Menu.route) {
            MenuScreen(navController = navController)
        }

        composable(Routes.Profile.route) {
            ProfileScreen(navController = navController)
        }

        composable(Routes.Rankings.route) {
            RankingsScreen(navController = navController)
        }

        composable(Routes.Support.route) {
            SupportScreen(navController = navController)
        }

        composable(Routes.Downloads.route) {
            DownloadsScreen(navController = navController)
        }

        // TEMPORARY — see the kdoc on Routes.StreetscapeDebug and on
        // StreetscapeDebugScreen itself.
        composable(Routes.StreetscapeDebug.route) {
            StreetscapeDebugScreen(navController = navController)
        }

        composable(Routes.History.route) {
            VisitHistoryScreen(navController = navController)
        }

        composable(Routes.MapOverview.route) {
            MapOverviewScreen(navController = navController)
        }

        composable(Routes.Gallery.route) {
            GalleryScreen(navController = navController)
        }

        composable(
            route = Routes.Legal.route,
            arguments = listOf(
                navArgument("docType") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val docType = backStackEntry.arguments?.getString("docType") ?: "privacy"
            LegalScreen(navController = navController, docType = docType)
        }

        composable(
            route = Routes.Map.route,
            arguments = listOf(
                navArgument("siteId") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->

            val siteId = backStackEntry.arguments?.getString("siteId") ?: ""

            MapScreen(
                siteId = siteId,
                navController = navController
            )
        }

        composable(
            route = Routes.Arrival.route,
            arguments = listOf(
                navArgument("siteId") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->

            val siteId = backStackEntry.arguments?.getString("siteId") ?: ""

            ArrivalScreen(
                siteId = siteId,
                navController = navController
            )
        }

        composable(
            route = Routes.SiteDetails.route,
            arguments = listOf(
                navArgument("siteId") {
                    type = NavType.StringType
                },
                navArgument("showAr") {
                    type = NavType.BoolType
                    defaultValue = true
                }
            )
        ) { backStackEntry ->

            val siteId = backStackEntry.arguments?.getString("siteId") ?: ""

            val showAr = backStackEntry.arguments?.getBoolean("showAr") ?: true

            SiteDetailsScreen(
                siteId = siteId,
                navController = navController,
                showArButton = showAr
            )
        }

        composable(
            route = Routes.ARExperience.route,
            arguments = listOf(
                navArgument("siteId") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->

            val siteId = backStackEntry.arguments?.getString("siteId") ?: ""

            ARExperienceScreen(
                siteId = siteId,
                navController = navController
            )
        }

        composable(Routes.SiteLibrary.route) {
            SiteLibraryScreen(navController = navController)
        }

        composable(
            route = Routes.PlaceDetails.route,
            arguments = listOf(
                navArgument("placeId") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->

            val placeId = backStackEntry.arguments?.getString("placeId") ?: ""

            PlaceDetailsScreen(
                placeId = placeId,
                navController = navController
            )
        }

        composable(
            route = Routes.Review.route,
            arguments = listOf(
                navArgument("siteId") {
                    type = NavType.StringType
                },
                navArgument("visitId") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->

            val siteId = backStackEntry.arguments?.getString("siteId") ?: ""

            val visitId = backStackEntry.arguments?.getString("visitId") ?: ""

            RateReviewScreen(
                siteId = siteId,
                visitId = visitId,
                navController = navController
            )
        }
    }
}
