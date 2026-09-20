package com.histoury.app.ui.screens.arrival

import com.histoury.app.data.offline.ArModelCache
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.histoury.app.theme.TextSecondary
import com.histoury.app.theme.SurfaceSoft
import com.histoury.app.theme.PrimarySoft
import com.histoury.app.theme.AccentGold
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.histoury.app.R
import com.histoury.app.data.itinerary.ActiveItineraryTrip
import com.histoury.app.data.viewmodel.ArrivalViewModel
import com.histoury.app.navigation.Routes
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Primary as PrimaryPink
import com.histoury.app.ui.components.PrimaryButton

@Composable
fun ArrivalScreen(
    siteId: String,
    navController: NavHostController,
    arrivalViewModel: ArrivalViewModel = viewModel()
) {

    val uiState by arrivalViewModel.uiState.collectAsState()

    val context = LocalContext.current

    /*
     * Play arrival_chime.mp3 once the site has finished
     * loading and the You've Arrived screen is shown.
     */
    val loadedSiteId = uiState.site?.documentId

    DisposableEffect(loadedSiteId) {

        var mediaPlayer: MediaPlayer? = null

        if (loadedSiteId != null) {

            val audioAttributes =
                AudioAttributes.Builder()
                    .setUsage(
                        AudioAttributes.USAGE_ASSISTANCE_SONIFICATION
                    )
                    .setContentType(
                        AudioAttributes.CONTENT_TYPE_SONIFICATION
                    )
                    .build()

            mediaPlayer =
                MediaPlayer.create(
                    context,
                    R.raw.arrival_chime,
                    audioAttributes,
                    0
                )?.apply {

                    setVolume(1f, 1f)

                    setOnCompletionListener { player ->

                        player.release()

                        mediaPlayer = null
                    }

                    start()
                }
        }

        onDispose {

            mediaPlayer?.release()

            mediaPlayer = null
        }
    }

    LaunchedEffect(siteId) {

        arrivalViewModel.loadArrivedSite(siteId)
    }

    // The visitor is at the site: start downloading its AR model now, in the
    // background, so AR opens without waiting for it (see ArModelCache).
    LaunchedEffect(uiState.site?.documentId) {
        ArModelCache.prefetch(context, uiState.site)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CardSurface)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(
                horizontal = 24.dp,
                vertical = 16.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(
            Modifier.height(24.dp)
        )

        when {

            uiState.isLoading -> {

                Spacer(
                    Modifier.height(80.dp)
                )

                CircularProgressIndicator(
                    color = PrimaryPink
                )
            }

            uiState.errorMessage != null -> {

                Spacer(
                    Modifier.height(80.dp)
                )

                Text(
                    text = uiState.errorMessage ?: "",
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(
                    Modifier.height(20.dp)
                )

                val belongsToActiveTrip =
                    ActiveItineraryTrip.progressFor(siteId) != null

                PrimaryButton(
                    text =
                        if (belongsToActiveTrip) {
                            "Back to Itinerary"
                        } else {
                            "Back to Home"
                        },
                    onClick = {

                        val destination =
                            if (belongsToActiveTrip) {

                                Routes.Itinerary.route

                            } else {

                                Routes.Home.route
                            }

                        navController.navigate(
                            destination
                        ) {

                            popUpTo(destination) {
                                inclusive = true
                            }

                            launchSingleTop = true
                        }
                    }
                )
            }

            uiState.site != null -> {

                val site =
                    uiState.site!!

                val tripProgress =
                    uiState.tripProgress

                ArrivalPinMark()

                Spacer(
                    Modifier.height(16.dp)
                )

                Text(
                    text =
                        "You've Arrived!",
                    fontSize =
                        26.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    Modifier.height(16.dp)
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(PrimarySoft)
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = PrimaryPink,
                        modifier = Modifier.size(15.dp)
                    )

                    Spacer(Modifier.width(6.dp))

                    Text(
                        text = site.siteName,
                        color = PrimaryPink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                Spacer(
                    Modifier.height(16.dp)
                )

                Text(
                    text =
                        if (uiState.isPlace) {

                            "You've arrived. Enjoy your visit!"

                        } else {

                            "Welcome to one of Intramuros' significant historical " +
                                    "landmarks. Discover the rich heritage and fascinating " +
                                    "stories behind this site."
                        },
                    color =
                        TextSecondary,
                    fontSize =
                        14.sp,
                    textAlign =
                        TextAlign.Center,
                    lineHeight =
                        20.sp
                )

                Spacer(
                    Modifier.height(20.dp)
                )

                // Photograph and caption share one rounded card, so the
                // description reads as belonging to the picture rather than
                // floating between it and the button below.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceSoft)
                ) {

                    AsyncImage(
                        model = site.featuredImage,
                        contentDescription = site.siteName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp),
                        contentScale = ContentScale.Crop
                    )

                    Text(
                        text = site.description,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    )
                }

                Spacer(
                    Modifier.height(28.dp)
                )

                if (!uiState.isPlace) {

                    PrimaryButton(
                        text = "View Historical Info  \u2192",
                        onClick = {

                            navController.navigate(
                                Routes.SiteDetails.createRoute(
                                    site.documentId
                                )
                            )
                        }
                    )

                    Spacer(Modifier.height(12.dp))
                }

                if (tripProgress != null) {

                    Text(
                        text =
                            "Stop ${tripProgress.currentNumber} of ${tripProgress.totalStops} complete · " +
                                    tripProgress.itineraryName,
                        color =
                            TextSecondary,
                        fontSize =
                            12.5.sp,
                        fontWeight =
                            FontWeight.SemiBold,
                        textAlign =
                            TextAlign.Center
                    )

                    Spacer(
                        Modifier.height(4.dp)
                    )

                    Text(
                        text =
                            if (tripProgress.nextStop != null) {

                                "Enjoy your visit. Your itinerary is saved—continue whenever you're ready."

                            } else {

                                "Itinerary completed in ${
                                    formatElapsedTrip(
                                        tripProgress.startedAtMillis
                                    )
                                }. Take your time and enjoy your final destination."
                            },
                        color =
                            TextSecondary,
                        fontSize =
                            12.sp,
                        textAlign =
                            TextAlign.Center
                    )

                    Spacer(
                        Modifier.height(10.dp)
                    )

                    PrimaryButton(
                        text =
                            tripProgress.nextStop?.let {

                                "I'm Ready · Next: ${it.name}"

                            } ?: "Finish Itinerary",

                        onClick = {

                            val nextStop =
                                ActiveItineraryTrip.advanceFrom(
                                    site.documentId
                                )

                            if (nextStop != null) {

                                navController.navigate(
                                    Routes.Map.createRoute(
                                        nextStop.refId
                                    )
                                ) {

                                    popUpTo(
                                        Routes.Itinerary.route
                                    )
                                }

                            } else {

                                navController.navigate(
                                    Routes.Itinerary.route
                                ) {

                                    popUpTo(
                                        Routes.Itinerary.route
                                    ) {
                                        inclusive = true
                                    }

                                    launchSingleTop = true
                                }
                            }
                        }
                    )

                    Spacer(
                        Modifier.height(12.dp)
                    )
                }

                if (
                    tripProgress == null ||
                    tripProgress.nextStop != null
                ) {

                    Text(
                        text =
                            if (tripProgress != null) {

                                "Pause & Save for Later"

                            } else {

                                "Explore Other Sites"
                            },
                        color =
                            PrimaryPink,
                        fontSize =
                            13.5.sp,
                        fontWeight =
                            FontWeight.Bold,
                        modifier =
                            Modifier.clickable(
                                interactionSource =
                                    remember {
                                        MutableInteractionSource()
                                    },
                                indication = null
                            ) {

                                val destination =
                                    if (tripProgress != null) {

                                        Routes.Itinerary.route

                                    } else {

                                        Routes.Home.route
                                    }

                                navController.navigate(
                                    destination
                                ) {

                                    popUpTo(destination) {
                                        inclusive = true
                                    }

                                    launchSingleTop = true
                                }
                            }
                    )
                }

                Spacer(
                    Modifier.height(16.dp)
                )
            }
        }
    }
}

