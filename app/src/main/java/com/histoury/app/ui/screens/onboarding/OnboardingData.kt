package com.histoury.app.ui.screens.onboarding

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.ui.graphics.vector.ImageVector

/** A small labelled chip that floats beside a page's illustration. */
data class OnboardingHighlight(
    val label: String,
    val icon: ImageVector
)

/**
 * One onboarding page = one idea. A short eyebrow, a title of a few words,
 * one or two sentences, and an illustration built from the app's own
 * shapes (a stacked card with a hero glyph and two feature chips), so the
 * intro looks like the app rather than stock artwork pasted on top.
 */
data class OnboardingPage(
    val eyebrow: String,
    val title: String,
    val description: String,
    val heroIcon: ImageVector,
    val highlights: List<OnboardingHighlight>
)

val onboardingPages = listOf(

    OnboardingPage(
        eyebrow = "Explore",
        title = "Discover Intramuros",
        description = "Find the historical sites of the Walled City and get directions to the ones near you.",
        heroIcon = Icons.Default.AccountBalance,
        highlights = listOf(
            OnboardingHighlight("Nearby sites", Icons.Default.Place),
            OnboardingHighlight("Walking directions", Icons.AutoMirrored.Filled.DirectionsWalk)
        )
    ),

    OnboardingPage(
        eyebrow = "Learn",
        title = "Hear the real story",
        description = "Read verified history and listen to narrations. Each site's full story unlocks when you arrive.",
        heroIcon = Icons.Default.AutoStories,
        highlights = listOf(
            OnboardingHighlight("Verified history", Icons.Default.Verified),
            OnboardingHighlight("Audio guides", Icons.Default.Headphones)
        )
    ),

    OnboardingPage(
        eyebrow = "Experience",
        title = "See the past in AR",
        description = "Point your camera at a landmark to see how it looked centuries ago.",
        heroIcon = Icons.Default.ViewInAr,
        highlights = listOf(
            OnboardingHighlight("3D reconstructions", Icons.Default.ViewInAr),
            OnboardingHighlight("Capture the moment", Icons.Default.PhotoCamera)
        )
    ),

    OnboardingPage(
        eyebrow = "Plan",
        title = "Make the trip yours",
        description = "Build an itinerary, save your favorite sites, and keep track of every place you visit.",
        heroIcon = Icons.Default.Route,
        highlights = listOf(
            OnboardingHighlight("Favorites", Icons.Default.Favorite),
            OnboardingHighlight("Visit history", Icons.Default.History)
        )
    )
)
