package com.histoury.app.ui.screens.downloads

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.layout.ContentScale
import com.histoury.app.theme.Divider
import com.histoury.app.navigation.safePopBackStack
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Pause
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.histoury.app.data.model.HistoricalSite
import com.histoury.app.data.model.SiteContent
import com.histoury.app.data.model.isArReady
import com.histoury.app.data.offline.OfflineSiteManager
import com.histoury.app.data.offline.OfflineSiteManager.LocalState
import com.histoury.app.data.offline.OfflineSiteManager.Phase
import com.histoury.app.data.offline.ResourcePack
import com.histoury.app.data.repository.HistoricalSiteRepository
import com.histoury.app.data.repository.SiteContentRepository
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Danger
import com.histoury.app.theme.Outline
import com.histoury.app.theme.Primary
import com.histoury.app.theme.PrimarySoft
import com.histoury.app.theme.SurfaceSoft
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary
import com.histoury.app.theme.TextTertiary
import com.histoury.app.ui.components.DownloadSettingsSheet
import com.histoury.app.data.offline.DownloadPreferences
import com.histoury.app.ui.components.PrimaryButton
import com.histoury.app.ui.components.rememberDownloadGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Preloading, for a visit that has not happened yet.
 *
 * Every site with an AR experience is listed here, downloaded or not — the
 * screen someone opens on hotel Wi-Fi the night before. Each card shows the
 * download size and its state (Not downloaded, Downloading, Downloaded,
 * Waiting for Wi-Fi, Waiting for connection, Update available, Updating,
 * Paused, Failed). Downloads themselves run in OfflineSiteManager's queue,
 * so they keep going when the visitor leaves this screen.
 *
 * The "Download over" and "Automatic resource updates" settings open from
 * the gear in the header (also in Menu > Preferences).
 */

/** A site together with what is known about its downloaded state. */
private data class DownloadRow(
    val site: HistoricalSite,
    val content: SiteContent?,
    val downloaded: OfflineSiteManager.SiteDownload?,
    val state: LocalState
)

private const val SIZE_CHECKING = -1L

