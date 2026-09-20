@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.histoury.app.ui.screens.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.histoury.app.data.itinerary.ItineraryPlanner
import com.histoury.app.data.model.HistoricalSite
import com.histoury.app.data.model.Place
import com.histoury.app.data.viewmodel.ItineraryViewModel
import com.histoury.app.data.viewmodel.StopPreviewMode
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Primary
import com.histoury.app.theme.PrimarySoft
import com.histoury.app.theme.RatingStar
import com.histoury.app.theme.SurfaceSoft
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary
import com.histoury.app.theme.TextTertiary
import com.histoury.app.ui.components.PrimaryButton

/**
 * "What is this place, actually?" — the preview layer of the itinerary
 * builder.
 *
 * Picking stops used to be a guessing game: the wizard showed a thumbnail,
 * a name and a one-line subtitle, so a visitor deciding between two cafés
 * or three sites had nothing to decide *with*. This sheet fills that gap
 * without leaving the builder — tap the info button on any card and the
 * listing opens over the wizard, keeping the selection you were in the
 * middle of making.
 *
 * It deliberately stays a *preview*, not a second Site Details screen.
 * Historical sites keep their arrival gate: the short teaser, rating and
 * practical facts are shown (all of which Home already shows publicly),
 * while the timeline, stories and sources still unlock only by visiting in
 * person. Places have no gate — they're business listings — so their sheet
 * mirrors PlaceDetailsScreen.
 */

// ---------------------------------------------------------------------------
// Normalized preview model
// ---------------------------------------------------------------------------

internal data class PreviewFact(
    val icon: ImageVector,
    val value: String
)

internal data class PreviewContact(
    val icon: ImageVector,
    val value: String
)

/**
 * Sites and places have different shapes but the same job here, so both
 * collapse into this before rendering.
 */
internal data class StopPreviewData(
    val refId: String,
    val refType: String,
    val title: String,
    val badgeLabel: String,
    val badgeEmoji: String,
    val locationLine: String,
    val heroImage: String,
    val galleryImages: List<String> = emptyList(),
    val description: String = "",
    val facts: List<PreviewFact> = emptyList(),
    val contacts: List<PreviewContact> = emptyList(),
    val averageRating: Double = 0.0,
    val reviewCount: Int = 0,
    val visitCount: Int = 0,
    val isHistoricalSite: Boolean = false,
    val isUnlocked: Boolean = false,
    val hasCoordinates: Boolean = true
)

internal fun HistoricalSite.toStopPreview(isUnlocked: Boolean): StopPreviewData {

    val facts = buildList {

        if (entranceFee.isNotBlank()) {
            add(PreviewFact(Icons.Default.ConfirmationNumber, entranceFee))
        }

        add(
            PreviewFact(
                Icons.Default.AccessTime,
                "~${ItineraryPlanner.defaultVisitMinutes("site")} min here"
            )
        )

        if (arEnabled) {
            add(PreviewFact(Icons.Default.CenterFocusStrong, "AR available"))
        }
    }

    return StopPreviewData(
        refId = documentId,
        refType = "site",
        title = siteName,
        badgeLabel = "Historical Site",
        badgeEmoji = "\uD83C\uDFDB\uFE0F", // 🏛️
        locationLine = location,
        heroImage = featuredImage,
        description = description,
        facts = facts,
        averageRating = averageRating,
        reviewCount = reviewCount,
        visitCount = visitCount,
        isHistoricalSite = true,
        isUnlocked = isUnlocked,
        hasCoordinates = latitude != null && longitude != null
    )
}

internal fun Place.toStopPreview(): StopPreviewData {

    val facts = buildList {

        if (openingHours.isNotBlank()) {
            add(PreviewFact(Icons.Default.Schedule, openingHours))
        }

        if (entranceFee.isNotBlank()) {
            add(PreviewFact(Icons.Default.ConfirmationNumber, entranceFee))
        }

        add(
            PreviewFact(
                Icons.Default.AccessTime,
                "~${ItineraryPlanner.defaultVisitMinutes("place")} min here"
            )
        )
    }

    val contacts = buildList {
        if (contactPhone.isNotBlank()) add(PreviewContact(Icons.Default.Phone, contactPhone))
        if (contactWebsite.isNotBlank()) add(PreviewContact(Icons.Default.Language, contactWebsite))
        if (contactEmail.isNotBlank()) add(PreviewContact(Icons.Default.Email, contactEmail))
    }

    return StopPreviewData(
        refId = documentId,
        refType = "place",
        title = name,
        badgeLabel = categoryLabel,
        badgeEmoji = categoryEmoji,
        locationLine = address.ifBlank { location },
        heroImage = featuredImage,
        galleryImages = galleryImages,
        description = fullDescription.ifBlank { description },
        facts = facts,
        contacts = contacts,
        isHistoricalSite = false,
        isUnlocked = true,
        hasCoordinates = latitude != null && longitude != null
    )
}

