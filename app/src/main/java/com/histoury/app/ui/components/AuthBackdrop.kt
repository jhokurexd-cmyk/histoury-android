package com.histoury.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.histoury.app.theme.PrimarySoft
import com.histoury.app.theme.PrimarySoftStrong
import com.histoury.app.theme.SurfaceSoft

/**
 * The shared background for Login, Register and the onboarding hand-off:
 * a solid SurfaceSoft field with two soft coral-tint discs at the top
 * corner. The same shapes as the onboarding illustrations, so going from
 * "Get Started" into the forms feels like one continuous place. Solid
 * fills only, no gradients; the theme tokens switch with dark mode.
 */
@Composable
fun AuthBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceSoft)
    ) {

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 110.dp, y = (-120).dp)
                .size(300.dp)
                .clip(CircleShape)
                .background(PrimarySoft)
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-28).dp, y = 200.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(PrimarySoftStrong)
        )

        content()
    }
}
