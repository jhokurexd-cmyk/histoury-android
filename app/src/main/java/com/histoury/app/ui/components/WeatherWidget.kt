package com.histoury.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Brightness2
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.histoury.app.data.model.Weather
import com.histoury.app.data.model.WeatherCondition
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextTertiary

// Weather-only accents. Kept local to this file rather than added to the
// brand palette in theme/Color.kt, since they're illustrative icon colors
// and shouldn't be reused as UI colors elsewhere.
private val SunYellow = Color(0xFFFFC93C)
private val CloudBlue = Color(0xFF2BA6E8)
private val CloudGrey = Color(0xFF9AA4B2)

// Hard ceiling on how much of the header row the widget may claim. Without
// it, a long condition label ("Thunderstorm") or a 2x font scale lets the
// widget expand and crowd the greeting text next to it.
private val MaxWidth = 116.dp
private val MaxTextWidth = 80.dp

/**
 * Compact current-conditions readout for the Home header: an icon, the
 * temperature in Celsius, and a one-word condition underneath.
 *
 * Sized to sit between the greeting block and the menu button without
 * pushing either off-screen, so the whole row still fits a 360dp phone.
 *
 * The widget is width-capped and every label is single-line, so a long
 * condition word or a large system font scale can never let it grow into
 * the greeting beside it.
 */
@Composable
fun WeatherWidget(
    weather: Weather?,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {}
) {

    // First load — show a muted placeholder rather than a spinner, so the
    // header doesn't visibly "flash" on every Home entry.
    if (weather == null) {

        Row(
            modifier = modifier
                .widthIn(max = MaxWidth)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = !isLoading,
                    onClick = onRetry
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Icon(
                imageVector = Icons.Default.Cloud,
                contentDescription = null,
                tint = CloudGrey.copy(alpha = 0.45f),
                modifier = Modifier.size(22.dp)
            )

            Spacer(Modifier.width(6.dp))

            Column(
                modifier = Modifier.widthIn(max = MaxTextWidth),
                horizontalAlignment = Alignment.Start
            ) {

                Text(
                    text = "--°",
                    color = TextTertiary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip
                )

                Text(
                    text = if (isLoading) "Loading" else "Tap to retry",
                    color = TextTertiary,
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        return
    }

    Row(
        modifier = modifier
            .widthIn(max = MaxWidth)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !isLoading,
                onClick = onRetry
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {

        WeatherIcon(
            condition = weather.condition,
            isDay = weather.isDay,
            size = 28.dp
        )

        Spacer(Modifier.width(6.dp))

        Column(
            modifier = Modifier.widthIn(max = MaxTextWidth),
            horizontalAlignment = Alignment.Start
        ) {

            Text(
                text = "${weather.temperatureC}°",
                color = TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )

            Text(
                text = weather.condition.label(weather.isDay),
                color = TextTertiary,
                fontSize = 9.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * One icon per condition bucket. "Mostly clear" gets a layered sun-behind-
 * cloud built from two icons, since Material has no single glyph for it.
 */
@Composable
private fun WeatherIcon(
    condition: WeatherCondition,
    isDay: Boolean,
    size: Dp
) {

    when (condition) {

        WeatherCondition.CLEAR -> Icon(
            imageVector = if (isDay) Icons.Default.WbSunny else Icons.Default.Brightness2,
            contentDescription = "Clear",
            tint = SunYellow,
            modifier = Modifier.size(size * 0.82f)
        )

        WeatherCondition.MOSTLY_CLEAR -> SunBehindCloud(
            isDay = isDay,
            size = size
        )

        WeatherCondition.CLOUDY, WeatherCondition.OVERCAST -> Icon(
            imageVector = Icons.Default.Cloud,
            contentDescription = "Cloudy",
            tint = CloudGrey,
            modifier = Modifier.size(size * 0.88f)
        )

        WeatherCondition.FOG -> Icon(
            imageVector = Icons.Default.BlurOn,
            contentDescription = "Foggy",
            tint = CloudGrey,
            modifier = Modifier.size(size * 0.85f)
        )

        WeatherCondition.DRIZZLE,
        WeatherCondition.RAIN,
        WeatherCondition.SHOWERS -> Icon(
            imageVector = Icons.Default.Grain,
            contentDescription = "Rain",
            tint = CloudBlue,
            modifier = Modifier.size(size * 0.85f)
        )

        WeatherCondition.THUNDERSTORM -> Icon(
            imageVector = Icons.Default.FlashOn,
            contentDescription = "Thunderstorm",
            tint = SunYellow,
            modifier = Modifier.size(size * 0.88f)
        )
    }
}

@Composable
private fun SunBehindCloud(
    isDay: Boolean,
    size: Dp
) {

    Box(modifier = Modifier.size(size)) {

        // Sun peeks out of the top-right corner, drawn first so the cloud
        // overlaps it — same stacking as the reference design.
        Icon(
            imageVector = if (isDay) Icons.Default.WbSunny else Icons.Default.Brightness2,
            contentDescription = null,
            tint = SunYellow,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(size * 0.58f)
                .offset(x = 1.dp, y = (-1).dp)
        )

        Icon(
            imageVector = Icons.Default.Cloud,
            contentDescription = "Partly cloudy",
            tint = CloudBlue,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(size * 0.74f)
        )
    }
}
