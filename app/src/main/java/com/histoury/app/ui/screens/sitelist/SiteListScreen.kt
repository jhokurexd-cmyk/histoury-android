package com.histoury.app.ui.screens.sitelist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.histoury.app.data.viewmodel.HomeViewModel
import com.histoury.app.navigation.Routes
import com.histoury.app.navigation.navigateSafely
import com.histoury.app.navigation.safePopBackStack
import com.histoury.app.theme.Primary
import com.histoury.app.theme.SurfaceSoft
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary
import com.histoury.app.ui.components.ContentCategory
import com.histoury.app.ui.components.ExploreGridCard
import com.histoury.app.ui.components.FeaturedSiteCard
import com.histoury.app.ui.components.HeroIconButton
import com.histoury.app.util.UserDistance
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.histoury.app.ui.screens.explore.BuilderCategoryChip
import com.histoury.app.ui.screens.explore.BuilderListControls
import com.histoury.app.ui.screens.explore.BuilderSort
import com.histoury.app.ui.screens.explore.builderFilterAndSort

/**
 * "View all" behind a Home section — the complete list that Home only
 * previews a few of.
 *
 *  - "nearest":        every site, closest first
 *  - "popular":        every site, highest rated first
 *  - "all":            every site, alphabetical
 *  - "places:<cat>":   every place in that category ("places:all" = all)
 *
 * Reuses HomeViewModel for the same data and category filtering as Home.
 */
@Composable
fun SiteListScreen(
    mode: String,
    navController: NavHostController,
    homeViewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val sites by homeViewModel.sites.collectAsState()
    val places by homeViewModel.places.collectAsState()
    val isLoading by homeViewModel.isLoading.collectAsState()
    val userPosition by UserDistance.position.collectAsState()

    val isPlaces = mode.startsWith("places:")
    val routeCategory = mode.removePrefix("places:").takeIf { isPlaces && it != "all" }

    // Every place is loaded; the category chips below filter them here, so
    // switching between Parks, Hotels, etc. doesn't need another screen.
    LaunchedEffect(isPlaces) {
        if (isPlaces) homeViewModel.onPlaceCategorySelected("all")
    }
    LaunchedEffect(Unit) { UserDistance.refresh(context) }

    // Search, sort and category: kept across rotation and coming back from
    // a details page. Starts in the order the Home section showed.
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable {
        mutableStateOf(
            when (mode) {
                "popular" -> BuilderSort.TOP_RATED
                "all" -> BuilderSort.NAME
                else -> BuilderSort.NEAREST
            }
        )
    }
    var category by rememberSaveable { mutableStateOf(routeCategory) }

    val categoryChips = remember(places) {
        ContentCategory.entries
            .filter { it != ContentCategory.ALL && it != ContentCategory.SITES }
            .mapNotNull { option ->
                val count = places.count { placeGroup(it.category) == option.key }
                if (count > 0) BuilderCategoryChip(option.key, option.label, option.icon, count) else null
            }
    }

    val title = when {
        isPlaces -> category?.let { key -> ContentCategory.entries.firstOrNull { it.key == key }?.label } ?: "All Places"
        mode == "nearest" -> "Nearest To You"
        mode == "popular" -> "Popular Historical Sites"
        else -> "All Historical Sites"
    }

    val orderedSites = remember(sites, query, sort, userPosition) {
        builderFilterAndSort(
            items = sites,
            query = query,
            sort = sort,
            from = userPosition,
            name = { it.siteName },
            latitude = { it.latitude },
            longitude = { it.longitude },
            location = { it.location },
            description = { it.description },
            rating = { it.averageRating }
        )
    }

    val orderedPlaces = remember(places, query, sort, category, userPosition) {
        builderFilterAndSort(
            items = places.filter { category == null || placeGroup(it.category) == category },
            query = query,
            sort = sort,
            from = userPosition,
            name = { it.name },
            latitude = { it.latitude },
            longitude = { it.longitude },
            location = { it.location },
            description = { it.description }
        )
    }

    val placeCategory = if (isPlaces) (category ?: "all") else null
    val count = if (isPlaces) orderedPlaces.size else orderedSites.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceSoft)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeroIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = { navController.safePopBackStack() }
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (!isLoading) {
            BuilderListControls(
                query = query,
                onQueryChange = { query = it },
                placeholder = if (isPlaces) "Search places..." else "Search historical sites...",
                sort = sort,
                onSortChange = { sort = it },
                hasLocation = userPosition != null,
                resultCount = count,
                categories = if (isPlaces) categoryChips else emptyList(),
                selectedCategory = category,
                onCategoryChange = { category = it },
                sortOptions = if (isPlaces) {
                    listOf(BuilderSort.NEAREST, BuilderSort.NAME)
                } else {
                    listOf(BuilderSort.NEAREST, BuilderSort.TOP_RATED, BuilderSort.NAME)
                }
            )
            Spacer(Modifier.height(6.dp))
        }

        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }

            count == 0 -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        query.isNotBlank() -> "Nothing matches \"$query\"."
                        placeCategory != null -> "No places in this category yet."
                        else -> "No historical sites to show right now."
                    },
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }

            placeCategory != null -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(orderedPlaces.chunked(2)) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        row.forEach { place ->
                            ExploreGridCard(
                                title = place.name,
                                subtitle = place.location,
                                badgeText = place.categoryLabel,
                                imageUrl = place.featuredImage,
                                rating = null,
                                distanceText = UserDistance.labelFor(place.latitude, place.longitude, userPosition),
                                onClick = {
                                    navController.navigateSafely(Routes.PlaceDetails.createRoute(place.documentId))
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                item { Spacer(Modifier.navigationBarsPadding()) }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(orderedSites, key = { it.documentId }) { site ->
                    FeaturedSiteCard(
                        title = site.siteName,
                        rating = site.averageRating,
                        imageUrl = site.featuredImage,
                        siteDocumentId = site.documentId,
                        distanceText = UserDistance.labelFor(site.latitude, site.longitude, userPosition),
                        onViewClick = {
                            navController.navigateSafely(Routes.Map.createRoute(site.documentId))
                        }
                    )
                }
                item { Spacer(Modifier.navigationBarsPadding()) }
            }
        }
    }
}

/** Cafés and restaurants are both "Food & Drink" (older entries used those values). */
private fun placeGroup(category: String): String = when (category.lowercase()) {
    "food", "cafe", "restaurant" -> "food"
    else -> category.lowercase()
}
