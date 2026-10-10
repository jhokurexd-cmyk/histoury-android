package com.histoury.app.ui.screens.support

import com.histoury.app.navigation.safePopBackStack
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.histoury.app.ui.components.CharacterCounter
import com.histoury.app.ui.components.FieldErrorText
import com.histoury.app.util.Validators
import com.histoury.app.util.rememberFormState
import com.histoury.app.data.viewmodel.SupportViewModel
import com.histoury.app.theme.Outline
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Primary
import com.histoury.app.theme.PrimarySoft
import com.histoury.app.theme.RatingStar
import com.histoury.app.theme.SurfaceSoft
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary
import com.histoury.app.ui.components.AuthStatusBanner
import com.histoury.app.ui.components.PrimaryButton

private data class QuickHelpItem(
    val title: String,
    val subtitle: String,
    val answer: String
)

// Kept in step with the app as it is now. When a feature changes, update
// the answer here too: an FAQ describing a button that no longer exists is
// worse than no FAQ.
private val quickHelpItems = listOf(
    QuickHelpItem(
        title = "How do I plan an itinerary?",
        subtitle = "Build a route around the sites you want",
        answer = "Tap the centre button in the bottom bar, then +. Pick your " +
            "historical sites, choose interests for nearby recommendations " +
            "(optional), and Histoury builds a walking route. Back takes you " +
            "one step at a time without losing your picks."
    ),
    QuickHelpItem(
        title = "Can I change the order of stops?",
        subtitle = "Reorder, add or remove stops",
        answer = "On the route screen, press and hold a stop (or drag its " +
            "handle) to move it, or tap its remove button to drop it. " +
            "Tap Save to keep your changes."
    ),
    QuickHelpItem(
        title = "How does navigation work?",
        subtitle = "Turn-by-turn directions to each stop",
        answer = "Tap \"Start Trip Now\" to get directions to your first stop. When " +
            "you arrive, Histoury opens that site automatically. If you leave " +
            "the app mid-trip the map keeps going in a small floating window."
    ),
    QuickHelpItem(
        title = "Why is a site locked?",
        subtitle = "Unlocking site information and AR",
        answer = "Full site stories and the AR experience unlock once you've " +
            "visited the site in person. After that, you can reread them any " +
            "time from Menu > Site Information."
    ),
    QuickHelpItem(
        title = "How do I use the AR experience?",
        subtitle = "See the site as it once was",
        answer = "At the site, tap \"Travel Back in Time (AR)\" and slowly " +
            "point your camera at the site until the model appears. Good " +
            "daylight and a clear view help it lock on."
    ),
    QuickHelpItem(
        title = "Can I use Histoury offline?",
        subtitle = "Downloads for weak signal",
        answer = "Go to Menu > Offline Downloads and download a site before " +
            "you go. Its AR model and narration are then saved on your phone. " +
            "Maps, directions and reviews still need a connection."
    ),
    QuickHelpItem(
        title = "Where are my photos?",
        subtitle = "Gallery and saving to your phone",
        answer = "Photos you take in AR appear in the Gallery tab. Tap " +
            "Download on a photo to save a copy to your phone's Pictures/" +
            "Histoury folder."
    ),
    QuickHelpItem(
        title = "How do I leave or rate reviews?",
        subtitle = "Reviews, helpful votes and reports",
        answer = "After visiting a site, open Visit History and tap \"Leave a " +
            "Review\". In Site Rankings you can mark reviews as helpful or " +
            "report ones that break the rules."
    ),
    QuickHelpItem(
        title = "Navigation or arrival not working?",
        subtitle = "Check location settings",
        answer = "Turn on GPS and set Histoury's location permission to " +
            "\"Allow all the time\" so arrivals are detected while your phone " +
            "is in your pocket. Allow notifications too."
    ),
    QuickHelpItem(
        title = "Can I use dark mode?",
        subtitle = "Change how Histoury looks",
        answer = "Yes. Go to Menu > Appearance and choose Light, Dark or " +
            "your phone's setting."
    )
)

