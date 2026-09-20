package com.histoury.app.ui.screens.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.histoury.app.R
import com.histoury.app.data.viewmodel.RegisterStep
import com.histoury.app.data.viewmodel.RegisterViewModel
import com.histoury.app.navigation.Routes
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Outline
import com.histoury.app.theme.Primary
import com.histoury.app.theme.SurfaceSoft
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary
import com.histoury.app.ui.components.AuthStatusBanner
import com.histoury.app.ui.components.AuthTextField
import com.histoury.app.ui.components.ConsentModalSheet
import com.histoury.app.ui.components.OtpInputField
import com.histoury.app.ui.components.PrimaryButton
import com.histoury.app.ui.components.RequiredMarker

@Composable
fun RegisterScreen(
    navController: NavController,
    registerViewModel: RegisterViewModel = viewModel()
) {

    val uiState by registerViewModel.uiState.collectAsState()

    // The checkbox can't be checked directly — tapping it while unchecked
    // opens this modal instead. Closing the modal (any way: the button,
    // the X, or swiping it away) is what actually marks it agreed.
    var showConsentModal by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.registerSucceeded) {
        if (uiState.registerSucceeded) {
            navController.navigate(Routes.Home.route) {
                popUpTo(Routes.AuthGate.route) {
                    inclusive = true
                }
            }
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
                    if (uiState.step == RegisterStep.DETAILS) {
                        navController.popBackStack()
                    } else {
                        registerViewModel.backToDetails()
                    }
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

        Image(
            painter = painterResource(R.drawable.logo_histoury),
            contentDescription = "Histoury",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(110.dp)
                .height(119.dp)
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = if (uiState.step == RegisterStep.DETAILS) {
                "Create Account"
            } else {
                "Verify Your Email"
            },
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = if (uiState.step == RegisterStep.DETAILS) {
                "Join Histoury and start exploring Intramuros"
            } else {
                "We sent a 6-digit code to ${uiState.email.trim()}"
            },
            fontSize = 13.5.sp,
            color = TextSecondary,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(Modifier.height(28.dp))

        if (uiState.step == RegisterStep.DETAILS) {

            AuthTextField(
                value = uiState.firstName,
                onValueChange = { registerViewModel.onFirstNameChange(it) },
                label = "First Name",
                leadingIcon = Icons.Default.Person,
                enabled = !uiState.isLoading,
                required = true
            )

            Spacer(Modifier.height(12.dp))

            AuthTextField(
                value = uiState.lastName,
                onValueChange = { registerViewModel.onLastNameChange(it) },
                label = "Last Name",
                leadingIcon = Icons.Default.Person,
                enabled = !uiState.isLoading,
                required = true
            )

            Spacer(Modifier.height(12.dp))

            AuthTextField(
                value = uiState.email,
                onValueChange = { registerViewModel.onEmailChange(it) },
                label = "Email",
                leadingIcon = Icons.Default.Email,
                keyboardType = KeyboardType.Email,
                enabled = !uiState.isLoading,
                required = true
            )

            Spacer(Modifier.height(12.dp))

            AuthTextField(
                value = uiState.password,
                onValueChange = { registerViewModel.onPasswordChange(it) },
                label = "Password",
                leadingIcon = Icons.Default.Lock,
                isPassword = true,
                enabled = !uiState.isLoading,
                required = true
            )

            Spacer(Modifier.height(12.dp))

            AuthTextField(
                value = uiState.confirmPassword,
                onValueChange = { registerViewModel.onConfirmPasswordChange(it) },
                label = "Confirm Password",
                leadingIcon = Icons.Default.Password,
                isPassword = true,
                enabled = !uiState.isLoading,
                required = true
            )

            Spacer(Modifier.height(18.dp))

            ConsentRow(
                agreed = uiState.agreedToTerms,
                enabled = !uiState.isLoading,
                onCheckboxClick = {
                    if (uiState.agreedToTerms) {
                        // Already agreed — unchecking doesn't need to
                        // re-open the modal, just flip it off directly.
                        registerViewModel.onAgreedToTermsChange(false)
                    } else {
                        showConsentModal = true
                    }
                },
                onOpenModal = {
                    showConsentModal = true
                }
            )

        } else {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "6-digit code",
                    color = TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                RequiredMarker()
            }

            Spacer(Modifier.height(8.dp))

            OtpInputField(
                value = uiState.otpCode,
                onValueChange = { registerViewModel.onOtpCodeChange(it) },
                enabled = !uiState.isLoading
            )

            if (uiState.infoMessage != null) {

                Spacer(Modifier.height(12.dp))

                AuthStatusBanner(
                    message = uiState.infoMessage ?: "",
                    isError = false
                )
            }

            Spacer(Modifier.height(12.dp))

            val cooldown = uiState.resendCooldownSeconds

            Text(
                text = if (cooldown > 0) {
                    "Resend code in ${cooldown}s"
                } else {
                    "Resend code"
                },
                color = if (cooldown > 0) TextSecondary else Primary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.clickable(enabled = !uiState.isLoading && cooldown == 0) {
                    registerViewModel.resendCode()
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
            text = if (uiState.step == RegisterStep.DETAILS) {
                "Register"
            } else {
                "Verify & Create Account"
            },
            isLoading = uiState.isLoading,
            onClick = {
                if (uiState.step == RegisterStep.DETAILS) {
                    registerViewModel.register()
                } else {
                    registerViewModel.verifyAndRegister()
                }
            }
        )

        Spacer(Modifier.height(22.dp))

        if (uiState.step == RegisterStep.DETAILS) {

            Row(
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {

                Text(
                    text = "Already have an account? ",
                    color = TextSecondary,
                    fontSize = 13.5.sp
                )

                Text(
                    text = "Login",
                    color = Primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    modifier = Modifier.clickable {
                        navController.popBackStack()
                    }
                )
            }

        } else {

            Text(
                text = "Wrong email? Go back",
                color = TextSecondary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable(enabled = !uiState.isLoading) {
                        registerViewModel.backToDetails()
                    }
            )
        }

        Spacer(Modifier.height(32.dp))
    }

    if (showConsentModal) {
        ConsentModalSheet(
            onDismiss = {
                showConsentModal = false
                registerViewModel.onAgreedToTermsChange(true)
            }
        )
    }
}

/**
 * Consent checkbox + tappable links to the Terms and Privacy Policy.
 *
 * The checkbox can't be checked directly — tapping it (or the links in the
 * paragraph) while unchecked opens the Terms & Privacy modal instead;
 * closing that modal is what actually marks it agreed. Already-agreed can
 * still be unchecked directly, no need to reopen the modal for that.
 *
 * Uses the project's Box().clickable() pattern instead of Material3's
 * Checkbox, which silently enforces a 48dp touch target and breaks
 * compact layouts.
 */
@Composable
private fun ConsentRow(
    agreed: Boolean,
    enabled: Boolean,
    onCheckboxClick: () -> Unit,
    onOpenModal: () -> Unit
) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {

        Box(
            modifier = Modifier
                .padding(top = 1.dp)
                .size(20.dp)
                .background(
                    color = if (agreed) Primary else Color.White,
                    shape = RoundedCornerShape(6.dp)
                )
                .border(
                    width = 1.5.dp,
                    color = if (agreed) Primary else Outline,
                    shape = RoundedCornerShape(6.dp)
                )
                .clickable(enabled = enabled, onClick = onCheckboxClick),
            contentAlignment = Alignment.Center
        ) {
            if (agreed) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Agreed",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        val termsTag = "terms"

        val privacyTag = "privacy"

        val annotatedText = buildAnnotatedString {

            append("I have read and agree to the ")

            pushStringAnnotation(tag = termsTag, annotation = termsTag)
            withStyle(SpanStyle(color = Primary, fontWeight = FontWeight.Bold)) {
                append("Terms & Conditions")
            }
            pop()

            append(" and ")

            pushStringAnnotation(tag = privacyTag, annotation = privacyTag)
            withStyle(SpanStyle(color = Primary, fontWeight = FontWeight.Bold)) {
                append("Privacy Policy")
            }
            pop()

            append(".")
        }

        ClickableText(
            text = annotatedText,
            style = TextStyle(
                fontSize = 13.sp,
                color = TextSecondary,
                lineHeight = 19.sp
            ),
            onClick = { offset ->

                if (!enabled) return@ClickableText

                val termsHit = annotatedText
                    .getStringAnnotations(termsTag, offset, offset)
                    .firstOrNull()

                val privacyHit = annotatedText
                    .getStringAnnotations(privacyTag, offset, offset)
                    .firstOrNull()

                if (termsHit != null || privacyHit != null) {
                    onOpenModal()
                    return@ClickableText
                }

                // Tapping the plain part of the sentence behaves the same
                // as tapping the checkbox itself.
                onCheckboxClick()
            }
        )
    }
}
