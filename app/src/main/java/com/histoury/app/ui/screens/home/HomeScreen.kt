@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.histoury.app.ui.screens.home

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.histoury.app.data.repository.BackgroundLocationPromptPreference
import com.histoury.app.data.viewmodel.GeofenceSetupViewModel
import com.histoury.app.data.viewmodel.HomeViewModel
import com.histoury.app.data.viewmodel.MenuViewModel
import com.histoury.app.data.viewmodel.WeatherViewModel
import com.histoury.app.navigation.Routes
import com.histoury.app.ui.components.BottomNavBarInset
import com.histoury.app.ui.components.BottomNavScaffold
import com.histoury.app.ui.components.ContentCategory
import com.histoury.app.ui.components.ContentCategoryChips
import com.histoury.app.ui.components.FeaturedSiteCard
import com.histoury.app.ui.components.ExploreGridCard
import com.histoury.app.ui.components.PopularSiteCard
import com.histoury.app.ui.components.SearchBar
import com.histoury.app.ui.components.TagFilterDialog
import com.histoury.app.ui.components.WeatherWidget
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Primary
import com.histoury.app.theme.PrimarySoft
import com.histoury.app.theme.SurfaceSoft
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary

@Composable
fun HomeScreen(
    navController: NavHostController,
    homeViewModel: HomeViewModel = viewModel(),
    geofenceSetupViewModel: GeofenceSetupViewModel = viewModel(),
    menuViewModel: MenuViewModel = viewModel(),
    weatherViewModel: WeatherViewModel = viewModel()
) {

    val sites by homeViewModel.sites.collectAsState()

    val isLoading by homeViewModel.isLoading.collectAsState()

    val searchQuery by homeViewModel.searchQuery.collectAsState()

    val allTags by homeViewModel.allTags.collectAsState()

    val selectedTagIds by homeViewModel.selectedTagIds.collectAsState()

    val places by homeViewModel.places.collectAsState()

    val selectedPlaceCategory by homeViewModel.selectedPlaceCategory.collectAsState()

    val selectedHomeSection by homeViewModel.selectedHomeSection.collectAsState()

    val userState by menuViewModel.uiState.collectAsState()

    val weather by weatherViewModel.weather.collectAsState()

    val isWeatherLoading by weatherViewModel.isLoading.collectAsState()

    var showTagFilterDialog by remember { mutableStateOf(false) }

    var showBackgroundLocationExplanation by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        menuViewModel.loadUser()
    }

    // Shared by the first-launch permission flow below and the resume
    // observer further down, so both agree on exactly what "permitted"
    // means before touching Firestore/Play Services.
    fun refreshGeofenceRegistrationIfPermitted() {

        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFineLocation) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            val hasBackgroundLocation = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (hasBackgroundLocation) {
                geofenceSetupViewModel.registerAllSiteGeofences()
            }

        } else {
            geofenceSetupViewModel.registerAllSiteGeofences()
        }
    }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            geofenceSetupViewModel.registerAllSiteGeofences()
        }
    }

    val backgroundLocationSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            geofenceSetupViewModel.registerAllSiteGeofences()
        }
    }

    fun requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            showBackgroundLocationExplanation = true
            BackgroundLocationPromptPreference.markAsked(context)
        } else {
            backgroundLocationLauncher.launch(
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
        }
    }

    if (showBackgroundLocationExplanation) {
        AlertDialog(
            onDismissRequest = { showBackgroundLocationExplanation = false },
            title = { Text("Allow background location") },
            text = {
                Text(
                    "Histoury uses background location to notify you when you arrive at a historical site. " +
                        "On the next screen, choose Location, then Allow all the time."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBackgroundLocationExplanation = false
                        backgroundLocationSettingsLauncher.launch(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }
                ) {
                    Text("Open settings")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBackgroundLocationExplanation = false }
                ) {
                    Text("Not now")
                }
            }
        )
    }

    val fineLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                requestBackgroundLocation()
            } else {
                geofenceSetupViewModel.registerAllSiteGeofences()
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Arrival notifications simply won't show if this is denied. */ }

    LaunchedEffect(Unit) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            val hasNotificationPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasNotificationPermission) {
                notificationPermissionLauncher.launch(
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }
        }

        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFineLocation) {
            fineLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return@LaunchedEffect
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            val hasBackgroundLocation = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasBackgroundLocation && !BackgroundLocationPromptPreference.hasAsked(context)) {
                requestBackgroundLocation()
            }
        }

        refreshGeofenceRegistrationIfPermitted()
    }

    // Re-syncs geofence registration every time Histoury comes back to the
    // foreground, not just once on first Home launch. Without this, an
    // admin editing a geofence's radius or location in the web panel had no
    // way to reach a phone that already had the app open — registration
    // used to happen exactly once per app process and was never revisited.
    // registerAllSiteGeofences() throttles itself, so switching back and
    // forth between this app and something else doesn't spam Firestore or
    // Play Services.
    DisposableEffect(lifecycleOwner) {

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshGeofenceRegistrationIfPermitted()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (showTagFilterDialog) {
        TagFilterDialog(
            allTags = allTags,
            appliedTagIds = selectedTagIds,
            onApply = { tagIds ->
                homeViewModel.onApplyTagFilters(tagIds)
            },
            onDismiss = {
                showTagFilterDialog = false
            }
        )
    }

    // Maps a unified Home chip straight onto the existing HomeViewModel
    // state (section + place-category), so all filtering/search/tag logic
    // stays exactly as it was — only the chip UI changed.
    val activeCategoryKey = if (selectedHomeSection == "sites") "sites" else selectedPlaceCategory

    // Computed here (composable scope) rather than inside the LazyColumn
    // builder, since that builder lambda isn't a @Composable context and
    // can't call remember() directly.
    val popularSites = remember(sites) {
        sites.sortedByDescending { it.averageRating }.take(6)
    }

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
                    .padding(top = 20.dp, bottom = 16.dp)
            ) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(PrimarySoft),
                        contentAlignment = Alignment.Center
                    ) {
                        if (userState.photoUrl.isNotBlank()) {
                            AsyncImage(
                                model = userState.photoUrl,
                                contentDescription = "Profile photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    // Greeting and weather share whatever space is left after
                    // the avatar and the menu button. Grouping them in their
                    // own weighted Row is what keeps the weather pinned to
                    // its spot: the widget is measured first at its natural
                    // width, and the greeting Column gets the remainder, so a
                    // long name or a large system font scale makes the name
                    // ellipsize instead of shoving the weather sideways.
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Column(modifier = Modifier.weight(1f, fill = false)) {

                            val firstName = userState.displayName
                                .trim()
                                .split(" ")
                                .firstOrNull()
                                ?.ifBlank { null }
                                ?: "Explorer"

                            // Clamped to one line: with the weather widget
                            // sharing this row, a long display name would
                            // otherwise wrap and stretch the header.
                            Text(
                                text = "Hello, $firstName",
                                color = TextPrimary,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = Primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    text = "Intramuros, Manila",
                                    color = TextSecondary,
                                    fontSize = 12.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(Modifier.width(10.dp))

                        // Current conditions for Intramuros. Tapping refetches —
                        // useful when the app opened offline and the first
                        // request came back empty.
                        WeatherWidget(
                            weather = weather,
                            isLoading = isWeatherLoading,
                            onRetry = { weatherViewModel.loadWeather() }
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .shadow(elevation = 2.dp, shape = CircleShape)
                            .clip(CircleShape)
                            .background(CardSurface)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    navController.navigate(Routes.Menu.route)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Main menu",
                            tint = Primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                SearchBar(
                    value = searchQuery,
                    onValueChange = { query ->
                        homeViewModel.onSearchQueryChange(query)
                    },
                    onFilterClick = {
                        showTagFilterDialog = true
                    },
                    hasActiveFilters = selectedTagIds.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Pinned category switch — Historical Sites / Food & Drink /
            // Parks / Hotels / Souvenir Shops / Schools / Banks. Always
            // visible regardless of scroll; scrolls horizontally since it
            // no longer fits one screen width.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                ContentCategoryChips(
                    categories = ContentCategory.entries.filter { it != ContentCategory.ALL },
                    selectedKey = activeCategoryKey,
                    onSelect = { category ->
                        if (category == ContentCategory.SITES) {
                            homeViewModel.onHomeSectionSelected("sites")
                        } else {
                            homeViewModel.onHomeSectionSelected("places")
                            homeViewModel.onPlaceCategorySelected(category.key)
                        }
                    }
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SurfaceSoft),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 4.dp,
                    // Content runs under the floating bar, so the list
                    // scrolls its last card clear of it rather than
                    // stopping short.
                    bottom = 24.dp + BottomNavBarInset
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                when {

                    isLoading -> {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = Primary
                                )
                            }
                        }
                    }

                    selectedHomeSection == "sites" && sites.isEmpty() -> {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (searchQuery.isBlank() && selectedTagIds.isEmpty()) {
                                        "No historical sites available yet"
                                    } else {
                                        "No historical sites match your search"
                                    },
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    selectedHomeSection == "sites" -> {

                        item {
                            Text(
                                text = "Popular Historical Sites",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        item {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(popularSites) { site ->
                                    PopularSiteCard(
                                        title = site.siteName,
                                        location = site.location,
                                        rating = site.averageRating,
                                        imageUrl = site.featuredImage,
                                        onClick = {
                                            navController.navigate(
                                                Routes.Map.createRoute(site.documentId)
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        item {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Recommended For You",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        items(sites) { site ->
                            FeaturedSiteCard(
                                title = site.siteName,
                                rating = site.averageRating,
                                imageUrl = site.featuredImage,
                                onViewClick = {
                                    navController.navigate(
                                        Routes.Map.createRoute(site.documentId)
                                    )
                                }
                            )
                        }
                    }

                    // Food & Drink / Parks / Schools: places filtered by
                    // category, shown as a 2-column grid (chunked manually
                    // since LazyVerticalGrid can't nest inside this
                    // LazyColumn) with the same card style as the Explore
                    // grid.
                    else -> {

                        item {
                            Text(
                                text = "Explore Intramuros",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        if (places.isEmpty()) {
                            item {
                                Text(
                                    text = if (selectedPlaceCategory == "all") {
                                        "No places added yet."
                                    } else {
                                        "No places in this category yet."
                                    },
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            items(places.chunked(2)) { rowPlaces ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    rowPlaces.forEach { place ->
                                        ExploreGridCard(
                                            title = place.name,
                                            subtitle = place.location,
                                            badgeText = place.categoryLabel,
                                            imageUrl = place.featuredImage,
                                            rating = null,
                                            onClick = {
                                                navController.navigate(
                                                    Routes.PlaceDetails.createRoute(place.documentId)
                                                )
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    // Odd count on the last row: keep the
                                    // lone card at half width instead of
                                    // stretching it full-width.
                                    if (rowPlaces.size == 1) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
