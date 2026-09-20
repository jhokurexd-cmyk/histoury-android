package com.histoury.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Password
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.histoury.app.data.viewmodel.ForgotPasswordViewModel
import com.histoury.app.navigation.Routes
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Primary
import com.histoury.app.theme.PrimarySoft
import com.histoury.app.theme.SurfaceSoft
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary
import com.histoury.app.ui.components.AuthStatusBanner
import com.histoury.app.ui.components.AuthTextField
import com.histoury.app.ui.components.PrimaryButton

/**
 * Step 3 of the Forgot Password wizard: only reached once
 * OtpVerificationScreen has confirmed the code. No OTP field here — just
 * the new password.
 */
@Composable
fun ResetPasswordScreen(
    navController: NavController,
    forgotPasswordViewModel: ForgotPasswordViewModel = viewModel()
) {

    val uiState by forgotPasswordViewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceSoft)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp)
    ) {

        Spacer(Modifier.height(24.dp))

        if (!uiState.resetSucceeded) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .shadow(elevation = 2.dp, shape = CircleShape)
                    .clip(CircleShape)
                    .background(CardSurface)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !uiState.isLoading
                    ) {
                        navController.popBackStack()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(64.dp)
                .clip(CircleShape)
                .background(PrimarySoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (uiState.resetSucceeded) Icons.Default.CheckCircle else Icons.Default.LockReset,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = if (uiState.resetSucceeded) "Password Reset" else "Create New Password",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = if (uiState.resetSucceeded) {
                "You can now log in with your new password."
            } else {
                "Choose a new password for your account."
            },
            fontSize = 13.5.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth(0.85f)
        )

        Spacer(Modifier.height(28.dp))

        if (uiState.resetSucceeded) {

            PrimaryButton(
                text = "Back to Login",
                onClick = {
                    // Pops the whole wizard off in one go, not just this
                    // screen — a plain popBackStack() here would only land
                    // back on the OTP screen, since this is now three
                    // screens deep instead of one.
                    navController.popBackStack(
                        Routes.ForgotPasswordFlow.route,
                        inclusive = true
                    )
                }
            )

        } else {

            AuthTextField(
                value = uiState.newPassword,
                onValueChange = { forgotPasswordViewModel.onNewPasswordChange(it) },
                label = "New Password",
                leadingIcon = Icons.Default.Lock,
                isPassword = true,
                enabled = !uiState.isLoading
            )

            Spacer(Modifier.height(12.dp))

            AuthTextField(
                value = uiState.confirmPassword,
                onValueChange = { forgotPasswordViewModel.onConfirmPasswordChange(it) },
                label = "Confirm New Password",
                leadingIcon = Icons.Default.Password,
                isPassword = true,
                enabled = !uiState.isLoading
            )

            if (uiState.errorMessage != null) {

                Spacer(Modifier.height(14.dp))

                AuthStatusBanner(
                    message = uiState.errorMessage ?: "",
                    isError = true
                )
            }

            Spacer(Modifier.height(24.dp))

            PrimaryButton(
                text = "Reset Password",
                isLoading = uiState.isLoading,
                onClick = { forgotPasswordViewModel.resetPassword() }
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}
