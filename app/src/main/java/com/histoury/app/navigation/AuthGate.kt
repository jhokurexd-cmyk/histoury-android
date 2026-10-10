package com.histoury.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.histoury.app.data.repository.AccountStatus
import com.histoury.app.data.repository.AuthRepository
import com.histoury.app.data.repository.OnboardingPreference
import com.histoury.app.data.repository.SuspensionInfoHolder
import com.histoury.app.theme.Background
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The app's real start destination: decides Onboarding vs Home vs
 * Suspended before anything is drawn, then leaves no trace on the back
 * stack.
 *
 * Nothing is shown here on purpose — there is no splash screen. The one
 * thing this still has to do is the account-status check that used to run
 * under the splash's cover: checkAccountStatus() reads from the server,
 * not the cache, so a suspended account is caught here rather than
 * flashing Home first.
 */
@Composable
fun AuthGate(navController: NavController) {

    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    )

    LaunchedEffect(Unit) {

        val currentUser = FirebaseAuth.getInstance().currentUser

        val destination = if (currentUser == null) {
            // Returning visitors who logged out go straight to Login; the
            // intro pages are only for a first launch on this device.
            if (OnboardingPreference.hasSeen(context)) Routes.Login.route else Routes.Onboarding.route
        } else {
            // This screen is blank, so a slow connection used to mean a
            // blank app for as long as the server took to answer. Cap the
            // wait: MainActivity's live listener still catches a suspension
            // moments later if this check gives up.
            val status = withTimeoutOrNull(STATUS_CHECK_TIMEOUT_MS) {
                AuthRepository().checkAccountStatus(currentUser.uid)
            } ?: AccountStatus.Active

            when (status) {

                is AccountStatus.Suspended -> {
                    val appealToken = AuthRepository().appealTokenOrNull()
                    FirebaseAuth.getInstance().signOut()
                    SuspensionInfoHolder.set(status.copy(appealToken = appealToken))
                    Routes.Suspended.route
                }

                // A login with no profile isn't an account (see
                // AccountStatus.NoProfile): sign it out.
                AccountStatus.NoProfile -> {
                    FirebaseAuth.getInstance().signOut()
                    Routes.Login.route
                }

                AccountStatus.Active -> Routes.Home.route
            }
        }

        navController.navigate(destination) {
            popUpTo(Routes.AuthGate.route) {
                inclusive = true
            }
        }
    }
}

private const val STATUS_CHECK_TIMEOUT_MS = 4_000L
