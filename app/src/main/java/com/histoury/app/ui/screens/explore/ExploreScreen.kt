package com.histoury.app.ui.screens.explore

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
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
        itineraryViewModel.loadSavedItineraries()
    }

    if (uiState.isBuilderActive) {

        BackHandler {
            when (uiState.builderStep) {
                BuilderStep.SELECT_SITES -> itineraryViewModel.closeBuilder()
                BuilderStep.SELECT_INTERESTS -> itineraryViewModel.backToSites()
                BuilderStep.RECOMMENDATIONS -> itineraryViewModel.backToInterests()
                BuilderStep.ROUTE_OVERVIEW -> itineraryViewModel.closeBuilder()
            }
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

    // The footer's raised centre button is this screen's "new itinerary"
    // action once you're already on the Itinerary tab, so a separate
    // floating button here would be a second plus sitting directly on top
    // of the first.
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

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceSoft)
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp, bottom = 6.dp)
            ) {
                Text(
                    text = "My Itineraries",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Plan your perfect Intramuros day",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
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
                        items(uiState.savedItineraries) { itinerary ->
                            SavedItineraryCard(
                                itinerary = itinerary,
                                onClick = { itineraryViewModel.openSavedItinerary(itinerary) },
                                onDeleteClick = { deleteTarget = itinerary }
                            )
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
}

@Composable
private fun SavedItineraryCard(
    itinerary: Itinerary,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {

    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 3.dp, shape = RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(CardSurface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
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
            .padding(top = 20.dp, bottom = 16.dp)
    ) {

        Row(verticalAlignment = Alignment.CenterVertically) {

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .shadow(elevation = 2.dp, shape = CircleShape)
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
                    fontSize = 12.5.sp,
                    color = TextSecondary
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
