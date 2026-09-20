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
import androidx.compose.material.icons.filled.MarkEmailRead
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
import com.histoury.app.ui.components.OtpInputField
import com.histoury.app.ui.components.PrimaryButton

/**
 * Step 2 of the Forgot Password wizard: the OTP alone, on its own screen —
 * no password fields here. Verifying moves on to ResetPasswordScreen; the
 * password itself isn't collected until there.
 */
@Composable
fun OtpVerificationScreen(
    navController: NavController,
    forgotPasswordViewModel: ForgotPasswordViewModel = viewModel()
) {

    val uiState by forgotPasswordViewModel.uiState.collectAsState()

    LaunchedEffect(uiState.otpVerified) {
        if (uiState.otpVerified) {
            forgotPasswordViewModel.otpVerifiedShown()
            navController.navigate(Routes.ResetPassword.route)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceSoft)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp)
    ) {

        Spacer(Modifier.height(24.dp))

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
                imageVector = Icons.Default.MarkEmailRead,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Verify Code",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "We sent a 6-digit code to ${uiState.email.trim()}",
            fontSize = 13.5.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth(0.85f)
        )

        Spacer(Modifier.height(28.dp))

        OtpInputField(
            value = uiState.otpCode,
            onValueChange = { forgotPasswordViewModel.onOtpCodeChange(it) },
            enabled = !uiState.isLoading
        )

        if (uiState.infoMessage != null) {

            Spacer(Modifier.height(14.dp))

            AuthStatusBanner(
                message = uiState.infoMessage ?: "",
                isError = false
            )
        }

        Spacer(Modifier.height(14.dp))

        val cooldown = uiState.resendCooldownSeconds

        Text(
            text = if (cooldown > 0) {
                "Resend code in %d:%02d".format(cooldown / 60, cooldown % 60)
            } else {
                "Resend code"
            },
            color = if (cooldown > 0) TextSecondary else Primary,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier.clickable(enabled = !uiState.isLoading && cooldown == 0) {
                forgotPasswordViewModel.resendCode()
            }
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
            text = "Verify Code",
            isLoading = uiState.isLoading,
            onClick = { forgotPasswordViewModel.verifyOtp() }
        )

        Spacer(Modifier.height(20.dp))

        Text(
            text = "Wrong email? Go back",
            color = TextSecondary,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable(enabled = !uiState.isLoading) {
                    navController.popBackStack()
                }
        )

        Spacer(Modifier.height(32.dp))
    }
}
