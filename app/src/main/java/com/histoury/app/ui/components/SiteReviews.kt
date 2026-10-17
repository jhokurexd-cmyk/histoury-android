package com.histoury.app.ui.components

import com.histoury.app.theme.SheetSurface
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.google.firebase.Timestamp
import com.histoury.app.util.Validators
import com.histoury.app.data.model.HistoricalSite
import com.histoury.app.data.model.Review
import com.histoury.app.data.repository.ReviewReportReason
import com.histoury.app.data.viewmodel.RankingSortMode
import com.histoury.app.data.viewmodel.RankingsViewModel
import com.histoury.app.navigation.Routes
import com.histoury.app.theme.AccentGold
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Danger
import com.histoury.app.theme.Outline
import com.histoury.app.theme.Primary
import com.histoury.app.theme.PrimaryDark
import com.histoury.app.theme.PrimarySoft
import com.histoury.app.theme.RatingStar
import com.histoury.app.theme.SurfaceSoft
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary
import com.histoury.app.theme.TextTertiary
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.mutableIntStateOf
import com.histoury.app.data.reviews.SiteReviewsState
import com.histoury.app.data.reviews.ReviewSort
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.histoury.app.data.reviews.SiteReviewsController
import com.histoury.app.theme.OnColor

// =============================================================================
// Site reviews — the shared UI.
//
// Reviews of historical sites are shown in Site Rankings > [site] > Reviews
// and collected there, from Visit History and from the post-AR prompt —
// never on the Site Information page, which stays about the history itself.
// One review model, one Helpful implementation, one Report implementation,
// backed by SiteReviewsController and ReviewRepository.
// =============================================================================

@Composable
fun ReviewSummaryCard(
    site: HistoricalSite,
    reviews: List<Review>,
    // Tapping a star row filters the list to that rating; tapping it again
    // shows all. Null = the bars are just a summary.
    selectedStar: Int? = null,
    onStarClick: ((Int) -> Unit)? = null
) {

    // Worked out from the loaded reviews when there are any — they are the
    // same published reviews the server averages, and this way a review the
    // visitor just saved is reflected at once instead of after the site
    // document catches up.
    val average = if (reviews.isNotEmpty()) reviews.map { it.rating }.average() else site.averageRating
    val count = if (reviews.isNotEmpty()) reviews.size else site.reviewCount

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(PrimarySoft)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        // Big average + stars + count
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            Text(
                text = "%.1f".format(average),
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            StarRow(rating = average, starSize = 13)

            Spacer(Modifier.height(2.dp))

            Text(
                text = "$count review${if (count == 1) "" else "s"}",
                fontSize = 11.sp,
                color = TextSecondary
            )
        }

        Spacer(Modifier.width(20.dp))

        // 5..1 star distribution bars, computed from the loaded reviews
        Column(modifier = Modifier.weight(1f)) {

            for (star in 5 downTo 1) {

                val starCount = reviews.count { review ->
                    review.rating.toInt() == star
                }

                val fraction = if (reviews.isEmpty()) {
                    0f
                } else {
                    starCount.toFloat() / reviews.size
                }

                val isSelected = selectedStar == star
                // With a filter on, the other rows fade back.
                val rowAlpha = if (selectedStar == null || isSelected) 1f else 0.4f

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) CardSurface else Color.Transparent)
                        .then(
                            if (onStarClick != null && starCount > 0) {
                                Modifier.clickable(
                                    onClickLabel = if (isSelected) "Show all reviews" else "Show only $star-star reviews"
                                ) { onStarClick(star) }
                            } else Modifier
                        )
                        .alpha(rowAlpha)
                        .padding(horizontal = 4.dp, vertical = if (onStarClick != null) 3.dp else 0.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Text(
                        text = star.toString(),
                        fontSize = 10.sp,
                        color = TextSecondary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.width(10.dp)
                    )

                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = RatingStar,
                        modifier = Modifier.size(10.dp)
                    )

                    Spacer(Modifier.width(6.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(CardSurface)
                    ) {
                        if (fraction > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction)
                                    .clip(RoundedCornerShape(50))
                                    .background(RatingStar)
                            )
                        }
                    }

                    Spacer(Modifier.width(6.dp))

                    Text(
                        text = starCount.toString(),
                        fontSize = 10.sp,
                        color = TextSecondary,
                        modifier = Modifier.width(16.dp)
                    )
                }

                if (star != 1) {
                    Spacer(Modifier.height(if (onStarClick != null) 1.dp else 4.dp))
                }
            }
        }
    }
}

