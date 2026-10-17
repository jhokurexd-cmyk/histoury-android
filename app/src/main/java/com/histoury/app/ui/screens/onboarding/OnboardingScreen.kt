package com.histoury.app.ui.screens.onboarding

import com.histoury.app.theme.softShadow
import com.histoury.app.navigation.navigateSafely
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import com.histoury.app.ui.components.HistouryMotion
import com.histoury.app.ui.components.rememberReducedMotion
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.histoury.app.data.repository.OnboardingPreference
import com.histoury.app.navigation.Routes
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.OnColor
import com.histoury.app.theme.Primary
import com.histoury.app.theme.PrimarySoft
import com.histoury.app.theme.PrimarySoftStrong
import com.histoury.app.theme.SurfaceSoft
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary
import com.histoury.app.ui.components.PageIndicator
import com.histoury.app.ui.components.PrimaryButton
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.min

/**
 * First-launch introduction: four pages, one idea each.
 *
 * Layout is fixed across pages — top bar (Back / wordmark / Skip), the
 * pager, then indicator + primary button + a reserved row for "Log in" —
 * so nothing jumps while swiping; only the page content moves.
 *
 * Get Started opens Register (with Login underneath, so Back and "Already
 * have an account?" land on Login). "I already have an account" and Skip
 * go straight to Login. Either way onboarding is marked as seen and is not
 * shown again on this device; AuthGate sends later launches to Login or
 * Home.
 */
