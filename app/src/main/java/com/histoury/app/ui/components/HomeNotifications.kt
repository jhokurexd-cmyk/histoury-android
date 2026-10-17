package com.histoury.app.ui.components

import com.histoury.app.theme.SheetSurface
import androidx.compose.foundation.layout.heightIn
import com.histoury.app.theme.OnColor
import com.histoury.app.theme.TextTertiary
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Primary
import com.histoury.app.theme.PrimarySoft
import com.histoury.app.theme.SurfaceSoft
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary

/**
 * Home's notification bell: the same 44dp round button as the menu, with a
 * red dot while there's something the visitor hasn't looked at yet.
 */
@Composable
fun NotificationBellButton(
    count: Int,
    hasUnseen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        HeroIconButton(
            icon = if (count > 0) Icons.Default.Notifications else Icons.Default.NotificationsNone,
            contentDescription = when (count) {
                0 -> "Notifications"
                1 -> "Notifications, 1 item"
                else -> "Notifications, $count items"
            },
            onClick = onClick
        )
        if (hasUnseen && count > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-6).dp, y = 6.dp)
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE53935))
                    .border(2.dp, CardSurface, CircleShape)
            )
        }
    }
}

/**
 * What the bell held the last time it was opened (for the whole app
 * session), so the red dot only comes back when something new arrives.
 */
object HomeNotificationsSeen {
    var signature by mutableStateOf<String?>(null)
}

/** What the bell can hold. Each part is null when there's nothing to show. */
data class HomeNotificationsState(
    /** The phone's location service is off (and Histoury has permission to use it). */
    val locationOff: Boolean = false,
    /** "Intramuros Walk · 2 of 5 stops done" — a trip that's under way. */
    val tripTitle: String? = null,
    val tripDetail: String? = null,
    /** Trips saved but not started yet, and up to two of their names. */
    val savedTripCount: Int = 0,
    val savedTripNames: List<String> = emptyList(),
    /** How many AR sites aren't downloaded yet (0 = nothing to offer). */
    val missingDownloads: Int = 0,
    /** 0..1 while "Download All" is running, else null. */
    val downloadProgress: Float? = null,
    val downloadingName: String? = null
) {
    val count: Int
        get() = (if (locationOff) 1 else 0) +
            (if (tripTitle != null) 1 else 0) +
            (if (savedTripCount > 0) 1 else 0) +
            (if (missingDownloads > 0 || downloadProgress != null) 1 else 0)
}

/**
 * The bell's contents: the trip to resume (moved here from the card that
 * used to take up the top of Home) and the Download Resources offer (which
 * stays here after its pop-up is closed).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeNotificationsSheet(
    state: HomeNotificationsState,
    onTurnOnLocation: () -> Unit = {},
    onResumeTrip: () -> Unit,
    onViewSavedTrips: () -> Unit = {},
    onDownloadAll: () -> Unit,
    onChooseDownloads: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SheetSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "Notifications",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                if (state.count == 0) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsNone,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "You're all caught up.",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }

                // First, since it stops everything else that uses the map.
                if (state.locationOff) {
                    NotificationItem(
                        icon = Icons.Default.LocationOff,
                        title = "Location Is Off",
                        body = "Navigation, nearby sites and arrival alerts won't work until you turn it on.",
                        primaryLabel = "Turn On",
                        onPrimary = onTurnOnLocation
                    )
                }

                if (state.tripTitle != null) {
                    NotificationItem(
                        icon = Icons.Default.LocationOn,
                        title = "Continue Your Trip",
                        body = state.tripTitle,
                        meta = state.tripDetail,
                        primaryLabel = "Resume Trip",
                        onPrimary = onResumeTrip
                    )
                }

                if (state.savedTripCount > 0) {
                    val count = state.savedTripCount
                    val shown = state.savedTripNames.take(2)
                    val more = count - shown.size
                    NotificationItem(
                        icon = Icons.Default.Map,
                        title = if (count == 1) "You have 1 saved itinerary" else "You have $count saved itineraries",
                        body = if (shown.isEmpty()) {
                            "Ready to start whenever you are."
                        } else {
                            shown.joinToString(", ") + (if (more > 0) " +$more more" else "")
                        },
                        meta = if (shown.isEmpty()) null else "Ready to start whenever you are",
                        primaryLabel = "View",
                        onPrimary = onViewSavedTrips
                    )
                }

                if (state.downloadProgress != null) {
                    NotificationItem(
                        icon = Icons.Default.Download,
                        title = "Downloading Resources",
                        body = "Saving AR experiences to your phone.",
                        meta = state.downloadingName?.let { "Now: $it" },
                        progress = state.downloadProgress,
                        secondaryLabel = "View",
                        onSecondary = onChooseDownloads
                    )
                } else if (state.missingDownloads > 0) {
                    NotificationItem(
                        icon = Icons.Default.Download,
                        title = "Download Resources",
                        body = (if (state.missingDownloads == 1) "1 AR experience" else "${state.missingDownloads} AR experiences") +
                            " can be saved for faster loading on site.",
                        primaryLabel = "Download All",
                        onPrimary = onDownloadAll,
                        secondaryLabel = "Choose sites",
                        onSecondary = onChooseDownloads
                    )
                }
            }
        }
    }
}

/**
 * One compact notification row: icon, title (strongest), a short muted
 * description, optional metadata and progress. A single action sits at the
 * right of the row; with two actions they share a slim row under the text
 * — the primary as a small coral pill, the secondary as plain text.
 */
@Composable
private fun NotificationItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    meta: String? = null,
    primaryLabel: String? = null,
    onPrimary: () -> Unit = {},
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {},
    progress: Float? = null
) {
    val twoActions = primaryLabel != null && secondaryLabel != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceSoft)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(PrimarySoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = body,
                    fontSize = 12.5.sp,
                    lineHeight = 16.sp,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!meta.isNullOrBlank()) {
                    Text(
                        text = meta,
                        fontSize = 11.5.sp,
                        color = TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // One action: right beside the text, no extra row.
            if (!twoActions) {
                when {
                    primaryLabel != null -> {
                        Spacer(Modifier.width(8.dp))
                        CompactPrimaryAction(primaryLabel, onPrimary)
                    }
                    secondaryLabel != null -> {
                        Spacer(Modifier.width(4.dp))
                        CompactTextAction(secondaryLabel, onSecondary)
                    }
                }
            }
        }

        if (progress != null) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                color = Primary,
                trackColor = PrimarySoft,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
            )
        }

        if (twoActions) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactTextAction(secondaryLabel!!, onSecondary)
                Spacer(Modifier.width(6.dp))
                CompactPrimaryAction(primaryLabel!!, onPrimary)
            }
        }
    }
}

/** Small coral pill — the stronger action. */
@Composable
private fun CompactPrimaryAction(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 32.dp)
            .clip(RoundedCornerShape(50))
            .background(Primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = OnColor,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/** Plain text action — the secondary one. */
@Composable
private fun CompactTextAction(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 32.dp)
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
