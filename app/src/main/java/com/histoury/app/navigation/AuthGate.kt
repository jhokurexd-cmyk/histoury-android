package com.histoury.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.histoury.app.data.repository.AccountStatus
import com.histoury.app.data.repository.AuthRepository
import com.histoury.app.data.repository.SuspensionInfoHolder
import com.histoury.app.theme.Background

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    )

    LaunchedEffect(Unit) {

        val currentUser = FirebaseAuth.getInstance().currentUser

        val destination = if (currentUser == null) {
            Routes.Onboarding.route
        } else {
            when (val status = AuthRepository().checkAccountStatus(currentUser.uid)) {

                is AccountStatus.Suspended -> {
                    FirebaseAuth.getInstance().signOut()
                    SuspensionInfoHolder.set(status)
                    Routes.Suspended.route
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
