package com.histoury.app.ui.screens.rankings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.google.firebase.Timestamp
import com.histoury.app.data.model.HistoricalSite
import com.histoury.app.data.model.Review
import com.histoury.app.data.viewmodel.RankingSortMode
import com.histoury.app.data.viewmodel.RankingsViewModel
import com.histoury.app.navigation.Routes
import com.histoury.app.theme.AccentGold
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Outline
import com.histoury.app.theme.Primary
import com.histoury.app.theme.PrimaryDark
import com.histoury.app.theme.PrimarySoft
import com.histoury.app.theme.RatingStar
import com.histoury.app.theme.SurfaceSoft
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary
import com.histoury.app.theme.TextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingsScreen(
    navController: NavHostController,
    rankingsViewModel: RankingsViewModel = viewModel()
) {

    val uiState by rankingsViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        rankingsViewModel.loadRankings()
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

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
                            navController.popBackStack()
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
                        text = "Site Rankings",
                        color = TextPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Most popular historical sites in Intramuros",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(CardSurface)
                    .padding(4.dp)
            ) {

                SortToggleButton(
                    label = "Top Rated",
                    icon = Icons.Default.Star,
                    isSelected = uiState.sortMode == RankingSortMode.TOP_RATED,
                    onClick = {
                        rankingsViewModel.onSortModeChanged(RankingSortMode.TOP_RATED)
                    },
                    modifier = Modifier.weight(1f)
                )

                SortToggleButton(
                    label = "Most Reviewed",
                    icon = Icons.Default.TrendingUp,
                    isSelected = uiState.sortMode == RankingSortMode.MOST_REVIEWED,
                    onClick = {
                        rankingsViewModel.onSortModeChanged(RankingSortMode.MOST_REVIEWED)
                    },
                    modifier = Modifier.weight(1f)
                )
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

        } else if (uiState.sites.isEmpty()) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No historical sites available yet",
                    color = TextSecondary
                )
            }

        } else {

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                itemsIndexed(uiState.sites) { index, site ->

                    RankingRow(
                        rank = index + 1,
                        site = site,
                        onViewClick = {
                            navController.navigate(
                                Routes.Map.createRoute(site.documentId)
                            )
                        },
                        onReviewsClick = {
                            rankingsViewModel.openSiteReviews(site)
                        }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Primary, PrimaryDark)
                        )
                    )
                    .padding(vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {

                RankingsStat(
                    value = uiState.totalReviews.toString(),
                    label = "Total Reviews"
                )

                RankingsStat(
                    value = "%.1f".format(uiState.averageRating),
                    label = "Avg Rating"
                )

                RankingsStat(
                    value = uiState.totalSites.toString(),
                    label = "Total Sites"
                )
            }
        }
    }

    // ----- All-reviews bottom sheet -----

    val reviewsSite = uiState.reviewsSite

    if (reviewsSite != null) {

        ModalBottomSheet(
            onDismissRequest = { rankingsViewModel.closeSiteReviews() },
            containerColor = CardSurface
        ) {

            SiteReviewsSheetContent(
                site = reviewsSite,
                reviews = uiState.siteReviews,
                isLoading = uiState.isLoadingReviews
            )
        }
    }
}

