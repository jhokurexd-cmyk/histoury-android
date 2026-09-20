package com.histoury.app.ui.screens.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.histoury.app.R
import com.histoury.app.data.repository.SuspensionInfoHolder
import com.histoury.app.data.viewmodel.LoginViewModel
import com.histoury.app.navigation.Routes
import com.histoury.app.theme.Primary
import com.histoury.app.theme.PrimarySoft
import com.histoury.app.theme.SurfaceSoft
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary
import com.histoury.app.ui.components.AuthStatusBanner
import com.histoury.app.ui.components.AuthTextField
import com.histoury.app.ui.components.PrimaryButton

@Composable
fun LoginScreen(
    navController: NavController,
    loginViewModel: LoginViewModel = viewModel()
) {

    val uiState by loginViewModel.uiState.collectAsState()

    LaunchedEffect(uiState.loginSucceeded) {
        if (uiState.loginSucceeded) {
            navController.navigate(Routes.Home.route) {
                popUpTo(Routes.AuthGate.route) {
                    inclusive = true
                }
            }
        }
    }

    LaunchedEffect(uiState.suspension) {
        val suspension = uiState.suspension
        if (suspension != null) {
            SuspensionInfoHolder.set(suspension)
            // Consume the event BEFORE navigating: if this Login screen
            // instance is returned to later (Back to Login), a stale
            // suspension left in the ViewModel state would otherwise
            // re-fire this effect and bounce the user straight back to
            // the Suspended screen even after being unsuspended.
            loginViewModel.suspensionShown()
            navController.navigate(Routes.Suspended.route)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                // A single coral wash bleeding down from the top, rather
                // than a flat field. It ties the screen to the splash that
                // precedes it — which is pure coral — so arriving here reads
                // as the colour receding rather than as a hard cut to grey.
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to PrimarySoft,
                        0.42f to SurfaceSoft,
                        1f to SurfaceSoft
                    )
                )
            )
            .verticalScroll(rememberScrollState())
            // Lifts the form clear of the keyboard. Without it the password
            // field and the Login button sit underneath it on a short
            // screen, and the only way out is to dismiss the keyboard first.
            .imePadding()
            .padding(horizontal = 28.dp)
    ) {

        Spacer(Modifier.height(36.dp))

        Image(
            painter = painterResource(R.drawable.logo_histoury),
            contentDescription = "Histoury",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                // Width only. The old fixed 150 x 162 forced an aspect the
                // artwork does not have, so Fit was letterboxing inside a
                // box that was itself the wrong shape.
                .width(132.dp)
        )

        Spacer(Modifier.height(26.dp))

        Text(
            text = "Welcome back",
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            lineHeight = 32.sp
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "Log in to continue your journey through Intramuros.",
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = TextSecondary
        )

        Spacer(Modifier.height(26.dp))

        AuthTextField(
            value = uiState.email,
            onValueChange = { loginViewModel.onEmailChange(it) },
            label = "Email",
            leadingIcon = Icons.Default.Email,
            enabled = !uiState.isLoading
        )

        Spacer(Modifier.height(14.dp))

        AuthTextField(
            value = uiState.password,
            onValueChange = { loginViewModel.onPasswordChange(it) },
            label = "Password",
            leadingIcon = Icons.Default.Lock,
            isPassword = true,
            enabled = !uiState.isLoading
        )

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = "Forgot Password?",
                color = Primary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.clickable(enabled = !uiState.isLoading) {
                    navController.navigate(Routes.ForgotPassword.route)
                }
            )
        }

        if (uiState.errorMessage != null) {

            Spacer(Modifier.height(14.dp))

            AuthStatusBanner(
                message = uiState.errorMessage ?: "",
                isError = true
            )
        }

        Spacer(Modifier.height(24.dp))

        PrimaryButton(
            text = if (uiState.cooldownSeconds > 0) {
                "Try again in ${uiState.cooldownSeconds}s"
            } else {
                "Login"
            },
            enabled = uiState.cooldownSeconds == 0,
            isLoading = uiState.isLoading,
            onClick = {
                loginViewModel.login()
            }
        )

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {

            Text(
                text = "Don't have an account? ",
                color = TextSecondary,
                fontSize = 13.5.sp
            )

            Text(
                text = "Register",
                color = Primary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp,
                modifier = Modifier.clickable {
                    navController.navigate(Routes.Register.route)
                }
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}
