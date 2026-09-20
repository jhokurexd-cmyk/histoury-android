package com.histoury.app.ui.screens.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items as lazyColumnItems
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.histoury.app.data.model.Place
import com.histoury.app.data.viewmodel.ItineraryViewModel
import com.histoury.app.data.viewmodel.StopPreviewMode
import com.histoury.app.theme.OnColor
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Primary
import com.histoury.app.theme.SurfaceSoft
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary
import com.histoury.app.ui.components.AuthStatusBanner
import com.histoury.app.ui.components.PrimaryButton

// Same taxonomy as ContentCategoryChips / the admin panel's placeCategories.js.
private data class InterestOption(val key: String, val label: String, val icon: ImageVector)

private val INTEREST_OPTIONS = listOf(
    InterestOption("food", "Food & Drink", Icons.Default.Restaurant),
    InterestOption("park", "Parks", Icons.Default.Park),
    InterestOption("hotel", "Hotels", Icons.Default.Hotel),
    InterestOption("souvenir_shop", "Souvenir Shops", Icons.Default.Storefront),
    InterestOption("school", "Schools", Icons.Default.School),
    InterestOption("bank", "Banks", Icons.Default.AccountBalanceWallet)
)

// ---------------------------------------------------------------------------
// Step 1 — Select Historical Sites
// ---------------------------------------------------------------------------

@Composable
fun SelectSitesStep(
    viewModel: ItineraryViewModel,
    onClose: () -> Unit
) {

    val uiState by viewModel.uiState.collectAsState()

    Scaffold(containerColor = SurfaceSoft) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            WizardStepHeader(
                title = "Select Historical Sites",
                subtitle = "Tap \u2139 on any site to see what it offers before you pick",
                stepLabel = "1 / 3",
                onBackClick = onClose
            )

            if (uiState.isLoadingCatalog) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Primary)
                }
            } else {

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    items(uiState.allSites) { site ->

                        val isSelected = site.documentId in uiState.selectedSiteIds

                        SelectableCard(
                            title = site.siteName,
                            subtitle = site.location,
                            imageUrl = site.featuredImage,
                            isSelected = isSelected,
                            onClick = { viewModel.toggleSiteSelected(site.documentId) },
                            onInfoClick = {
                                viewModel.openStopPreview(
                                    refId = site.documentId,
                                    refType = "site",
                                    mode = StopPreviewMode.SELECTION
                                )
                            }
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardSurface)
                        .padding(20.dp)
                ) {

                    if (uiState.errorMessage != null) {
                        AuthStatusBanner(
                            message = uiState.errorMessage ?: "",
                            isError = true
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    PrimaryButton(
                        text = if (uiState.selectedSiteIds.isEmpty()) {
                            "Continue"
                        } else {
                            "Continue \u2022 ${uiState.selectedSiteIds.size} selected"
                        },
                        onClick = { viewModel.goToInterestsStep() }
                    )
                }
            }
        }
    }

    ItineraryStopPreviewHost(viewModel)
}