@Composable
private fun SortToggleButton(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (isSelected) Primary else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) Color.White else TextSecondary,
            modifier = Modifier.size(14.dp)
        )

        Spacer(Modifier.width(4.dp))

        Text(
            text = label,
            color = if (isSelected) Color.White else TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

// Medal colours are fixed by convention (gold/silver/bronze read the same
// in any theme), so they live here as named constants rather than as a
// theme token or as unnamed hex passed straight to `tint`.
private val MedalGold = Color(0xFFFFD700)
private val MedalSilver = Color(0xFFC0C0C0)
private val MedalBronze = Color(0xFFCD7F32)

@Composable
private fun RankingRow(
    rank: Int,
    site: HistoricalSite,
    onViewClick: () -> Unit,
    onReviewsClick: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 3.dp, shape = RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(CardSurface)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (rank <= 3) AccentGold.copy(alpha = 0.14f) else SurfaceSoft),
            contentAlignment = Alignment.Center
        ) {

            when (rank) {
                1 -> Icon(
                    Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = MedalGold,
                    modifier = Modifier.size(18.dp)
                )
                2 -> Icon(
                    Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = MedalSilver,
                    modifier = Modifier.size(18.dp)
                )
                3 -> Icon(
                    Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = MedalBronze,
                    modifier = Modifier.size(18.dp)
                )
                else -> Text(
                    text = rank.toString(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceSoft)
        ) {
            AsyncImage(
                model = site.featuredImage,
                contentDescription = site.siteName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {

            Text(
                text = site.siteName,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = TextPrimary,
                maxLines = 1
            )

            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {

                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = RatingStar,
                    modifier = Modifier.size(13.dp)
                )

                Spacer(Modifier.width(2.dp))

                Text(
                    text = "%.1f".format(site.averageRating),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = TextPrimary
                )

                Spacer(Modifier.width(4.dp))

                Text(
                    text = "(${site.reviewCount})",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {

                Surface(
                    shape = RoundedCornerShape(50),
                    color = Primary,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onViewClick
                    )
                ) {
                    Text(
                        text = "View",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = SurfaceSoft,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onReviewsClick
                    )
                ) {
                    Text(
                        text = "Reviews",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RankingsStat(value: String, label: String) {

    Column(horizontalAlignment = Alignment.CenterHorizontally) {

        Text(
            text = value,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp
        )

        Text(
            text = label,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 11.sp
        )
    }
}

// ---------------------------------------------------------------------------
// Reviews bottom sheet
// ---------------------------------------------------------------------------

@Composable
private fun SiteReviewsSheetContent(
    site: HistoricalSite,
    reviews: List<Review>,
    isLoading: Boolean
) {

    // One LazyColumn for everything: wraps when short, caps + scrolls when
    // long, so the sheet height always behaves.
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 560.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp)
    ) {

        item {

            Text(
                text = site.siteName,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Text(
                text = "What visitors are saying",
                fontSize = 12.sp,
                color = TextSecondary
            )

            Spacer(Modifier.height(16.dp))

            ReviewSummaryCard(site = site, reviews = reviews)

            Spacer(Modifier.height(16.dp))

            Divider(color = SurfaceSoft)

            Spacer(Modifier.height(8.dp))
        }

        when {

            isLoading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Primary)
                    }
                }
            }

            reviews.isEmpty() -> {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {

                        Text(
                            text = "No reviews yet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text = "Visit ${site.siteName} and be the first to share your experience!",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            else -> {
                items(reviews.size) { index ->
                    ReviewCard(review = reviews[index])
                    if (index != reviews.lastIndex) {
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewSummaryCard(
    site: HistoricalSite,
    reviews: List<Review>
) {

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
                text = "%.1f".format(site.averageRating),
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            StarRow(rating = site.averageRating, starSize = 13)

            Spacer(Modifier.height(2.dp))

            Text(
                text = "${site.reviewCount} review${if (site.reviewCount == 1) "" else "s"}",
                fontSize = 11.sp,
                color = TextSecondary
            )
        }

        Spacer(Modifier.width(20.dp))

        // 5..1 star distribution bars, computed from the loaded reviews
        Column(modifier = Modifier.weight(1f)) {

            for (star in 5 downTo 1) {

                val count = reviews.count { review ->
                    review.rating.toInt() == star
                }

                val fraction = if (reviews.isEmpty()) {
                    0f
                } else {
                    count.toFloat() / reviews.size
                }

                Row(verticalAlignment = Alignment.CenterVertically) {

                    Text(
                        text = star.toString(),
                        fontSize = 10.sp,
                        color = TextSecondary,
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
                        text = count.toString(),
                        fontSize = 10.sp,
                        color = TextSecondary,
                        modifier = Modifier.width(16.dp)
                    )
                }

                if (star != 1) {
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(review: Review) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceSoft)
            .padding(14.dp)
    ) {

        Row(verticalAlignment = Alignment.CenterVertically) {

            // Initial avatar
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(PrimarySoft),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = review.userName.trim()
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
                        text = review.userName.ifBlank { "Tourist User" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextPrimary,
                        maxLines = 1,
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

                Text(
                    text = formatReviewDate(review.createdAt),
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }

            StarRow(rating = review.rating, starSize = 12)
        }

        if (review.reviewText.isNotBlank()) {

            Spacer(Modifier.height(10.dp))

            Text(
                text = review.reviewText,
                fontSize = 13.sp,
                color = TextSecondary,
                lineHeight = 19.sp
            )
        }

        if (review.likes > 0) {

            Spacer(Modifier.height(8.dp))

            Text(
                text = "\uD83D\uDC4D ${review.likes} helpful",
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun StarRow(rating: Double, starSize: Int) {

    val filledStars = rating.toInt().coerceIn(0, 5)

    Row {
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
private fun formatReviewDate(rawDate: Any?): String {

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