@Composable
fun ReviewCard(
    review: Review,
    isOwnReview: Boolean,
    isHelpful: Boolean,
    isReported: Boolean,
    onToggleHelpful: () -> Unit,
    onReport: () -> Unit
) {

    // A moderator hid this review's comment; its stars still count.
    val commentHidden = review.status == "hidden"

    // Height comes from the content alone: header, text (any length), then
    // one compact action row. No fixed height, no trailing spacer.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceSoft)
            .padding(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 8.dp)
    ) {

        // Avatar + name/time on the left, stars pinned to the upper right
        // and lined up with the name rather than floating in the middle.
        Row(verticalAlignment = Alignment.Top) {

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(PrimarySoft),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = publicReviewerName(review.userName)
                        .firstOrNull()?.uppercase() ?: "?",
                    color = Primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {

                Row(verticalAlignment = Alignment.CenterVertically) {

                    Text(
                        text = publicReviewerName(review.userName),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (review.edited) {

                        Spacer(Modifier.width(6.dp))

                        Text(
                            text = "edited",
                            fontSize = 10.sp,
                            color = TextTertiary
                        )
                    }
                }

                Spacer(Modifier.height(1.dp))

                Text(
                    text = formatReviewDate(review.createdAt),
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }

            Spacer(Modifier.width(8.dp))

            StarRow(
                rating = review.rating,
                starSize = 12,
                modifier = Modifier.padding(top = 3.dp)
            )
        }

        if (commentHidden) {

            Spacer(Modifier.height(10.dp))

            Text(
                text = "This comment was hidden by the Histoury team for breaking the review " +
                    "guidelines. The star rating still counts.",
                fontSize = 12.sp,
                fontStyle = FontStyle.Italic,
                color = TextTertiary,
                lineHeight = 17.sp
            )
        } else if (review.reviewText.isNotBlank()) {

            Spacer(Modifier.height(10.dp))

            Text(
                text = review.reviewText,
                fontSize = 13.sp,
                color = TextSecondary,
                lineHeight = 19.sp
            )
        }

        Spacer(Modifier.height(6.dp))

        // Helpful · N on the left, Report on the right. On the visitor's own
        // review the server refuses both, so the count is still shown (read
        // only) and "Your review" takes Report's place.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            HelpfulButton(
                likes = review.likes,
                isHelpful = isHelpful,
                enabled = !isOwnReview && !commentHidden,
                onClick = onToggleHelpful
            )

            Spacer(Modifier.weight(1f))

            if (isOwnReview) {
                Text(
                    text = "Your review",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextTertiary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                )
            } else if (!commentHidden) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(
                            enabled = !isReported,
                            onClickLabel = "Report review",
                            role = Role.Button,
                            onClick = onReport
                        )
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = if (isReported) Icons.Filled.Flag else Icons.Outlined.Flag,
                        contentDescription = null,
                        tint = if (isReported) TextTertiary else TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (isReported) "Reported" else "Report",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isReported) TextTertiary else TextSecondary
                    )
                }
            }
        }
    }
}

/**
 * Small pill: outlined thumb and "Helpful" until the visitor taps it, then
 * filled in the brand colour. The count sits inside it so the action and
 * its effect read as one thing.
 */
@Composable
private fun HelpfulButton(
    likes: Int,
    isHelpful: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {

    val tint = when {
        isHelpful -> Primary
        enabled -> TextSecondary
        else -> TextTertiary
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (isHelpful) PrimarySoft else CardSurface)
            .clickable(
                enabled = enabled,
                onClickLabel = if (isHelpful) "Remove helpful" else "Mark as helpful",
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = if (isHelpful) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "Helpful \u00B7 $likes",
            fontSize = 11.5.sp,
            fontWeight = if (isHelpful) FontWeight.Bold else FontWeight.Medium,
            color = tint
        )
    }
}

