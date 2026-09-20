package com.histoury.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.histoury.app.data.repository.AppealRepository
import com.histoury.app.data.repository.SuspensionInfoHolder
import com.histoury.app.navigation.Routes
import com.histoury.app.theme.Success
import com.histoury.app.theme.SuccessSurface
import com.histoury.app.theme.Outline
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Danger
import com.histoury.app.theme.DangerSurface
import com.histoury.app.theme.Primary
import com.histoury.app.theme.SurfaceSoft
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary
import com.histoury.app.ui.components.AuthStatusBanner
import com.histoury.app.ui.components.PrimaryButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val REASON_LABELS = mapOf(
    "profanity" to "Profanity",
    "spam" to "Spam",
    "harassment" to "Harassment",
    "inappropriate_content" to "Inappropriate Content",
    "other" to "Other"
)

/**
 * Formats the time left until [expiresAtMillis] as e.g. "3 days, 4 hours",
 * "42 minutes", or "Indefinite" when there's no expiry.
 */
private fun formatRemaining(expiresAtMillis: Long?, nowMillis: Long): String {

    if (expiresAtMillis == null) return "Indefinite"

    val diffMs = expiresAtMillis - nowMillis
    if (diffMs <= 0) return "Ending shortly"

    val totalMinutes = diffMs / 60000
    val days = totalMinutes / (60 * 24)
    val hours = (totalMinutes % (60 * 24)) / 60
    val minutes = totalMinutes % 60

    return when {
        days > 0 -> "$days day${if (days == 1L) "" else "s"}, $hours hour${if (hours == 1L) "" else "s"}"
        hours > 0 -> "$hours hour${if (hours == 1L) "" else "s"}, $minutes minute${if (minutes == 1L) "" else "s"}"
        else -> "$minutes minute${if (minutes == 1L) "" else "s"}"
    }
}

@Composable
fun SuspendedScreen(
    navController: NavController
) {

    // Read once: SuspensionInfoHolder is only meant to survive the single
    // navigation call that led here (see its own doc comment).
    val info = remember { SuspensionInfoHolder.current }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Keeps the "time remaining" text accurate without the person needing
    // to reopen the screen. A minute granularity is plenty here.
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = System.currentTimeMillis()
        }
    }

    var showAppealForm by remember { mutableStateOf(false) }
    var appealMessage by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var appealSubmitted by remember { mutableStateOf(false) }
    var appealError by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val appealRepository = remember { AppealRepository() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceSoft)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp)
    ) {

        Spacer(Modifier.height(56.dp))

        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(72.dp)
                .background(Danger.copy(alpha = 0.16f), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Block,
                contentDescription = null,
                tint = Danger,
                modifier = Modifier.size(34.dp)
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            "Account Suspended",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth()
        )

        Spacer(Modifier.height(6.dp))

        Text(
            "Your account has been suspended for violating our community guidelines.",
            textAlign = TextAlign.Center,
            color = TextSecondary,
            fontSize = 13.5.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth(0.85f)
        )

        Spacer(Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    DangerSurface,
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(18.dp)
        ) {

            Text(
                "Reason",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = Danger
            )

            Text(
                REASON_LABELS[info?.reason] ?: "Violation",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            if (!info?.note.isNullOrBlank()) {

                Spacer(Modifier.height(12.dp))

                Text(
                    "Note from admin",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Danger
                )

                Text(
                    info?.note ?: "",
                    fontSize = 13.5.sp,
                    color = TextPrimary,
                    lineHeight = 19.sp
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "Time remaining",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = Danger
            )

            Text(
                formatRemaining(info?.expiresAtMillis, now),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        Spacer(Modifier.height(24.dp))

        if (appealSubmitted) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        SuccessSurface,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Success,
                    modifier = Modifier.size(26.dp)
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    "Appeal submitted",
                    fontWeight = FontWeight.Bold,
                    color = Success,
                    fontSize = 14.sp
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    "Our team will review it. You'll be able to sign in normally once your account is reactivated.",
                    textAlign = TextAlign.Center,
                    color = Success,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp
                )
            }

        } else if (showAppealForm) {

            Column(modifier = Modifier.fillMaxWidth()) {

                Text(
                    "Think this is a mistake? Explain why below.",
                    fontSize = 13.sp,
                    color = TextSecondary
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = appealMessage,
                    onValueChange = {
                        appealMessage = it
                        appealError = null
                    },
                    placeholder = { Text("Tell us why you think this suspension should be reviewed...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp),
                    enabled = !isSubmitting,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    maxLines = 6,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardSurface,
                        unfocusedContainerColor = CardSurface,
                        disabledContainerColor = CardSurface,
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = Outline,
                        cursorColor = Primary
                    )
                )

                if (appealError != null) {

                    Spacer(Modifier.height(10.dp))

                    AuthStatusBanner(
                        message = appealError ?: "",
                        isError = true
                    )
                }

                Spacer(Modifier.height(14.dp))

                PrimaryButton(
                    text = "Submit Appeal",
                    isLoading = isSubmitting,
                    onClick = {

                        val uid = info?.uid

                        if (appealMessage.trim().isBlank()) {
                            appealError = "Please enter a message before submitting."
                            return@PrimaryButton
                        }

                        if (uid == null) {
                            appealError = "Couldn't identify your account. Please try logging in again first."
                            return@PrimaryButton
                        }

                        isSubmitting = true
                        appealError = null

                        coroutineScope.launch {

                            val result = appealRepository.submitAppeal(uid, appealMessage)

                            isSubmitting = false

                            result.fold(
                                onSuccess = { appealSubmitted = true },
                                onFailure = { error ->
                                    appealError = error.message ?: "Couldn't submit your appeal."
                                }
                            )
                        }
                    }
                )
            }

        } else {

            Text(
                "Think this is a mistake? Tap here to appeal.",
                textAlign = TextAlign.Center,
                color = Primary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable { showAppealForm = true }
            )
        }

        Spacer(Modifier.height(24.dp))

        PrimaryButton(
            text = "Back to Login",
            onClick = {

                SuspensionInfoHolder.clear()

                navController.navigate(Routes.Login.route) {
                    popUpTo(Routes.Suspended.route) {
                        inclusive = true
                    }
                }
            }
        )

        Spacer(Modifier.height(32.dp))
    }
}
