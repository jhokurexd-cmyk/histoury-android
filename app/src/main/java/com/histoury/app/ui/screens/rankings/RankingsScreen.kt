package com.histoury.app.ui.screens.rankings

import com.histoury.app.navigation.safePopBackStack
import com.histoury.app.navigation.navigateSafely
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
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import com.histoury.app.theme.Danger
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.histoury.app.ui.components.HeroIconButton
import com.histoury.app.ui.components.ReviewSummaryCard
import com.histoury.app.ui.components.SiteReviewsList
import com.histoury.app.ui.components.SiteReviewsDialogs
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

                HeroIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    onClick = { navController.safePopBackStack() }
                )

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
                            navController.navigateSafely(
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
                    // Coloured bar runs to the screen edge; its numbers stay
                    // above the gesture / navigation bar.
                    .navigationBarsPadding()
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

    val reviewsState by rankingsViewModel.reviews.state.collectAsState()

    if (reviewsSite != null) {

        ModalBottomSheet(
            onDismissRequest = { rankingsViewModel.closeSiteReviews() },
            containerColor = CardSurface
        ) {

            SiteReviewsSheetContent(
                site = reviewsSite,
                state = reviewsState,
                onToggleHelpful = { review -> rankingsViewModel.reviews.toggleHelpful(review) },
                onReport = { review -> rankingsViewModel.reviews.openReport(review) },
                onSortChange = { sort -> rankingsViewModel.reviews.setSort(sort) }
            )
        }
    }

    SiteReviewsDialogs(state = reviewsState, controller = rankingsViewModel.reviews)
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(4.dp))

            if (site.reviewCount == 0) {
                Text(
                    text = "No reviews yet",
                    color = TextTertiary,
                    fontSize = 11.5.sp
                )
            } else {
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
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                RankingAction(
                    text = "View",
                    filled = true,
                    onClick = onViewClick
                )

                RankingAction(
                    text = "Reviews",
                    filled = false,
                    onClick = onReviewsClick
                )
            }
        }
    }
}

/** Compact pill action on a ranking row, tall enough to tap comfortably. */
@Composable
private fun RankingAction(
    text: String,
    filled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(50))
            .background(if (filled) Primary else SurfaceSoft)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (filled) Color.White else TextPrimary
        )
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
    state: com.histoury.app.data.reviews.SiteReviewsState,
    onToggleHelpful: (Review) -> Unit,
    onReport: (Review) -> Unit,
    onSortChange: (com.histoury.app.data.reviews.ReviewSort) -> Unit
) {

    SiteReviewsList(
        state = state,
        onToggleHelpful = onToggleHelpful,
        onReport = onReport,
        header = {
            Column {

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

                ReviewSummaryCard(site = site, reviews = state.reviews)

                Spacer(Modifier.height(16.dp))

                Divider(color = SurfaceSoft)

                Spacer(Modifier.height(8.dp))

                // Only worth offering once there's more than one review to order.
                if (state.reviews.size > 1) {
                    com.histoury.app.ui.components.ReviewSortChips(
                        selected = state.sort,
                        onSelect = onSortChange,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                }

                when {
                    state.isLoading && state.reviews.isEmpty() -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Primary)
                    }

                    state.loadFailed -> Text(
                        text = "Couldn't load reviews. Check your connection.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )

                    state.reviews.isEmpty() -> Column(
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
        }
    )
}
