package com.histoury.app.ui.screens.support

import com.histoury.app.theme.softShadow
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
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import com.histoury.app.navigation.Routes
import com.histoury.app.navigation.navigateSafely
import com.histoury.app.ui.screens.legal.LegalContact
import com.histoury.app.ui.screens.legal.LegalContent
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

private data class QuickHelpGroup(
    val title: String,
    val items: List<QuickHelpItem>
)

// Kept in step with the app as it is now. When a feature changes, update
// the answer here too: an FAQ describing a button that no longer exists is
// worse than no FAQ. Privacy answers must agree with LegalContent.
private val quickHelpGroups = listOf(
    QuickHelpGroup(
        title = "Trips & navigation",
        items = listOf(
            QuickHelpItem(
                title = "How do I plan an itinerary?",
                subtitle = "Build a route around the sites you want",
                answer = "Tap the centre button in the bottom bar, then +. Pick your " +
                    "historical sites (search or sort them by Nearest or A\u2013Z), " +
                    "choose interests if you like, then add places: suggestions near " +
                    "your sites come first, and every other place is listed below them. " +
                    "Tap Generate Optimized Route and Histoury puts your stops in order."
            ),
            QuickHelpItem(
                title = "Can I change my trip?",
                subtitle = "Reorder, add or remove stops, change travel mode",
                answer = "On the route screen, press and hold a stop (or drag its handle) " +
                    "to move it, or tap its remove button. Add a Stop lets you search " +
                    "and filter sites and places by type. Switch between Walk, Drive, " +
                    "Ride and Commute at the top; for Drive you can avoid expressways " +
                    "and tolls. Save the itinerary to keep your edits."
            ),
            QuickHelpItem(
                title = "How do I get to Intramuros from where I am?",
                subtitle = "The \"Getting there\" card",
                answer = "If you're not near your first stop yet, the route screen shows " +
                    "Getting there: the way from your current location to stop 1 in " +
                    "your travel mode. In Commute, tap it for the step-by-step rides."
            ),
            QuickHelpItem(
                title = "How does Commute work?",
                subtitle = "Trains, buses and jeepneys",
                answer = "Commute uses train, bus and jeepney routes mapped in " +
                    "OpenStreetMap. Tap the commute line on a stop (or Steps while " +
                    "navigating) to see each walk and ride: which line, which way, " +
                    "where to get on and off. Times are estimates, not schedules, and " +
                    "fares aren't shown. When walking is quicker, Histoury says so. " +
                    "Not every route is mapped yet."
            ),
            QuickHelpItem(
                title = "How does navigation work?",
                subtitle = "Turn-by-turn directions to each stop",
                answer = "Tap Start Trip Now for directions to your first stop. The arrow " +
                    "follows your phone's compass when walking and your direction of " +
                    "travel in a vehicle. Tap the compass button to keep north at the " +
                    "top. If you leave the app, navigation continues in a small " +
                    "floating window. Please don't use the app while driving."
            ),
            QuickHelpItem(
                title = "Why didn't the arrival screen open?",
                subtitle = "Location and arrival detection",
                answer = "Turn on location, and set Histoury's location permission to " +
                    "\"Allow all the time\" so arrivals are noticed with your phone in " +
                    "your pocket. With the app open, an arrival is detected within " +
                    "seconds of being inside a site's area. Each site counts once every " +
                    "30 minutes. If you're busy in AR, a review or your itinerary, you " +
                    "get a notification instead of being taken away."
            )
        )
    ),
    QuickHelpGroup(
        title = "Sites, places & AR",
        items = listOf(
            QuickHelpItem(
                title = "How do I find a site or place?",
                subtitle = "Search, categories and filters",
                answer = "Use the search bar on Home: it matches the start of words, so " +
                    "\"fo\" finds Fort Santiago. Choose a category such as Food & " +
                    "Drink or Parks, and open View all for search, category filters " +
                    "and sorting by Nearest, A\u2013Z or Top rated."
            ),
            QuickHelpItem(
                title = "Why is a site locked?",
                subtitle = "Unlocking site information",
                answer = "Full site stories unlock once you've visited the site in " +
                    "person. After that you can reread them any time from Menu > " +
                    "Site Information."
            ),
            QuickHelpItem(
                title = "How do I use the AR experience?",
                subtitle = "See the site as it once was",
                answer = "At the site, tap \"Travel Back in Time (AR)\" on the site's " +
                    "page \u2014 it also appears when you open the site from Site " +
                    "Information while you're there. Slowly point your camera at the " +
                    "site until the model appears. Good daylight and a clear view help."
            ),
            QuickHelpItem(
                title = "Can the narration keep playing?",
                subtitle = "Listening with the screen off",
                answer = "Yes. Narration keeps playing if you go Home or lock your phone, " +
                    "with Pause and Stop in the notification. It pauses for calls and " +
                    "when headphones are unplugged. In AR, closing the info panel stops it."
            ),
            QuickHelpItem(
                title = "Can I book a hotel in Histoury?",
                subtitle = "\"Book or check rates\"",
                answer = "Histoury doesn't take bookings or payments. \"Book or check " +
                    "rates\" opens the hotel's own booking page; any booking is between " +
                    "you and the hotel. You can also call or message them from the " +
                    "hotel's page."
            ),
            QuickHelpItem(
                title = "Can I use Histoury offline?",
                subtitle = "Downloads for weak signal",
                answer = "Go to Menu > Offline Downloads and download a site before you " +
                    "go. Its AR model and narration are then saved on your phone. Maps, " +
                    "directions and reviews still need a connection."
            )
        )
    ),
    QuickHelpGroup(
        title = "Reviews & photos",
        items = listOf(
            QuickHelpItem(
                title = "How do I leave or read reviews?",
                subtitle = "Reviews, filters, helpful votes and reports",
                answer = "After visiting a site, open Visit History and tap Leave a " +
                    "review. In Site Rankings, open Reviews to sort them, tap a star " +
                    "row (for example 1 \u2605) to see only those, mark reviews " +
                    "helpful, or report ones that break the rules."
            ),
            QuickHelpItem(
                title = "Why was my review blocked or removed?",
                subtitle = "Review rules",
                answer = "Reviews are checked automatically for profanity, links and " +
                    "contact details. Reviews that other visitors report are checked " +
                    "by moderators and removed if they break the Terms."
            ),
            QuickHelpItem(
                title = "Where are my photos?",
                subtitle = "Gallery and saving to your phone",
                answer = "AR photos appear in the Gallery tab. They're kept only on this " +
                    "phone, in Histoury, separately for each account. Tap Save to " +
                    "device on a photo to copy it to your phone's Pictures/Histoury " +
                    "folder \u2014 photos you don't save are lost if you uninstall the app."
            )
        )
    ),
    QuickHelpGroup(
        title = "Account & privacy",
        items = listOf(
            QuickHelpItem(
                title = "How do I change my details?",
                subtitle = "Name, email, password and photo",
                answer = "Go to Menu > View Profile to edit your name and photo, change " +
                    "your email, or update your password."
            ),
            QuickHelpItem(
                title = "How do I delete my account or get my data?",
                subtitle = "Your rights under the Data Privacy Act",
                answer = "Tap \"Privacy & data requests\" below to email our Data " +
                    "Protection Officer. You can ask for a copy of your data, a " +
                    "correction, or to delete your account. Deleting removes your " +
                    "sign-in, profile, reviews, visit history, itineraries and favorites. " +
                    "We may ask you to confirm it's you."
            ),
            QuickHelpItem(
                title = "My account was suspended. What can I do?",
                subtitle = "Suspensions and appeals",
                answer = "When you sign in, Histoury shows the reason for the suspension " +
                    "and lets you send an appeal, which an administrator reviews."
            ),
            QuickHelpItem(
                title = "Why does Histoury ask for permissions?",
                subtitle = "Location, camera and notifications",
                answer = "Location shows where you are, gives directions and detects " +
                    "arrivals (\"all the time\" only for arrivals with the app closed). " +
                    "Camera is used only for AR. Notifications are for arrivals, " +
                    "navigation and narration. You can turn any of them off in your " +
                    "phone's settings; only the features that need them stop working."
            ),
            QuickHelpItem(
                title = "Can I use dark mode?",
                subtitle = "Change how Histoury looks",
                answer = "Yes. Go to Menu > Appearance and choose Light, Dark or your " +
                    "phone's setting. Menu > Theme changes the style of cards and " +
                    "buttons: Default, Neumorphism or Glassmorphism. Each works in light and dark."
            )
        )
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
                        .softShadow(elevation = 2.dp, shape = CircleShape)
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
                        text = "Support Center",
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
                    .softShadow(elevation = 3.dp, shape = RoundedCornerShape(22.dp))
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

                quickHelpGroups.forEach { group ->

                Spacer(Modifier.height(12.dp))

                Text(
                    text = group.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                )

                group.items.forEach { item ->

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
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .softShadow(elevation = 3.dp, shape = RoundedCornerShape(22.dp))
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
                    .softShadow(elevation = 3.dp, shape = RoundedCornerShape(22.dp))
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
                    value = LegalContact.SUPPORT_EMAIL,
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:${LegalContact.SUPPORT_EMAIL}")
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
                    value = LegalContact.SUPPORT_PHONE,
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:${LegalContact.SUPPORT_PHONE}")
                                }
                            )
                        } catch (e: Exception) {
                            // No dialer (e.g. some tablets) — row is still readable.
                        }
                    }
                )
            }

            // ----- Privacy & legal -----
            // Where a visitor exercises their Data Privacy Act rights (access,
            // correction, erasure, portability) and finds the documents.

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .softShadow(elevation = 3.dp, shape = RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp))
                    .background(CardSurface)
                    .padding(16.dp)
            ) {

                Text(
                    text = "Privacy & Legal",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(Modifier.height(12.dp))

                ContactRow(
                    icon = Icons.Default.Shield,
                    label = "Privacy & data requests",
                    value = "Get a copy, correct or delete your data",
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:${LegalContact.DPO_EMAIL}")
                                    putExtra(Intent.EXTRA_SUBJECT, LegalContact.PRIVACY_REQUEST_SUBJECT)
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Request (access / correction / deletion / copy of my data):\n\n" +
                                            "Account email:\n\nDetails:\n"
                                    )
                                }
                            )
                        } catch (e: Exception) {
                            // No email app: the address is in the Privacy Policy.
                        }
                    }
                )

                Spacer(Modifier.height(10.dp))

                ContactRow(
                    icon = Icons.Default.Shield,
                    label = "Privacy Policy",
                    value = "How your information is handled",
                    onClick = {
                        navController.navigateSafely(Routes.Legal.createRoute(LegalContent.TYPE_PRIVACY))
                    }
                )

                Spacer(Modifier.height(10.dp))

                ContactRow(
                    icon = Icons.Default.Description,
                    label = "Terms & Conditions",
                    value = "Rules for using Histoury",
                    onClick = {
                        navController.navigateSafely(Routes.Legal.createRoute(LegalContent.TYPE_TERMS))
                    }
                )

                Spacer(Modifier.height(10.dp))

                ContactRow(
                    icon = Icons.Default.Info,
                    label = "Legal Notices & Credits",
                    value = "Map data, licences and attributions",
                    onClick = {
                        navController.navigateSafely(Routes.Legal.createRoute(LegalContent.TYPE_NOTICES))
                    }
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// Contact details live in LegalContact (shared with the legal documents).

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