@Composable
fun SupportScreen(
    navController: NavHostController,
    supportViewModel: SupportViewModel = viewModel()
) {

    val uiState by supportViewModel.uiState.collectAsState()

    val feedbackForm = rememberFormState()

    var expandedItem by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current

    LaunchedEffect(uiState.submitSucceeded) {
        if (uiState.submitSucceeded) {
            android.widget.Toast.makeText(
                context,
                "Thank you! Your feedback was submitted.",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            supportViewModel.resetSubmitSuccess()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceSoft)
            .statusBarsPadding()
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceSoft)
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = 16.dp)
        ) {

            Row(verticalAlignment = Alignment.CenterVertically) {

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .shadow(elevation = 2.dp, shape = CircleShape)
                        .clip(CircleShape)
                        .background(CardSurface)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
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

                Spacer(Modifier.width(14.dp))

                Column {

                    Text(
                        text = "Support & Feedback",
                        color = TextPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "We'd love to hear from you!",
                        color = TextSecondary,
                        fontSize = 12.5.sp
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 3.dp, shape = RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp))
                    .background(CardSurface)
                    .padding(16.dp)
            ) {

                Text(
                    text = "Quick Help",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(Modifier.height(12.dp))

                quickHelpItems.forEach { item ->

                    val isExpanded = expandedItem == item.title

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceSoft)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                expandedItem = if (isExpanded) null else item.title
                            }
                            .padding(14.dp)
                    ) {

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Column(modifier = Modifier.weight(1f)) {

                                Text(
                                    text = item.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = TextPrimary
                                )

                                Text(
                                    text = item.subtitle,
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }

                            val chevronRotation by animateFloatAsState(
                                targetValue = if (isExpanded) 180f else 0f,
                                animationSpec = tween(200),
                                label = "faqChevronRotation"
                            )

                            Icon(
                                imageVector = Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.graphicsLayer { rotationZ = chevronRotation }
                            )
                        }

                        AnimatedVisibility(visible = isExpanded) {
                            Column {

                                Spacer(Modifier.height(10.dp))

                                Text(
                                    text = item.answer,
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp
                                )
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 3.dp, shape = RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp))
                    .background(CardSurface)
                    .padding(16.dp)
            ) {

                Row(verticalAlignment = Alignment.CenterVertically) {

                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(18.dp)
                    )

                    Spacer(Modifier.width(8.dp))

                    Text(
                        text = "Send Feedback",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "How would you rate your experience?",
                    fontSize = 13.sp,
                    color = TextPrimary
                )

                Spacer(Modifier.height(8.dp))

                Row {

                    for (starIndex in 1..5) {

                        val isFilled = starIndex <= uiState.rating

                        val starColor by animateColorAsState(
                            targetValue = if (isFilled) RatingStar else Outline,
                            animationSpec = tween(150),
                            label = "starColor"
                        )

                        val starScale by animateFloatAsState(
                            targetValue = if (isFilled) 1f else 0.85f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "starScale"
                        )

                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Rate $starIndex",
                            tint = starColor,
                            modifier = Modifier
                                .size(32.dp)
                                .graphicsLayer {
                                    scaleX = starScale
                                    scaleY = starScale
                                }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    supportViewModel.onRatingChanged(starIndex)
                                }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "Tell us more (optional)",
                    fontSize = 13.sp,
                    color = TextPrimary
                )

                Spacer(Modifier.height(8.dp))

                val messageError = Validators.text(
                    uiState.message, "Your message", max = FEEDBACK_MAX, required = false
                )
                val ratingError = if (uiState.rating == 0) "Choose a star rating." else null

                OutlinedTextField(
                    value = uiState.message,
                    onValueChange = {
                        supportViewModel.onMessageChanged(it)
                        feedbackForm.touch("message")
                    },
                    isError = feedbackForm.shown("message", messageError) != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    placeholder = {
                        Text(
                            text = "Share your thoughts, suggestions, or report issues...",
                            fontSize = 13.sp
                        )
                    },
                    keyboardOptions = KeyboardOptions.Default,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceSoft,
                        unfocusedContainerColor = SurfaceSoft,
                        errorContainerColor = SurfaceSoft,
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = Primary
                    ),
                    shape = RoundedCornerShape(16.dp)
                )

                CharacterCounter(uiState.message.trim().length, FEEDBACK_MAX)
                FieldErrorText(feedbackForm.shown("message", messageError))
                // The rating is the one required part; say so under the form
                // once Submit is pressed without one.
                FieldErrorText(if (feedbackForm.submitted) ratingError else null)

                if (uiState.errorMessage != null) {

                    Spacer(Modifier.height(10.dp))

                    AuthStatusBanner(
                        message = uiState.errorMessage ?: "",
                        isError = true
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Mirrors the server's feedback limits (see SupportViewModel):
                // while a wait is in effect the button says how long, rather
                // than accepting a tap that would only be discarded.
                val cooldown = uiState.cooldownSecondsLeft
                PrimaryButton(
                    text = when {
                        cooldown <= 0 -> "Submit Feedback"
                        cooldown < 60 -> "Send again in ${cooldown}s"
                        else -> "Send again in ${(cooldown + 59) / 60} min"
                    },
                    enabled = cooldown <= 0,
                    isLoading = uiState.isSubmitting,
                    onClick = {
                        if (feedbackForm.submit(ratingError, messageError)) {
                            supportViewModel.submitFeedback()
                        }
                    }
                )
            }

            // ----- Contact Us -----

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 3.dp, shape = RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp))
                    .background(CardSurface)
                    .padding(16.dp)
            ) {

                Text(
                    text = "Contact Us",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(Modifier.height(12.dp))

                ContactRow(
                    icon = Icons.Default.Email,
                    label = "Email",
                    value = SUPPORT_EMAIL,
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:$SUPPORT_EMAIL")
                                    putExtra(Intent.EXTRA_SUBJECT, "Histoury Support")
                                }
                            )
                        } catch (e: Exception) {
                            // No email app installed — row is still readable.
                        }
                    }
                )

                Spacer(Modifier.height(10.dp))

                ContactRow(
                    icon = Icons.Default.Phone,
                    label = "Phone",
                    value = SUPPORT_PHONE,
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:$SUPPORT_PHONE")
                                }
                            )
                        } catch (e: Exception) {
                            // No dialer (e.g. some tablets) — row is still readable.
                        }
                    }
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// Shown on the Contact Us card and referenced by the Privacy Policy's
// "contact via the Support & Feedback section". Swap in real values here.
private const val SUPPORT_EMAIL = "support@histoury.com"
private const val SUPPORT_PHONE = "+63 912 345 678"

@Composable
private fun ContactRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit
) {

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceSoft)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(12.dp)
    ) {

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(PrimarySoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column {

            Text(
                text = label,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = TextPrimary
            )

            Text(
                text = value,
                fontSize = 13.sp,
                color = TextSecondary
            )
        }
    }
}

/** Longest feedback message accepted (Firestore-friendly; generous for real feedback). */
private const val FEEDBACK_MAX = 1000