@Composable
fun ReportReviewDialog(
    reviewId: String,
    isSubmitting: Boolean,
    errorMessage: String?,
    onSubmit: (ReviewReportReason, String) -> Unit,
    onDismiss: () -> Unit
) {

    // Keyed to the review so reopening for a different one starts fresh,
    // but a rotation mid-report keeps the choice.
    var reason by rememberSaveable(reviewId) { mutableStateOf<ReviewReportReason?>(null) }
    var detailsTouched by rememberSaveable(reviewId) { mutableStateOf(false) }
    var details by rememberSaveable(reviewId) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SheetSurface,
        title = {
            Text(
                text = "Report review",
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column {

                Text(
                    text = "Why are you reporting this review? Reports are private " +
                        "and are checked by the Histoury team.",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )

                Spacer(Modifier.height(8.dp))

                ReviewReportReason.values().forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .selectable(
                                selected = reason == option,
                                enabled = !isSubmitting,
                                role = Role.RadioButton,
                                onClick = { reason = option }
                            )
                            .padding(vertical = 2.dp)
                    ) {
                        RadioButton(
                            selected = reason == option,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = Primary)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = option.label, fontSize = 14.sp, color = TextPrimary)
                    }
                }

                // "Other" means nothing on its own, so the server requires
                // details for it; say so as soon as it's picked and typed in.
                val detailsError = if (reason == ReviewReportReason.OTHER && details.isBlank()) {
                    "Tell us what's wrong with this review."
                } else {
                    null
                }

                if (reason != null) {

                    Spacer(Modifier.height(6.dp))

                    OutlinedTextField(
                        value = details,
                        onValueChange = {
                            if (it.length <= Validators.REPORT_DETAILS_MAX) details = it
                            detailsTouched = true
                        },
                        isError = detailsError != null && detailsTouched,
                        enabled = !isSubmitting,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                text = if (reason == ReviewReportReason.OTHER) {
                                    "Tell us what's wrong"
                                } else {
                                    "Add details (optional)"
                                },
                                fontSize = 13.sp
                            )
                        },
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            cursorColor = Primary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    CharacterCounter(details.length, Validators.REPORT_DETAILS_MAX)
                    FieldErrorText(if (detailsTouched) detailsError else null)
                }

                if (errorMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(text = errorMessage, fontSize = 12.5.sp, color = Danger)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = reason != null && !isSubmitting,
                onClick = {
                    val picked = reason
                    // Final check: "Other" still needs details.
                    detailsTouched = true
                    if (picked != null && !(picked == ReviewReportReason.OTHER && details.isBlank())) {
                        onSubmit(picked, details)
                    }
                }
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        color = Primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Text(
                        text = "Submit",
                        color = if (reason != null) Danger else TextTertiary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !isSubmitting, onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@Composable
fun StarRow(rating: Double, starSize: Int, modifier: Modifier = Modifier) {

    // Rounded, not cut off: a 4.8 average shows 5 stars, not 4.
    val filledStars = kotlin.math.round(rating).toInt().coerceIn(0, 5)

    Row(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "Rated $filledStars out of 5"
        }
    ) {
        repeat(5) { index ->
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = if (index < filledStars) {
                    RatingStar
                } else {
                    Outline
                },
                modifier = Modifier.size(starSize.dp)
            )
        }
    }
}

/** "Just now" / "5m ago" / "3h ago" / "2d ago", then a full date. */
fun formatReviewDate(rawDate: Any?): String {

    val timestamp = rawDate as? Timestamp ?: return ""

    val elapsedMs = System.currentTimeMillis() - timestamp.toDate().time

    val minutes = elapsedMs / 60_000

    val hours = minutes / 60

    val days = hours / 24

    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days <= 7 -> "${days}d ago"
        else -> {
            val calendar = java.util.Calendar.getInstance()
            calendar.time = timestamp.toDate()
            val months = arrayOf(
                "Jan", "Feb", "Mar", "Apr", "May", "Jun",
                "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
            )
            "${months[calendar.get(java.util.Calendar.MONTH)]} " +
                "${calendar.get(java.util.Calendar.DAY_OF_MONTH)}, " +
                "${calendar.get(java.util.Calendar.YEAR)}"
        }
    }
}


/**
 * The report dialog and toast messages for a [SiteReviewsController].
 * Placed once per screen that shows reviews.
 */
@Composable
fun SiteReviewsDialogs(
    state: SiteReviewsState,
    controller: SiteReviewsController
) {

    state.reportTarget?.let { review ->
        ReportReviewDialog(
            reviewId = review.id,
            isSubmitting = state.isSubmittingReport,
            errorMessage = state.reportError,
            onSubmit = { reason, details -> controller.submitReport(reason, details) },
            onDismiss = { controller.dismissReport() }
        )
    }

    val context = LocalContext.current
    LaunchedEffect(state.message) {
        state.message?.let { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            controller.consumeMessage()
        }
    }
}

