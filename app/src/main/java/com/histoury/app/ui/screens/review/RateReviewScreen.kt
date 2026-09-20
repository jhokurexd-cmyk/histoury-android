package com.histoury.app.ui.screens.review

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.histoury.app.data.viewmodel.RateReviewViewModel
import com.histoury.app.ui.components.PrimaryButton
import com.histoury.app.theme.Outline
import com.histoury.app.theme.RatingStar
import com.histoury.app.theme.SurfaceSoft
import com.histoury.app.theme.Danger
import com.histoury.app.theme.Primary as PrimaryPink
import com.histoury.app.theme.CardSurface

// These file-local names predate the theme system; they now alias the
// shared palette so this screen follows dark mode with the rest of the app.

@Composable
fun RateReviewScreen(
    siteId: String,
    visitId: String,
    navController: NavHostController,
    rateReviewViewModel: RateReviewViewModel = viewModel()
) {

    val uiState by rateReviewViewModel.uiState.collectAsState()

    val context = LocalContext.current

    LaunchedEffect(siteId) {
        rateReviewViewModel.loadSite(siteId)
    }

    LaunchedEffect(uiState.submitSucceeded) {
        if (uiState.submitSucceeded) {
            android.widget.Toast.makeText(
                context,
                "Thanks for your review!",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            navController.popBackStack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceSoft)
            .statusBarsPadding()
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PrimaryPink)
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    navController.popBackStack()
                }
            )

            Spacer(Modifier.width(12.dp))

            Text(
                text = "Rate & Review",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            val site = uiState.site

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardSurface)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Outline)
                ) {
                    AsyncImage(
                        model = site?.featuredImage,
                        contentDescription = site?.siteName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column {
                    Text(
                        text = site?.siteName ?: "Historical Site",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = site?.description ?: "",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        maxLines = 2
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardSurface)
                    .padding(16.dp)
            ) {

                Text(
                    text = "How was your AR experience?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                Spacer(Modifier.height(12.dp))

                Row {
                    for (starIndex in 1..5) {

                        val isFilled = starIndex <= uiState.rating

                        val starColor by animateColorAsState(
                            targetValue = if (isFilled) RatingStar else Outline,
                            animationSpec = tween(150),
                            label = "starColor"
                        )

                        val starScale by animateFloatAsState(
                            targetValue = if (isFilled) 1f else 0.85f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "starScale"
                        )

                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Rate $starIndex",
                            tint = starColor,
                            modifier = Modifier
                                .size(34.dp)
                                .graphicsLayer {
                                    scaleX = starScale
                                    scaleY = starScale
                                }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    rateReviewViewModel.onRatingChanged(starIndex)
                                }
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardSurface)
                    .padding(16.dp)
            ) {

                Text(
                    text = "Share your experience",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.reviewText,
                    onValueChange = { rateReviewViewModel.onReviewTextChanged(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    placeholder = {
                        Text(
                            text = "Tell us about your visit to this historical site. " +
                                "What did you like most?",
                            fontSize = 13.sp
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceSoft,
                        unfocusedContainerColor = SurfaceSoft,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "${uiState.reviewText.length} / 500 characters",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }

            if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage ?: "",
                    color = Danger,
                    fontSize = 12.sp
                )
            }

            if (uiState.isSubmitting) {

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PrimaryPink)
                }

            } else {

                PrimaryButton(
                    text = "Submit Review",
                    onClick = {
                        rateReviewViewModel.submitReview(visitId)
                    }
                )
            }
        }
    }
}