@Composable
private fun SelectableCard(
    title: String,
    subtitle: String,
    imageUrl: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onInfoClick: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 3.dp, shape = RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(CardSurface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
        ) {

            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Primary else Color.White.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Sits opposite the checkmark so the two controls read as
            // separate: one changes the trip, the other only explains.
            PreviewInfoButton(
                onClick = onInfoClick,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp),
                background = Color.White.copy(alpha = 0.92f)
            )
        }

        Column(modifier = Modifier.padding(10.dp)) {

            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 10.5.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun FlowingInterestChips(
    selectedKeys: Set<String>,
    onToggle: (String) -> Unit
) {

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

        INTEREST_OPTIONS.chunked(2).forEach { rowOptions ->

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {

                rowOptions.forEach { option ->

                    val isSelected = option.key in selectedKeys

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (isSelected) Primary else CardSurface)
                            .shadow(
                                elevation = if (isSelected) 0.dp else 2.dp,
                                shape = RoundedCornerShape(50)
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onToggle(option.key)
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = option.icon,
                            contentDescription = null,
                            tint = if (isSelected) OnColor else Primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = option.label,
                            color = if (isSelected) OnColor else TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Step 2 — Select Interests
// ---------------------------------------------------------------------------

@Composable
fun SelectInterestsStep(
    viewModel: ItineraryViewModel
) {

    val uiState by viewModel.uiState.collectAsState()

    Scaffold(containerColor = SurfaceSoft) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            WizardStepHeader(
                title = "Select Your Interests",
                subtitle = "We'll recommend nearby spots that match",
                stepLabel = "2 / 3",
                onBackClick = { viewModel.backToSites() }
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(20.dp)
            ) {

                FlowingInterestChips(
                    selectedKeys = uiState.selectedInterestCategories,
                    onToggle = { key -> viewModel.toggleInterestCategory(key) }
                )

                Spacer(Modifier.height(20.dp))

                Text(
                    text = "Optional \u2014 skip this if you just want your selected sites.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSurface)
                    .padding(20.dp)
            ) {

                PrimaryButton(
                    text = if (uiState.selectedInterestCategories.isEmpty()) {
                        "Continue Without Interests"
                    } else {
                        "Find Recommendations"
                    },
                    onClick = {
                        if (uiState.selectedInterestCategories.isEmpty()) {
                            viewModel.generateRoute()
                        } else {
                            viewModel.goToRecommendationsStep()
                        }
                    }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Step 3 — Smart Recommendations
// ---------------------------------------------------------------------------

@Composable
fun RecommendationsStep(
    viewModel: ItineraryViewModel
) {

    val uiState by viewModel.uiState.collectAsState()

    Scaffold(containerColor = SurfaceSoft) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            WizardStepHeader(
                title = "Smart Recommendations",
                subtitle = "Tap Details to see hours, prices and photos first",
                stepLabel = "3 / 3",
                onBackClick = { viewModel.backToInterests() }
            )

            if (uiState.recommendedPlaces.isEmpty()) {

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No matching spots within walking distance of your " +
                                "selected sites \u2014 you can still generate your route with " +
                                "just the sites you picked.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }

            } else {

                RecommendationsList(
                    viewModel = viewModel,
                    places = uiState.recommendedPlaces,
                    dismissedIds = uiState.dismissedRecommendationIds
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSurface)
                    .padding(20.dp)
            ) {

                if (uiState.isGeneratingRoute) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Primary, modifier = Modifier.size(28.dp))
                    }
                } else {
                    PrimaryButton(
                        text = "Generate Optimized Route",
                        onClick = { viewModel.generateRoute() }
                    )
                }
            }
        }
    }

    ItineraryStopPreviewHost(viewModel)
}

@Composable
private fun ColumnScope.RecommendationsList(
    viewModel: ItineraryViewModel,
    places: List<Place>,
    dismissedIds: Set<String>
) {

    LazyColumn(
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        lazyColumnItems(places) { place ->

            val isKept = place.documentId !in dismissedIds

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 2.dp, shape = RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardSurface)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        viewModel.toggleRecommendationDismissed(place.documentId)
                    }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceSoft)
                ) {
                    if (place.featuredImage.isNotBlank()) {
                        AsyncImage(
                            model = place.featuredImage,
                            contentDescription = place.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(place.categoryEmoji, fontSize = 22.sp)
                        }
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {

                    Text(
                        text = place.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = place.categoryLabel,
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }

                    Spacer(Modifier.height(2.dp))

                    PreviewDetailsLink(
                        onClick = {
                            viewModel.openStopPreview(
                                refId = place.documentId,
                                refType = "place",
                                mode = StopPreviewMode.RECOMMENDATION
                            )
                        },
                        modifier = Modifier.offset(x = (-8).dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(if (isKept) Primary else SurfaceSoft),
                    contentAlignment = Alignment.Center
                ) {
                    if (isKept) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Included",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}