/**
 * "Most helpful · Most recent · Most favorable · Most critical" — the order
 * of the review list. Scrolls sideways on narrow screens.
 */
@Composable
fun ReviewSortChips(
    selected: ReviewSort,
    onSelect: (ReviewSort) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ReviewSort.values().forEach { sort ->
            val isSelected = sort == selected
            Text(
                text = sort.label,
                fontSize = 12.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) OnColor else TextSecondary,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) Primary else SurfaceSoft)
                    .selectable(
                        selected = isSelected,
                        role = Role.Tab,
                        onClick = { onSelect(sort) }
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

/**
 * All of a site's reviews, for a bottom sheet. Wraps when short, caps and
 * scrolls when long.
 */
@Composable
fun SiteReviewsList(
    state: SiteReviewsState,
    onToggleHelpful: (Review) -> Unit,
    onReport: (Review) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {}
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 560.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { header() }
        val shown = state.displayedReviews
        items(shown.size, key = { index -> shown[index].id.ifBlank { "review-$index" } }) { index ->
            val review = shown[index]
            ReviewCard(
                review = review,
                isOwnReview = state.currentUserId != null && review.userId == state.currentUserId,
                isHelpful = review.id in state.helpfulReviewIds,
                isReported = review.id in state.reportedReviewIds,
                onToggleHelpful = { onToggleHelpful(review) },
                onReport = { onReport(review) }
            )
        }
    }
}

/**
 * The rating form as a dialog: stars, an optional short review, submit or
 * "Not now". Used by the post-AR prompt. Writes a normal site review through
 * the same one-review-per-site path as every other entry point.
 */
@Composable
fun RateSiteDialog(
    title: String,
    subtitle: String?,
    initialRating: Int,
    initialText: String,
    isSubmitting: Boolean,
    errorMessage: String?,
    submitLabel: String,
    onSubmit: (rating: Int, text: String) -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Not now"
) {

    // Keyed on the initial values: the visitor's existing review is looked up
    // when the model is placed, and if that answer lands after the dialog has
    // opened, the form picks it up instead of staying blank.
    var rating by rememberSaveable(initialRating) { mutableIntStateOf(initialRating.coerceIn(0, 5)) }
    var text by rememberSaveable(initialText) { mutableStateOf(initialText) }

    // Profanity, links and contact details — checked as they type
    // (the server runs the same check on submit).
    val textError = remember(text) { com.histoury.app.util.ReviewTextFilter.check(text)?.message }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        containerColor = SheetSurface,
        title = {
            Text(text = title, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 18.sp, lineHeight = 23.sp)
        },
        text = {
            Column {

                if (subtitle != null) {
                    Text(text = subtitle, fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp)
                    Spacer(Modifier.height(12.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    for (star in 1..5) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .selectable(
                                    selected = star == rating,
                                    enabled = !isSubmitting,
                                    role = Role.RadioButton,
                                    onClick = { rating = star }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (star <= rating) Icons.Default.Star else Icons.Outlined.StarOutline,
                                contentDescription = "$star star${if (star == 1) "" else "s"}",
                                tint = if (star <= rating) RatingStar else Outline,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= Validators.REVIEW_MAX) text = it },
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Share a few words (optional)", fontSize = 13.sp) },
                    minLines = 2,
                    maxLines = 5,
                    isError = textError != null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        cursorColor = Primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                FieldErrorText(textError)

                CharacterCounter(text.length, Validators.REVIEW_MAX)

                if (errorMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(text = errorMessage, fontSize = 12.5.sp, color = Danger)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = rating > 0 && !isSubmitting && textError == null,
                onClick = { onSubmit(rating, text) }
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = Primary, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                } else {
                    Text(
                        text = submitLabel,
                        color = if (rating > 0) Primary else TextTertiary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !isSubmitting, onClick = onDismiss) {
                Text(dismissLabel, color = TextSecondary)
            }
        }
    )
}

/**
 * The name shown on a public review. Older reviews by people with no first
 * or last name were saved with their EMAIL ADDRESS as the name (the server
 * fell back to it); an address is never shown to other visitors.
 */
private fun publicReviewerName(raw: String): String {
    val name = raw.trim()
    return if (name.isBlank() || name.contains('@')) "Tourist User" else name
}
