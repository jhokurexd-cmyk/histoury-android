package com.histoury.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.histoury.app.data.repository.RouteStep
import com.histoury.app.map.MapConfig
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Primary
import com.histoury.app.theme.Success
import com.histoury.app.theme.SurfaceSoft
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary

// The commute plan as a step-by-step timeline (walk, ride, walk... arrive).
// Shared by the Map screen's directions sheet and the itinerary's
// stop-to-stop commute directions.

/** "#1E88E5" -> Color, or null when Google sent nothing usable. */
fun transitLineColor(hex: String): Color? = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrNull()

/**
 * The commute plan as a short list: each walk and each ride, in order,
 * using only what Google returned. Rides show the line, direction, where to
 * get on and off, and the number of stops; nothing is guessed.
 */
/** One row of the commute timeline: a walk (merged) or a ride. */
private sealed interface CommutePart {
    data class Walk(val meters: Int, val seconds: Int) : CommutePart
    data class Ride(val step: RouteStep) : CommutePart
}

/**
 * Commute plan as a vertical timeline: a node for each part (walk or ride)
 * joined by a line — dotted for walking, solid in the route's own colour for
 * a ride — ending at the destination.
 */
@Composable
fun CommuteTimeline(
    steps: List<RouteStep>,
    destinationName: String?,
    // The note about mapped routes and estimates; once per screen is enough.
    showFootnote: Boolean = true,
    topSpacing: Dp = 12.dp
) {

    // Consecutive walking steps read as one "Walk 600 m" part.
    val parts = remember(steps) {
        val out = mutableListOf<CommutePart>()
        steps.forEach { step ->
            val last = out.lastOrNull()
            if (step.isTransitRide) {
                out += CommutePart.Ride(step)
            } else if (last is CommutePart.Walk) {
                out[out.lastIndex] = CommutePart.Walk(last.meters + step.distanceMeters, last.seconds + step.durationSeconds)
            } else {
                out += CommutePart.Walk(step.distanceMeters, step.durationSeconds)
            }
        }
        out
    }

    Spacer(Modifier.height(topSpacing))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceSoft)
            .padding(start = 12.dp, end = 14.dp, top = 14.dp, bottom = 12.dp)
    ) {
        parts.forEach { part ->
            when (part) {
                is CommutePart.Walk -> TimelineRow(
                    node = { WalkNode() },
                    connectorColor = TextSecondary.copy(alpha = 0.55f),
                    dotted = true
                ) {
                    Text(
                        text = buildString {
                            append("Walk ").append(commuteMeters(part.meters))
                            if (part.seconds > 0) append(" · about ").append(commuteMinutes(part.seconds))
                        },
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                is CommutePart.Ride -> {
                    val info = part.step.transit
                    val lineColor = info?.lineColor?.let(::transitLineColor) ?: Primary
                    TimelineRow(
                        node = { RideNode(info?.vehicleType, lineColor) },
                        connectorColor = lineColor,
                        dotted = false
                    ) {
                        RideDetails(part.step, lineColor)
                    }
                }
            }
        }

        // Where it ends.
        TimelineRow(
            node = {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Success),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            },
            connectorColor = null,
            dotted = false
        ) {
            Text(
                text = destinationName?.let { "Arrive at $it" } ?: "Arrive",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (showFootnote) {
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (MapConfig.useMapLibre) {
                "Trains, buses and jeepneys mapped in OpenStreetMap. Ride and wait times are estimates, " +
                    "not schedules, and fares aren't shown. Not every jeepney or bus route is mapped yet."
            } else {
                "Public transport from Google: trains and buses. Jeepney and UV Express routes may not be listed."
            },
            color = TextSecondary,
            fontSize = 10.5.sp,
            lineHeight = 14.sp,
            textAlign = TextAlign.Justify,
            letterSpacing = 0.sp // see JustifiedReading in SiteContentTabs.kt
        )
        }
    }
}

/**
 * Left: the node and the line down to the next node. Right: the content.
 * The row's height follows its content, so the line always reaches the
 * next node exactly.
 */
@Composable
private fun TimelineRow(
    node: @Composable () -> Unit,
    connectorColor: Color?,
    dotted: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        Box(
            modifier = Modifier
                .width(30.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.TopCenter
        ) {
            if (connectorColor != null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val x = size.width / 2f
                    val top = 24.dp.toPx()
                    val stroke = 3.dp.toPx()
                    drawLine(
                        color = connectorColor,
                        start = Offset(x, top),
                        end = Offset(x, size.height + 2.dp.toPx()),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                        pathEffect = if (dotted) {
                            PathEffect.dashPathEffect(floatArrayOf(0.1f, 6.dp.toPx()), 0f)
                        } else null
                    )
                }
            }
            node()
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp, top = 2.dp, bottom = if (connectorColor != null) 16.dp else 0.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun WalkNode() {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(CardSurface)
            .border(1.5.dp, TextSecondary.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.DirectionsWalk, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun RideNode(vehicleType: String?, color: Color) {
    val icon = when (vehicleType) {
        "BUS", "INTERCITY_BUS", "TROLLEYBUS", "SHARE_TAXI" -> Icons.Default.DirectionsBus
        "SUBWAY", "LIGHT_RAIL", "HEAVY_RAIL", "RAIL", "TRAM", "MONORAIL", "COMMUTER_TRAIN", "HIGH_SPEED_TRAIN", "METRO_RAIL" -> Icons.Default.Train
        else -> Icons.Default.DirectionsTransit
    }
    // Light route colours (e.g. MRT-3 yellow) get a dark icon so it stays visible.
    val iconTint = if (color.luminance() > 0.6f) Color(0xFF1F1F1F) else Color.White
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun RideDetails(step: RouteStep, lineColor: Color) {
    val info = step.transit
    val vehicle = info?.vehicleName ?: "Ride"
    val name = info?.lineName ?: info?.label

    // "JEEPNEY", "BUS", "TRAIN": what to look for, in the route's colour.
    Text(
        text = vehicle.uppercase(),
        color = if (lineColor.luminance() > 0.6f) TextSecondary else lineColor,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp
    )
    if (name != null) {
        Text(
            text = name,
            color = TextPrimary,
            fontSize = 13.5.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
    info?.headsign?.let { towards ->
        Text(
            text = "Towards $towards",
            color = TextSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    // Where to get on and off. Named stops when the route has them;
    // otherwise (jeepneys stop anywhere) how far to ride, shown on the map.
    val from = info?.departureStop
    val to = info?.arrivalStop
    val minutes = step.durationSeconds.takeIf { it > 0 }?.let { "about ${commuteMinutes(it)}" }
    val detail = if (from != null && to != null) {
        listOfNotNull(
            "$from → $to",
            info.stopCount?.let { if (it == 1) "1 stop" else "$it stops" },
            minutes
        ).joinToString(" · ")
    } else {
        listOfNotNull(
            "Get off after about ${commuteMeters(step.distanceMeters)} (see map)",
            minutes
        ).joinToString(" · ")
    }
    Spacer(Modifier.height(4.dp))
    Text(
        text = detail,
        color = TextPrimary,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CardSurface)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

internal fun commuteMeters(meters: Int): String =
    if (meters >= 1000) "%.1f km".format(meters / 1000f) else "${(meters / 10) * 10} m"

internal fun commuteMinutes(seconds: Int): String {
    val min = ((seconds + 30) / 60).coerceAtLeast(1)
    return if (min < 60) "$min min" else "${min / 60} h ${min % 60} min"
}


/**
 * "Jeepney → Train", the rides of a commute leg in order, or null when the
 * leg is walking only (walking was quicker, or nothing is mapped nearby).
 */
fun commuteRideSummary(steps: List<RouteStep>): String? {
    val rides = steps.filter { it.isTransitRide }
        .map { it.transit?.vehicleName ?: "Ride" }
    if (rides.isEmpty()) return null
    // "Jeepney → Jeepney" reads as a transfer, which it is.
    return rides.joinToString(" \u2192 ")
}
