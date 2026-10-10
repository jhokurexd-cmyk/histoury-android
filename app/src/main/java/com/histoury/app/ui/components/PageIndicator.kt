package com.histoury.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.histoury.app.theme.Primary
import kotlin.math.absoluteValue

private val DotSize = 8.dp
private val ActiveWidth = 24.dp
private val DotGap = 8.dp
private const val InactiveAlpha = 0.22f

/**
 * Pill-and-dots page indicator. The active page is a wider coral pill.
 *
 * With [position] (current page + swipe fraction, read from a pager) the
 * pill stretches and shrinks WITH the finger — it tracks the gesture rather
 * than animating after it, so it's interruptible by construction. It's drawn
 * on a Canvas and read in the draw phase, so a swipe only redraws it; it
 * never recomposes or re-measures.
 *
 * Without [position] it animates between pages on its own (250ms ease-out).
 *
 * Read by TalkBack as "Page 2 of 4".
 */
@Composable
fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
    position: (() -> Float)? = null
) {

    val semanticsModifier = modifier.semantics(mergeDescendants = true) {
        contentDescription = "Page ${currentPage + 1} of $pageCount"
    }

    if (position != null) {

        val color = Primary

        Canvas(
            modifier = semanticsModifier.size(
                width = ActiveWidth + DotSize * (pageCount - 1) + DotGap * (pageCount - 1),
                height = DotSize
            )
        ) {
            val p = position()
            val dot = DotSize.toPx()
            val active = ActiveWidth.toPx()
            val gap = DotGap.toPx()
            var x = 0f

            for (index in 0 until pageCount) {
                // 1 when this dot is the page in view, 0 a page or more away.
                val t = (1f - (p - index).absoluteValue).coerceIn(0f, 1f)
                val width = dot + (active - dot) * t

                drawRoundRect(
                    color = color.copy(alpha = InactiveAlpha + (1f - InactiveAlpha) * t),
                    topLeft = Offset(x, 0f),
                    size = Size(width, dot),
                    cornerRadius = CornerRadius(dot / 2f)
                )

                x += width + gap
            }
        }
        return
    }

    Row(
        modifier = semanticsModifier,
        horizontalArrangement = Arrangement.spacedBy(DotGap),
        verticalAlignment = Alignment.CenterVertically
    ) {

        repeat(pageCount) { index ->

            val isActive = index == currentPage

            val color by animateColorAsState(
                targetValue = if (isActive) Primary else Primary.copy(alpha = InactiveAlpha),
                animationSpec = tween(HistouryMotion.DurationMedium, easing = HistouryMotion.EaseOut),
                label = "indicatorColor"
            )

            val width by animateDpAsState(
                targetValue = if (isActive) ActiveWidth else DotSize,
                animationSpec = tween(HistouryMotion.DurationMedium, easing = HistouryMotion.EaseOut),
                label = "indicatorWidth"
            )

            Box(
                modifier = Modifier
                    .width(width)
                    .height(DotSize)
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )
        }
    }
}
