package com.histoury.app.ui.screens.onboarding

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.House
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Verified
import androidx.compose.ui.graphics.vector.ImageVector

data class FeatureCard(
    val title: String,
    val icon: ImageVector? = null
)

data class OnboardingPage(
    val title: String,
    val description: String,
    /**
     * The page's own hero glyph.
     *
     * Previously the screen borrowed the first feature card's icon, so the
     * hero and the card beneath it were always the same symbol twice — and
     * two of the three pages opened with the same landmark icon. A page
     * deserves an image that stands for the whole page, not a duplicate of
     * one of its parts.
     */
    val heroIcon: ImageVector,
    val cards: List<FeatureCard>
)

val onboardingPages = listOf(

    OnboardingPage(
        title = "Welcome to Histoury",
        description = "Discover Intramuros through interactive historical narratives, augmented reality experiences, and personalized guided tours.",

        heroIcon = Icons.Default.AccountBalance,

        cards = listOf(
            FeatureCard("Historical\nLandmarks", Icons.Default.AccountBalance),
            FeatureCard("Verified\nNarratives", Icons.Default.Verified),
            FeatureCard("AR\nExperiences", Icons.Default.CameraAlt)
        )
    ),

    OnboardingPage(
        title = "Explore Iconic Sites",
        description = "Visit Fort Santiago, Manila Cathedral, Casa Manila, Plaza de Roma, San Agustin Church, and many more heritage locations.",

        heroIcon = Icons.Default.Church,

        cards = listOf(
            FeatureCard("Fort\nSantiago", Icons.Default.AccountBalance),
            FeatureCard("Casa\nManila", Icons.Default.House),
            FeatureCard("San Agustin\nChurch", Icons.Default.Church)
        )
    ),

    OnboardingPage(
        title = "Navigate & Experience",
        description = "Receive smart navigation, automatic geofence detection, and immersive AR experiences when visiting historical landmarks.",

        heroIcon = Icons.Default.Navigation,

        cards = listOf(
            FeatureCard("GPS\nNavigation", Icons.Default.Navigation),
            FeatureCard("Geofence\nDetection", Icons.Default.GpsFixed),
            FeatureCard("AR\nExperience", Icons.Default.CameraAlt)
        )
    ),

    OnboardingPage(
        title = "Capture Every Journey",
        description = "Save memorable visits, upload photos, review historical sites, and discover the highest-rated destinations in Intramuros.",

        heroIcon = Icons.Default.PhotoLibrary,

        cards = listOf(
            FeatureCard("Visit\nHistory", Icons.Default.History),
            FeatureCard("Photo\nGallery", Icons.Default.PhotoLibrary),
            FeatureCard("Site\nRankings", Icons.Default.EmojiEvents)
        )
    )

)
