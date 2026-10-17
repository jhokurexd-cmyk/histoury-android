package com.histoury.app.ui.screens.auth

import com.histoury.app.theme.softShadow
import com.histoury.app.navigation.safePopBackStack
import com.histoury.app.navigation.navigateSafely
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.histoury.app.util.Validators
import com.histoury.app.util.rememberFormState
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
 * Step 1 of the Forgot Password wizard: just the email, to send the code.
 * See OtpVerificationScreen and ResetPasswordScreen for steps 2 and 3.
 */
@Composable
fun ForgotPasswordScreen(
    navController: NavController,
    forgotPasswordViewModel: ForgotPasswordViewModel = viewModel()
) {

    val uiState by forgotPasswordViewModel.uiState.collectAsState()

    // Live validation with the shared email rule.
    val form = rememberFormState()
    val emailError = Validators.email(uiState.email)
    fun submit() {
        if (form.submit(emailError)) forgotPasswordViewModel.sendCode()
    }

    LaunchedEffect(uiState.codeSent) {
        if (uiState.codeSent) {
            forgotPasswordViewModel.codeSentShown()
            navController.navigateSafely(Routes.OtpVerification.route)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceSoft)
            // Outside the scroll, not inside it: an inset applied after
            // verticalScroll scrolls away with the content and the header
            // slides back under the cutout.
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp)
    ) {

        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .size(38.dp)
                .softShadow(elevation = 2.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(CardSurface)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = !uiState.isLoading
                ) {
                    navController.safePopBackStack()
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
                imageVector = Icons.Default.LockReset,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Forgot Password",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "Enter your account email and we'll send you a code.",
            fontSize = 13.5.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth(0.85f)
        )

        Spacer(Modifier.height(28.dp))

        AuthTextField(
            value = uiState.email,
            onValueChange = {
                forgotPasswordViewModel.onEmailChange(it)
                form.touch("email")
            },
            label = "Email",
            leadingIcon = Icons.Default.Mail,
            keyboardType = KeyboardType.Email,
            enabled = !uiState.isLoading,
            imeAction = androidx.compose.ui.text.input.ImeAction.Done,
            onImeAction = { submit() },
            error = form.shown("email", emailError),
            onFocusLost = { form.touch("email") }
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
            text = "Send Code",
            isLoading = uiState.isLoading,
            onClick = { submit() }
        )

        Spacer(Modifier.height(20.dp))

        Text(
            text = "Back to Login",
            color = TextSecondary,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable(enabled = !uiState.isLoading) {
                    navController.safePopBackStack()
                }
        )

        Spacer(Modifier.height(32.dp))
    }
}