@Composable
fun DownloadsScreen(navController: NavController) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gate = rememberDownloadGate()

    var rows by remember { mutableStateOf<List<DownloadRow>>(emptyList()) }
    var showSettings by remember { mutableStateOf(false) }

    if (showSettings) {
        DownloadSettingsSheet(onDismiss = { showSettings = false })
    }

    val downloadOver by DownloadPreferences.downloadOver.collectAsState()
    var loading by remember { mutableStateOf(true) }

    // Sizes live apart from the list because each unknown one costs a
    // network round trip; the list appears at once and numbers fill in.
    // Full size per site (for "AR model + narration • 39 MB"), and the size
    // an update would actually download.
    val estimates = remember { mutableStateMapOf<String, Long?>() }
    val updateSizes = remember { mutableStateMapOf<String, Long?>() }

    val tasks by OfflineSiteManager.tasks.collectAsState()
    val changed by OfflineSiteManager.changed.collectAsState()
    val groupIds by ResourcePack.runSiteIds.collectAsState()

    suspend fun load(): List<DownloadRow> {
        val sites = HistoricalSiteRepository().getHistoricalSites().filter { it.isArReady }
        // Content (for the narration clips) fetched concurrently.
        return coroutineScope {
            sites.map { site ->
                async(Dispatchers.IO) {
                    val content = SiteContentRepository().getSiteContent(site.siteId)
                    DownloadRow(
                        site = site,
                        content = content,
                        downloaded = OfflineSiteManager.downloadFor(context, site.siteId),
                        state = OfflineSiteManager.localState(context, site, content)
                    )
                }
            }.awaitAll()
        }
    }

    suspend fun refreshSizes(list: List<DownloadRow>) {
        list.forEach { row ->
            val id = row.site.siteId
            if (!estimates.containsKey(id) || estimates[id] == null) {
                estimates[id] = OfflineSiteManager.estimateBytes(row.site, row.content)
            }
            if (row.state == LocalState.UPDATE_AVAILABLE || row.state == LocalState.DAMAGED) {
                updateSizes[id] = OfflineSiteManager.estimateMissingBytes(context, row.site, row.content)
            }
        }
    }

    // Reloads whenever files on disk change (a download finished, an update
    // replaced a model, a site was removed).
    LaunchedEffect(changed) {
        rows = load()
        loading = false
        refreshSizes(rows)
    }

    // Opening the screen is a good moment to look for newer versions
    // (queued automatically unless automatic updates are off).
    LaunchedEffect(Unit) {
        runCatching { OfflineSiteManager.checkForUpdates(context) }
    }

    // A download that needs the visitor (after the automatic retries) is
    // explained once. Group downloads report once at the end instead.
    LaunchedEffect(Unit) {
        OfflineSiteManager.finished.collect { (siteId, result) ->
            if (result.isFailure && siteId !in ResourcePack.runSiteIds.value) {
                val name = rows.firstOrNull { it.site.siteId == siteId }?.site?.siteName ?: "this site"
                android.widget.Toast.makeText(
                    context,
                    "Couldn't download $name. Tap retry to try again.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun startSite(row: DownloadRow) {
        gate.request { allowMobile ->
            OfflineSiteManager.startDownload(
                context = context,
                site = row.site,
                content = row.content,
                allowMobile = allowMobile,
                isUpdate = row.downloaded != null
            )
        }
    }

    // ----- summary numbers -----
    val notDownloaded = rows.filter { it.downloaded == null && tasks[it.site.siteId]?.isPending != true }
    val allSizesKnown = notDownloaded.all { estimates[it.site.siteId] != null }
    val totalMissingBytes = if (allSizesKnown) notDownloaded.sumOf { estimates[it.site.siteId] ?: 0L } else SIZE_CHECKING
    val allDownloaded = rows.isNotEmpty() && rows.all { it.downloaded != null }

    val totalStored = rows.mapNotNull { it.downloaded }.sumOf { it.bytes }
    val storedCount = rows.count { it.downloaded != null }

    // Everything the visitor would have with all sites saved: what's on the
    // phone plus what's still to download (unknown until every size is in).
    val pendingBytes = rows
        .filter { it.downloaded == null && tasks[it.site.siteId]?.isPending == true }
        .sumOf { row -> tasks[row.site.siteId]?.bytesTotal?.takeIf { it > 0L } ?: estimates[row.site.siteId] ?: 0L }
    val totalAllBytes = if (totalMissingBytes == SIZE_CHECKING) null else totalStored + totalMissingBytes + pendingBytes

    val groupState by ResourcePack.state.collectAsState()
    val activeTask = tasks.values.firstOrNull { it.phase == Phase.DOWNLOADING }
    val groupRunning = groupIds.any { tasks[it]?.isPending == true }

    val progress = when {
        groupRunning && groupState is ResourcePack.State.Downloading -> {
            val group = groupState as ResourcePack.State.Downloading
            SummaryProgress.Downloading(
                label = "Downloading ${minOf(group.done + 1, group.total)} of ${group.total}",
                fraction = group.overall
            )
        }
        activeTask != null -> SummaryProgress.Downloading(
            label = if (activeTask.isUpdate) "Updating ${activeTask.siteName}" else "Downloading ${activeTask.siteName}",
            fraction = activeTask.fraction ?: 0f
        )
        allDownloaded -> SummaryProgress.Complete(rows.size, totalStored)
        else -> SummaryProgress.Idle(
            saved = storedCount,
            total = rows.size,
            savedBytes = totalStored,
            totalBytes = totalAllBytes
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceSoft)
            .statusBarsPadding()
    ) {

        // ----- header: back, title, settings -----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                description = "Back",
                onClick = { navController.safePopBackStack() }
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Offline Downloads",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Save AR experiences for offline use",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(12.dp))

            HeaderButton(
                icon = Icons.Default.Settings,
                description = "Download settings",
                onClick = { showSettings = true }
            )
        }

        when {

            loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Primary)
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 4.dp,
                    bottom = 32.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                if (rows.isEmpty()) {
                    item { EmptyDownloads() }
                } else {
                    item {
                        PrepareCard(
                            progress = progress,
                            networkLabel = downloadOver.label,
                            onOpenSettings = { showSettings = true },
                            showDownloadAll = !allDownloaded,
                            downloadAllEnabled = !groupRunning && notDownloaded.isNotEmpty(),
                            onDownloadAll = {
                                val sites = notDownloaded.map { it.site }
                                gate.request(
                                    bulk = true,
                                    size = { totalMissingBytes.takeIf { it > 0L } ?: ResourcePack.estimateBytes(sites) }
                                ) { allowMobile ->
                                    ResourcePack.downloadAll(context, sites, allowMobile)
                                }
                            }
                        )
                    }

                    item {
                        SectionHeader(
                            title = "Available offline",
                            trailing = "$storedCount / ${rows.size} downloaded"
                        )
                    }

                    items(rows, key = { it.site.siteId }) { row ->
                        val siteId = row.site.siteId
                        SiteDownloadCard(
                            row = row,
                            task = tasks[siteId],
                            estimate = estimates[siteId],
                            updateSize = updateSizes[siteId],
                            onDownload = { startSite(row) },
                            onPause = { OfflineSiteManager.pause(context, siteId) },
                            onResume = {
                                gate.request { allowMobile ->
                                    OfflineSiteManager.resume(context, siteId, allowMobile)
                                }
                            },
                            onCancel = { OfflineSiteManager.cancel(context, siteId) },
                            onRemove = {
                                scope.launch {
                                    OfflineSiteManager.delete(context, siteId)
                                    estimates.remove(siteId)
                                    updateSizes.remove(siteId)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------

/** The round header button used across Histoury (back, settings). */
@Composable
private fun HeaderButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(CardSurface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = Primary,
            modifier = Modifier.size(19.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// "Prepare before your visit"
// ---------------------------------------------------------------------------

/** What the summary's progress row shows. */
private sealed interface SummaryProgress {
    /** Nothing downloading. [totalBytes] is null while sizes are still being checked. */
    data class Idle(val saved: Int, val total: Int, val savedBytes: Long, val totalBytes: Long?) : SummaryProgress
    data class Downloading(val label: String, val fraction: Float) : SummaryProgress
    data class Complete(val total: Int, val bytes: Long) : SummaryProgress
}

/**
 * The screen's lead section: why to download, progress across all sites,
 * the "Download using" setting, and Download All — the strongest action on
 * the screen. Larger type and more room than a site card, so it reads as
 * the summary rather than one more item in the list.
 */
@Composable
private fun PrepareCard(
    progress: SummaryProgress,
    networkLabel: String,
    onOpenSettings: () -> Unit,
    showDownloadAll: Boolean,
    downloadAllEnabled: Boolean,
    onDownloadAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardSurface)
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {

        Text(
            text = "Prepare before your visit",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Keep AR experiences ready even when your connection is weak or unavailable.",
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = TextSecondary
        )

        Spacer(Modifier.height(18.dp))

        // ----- progress across all sites -----
        val (label, trailing, fraction) = when (progress) {
            is SummaryProgress.Idle -> Triple(
                "${progress.saved} of ${progress.total} sites saved",
                progress.totalBytes?.let { "About ${OfflineSiteManager.formatBytes(it)}" } ?: "Checking size…",
                if (progress.total > 0) progress.saved.toFloat() / progress.total else 0f
            )
            is SummaryProgress.Downloading -> Triple(
                progress.label,
                "${(progress.fraction * 100).toInt()}%",
                progress.fraction
            )
            is SummaryProgress.Complete -> Triple(
                "${progress.total} of ${progress.total} downloaded",
                OfflineSiteManager.formatBytes(progress.bytes),
                1f
            )
        }
        val emphasised = progress !is SummaryProgress.Idle

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = trailing,
                fontSize = 13.sp,
                fontWeight = if (emphasised) FontWeight.Bold else FontWeight.Normal,
                color = if (emphasised) Primary else TextSecondary
            )
        }

        Spacer(Modifier.height(8.dp))

        val animated by animateFloatAsState(targetValue = fraction, label = "summaryProgress")
        LinearProgressIndicator(
            progress = { animated },
            color = Primary,
            trackColor = PrimarySoft,
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(50))
        )

        if (progress is SummaryProgress.Idle && progress.totalBytes != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${OfflineSiteManager.formatBytes(progress.savedBytes)} of about " +
                    OfflineSiteManager.formatBytes(progress.totalBytes),
                fontSize = 11.5.sp,
                color = TextTertiary
            )
        }

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = Divider, thickness = 0.5.dp)

        // ----- "Download using" — opens the network options -----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenSettings
                )
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Download using",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Text(
                    text = networkLabel,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Change download network",
                tint = TextTertiary,
                modifier = Modifier.size(22.dp)
            )
        }

        if (showDownloadAll) {
            HorizontalDivider(color = Divider, thickness = 0.5.dp)
            Spacer(Modifier.height(16.dp))
            // Same coral PrimaryButton as the rest of the app, at a
            // compact height: still the strongest action on the screen
            // without dominating the card.
            PrimaryButton(
                onClick = onDownloadAll,
                enabled = downloadAllEnabled,
                modifier = Modifier.height(46.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Download All",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, trailing: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 16.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = trailing,
            color = TextTertiary,
            fontSize = 12.sp
        )
    }
}

// ---------------------------------------------------------------------------
// One site
// ---------------------------------------------------------------------------

/** What a card shows, combining the queue and the files on disk. */
private enum class CardState {
    NOT_DOWNLOADED, QUEUED, DOWNLOADING, UPDATING, DOWNLOADED,
    WAITING_FOR_WIFI, WAITING_FOR_CONNECTION, UPDATE_AVAILABLE, PAUSED, FAILED, DAMAGED
}

private fun cardState(row: DownloadRow, task: OfflineSiteManager.Task?): CardState = when (task?.phase) {
    Phase.QUEUED -> CardState.QUEUED
    Phase.DOWNLOADING -> if (task?.isUpdate == true) CardState.UPDATING else CardState.DOWNLOADING
    Phase.WAITING_FOR_WIFI -> CardState.WAITING_FOR_WIFI
    Phase.WAITING_FOR_CONNECTION -> CardState.WAITING_FOR_CONNECTION
    Phase.PAUSED -> CardState.PAUSED
    Phase.FAILED -> CardState.FAILED
    null -> when (row.state) {
        LocalState.NOT_DOWNLOADED -> CardState.NOT_DOWNLOADED
        LocalState.DOWNLOADED -> CardState.DOWNLOADED
        LocalState.UPDATE_AVAILABLE -> CardState.UPDATE_AVAILABLE
        LocalState.DAMAGED -> CardState.DAMAGED
    }
}

/**
 * One site: thumbnail, name (strongest), what's included (muted), and one
 * status line (size, progress, or state). A single light action on the
 * right — the icon itself shows the state (download, pause/resume, check,
 * retry, update); cancel and remove sit in a small long-press
 * menu behind it, so the list doesn't repeat six heavy buttons.
 */
@Composable
private fun SiteDownloadCard(
    row: DownloadRow,
    task: OfflineSiteManager.Task?,
    estimate: Long?,
    updateSize: Long?,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit
) {
    val state = cardState(row, task)
    val stored = row.downloaded

    val contents = if (OfflineSiteManager.hasNarration(row.content)) "AR model · Narration" else "AR model"

    val size = when {
        task != null && task.bytesTotal > 0L -> OfflineSiteManager.formatBytes(task.bytesTotal)
        stored != null && (state == CardState.DOWNLOADED || state == CardState.UPDATE_AVAILABLE) ->
            OfflineSiteManager.formatBytes(stored.bytes)
        estimate != null -> OfflineSiteManager.formatBytes(estimate)
        else -> null
    }
    val done = task?.takeIf { it.bytesTotal > 0L }?.let {
        "${OfflineSiteManager.formatBytes(it.bytesDone)} / ${OfflineSiteManager.formatBytes(it.bytesTotal)}"
    }
    val percent = task?.fraction?.let { "${(it * 100).toInt()}%" }

    // The status line: size when idle, otherwise what's happening.
    val status: String
    var statusColor: Color = TextTertiary
    var statusBold = false

    when (state) {
        CardState.NOT_DOWNLOADED -> status = size ?: "Checking size…"
        CardState.QUEUED -> status = listOfNotNull(if (task?.isUpdate == true) "Update queued" else "Queued", size).joinToString(" · ")
        CardState.DOWNLOADING, CardState.UPDATING -> {
            status = listOfNotNull(
                if (state == CardState.UPDATING) "Updating" else null,
                done,
                percent
            ).joinToString(" · ").ifBlank { "Starting…" }
            statusColor = Primary
            statusBold = true
        }
        CardState.WAITING_FOR_WIFI -> {
            status = "Waiting for Wi-Fi"
            statusColor = TextSecondary
        }
        CardState.WAITING_FOR_CONNECTION -> {
            status = "Waiting for connection"
            statusColor = TextSecondary
        }
        CardState.PAUSED -> {
            status = listOfNotNull("Paused", done).joinToString(" · ")
            statusColor = TextSecondary
        }
        CardState.FAILED -> {
            status = "Download failed · Tap retry"
            statusColor = Danger
        }
        CardState.DAMAGED -> {
            status = "Files missing · Tap retry"
            statusColor = Danger
        }
        CardState.DOWNLOADED -> {
            status = listOfNotNull("Downloaded", size).joinToString(" · ")
            statusColor = Primary
        }
        CardState.UPDATE_AVAILABLE -> {
            status = "Update available" + (updateSize?.takeIf { it > 0L }?.let { " · ${OfflineSiteManager.formatBytes(it)}" } ?: "")
            statusColor = Primary
        }
    }

    val showBar = state == CardState.DOWNLOADING || state == CardState.UPDATING ||
        ((state == CardState.PAUSED || state == CardState.WAITING_FOR_WIFI ||
            state == CardState.WAITING_FOR_CONNECTION) && (task?.bytesDone ?: 0L) > 0L)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardSurface)
            .padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        AsyncImage(
            model = row.site.featuredImage,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Outline)
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {

            Text(
                text = row.site.siteName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = contents,
                fontSize = 12.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = status,
                fontSize = 11.5.sp,
                fontWeight = if (statusBold) FontWeight.SemiBold else FontWeight.Normal,
                color = statusColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (showBar) {
                Spacer(Modifier.height(5.dp))
                val animated by animateFloatAsState(
                    targetValue = task?.fraction ?: 0f,
                    label = "downloadProgress"
                )
                LinearProgressIndicator(
                    progress = { animated },
                    color = if (state == CardState.DOWNLOADING || state == CardState.UPDATING) Primary else TextTertiary,
                    trackColor = PrimarySoft,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(50))
                )
            }
        }

        Spacer(Modifier.width(4.dp))

        SiteAction(
            state = state,
            task = task,
            siteName = row.site.siteName,
            onDownload = onDownload,
            onPause = onPause,
            onResume = onResume,
            onCancel = onCancel,
            onRemove = onRemove
        )
    }
}

