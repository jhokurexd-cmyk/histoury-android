package com.histoury.app.ui.screens.auth

import com.histoury.app.navigation.navigateSafely
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.histoury.app.R
import com.histoury.app.data.repository.SuspensionInfoHolder
import com.histoury.app.data.viewmodel.LoginViewModel
import com.histoury.app.navigation.Routes
import com.histoury.app.theme.Primary
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary
import com.histoury.app.ui.components.AuthBackdrop
import com.histoury.app.ui.components.AuthStatusBanner
import com.histoury.app.ui.components.AuthTextField
import com.histoury.app.ui.components.PrimaryButton
import com.histoury.app.util.Validators
import com.histoury.app.util.rememberFormState

@Composable
fun LoginScreen(
    navController: NavController,
    loginViewModel: LoginViewModel = viewModel()
) {

    val uiState by loginViewModel.uiState.collectAsState()

    // Live validation (shared rules): shown once a field is typed in or
    // left, and for every field when Log in is pressed.
    val form = rememberFormState()
    val emailError = Validators.email(uiState.email)
    val passwordError = Validators.loginPassword(uiState.password)
    fun submit() {
        if (form.submit(emailError, passwordError)) loginViewModel.login()
    }

    LaunchedEffect(uiState.loginSucceeded) {
        if (uiState.loginSucceeded) {
            // Clear everything under Home. popUpTo(AuthGate) did nothing here
            // (AuthGate is already gone by now), which left Onboarding/Login
            // underneath — so Back on Home returned to the Login screen.
            navController.navigateSafely(Routes.Home.route) {
                popUpTo(navController.graph.id) {
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
            navController.navigateSafely(Routes.Suspended.route)
        }
    }

    AuthBackdrop {

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Insets outside the scroll so the logo never slides under the
            // status bar; nav bar + keyboard keep the button reachable.
            .statusBarsPadding()
            .navigationBarsPadding()
            // Lifts the form clear of the keyboard. Without it the password
            // field and the Login button sit underneath it on a short
            // screen, and the only way out is to dismiss the keyboard first.
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp)
    ) {

        Spacer(Modifier.height(28.dp))

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
            onValueChange = {
                loginViewModel.onEmailChange(it)
                form.touch("email")
            },
            label = "Email",
            leadingIcon = Icons.Default.Email,
            keyboardType = KeyboardType.Email,
            enabled = !uiState.isLoading,
            error = form.shown("email", emailError),
            onFocusLost = { form.touch("email") }
        )

        Spacer(Modifier.height(14.dp))

        AuthTextField(
            value = uiState.password,
            onValueChange = {
                loginViewModel.onPasswordChange(it)
                form.touch("password")
            },
            label = "Password",
            leadingIcon = Icons.Default.Lock,
            isPassword = true,
            enabled = !uiState.isLoading,
            imeAction = ImeAction.Done,
            onImeAction = { submit() },
            error = form.shown("password", passwordError),
            onFocusLost = { form.touch("password") }
        )

        Spacer(Modifier.height(2.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = { navController.navigateSafely(Routes.ForgotPassword.route) { launchSingleTop = true } },
                enabled = !uiState.isLoading
            ) {
                Text(
                    text = "Forgot Password?",
                    color = Primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp
                )
            }
        }

        if (uiState.errorMessage != null) {

            Spacer(Modifier.height(6.dp))

            AuthStatusBanner(
                message = uiState.errorMessage ?: "",
                isError = true
            )
        }

        Spacer(Modifier.height(18.dp))

        PrimaryButton(
            text = if (uiState.cooldownSeconds > 0) {
                "Try again in ${uiState.cooldownSeconds}s"
            } else {
                "Log in"
            },
            enabled = uiState.cooldownSeconds == 0,
            isLoading = uiState.isLoading,
            onClick = {
                submit()
            }
        )

        Spacer(Modifier.height(24.dp))

        // Whole line is the tap target (48dp), not just the word.
        TextButton(
            onClick = {
                // Single top: a double tap mustn't stack two Register screens.
                navController.navigateSafely(Routes.Register.route) { launchSingleTop = true }
            },
            enabled = !uiState.isLoading,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(
                text = "Don't have an account? ",
                color = TextSecondary,
                fontSize = 14.sp
            )
            Text(
                text = "Register",
                color = Primary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        Spacer(Modifier.height(24.dp))
    }
    }
}
