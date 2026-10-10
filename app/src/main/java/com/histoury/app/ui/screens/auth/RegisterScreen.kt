package com.histoury.app.ui.screens.auth

import com.histoury.app.navigation.safePopBackStack
import com.histoury.app.navigation.navigateSafely
import androidx.activity.compose.BackHandler
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
import com.histoury.app.ui.components.AsyncCheckLine
import com.histoury.app.ui.components.AuthBackdrop
import com.histoury.app.ui.components.AuthStatusBanner
import com.histoury.app.ui.components.HeroIconButton
import com.histoury.app.ui.components.AuthTextField
import com.histoury.app.ui.components.ConsentModalSheet
import com.histoury.app.ui.components.FieldErrorText
import com.histoury.app.ui.components.OtpInputField
import com.histoury.app.ui.components.PasswordChecklist
import com.histoury.app.util.AsyncCheck
import com.histoury.app.util.rememberFormState
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

    // Live validation: which fields have been touched / whether Register
    // was pressed. The errors themselves come from uiState (shared rules).
    val form = rememberFormState()
    val otpForm = rememberFormState()

    // A fresh code step each time (e.g. after "Wrong email? Go back").
    LaunchedEffect(uiState.step) { otpForm.reset() }

    LaunchedEffect(uiState.registerSucceeded) {
        if (uiState.registerSucceeded) {
            // Clear the whole auth flow (Onboarding/Login/Register) so Back
            // on Home leaves the app instead of reopening Register.
            navController.navigateSafely(Routes.Home.route) {
                popUpTo(navController.graph.id) {
                    inclusive = true
                }
            }
        }
    }

    // Without this, the system/gesture back button falls through to the
    // default NavController behavior and pops this whole route — discarding
    // the entered name/email/password with no confirmation — instead of
    // matching the in-app back arrow above, which returns to DETAILS and
    // keeps everything but the OTP code. Only handle it on VERIFY_EMAIL:
    // on DETAILS, the system back button popping the route is the
    // correct/expected behavior (same as the arrow there).
    if (uiState.step == RegisterStep.VERIFY_EMAIL) {
        BackHandler {
            registerViewModel.backToDetails()
        }
    }

    AuthBackdrop {

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Outside the scroll, not inside it: an inset applied after
            // verticalScroll scrolls away with the content and the header
            // slides back under the cutout.
            .statusBarsPadding()
            .navigationBarsPadding()
            // Keeps the lower fields and the Register button above the
            // keyboard instead of hidden behind it.
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp)
    ) {

        Spacer(Modifier.height(12.dp))

        // Same 44dp round back button as the rest of the app.
        HeroIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = if (uiState.step == RegisterStep.DETAILS) "Back to log in" else "Back to details",
            onClick = {
                if (!uiState.isLoading) {
                    if (uiState.step == RegisterStep.DETAILS) {
                        navController.safePopBackStack()
                    } else {
                        registerViewModel.backToDetails()
                    }
                }
            }
        )

        Spacer(Modifier.height(4.dp))

        // Width only, like Login: a fixed width AND height forced an aspect
        // the artwork doesn't have.
        Image(
            painter = painterResource(R.drawable.logo_histoury),
            contentDescription = "Histoury",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(100.dp)
        )

        Spacer(Modifier.height(20.dp))

        Text(
            text = if (uiState.step == RegisterStep.DETAILS) {
                "Create your account"
            } else {
                "Verify Your Email"
            },
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            lineHeight = 32.sp
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = if (uiState.step == RegisterStep.DETAILS) {
                "Join Histoury and start exploring Intramuros."
            } else {
                "We sent a 6-digit code to ${uiState.email.trim()}"
            },
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = TextSecondary
        )

        Spacer(Modifier.height(26.dp))

        if (uiState.step == RegisterStep.DETAILS) {

            AuthTextField(
                value = uiState.firstName,
                onValueChange = {
                    registerViewModel.onFirstNameChange(it)
                    form.touch("firstName")
                },
                label = "First Name",
                leadingIcon = Icons.Default.Person,
                enabled = !uiState.isLoading,
                required = true,
                error = form.shown("firstName", uiState.firstNameError),
                onFocusLost = { form.touch("firstName") }
            )

            Spacer(Modifier.height(12.dp))

            AuthTextField(
                value = uiState.lastName,
                onValueChange = {
                    registerViewModel.onLastNameChange(it)
                    form.touch("lastName")
                },
                label = "Last Name",
                leadingIcon = Icons.Default.Person,
                enabled = !uiState.isLoading,
                required = true,
                error = form.shown("lastName", uiState.lastNameError),
                onFocusLost = { form.touch("lastName") }
            )

            Spacer(Modifier.height(12.dp))

            val shownEmailError = form.shown("email", uiState.emailError)
            AuthTextField(
                value = uiState.email,
                onValueChange = {
                    registerViewModel.onEmailChange(it)
                    form.touch("email")
                },
                label = "Email",
                leadingIcon = Icons.Default.Email,
                keyboardType = KeyboardType.Email,
                enabled = !uiState.isLoading,
                required = true,
                error = shownEmailError,
                onFocusLost = { form.touch("email") },
                supportingContent = {
                    // "Checking…" / "Email available" (a taken email shows
                    // as the field's error instead).
                    AsyncCheckLine(
                        status = if (shownEmailError == null) uiState.emailCheck else AsyncCheck.IDLE,
                        availableText = "Email available"
                    )
                }
            )

            Spacer(Modifier.height(12.dp))

            AuthTextField(
                value = uiState.password,
                onValueChange = {
                    registerViewModel.onPasswordChange(it)
                    form.touch("password")
                },
                label = "Password",
                leadingIcon = Icons.Default.Lock,
                isPassword = true,
                enabled = !uiState.isLoading,
                required = true,
                error = form.shown("password", uiState.passwordError),
                onFocusLost = { form.touch("password") },
                // Requirements tick off as they're met.
                supportingContent = { PasswordChecklist(uiState.password) }
            )

            Spacer(Modifier.height(12.dp))

            AuthTextField(
                value = uiState.confirmPassword,
                onValueChange = {
                    registerViewModel.onConfirmPasswordChange(it)
                    form.touch("confirmPassword")
                },
                label = "Confirm Password",
                leadingIcon = Icons.Default.Password,
                isPassword = true,
                enabled = !uiState.isLoading,
                required = true,
                error = form.shown("confirmPassword", uiState.confirmPasswordError),
                onFocusLost = { form.touch("confirmPassword") },
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                onImeAction = {
                    if (form.submit(*uiState.detailsErrors.toTypedArray())) registerViewModel.register()
                }
            )

            Spacer(Modifier.height(18.dp))

            ConsentRow(
                agreed = uiState.agreedToTerms,
                enabled = !uiState.isLoading,
                onCheckboxClick = {
                    form.touch("terms")
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

            FieldErrorText(form.shown("terms", uiState.termsError))

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
                onValueChange = {
                    registerViewModel.onOtpCodeChange(it)
                    otpForm.touch("otp")
                },
                enabled = !uiState.isLoading
            )

            // Only once all six boxes were reached or Verify pressed —
            // "must be 6 digits" after the first digit would just nag.
            FieldErrorText(
                if (otpForm.submitted || uiState.otpCode.length == 6) otpForm.shown("otp", uiState.otpError) else null
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
                // Final check: reveals every remaining field error in place
                // and only sends valid data.
                if (uiState.step == RegisterStep.DETAILS) {
                    if (form.submit(*uiState.detailsErrors.toTypedArray())) {
                        registerViewModel.register()
                    }
                } else {
                    if (otpForm.submit(uiState.otpError)) {
                        registerViewModel.verifyAndRegister()
                    }
                }
            }
        )

        Spacer(Modifier.height(12.dp))

        if (uiState.step == RegisterStep.DETAILS) {

            TextButton(
                onClick = { navController.safePopBackStack() },
                enabled = !uiState.isLoading,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = "Already have an account? ",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
                Text(
                    text = "Log in",
                    color = Primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

        } else {

            TextButton(
                onClick = { registerViewModel.backToDetails() },
                enabled = !uiState.isLoading,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = "Wrong email? Go back",
                    color = TextSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
    }

    if (showConsentModal) {
        ConsentModalSheet(
            // Only the explicit "I Agree" button records consent. Closing
            // the sheet any other way (X, swipe, tapping outside) used to
            // tick the box too — consent under RA 10173 has to be an
            // affirmative act, not a side effect of dismissing a sheet.
            onDismiss = { showConsentModal = false },
            onAgree = {
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
