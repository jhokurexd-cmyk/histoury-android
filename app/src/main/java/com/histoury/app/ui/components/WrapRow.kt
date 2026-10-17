package com.histoury.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * Lays children out left to right and wraps onto a new line when the next
 * one doesn't fit — for tag chips and info pills.
 *
 * Used instead of Compose's FlowRow: FlowRow is an experimental API whose
 * signature has changed between Compose versions, and a mismatch between the
 * version the app compiles against and the one it runs with made Site
 * Information crash with NoSuchMethodError. This uses only the stable
 * Layout API, so it can't break that way.
 */
@Composable
fun WrapRow(
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = 0.dp,
    verticalSpacing: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->

        val hGap = horizontalSpacing.roundToPx()
        val vGap = verticalSpacing.roundToPx()
        val maxWidth = constraints.maxWidth

        val placeables = measurables.map {
            it.measure(Constraints(maxWidth = maxWidth))
        }

        val positions = ArrayList<IntOffset>(placeables.size)
        var x = 0
        var y = 0
        var rowHeight = 0
        var usedWidth = 0

        placeables.forEach { placeable ->
            // Wrap unless this is the first item on the line.
            if (x > 0 && maxWidth != Constraints.Infinity && x + placeable.width > maxWidth) {
                x = 0
                y += rowHeight + vGap
                rowHeight = 0
            }
            positions += IntOffset(x, y)
            usedWidth = max(usedWidth, x + placeable.width)
            x += placeable.width + hGap
            rowHeight = max(rowHeight, placeable.height)
        }

        val height = if (placeables.isEmpty()) 0 else y + rowHeight
        val width = if (maxWidth == Constraints.Infinity) usedWidth else usedWidth.coerceAtMost(maxWidth)

        layout(
            width = width.coerceAtLeast(constraints.minWidth),
            height = height.coerceIn(constraints.minHeight, constraints.maxHeight)
        ) {
            placeables.forEachIndexed { index, placeable ->
                placeable.place(positions[index])
            }
        }
    }
}