/** A menu entry behind a site's action icon. */
private data class ActionOption(val label: String, val destructive: Boolean = false, val run: () -> Unit)

/**
 * The single action per site. 48dp touch target, no filled container: the
 * coral icon is enough, and Download All stays the heaviest thing on screen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SiteAction(
    state: CardState,
    task: OfflineSiteManager.Task?,
    siteName: String,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    val retry = { if (task != null) onResume() else onDownload() }

    // Tapping the icon either acts directly or opens its options.
    val options: List<ActionOption> = when (state) {
        CardState.QUEUED, CardState.DOWNLOADING, CardState.UPDATING, CardState.WAITING_FOR_CONNECTION -> listOf(
            ActionOption("Cancel download", destructive = true, run = onCancel)
        )
        CardState.WAITING_FOR_WIFI -> listOf(
            ActionOption("Download now", run = onResume),
            ActionOption("Cancel download", destructive = true, run = onCancel)
        )
        CardState.PAUSED -> listOf(
            ActionOption("Cancel download", destructive = true, run = onCancel)
        )
        CardState.FAILED -> if (task != null) listOf(
            ActionOption("Retry", run = retry),
            ActionOption("Cancel download", destructive = true, run = onCancel)
        ) else emptyList()
        CardState.DOWNLOADED -> listOf(
            ActionOption("Remove download", destructive = true, run = onRemove)
        )
        CardState.UPDATE_AVAILABLE -> listOf(
            ActionOption("Update now", run = onDownload),
            ActionOption("Remove download", destructive = true, run = onRemove)
        )
        CardState.NOT_DOWNLOADED, CardState.DAMAGED -> emptyList()
    }

    val direct: (() -> Unit)? = when (state) {
        CardState.NOT_DOWNLOADED -> onDownload
        CardState.DAMAGED -> retry
        // The card's progress bar already shows the download, so the button
        // is just Pause / Resume. Cancel is on a long press.
        CardState.QUEUED, CardState.DOWNLOADING, CardState.UPDATING, CardState.WAITING_FOR_CONNECTION -> onPause
        CardState.PAUSED -> onResume
        CardState.FAILED -> if (task == null) retry else null
        else -> null
    }

    val description = when (state) {
        CardState.NOT_DOWNLOADED -> "Download $siteName"
        CardState.DOWNLOADED -> "$siteName downloaded. Options"
        CardState.UPDATE_AVAILABLE -> "Update $siteName"
        CardState.FAILED, CardState.DAMAGED -> "Retry $siteName"
        CardState.QUEUED, CardState.DOWNLOADING, CardState.UPDATING, CardState.WAITING_FOR_CONNECTION -> "Pause $siteName"
        CardState.PAUSED -> "Resume $siteName"
        else -> "$siteName download options"
    }

    Box {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onLongClick = { if (options.isNotEmpty()) menuOpen = true },
                    onClick = {
                        if (direct != null) direct() else if (options.isNotEmpty()) menuOpen = true
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            when (state) {
                else -> Icon(
                    imageVector = when (state) {
                        CardState.NOT_DOWNLOADED -> Icons.Default.Download
                        CardState.DOWNLOADING, CardState.UPDATING, CardState.QUEUED -> Icons.Default.Pause
                        CardState.WAITING_FOR_WIFI -> Icons.Default.Wifi
                        CardState.WAITING_FOR_CONNECTION -> Icons.Default.CloudOff
                        CardState.PAUSED -> Icons.Default.PlayArrow
                        CardState.FAILED, CardState.DAMAGED -> Icons.Default.Refresh
                        CardState.DOWNLOADED -> Icons.Default.CheckCircle
                        CardState.UPDATE_AVAILABLE -> Icons.Default.Update
                        else -> Icons.Default.Download
                    },
                    contentDescription = description,
                    tint = when (state) {
                        CardState.FAILED, CardState.DAMAGED -> Danger
                        CardState.QUEUED, CardState.WAITING_FOR_WIFI, CardState.WAITING_FOR_CONNECTION -> TextSecondary
                        else -> Primary
                    },
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            color = if (option.destructive) Danger else TextPrimary,
                            fontSize = 14.sp
                        )
                    },
                    onClick = {
                        menuOpen = false
                        option.run()
                    }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Empty state
// ---------------------------------------------------------------------------

@Composable
private fun EmptyDownloads() {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 48.dp),
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

