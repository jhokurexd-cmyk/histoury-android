package com.histoury.app.ui.screens.history

import com.histoury.app.theme.styledElevation
import com.histoury.app.theme.softShadow
import com.histoury.app.ui.tutorial.TutorialStep
import com.histoury.app.ui.tutorial.TutorialTour
import com.histoury.app.navigation.navigateSafely
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.google.firebase.Timestamp
import com.histoury.app.data.model.HistoricalSite
import com.histoury.app.data.viewmodel.VisitHistoryViewModel
import com.histoury.app.data.viewmodel.VisitedSiteEntry
import com.histoury.app.navigation.Routes
import com.histoury.app.theme.SuccessSurface
import com.histoury.app.theme.Success
import com.histoury.app.theme.Outline
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.AccentGold
import com.histoury.app.theme.Primary
import com.histoury.app.theme.PrimarySoft
import com.histoury.app.theme.SurfaceSoft
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary
import com.histoury.app.ui.components.BottomNavBarInset
import com.histoury.app.ui.components.BottomNavScaffold

@Composable
fun VisitHistoryScreen(
    navController: NavHostController,
    visitHistoryViewModel: VisitHistoryViewModel = viewModel()
) {

    val uiState by visitHistoryViewModel.uiState.collectAsState()

    // Refresh every time the screen comes (back) into view — returning from
    // a site, from AR, or from another tab. The ViewModel also loads on its
    // own when Firebase Auth restores the session and when a visit is
    // recorded anywhere in the app.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                visitHistoryViewModel.loadVisitHistory()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    TutorialTour(
        id = "visit_history",
        steps = listOf(
            TutorialStep(
                target = null,
                title = "Your Visit History",
                body = "Every site you've been to shows up here once you arrive, and its full story stays unlocked for you."
            ),
            TutorialStep(
                target = null,
                title = "Yet to Explore",
                body = "Further down are the sites you haven't visited. Tap Visit on one to get directions there."
            )
        )
    )

    BottomNavScaffold(
        navController = navController,
        containerColor = SurfaceSoft
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceSoft)
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp, bottom = 6.dp)
            ) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    // Top-level tab: no back arrow (Back already returns to
                    // Home), same header as the other tabs — title on the
                    // left, menu on the right.
                    Text(
                        text = "Visit History",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )

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
                                navController.navigateSafely(Routes.Menu.route)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu",
                            tint = Primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = PrimarySoft,
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(22.dp)
                        )

                        Spacer(Modifier.width(10.dp))

                        Column {
                            Text(
                                text = if (uiState.refreshFailed && uiState.visitedEntries.isEmpty()) {
                                    "\u2014"
                                } else {
                                    uiState.visitedEntries.size.toString()
                                },
                                color = TextPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Sites Visited",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            if (uiState.isLoading) {

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Primary)
                }

            } else {

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = 16.dp + BottomNavBarInset
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    item {
                        Text(
                            text = "Your Journey",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    if (uiState.visitedEntries.isEmpty() && uiState.refreshFailed) {
                        // Couldn't load — not the same as "nothing visited".
                        item {
                            Column {
                                Text(
                                    text = "Couldn't load your visit history. Check your connection and try again.",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                                androidx.compose.material3.TextButton(
                                    onClick = { visitHistoryViewModel.loadVisitHistory() }
                                ) {
                                    Text("Try Again", color = Primary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else if (uiState.visitedEntries.isEmpty()) {
                        item {
                            Text(
                                text = "No sites visited yet. Head out and explore!",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        items(uiState.visitedEntries) { entry ->
                            VisitedSiteRow(
                                entry = entry,
                                // A visited site is unlocked: open its info page.
                                onOpenClick = {
                                    navController.navigateSafely(
                                        Routes.SiteDetails.createRoute(entry.site.documentId, showAr = false)
                                    )
                                },
                                onLeaveReviewClick = {
                                    navController.navigateSafely(
                                        Routes.Review.createRoute(
                                            entry.site.documentId,
                                            entry.visit.id
                                        )
                                    )
                                }
                            )
                        }
                    }

                    // After a failed load there's nothing to compare against,
                    // so "Yet to Explore" would wrongly claim everything has
                    // been explored.
                    val loadFailedWithNothing = uiState.refreshFailed &&
                        uiState.visitedEntries.isEmpty() &&
                        uiState.yetToExploreSites.isEmpty()

                    if (!loadFailedWithNothing) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Yet to Explore",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        if (uiState.yetToExploreSites.isEmpty()) {
                            item {
                                Text(
                                    text = "You've explored everything published so far!",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            items(uiState.yetToExploreSites) { site ->
                                YetToExploreRow(
                                    site = site,
                                    onVisitClick = {
                                        navController.navigateSafely(
                                            Routes.Map.createRoute(site.documentId)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VisitedSiteRow(
    entry: VisitedSiteEntry,
    onOpenClick: () -> Unit,
    onLeaveReviewClick: () -> Unit
) {

    Surface(
        onClick = onOpenClick,
        shape = RoundedCornerShape(20.dp),
        color = CardSurface,
        shadowElevation = styledElevation(3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {

        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // Gold accent strip marking a completed visit (per Figma).
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(AccentGold)
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Box {

                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceSoft)
                    ) {
                        AsyncImage(
                            model = entry.site.featuredImage,
                            contentDescription = entry.site.siteName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Success,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(2.dp)
                            .background(CardSurface, RoundedCornerShape(50))
                            .size(16.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        Text(
                            text = entry.site.siteName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = SuccessSurface
                        ) {
                            Text(
                                text = "Visited",
                                color = Success,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = formatVisitDate(entry.visit.visitDate),
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(Modifier.height(3.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = entry.site.location,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Both review states share one slot, one pill shape and
                    // one type style, so a card doesn't shift between them.
                    Spacer(Modifier.height(10.dp))

                    ReviewStatusPill(
                        reviewSubmitted = entry.visit.reviewSubmitted,
                        onLeaveReviewClick = onLeaveReviewClick
                    )
                }
            }
        }
    }
}

/**
 * The review state of a visited site, always in the same place and shape:
 * a quiet green "Review submitted" status, or a coral "Leave a Review" action.
 */
@Composable
private fun ReviewStatusPill(
    reviewSubmitted: Boolean,
    onLeaveReviewClick: () -> Unit
) {

    val shape = RoundedCornerShape(50)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(shape)
            .background(if (reviewSubmitted) SuccessSurface else PrimarySoft)
            .then(
                if (reviewSubmitted) {
                    Modifier
                } else {
                    Modifier.clickable(
                        onClickLabel = "Leave a review",
                        role = Role.Button,
                        onClick = onLeaveReviewClick
                    )
                }
            )
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Icon(
            imageVector = if (reviewSubmitted) Icons.Default.CheckCircle else Icons.Default.RateReview,
            contentDescription = null,
            tint = if (reviewSubmitted) Success else Primary,
            modifier = Modifier.size(13.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = if (reviewSubmitted) "Review submitted" else "Leave a Review \u2192",
            color = if (reviewSubmitted) Success else Primary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

// Renders the site photo without color, matching the muted "not yet
// visited" treatment in the Figma design.
private val GrayscaleFilter = ColorFilter.colorMatrix(
    ColorMatrix().apply { setToSaturation(0f) }
)

@Composable
private fun YetToExploreRow(
    site: HistoricalSite,
    onVisitClick: () -> Unit
) {

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = SurfaceSoft,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onVisitClick
            )
    ) {

        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Outline)
            ) {
                AsyncImage(
                    model = site.featuredImage,
                    contentDescription = site.siteName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    colorFilter = GrayscaleFilter
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {

                Text(
                    text = site.siteName,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    fontSize = 14.sp
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = site.location,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                Text(
                    text = "Visit \u2192",
                    color = Primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun formatVisitDate(rawDate: Any?): String {

    val timestamp = rawDate as? Timestamp ?: return "--"

    val calendar = java.util.Calendar.getInstance()

    calendar.time = timestamp.toDate()

    val months = arrayOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    val month = months[calendar.get(java.util.Calendar.MONTH)]

    val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)

    val year = calendar.get(java.util.Calendar.YEAR)

    return "$month $day, $year"
}