private fun formatElapsedTrip(
    startedAtMillis: Long
): String {

    val minutes =
        (
                (
                        System.currentTimeMillis() -
                                startedAtMillis
                        ) / 60_000L
                )
            .coerceAtLeast(1L)
            .toInt()

    val hours =
        minutes / 60

    val remainder =
        minutes % 60

    return when {

        hours == 0 ->
            "$minutes min"

        remainder == 0 ->
            "${hours}h"

        else ->
            "${hours}h ${remainder}m"
    }
}

/**
 * The arrival mark: a pin with rays, over a soft arc.
 *
 * Drawn rather than shipped as an image so it follows the theme and stays
 * sharp at any density, and because the composition — pin, rays, ground —
 * is three primitives rather than artwork worth a file.
 */
@Composable
private fun ArrivalPinMark() {

    Box(
        modifier = Modifier.size(width = 120.dp, height = 78.dp),
        contentAlignment = Alignment.BottomCenter
    ) {

        // The arc reads as ground for the pin to stand on. Without it the
        // pin floats and the rays have nothing to sit above.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .clip(RoundedCornerShape(topStart = 60.dp, topEnd = 60.dp))
                .background(PrimarySoft)
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Top
        ) {

            ArrivalRay(rotation = -28f)

            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = "Destination reached",
                tint = PrimaryPink,
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .size(48.dp)
            )

            ArrivalRay(rotation = 28f)
        }
    }
}

@Composable
private fun ArrivalRay(rotation: Float) {

    Box(
        modifier = Modifier
            .padding(top = 14.dp)
            .rotate(rotation)
            .size(width = 3.dp, height = 14.dp)
            .clip(RoundedCornerShape(50))
            .background(AccentGold)
    )
}
