package com.histoury.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.histoury.app.theme.Primary

@Composable
fun PageIndicator(
    pageCount: Int,
    currentPage: Int
) {

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        repeat(pageCount) { index ->

            val color by animateColorAsState(
                targetValue =
                    if (index == currentPage)
                        Primary
                    else
                        Primary.copy(alpha = 0.18f),
                animationSpec = tween(300),
                label = ""
            )

            Box(
                modifier =
                    if (index == currentPage) {
                        Modifier
                            .size(width = 26.dp, height = 8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(color)
                    } else {
                        Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(color)
                    }
            )

        }

    }

}
