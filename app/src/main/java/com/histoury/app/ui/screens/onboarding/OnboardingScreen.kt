package com.histoury.app.ui.screens.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.histoury.app.navigation.Routes
import com.histoury.app.theme.Primary
import com.histoury.app.theme.PrimarySoft
import com.histoury.app.theme.PrimarySoftStrong
import com.histoury.app.theme.SurfaceSoft
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary
import com.histoury.app.ui.components.FeatureCard
import com.histoury.app.ui.components.PageIndicator
import com.histoury.app.ui.components.PrimaryButton

/**
 * Onboarding — first thing anyone sees, so it follows the same clean,
 * light look as the rest of the app (SurfaceSoft background, coral as an
 * accent) rather than a full coral wash.
 *
 * Skip and swipe are the only ways to move between pages — there's no
 * per-page "Next". Get Started only appears once you've reached the last
 * page (fades/slides in), and Skip disappears there since it would be
 * redundant next to it.
 */
@Composable
fun OnboardingScreen(navController: NavController) {

    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })

    val isLastPage = pagerState.currentPage == onboardingPages.lastIndex

    fun goToApp() {
        navController.navigate(Routes.Login.route)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceSoft)
            .padding(horizontal = 24.dp)
    ) {

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            horizontalArrangement = Arrangement.End
        ) {

            AnimatedVisibility(
                visible = !isLastPage,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = "Skip",
                    color = TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { goToApp() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->

            val item = onboardingPages[page]

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                // Two rings rather than one flat disc. A single tinted
                // circle reads as a placeholder; concentric tints give the
                // glyph somewhere to sit and cost nothing.
                Box(
                    modifier = Modifier
                        .size(168.dp)
                        .clip(CircleShape)
                        .background(PrimarySoft),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(122.dp)
                            .clip(CircleShape)
                            .background(PrimarySoftStrong),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.heroIcon,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(58.dp)
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))

                Text(
                    text = item.title,
                    fontSize = 25.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = 31.sp
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = item.description,
                    color = TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.88f)
                )

                Spacer(Modifier.height(32.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    item.cards.forEach {
                        // Every card was being handed the same pin emoji as
                        // a fallback, which would have shown on any card
                        // whose icon was ever dropped — three identical pins
                        // under three different labels.
                        FeatureCard(title = it.title, icon = it.icon)
                    }
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            PageIndicator(onboardingPages.size, pagerState.currentPage)
        }

        Spacer(Modifier.height(20.dp))

        // The button's space is reserved on every page rather than added
        // when it appears. Letting the layout grow on the last page pushed
        // the page indicator and the whole pager upward mid-swipe, which
        // made the final page feel like a different screen.
        //
        // Animated by hand rather than with AnimatedVisibility. Inside a Box
        // the only AnimatedVisibility in scope is the outer Column's
        // extension, which Kotlin refuses to call on an implicit receiver —
        // and it animates layout height, which is the one thing this Box
        // exists to prevent. Fading the button in place does what is
        // actually wanted and leaves the layout still.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        ) {

            val appear by animateFloatAsState(
                targetValue = if (isLastPage) 1f else 0f,
                animationSpec = tween(durationMillis = if (isLastPage) 250 else 150),
                label = "getStartedAppear"
            )

            // Skipped entirely when invisible, so the button cannot be
            // tapped through a zero-alpha layer on the earlier pages.
            if (appear > 0.01f) {
                PrimaryButton(
                    text = "Get Started",
                    onClick = { goToApp() },
                    modifier = Modifier.graphicsLayer {
                        alpha = appear
                        translationY = (1f - appear) * 18.dp.toPx()
                    }
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
