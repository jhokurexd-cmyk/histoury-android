package com.histoury.app.ui.components

import com.histoury.app.theme.SheetSurface
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.histoury.app.theme.TextTertiary
import com.histoury.app.data.model.HistoricalSite
import com.histoury.app.data.offline.OfflineSiteManager
import com.histoury.app.data.offline.ResourcePack
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Primary
import com.histoury.app.theme.PrimarySoft
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary
import com.histoury.app.util.ConnectivityObserver

/**
 * "Download Resources" pop-up. Shown prominently the first time the visitor
 * meets the feature, and after that only when a new AR experience has been
 * published since they last answered it — not on every launch. Closing it
 * is respected. Offline Downloads stays in the Menu, and the bell on Home
 * keeps a "Download Resources" entry, so it is never more than a tap away.
 *
 * Downloading everything stays optional: "Download All" follows the same
 * Wi-Fi / mobile-data rules as every other download (see DownloadGate), and
 * "Choose sites" opens Offline Downloads. Never shown when everything is
 * already downloaded, or when the phone is offline.
 *
 * Place it once on Home, outside the list.
 */
@Composable
fun ResourcePackPrompt(onChoose: () -> Unit) {
    // onChoose opens Offline Downloads (used by "Download All" too).

    val context = LocalContext.current
    val gate = rememberDownloadGate()

    var missing by remember { mutableStateOf<List<HistoricalSite>>(emptyList()) }
    var sizeBytes by remember { mutableStateOf<Long?>(null) }
    var show by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (ResourcePack.state.value !is ResourcePack.State.Idle) return@LaunchedEffect
        if (!ConnectivityObserver.isOnline(context)) return@LaunchedEffect

        val sites = runCatching { ResourcePack.missingSites(context) }.getOrNull() ?: return@LaunchedEffect
        if (!ResourcePack.shouldOfferPrompt(context, sites)) return@LaunchedEffect

        missing = sites
        show = true

        // The size fills in once known; the pop-up doesn't wait for it.
        sizeBytes = runCatching { ResourcePack.estimateBytes(sites) }.getOrNull()
    }

    if (!show) return

    val heroImage = missing.firstOrNull { it.featuredImage.isNotBlank() }?.featuredImage
    // Closing keeps it one tap away: it stays under the bell on Home.
    val later = {
        show = false
        ResourcePack.markPromptHandled(context)
        Toast.makeText(
            context,
            "No problem. You can download later from Offline Downloads in the Menu.",
            Toast.LENGTH_SHORT
        ).show()
    }

    Dialog(
        onDismissRequest = later,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .widthIn(max = 420.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(SheetSurface)
        ) {

            // ----- hero: a real site photo, so it reads as Histoury, not a system prompt
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(172.dp)
                    .background(PrimarySoft)
            ) {
                if (heroImage != null) {
                    AsyncImage(
                        model = heroImage,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.10f),
                                0.45f to Color.Black.copy(alpha = 0.25f),
                                1f to Color.Black.copy(alpha = 0.80f)
                            )
                        )
                )

                // Badge
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(14.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ViewInAr,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "AR RESOURCE PACK",
                        color = Color.White,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }

                // Later (X)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(onClick = later),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Later",
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = "Take Intramuros with you",
                        color = Color.White,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 25.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Download AR before your visit for faster, more reliable " +
                            "loading, even where the signal is weak.",
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp
                    )
                }
            }

            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {

                // ----- which sites
                Row(verticalAlignment = Alignment.CenterVertically) {

                    SiteStack(missing)

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = siteSummary(missing),
                            color = TextPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 18.sp
                        )
                        Text(
                            text = buildString {
                                append(if (missing.size == 1) "1 site" else "${missing.size} sites")
                                append(" · ")
                                append(sizeBytes?.let { "about " + OfflineSiteManager.formatBytes(it) } ?: "checking size\u2026")
                            },
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ----- what you get
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Perk(Icons.Default.Bolt, "Instant AR", Modifier.weight(1f))
                    Perk(Icons.Default.CloudOff, "Works offline", Modifier.weight(1f))
                    Perk(Icons.Default.Headphones, "Narration", Modifier.weight(1f))
                }

                Spacer(Modifier.height(18.dp))

                PrimaryButton(
                    text = "Download All",
                    onClick = {
                        show = false
                        ResourcePack.markPromptHandled(context)
                        val sites = missing
                        gate.request(
                            bulk = true,
                            size = { sizeBytes ?: ResourcePack.estimateBytes(sites) }
                        ) { allowMobile ->
                            ResourcePack.downloadAll(context, sites, allowMobile)
                            // Straight to Offline Downloads, where each site's
                            // progress is shown as it downloads.
                            onChoose()
                        }
                    }
                )

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "Wi-Fi recommended",
                        color = TextTertiary,
                        fontSize = 11.5.sp
                    )
                    Text(
                        text = "  \u00B7  ",
                        color = TextTertiary,
                        fontSize = 11.5.sp
                    )
                    Text(
                        text = "Choose sites",
                        color = Primary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                show = false
                                ResourcePack.markPromptHandled(context)
                                onChoose()
                            }
                            .padding(horizontal = 4.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

/** Up to three overlapping site photos, then "+N". */
@Composable
private fun SiteStack(sites: List<HistoricalSite>) {
    val shown = sites.take(3)
    val extra = sites.size - shown.size
    val size = 36.dp
    val overlap = 12.dp
    val count = shown.size + if (extra > 0) 1 else 0

    Box(modifier = Modifier.width(size + (size - overlap) * (count - 1).coerceAtLeast(0))) {
        shown.forEachIndexed { index, site ->
            Box(
                modifier = Modifier
                    .padding(start = (size - overlap) * index)
                    .size(size)
                    .clip(CircleShape)
                    .background(CardSurface)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(PrimarySoft),
                contentAlignment = Alignment.Center
            ) {
                if (site.featuredImage.isNotBlank()) {
                    AsyncImage(
                        model = site.featuredImage,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.ViewInAr,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        if (extra > 0) {
            Box(
                modifier = Modifier
                    .padding(start = (size - overlap) * shown.size)
                    .size(size)
                    .clip(CircleShape)
                    .background(CardSurface)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(Primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+$extra",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun Perk(icon: ImageVector, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(PrimarySoft)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

private fun siteSummary(sites: List<HistoricalSite>): String {
    val names = sites.map { it.siteName }.filter { it.isNotBlank() }
    return when {
        names.isEmpty() -> "AR experiences for your visit"
        names.size == 1 -> names[0]
        names.size == 2 -> "${names[0]} & ${names[1]}"
        else -> "${names[0]}, ${names[1]} & ${names.size - 2} more"
    }
}
