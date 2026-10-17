package com.histoury.app.ui.components

import com.histoury.app.theme.styledElevation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Primary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.exp

/** How far the content has to be pulled before letting go refreshes. */
private val RefreshThreshold = 80.dp

/** The spinner's disc. */
private val SpinnerSize = 36.dp

/**
 * A refresh that finishes instantly (Firestore answering from its cache)
 * would flash the spinner for a frame and read as "nothing happened". It
 * always stays at least this long.
 */
private const val MIN_REFRESH_MS = 900L

/**
 * Pull down from the top of a list to reload it, the way Facebook and most
 * apps do: the whole list stretches down with your finger, a coral spinner
 * appears in the gap it opens, and letting go past the threshold refreshes.
 * The list holds that gap while loading, then slides back up.
 *
 * Wrap the screen's scrolling list (LazyColumn or a verticalScroll Column):
 * the gesture only starts once that list is already at the top.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistouryPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val state = rememberPullToRefreshState()
    val haptics = LocalHapticFeedback.current
    val thresholdPx = with(LocalDensity.current) { RefreshThreshold.toPx() }

    // Set the moment the visitor lets go, so the spinner shows straight away
    // and stays for MIN_REFRESH_MS even if the reload is instant (or was
    // ignored because one was already running).
    var requested by remember { mutableStateOf(false) }
    var requestedAt by remember { mutableLongStateOf(0L) }

    LaunchedEffect(requested, isRefreshing) {
        if (requested && !isRefreshing) {
            val elapsed = System.currentTimeMillis() - requestedAt
            if (elapsed < MIN_REFRESH_MS) delay(MIN_REFRESH_MS - elapsed)
            requested = false
        }
    }

    val showRefreshing = isRefreshing || requested

    // A light tick when the pull passes the point where letting go refreshes.
    LaunchedEffect(state) {
        snapshotFlow { state.distanceFraction >= 1f }
            .distinctUntilChanged()
            .collect { pastThreshold ->
                if (pastThreshold && !showRefreshing) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
    }

    PullToRefreshBox(
        isRefreshing = showRefreshing,
        onRefresh = {
            requested = true
            requestedAt = System.currentTimeMillis()
            onRefresh()
        },
        modifier = modifier,
        state = state,
        indicator = {
            StretchSpinner(
                state = state,
                isRefreshing = showRefreshing,
                thresholdPx = thresholdPx,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    ) {
        // The list itself moves down with the pull, instead of a spinner
        // sliding over a list that stays put.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = stretchOffset(state.distanceFraction, thresholdPx) },
            content = content
        )
    }
}

/**
 * How far the content sits below its resting place for a given pull.
 * One-to-one up to the threshold, then a rubber band: each extra bit of
 * pulling moves it less, up to half a threshold more.
 */
private fun stretchOffset(fraction: Float, thresholdPx: Float): Float {
    if (fraction <= 0f) return 0f
    if (fraction <= 1f) return fraction * thresholdPx
    val extra = 1f - exp(-(fraction - 1f))
    return thresholdPx * (1f + 0.5f * extra)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StretchSpinner(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    thresholdPx: Float,
    modifier: Modifier = Modifier
) {
    val spinnerPx = with(LocalDensity.current) { SpinnerSize.toPx() }

    Surface(
        modifier = modifier
            .size(SpinnerSize)
            .graphicsLayer {
                val fraction = state.distanceFraction
                val gap = stretchOffset(fraction, thresholdPx)
                // Centred in the gap the content has opened.
                translationY = (gap - spinnerPx) / 2f
                alpha = fraction.coerceIn(0f, 1f)
                val scale = 0.6f + 0.4f * fraction.coerceIn(0f, 1f)
                scaleX = scale
                scaleY = scale
                // Turns as you pull, so it visibly responds to the finger.
                rotationZ = if (isRefreshing) 0f else fraction * 270f
            },
        shape = CircleShape,
        color = CardSurface,
        shadowElevation = styledElevation(3.dp)
    ) {
        if (isRefreshing) {
            CircularProgressIndicator(
                color = Primary,
                strokeWidth = 2.5.dp,
                modifier = Modifier.padding(8.dp)
            )
        } else {
            CircularProgressIndicator(
                progress = { (state.distanceFraction * 0.85f).coerceIn(0f, 0.85f) },
                color = Primary,
                strokeWidth = 2.5.dp,
                trackColor = CardSurface,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}
