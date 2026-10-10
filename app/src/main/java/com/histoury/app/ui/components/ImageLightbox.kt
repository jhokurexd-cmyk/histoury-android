package com.histoury.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/**
 * Full-screen gallery viewer. Opens on [startIndex], supports swiping or
 * tapping the arrow buttons to browse (wraps seamlessly at both ends), and
 * closes via the X button or the system back gesture/button (handled
 * automatically by [Dialog]'s onDismissRequest).
 *
 * Wrap-around is done with the standard "virtual infinite pager" trick:
 * an effectively unbounded page count anchored near its midpoint, with the
 * real image index derived as `page % images.size`. That way both swiping
 * and the arrow buttons can just keep incrementing/decrementing the page
 * forever without any special-casing at the ends.
 */
@Composable
fun ImageLightbox(
    images: List<String>,
    startIndex: Int,
    onDismiss: () -> Unit
) {

    if (images.isEmpty()) return

    val hasMultiple = images.size > 1

    val anchor = Int.MAX_VALUE / 2

    val startPage = if (hasMultiple) {
        anchor - (anchor % images.size) + startIndex.coerceIn(0, images.lastIndex)
    } else {
        0
    }

    val pagerState = rememberPagerState(initialPage = startPage) {
        if (hasMultiple) Int.MAX_VALUE else 1
    }

    val coroutineScope = rememberCoroutineScope()

    // Safe area as the screen underneath sees it. Read here, outside the
    // Dialog, because a dialog window doesn't reliably receive insets on
    // every Android version; inside we take whichever is larger.
    val layoutDirection = LocalLayoutDirection.current
    val outerSafe = WindowInsets.safeDrawing.asPaddingValues()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {

        val innerSafe = WindowInsets.safeDrawing.asPaddingValues()
        val safeTop = maxOf(outerSafe.calculateTopPadding(), innerSafe.calculateTopPadding())
        val safeStart = maxOf(
            outerSafe.calculateStartPadding(layoutDirection),
            innerSafe.calculateStartPadding(layoutDirection)
        )
        val safeEnd = maxOf(
            outerSafe.calculateEndPadding(layoutDirection),
            innerSafe.calculateEndPadding(layoutDirection)
        )

        val currentIndex = ((pagerState.currentPage % images.size) + images.size) % images.size

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
        ) {

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->

                val imageIndex = ((page % images.size) + images.size) % images.size

                AsyncImage(
                    model = images[imageIndex],
                    contentDescription = "Gallery image ${imageIndex + 1} of ${images.size}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp)
                )
            }

            // Top controls: the counter centred and the close button on the
            // right, in one row whose centre line both share, placed below
            // the status bar and clear of any display cutout.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(
                        top = safeTop + 12.dp,
                        start = safeStart + 16.dp,
                        end = safeEnd + 16.dp
                    )
                    .height(OverlayControls.GlassButtonSize)
            ) {

                if (hasMultiple) {
                    GlassLabel(
                        text = "${currentIndex + 1} / ${images.size}",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                GlassIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = "Close",
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }

            if (hasMultiple) {

                // Same size, glass and edge spacing on both sides, centred
                // on the photo (which is itself centred by ContentScale.Fit).
                GlassIconButton(
                    icon = Icons.Default.ChevronLeft,
                    contentDescription = "Previous image",
                    iconSize = 28.dp,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = safeStart + 12.dp)
                )

                GlassIconButton(
                    icon = Icons.Default.ChevronRight,
                    contentDescription = "Next image",
                    iconSize = 28.dp,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = safeEnd + 12.dp)
                )
            }
        }
    }
}