// ---------------------------------------------------------------------------
// Host — one call site per builder step
// ---------------------------------------------------------------------------

/**
 * Drops the preview sheet into whichever step is on screen. Resolving the
 * target from the already-loaded catalog means opening a preview costs no
 * extra Firestore read, so it stays instant even on a slow connection.
 */
@Composable
internal fun ItineraryStopPreviewHost(viewModel: ItineraryViewModel) {

    val uiState by viewModel.uiState.collectAsState()

    val target = uiState.previewTarget ?: return

    val preview = when (target.refType) {

        "site" -> uiState.allSites
            .firstOrNull { it.documentId == target.refId }
            ?.let { site ->
                site.toStopPreview(isUnlocked = site.siteId in uiState.visitedSiteSlugs)
            }

        else -> uiState.allPlaces
            .firstOrNull { it.documentId == target.refId }
            ?.toStopPreview()
    }

    if (preview == null) {

        // Two ways to get here: the catalog is still loading (opening a
        // saved itinerary starts both at once), or the underlying document
        // was unpublished since the stop was added.
        if (uiState.isLoadingCatalog) {
            PreviewLoadingSheet(onDismiss = { viewModel.closeStopPreview() })
        } else {
            LaunchedEffect(target) { viewModel.closeStopPreview() }
        }

        return
    }

    val isAlreadyIn = when (target.mode) {
        StopPreviewMode.SELECTION -> preview.refId in uiState.selectedSiteIds
        StopPreviewMode.RECOMMENDATION -> preview.refId !in uiState.dismissedRecommendationIds
        else -> uiState.generatedStops.any { it.refId == preview.refId }
    }

    val actionLabel = when (target.mode) {
        StopPreviewMode.SELECTION ->
            if (isAlreadyIn) "Remove from Trip" else "Add to Trip"
        StopPreviewMode.RECOMMENDATION ->
            if (isAlreadyIn) "Leave Out of Trip" else "Include in Trip"
        StopPreviewMode.ADD_TO_ROUTE -> "Add to Trip"
        StopPreviewMode.VIEW_ONLY -> null
    }

    // addStopFromSite/addStopFromPlace both bail out silently on a stop with
    // no coordinates, which from the visitor's side looks like a dead button.
    // Say why instead.
    val blockedReason = if (
        target.mode == StopPreviewMode.ADD_TO_ROUTE && !preview.hasCoordinates
    ) {
        "This spot doesn't have a map location yet, so it can't be added to a route."
    } else {
        null
    }

    StopPreviewSheet(
        preview = preview,
        actionLabel = actionLabel,
        isActionRemoval = isAlreadyIn && target.mode != StopPreviewMode.ADD_TO_ROUTE,
        blockedReason = blockedReason,
        onAction = { viewModel.confirmStopPreviewAction() },
        onDismiss = { viewModel.closeStopPreview() }
    )
}

@Composable
private fun PreviewLoadingSheet(onDismiss: () -> Unit) {

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardSurface
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Primary)
        }
    }
}

// ---------------------------------------------------------------------------
// Sheet
// ---------------------------------------------------------------------------

