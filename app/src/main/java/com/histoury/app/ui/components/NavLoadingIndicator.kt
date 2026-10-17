package com.histoury.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.histoury.app.theme.Primary

/**
 * A thin progress line under the status bar that sweeps across on every
 * page change — the quick "loading" cue that makes switching screens feel
 * deliberate while the next screen settles in. Purely visual and brief
 * (about half a second); it never blocks touches or delays navigation.
 */
@Composable
fun NavLoadingIndicator(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route

    val progress = remember { Animatable(0f) }
    val visibility = remember { Animatable(0f) }

    val isFirst = remember { booleanArrayOf(true) }

    LaunchedEffect(route) {
        if (route == null) return@LaunchedEffect
        // Not on app start: the splash/auth screen is its own loading state.
        if (isFirst[0]) {
            isFirst[0] = false
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        visibility.snapTo(1f)
        progress.animateTo(1f, tween(durationMillis = 420, easing = FastOutSlowInEasing))
        visibility.animateTo(0f, tween(durationMillis = 180))
    }

    if (visibility.value > 0f) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(2.5.dp)
                .alpha(visibility.value)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.value.coerceIn(0.02f, 1f))
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Primary)
            )
        }
    }
}
