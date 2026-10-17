package com.histoury.app.ui.screens.profile

import com.histoury.app.theme.SheetSurface
import com.histoury.app.navigation.safePopBackStack
import com.histoury.app.navigation.navigateSafely
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.histoury.app.data.repository.EmailOtpRepository
import com.histoury.app.ui.components.AsyncCheckLine
import com.histoury.app.ui.components.FieldErrorText
import com.histoury.app.ui.components.PasswordChecklist
import com.histoury.app.util.AsyncCheck
import com.histoury.app.util.Validators
import com.histoury.app.util.rememberFormState
import kotlinx.coroutines.delay
import com.histoury.app.data.viewmodel.ProfileDialog
import com.histoury.app.data.viewmodel.ProfileViewModel
import com.histoury.app.navigation.Routes
import com.histoury.app.theme.AccentGold
import com.histoury.app.theme.Success
import com.histoury.app.theme.Outline as DividerColor
import com.histoury.app.theme.TextSecondary
import com.histoury.app.theme.TextPrimary as TextMain
import com.histoury.app.theme.SurfaceSoft as BackgroundGray
import com.histoury.app.theme.Primary as PrimaryPink
import com.histoury.app.theme.CardSurface
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.histoury.app.data.viewmodel.ProfileUiState
import com.histoury.app.ui.components.AuthStatusBanner
import com.histoury.app.ui.components.AuthTextField
import com.histoury.app.ui.components.OtpInputField
import com.histoury.app.ui.components.PrimaryButton

// These file-local names predate the theme system; they now alias the
// shared palette so this screen follows dark mode with the rest of the app.

@Composable
fun ProfileScreen(
    navController: NavHostController,
    profileViewModel: ProfileViewModel = viewModel()
) {
    val uiState by profileViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        profileViewModel.loadProfile()
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            profileViewModel.uploadProfilePhoto(uri)
        }
    }

    val context = LocalContext.current

    // Confirmations ("Password updated.") and errors that happen with no
    // dialog open (e.g. a failed photo upload) surface as a toast.
    LaunchedEffect(uiState.updateSuccessMessage) {
        uiState.updateSuccessMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            // Shown once; coming back to Profile or rotating used to repeat it.
            profileViewModel.consumeSuccessMessage()
        }
    }

    LaunchedEffect(uiState.updateErrorMessage, uiState.activeDialog) {
        val message = uiState.updateErrorMessage
        if (message != null && uiState.activeDialog == ProfileDialog.NONE) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            profileViewModel.consumeErrorMessage()
        }
    }

    // The email changed server-side but the silent re-sign-in failed, so the
    // ViewModel signed the user out. Send them to Login to sign in with the
    // new address instead of leaving them on a profile with no session.
    LaunchedEffect(uiState.emailChangeComplete) {
        if (uiState.emailChangeComplete) {
            Toast.makeText(
                context,
                "Email changed. Please sign in with your new email.",
                Toast.LENGTH_LONG
            ).show()
            navController.navigateSafely(Routes.Login.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    ProfileDialogs(uiState = uiState, viewModel = profileViewModel)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGray)
            .statusBarsPadding()
    ) {
        // --- Header ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = "My Profile",
                color = TextMain,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(CardSurface)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        navController.safePopBackStack()
                    }
                    .align(Alignment.CenterStart),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextMain,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // --- Profile Summary Card ---
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                color = CardSurface
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.size(100.dp)) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(PrimaryPink.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (uiState.photoUrl.isNotBlank()) {
                                AsyncImage(
                                    model = uiState.photoUrl,
                                    contentDescription = "Profile photo",
                                    // Without this the image defaults to
                                    // Fit, which letterboxes a non-square
                                    // photo inside the circle and reads as
                                    // the avatar being zoomed out. Crop
                                    // fills the frame and trims the
                                    // overflow, matching every other place
                                    // this photo appears.
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = PrimaryPink,
                                    modifier = Modifier.size(48.dp)
                                )
                            }

                            if (uiState.isUploadingPhoto) {
                                Box(
                                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(AccentGold)
                                .clickable {
                                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.CameraAlt, contentDescription = "Edit photo", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = uiState.displayName.ifBlank { "Tourist User" },
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMain
                        )
                        Spacer(Modifier.width(0.dp))
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit name",
                            tint = PrimaryPink,
                            // 18dp icon inside a 40dp tap area — the bare
                            // icon was hard to hit.
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { profileViewModel.openDialog(ProfileDialog.EDIT_NAME) }
                                .padding(11.dp)
                                .size(18.dp)
                        )
                    }

                    Text(text = uiState.email, fontSize = 14.sp, color = TextSecondary)
                }
            }

            // --- Stats Card ---
            ProfileSection(title = "Your Activity") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    StatItem(value = uiState.sitesVisitedCount.toString(), label = "Sites", color = PrimaryPink)
                    StatItem(value = uiState.photosCount.toString(), label = "Photos", color = AccentGold)
                    StatItem(value = uiState.reviewsCount.toString(), label = "Reviews", color = Success)
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = DividerColor, thickness = 0.5.dp)
                ProfileRow(
                    icon = Icons.Default.History,
                    label = "View Visit History",
                    onClick = { navController.navigateSafely(Routes.History.route) }
                )
            }

            // --- Account Settings Card ---
            ProfileSection(title = "Account Settings") {
                ProfileRow(
                    icon = Icons.Default.Email,
                    label = "Change Email",
                    onClick = { profileViewModel.openDialog(ProfileDialog.EDIT_EMAIL) }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = DividerColor, thickness = 0.5.dp)
                ProfileRow(
                    icon = Icons.Default.Lock,
                    label = "Update Password",
                    onClick = { profileViewModel.openDialog(ProfileDialog.EDIT_PASSWORD) }
                )
            }

            // --- Info Card ---
            ProfileSection(title = "Information") {
                ProfileRow(
                    icon = Icons.Default.CalendarToday,
                    label = "Member Since",
                    secondaryText = uiState.memberSince,
                    showChevron = false,
                    onClick = {}
                )
            }
        }
    }
}

