package com.histoury.app.ui.screens.explore

import com.histoury.app.ui.tutorial.TutorialStep
import com.histoury.app.ui.tutorial.TutorialTour
import com.histoury.app.ui.tutorial.tutorialTarget
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.NearMe
import com.histoury.app.theme.PrimarySoft
import com.histoury.app.util.UserDistance
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

    // Search + sort (nearest first by default), kept across rotation and
    // opening a site's details.
    val context = LocalContext.current
    val userPosition by UserDistance.position.collectAsState()
    LaunchedEffect(Unit) { UserDistance.refresh(context) }
    var siteQuery by rememberSaveable { mutableStateOf("") }
    var siteSort by rememberSaveable { mutableStateOf(BuilderSort.NEAREST) }
    val shownSites = remember(uiState.allSites, siteQuery, siteSort, userPosition) {
        builderFilterAndSort(
            items = uiState.allSites,
            query = siteQuery,
            sort = siteSort,
            from = userPosition,
            name = { it.siteName },
            latitude = { it.latitude },
            longitude = { it.longitude },
            location = { it.location },
            description = { it.description }
        )
    }

    TutorialTour(
        id = "builder_sites",
        enabled = !uiState.isLoadingCatalog,
        steps = listOf(
            TutorialStep(
                target = null,
                title = "Plan a trip in 3 steps",
                body = "1. Pick the historical sites you want to see.\n2. Choose your interests (optional).\n3. Add suggested spots nearby.\nHistoury then puts them in the best order for you."
            ),
            TutorialStep(
                target = "builder_first_site",
                title = "Read about a site",
                body = "Tap a card to see the site's photos and details before deciding."
            ),
            TutorialStep(
                target = "builder_first_site_circle",
                title = "Add it to your trip",
                body = "Tap the circle in the corner to pick the site. Picked sites get a coral border; tap again to remove."
            ),
            TutorialStep(
                target = "builder_sites_continue",
                title = "Continue",
                body = "Picked everything you want? Tap Continue to go to the next step."
            )
        )
    )

    Scaffold(containerColor = SurfaceSoft) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            WizardStepHeader(
                title = "Select Historical Sites",
                subtitle = "Tap for details \u2022 circle to pick",
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

                BuilderListControls(
                    query = siteQuery,
                    onQueryChange = { siteQuery = it },
                    placeholder = "Search historical sites...",
                    sort = siteSort,
                    onSortChange = { siteSort = it },
                    hasLocation = userPosition != null,
                    resultCount = shownSites.size
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    items(shownSites) { site ->

                        val isSelected = site.documentId in uiState.selectedSiteIds

                        SelectableCard(
                            title = site.siteName,
                            subtitle = site.location,
                            distance = UserDistance.labelFor(site.latitude, site.longitude, userPosition),
                            imageUrl = site.featuredImage,
                            isSelected = isSelected,
                            onToggleSelected = { viewModel.toggleSiteSelected(site.documentId) },
                            onOpenDetails = {
                                viewModel.openStopPreview(
                                    refId = site.documentId,
                                    refType = "site",
                                    mode = StopPreviewMode.SELECTION
                                )
                            },
                            tutorialKey = if (site.documentId == shownSites.firstOrNull()?.documentId) {
                                "builder_first_site"
                            } else {
                                null
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

                    Box(Modifier.tutorialTarget("builder_sites_continue")) {
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
    }

    ItineraryStopPreviewHost(viewModel)
}

@Composable
private fun SelectableCard(
    title: String,
    subtitle: String,
    imageUrl: String,
    isSelected: Boolean,
    // "350 m" from the visitor, or null when unknown.
    distance: String? = null,
    onToggleSelected: () -> Unit,
    onOpenDetails: () -> Unit,
    // Set on the first card only, so the tutorial can point at it.
    tutorialKey: String? = null
) {

    // Tapping the card (picture or name) opens the site's details. Only the
    // circle in the corner selects — so browsing never changes the trip.
    Column(
        modifier = Modifier
            .then(if (tutorialKey != null) Modifier.tutorialTarget(tutorialKey) else Modifier)
            .fillMaxWidth()
            .shadow(elevation = 3.dp, shape = RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(CardSurface)
            .then(
                if (isSelected) Modifier.border(2.dp, Primary, RoundedCornerShape(18.dp))
                else Modifier
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpenDetails
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

            SelectCircle(
                isSelected = isSelected,
                onClick = onToggleSelected,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .then(if (tutorialKey != null) Modifier.tutorialTarget(tutorialKey + "_circle") else Modifier)
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

            if (distance != null) {
                Spacer(Modifier.height(3.dp))
                DistanceLabel(distance)
            } else if (subtitle.isNotBlank()) {
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

    TutorialTour(
        id = "builder_interests",
        steps = listOf(
            TutorialStep(
                target = "builder_interests",
                title = "What do you enjoy?",
                body = "Pick a few, like food or parks, and we'll suggest spots near your sites that fit. This step is optional."
            ),
            TutorialStep(
                target = "builder_interests_continue",
                title = "Next",
                body = "With interests picked, this finds recommendations. With none, it goes straight to your route."
            )
        )
    )

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

                Box(Modifier.tutorialTarget("builder_interests")) {
                    FlowingInterestChips(
                        selectedKeys = uiState.selectedInterestCategories,
                        onToggle = { key -> viewModel.toggleInterestCategory(key) }
                    )
                }

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

                Box(Modifier.tutorialTarget("builder_interests_continue")) {
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
}

// ---------------------------------------------------------------------------
// Step 3 — Smart Recommendations
// ---------------------------------------------------------------------------

@Composable
fun RecommendationsStep(
    viewModel: ItineraryViewModel
) {

    val uiState by viewModel.uiState.collectAsState()

    TutorialTour(
        id = "builder_recommendations",
        steps = listOf(
            TutorialStep(
                target = null,
                title = "Suggested spots",
                body = "Spots near your sites that match your interests come first. Every other place is listed below them, so you can add any of them too. Tap one for details, or its circle to add it."
            ),
            TutorialStep(
                target = "builder_generate",
                title = "Make your route",
                body = "Histoury puts every stop in the shortest walking order and works out how long your day takes."
            )
        )
    )

    Scaffold(containerColor = SurfaceSoft) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            WizardStepHeader(
                title = "Add Places",
                subtitle = "Suggestions first \u2022 circle to add",
                stepLabel = "3 / 3",
                onBackClick = { viewModel.backToInterests() }
            )

            // Suggestions first, then every other place, so each one still
            // gets a chance to be picked.
            RecommendationsList(
                viewModel = viewModel,
                recommended = uiState.recommendedPlaces,
                allPlaces = uiState.allPlaces,
                selectedIds = uiState.selectedRecommendationIds
            )

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
                    Box(Modifier.tutorialTarget("builder_generate")) {
                        PrimaryButton(
                            text = "Generate Optimized Route",
                            onClick = { viewModel.generateRoute() }
                        )
                    }
                }
            }
        }
    }

    ItineraryStopPreviewHost(viewModel)
}

@Composable
private fun ColumnScope.RecommendationsList(
    viewModel: ItineraryViewModel,
    recommended: List<Place>,
    allPlaces: List<Place>,
    selectedIds: Set<String>
) {

    val context = LocalContext.current
    val userPosition by UserDistance.position.collectAsState()
    LaunchedEffect(Unit) { UserDistance.refresh(context) }

    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(BuilderSort.NEAREST) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }

    // Only places that can be routed to (they need coordinates).
    val routable = remember(allPlaces) { allPlaces.filter { it.latitude != null && it.longitude != null } }
    val recommendedIds = remember(recommended) { recommended.map { it.documentId }.toSet() }

    // Category chips for the kinds of place there are, in the same order as
    // the interests step, each with how many there are.
    val chips = remember(routable) {
        INTEREST_OPTIONS.mapNotNull { option ->
            val count = routable.count { placeGroup(it) == option.key }
            if (count > 0) BuilderCategoryChip(option.key, option.label, option.icon, count) else null
        }
    }
    // A category that no longer has any places falls back to All.
    val activeCategory = category?.takeIf { key -> chips.any { it.key == key } }

    fun ordered(items: List<Place>) = builderFilterAndSort(
        items = items.filter { activeCategory == null || placeGroup(it) == activeCategory },
        query = query,
        sort = sort,
        from = userPosition,
        name = { it.name },
        latitude = { it.latitude },
        longitude = { it.longitude },
        location = { it.location },
        description = { it.description }
    )

    // Suggested (near your sites, matching your interests) first; then
    // everything else, so every place still gets a chance to be picked.
    val shownSuggested = remember(routable, recommendedIds, query, sort, activeCategory, userPosition) {
        ordered(routable.filter { it.documentId in recommendedIds })
    }
    val shownOthers = remember(routable, recommendedIds, query, sort, activeCategory, userPosition) {
        ordered(routable.filter { it.documentId !in recommendedIds })
    }

    BuilderListControls(
        query = query,
        onQueryChange = { query = it },
        placeholder = "Search caf\u00E9s, parks, hotels...",
        sort = sort,
        onSortChange = { sort = it },
        hasLocation = userPosition != null,
        resultCount = shownSuggested.size + shownOthers.size,
        categories = chips,
        selectedCategory = activeCategory,
        onCategoryChange = { category = it }
    )

    if (shownSuggested.isEmpty() && shownOthers.isEmpty()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (query.isNotBlank()) "No places match \"$query\"." else "No places to add yet.",
                color = TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        if (shownSuggested.isNotEmpty()) {
            item {
                PlaceSectionHeader(title = "Suggested for you")
            }
            lazyColumnItems(shownSuggested) { place ->
                PlacePickRow(viewModel, place, place.documentId in selectedIds, userPosition, suggested = true)
            }
        }

        if (shownOthers.isNotEmpty()) {
            item {
                PlaceSectionHeader(
                    title = if (shownSuggested.isEmpty()) "All places" else "More places",
                    topSpace = shownSuggested.isNotEmpty()
                )
            }
            lazyColumnItems(shownOthers) { place ->
                PlacePickRow(viewModel, place, place.documentId in selectedIds, userPosition, suggested = false)
            }
        }
    }
}

@Composable
private fun PlaceSectionHeader(title: String, topSpace: Boolean = false) {
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
        modifier = Modifier.padding(top = if (topSpace) 10.dp else 0.dp)
    )
}

/** One place in step 3: tap for details, the circle adds it to the trip. */
@Composable
private fun PlacePickRow(
    viewModel: ItineraryViewModel,
    place: Place,
    isSelected: Boolean,
    userPosition: com.google.android.gms.maps.model.LatLng?,
    suggested: Boolean
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(CardSurface)
            .then(
                if (isSelected) Modifier.border(2.dp, Primary, RoundedCornerShape(16.dp))
                else Modifier
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                viewModel.openStopPreview(
                    refId = place.documentId,
                    refType = "place",
                    mode = StopPreviewMode.RECOMMENDATION
                )
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

            Spacer(Modifier.height(5.dp))

            // What kind of place it is, at a glance, then how far.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${place.categoryEmoji} ${place.categoryLabel}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Primary,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(PrimarySoft)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
                UserDistance.labelFor(place.latitude, place.longitude, userPosition)?.let { distance ->
                    Spacer(Modifier.width(8.dp))
                    DistanceLabel(distance)
                }
            }
        }

        SelectCircle(
            isSelected = isSelected,
            onClick = { viewModel.toggleRecommendationSelected(place.documentId) },
            unselectedFill = SurfaceSoft
        )
    }
}

/**
 * The select control on builder cards. Its own 44dp touch target, separate
 * from the card's tap-for-details, so picking a stop and reading about it
 * never get mixed up.
 */
@Composable
private fun SelectCircle(
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    unselectedFill: Color = Color.White.copy(alpha = 0.92f)
) {

    Box(
        modifier = modifier
            .size(44.dp)
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
                .size(26.dp)
                .clip(CircleShape)
                .background(if (isSelected) Primary else unselectedFill)
                .then(
                    if (isSelected) Modifier
                    else Modifier.border(2.dp, TextSecondary, CircleShape)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

/** Which interest chip a place belongs to (cafés and restaurants are both Food & Drink). */
private fun placeGroup(place: Place): String = when (place.category.lowercase()) {
    "food", "cafe", "restaurant" -> "food"
    else -> place.category.lowercase()
}

/** "➤ 350 m away" on builder cards. */
@Composable
private fun DistanceLabel(distance: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.NearMe,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(11.dp)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = if (distance == "Nearby") distance else "$distance away",
            fontSize = 11.sp,
            color = TextSecondary,
            maxLines = 1
        )
    }
}
