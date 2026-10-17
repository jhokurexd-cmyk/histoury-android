package com.histoury.app.ui.screens.tripcomplete

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.histoury.app.data.model.ItineraryStop
import com.histoury.app.data.model.StopStatus
import com.histoury.app.data.viewmodel.TripCompleteViewModel
import com.histoury.app.navigation.Routes
import com.histoury.app.navigation.navigateToTab
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.CompletedContentColor
import com.histoury.app.theme.CompletedSurfaceColor
import com.histoury.app.theme.Danger
import com.histoury.app.theme.DangerSurface
import com.histoury.app.theme.Primary
import com.histoury.app.theme.PrimarySoft
import com.histoury.app.theme.SurfaceSoft
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary
import com.histoury.app.ui.components.PrimaryButton

/**
 * The distinct "you're done" moment the itinerary rework calls for once an
 * itinerary's final stop resolves (Complete or Skip) — separate from the
 * per-stop Arrival screen, which only ever hands off here rather than
 * looping back to "Next Stop" wording on the last leg.
 *
 * Reads the itinerary fresh from Firestore by [itineraryId] instead of
 * accepting any progress as a navigation argument or recomputing it from a
 * screen-local copy — the saved document is the only source of truth for
 * what happened on this trip.
 */
@Composable
fun TripCompleteScreen(
    itineraryId: String,
    navController: NavHostController,
    viewModel: TripCompleteViewModel = viewModel()
) {

    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(itineraryId) {
        viewModel.load(itineraryId)
    }

    fun goHome() {
        navController.navigateSafely(Routes.Home.route) {
            popUpTo(Routes.Home.route) { inclusive = true }
            launchSingleTop = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceSoft)
    ) {

        when {

            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Primary)
                }
            }

            uiState.itinerary == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {

                        Text(
                            text = uiState.errorMessage ?: "Couldn't load this trip's summary.",
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(20.dp))

                        PrimaryButton(text = "Back to Home", onClick = { goHome() })
                    }
                }
            }

            else -> {

                val itinerary = uiState.itinerary!!
                val stops = remember(itinerary) { itinerary.stops.sortedBy { it.order } }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp)
                ) {

                    item {

                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(84.dp)
                                    .clip(CircleShape)
                                    .background(PrimarySoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Primary,
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        Text(
                            text = "Trip Complete!",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(6.dp))

                        Text(
                            text = itinerary.name,
                            color = TextSecondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(24.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(CardSurface)
                                .padding(vertical = 18.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {

                            TripStat(value = "${uiState.completedCount}", label = "Visited")

                            if (uiState.skippedCount > 0) {
                                TripStat(value = "${uiState.skippedCount}", label = "Skipped")
                            }

                            uiState.elapsedMinutes?.let { minutes ->
                                TripStat(value = formatTripDuration(minutes), label = "Duration")
                            }

                            TripStat(value = "${stops.size}", label = "Total Stops")
                        }

                        Spacer(Modifier.height(28.dp))

                        Text(
                            text = "Your Stops",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        Spacer(Modifier.height(12.dp))
                    }

                    items(stops) { stop ->
                        TripStopRow(stop = stop)
                        Spacer(Modifier.height(10.dp))
                    }

                    item {

                        Spacer(Modifier.height(16.dp))

                        PrimaryButton(
                            text = "Finish Trip",
                            onClick = { goHome() }
                        )

                        Spacer(Modifier.height(16.dp))

                        Text(
                            text = "View Visit History",
                            color = Primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    // Open the Visit History tab as a tab (Home
                                    // underneath), not as a stray screen on
                                    // top of this finished trip.
                                    navController.navigateToTab(Routes.History.route)
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TripStat(value: String, label: String) {

    Column(horizontalAlignment = Alignment.CenterHorizontally) {

        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(Modifier.height(2.dp))

        Text(
            text = label,
            fontSize = 11.5.sp,
            color = TextSecondary
        )
    }
}

@Composable
private fun TripStopRow(stop: ItineraryStop) {

    val isSkipped = stop.status == StopStatus.SKIPPED
    val isCompleted = stop.status == StopStatus.COMPLETED

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardSurface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isSkipped -> DangerSurface
                        isCompleted -> CompletedSurfaceColor
                        else -> CardSurface
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when {
                    isSkipped -> Icons.Default.Close
                    isCompleted -> Icons.Default.CheckCircle
                    else -> Icons.Default.LocationOn
                },
                contentDescription = null,
                tint = when {
                    isSkipped -> Danger
                    isCompleted -> CompletedContentColor
                    else -> TextSecondary
                },
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {

            Text(
                text = stop.name,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )

            Text(
                text = when {
                    isSkipped -> "Skipped"
                    isCompleted -> "Visited"
                    else -> "Not visited"
                },
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

private fun formatTripDuration(minutes: Int): String {

    val hours = minutes / 60
    val remainder = minutes % 60

    return when {
        hours == 0 -> "${minutes}m"
        remainder == 0 -> "${hours}h"
        else -> "${hours}h ${remainder}m"
    }
}