@Composable
private fun ProfileSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(text = title, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = CardSurface) {
            Column(content = content)
        }
    }
}

@Composable
private fun StatItem(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
        Text(text = label, fontSize = 12.sp, color = TextSecondary)
    }
}

@Composable
private fun ProfileRow(icon: ImageVector, label: String, onClick: () -> Unit, secondaryText: String? = null, showChevron: Boolean = true) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(BackgroundGray), contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = null, tint = PrimaryPink, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(text = label, color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (secondaryText != null) Text(text = secondaryText, color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp))
        if (showChevron) Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextSecondary.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
    }
}

// ---------------------------------------------------------------- dialogs
//
// ProfileViewModel drives which dialog is open (activeDialog) and does all
// the work — validation, re-authentication, the OTP Cloud Functions. These
// composables only collect input and show its state.

@Composable
private fun ProfileDialogs(
    uiState: ProfileUiState,
    viewModel: ProfileViewModel
) {
    when (uiState.activeDialog) {
        ProfileDialog.NONE -> Unit
        ProfileDialog.EDIT_NAME -> EditNameDialog(uiState, viewModel)
        ProfileDialog.EDIT_EMAIL -> EditEmailDialog(uiState, viewModel)
        ProfileDialog.EDIT_EMAIL_OTP -> EmailOtpDialog(uiState, viewModel)
        ProfileDialog.EDIT_PASSWORD -> EditPasswordDialog(uiState, viewModel)
    }
}