@Composable
internal fun StopPreviewSheet(
    preview: StopPreviewData,
    actionLabel: String?,
    isActionRemoval: Boolean,
    blockedReason: String?,
    onAction: () -> Unit,
    onDismiss: () -> Unit
) {

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardSurface
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
        ) {

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {

                PreviewHero(preview)

                Column(modifier = Modifier.padding(horizontal = 20.dp)) {

                    Spacer(Modifier.height(14.dp))

                    Text(
                        text = preview.title,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    if (preview.locationLine.isNotBlank()) {

                        Spacer(Modifier.height(5.dp))

                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier
                                    .padding(top = 1.dp)
                                    .size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = preview.locationLine,
                                fontSize = 12.5.sp,
                                color = TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (preview.isHistoricalSite && preview.reviewCount > 0) {

                        Spacer(Modifier.height(8.dp))

                        RatingRow(
                            rating = preview.averageRating,
                            reviewCount = preview.reviewCount,
                            visitCount = preview.visitCount
                        )
                    }

                    if (preview.facts.isNotEmpty()) {

                        Spacer(Modifier.height(12.dp))

                        FactPills(preview.facts)
                    }

                    if (preview.description.isNotBlank()) {

                        Spacer(Modifier.height(16.dp))

                        PreviewSectionLabel("About")

                        Spacer(Modifier.height(6.dp))

                        Text(
                            text = preview.description,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = TextSecondary
                        )
                    }

                    if (preview.galleryImages.isNotEmpty()) {

                        Spacer(Modifier.height(16.dp))

                        PreviewSectionLabel("Photos")

                        Spacer(Modifier.height(8.dp))
                    }
                }

                if (preview.galleryImages.isNotEmpty()) {

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(preview.galleryImages) { imageUrl ->
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = preview.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(width = 130.dp, height = 92.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }
                    }
                }

                Column(modifier = Modifier.padding(horizontal = 20.dp)) {

                    if (preview.contacts.isNotEmpty()) {

                        Spacer(Modifier.height(16.dp))

                        PreviewSectionLabel("Good to Know")

                        Spacer(Modifier.height(8.dp))

                        preview.contacts.forEach { contact ->

                            Row(
                                modifier = Modifier.padding(bottom = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = contact.icon,
                                    contentDescription = null,
                                    tint = Primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = contact.value,
                                    fontSize = 12.5.sp,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (preview.isHistoricalSite) {

                        Spacer(Modifier.height(16.dp))

                        UnlockNotice(isUnlocked = preview.isUnlocked)
                    }

                    Spacer(Modifier.height(16.dp))
                }
            }

            // Footer stays pinned so the decision the sheet was opened to
            // help with is always one tap away, however long the listing is.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSurface)
                    .padding(horizontal = 20.dp)
                    .padding(top = 10.dp, bottom = 24.dp)
            ) {

                if (blockedReason != null) {

                    Text(
                        text = blockedReason,
                        fontSize = 11.5.sp,
                        color = TextTertiary
                    )

                    Spacer(Modifier.height(8.dp))
                }

                if (actionLabel != null && blockedReason == null) {

                    if (isActionRemoval) {
                        SecondaryActionButton(text = actionLabel, onClick = onAction)
                    } else {
                        PrimaryButton(text = actionLabel, onClick = onAction)
                    }

                    Spacer(Modifier.height(8.dp))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss
                        )
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Close",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Pieces
// ---------------------------------------------------------------------------

@Composable
private fun PreviewHero(preview: StopPreviewData) {

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(170.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceSoft)
    ) {

        if (preview.heroImage.isNotBlank()) {

            AsyncImage(
                model = preview.heroImage,
                contentDescription = preview.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.28f)),
                            startY = 220f
                        )
                    )
            )

        } else {

            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = preview.badgeEmoji, fontSize = 40.sp)
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
            shape = RoundedCornerShape(50),
            color = CardSurface
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = preview.badgeEmoji, fontSize = 12.sp)
                Spacer(Modifier.width(5.dp))
                Text(
                    text = preview.badgeLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        }
    }
}

@Composable
private fun RatingRow(rating: Double, reviewCount: Int, visitCount: Int) {

    Row(verticalAlignment = Alignment.CenterVertically) {

        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            tint = RatingStar,
            modifier = Modifier.size(15.dp)
        )

        Spacer(Modifier.width(4.dp))

        Text(
            text = String.format("%.1f", rating),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(Modifier.width(4.dp))

        Text(
            text = if (reviewCount == 1) "(1 review)" else "($reviewCount reviews)",
            fontSize = 12.sp,
            color = TextSecondary
        )

        if (visitCount > 0) {

            Spacer(Modifier.width(8.dp))

            Text(
                text = "\u2022  $visitCount visits",
                fontSize = 12.sp,
                color = TextTertiary
            )
        }
    }
}

@Composable
private fun FactPills(facts: List<PreviewFact>) {

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {

        facts.chunked(2).forEach { rowFacts ->

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {

                rowFacts.forEach { fact ->

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(SurfaceSoft)
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = fact.icon,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = fact.value,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Keeps the arrival gate honest and legible: the preview is planning
 * information, not the site's history. A visitor who has already been here
 * sees the opposite message, since their unlock is permanent.
 */
@Composable
private fun UnlockNotice(isUnlocked: Boolean) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PrimarySoft)
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {

        Icon(
            imageVector = if (isUnlocked) Icons.Default.CheckCircle else Icons.Default.Lock,
            contentDescription = null,
            tint = Primary,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(15.dp)
        )

        Spacer(Modifier.width(9.dp))

        Text(
            text = if (isUnlocked) {
                "You've visited this site \u2014 its full history, timeline and " +
                    "stories are already unlocked in your Site Library."
            } else {
                "The full history, timeline and stories unlock when you arrive " +
                    "here in person."
            },
            fontSize = 11.5.sp,
            lineHeight = 17.sp,
            color = TextSecondary
        )
    }
}

@Composable
private fun PreviewSectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 13.5.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary
    )
}

@Composable
private fun SecondaryActionButton(text: String, onClick: () -> Unit) {

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceSoft)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
    }
}

// ---------------------------------------------------------------------------
// The affordance itself
// ---------------------------------------------------------------------------

/**
 * The small "i" that opens a preview. Sized to a 32dp touch target and
 * given its own click handler so tapping it never toggles the selection of
 * the card it sits on.
 */
@Composable
internal fun PreviewInfoButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Primary,
    background: Color = CardSurface
) {

    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {

        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(background),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "View details",
                tint = tint,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

/**
 * Text version, for list rows where a floating circle would crowd the
 * layout. Reads as a link rather than a control that changes the trip.
 */
@Composable
internal fun PreviewDetailsLink(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = Primary,
            modifier = Modifier.size(12.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "Details",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Primary
        )
    }
}
