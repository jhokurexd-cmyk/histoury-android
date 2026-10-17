package com.histoury.app.ui.screens.explore

import com.histoury.app.theme.softShadow
import com.histoury.app.navigation.Routes
import com.histoury.app.ui.tutorial.TutorialStep
import com.histoury.app.ui.tutorial.TutorialTour
import com.histoury.app.ui.tutorial.tutorialTarget
import com.histoury.app.navigation.navigateSafely
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.histoury.app.data.model.Itinerary
import com.histoury.app.data.viewmodel.BuilderStep
import com.histoury.app.data.viewmodel.ItineraryViewModel
import com.histoury.app.theme.Danger
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Primary
import com.histoury.app.theme.PrimarySoft
import com.histoury.app.theme.SurfaceSoft
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary
import com.histoury.app.ui.components.BottomNavBarInset
import com.histoury.app.ui.components.BottomNavScaffold
import kotlin.math.roundToInt
import kotlin.math.ceil
import java.util.Date
import com.google.firebase.Timestamp

/**
 * The Itinerary Builder — replaces the old Explore tab. Shows saved
 * itineraries by default; "+" starts the creation wizard
 * (sites -> interests -> recommendations -> route), and tapping a saved
 * itinerary reopens it straight at the route overview.
 */
@Composable
fun ExploreScreen(
    navController: NavHostController,
    itineraryViewModel: ItineraryViewModel = viewModel()
) {

    val uiState by itineraryViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        // Deliberately autoResume = false (the default): opening the
        // Itinerary tab always shows the plain saved list, never jumps
        // straight into whichever trip happens to be IN_PROGRESS/PAUSED —
        // see loadSavedItineraries's autoResume kdoc for why that changed.
        // Resuming a trip goes through Home's "Continue Your Trip" card, or
        // tapping that itinerary's own card here.
        itineraryViewModel.loadSavedItineraries()

        // An open trip's Done badges / Resume state may have changed on the
        // Map or Arrival screens since this tab was last shown.
        itineraryViewModel.refreshOpenItineraryProgress()

        // Arrived here from Home's "Resume Trip": open that trip directly.
        com.histoury.app.data.itinerary.ItineraryOpenRequest.consume()?.let { id ->
            itineraryViewModel.openSavedItineraryById(id)
        }
    }

    if (uiState.isBuilderActive) {

        // Back walks the wizard one step at a time, keeping every
        // selection; only the first step closes the builder. The overview
        // registers its own handler (it may need to confirm unsaved edits).
        BackHandler(enabled = uiState.builderStep != BuilderStep.ROUTE_OVERVIEW) {
            if (!itineraryViewModel.stepBack()) itineraryViewModel.closeBuilder()
        }

        when (uiState.builderStep) {

            BuilderStep.SELECT_SITES -> SelectSitesStep(
                viewModel = itineraryViewModel,
                onClose = { itineraryViewModel.closeBuilder() }
            )

            BuilderStep.SELECT_INTERESTS -> SelectInterestsStep(
                viewModel = itineraryViewModel
            )

            BuilderStep.RECOMMENDATIONS -> RecommendationsStep(
                viewModel = itineraryViewModel
            )

            BuilderStep.ROUTE_OVERVIEW -> ItineraryRouteOverviewScreen(
                viewModel = itineraryViewModel,
                navController = navController
            )
        }

        return
    }

    var deleteTarget by remember { mutableStateOf<Itinerary?>(null) }

    // Long-press multi-select. Purely local UI state — nothing here needs
    // to survive process death or be seen outside this screen — derived
    // rather than a separate flag, so a selection can never exist while
    // isSelectionMode says otherwise.
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    val isSelectionMode = selectedIds.isNotEmpty()
    var showBulkDeleteConfirmation by remember { mutableStateOf(false) }

    var sortNewestFirst by remember { mutableStateOf(true) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Dropping a selected id the moment it's no longer in the loaded list
    // (a bulk delete just finished, or the single-item "..." delete ran
    // while it happened to be selected) keeps "N selected" honest instead
    // of counting an itinerary that no longer exists.
    LaunchedEffect(uiState.savedItineraries) {
        val liveIds = uiState.savedItineraries.map { it.documentId }.toSet()
        selectedIds = selectedIds.intersect(liveIds)
    }

    val sortedItineraries = remember(uiState.savedItineraries, sortNewestFirst) {
        uiState.savedItineraries.sortedBy { itinerarySortMillis(it) }
            .let { if (sortNewestFirst) it.reversed() else it }
    }

    // The footer's raised centre button is this screen's "new itinerary"
    // action once you're already on the Itinerary tab, so a separate
    // floating button here would be a second plus sitting directly on top
    // of the first.
    TutorialTour(
        id = "itinerary_list",
        steps = listOf(
            TutorialStep(
                target = null,
                title = "Your itineraries",
                body = "An itinerary is your plan for the day: the sites you want to see, in the best order, with a walking route and times."
            ),
            TutorialStep(
                target = "nav_" + Routes.Itinerary.route,
                title = "Create one",
                body = "On this tab the coral button becomes a +. Tap it to plan a new trip; Histoury walks you through it step by step."
            )
        )
    )

    // Once there's at least one saved trip: how to use the cards.
    TutorialTour(
        id = "itinerary_cards",
        enabled = uiState.savedItineraries.isNotEmpty(),
        steps = listOf(
            TutorialStep(
                target = "itin_first_card",
                title = "Your saved trips",
                body = "Tap a trip to open it: see its route, start it, or change it. Press and hold to select several and delete them."
            )
        )
    )

    BottomNavScaffold(
        navController = navController,
        onCenterReselected = { itineraryViewModel.startNewItinerary() },
        containerColor = SurfaceSoft
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            if (isSelectionMode) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceSoft)
                        .padding(horizontal = 20.dp)
                        .padding(top = 20.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel selection",
                        tint = TextPrimary,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { selectedIds = emptySet() }
                            .padding(4.dp)
                    )

                    Spacer(Modifier.width(12.dp))

                    Text(
                        text = "${selectedIds.size} selected",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )

                    // The bulk "select all in the upper right" control —
                    // doubles as "deselect all" once everything is already
                    // checked, so it never becomes a dead end.
                    Text(
                        text = if (selectedIds.size >= uiState.savedItineraries.size) {
                            "Deselect All"
                        } else {
                            "Select All"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Primary,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            selectedIds = if (selectedIds.size >= uiState.savedItineraries.size) {
                                emptySet()
                            } else {
                                uiState.savedItineraries.map { it.documentId }.toSet()
                            }
                        }
                    )

                    Spacer(Modifier.width(16.dp))

                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Delete selected itineraries",
                        tint = Danger,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { showBulkDeleteConfirmation = true }
                            .padding(4.dp)
                    )
                }

            } else {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceSoft)
                        .padding(horizontal = 20.dp)
                        .padding(top = 20.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.Top
                ) {

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "My Itineraries",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Plan your perfect Intramuros day",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }

                    // Newest-first / oldest-first, only worth showing once
                    // there's more than one itinerary to reorder.
                    if (uiState.savedItineraries.size > 1) {

                        Box {

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { showSortMenu = true }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (sortNewestFirst) "Newest" else "Oldest",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextSecondary
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Sort itineraries",
                                    tint = TextSecondary
                                )
                            }

                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Newest first") },
                                    onClick = {
                                        sortNewestFirst = true
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Oldest first") },
                                    onClick = {
                                        sortNewestFirst = false
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(10.dp))

                    // Same menu button as the Home, History and Gallery tabs.
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
                                navController.navigateSafely(com.histoury.app.navigation.Routes.Menu.route)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Menu,
                            contentDescription = "Menu",
                            tint = Primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            when {

                uiState.isLoadingSavedList -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Primary)
                    }
                }

                uiState.savedItineraries.isEmpty() && (uiState.isOffline || uiState.savedListLoadFailed) -> {
                    // No connection and nothing cached: say that, rather
                    // than "No itineraries yet", which reads as if the
                    // visitor's saved trips were gone.
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .clip(CircleShape)
                                .background(PrimarySoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                        Text(
                            text = if (uiState.isOffline) "You're offline" else "Couldn't load itineraries",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (uiState.isOffline) {
                                "Your itineraries couldn't be loaded. Connect to the internet and try again."
                            } else {
                                "Something went wrong loading your saved trips. They're still saved — try again."
                            },
                            fontSize = 13.sp,
                            color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { itineraryViewModel.loadSavedItineraries() }) {
                            Text("Try again", color = Primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                uiState.savedItineraries.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {

                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .clip(CircleShape)
                                .background(PrimarySoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        Spacer(Modifier.height(18.dp))

                        Text(
                            text = "No itineraries yet",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        Spacer(Modifier.height(6.dp))

                        Text(
                            text = "Pick the sites and spots you want to see, and we'll " +
                                "build an optimized walking route through Intramuros for you.",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        Spacer(Modifier.height(22.dp))

                        Text(
                            text = "Tap + to create your first itinerary",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Primary
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 16.dp,
                            bottom = 16.dp + BottomNavBarInset
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (uiState.isOffline) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(PrimarySoft)
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudOff,
                                        contentDescription = null,
                                        tint = Primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "You're offline — showing your last saved copy.",
                                        fontSize = 12.5.sp,
                                        color = TextPrimary
                                    )
                                }
                            }
                        }

                        items(sortedItineraries, key = { it.documentId }) { itinerary ->
                            // Only the top card is spotlit by the tutorial.
                            Box(
                                if (itinerary.documentId == sortedItineraries.firstOrNull()?.documentId) {
                                    Modifier.tutorialTarget("itin_first_card")
                                } else {
                                    Modifier
                                }
                            ) {
                                SavedItineraryCard(
                                    itinerary = itinerary,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = itinerary.documentId in selectedIds,
                                    onClick = {
                                        if (isSelectionMode) {
                                            selectedIds = if (itinerary.documentId in selectedIds) {
                                                selectedIds - itinerary.documentId
                                            } else {
                                                selectedIds + itinerary.documentId
                                            }
                                        } else {
                                            itineraryViewModel.openSavedItinerary(itinerary)
                                        }
                                    },
                                    onLongClick = {
                                        selectedIds = selectedIds + itinerary.documentId
                                    },
                                    onDeleteClick = { deleteTarget = itinerary }
                                )
                            }
                        }

                        item { Spacer(Modifier.height(60.dp)) }
                    }
                }
            }
        }
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete this itinerary?") },
            text = { Text("\"${deleteTarget?.name}\" will be permanently removed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget?.let { itineraryViewModel.deleteItinerary(it.documentId) }
                        deleteTarget = null
                    }
                ) {
                    Text("Delete", color = Danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    if (showBulkDeleteConfirmation) {
        val count = selectedIds.size
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirmation = false },
            title = { Text(if (count == 1) "Delete this itinerary?" else "Delete $count itineraries?") },
            text = { Text("They will be permanently removed. This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        itineraryViewModel.deleteItineraries(selectedIds)
                        showBulkDeleteConfirmation = false
                        selectedIds = emptySet()
                    }
                ) {
                    Text("Delete", color = Danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirmation = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SavedItineraryCard(
    itinerary: Itinerary,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDeleteClick: () -> Unit
) {

    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .softShadow(elevation = 3.dp, shape = RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(CardSurface)
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, Primary, RoundedCornerShape(20.dp))
                } else {
                    Modifier
                }
            )
            // Long-press enters multi-select and checks this card; a plain
            // tap opens it as before, or toggles its check once already in
            // selection mode — the "..." per-item delete stays available
            // outside selection mode as its own, separate path.
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onLongClick = onLongClick,
                onClick = onClick
            )
            .padding(16.dp)
    ) {

        Row(verticalAlignment = Alignment.Top) {

            Column(modifier = Modifier.weight(1f)) {

                Text(
                    text = itinerary.name.ifBlank { "Untitled Itinerary" },
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = when {
                        itinerary.status == "completed" -> completedDeletionLabel(itinerary)
                        itinerary.completedStopIds.isNotEmpty() ->
                            "${itinerary.completedStopIds.size}/${itinerary.stops.size} stops complete"
                        else -> "${itinerary.stops.size} stop${if (itinerary.stops.size == 1) "" else "s"}"
                    },
                    fontSize = 12.5.sp,
                    color = TextSecondary
                )
            }

            if (isSelectionMode) {

                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (isSelected) "Selected" else "Not selected",
                    tint = if (isSelected) Primary else TextSecondary,
                    modifier = Modifier.size(24.dp)
                )

            } else {

                Box {

                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = TextSecondary,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                showMenu = true
                            }
                            .padding(4.dp)
                    )

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Delete", color = Danger) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = Danger
                                )
                            },
                            onClick = {
                                showMenu = false
                                onDeleteClick()
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceSoft)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            ItineraryStatMini(
                icon = Icons.Default.DirectionsWalk,
                label = formatDistance(itinerary.totalDistanceMeters)
            )

            ItineraryStatMini(
                icon = Icons.Default.AccessTime,
                label = if (itinerary.status == "completed") {
                    actualTripDurationLabel(itinerary)
                } else {
                    "${itinerary.totalWalkingMinutes + itinerary.totalVisitMinutes} min estimated"
                }
            )
        }
    }
}

