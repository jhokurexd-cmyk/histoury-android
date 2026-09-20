package com.histoury.app.ui.screens.placedetails

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Facebook
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.histoury.app.data.model.Place
import com.histoury.app.data.viewmodel.PlaceDetailsViewModel
import com.histoury.app.navigation.Routes
import com.histoury.app.theme.SurfaceSoft
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Primary
import com.histoury.app.theme.PrimarySoft
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary
import com.histoury.app.ui.components.ImageLightbox
import com.histoury.app.ui.components.PrimaryButton

/**
 * Showcase screen for non-historical places (cafés, restaurants, hotels,
 * souvenir shops, schools, banks) — reached by tapping a place card from
 * Home or Explore.
 *
 * Unlike SiteDetailsScreen, this is freely viewable any time: no arrival
 * gate, no AR, no tabs. It's a business listing, not unlockable
 * educational content.
 */
@Composable
fun PlaceDetailsScreen(
    placeId: String,
    navController: NavHostController,
    placeDetailsViewModel: PlaceDetailsViewModel = viewModel()
) {

    val uiState by placeDetailsViewModel.uiState.collectAsState()

    val context = LocalContext.current

    var lightboxIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(placeId) {
        placeDetailsViewModel.loadPlace(placeId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CardSurface)
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

            uiState.errorMessage != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.errorMessage ?: "",
                        color = TextSecondary
                    )
                }
            }

            uiState.place != null -> {

                val place = uiState.place!!

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    ) {

                        AsyncImage(
                            model = place.featuredImage,
                            contentDescription = place.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.3f)),
                                        startY = 380f
                                    )
                                )
                        )

                        Box(
                            modifier = Modifier
                                .padding(16.dp)
                                .clip(CircleShape)
                                .background(CardSurface)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    navController.popBackStack()
                                }
                                // The hero image stays full-bleed under the
                                // status bar; only the control steps out of
                                // it, so the photograph keeps its edge-to-edge
                                // look without the button hiding behind the
                                // camera cutout.
                                .align(Alignment.TopStart)
                                .statusBarsPadding()
                                .padding(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Primary
                            )
                        }

                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 20.dp, bottom = 20.dp),
                            shape = RoundedCornerShape(50),
                            color = CardSurface
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = place.categoryEmoji, fontSize = 14.sp)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = place.categoryLabel,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = TextPrimary
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = (-20).dp)
                            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                            .background(CardSurface)
                            .padding(20.dp)
                    ) {

                        Text(
                            text = place.name,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        val addressText = place.address.ifBlank { place.location }

                        if (addressText.isNotBlank()) {

                            Spacer(Modifier.height(6.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = Primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = addressText,
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        if (place.openingHours.isNotBlank() || place.entranceFee.isNotBlank()) {

                            Spacer(Modifier.height(10.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {

                                if (place.openingHours.isNotBlank()) {
                                    InfoPill(
                                        icon = Icons.Default.Schedule,
                                        text = place.openingHours
                                    )
                                }

                                if (place.entranceFee.isNotBlank()) {
                                    InfoPill(
                                        icon = Icons.Default.ConfirmationNumber,
                                        text = place.entranceFee
                                    )
                                }
                            }
                        }

                        val descriptionText = place.fullDescription.ifBlank { place.description }

                        if (descriptionText.isNotBlank()) {

                            Spacer(Modifier.height(18.dp))

                            Text(
                                text = "About",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )

                            Spacer(Modifier.height(6.dp))

                            Text(
                                text = descriptionText,
                                color = TextSecondary,
                                fontSize = 13.5.sp,
                                lineHeight = 20.sp
                            )
                        }

                        if (place.galleryImages.isNotEmpty()) {

                            Spacer(Modifier.height(18.dp))

                            Text(
                                text = "Gallery",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )

                            Spacer(Modifier.height(10.dp))

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                itemsIndexed(place.galleryImages) { index, imageUrl ->
                                    AsyncImage(
                                        model = imageUrl,
                                        contentDescription = place.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(120.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                lightboxIndex = index
                                            }
                                    )
                                }
                            }
                        }

                        val hasContactInfo = place.contactPhone.isNotBlank() ||
                            place.contactWebsite.isNotBlank() ||
                            place.contactFacebook.isNotBlank() ||
                            place.contactEmail.isNotBlank()

                        if (hasContactInfo) {

                            Spacer(Modifier.height(18.dp))

                            Text(
                                text = "Contact",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )

                            Spacer(Modifier.height(8.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                                if (place.contactPhone.isNotBlank()) {
                                    ContactRow(
                                        icon = Icons.Default.Phone,
                                        label = place.contactPhone
                                    ) {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_DIAL).apply {
                                                    data = Uri.parse("tel:${place.contactPhone}")
                                                }
                                            )
                                        }
                                    }
                                }

                                if (place.contactWebsite.isNotBlank()) {
                                    ContactRow(
                                        icon = Icons.Default.Language,
                                        label = place.contactWebsite
                                    ) {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(place.contactWebsite))
                                            )
                                        }
                                    }
                                }

                                if (place.contactFacebook.isNotBlank()) {
                                    ContactRow(
                                        icon = Icons.Default.Facebook,
                                        label = place.contactFacebook
                                    ) {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(place.contactFacebook))
                                            )
                                        }
                                    }
                                }

                                if (place.contactEmail.isNotBlank()) {
                                    ContactRow(
                                        icon = Icons.Default.Email,
                                        label = place.contactEmail
                                    ) {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_SENDTO).apply {
                                                    data = Uri.parse("mailto:${place.contactEmail}")
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        PrimaryButton(
                            text = "Navigate",
                            onClick = {
                                navController.navigate(
                                    Routes.Map.createRoute(place.documentId)
                                )
                            }
                        )

                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }

    val currentLightboxIndex = lightboxIndex

    if (currentLightboxIndex != null) {
        ImageLightbox(
            images = uiState.place?.galleryImages ?: emptyList(),
            startIndex = currentLightboxIndex,
            onDismiss = { lightboxIndex = null }
        )
    }
}

@Composable
private fun InfoPill(icon: ImageVector, text: String) {

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = PrimarySoft
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = text,
                color = Primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ContactRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceSoft)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(PrimarySoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text = label,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}
