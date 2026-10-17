package com.histoury.app.ui.components

import com.histoury.app.theme.overlayShadow
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Primary
import com.histoury.app.theme.Success
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary
import com.histoury.app.util.ConnectivityObserver
import kotlinx.coroutines.delay

/**
 * The state the visitor was last told about, kept for the whole app
 * process. Starts as "online", so an app opened online shows nothing and one
 * opened offline says so. Kept outside the composable so the banner leaving
 * the screen (the floating mini map, a rotation) doesn't make it repeat
 * "You're offline" — or miss "Back online" — when it comes back.
 */
private object LastAnnounced {
    @Volatile
    var online: Boolean = true
}

private const val OFFLINE_NOTICE_MS = 6_000L
private const val ONLINE_NOTICE_MS = 3_000L

/** Ignore blips shorter than this (e.g. switching from Wi-Fi to mobile data). */
private const val SETTLE_MS = 1_500L

/**
 * App-wide "You're offline" / "Back online" notice.
 *
 * A small card that slides in under the status bar when the connection
 * changes and dismisses itself: shown once per change, never repeated while
 * the state stays the same, and nothing is shown at launch when the phone
 * is online. Placed once, over the whole navigation graph (MainActivity), so
 * it works on every screen.
 */
@Composable
fun ConnectivityBanner(modifier: Modifier = Modifier) {

    val context = LocalContext.current

    // null = nothing to show.
    var notice by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        ConnectivityObserver.observe(context).collect { online ->
            // Let short blips settle before saying anything.
            delay(SETTLE_MS)
            val settled = ConnectivityObserver.isOnline(context)
            if (settled != online || settled == LastAnnounced.online) return@collect
            LastAnnounced.online = settled
            notice = settled
        }
    }

    LaunchedEffect(notice) {
        val current = notice ?: return@LaunchedEffect
        delay(if (current) ONLINE_NOTICE_MS else OFFLINE_NOTICE_MS)
        notice = null
    }

    var lastShown by remember { mutableStateOf(true) }
    notice?.let { lastShown = it }

    AnimatedVisibility(
        visible = notice != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier
    ) {
        val online = lastShown
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .widthIn(max = 480.dp)
                .overlayShadow(8.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(CardSurface)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (online) Icons.Default.Wifi else Icons.Default.CloudOff,
                contentDescription = null,
                tint = if (online) Success else Primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = if (online) "Back online" else "You're offline",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (online) {
                        "Your internet connection has been restored."
                    } else {
                        "Some features may be limited, but downloaded content can still be used."
                    },
                    color = TextSecondary,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp
                )
            }
        }
    }
}
