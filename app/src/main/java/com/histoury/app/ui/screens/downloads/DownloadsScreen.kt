package com.histoury.app.ui.screens.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.histoury.app.data.model.HistoricalSite
import com.histoury.app.data.model.SiteContent
import com.histoury.app.data.offline.OfflineSiteManager
import com.histoury.app.data.repository.HistoricalSiteRepository
import com.histoury.app.data.repository.SiteContentRepository
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Danger
import com.histoury.app.theme.DangerSurface
import com.histoury.app.theme.Outline
import com.histoury.app.theme.Primary
import com.histoury.app.theme.PrimarySoft
import com.histoury.app.theme.SurfaceSoft
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary
import com.histoury.app.theme.TextTertiary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Preloading, for a visit that has not happened yet.
 *
 * Every site with an AR experience is listed here, downloaded or not — which
 * is the whole point of the screen. Putting the control on a site's own page
 * would mean loading that page over the network first, so preparing for a
 * trip would cost most of what simply turning up would have cost. This is
 * the screen someone opens on hotel wifi the night before.
 *
 * Only AR-capable sites are listed. A site with no model has nothing here
 * worth saving: its text and photographs are small, and Firestore already
 * keeps its own offline copy of those.
 */

/** A site together with what is known about its downloaded state. */
private data class DownloadRow(
    val site: HistoricalSite,
    val content: SiteContent?,
    val downloaded: OfflineSiteManager.SiteDownload?
)

@Composable
fun DownloadsScreen(navController: NavController) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var rows by remember { mutableStateOf<List<DownloadRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    // Sizes live apart from the list because each one costs a network round
    // trip. Keeping them separate lets the list appear at once and the
    // numbers fill in as they arrive, rather than the whole screen waiting
    // on the slowest server.
    val estimates = remember { mutableStateMapOf<String, Long?>() }
    val busy = remember { mutableStateMapOf<String, Float>() }

    suspend fun load(): List<DownloadRow> {

        val sites = HistoricalSiteRepository().getHistoricalSites()
            .filter { it.arEnabled && it.arModelUrl.isNotBlank() }

        val stored = OfflineSiteManager.listDownloads(context).associateBy { it.siteId }

        // Content is fetched for the narration URLs, concurrently. One
        // request per site in sequence would make a six-site list wait for
        // six round trips before showing anything.
        return coroutineScope {
            sites.map { site ->
                async(Dispatchers.IO) {
                    DownloadRow(
                        site = site,
                        content = SiteContentRepository().getSiteContent(site.siteId),
                        downloaded = stored[site.siteId]
                    )
                }
            }.awaitAll()
        }
    }

    LaunchedEffect(Unit) {
        rows = load()
        loading = false

        // Estimates run after the list is on screen, so a slow HEAD request
        // delays one number rather than the whole view.
        rows.forEach { row ->
            if (row.downloaded == null && !estimates.containsKey(row.site.siteId)) {
                estimates[row.site.siteId] =
                    OfflineSiteManager.estimateBytes(row.site, row.content)
            }
        }
    }

    val totalStored = rows.mapNotNull { it.downloaded }.sumOf { it.bytes }
    val storedCount = rows.count { it.downloaded != null }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceSoft)
            .statusBarsPadding()
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(CardSurface)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        navController.popBackStack()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column {
                Text(
                    text = "Offline Downloads",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = if (storedCount == 0) {
                        "Save AR experiences before you travel"
                    } else {
                        "$storedCount saved · ${OfflineSiteManager.formatBytes(totalStored)}"
                    },
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }

        when {

            loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Primary)
            }

            rows.isEmpty() -> EmptyDownloads()

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 4.dp,
                    bottom = 32.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                item {
                    Text(
                        text = "Downloading a site keeps its 3D model and " +
                            "narration on your phone, so the AR experience " +
                            "works with no signal when you get there.",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = TextTertiary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                items(rows, key = { it.site.siteId }) { row ->

                    val siteId = row.site.siteId

                    SiteDownloadCard(
                        row = row,
                        estimate = estimates[siteId],
                        progress = busy[siteId],
                        onDownload = {
                            scope.launch {
                                busy[siteId] = 0f
                                val result = OfflineSiteManager.download(
                                    context = context,
                                    site = row.site,
                                    content = row.content
                                ) { fraction -> busy[siteId] = fraction }
                                busy.remove(siteId)
                                if (result.isSuccess) rows = load()
                            }
                        },
                        onRemove = {
                            scope.launch {
                                OfflineSiteManager.delete(context, siteId)
                                estimates.remove(siteId)
                                rows = load()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SiteDownloadCard(
    row: DownloadRow,
    estimate: Long?,
    progress: Float?,
    onDownload: () -> Unit,
    onRemove: () -> Unit
) {

    val stored = row.downloaded
    val working = progress != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardSurface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        AsyncImage(
            model = row.site.featuredImage,
            contentDescription = null,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Outline)
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {

            Text(
                text = row.site.siteName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1
            )

            Spacer(Modifier.height(3.dp))

            Text(
                text = when {
                    working -> "Downloading… ${((progress ?: 0f) * 100).toInt()}%"
                    stored != null -> "Saved · ${OfflineSiteManager.formatBytes(stored.bytes)}"
                    // Says nothing rather than guessing when a size cannot be
                    // had: the estimate needs every server to report one, and
                    // a confident wrong number is worse than none.
                    estimate != null -> "About ${OfflineSiteManager.formatBytes(estimate)}"
                    else -> "3D model and narration"
                },
                fontSize = 11.5.sp,
                color = if (stored != null) Primary else TextSecondary
            )
        }

        Spacer(Modifier.width(10.dp))

        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (stored != null) DangerSurface else PrimarySoft)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = !working
                ) {
                    if (stored != null) onRemove() else onDownload()
                },
            contentAlignment = Alignment.Center
        ) {
            when {
                working -> CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = Primary,
                    modifier = Modifier.size(15.dp)
                )

                stored != null -> Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove ${row.site.siteName}",
                    tint = Danger,
                    modifier = Modifier.size(17.dp)
                )

                else -> Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Download ${row.site.siteName}",
                    tint = Primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyDownloads() {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(PrimarySoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(30.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "No AR sites yet",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(Modifier.height(6.dp))

        // Explains why the list is empty, not just that it is. A site has to
        // have an AR experience published before there is anything to save.
        Text(
            text = "Sites appear here once they have an AR experience " +
                "published. Check back before your visit.",
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}