@Composable
fun OnboardingScreen(navController: NavController) {

    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val reducedMotion = rememberReducedMotion()

    // One-time entrance when onboarding first opens: 0 → 1 over 700ms,
    // linear here; each element eases its own slice of it (staggered below).
    // Never blocks input: everything is tappable/swipeable from frame one.
    val entrance = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, tween(ENTRANCE_TOTAL_MS, easing = LinearEasing))
    }

    val currentPage = pagerState.currentPage
    val isFirstPage = currentPage == 0
    val isLastPage = currentPage == onboardingPages.lastIndex

    // A quick double tap on Get Started / Skip would otherwise run this
    // twice: the second time Onboarding is already gone, so popUpTo does
    // nothing and a second Login (+ Register) gets stacked on top.
    var finished by remember { mutableStateOf(false) }

    fun finish(openRegister: Boolean) {
        if (finished) return
        finished = true
        OnboardingPreference.markSeen(context)
        // Onboarding leaves the back stack: Back on Login exits the app
        // instead of reopening the intro.
        navController.navigateSafely(Routes.Login.route) {
            popUpTo(Routes.Onboarding.route) { inclusive = true }
        }
        if (openRegister) {
            navController.navigateSafely(Routes.Register.route)
        }
    }

    // Next / Back / system Back: the page is already on screen and moves,
    // so strong ease-in-out. A swipe during it simply takes over.
    fun goToPage(page: Int) {
        scope.launch {
            pagerState.animateScrollToPage(
                page = page,
                animationSpec = tween(HistouryMotion.DurationLong, easing = HistouryMotion.EaseInOut)
            )
        }
    }

    // System Back steps back through the pages before leaving the app.
    BackHandler(enabled = !isFirstPage) {
        goToPage(currentPage - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceSoft)
            .systemBarsPadding()
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
    ) {

        // ----- Top bar: Back · wordmark · Skip (fixed height on every page) -----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp)
        ) {

            val backAlpha by animateFloatAsState(
                targetValue = if (isFirstPage) 0f else 1f,
                animationSpec = tween(HistouryMotion.DurationMedium, easing = HistouryMotion.EaseOut),
                label = "backAlpha"
            )

            // Removed from composition when hidden so it can't be tapped
            // (or focused by TalkBack) through a transparent layer.
            if (backAlpha > 0.01f) {
                IconButton(
                    onClick = { goToPage(currentPage - 1) },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .graphicsLayer { alpha = backAlpha }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous page",
                        tint = TextPrimary
                    )
                }
            }

            Text(
                text = "Histoury",
                color = Primary,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                modifier = Modifier.align(Alignment.Center)
            )

            val skipAlpha by animateFloatAsState(
                targetValue = if (isLastPage) 0f else 1f,
                animationSpec = tween(HistouryMotion.DurationMedium, easing = HistouryMotion.EaseOut),
                label = "skipAlpha"
            )

            if (skipAlpha > 0.01f) {
                TextButton(
                    onClick = { finish(openRegister = false) },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .graphicsLayer { alpha = skipAlpha }
                        .semantics { contentDescription = "Skip introduction" }
                ) {
                    Text(
                        text = "Skip",
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // ----- Pages -----
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { index ->

            OnboardingPageContent(
                page = onboardingPages[index],
                entrance = { entrance.value },
                reducedMotion = reducedMotion,
                // Read inside graphicsLayer (draw phase), so swiping only
                // redraws — it doesn't recompose the page every frame.
                // 0 when this page is settled in view, ±1 when a page away.
                pageOffset = {
                    (pagerState.currentPage - index) + pagerState.currentPageOffsetFraction
                }
            )
        }

        // ----- Bottom: indicator, primary action, reserved secondary row -----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
                .graphicsLayer {
                    // Last in the entrance stagger: the controls arrive once
                    // there's something to act on.
                    val t = staggered(entrance.value, ENTRANCE_CONTROLS_DELAY_MS)
                    alpha = t
                    if (!reducedMotion) translationY = (1f - t) * 16.dp.toPx()
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Follows the finger (page + swipe fraction), read at draw time.
            PageIndicator(
                pageCount = onboardingPages.size,
                currentPage = currentPage,
                position = { pagerState.currentPage + pagerState.currentPageOffsetFraction }
            )

            Spacer(Modifier.height(24.dp))

            PrimaryButton(
                onClick = {
                    if (isLastPage) finish(openRegister = true) else goToPage(currentPage + 1)
                }
            ) {
                // Same button, new job: the label rolls up into place instead
                // of snapping. Transitions retarget, so swiping back and forth
                // across the last page never stacks or restarts them.
                AnimatedContent(
                    targetState = isLastPage,
                    transitionSpec = {
                        val enter = fadeIn(tween(HistouryMotion.DurationMedium, easing = HistouryMotion.EaseOut)) +
                            slideInVertically(tween(HistouryMotion.DurationMedium, easing = HistouryMotion.EaseOut)) { it / 2 }
                        val exit = fadeOut(tween(HistouryMotion.DurationShort, easing = HistouryMotion.EaseOut)) +
                            slideOutVertically(tween(HistouryMotion.DurationShort, easing = HistouryMotion.EaseOut)) { -it / 2 }
                        (enter togetherWith exit).using(SizeTransform(clip = false))
                    },
                    contentAlignment = Alignment.Center,
                    label = "primaryLabel"
                ) { last ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (last) "Get Started" else "Next",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (!last) {
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Space for "Log in" is kept on every page so the button above
            // never shifts when it appears on the last one.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                contentAlignment = Alignment.Center
            ) {

                val loginAlpha by animateFloatAsState(
                    targetValue = if (isLastPage) 1f else 0f,
                    animationSpec = tween(HistouryMotion.DurationMedium, easing = HistouryMotion.EaseOut),
                    label = "loginAlpha"
                )

                if (loginAlpha > 0.01f) {
                    TextButton(
                        onClick = { finish(openRegister = false) },
                        modifier = Modifier.graphicsLayer {
                            alpha = loginAlpha
                            // Rises 8dp as it fades in, from under the button.
                            if (!reducedMotion) translationY = (1f - loginAlpha) * 8.dp.toPx()
                        }
                    ) {
                        Text(
                            text = "I already have an account",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Log in",
                            color = Primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    entrance: () -> Float,
    reducedMotion: Boolean,
    pageOffset: () -> Float
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // The illustration takes whatever height is left after the text,
        // so small screens shrink the picture rather than clip the words.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            OnboardingIllustration(
                page = page,
                pageOffset = pageOffset,
                reducedMotion = reducedMotion,
                modifier = Modifier.graphicsLayer {
                    // Swipe: lags a little behind the page and eases down in
                    // scale — depth that tracks the finger, so it's always
                    // interruptible. Entrance: grows in from 94%, never 0.
                    val offset = pageOffset()
                    val distance = offset.absoluteValue.coerceIn(0f, 1f)
                    val enter = staggered(entrance(), 0)
                    val scale = if (reducedMotion) {
                        1f
                    } else {
                        (1f - distance * 0.08f) * (0.94f + 0.06f * enter)
                    }
                    if (!reducedMotion) translationX = -offset * size.width * 0.18f
                    scaleX = scale
                    scaleY = scale
                    alpha = (1f - distance * 0.5f) * enter
                }
            )
        }

        Spacer(Modifier.height(12.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            Text(
                modifier = Modifier.textMotion(pageOffset, entrance, ENTRANCE_EYEBROW_DELAY_MS, 0.6f, reducedMotion),
                text = page.eyebrow.uppercase(),
                color = Primary,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                letterSpacing = 1.4.sp
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = page.title,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                lineHeight = 32.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .textMotion(pageOffset, entrance, ENTRANCE_TITLE_DELAY_MS, 0.7f, reducedMotion)
                    .semantics { heading() }
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = page.description,
                color = TextSecondary,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    // Fades a touch faster and drifts a touch further than
                    // the title, so the block separates slightly mid-swipe.
                    .textMotion(pageOffset, entrance, ENTRANCE_BODY_DELAY_MS, 0.9f, reducedMotion)
                    .widthIn(max = 340.dp)
                    // Room for three lines on every page, so the title
                    // doesn't bob up and down between pages.
                    .heightIn(min = 66.dp)
            )
        }

        Spacer(Modifier.height(12.dp))
    }
}

/**
 * The page picture, drawn from the app's own shapes: a solid coral-tint
 * disc, a tilted card behind a front card (the page's glyph on a coral
 * badge, with placeholder text lines), and two floating feature chips.
 * Scales to the space available; the chips are dropped when there is too
 * little room for them to be legible.
 */
@Composable
private fun OnboardingIllustration(
    page: OnboardingPage,
    pageOffset: () -> Float,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {

    // Layers shift by different amounts during a swipe (back card least,
    // chips most), so the picture reads as layered rather than flat.
    fun Modifier.depth(factor: Float): Modifier =
        if (reducedMotion) this else graphicsLayer {
            translationX = -pageOffset() * size.width * factor
        }


    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {

        val side: Dp = min(min(maxWidth.value, maxHeight.value), 320f).dp
        val cardWidth = side * 0.58f
        val cardHeight = side * 0.68f
        val showChips = side >= 200.dp

        Box(
            modifier = Modifier.size(side),
            contentAlignment = Alignment.Center
        ) {

            // Backdrop disc.
            Box(
                modifier = Modifier
                    .size(side * 0.92f)
                    .clip(CircleShape)
                    .background(PrimarySoft)
            )

            // Small accent dots.
            Box(
                modifier = Modifier
                    .offset(x = side * 0.36f, y = side * 0.30f)
                    .size(side * 0.05f)
                    .clip(CircleShape)
                    .background(PrimarySoftStrong)
            )
            Box(
                modifier = Modifier
                    .offset(x = -side * 0.40f, y = -side * 0.22f)
                    .size(side * 0.035f)
                    .clip(CircleShape)
                    .background(Primary.copy(alpha = 0.35f))
            )

            // Card behind, tilted.
            Box(
                modifier = Modifier
                    .depth(-0.10f)
                    .offset(x = -side * 0.06f, y = side * 0.02f)
                    .rotate(-8f)
                    .size(width = cardWidth, height = cardHeight)
                    .clip(RoundedCornerShape(24.dp))
                    .background(PrimarySoftStrong)
            )

            // Front card.
            Column(
                modifier = Modifier
                    .offset(x = side * 0.03f)
                    .rotate(3f)
                    .size(width = cardWidth, height = cardHeight)
                    .softShadow(elevation = 10.dp, shape = RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(CardSurface)
                    .padding(side * 0.06f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                Box(
                    modifier = Modifier
                        .size(side * 0.24f)
                        .clip(CircleShape)
                        .background(Primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = page.heroIcon,
                        contentDescription = null,
                        tint = OnColor,
                        modifier = Modifier.size(side * 0.12f)
                    )
                }

                Spacer(Modifier.height(side * 0.07f))

                // Suggested text lines — reads as "content", not a blank card.
                SkeletonLine(widthFraction = 0.78f, height = side * 0.03f)
                Spacer(Modifier.height(side * 0.03f))
                SkeletonLine(widthFraction = 0.56f, height = side * 0.03f)
                Spacer(Modifier.height(side * 0.03f))
                SkeletonLine(widthFraction = 0.66f, height = side * 0.03f)
            }

            if (showChips) {
                page.highlights.getOrNull(0)?.let { highlight ->
                    HighlightChip(
                        highlight = highlight,
                        modifier = Modifier
                            .offset(x = side * 0.20f, y = -side * 0.34f)
                            .depth(0.35f)
                    )
                }
                page.highlights.getOrNull(1)?.let { highlight ->
                    HighlightChip(
                        highlight = highlight,
                        modifier = Modifier
                            .offset(x = -side * 0.18f, y = side * 0.35f)
                            .depth(0.25f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SkeletonLine(widthFraction: Float, height: Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(SurfaceSoft)
    )
}

@Composable
private fun HighlightChip(
    highlight: OnboardingHighlight,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .softShadow(elevation = 6.dp, shape = RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .background(CardSurface)
            .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(PrimarySoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = highlight.icon,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(15.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = highlight.label,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------------------------------------------------------------------------
// Motion helpers
// ---------------------------------------------------------------------------

// Entrance: 700ms total, each element 380ms, staggered 60ms apart.
private const val ENTRANCE_TOTAL_MS = 700
private const val ENTRANCE_ELEMENT_MS = 380
private const val ENTRANCE_EYEBROW_DELAY_MS = 120
private const val ENTRANCE_TITLE_DELAY_MS = 180
private const val ENTRANCE_BODY_DELAY_MS = 240
private const val ENTRANCE_CONTROLS_DELAY_MS = 320

/**
 * This element's eased 0..1 slice of the shared entrance clock [t]
 * (0..1 over [ENTRANCE_TOTAL_MS]), starting [delayMs] in.
 */
private fun staggered(t: Float, delayMs: Int): Float {
    val elapsed = t * ENTRANCE_TOTAL_MS - delayMs
    val local = (elapsed / ENTRANCE_ELEMENT_MS).coerceIn(0f, 1f)
    return HistouryMotion.EaseOut.transform(local)
}

/**
 * Text entrance (fade + 14dp rise, staggered) combined with swipe motion
 * (fade by [swipeFade] and drift down as the page leaves). All read in the
 * draw phase. Reduced motion keeps the fades and drops the movement.
 */
private fun Modifier.textMotion(
    pageOffset: () -> Float,
    entrance: () -> Float,
    delayMs: Int,
    swipeFade: Float,
    reducedMotion: Boolean
): Modifier = graphicsLayer {
    val distance = pageOffset().absoluteValue.coerceIn(0f, 1f)
    val enter = staggered(entrance(), delayMs)
    alpha = (1f - distance * swipeFade) * enter
    if (!reducedMotion) {
        translationY = distance * (12.dp.toPx() * swipeFade / 0.7f) + (1f - enter) * 14.dp.toPx()
    }
}