/** Shared frame: title, subtitle, error banner, body, and a Cancel link. */
@Composable
private fun ProfileDialogFrame(
    title: String,
    subtitle: String,
    errorMessage: String?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        // Don't let a stray tap outside cancel a request that's in flight.
        onDismissRequest = { if (!isBusy) onDismiss() }
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SheetSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMain
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )

                Spacer(Modifier.height(16.dp))

                if (errorMessage != null) {
                    AuthStatusBanner(message = errorMessage, isError = true)
                    Spacer(Modifier.height(12.dp))
                }

                content()

                Spacer(Modifier.height(4.dp))

                TextButton(
                    onClick = onDismiss,
                    enabled = !isBusy,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Cancel", color = TextSecondary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun EditNameDialog(uiState: ProfileUiState, viewModel: ProfileViewModel) {

    // Prefill from the stored first and last name. Only when those aren't
    // known (an older account) is the full name split: last word is the
    // last name, the rest is the first name.
    val parts = remember { uiState.displayName.trim().split(Regex("\\s+")).filter { it.isNotBlank() } }
    val hasStored = uiState.firstName.isNotBlank() || uiState.lastName.isNotBlank()
    var firstName by remember {
        mutableStateOf(
            if (hasStored) uiState.firstName
            else if (parts.size > 1) parts.dropLast(1).joinToString(" ") else parts.firstOrNull().orEmpty()
        )
    }
    var lastName by remember {
        mutableStateOf(if (hasStored) uiState.lastName else if (parts.size > 1) parts.last() else "")
    }

    // Second step: an explicit "are you sure" before the change is saved,
    // since saving starts the 30-day cooldown.
    var isConfirming by remember { mutableStateOf(false) }

    val newFullName = "${firstName.trim()} ${lastName.trim()}".trim()

    // Live validation (shared rules, same as Register).
    val form = rememberFormState()
    val firstNameError = Validators.name(firstName, "First name")
    val lastNameError = Validators.name(lastName, "Last name")

    // Resubmitting the same name doesn't use up the cooldown (see
    // UserProfileRepository.updateDisplayName), so it needs no warning.
    val isActualChange = !newFullName.equals(uiState.displayName.trim(), ignoreCase = false)

    // A failed save (e.g. still in cooldown) drops back to the form so the
    // error shows next to the fields it's about.
    LaunchedEffect(uiState.updateErrorMessage) {
        if (uiState.updateErrorMessage != null) isConfirming = false
    }

    if (isConfirming) {
        ProfileDialogFrame(
            title = "Confirm Name Change",
            subtitle = "Please check your new name before confirming.",
            errorMessage = null,
            isBusy = uiState.isUpdating,
            onDismiss = { viewModel.closeDialog() }
        ) {
            Text(
                text = newFullName,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextMain,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(14.dp))
            NameCooldownWarning(
                text = "Once you confirm, you won't be able to change your name " +
                    "again for 30 days."
            )
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = "Confirm Name Change",
                isLoading = uiState.isUpdating,
                enabled = !uiState.isUpdating,
                onClick = { viewModel.updateDisplayName(firstName.trim(), lastName.trim()) }
            )
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = { isConfirming = false },
                enabled = !uiState.isUpdating,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Go Back and Edit", color = PrimaryPink, fontWeight = FontWeight.Bold)
            }
        }
        return
    }

    ProfileDialogFrame(
        title = "Edit Name",
        subtitle = "Enter your first and last name as you'd like them shown.",
        errorMessage = uiState.updateErrorMessage,
        isBusy = uiState.isUpdating,
        onDismiss = { viewModel.closeDialog() }
    ) {
        NameCooldownWarning(
            text = "You can only change your name once every 30 days."
        )
        Spacer(Modifier.height(14.dp))
        AuthTextField(
            value = firstName,
            onValueChange = {
                firstName = it
                form.touch("firstName")
            },
            label = "First name",
            leadingIcon = Icons.Default.Person,
            enabled = !uiState.isUpdating,
            error = form.shown("firstName", firstNameError),
            onFocusLost = { form.touch("firstName") }
        )
        Spacer(Modifier.height(12.dp))
        AuthTextField(
            value = lastName,
            onValueChange = {
                lastName = it
                form.touch("lastName")
            },
            label = "Last name",
            leadingIcon = Icons.Default.Person,
            enabled = !uiState.isUpdating,
            error = form.shown("lastName", lastNameError),
            onFocusLost = { form.touch("lastName") }
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            text = "Continue",
            isLoading = uiState.isUpdating,
            enabled = !uiState.isUpdating,
            onClick = {
                when {
                    // Invalid: reveal the field errors, don't continue.
                    !form.submit(firstNameError, lastNameError) -> Unit
                    // Nothing changed: close without a warning.
                    !isActualChange -> viewModel.closeDialog()
                    else -> isConfirming = true
                }
            }
        )
    }
}

