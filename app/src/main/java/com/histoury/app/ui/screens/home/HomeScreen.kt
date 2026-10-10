@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.histoury.app.ui.screens.home

import com.histoury.app.ui.tutorial.TutorialStep
import com.histoury.app.ui.tutorial.TutorialTour
import com.histoury.app.ui.tutorial.tutorialTarget
import com.histoury.app.navigation.navigateSafely
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.histoury.app.ui.components.HeroIconButton
import com.histoury.app.ui.components.HistouryPullToRefresh
import com.histoury.app.ui.components.TurnOnLocationDialog
import com.histoury.app.util.DeviceLocation
import com.histoury.app.ui.components.rememberDeviceLocationEnabled
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.histoury.app.data.model.Itinerary
import com.histoury.app.data.model.HistoricalSite
import com.histoury.app.util.UserDistance
import com.histoury.app.data.offline.ResourcePack
import com.histoury.app.ui.components.HomeNotificationsSeen
import com.histoury.app.ui.components.HomeNotificationsSheet
import com.histoury.app.ui.components.HomeNotificationsState
import com.histoury.app.ui.components.NotificationBellButton
import com.histoury.app.data.repository.BackgroundLocationPromptPreference
import com.histoury.app.data.viewmodel.GeofenceSetupViewModel
import com.histoury.app.data.viewmodel.HomeViewModel
import com.histoury.app.data.viewmodel.MenuViewModel
import com.histoury.app.data.viewmodel.WeatherViewModel
import com.histoury.app.navigation.Routes
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.histoury.app.navigation.navigateToTab
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

    val isRefreshing by homeViewModel.isRefreshing.collectAsState()

    // Pull-to-refresh also gets a fresh location fix (for "Nearest To You",
    // the distances and the area under the greeting), which can take a few
    // seconds; the spinner stays until that's done too.
    var isLocationRefreshing by remember { mutableStateOf(false) }

    val searchQuery by homeViewModel.searchQuery.collectAsState()

    val allTags by homeViewModel.allTags.collectAsState()

    val selectedTagIds by homeViewModel.selectedTagIds.collectAsState()

    val places by homeViewModel.places.collectAsState()

    val selectedPlaceCategory by homeViewModel.selectedPlaceCategory.collectAsState()

    val selectedHomeSection by homeViewModel.selectedHomeSection.collectAsState()

    val resumableItinerary by homeViewModel.resumableItinerary.collectAsState()

    val plannedItineraries by homeViewModel.plannedItineraries.collectAsState()

    // "Download Resources" pop-up: the first time only (and again when a new
    // AR experience is published), while some AR experience isn't on the
    // phone yet. Closing it is respected.
    com.histoury.app.ui.components.ResourcePackPrompt(
        onChoose = { navController.navigateSafely(Routes.Downloads.route) }
    )

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

    // The area shown under the greeting. It was hardcoded to "Intramuros,
    // Manila" for everyone, wherever they were. Now: the location saved on
    // the visitor's profile if they've set one, otherwise the area the
    // phone is in (e.g. "Ermita, Manila"), and only then the old default.
    var deviceArea by remember { mutableStateOf("") }

    val areaScope = rememberCoroutineScope()

    // Runs on every resume (see the lifecycle observer below), which also
    // covers the moment the location prompt closes after being allowed.
    fun refreshDeviceArea() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            areaScope.launch {
                currentAreaName(context)?.let { deviceArea = it }
            }
            // Also the position behind the "1.2 km away" labels and
            // "Nearest To You".
            areaScope.launch { UserDistance.refresh(context) }
        }
        // A site downloaded from Offline Downloads comes off the bell.
        areaScope.launch { runCatching { ResourcePack.missingSites(context) } }
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
        // Marked on every version: Android 10 used to skip this, so a
        // declined prompt came back on every Home launch.
        BackgroundLocationPromptPreference.markAsked(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            showBackgroundLocationExplanation = true
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

    // Location permission alone isn't enough: with the phone's Location
    // toggle off, nearby distances and arrival notifications never work.
    // Offered once per launch, and only once the permission is in place
    // (so it never stacks on the first-launch permission prompts).
    val locationServicesOn = rememberDeviceLocationEnabled()
    var showLocationOffDialog by remember { mutableStateOf(false) }
    var locationWasOff by remember { mutableStateOf(false) }

    LaunchedEffect(locationServicesOn) {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!locationServicesOn && hasFineLocation && !DeviceLocation.homePromptShown) {
            DeviceLocation.homePromptShown = true
            showLocationOffDialog = true
        }

        if (!locationServicesOn) {
            locationWasOff = true
        } else if (locationWasOff) {
            // Back on: refresh what was missing while it was off.
            locationWasOff = false
            showLocationOffDialog = false
            refreshGeofenceRegistrationIfPermitted()
            UserDistance.refresh(context)
        }
    }

    if (showLocationOffDialog) {
        TurnOnLocationDialog(
            message = "Your phone's location is off. Turn it on so Histoury can show sites near you and tell you when you arrive at one.",
            onDismiss = { showLocationOffDialog = false }
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

    // Location half of the first-launch permission flow.
    fun continueWithLocationPermissions() {

        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFineLocation) {
            fineLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
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

    // The two system prompts are asked one after the other. Launching the
    // location request while the notification prompt was still on screen
    // made Android drop it as an automatic "deny" (only one permission
    // dialog can be pending), so on Android 13+ first launch never actually
    // asked for location — arrival detection and live distances silently
    // stayed off.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        // Arrival notifications simply won't show if this is denied.
        continueWithLocationPermissions()
    }

    LaunchedEffect(Unit) {

        val needsNotificationPrompt = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED

        if (needsNotificationPrompt) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            continueWithLocationPermissions()
        }
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
                refreshDeviceArea()
                // Catches a trip paused (or finished) while this Home
                // instance sat in the background — not just a fresh Home
                // created via popUpTo/launchSingleTop navigation.
                homeViewModel.refreshResumableItinerary()
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
        sites.sortedByDescending { it.averageRating }.take(HOME_ROW_LIMIT)
    }

    // "All Historical Sites": A to Z, the same order as its View all page.
    val allSitesSorted = remember(sites) { sites.sortedBy { it.siteName.lowercase() } }

    val userPosition by UserDistance.position.collectAsState()

    // Closest sites first; empty (and the section hidden) until the
    // phone's location is known.
    val nearestSites = remember(sites, userPosition) {
        if (userPosition == null) emptyList()
        else sites
            .filter { UserDistance.metersTo(it.latitude, it.longitude, userPosition) != null }
            .sortedBy { UserDistance.metersTo(it.latitude, it.longitude, userPosition) }
            .take(HOME_ROW_LIMIT)
    }

    fun distanceOf(latitude: Double?, longitude: Double?): String? =
        UserDistance.labelFor(latitude, longitude, userPosition)

    // Searching or filtering shows every match; otherwise Home only previews
    // a few per section and "View all" opens the rest.
    val isSearchingSites = searchQuery.isNotBlank() || selectedTagIds.isNotEmpty()
    val isSearchingPlaces = searchQuery.isNotBlank()

    // ----- notification bell: resume trip + download resources -----

    val resourcesMissing by ResourcePack.missing.collectAsState()
    val resourcePackState by ResourcePack.state.collectAsState()
    val resourceDownloading = resourcePackState as? ResourcePack.State.Downloading
    val tripToResume = resumableItinerary

    val notifications = HomeNotificationsState(
        locationOff = !locationServicesOn && ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED,
        tripTitle = tripToResume?.name?.ifBlank { "Your trip" },
        tripDetail = tripToResume?.let { homeViewModel.resumableStopProgressText(it) },
        savedTripCount = plannedItineraries.size,
        savedTripNames = plannedItineraries.take(2).map { it.name.ifBlank { "Untitled trip" } },
        missingDownloads = resourcesMissing?.size ?: 0,
        downloadProgress = resourceDownloading?.overall,
        downloadingName = resourceDownloading?.currentName
    )

    // What's in the bell right now. The red dot shows until the visitor has
    // opened the bell with exactly these items in it.
    val notificationsSignature = listOf(
        if (notifications.locationOff) "location-off" else "",
        tripToResume?.documentId.orEmpty(),
        // Saving (or removing) a trip changes the set and brings the dot back.
        "saved:" + plannedItineraries.joinToString(",") { it.documentId },
        if (notifications.missingDownloads > 0) "downloads" else "",
        if (resourceDownloading != null) "downloading" else ""
    ).joinToString("|")

    var showNotifications by remember { mutableStateOf(false) }

    val downloadGate = com.histoury.app.ui.components.rememberDownloadGate()

    // Knows which AR sites aren't downloaded yet, for the bell (the pop-up
    // only checks once per app open).
    LaunchedEffect(Unit) {
        if (ResourcePack.missing.value == null) {
            runCatching { ResourcePack.missingSites(context) }
        }
    }

    if (showNotifications) {
        HomeNotificationsSheet(
            state = notifications,
            onTurnOnLocation = {
                showNotifications = false
                // Straight to the system's Location page, where the toggle is.
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
            },
            onResumeTrip = {
                showNotifications = false
                tripToResume?.let { trip ->
                    // Opens this exact trip in the Itinerary tab, using the
                    // same tab-switch options as the bottom bar.
                    com.histoury.app.data.itinerary.ItineraryOpenRequest.request(trip.documentId)
                    navController.navigateToTab(Routes.Itinerary.route)
                }
            },
            onViewSavedTrips = {
                showNotifications = false
                // Same tab switch as the bottom bar's Itinerary button.
                navController.navigateToTab(Routes.Itinerary.route)
            },
            onDownloadAll = {
                showNotifications = false
                val sites = resourcesMissing.orEmpty()
                if (sites.isNotEmpty()) {
                    // Same Wi-Fi / mobile-data rules as Offline Downloads.
                    // With no connection the sites simply wait and start
                    // by themselves once online.
                    downloadGate.request(
                        bulk = true,
                        size = { ResourcePack.estimateBytes(sites) }
                    ) { allowMobile ->
                        ResourcePack.downloadAll(context, sites, allowMobile)
                        navController.navigateSafely(Routes.Downloads.route)
                    }
                }
            },
            onChooseDownloads = {
                showNotifications = false
                navController.navigateSafely(Routes.Downloads.route)
            },
            onDismiss = { showNotifications = false }
        )
    }

    // First visit: a spotlight tour of Home and the bottom bar.
    TutorialTour(
        id = "home",
        enabled = !isLoading,
        steps = listOf(
            TutorialStep(
                target = null,
                title = "Welcome to Histoury",
                body = "Here's a quick tour so you know where everything is. Tap Next (or anywhere) to continue, or Skip tour anytime."
            ),
            TutorialStep(
                target = "home_search",
                title = "Search",
                body = "Type a site or place name to find it. The filter button narrows the list by type, like churches or museums."
            ),
            TutorialStep(
                target = "home_categories",
                title = "Choose what to browse",
                body = "Historical Sites, Food & Drink, Parks, Hotels and more. Tap one to show only that kind."
            ),
            TutorialStep(
                target = "home_sites",
                title = "Open a site",
                body = "Tap any card to see the site on the map, get directions and read its history."
            ),
            TutorialStep(
                target = "home_weather",
                title = "Weather now",
                body = "Current weather in Intramuros, so you can plan your walk. Tap it to update."
            ),
            TutorialStep(
                target = "home_bell",
                title = "Notifications",
                body = "Your trip to continue, saved itineraries, downloads and alerts (like location being off). A red dot means something new."
            ),
            TutorialStep(
                target = "home_menu",
                title = "Menu",
                body = "Your profile, favorites, offline downloads, settings and help. You can replay these tutorials from here."
            ),
            TutorialStep(
                target = "nav_" + Routes.MapOverview.route,
                title = "Map",
                body = "Every historical site on one map of Intramuros."
            ),
            TutorialStep(
                target = "nav_" + Routes.Itinerary.route,
                title = "Plan a trip",
                body = "Pick the sites you want to visit and Histoury builds a walking route with times."
            ),
            TutorialStep(
                target = "nav_" + Routes.History.route,
                title = "Visit History",
                body = "The sites you've been to. A site's full story unlocks once you visit it in person."
            ),
            TutorialStep(
                target = "nav_" + Routes.Gallery.route,
                title = "Gallery",
                body = "Photos you take in AR are kept here."
            ),
            TutorialStep(
                target = null,
                title = "Pull down to refresh",
                body = "On Home, drag the list down to reload sites, distances and the weather. That's it, enjoy exploring!"
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
                                    text = userState.location
                                        .ifBlank { deviceArea }
                                        .ifBlank { "Intramuros, Manila" },
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
                        Box(Modifier.tutorialTarget("home_weather")) {
                            WeatherWidget(
                                weather = weather,
                                isLoading = isWeatherLoading,
                                onRetry = { weatherViewModel.loadWeather() }
                            )
                        }
                    }

                    Spacer(Modifier.width(10.dp))

                    Box(Modifier.tutorialTarget("home_bell")) {
                        NotificationBellButton(
                            count = notifications.count,
                            hasUnseen = HomeNotificationsSeen.signature != notificationsSignature,
                            onClick = {
                                HomeNotificationsSeen.signature = notificationsSignature
                                showNotifications = true
                            }
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    // Same 44dp round button as every other header/overlay control.
                    Box(Modifier.tutorialTarget("home_menu")) {
                        HeroIconButton(
                            icon = Icons.Default.Menu,
                            contentDescription = "Main menu",
                            onClick = { navController.navigateSafely(Routes.Menu.route) }
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
                    modifier = Modifier.tutorialTarget("home_search").fillMaxWidth()
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
                Box(Modifier.tutorialTarget("home_categories")) {
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
            }

            // Pull down at the top of the list to reload, like most apps.
            HistouryPullToRefresh(
                isRefreshing = isRefreshing || isLocationRefreshing,
                onRefresh = {
                    homeViewModel.refresh()
                    weatherViewModel.loadWeather()
                    menuViewModel.loadUser()
                    if (!isLocationRefreshing) {
                        areaScope.launch {
                            isLocationRefreshing = true
                            try {
                                // A NEW fix, not the cached one: after
                                // moving, the old one still put "Nearest To
                                // You" where the visitor used to be.
                                UserDistance.refresh(context, fresh = true)
                                currentAreaName(context, UserDistance.position.value)
                                    ?.let { deviceArea = it }
                                runCatching { ResourcePack.missingSites(context) }
                            } finally {
                                isLocationRefreshing = false
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) {
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

                    // "Continue Your Trip" lives under the notification bell now,
                    // instead of taking up the top of the page.

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
                                    val isFiltered = searchQuery.isNotBlank() || selectedTagIds.isNotEmpty()

                                    Text(
                                        text = if (!isFiltered) {
                                            "No historical sites to show right now"
                                        } else {
                                            "No historical sites match your search"
                                        },
                                        color = TextSecondary
                                    )

                                    // An empty, unfiltered list almost always means
                                    // the first load failed (offline) — give a way
                                    // out besides restarting the app.
                                    if (!isFiltered) {
                                        TextButton(onClick = { homeViewModel.loadHomeData() }) {
                                            Text("Try again", color = Primary, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        TextButton(onClick = {
                                            homeViewModel.onSearchQueryChange("")
                                            homeViewModel.onResetTagFilters()
                                        }) {
                                            Text("Clear search & filters", color = Primary, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        selectedHomeSection == "sites" && isSearchingSites -> {

                            item {
                                HomeSectionHeader(
                                    title = if (sites.size == 1) "1 result" else "${sites.size} results"
                                )
                            }

                            items(sites, key = { "result_" + it.documentId }) { site ->
                                FeaturedSiteCard(
                                    title = site.siteName,
                                    rating = site.averageRating,
                                    imageUrl = site.featuredImage,
                                    siteDocumentId = site.documentId,
                                    distanceText = distanceOf(site.latitude, site.longitude),
                                    onViewClick = {
                                        navController.navigateSafely(
                                            Routes.Map.createRoute(site.documentId)
                                        )
                                    }
                                )
                            }
                        }

                        selectedHomeSection == "sites" -> {

                            item {
                                HomeSectionHeader(
                                    title = "Popular Historical Sites",
                                    // The full ranking lives on the Site Rankings page.
                                    onViewAll = {
                                        navController.navigateSafely(Routes.Rankings.route)
                                    }
                                )
                            }

                            item {
                                Box(Modifier.tutorialTarget("home_sites")) {
                                    SiteCarousel(
                                        sites = popularSites,
                                        keyPrefix = "popular_",
                                        distanceOf = ::distanceOf,
                                        onOpen = { site ->
                                            navController.navigateSafely(Routes.Map.createRoute(site.documentId))
                                        }
                                    )
                                }
                            }

                            if (nearestSites.isNotEmpty()) {

                                item {
                                    HomeSectionHeader(
                                        title = "Nearest To You",
                                        onViewAll = {
                                            navController.navigateSafely(Routes.SiteList.createRoute("nearest"))
                                        }
                                    )
                                }

                                item {
                                    SiteCarousel(
                                        sites = nearestSites,
                                        keyPrefix = "nearest_",
                                        distanceOf = ::distanceOf,
                                        onOpen = { site ->
                                            navController.navigateSafely(Routes.Map.createRoute(site.documentId))
                                        }
                                    )
                                }
                            }

                            item {
                                HomeSectionHeader(
                                    title = "All Historical Sites",
                                    onViewAll = if (sites.size > HOME_ALL_SITES_LIMIT) {
                                        { navController.navigateSafely(Routes.SiteList.createRoute("all")) }
                                    } else null
                                )
                            }

                            items(allSitesSorted.take(HOME_ALL_SITES_LIMIT), key = { "featured_" + it.documentId }) { site ->
                                FeaturedSiteCard(
                                    title = site.siteName,
                                    rating = site.averageRating,
                                    imageUrl = site.featuredImage,
                                    siteDocumentId = site.documentId,
                                    distanceText = distanceOf(site.latitude, site.longitude),
                                    onViewClick = {
                                        navController.navigateSafely(
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

                            val shownPlaces = if (isSearchingPlaces) places else places.take(HOME_PLACES_LIMIT)

                            item {
                                HomeSectionHeader(
                                    title = "Explore Intramuros",
                                    onViewAll = if (!isSearchingPlaces && places.size > shownPlaces.size) {
                                        {
                                            navController.navigateSafely(
                                                Routes.SiteList.createRoute("places:$selectedPlaceCategory")
                                            )
                                        }
                                    } else null
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
                                items(shownPlaces.chunked(2)) { rowPlaces ->
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
                                                distanceText = distanceOf(place.latitude, place.longitude),
                                                onClick = {
                                                    navController.navigateSafely(
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
}

/**
 * A short, human name for where the phone is — "Ermita, Manila" — from its
 * last known location. Null when there's no fix or no geocoder; the caller
 * falls back to a default rather than showing nothing.
 */
@android.annotation.SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
private suspend fun currentAreaName(
    context: android.content.Context,
    // A fix already in hand (pull-to-refresh's fresh one). Without it, the
    // phone's last known location is used, which can be out of date.
    at: com.google.android.gms.maps.model.LatLng? = null
): String? {
    return try {
        val location = at
            ?: com.google.android.gms.location.LocationServices
                .getFusedLocationProviderClient(context)
                .lastLocation
                .await()
                ?.let { com.google.android.gms.maps.model.LatLng(it.latitude, it.longitude) }
            ?: return null

        if (!android.location.Geocoder.isPresent()) return null

        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val address = android.location.Geocoder(context)
                .getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()
                ?: return@withContext null

            val area = address.subLocality ?: address.locality
            val city = address.locality?.takeIf { it != area } ?: address.subAdminArea

            listOfNotNull(area, city)
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(", ")
                .ifBlank { null }
        }
    } catch (e: Exception) {
        null
    }
}

/** Horizontal margin of Home's content column. */
private val HomeContentMargin = 20.dp

/**
 * Lets a child (a horizontal carousel) extend [bleed] past both sides of a
 * padded parent, so it can scroll to the screen edges.
 */
private fun Modifier.bleedHorizontally(bleed: Dp): Modifier = layout { measurable, constraints ->
    val extra = bleed.roundToPx()
    val width = constraints.maxWidth + extra * 2
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) {
        placeable.place(-extra, 0)
    }
}

/** How many cards a Home carousel previews before "View all". */
private const val HOME_ROW_LIMIT = 6

/** How many "All Historical Sites" cards Home shows before "View all". */
private const val HOME_ALL_SITES_LIMIT = 4

/** How many places Home shows in a category before "View all". */
private const val HOME_PLACES_LIMIT = 6

/**
 * A Home section title with an optional "View all →" on the right, which
 * opens the full list behind a section that only shows a few items.
 */
@Composable
private fun HomeSectionHeader(
    title: String,
    onViewAll: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
        if (onViewAll != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onViewAll)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "View all",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

/** An edge-to-edge row of [PopularSiteCard]s, shared by Home's carousels. */
@Composable
private fun SiteCarousel(
    sites: List<HistoricalSite>,
    keyPrefix: String,
    distanceOf: (Double?, Double?) -> String?,
    onOpen: (HistoricalSite) -> Unit
) {
    // Runs edge to edge (cards slide off the screen edge, not a 20dp margin)
    // while the first card still lines up with the headings; vertical
    // padding keeps the card shadows from clipping.
    LazyRow(
        modifier = Modifier.bleedHorizontally(HomeContentMargin),
        contentPadding = PaddingValues(
            horizontal = HomeContentMargin,
            vertical = 6.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(sites, key = { keyPrefix + it.documentId }) { site ->
            PopularSiteCard(
                title = site.siteName,
                location = site.location,
                rating = site.averageRating,
                imageUrl = site.featuredImage,
                siteDocumentId = site.documentId,
                distanceText = distanceOf(site.latitude, site.longitude),
                onClick = { onOpen(site) }
            )
        }
    }
}