/**
 * Sort key for the "Newest first" / "Oldest first" filter on the saved
 * list: when the itinerary was created, falling back to when it was last
 * updated for a legacy document saved before `createdAt` existed.
 */
private fun itinerarySortMillis(itinerary: Itinerary): Long {
    fun millis(value: Any?): Long? = when (value) {
        is Timestamp -> value.toDate().time
        is Date -> value.time
        else -> null
    }
    return millis(itinerary.createdAt) ?: millis(itinerary.updatedAt) ?: 0L
}

private fun completedDeletionLabel(itinerary: Itinerary): String {
    val deleteAtMillis = when (val value = itinerary.deleteAt) {
        is Timestamp -> value.toDate().time
        is Date -> value.time
        else -> null
    }

    if (deleteAtMillis == null) {
        return "Done · Deleted 48 hours after completion"
    }

    val remainingHours = ceil(
        (deleteAtMillis - System.currentTimeMillis()).coerceAtLeast(0L) / 3_600_000.0
    ).toInt()

    return "Done · Auto-deletes in ${remainingHours.coerceAtLeast(1)}h"
}

private fun actualTripDurationLabel(itinerary: Itinerary): String {
    fun millis(value: Any?): Long? = when (value) {
        is Timestamp -> value.toDate().time
        is Date -> value.time
        else -> null
    }

    val started = millis(itinerary.startedAt)
    val completed = millis(itinerary.completedAt)
    if (started == null || completed == null || completed < started) {
        return "Trip completed"
    }

    val minutes = ((completed - started) / 60_000L).coerceAtLeast(1L).toInt()
    val hours = minutes / 60
    val remainder = minutes % 60

    return when {
        hours == 0 -> "$minutes min actual"
        remainder == 0 -> "${hours}h actual"
        else -> "${hours}h ${remainder}m actual"
    }
}

@Composable
private fun ItineraryStatMini(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Primary,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
    }
}

internal fun formatDistance(distanceMeters: Double): String {
    return if (distanceMeters < 1000) {
        "${distanceMeters.roundToInt()} m"
    } else {
        "%.1f km".format(distanceMeters / 1000.0)
    }
}

/** Shared header for every builder step: back button, title, subtitle, and an optional step counter. */
@Composable
internal fun WizardStepHeader(
    title: String,
    subtitle: String,
    stepLabel: String? = null,
    onBackClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceSoft)
            .padding(horizontal = 20.dp)
            // Compact: on the builder steps the list below needs the room.
            .padding(top = 12.dp, bottom = 8.dp)
    ) {

        Row(verticalAlignment = Alignment.CenterVertically) {

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .softShadow(elevation = 2.dp, shape = CircleShape)
                    .clip(CircleShape)
                    .background(CardSurface)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBackClick
                    ),
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

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            if (stepLabel != null) {
                androidx.compose.material3.Surface(
                    shape = RoundedCornerShape(50),
                    color = PrimarySoft
                ) {
                    Text(
                        text = stepLabel,
                        color = Primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}