/** Amber notice used on both name-change steps. */
@Composable
private fun NameCooldownWarning(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AccentGold.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Default.WarningAmber,
            contentDescription = null,
            tint = AccentGold,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 12.5.sp,
            lineHeight = 17.sp,
            color = TextMain,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun EditEmailDialog(uiState: ProfileUiState, viewModel: ProfileViewModel) {

    var newEmail by remember { mutableStateOf(uiState.pendingNewEmail) }
    var currentPassword by remember { mutableStateOf("") }

    // Live validation + a debounced "already used?" check once the format
    // is valid (the code step still refuses a taken email on the server).
    val form = rememberFormState()
    val otpRepository = remember { EmailOtpRepository() }
    var emailCheck by remember { mutableStateOf(AsyncCheck.IDLE) }
    val formatError = Validators.email(newEmail)
        ?: if (newEmail.trim().equals(uiState.email, ignoreCase = true)) "That's already your current email." else null
    LaunchedEffect(newEmail, formatError) {
        if (formatError != null) {
            emailCheck = AsyncCheck.IDLE
            return@LaunchedEffect
        }
        delay(600)
        emailCheck = AsyncCheck.CHECKING
        emailCheck = otpRepository.isEmailAvailable(newEmail).fold(
            onSuccess = { if (it) AsyncCheck.AVAILABLE else AsyncCheck.TAKEN },
            onFailure = { AsyncCheck.FAILED }
        )
    }
    val emailError = formatError
        ?: if (emailCheck == AsyncCheck.TAKEN) "That email is already used by another account." else null
    val passwordError = Validators.loginPassword(currentPassword)
    val shownEmailError = form.shown("email", emailError)

    ProfileDialogFrame(
        title = "Change Email",
        subtitle = "We'll send a 6-digit code to your new email to confirm it's yours.",
        errorMessage = uiState.updateErrorMessage,
        isBusy = uiState.isUpdating,
        onDismiss = { viewModel.closeDialog() }
    ) {
        if (uiState.email.isNotBlank()) {
            Text(
                text = "Current: ${uiState.email}",
                fontSize = 12.5.sp,
                color = TextSecondary
            )
            Spacer(Modifier.height(10.dp))
        }
        AuthTextField(
            value = newEmail,
            onValueChange = {
                newEmail = it
                form.touch("email")
            },
            label = "New email",
            leadingIcon = Icons.Default.Email,
            keyboardType = KeyboardType.Email,
            enabled = !uiState.isUpdating,
            error = shownEmailError,
            onFocusLost = { form.touch("email") },
            supportingContent = {
                AsyncCheckLine(
                    status = if (shownEmailError == null) emailCheck else AsyncCheck.IDLE,
                    availableText = "Email available"
                )
            }
        )
        Spacer(Modifier.height(12.dp))
        AuthTextField(
            value = currentPassword,
            onValueChange = {
                currentPassword = it
                form.touch("password")
            },
            label = "Current password",
            leadingIcon = Icons.Default.Lock,
            isPassword = true,
            keyboardType = KeyboardType.Password,
            enabled = !uiState.isUpdating,
            error = form.shown("password", passwordError),
            onFocusLost = { form.touch("password") }
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            text = "Send Code",
            isLoading = uiState.isUpdating,
            enabled = !uiState.isUpdating,
            onClick = {
                if (form.submit(emailError, passwordError)) {
                    viewModel.requestEmailChange(newEmail, currentPassword)
                }
            }
        )
    }
}

@Composable
private fun EmailOtpDialog(uiState: ProfileUiState, viewModel: ProfileViewModel) {

    var code by remember { mutableStateOf("") }

    ProfileDialogFrame(
        title = "Enter Verification Code",
        subtitle = "We sent a 6-digit code to ${uiState.pendingNewEmail}.",
        errorMessage = uiState.updateErrorMessage,
        isBusy = uiState.isUpdating,
        onDismiss = { viewModel.closeDialog() }
    ) {
        OtpInputField(
            value = code,
            onValueChange = { code = it.filter(Char::isDigit).take(6) },
            enabled = !uiState.isUpdating,
            modifier = Modifier.fillMaxWidth()
        )

        // The button stays disabled until all 6 digits are in; this says why.
        FieldErrorText(
            if (code.isNotEmpty() && code.length < Validators.OTP_LENGTH) {
                "Enter all ${Validators.OTP_LENGTH} digits."
            } else {
                null
            }
        )

        uiState.otpInfoMessage?.let { info ->
            Spacer(Modifier.height(10.dp))
            AuthStatusBanner(message = info, isError = false)
        }

        Spacer(Modifier.height(20.dp))

        PrimaryButton(
            text = "Verify & Change Email",
            isLoading = uiState.isUpdating,
            enabled = !uiState.isUpdating && code.length == 6,
            onClick = { viewModel.confirmEmailChange(code) }
        )

        Spacer(Modifier.height(6.dp))

        TextButton(
            onClick = {
                code = ""
                viewModel.resendEmailChangeOtp()
            },
            enabled = !uiState.isUpdating,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Resend code", color = PrimaryPink, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EditPasswordDialog(uiState: ProfileUiState, viewModel: ProfileViewModel) {

    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    // Live validation (shared password rules).
    val form = rememberFormState()
    val currentError = Validators.loginPassword(currentPassword)
    val newError = Validators.newPassword(newPassword)
        ?: if (newPassword.isNotEmpty() && newPassword == currentPassword) {
            "New password must be different from your current one."
        } else {
            null
        }
    val confirmError = Validators.confirmPassword(newPassword, confirmPassword)

    ProfileDialogFrame(
        title = "Update Password",
        subtitle = "Enter your current password, then choose a new one (at least 6 characters).",
        errorMessage = uiState.updateErrorMessage,
        isBusy = uiState.isUpdating,
        onDismiss = { viewModel.closeDialog() }
    ) {
        AuthTextField(
            value = currentPassword,
            onValueChange = {
                currentPassword = it
                form.touch("current")
            },
            label = "Current password",
            leadingIcon = Icons.Default.Lock,
            isPassword = true,
            keyboardType = KeyboardType.Password,
            enabled = !uiState.isUpdating,
            error = form.shown("current", currentError),
            onFocusLost = { form.touch("current") }
        )
        Spacer(Modifier.height(12.dp))
        AuthTextField(
            value = newPassword,
            onValueChange = {
                newPassword = it
                form.touch("new")
            },
            label = "New password",
            leadingIcon = Icons.Default.Lock,
            isPassword = true,
            keyboardType = KeyboardType.Password,
            enabled = !uiState.isUpdating,
            error = form.shown("new", newError),
            onFocusLost = { form.touch("new") },
            supportingContent = { PasswordChecklist(newPassword) }
        )
        Spacer(Modifier.height(12.dp))
        AuthTextField(
            value = confirmPassword,
            onValueChange = {
                confirmPassword = it
                form.touch("confirm")
            },
            label = "Confirm new password",
            leadingIcon = Icons.Default.Lock,
            isPassword = true,
            keyboardType = KeyboardType.Password,
            enabled = !uiState.isUpdating,
            error = form.shown("confirm", confirmError),
            onFocusLost = { form.touch("confirm") }
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            text = "Update Password",
            isLoading = uiState.isUpdating,
            enabled = !uiState.isUpdating,
            onClick = {
                if (form.submit(currentError, newError, confirmError)) {
                    viewModel.changePassword(currentPassword, newPassword, confirmPassword)
                }
            }
        )
    }
}
