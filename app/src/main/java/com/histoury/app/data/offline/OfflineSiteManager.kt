package com.histoury.app.data.offline

import android.content.Context
import android.util.Log
import com.histoury.app.data.model.HistoricalSite
import com.histoury.app.data.model.SiteContent
import com.histoury.app.data.model.isArReady
import com.histoury.app.data.repository.HistoricalSiteRepository
import com.histoury.app.data.repository.SiteContentRepository
import com.histoury.app.util.ConnectivityObserver
import com.histoury.app.util.ConnectivityObserver.NetworkKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/**
 * Downloading a whole site for offline use, and knowing what has been
 * downloaded — the single download system behind Offline Downloads,
 * "Download All", "Download for This Trip" and automatic updates.
 *
 * This sits on top of [OfflineStore], which decides where files live.
 *
 * WHAT IS KEPT PER SITE
 * A small JSON manifest (offline/manifests/<siteId>.json) records which
 * files belong to the site, the model URL and version it was downloaded
 * from, the narration clips, sizes and dates. The files themselves can't
 * live in a database — ARCore and MediaPlayer both want a real path on disk.
 *
 * VERSIONS
 * Every model or narration upload in the admin panel gets a new Storage
 * URL, and files are stored under a hash of their URL. So "is the local
 * copy current?" is answered by checking that every file the server lists
 * right now is among the files this site's manifest recorded. The admin
 * panel also writes `arModelVersion` (and `arModelSizeBytes`), which is
 * stored in the manifest for display and as a second check.
 *
 * THE QUEUE
 * Downloads run one at a time in an app-wide scope, so leaving a screen
 * doesn't cancel them and a slow connection isn't split six ways. Each
 * queued site follows the "Download over" / "Automatic resource updates"
 * settings: it waits (Waiting for Wi-Fi / Waiting for connection) instead
 * of failing, and continues on its own when a suitable connection returns.
 * The queue is saved to disk, so it picks up again the next time the app
 * opens.
 *
 * NEVER BREAKING A WORKING COPY
 * Files are written to "<name>.part" and only renamed once complete and
 * verified; an interrupted download keeps its .part and resumes from where
 * it stopped (HTTP Range). An update downloads the new files alongside the
 * old ones, rewrites the manifest only after every new file is valid, and
 * deletes the old files last.
 */
object OfflineSiteManager {

    private const val TAG = "OfflineSite"

    /** Automatic retries for a download that fails while online. */
    private const val MAX_ATTEMPTS = 3

    // ----- what is on the phone -----

    /** What the app knows about one site's downloaded files. */
    data class SiteDownload(
        val siteId: String,
        val siteName: String,
        /** Bytes present on disk right now (recounted, not trusted from the manifest). */
        val bytes: Long,
        val fileCount: Int,
        val downloadedAt: Long,
        val updatedAt: Long = downloadedAt,
        val modelUrl: String = "",
        val modelVersion: Long = 0L,
        val modelPath: String = "",
        val files: List<String> = emptyList(),
        /** Narration section key ("overview", "story_0"...) -> local path. */
        val audio: Map<String, String> = emptyMap(),
        /** False when a recorded file is missing or damaged. */
        val complete: Boolean = true
    )

    /** How a site's local copy compares with what the server has now. */
    enum class LocalState { NOT_DOWNLOADED, DOWNLOADED, UPDATE_AVAILABLE, DAMAGED }

    // ----- what is being downloaded -----

    enum class Phase {
        /** Next in line (or about to start). */
        QUEUED,
        DOWNLOADING,
        WAITING_FOR_WIFI,
        WAITING_FOR_CONNECTION,
        PAUSED,
        FAILED
    }

    data class Task(
        val siteId: String,
        val siteName: String,
        val phase: Phase = Phase.QUEUED,
        /** Replacing an older downloaded copy (shown as "Updating"). */
        val isUpdate: Boolean = false,
        /** Started by "Automatic resource updates", not by the visitor. */
        val automatic: Boolean = false,
        /** "Use Mobile Data This Time" — for this download only. */
        val allowMobile: Boolean = false,
        /** Queued by "Auto-download on Wi-Fi": only ever runs on Wi-Fi. */
        val wifiOnly: Boolean = false,
        val bytesDone: Long = 0L,
        val bytesTotal: Long = 0L,
        val attempts: Int = 0,
        val error: String? = null
    ) {
        val fraction: Float?
            get() = if (bytesTotal > 0L) (bytesDone.toFloat() / bytesTotal).coerceIn(0f, 1f) else null

        /** Still intends to finish (not paused, not failed). */
        val isPending: Boolean
            get() = phase != Phase.PAUSED && phase != Phase.FAILED
    }

    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _tasks = MutableStateFlow<Map<String, Task>>(emptyMap())

    /** Every site in the queue, in queue order. */
    val tasks: StateFlow<Map<String, Task>> = _tasks.asStateFlow()

    private val _finished = MutableSharedFlow<Pair<String, Result<SiteDownload>>>(extraBufferCapacity = 16)

    /**
     * One event per download that ends: success, or a failure that needs the
     * visitor (after the automatic retries). Waiting for a connection is not
     * an end, so it is never reported here.
     */
    val finished: SharedFlow<Pair<String, Result<SiteDownload>>> = _finished.asSharedFlow()

    private val _changed = MutableStateFlow(0L)

    /** Bumped whenever a site's files on disk change (download, update, removal). */
    val changed: StateFlow<Long> = _changed.asStateFlow()

    private data class Request(val site: HistoricalSite, val content: SiteContent?)

    /** Visible in the app (the admin panel's "published" status). */
    private val HistoricalSite.isPublished: Boolean
        get() = status == "published"

    private val requests = ConcurrentHashMap<String, Request>()

    // ---------------------------------------------------------------------
    // Manifests
    // ---------------------------------------------------------------------

    private fun manifestsDir(context: Context): File =
        File(File(context.filesDir, "offline"), "manifests").apply { mkdirs() }

    private fun manifestFor(context: Context, siteId: String): File =
        File(manifestsDir(context), "${safeName(siteId)}.json")

    /** siteIds can contain characters a filename can't. */
    private fun safeName(siteId: String): String = siteId.replace(Regex("[^A-Za-z0-9._-]"), "_")

    fun isDownloaded(context: Context, siteId: String): Boolean =
        manifestFor(context, siteId).isFile

    private fun readManifest(context: Context, file: File): SiteDownload? = runCatching {
        val json = JSONObject(file.readText())
        val paths = json.optJSONArray("files")
        val files = buildList {
            for (i in 0 until (paths?.length() ?: 0)) add(paths!!.getString(i))
        }

        // Recomputed from disk rather than trusted from the manifest. If a
        // file has gone — cleared app data, a failed write — the listing
        // should say so instead of reporting megabytes that are not there.
        var bytes = 0L
        var usable = 0
        files.forEach { path ->
            val candidate = File(path)
            if (candidate.isFile) bytes += candidate.length()
            if (OfflineStore.isUsable(candidate)) usable++
        }

        val downloadedAt = json.optLong("downloadedAt")
        SiteDownload(
            siteId = json.optString("siteId"),
            siteName = json.optString("siteName"),
            bytes = bytes,
            fileCount = usable,
            downloadedAt = downloadedAt,
            updatedAt = json.optLong("updatedAt", downloadedAt),
            modelUrl = json.optString("modelUrl"),
            modelVersion = json.optLong("modelVersion"),
            // Older manifests have no modelPath; the .glb in the list is it.
            modelPath = json.optString("modelPath").ifBlank {
                files.firstOrNull { it.endsWith(".glb", ignoreCase = true) }.orEmpty()
            },
            files = files,
            audio = json.optJSONObject("audio")?.let { map ->
                map.keys().asSequence().associateWith { key -> map.optString(key) }
            }.orEmpty(),
            complete = files.isNotEmpty() && usable == files.size
        )
    }.getOrNull()

    /** This site's manifest, or null when it isn't downloaded. */
    fun downloadFor(context: Context, siteId: String): SiteDownload? {
        val file = manifestFor(context, siteId)
        return if (file.isFile) readManifest(context, file) else null
    }

    private fun writeManifest(
        context: Context,
        site: HistoricalSite,
        files: List<File>,
        model: File?,
        audio: Map<String, File>,
        firstDownloadedAt: Long?
    ): SiteDownload {
        val now = System.currentTimeMillis()
        val json = JSONObject().apply {
            put("siteId", site.siteId)
            put("siteName", site.siteName)
            put("bytes", files.sumOf { it.length() })
            put("downloadedAt", firstDownloadedAt ?: now)
            put("updatedAt", now)
            put("status", "complete")
            put("modelUrl", site.arModelUrl)
            put("modelVersion", site.arModelVersion)
            put("modelPath", model?.absolutePath.orEmpty())
            put("audio", JSONObject().apply { audio.forEach { (key, file) -> put(key, file.absolutePath) } })
            put("files", JSONArray().apply { files.forEach { put(it.absolutePath) } })
        }

        // Written to a temporary file and renamed, so a crash mid-write can
        // never leave a half manifest that points at nothing.
        val target = manifestFor(context, site.siteId)
        val temporary = File(target.parentFile, "${target.name}.tmp")
        temporary.writeText(json.toString())
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }

        return readManifest(context, target) ?: SiteDownload(
            siteId = site.siteId,
            siteName = site.siteName,
            bytes = files.sumOf { it.length() },
            fileCount = files.size,
            downloadedAt = firstDownloadedAt ?: now,
            updatedAt = now,
            modelUrl = site.arModelUrl,
            modelVersion = site.arModelVersion,
            modelPath = model?.absolutePath.orEmpty(),
            files = files.map { it.absolutePath }
        )
    }

    /** Everything currently downloaded, newest first. */
    suspend fun listDownloads(context: Context): List<SiteDownload> =
        withContext(Dispatchers.IO) {
            manifestsDir(context).listFiles()
                ?.filter { it.extension == "json" }
                ?.mapNotNull { readManifest(context, it) }
                ?.sortedByDescending { it.updatedAt }
                ?: emptyList()
        }

    /**
     * The downloaded model for [siteId], if it is on the phone and whole —
     * even when a newer version exists on the server. The AR screen opens
     * this straight away instead of going to Firebase Storage; updates are
     * fetched separately, under the "Automatic resource updates" setting.
     */
    fun localModelFor(context: Context, siteId: String?): File? {
        if (siteId.isNullOrBlank()) return null
        val record = downloadFor(context, siteId) ?: return null
        val model = File(record.modelPath.ifBlank { return null })
        return model.takeIf { OfflineStore.isValidGlb(it) }
    }

    // ---------------------------------------------------------------------
    // Assets and sizes
    // ---------------------------------------------------------------------

    private data class Asset(val url: String, val extension: String, val audioKey: String? = null)

    /**
     * Every asset a site needs to work with no connection: the AR model and
     * the narration clips. (Reference photos are no longer downloaded — AR
     * is manual placement only, so they're never read on site.)
     */
    private fun assetsFor(site: HistoricalSite, content: SiteContent?): List<Asset> {
        val assets = mutableListOf<Asset>()
        if (site.arModelUrl.isNotBlank()) assets += Asset(site.arModelUrl, "glb")
        content?.audioUrls?.forEach { (key, url) ->
            if (url.isNotBlank()) assets += Asset(url, "mp3", key)
        }
        return assets
    }

    /** Whether the site has narration to download alongside its model. */
    fun hasNarration(content: SiteContent?): Boolean =
        content?.audioUrls?.values?.any { it.isNotBlank() } == true

    private fun destinationFor(context: Context, asset: Asset): File {
        val directory = when (asset.extension) {
            "glb" -> OfflineStore.modelsDir(context)
            "img" -> OfflineStore.referencesDir(context)
            else -> OfflineStore.audioDir(context)
        }
        return OfflineStore.fileFor(directory, asset.url, asset.extension)
    }

    private val sizeCache = ConcurrentHashMap<String, Long>()

    /** Size of one asset: from Firestore for the model when known, else a HEAD request (cached). */
    private fun sizeOf(site: HistoricalSite, asset: Asset): Long {
        if (asset.extension == "glb" && site.arModelSizeBytes > 0L) return site.arModelSizeBytes
        sizeCache[asset.url]?.let { return it }
        val length = headLength(asset.url)
        if (length > 0L) sizeCache[asset.url] = length
        return length
    }

    /**
     * Total download size of a site (model + narration), or null when any
     * part's size can't be learned — the caller then says "checking size"
     * rather than print a confident wrong total.
     */
    suspend fun estimateBytes(
        site: HistoricalSite,
        content: SiteContent?
    ): Long? = withContext(Dispatchers.IO) {
        var total = 0L
        assetsFor(site, content).forEach { asset ->
            val length = sizeOf(site, asset)
            if (length <= 0L) return@withContext null
            total += length
        }
        if (total == 0L) null else total
    }

    /**
     * Bytes an update would actually download: only the files that aren't on
     * the phone yet (narration that didn't change is kept, not fetched again).
     */
    suspend fun estimateMissingBytes(
        context: Context,
        site: HistoricalSite,
        content: SiteContent?
    ): Long? = withContext(Dispatchers.IO) {
        var total = 0L
        assetsFor(site, content).forEach { asset ->
            if (OfflineStore.isUsable(destinationFor(context, asset))) return@forEach
            val length = sizeOf(site, asset)
            if (length <= 0L) return@withContext null
            total += length
        }
        total
    }

    /** How this site's local copy compares with the server's current files. */
    suspend fun localState(
        context: Context,
        site: HistoricalSite,
        content: SiteContent?
    ): LocalState = withContext(Dispatchers.IO) {
        val record = downloadFor(context, site.siteId) ?: return@withContext LocalState.NOT_DOWNLOADED
        if (!record.complete) return@withContext LocalState.DAMAGED

        val recorded = record.files.toSet()
        // Without the content document (offline and never cached) only the
        // model can be compared; narration is then assumed unchanged.
        val current = assetsFor(site, content)
            .filter { content != null || it.extension == "glb" }
            .map { destinationFor(context, it).absolutePath }

        val versionBehind = site.arModelVersion > 0L && record.modelVersion > 0L &&
            site.arModelVersion > record.modelVersion

        if (current.all { it in recorded } && !versionBehind) LocalState.DOWNLOADED
        else LocalState.UPDATE_AVAILABLE
    }

    // ---------------------------------------------------------------------
    // Queue: start, pause, cancel
    // ---------------------------------------------------------------------

    /**
     * Adds [site] to the download queue. Ignored if it's already downloading
     * or queued (except that a one-time mobile-data permission is added).
     * A paused, failed or waiting download is queued again.
     *
     * The network check happens in the queue, not here: on the wrong kind of
     * connection the site simply shows "Waiting for Wi-Fi" or "Waiting for
     * connection" and starts by itself later. The Wi-Fi confirmation dialog
     * is the screen's job (see DownloadGate) and happens before this call.
     */
    fun startDownload(
        context: Context,
        site: HistoricalSite,
        content: SiteContent?,
        allowMobile: Boolean = false,
        isUpdate: Boolean = isDownloaded(context, site.siteId),
        automatic: Boolean = false,
        wifiOnly: Boolean = false
    ) {
        val appContext = context.applicationContext
        requests[site.siteId] = Request(site, content)
        // Asked for by the visitor: auto-download may pick it up again later.
        if (!automatic && !wifiOnly) DownloadPreferences.includeInAutoDownload(appContext, site.siteId)

        _tasks.update { current ->
            val existing = current[site.siteId]
            val next = when {
                existing == null -> Task(
                    siteId = site.siteId,
                    siteName = site.siteName,
                    isUpdate = isUpdate,
                    automatic = automatic,
                    allowMobile = allowMobile,
                    wifiOnly = wifiOnly
                )
                // A visitor asking for a site that an automatic update is
                // already fetching makes it theirs (their network setting).
                existing.isPending -> existing.copy(
                    allowMobile = existing.allowMobile || allowMobile,
                    automatic = existing.automatic && automatic,
                    // The visitor tapping Download makes it follow their setting.
                    wifiOnly = existing.wifiOnly && wifiOnly
                )
                else -> existing.copy(
                    phase = Phase.QUEUED,
                    allowMobile = allowMobile,
                    automatic = automatic,
                    wifiOnly = wifiOnly,
                    attempts = 0,
                    error = null
                )
            }
            current + (site.siteId to next)
        }

        persist(appContext)
        ensureRunner(appContext)
    }

    /** Stops a download but keeps what was downloaded so far, to continue later. */
    fun pause(context: Context, siteId: String) {
        val task = _tasks.value[siteId] ?: return
        setTask(task.copy(phase = Phase.PAUSED))
        if (running?.first == siteId) {
            stopReason = StopReason.PAUSED
            running?.second?.cancel()
        }
        persist(context.applicationContext)
    }

    /** Continues a paused, failed or waiting download. */
    fun resume(context: Context, siteId: String, allowMobile: Boolean = false) {
        val task = _tasks.value[siteId] ?: return
        setTask(
            task.copy(
                phase = Phase.QUEUED,
                allowMobile = task.allowMobile || allowMobile,
                attempts = 0,
                error = null
            )
        )
        persist(context.applicationContext)
        ensureRunner(context.applicationContext)
    }

    /** Removes a site from the queue and discards its partial files. */
    fun cancel(context: Context, siteId: String, excludeFromAutoDownload: Boolean = true) {
        val appContext = context.applicationContext
        // Cancelled by the visitor: auto-download leaves it alone from now on.
        // (Not when the app removes a site that was taken down — if it comes
        // back, auto-download may fetch it again.)
        if (excludeFromAutoDownload) DownloadPreferences.excludeFromAutoDownload(appContext, siteId)
        val active = running?.takeIf { it.first == siteId }?.second
        if (active != null) {
            stopReason = StopReason.CANCELLED
            active.cancel()
        }
        _tasks.update { it - siteId }
        val request = requests.remove(siteId)
        persist(appContext)
        // Only once the download has actually stopped, so it can't
        // recreate a .part file after it was deleted.
        downloadScope.launch {
            active?.join()
            // A task brought back by restore() after a restart has no
            // request in memory yet; without loading it, its partial files
            // (possibly hundreds of MB) were never deleted.
            val known = request ?: runCatching { loadRequest(siteId) }.getOrNull()
                ?.also { requests.remove(siteId) }
                ?: return@launch
            // Queued again in the meantime: those partials are in use.
            if (siteId in _tasks.value) return@launch
            assetsFor(known.site, known.content).forEach { asset ->
                val destination = destinationFor(appContext, asset)
                File(destination.parentFile, "${destination.name}.part").delete()
            }
        }
        wake.update { it + 1 }
    }

    private fun setTask(task: Task) {
        _tasks.update { current ->
            if (task.siteId in current) current + (task.siteId to task) else current
        }
    }

    // ---------------------------------------------------------------------
    // The runner
    // ---------------------------------------------------------------------

    private enum class StopReason { NONE, PAUSED, CANCELLED, BLOCKED }

    private val runnerLock = Any()
    private var runnerJob: Job? = null

    @Volatile
    private var running: Pair<String, Job>? = null

    @Volatile
    private var stopReason: StopReason = StopReason.NONE

    /** Re-evaluate the queue (network or settings changed, a task was added). */
    private val wake = MutableStateFlow(0L)

    private val network = MutableStateFlow<NetworkKind?>(null)

    /** Called by DownloadPreferences when a setting changes. */
    fun onSettingsChanged(context: Context) {
        val appContext = context.applicationContext
        if (DownloadPreferences.autoUpdates(appContext) == AutoUpdateMode.OFF) {
            // "Off" means the visitor updates by hand: automatic updates not
            // yet started are dropped (their partial files are kept, so a
            // manual update later continues from there).
            _tasks.update { current ->
                current.filterValues { !(it.automatic && it.phase != Phase.DOWNLOADING) }
            }
            val active = running
            if (active != null && _tasks.value[active.first]?.automatic == true) {
                stopReason = StopReason.CANCELLED
                active.second.cancel()
                _tasks.update { it - active.first }
            }
            persist(appContext)
        } else {
            downloadScope.launch { checkForUpdates(appContext) }
        }
        wake.update { it + 1 }
    }

    /**
     * "Auto-download on Wi-Fi" was turned off: drop the sites it queued that
     * haven't started (a running one finishes; its partial files are kept).
     */
    fun dropAutoDownloads(context: Context) {
        _tasks.update { current ->
            current.filterValues { !(it.wifiOnly && it.phase != Phase.DOWNLOADING) }
        }
        persist(context.applicationContext)
        wake.update { it + 1 }
    }

    private fun ensureRunner(context: Context) {
        synchronized(runnerLock) {
            if (runnerJob?.isActive == true) {
                wake.update { it + 1 }
                return
            }
            runnerJob = downloadScope.launch { runLoop(context) }
        }
    }

    /**
     * Why [task] can't run on [kind] right now, or null when it can.
     *
     * Wi-Fi only (for a visitor's download, unless they chose "Use Mobile
     * Data This Time"; for an automatic update, from its own setting) waits
     * for Wi-Fi. Otherwise it only waits for any connection at all.
     */
    private fun blockedPhase(context: Context, task: Task, kind: NetworkKind): Phase? {
        val needsWifi = if (task.wifiOnly) {
            true
        } else if (task.automatic) {
            DownloadPreferences.autoUpdates(context) == AutoUpdateMode.WIFI_ONLY
        } else {
            DownloadPreferences.downloadOver(context) == DownloadNetwork.WIFI_ONLY && !task.allowMobile
        }
        return when {
            kind == NetworkKind.NONE -> if (needsWifi) Phase.WAITING_FOR_WIFI else Phase.WAITING_FOR_CONNECTION
            needsWifi && kind == NetworkKind.MOBILE -> Phase.WAITING_FOR_WIFI
            else -> null
        }
    }

    private suspend fun runLoop(context: Context) {
        val watcher = downloadScope.launch {
            ConnectivityObserver.observeKind(context).collect { kind ->
                network.value = kind
                wake.update { it + 1 }
            }
        }

        try {
            while (true) {
                val wakeSeen = wake.value

                val next: Task? = synchronized(runnerLock) {
                    val pending = _tasks.value.values.filter { it.isPending }
                    if (pending.isEmpty()) {
                        // Nothing left to do: stop, so the network callback
                        // isn't held for the life of the app. A later
                        // startDownload starts a fresh runner.
                        runnerJob = null
                        return
                    }
                    val kind = network.value ?: ConnectivityObserver.currentKind(context)
                    var first: Task? = null
                    pending.forEach { task ->
                        val blocked = blockedPhase(context, task, kind)
                        if (blocked == null) {
                            if (task.phase != Phase.QUEUED && task.phase != Phase.DOWNLOADING) {
                                setTask(task.copy(phase = Phase.QUEUED))
                            }
                            if (first == null) first = task
                        } else if (task.phase != blocked) {
                            setTask(task.copy(phase = blocked))
                        }
                    }
                    first
                }

                if (next == null) {
                    // Everything is waiting for a connection: sleep until the
                    // network or the queue changes.
                    wake.first { it != wakeSeen }
                    continue
                }

                runTask(context, _tasks.value[next.siteId] ?: continue)
            }
        } finally {
            watcher.cancel()
        }
    }

    private suspend fun loadRequest(siteId: String): Request? {
        requests[siteId]?.let { return it }
        val site = HistoricalSiteRepository().getHistoricalSiteBySiteId(siteId) ?: return null
        val content = runCatching { SiteContentRepository().getSiteContent(site.siteId) }.getOrNull()
        return Request(site, content).also { requests[siteId] = it }
    }

    /** The network as the queue sees it (one source for every decision). */
    private fun networkNow(context: Context): NetworkKind =
        network.value ?: ConnectivityObserver.currentKind(context)

    private suspend fun runTask(context: Context, task: Task) {
        var request = loadRequest(task.siteId)
        // Content (narration list) couldn't be read earlier: try once more,
        // so a site isn't saved without its narration by accident.
        if (request != null && request.content == null) {
            val content = runCatching { SiteContentRepository().getSiteContent(request.site.siteId) }.getOrNull()
            if (content != null) {
                request = request.copy(content = content)
                requests[task.siteId] = request
            }
        }
        // Unpublished in the admin panel since it was queued (the lookup by
        // id still finds the document): not available any more either.
        if (request == null || !request.site.isArReady || !request.site.isPublished) {
            if (networkNow(context) == NetworkKind.NONE) {
                // Couldn't even read the site record; try again once online.
                setTask(task.copy(phase = Phase.WAITING_FOR_CONNECTION))
                wake.first { network.value != NetworkKind.NONE }
                return
            }
            _finished.tryEmit(task.siteId to Result.failure(IllegalStateException("Site unavailable")))
            setTask(task.copy(phase = Phase.FAILED, error = "This site's AR experience is no longer available."))
            persist(context)
            return
        }

        // Paused, cancelled or dropped (automatic updates turned off) while
        // the site record was loading: don't start.
        val fresh = _tasks.value[task.siteId]
        if (fresh == null || !fresh.isPending) return

        val site = request.site
        val content = request.content
        val job = downloadScope.async(start = CoroutineStart.LAZY) {
            download(context, site, content) { done, total ->
                _tasks.value[task.siteId]?.let { current ->
                    if (current.phase == Phase.DOWNLOADING) {
                        setTask(current.copy(bytesDone = done, bytesTotal = total))
                    }
                }
            }
        }
        stopReason = StopReason.NONE
        running = task.siteId to job
        setTask(fresh.copy(phase = Phase.DOWNLOADING, error = null))
        job.start()

        // Stops the download the moment the connection or a setting no
        // longer allows it (e.g. Wi-Fi drops on a Wi-Fi-only download). The
        // .part file is kept, so it continues from there later.
        val guard = downloadScope.launch {
            combine(
                network,
                DownloadPreferences.downloadOver,
                DownloadPreferences.autoUpdates
            ) { kind, _, _ -> kind }.collect { kind ->
                val current = _tasks.value[task.siteId] ?: return@collect
                if (kind != null && blockedPhase(context, current, kind) != null && job.isActive) {
                    stopReason = StopReason.BLOCKED
                    job.cancel()
                }
            }
        }

        val result: Result<SiteDownload>? = try {
            job.await()
        } catch (cancelled: CancellationException) {
            coroutineContext.ensureActive()
            null
        } finally {
            guard.cancel()
            running = null
        }

        val latest = _tasks.value[task.siteId]

        when {
            result == null -> {
                // Stopped on purpose. Paused/cancelled already recorded their
                // own state; a blocked one goes back to waiting.
                if (stopReason == StopReason.BLOCKED && latest != null && latest.phase == Phase.DOWNLOADING) {
                    val kind = networkNow(context)
                    setTask(latest.copy(phase = blockedPhase(context, latest, kind) ?: Phase.QUEUED))
                }
            }

            result.isSuccess -> {
                _tasks.update { it - task.siteId }
                requests.remove(task.siteId)
                _changed.update { it + 1 }
                persist(context)
                _finished.tryEmit(task.siteId to result)
            }

            else -> {
                val error = result.exceptionOrNull()
                val kind = networkNow(context)
                val current = latest ?: return
                if (current.phase != Phase.DOWNLOADING) return
                val blocked = blockedPhase(context, current, kind)
                when {
                    // Lost the connection (or it changed to one this download
                    // may not use): wait, don't fail. Resumes by itself.
                    blocked != null -> {
                        setTask(current.copy(phase = blocked))
                        // The network callback can lag the failure by a
                        // moment; never spin on it.
                        delay(1_000L)
                    }

                    // Signal dropped or too weak (the socket timed out)
                    // while the phone still reports a network: keep
                    // waiting and retrying, never fail for this. The .part
                    // file keeps what has arrived so far.
                    error is IOException && error !is HttpStatusException -> {
                        setTask(current.copy(phase = Phase.WAITING_FOR_CONNECTION, attempts = current.attempts + 1))
                        delay(minOf(60_000L, 2_000L shl minOf(current.attempts, 5)))
                    }

                    current.attempts + 1 < MAX_ATTEMPTS -> {
                        setTask(current.copy(phase = Phase.QUEUED, attempts = current.attempts + 1))
                        // Short back-off before trying again.
                        delay(2_000L * (current.attempts + 1))
                    }

                    else -> {
                        Log.w(TAG, "Download failed for ${task.siteId}", error)
                        // Reported before the state changes, so a group
                        // download still owns this site when the screen
                        // decides whether to show its own message.
                        _finished.tryEmit(
                            task.siteId to Result.failure(error ?: IllegalStateException("Download failed."))
                        )
                        setTask(
                            current.copy(
                                phase = Phase.FAILED,
                                error = friendlyError(error)
                            )
                        )
                        persist(context)
                    }
                }
            }
        }
    }

    private fun friendlyError(error: Throwable?): String = when (error) {
        is HttpStatusException ->
            if (error.code == 404) "A file for this site is no longer on the server."
            else "The server couldn't send this file (error ${error.code})."
        is CorruptDownloadException -> "The downloaded file was damaged."
        is IOException -> "The connection was interrupted."
        else -> error?.message ?: "Download failed."
    }

    private class HttpStatusException(val code: Int) : IOException("Download returned HTTP $code.")
    private class CorruptDownloadException(message: String) : Exception(message)

    // ---------------------------------------------------------------------
    // Downloading one site
    // ---------------------------------------------------------------------

    /**
     * Downloads everything [site] needs, then writes its manifest.
     *
     * Files already on the phone are kept, not fetched again (a model the AR
     * screen already cached, or narration that didn't change in an update).
     * Only after every file is present and verified is the manifest
     * rewritten; the previous version's files are deleted after that, so a
     * working local copy is never lost to a half-finished update.
     */
    private suspend fun download(
        context: Context,
        site: HistoricalSite,
        content: SiteContent?,
        onProgress: (done: Long, total: Long) -> Unit
    ): Result<SiteDownload> = withContext(Dispatchers.IO) {
        runCatching {
            val assets = assetsFor(site, content)
            check(assets.isNotEmpty()) { "This site has nothing to download yet." }

            val destinations = assets.map { destinationFor(context, it) }

            // Sizes up front, for "48 MB / 66 MB". A file already present
            // counts at its size on disk; an unknown size makes the total
            // unknown rather than wrong.
            val sizes = assets.mapIndexed { index, asset ->
                val destination = destinations[index]
                if (OfflineStore.isUsable(destination)) destination.length() else sizeOf(site, asset)
            }
            val total = if (sizes.all { it > 0L }) sizes.sum() else 0L

            var completed = 0L
            onProgress(0L, total)

            assets.forEachIndexed { index, asset ->
                coroutineContext.ensureActive()
                val destination = destinations[index]
                if (!OfflineStore.isUsable(destination)) {
                    fetch(asset.url, destination, isModel = asset.extension == "glb") { fileBytes ->
                        onProgress(completed + fileBytes, total)
                    }
                }
                completed += destination.length()
                onProgress(completed, maxOf(total, completed))
            }

            // Everything is here and verified: only now switch the record over.
            val previous = downloadFor(context, site.siteId)
            val model = assets.indexOfFirst { it.extension == "glb" }
                .takeIf { it >= 0 }?.let { destinations[it] }
            val audio = assets.mapIndexedNotNull { index, asset ->
                asset.audioKey?.let { it to destinations[index] }
            }.toMap().toMutableMap()
            val files = destinations.toMutableList()

            // The narration list couldn't be read (content unavailable): keep
            // the narration already on the phone rather than dropping it from
            // the record and deleting it below.
            if (content == null && previous != null) {
                previous.files
                    .filter { it.endsWith(".mp3", ignoreCase = true) }
                    .map { File(it) }
                    .filter { OfflineStore.isUsable(it) && it !in files }
                    .forEach { files += it }
                previous.audio.forEach { (key, path) ->
                    if (key !in audio && File(path).isFile) audio[key] = File(path)
                }
            }

            val record = writeManifest(
                context = context,
                site = site,
                files = files,
                model = model,
                audio = audio,
                firstDownloadedAt = previous?.downloadedAt
            )

            // The old version's files, once nothing refers to them any more.
            if (previous != null) {
                val stillUsed = files.map { it.absolutePath }.toSet() + filesOfOtherSites(context, site.siteId)
                previous.files.filterNot { it in stillUsed }.forEach { path ->
                    runCatching { File(path).delete() }
                }
            }

            record
        }
    }

    /** Content length from a HEAD request, or -1 when the server doesn't say. */
    private fun headLength(url: String): Long = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
            connectTimeout = 15_000
            readTimeout = 15_000
            instanceFollowRedirects = true
        }
        try {
            connection.connect()
            if (connection.responseCode in 200..299) connection.contentLengthLong else -1L
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(-1L)

    /**
     * Downloads [url] to [destination], continuing a previous partial
     * download when one exists.
     *
     * Bytes go to "<name>.part" and it is renamed only once complete and
     * verified — a half-written file left under the real name would be
     * treated as downloaded and fail on site. The .part survives a dropped
     * connection, a closed app or a paused download, and the next attempt
     * asks the server only for the rest (HTTP Range). If the server ignores
     * the Range request it simply starts over.
     */
    private suspend fun fetch(
        url: String,
        destination: File,
        isModel: Boolean,
        onBytes: (Long) -> Unit
    ): Unit = withContext(Dispatchers.IO) {

        val partial = File(destination.parentFile, "${destination.name}.part")
        var existing = if (partial.isFile) partial.length() else 0L

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 30_000
            // Short enough that a dead connection is noticed and the site
            // moves to "Waiting for connection" instead of hanging.
            readTimeout = 30_000
            instanceFollowRedirects = true
            if (existing > 0L) setRequestProperty("Range", "bytes=$existing-")
        }

        // Cancelling (pause, Wi-Fi lost) must unblock a read stuck on a dead
        // socket: this watchdog is cancelled together with the download and
        // closes the connection as it goes.
        val watchdog = launch {
            try {
                awaitCancellation()
            } finally {
                runCatching { connection.disconnect() }
            }
        }

        try {
            connection.connect()
            val code = connection.responseCode

            if (code == 416) {
                // The partial is already the whole file (or unusable): start clean.
                partial.delete()
                throw IOException("Range not satisfiable; restarting.")
            }
            if (code !in 200..299) throw HttpStatusException(code)

            val resuming = code == 206 && existing > 0L
            if (!resuming) existing = 0L

            val expected: Long = if (code == 206) {
                connection.getHeaderField("Content-Range")
                    ?.substringAfter('/')?.trim()?.toLongOrNull()
                    ?: connection.contentLengthLong.takeIf { it > 0L }?.plus(existing)
                    ?: -1L
            } else {
                connection.contentLengthLong
            }

            onBytes(existing)

            connection.inputStream.use { input ->
                FileOutputStream(partial, resuming).buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var written = existing
                    var lastReported = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        // About every 256 KB, so a fast connection doesn't flood the UI.
                        if (written - lastReported >= 256 * 1024) {
                            lastReported = written
                            onBytes(written)
                        }
                    }
                    onBytes(written)
                }
            }

            // A stream that ended early is a dropped connection, not a file:
            // keep the .part and continue from here next time.
            if (expected > 0L && partial.length() < expected) {
                throw IOException("Download interrupted at ${partial.length()} of $expected bytes.")
            }
            if (partial.length() <= 0L) throw IOException("Downloaded file was empty.")

            if (isModel && !OfflineStore.isValidGlb(partial)) {
                partial.delete()
                throw CorruptDownloadException("Downloaded model is not a valid GLB.")
            }

            if (destination.exists()) destination.delete()
            if (!partial.renameTo(destination)) {
                partial.copyTo(destination, overwrite = true)
                partial.delete()
            }
        } finally {
            watchdog.cancel()
            runCatching { connection.disconnect() }
        }
    }

    private fun filesOfOtherSites(context: Context, siteId: String): Set<String> {
        val keep = mutableSetOf<String>()
        manifestsDir(context).listFiles()
            ?.filter { it.extension == "json" && it.nameWithoutExtension != safeName(siteId) }
            ?.forEach { other ->
                runCatching {
                    val paths = JSONObject(other.readText()).optJSONArray("files")
                    for (i in 0 until (paths?.length() ?: 0)) keep += paths!!.getString(i)
                }
            }
        return keep
    }

    // ---------------------------------------------------------------------
    // Updates
    // ---------------------------------------------------------------------

    /**
     * Looks for newer versions of downloaded sites (and repairs damaged
     * ones). With "Automatic resource updates" on, each is queued as an
     * automatic update, which then waits for the allowed connection by
     * itself. With it off, nothing is downloaded: Offline Downloads shows
     * "Update available" and the visitor updates by hand.
     *
     * Safe to call often (app start, opening Offline Downloads).
     */
    suspend fun checkForUpdates(context: Context) {
        val appContext = context.applicationContext
        val downloaded = listDownloads(appContext)
        if (downloaded.isEmpty()) return
        if (DownloadPreferences.autoUpdates(appContext) == AutoUpdateMode.OFF) return

        val sites = runCatching { HistoricalSiteRepository().getHistoricalSites() }.getOrDefault(emptyList())
        if (sites.isEmpty()) return

        downloaded.forEach { record ->
            val site = sites.firstOrNull { it.siteId == record.siteId } ?: return@forEach
            if (!site.isArReady) return@forEach
            if (_tasks.value[site.siteId]?.isPending == true) return@forEach

            val content = runCatching { SiteContentRepository().getSiteContent(site.siteId) }.getOrNull()
            val state = localState(appContext, site, content)
            if (state == LocalState.UPDATE_AVAILABLE || state == LocalState.DAMAGED) {
                // A visitor's own paused/failed download isn't overridden.
                if (_tasks.value[site.siteId] != null) return@forEach
                startDownload(appContext, site, content, isUpdate = true, automatic = true)
            }
        }
    }

    // ---------------------------------------------------------------------
    // Saving the queue across app restarts
    // ---------------------------------------------------------------------

    private const val QUEUE_PREFS = "histoury_offline_queue"
    private const val KEY_TASKS = "tasks"

    @Volatile
    private var restored = false

    private fun persist(context: Context) {
        val array = JSONArray()
        _tasks.value.values.forEach { task ->
            array.put(JSONObject().apply {
                put("siteId", task.siteId)
                put("siteName", task.siteName)
                put("isUpdate", task.isUpdate)
                put("automatic", task.automatic)
                put("allowMobile", task.allowMobile)
                put("wifiOnly", task.wifiOnly)
                put("phase", if (task.phase == Phase.PAUSED || task.phase == Phase.FAILED) task.phase.name else Phase.QUEUED.name)
                task.error?.let { put("error", it) }
            })
        }
        runCatching {
            context.applicationContext
                .getSharedPreferences(QUEUE_PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_TASKS, array.toString()).apply()
        }
    }

    /**
     * Brings back downloads that were still queued, waiting or paused when
     * the app was closed, and continues them. Call once when the app starts.
     */
    fun restore(context: Context) {
        val appContext = context.applicationContext
        if (restored) return
        restored = true
        DownloadPreferences.load(appContext)

        val saved = runCatching {
            JSONArray(
                appContext.getSharedPreferences(QUEUE_PREFS, Context.MODE_PRIVATE)
                    .getString(KEY_TASKS, "[]")
            )
        }.getOrNull() ?: return

        val restoredTasks = LinkedHashMap<String, Task>()
        for (i in 0 until saved.length()) {
            val json = saved.optJSONObject(i) ?: continue
            val siteId = json.optString("siteId")
            if (siteId.isBlank() || siteId in _tasks.value) continue
            val error = json.optString("error")
            restoredTasks[siteId] = Task(
                siteId = siteId,
                siteName = json.optString("siteName"),
                phase = runCatching { Phase.valueOf(json.optString("phase")) }.getOrDefault(Phase.QUEUED),
                isUpdate = json.optBoolean("isUpdate"),
                automatic = json.optBoolean("automatic"),
                allowMobile = json.optBoolean("allowMobile"),
                wifiOnly = json.optBoolean("wifiOnly"),
                error = error.takeIf { it.isNotBlank() }
            )
        }
        if (restoredTasks.isEmpty()) return

        _tasks.update { restoredTasks + it }
        ensureRunner(appContext)
    }

    // ---------------------------------------------------------------------
    // Sites taken down: unpublished, deleted, or AR switched off
    // ---------------------------------------------------------------------

    private val _retired = MutableSharedFlow<List<String>>(extraBufferCapacity = 4)

    /**
     * Names of downloaded sites just removed from the phone because they
     * are no longer available (MainActivity tells the visitor once).
     */
    val retired: SharedFlow<List<String>> = _retired.asSharedFlow()

    /**
     * Removes the files (and any queued download) of sites the admin panel
     * has since unpublished or deleted, or whose AR experience was switched
     * off or lost its model. Otherwise they would sit on the phone unseen:
     * Offline Downloads only lists sites that are published with AR, so the
     * visitor could never find them to delete.
     *
     * Only acts on a fresh answer from the server, never on the phone's
     * cache, and never when the server reports no published sites at all —
     * a misconfiguration must not wipe every download.
     *
     * Returns the siteIds removed.
     */
    suspend fun removeRetiredSites(context: Context): List<String> = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val downloaded = listDownloads(appContext)
        val queued = _tasks.value.values.toList()
        if (downloaded.isEmpty() && queued.isEmpty()) return@withContext emptyList()

        val published = HistoricalSiteRepository().getPublishedSitesFromServer()
            ?: return@withContext emptyList()
        if (published.isEmpty()) return@withContext emptyList()

        val available = published.filter { it.isArReady }.map { it.siteId }.toSet()

        val names = LinkedHashMap<String, String>()
        downloaded.filter { it.siteId !in available }.forEach { names[it.siteId] = it.siteName }
        queued.filter { it.siteId !in available }.forEach { names.putIfAbsent(it.siteId, it.siteName) }
        if (names.isEmpty()) return@withContext emptyList()

        names.keys.forEach { siteId ->
            Log.i(TAG, "Removing $siteId: no longer published with AR")
            if (isDownloaded(appContext, siteId)) {
                delete(appContext, siteId, excludeFromAutoDownload = false)
            } else {
                cancel(appContext, siteId, excludeFromAutoDownload = false)
            }
        }

        // Only downloads the visitor had are worth telling them about.
        val removedDownloads = downloaded.filter { it.siteId in names }.map { it.siteName.ifBlank { it.siteId } }
        if (removedDownloads.isNotEmpty()) _retired.tryEmit(removedDownloads)
        names.keys.toList()
    }

    // ---------------------------------------------------------------------
    // Keeping downloads current
    // ---------------------------------------------------------------------

    /** Shortest gap between two automatic checks. */
    private const val MAINTENANCE_INTERVAL_MS = 10L * 60L * 1000L

    @Volatile
    private var maintenanceStarted = false

    @Volatile
    private var lastMaintenanceAt = 0L

    private val maintenanceLock = Mutex()

    /**
     * Call once when the app starts. Each time the phone comes back online
     * (and at start, if online), tidies up sites that were taken down and
     * looks for newer versions of downloaded ones. Before, this only ran
     * once per launch, so coming back into signal mid-visit changed nothing.
     */
    fun startMaintenance(context: Context) {
        if (maintenanceStarted) return
        maintenanceStarted = true
        val appContext = context.applicationContext
        downloadScope.launch {
            ConnectivityObserver.observe(appContext)
                .distinctUntilChanged()
                .filter { online -> online }
                .collect { runMaintenance(appContext, force = false) }
        }
    }

    /**
     * The same check right now (e.g. after signing in), unless one ran in
     * the last minute.
     */
    fun refreshNow(context: Context) {
        val appContext = context.applicationContext
        downloadScope.launch { runMaintenance(appContext, force = true) }
    }

    private suspend fun runMaintenance(context: Context, force: Boolean) {
        if (maintenanceLock.isLocked) return
        maintenanceLock.withLock {
            val since = System.currentTimeMillis() - lastMaintenanceAt
            val gap = if (force) 60_000L else MAINTENANCE_INTERVAL_MS
            if (since < gap) return@withLock
            lastMaintenanceAt = System.currentTimeMillis()
            runCatching { removeRetiredSites(context) }
                .onFailure { Log.w(TAG, "Couldn't check for removed sites", it) }
            runCatching { checkForUpdates(context) }
                .onFailure { Log.w(TAG, "Couldn't check for updates", it) }
        }
    }

    // ---------------------------------------------------------------------
    // Removing
    // ---------------------------------------------------------------------

    /**
     * Deletes one site's files.
     *
     * Files shared with another downloaded site are kept. Assets are named
     * by a hash of their URL, so two sites using the same model point at one
     * file — deleting it for one would silently break the other.
     */
    suspend fun delete(context: Context, siteId: String, excludeFromAutoDownload: Boolean = true): Boolean =
        withContext(Dispatchers.IO) {
            // Stop a download for this site first, and wait until it has
            // stopped, so it can't write a manifest after the removal.
            val active = running?.takeIf { it.first == siteId }?.second
            if (siteId in _tasks.value) cancel(context, siteId, excludeFromAutoDownload)
            active?.join()
            // Removed by the visitor: don't let auto-download put it back.
            if (excludeFromAutoDownload) DownloadPreferences.excludeFromAutoDownload(context, siteId)

            val manifest = manifestFor(context, siteId)
            if (!manifest.isFile) return@withContext false

            val mine = readManifest(context, manifest)?.files.orEmpty()
            val keep = filesOfOtherSites(context, siteId)

            mine.filterNot { it in keep }.forEach { path ->
                runCatching { File(path).delete() }
            }

            manifest.delete().also { _changed.update { it + 1 } }
        }

    // ---------------------------------------------------------------------
    // Storage sweep
    // ---------------------------------------------------------------------

    /** Browse-time cache (models/narration not in any download) is kept under this. */
    private const val UNREFERENCED_CACHE_CAP_BYTES = 500L * 1024L * 1024L

    /** An unreferenced file not used for this long is stale (e.g. a replaced model). */
    private const val UNREFERENCED_MAX_AGE_MS = 30L * 24L * 60L * 60L * 1000L

    /**
     * Never touch anything written or used this recently: it may belong to a
     * download that is running right now and hasn't written its manifest yet.
     */
    private const val SWEEP_GRACE_MS = 60L * 60L * 1000L

    @Volatile
    private var sweepStarted = false

    /**
     * Once per app start, in the background: removes files nothing refers to.
     *
     * Models prefetched while browsing (ArModelCache) and narration cached by
     * the player are keyed by URL and were never deleted, so every replaced
     * model and every site merely looked at stayed in filesDir for good —
     * which Android never reclaims, and Offline Downloads can't show. Same
     * for partial files left by crashed or cancelled downloads.
     *
     * Conservative on purpose:
     *  - anything any download manifest refers to is always kept (matched by
     *    file name, so a different spelling of the same path can't matter);
     *  - if any manifest can't be read, nothing is deleted at all;
     *  - nothing touched within the last hour is deleted;
     *  - partial files of the download queue are kept while it has entries,
     *    so paused downloads can still resume.
     */
    fun sweepStorageOnce(context: Context) {
        if (sweepStarted) return
        sweepStarted = true
        val appContext = context.applicationContext
        downloadScope.launch {
            runCatching { sweepStorage(appContext) }
                .onFailure { Log.w(TAG, "Storage sweep failed", it) }
        }
    }

    private fun sweepStorage(context: Context) {

        val keepNames = referencedFileNames(context) ?: return

        val queueHasEntries = _tasks.value.isNotEmpty() || runCatching {
            JSONArray(
                context.getSharedPreferences(QUEUE_PREFS, Context.MODE_PRIVATE)
                    .getString(KEY_TASKS, "[]")
            ).length() > 0
        }.getOrDefault(true)

        val now = System.currentTimeMillis()
        val dirs = listOf(OfflineStore.modelsDir(context), OfflineStore.audioDir(context))
        val unreferenced = mutableListOf<File>()

        dirs.forEach { dir ->
            dir.listFiles()?.forEach { file ->
                if (!file.isFile || file.name in keepNames) return@forEach
                if (now - file.lastModified() < SWEEP_GRACE_MS) return@forEach

                val name = file.name
                if (name.endsWith(".glb.part") || name.endsWith(".mp3.part")) {
                    // The download queue's own partials: resumable, so only
                    // removed once the queue is empty and nothing can resume them.
                    if (!queueHasEntries) file.delete()
                } else if (name.endsWith(".part") || name.endsWith(".download")) {
                    // ArModelCache's and the narration player's temporary
                    // files: never resumed, each attempt starts over.
                    file.delete()
                } else if (name.endsWith(".glb") || name.endsWith(".mp3")) {
                    unreferenced.add(file)
                }
            }
        }

        // Stale first, then least recently used until under the cap.
        // (ArModelCache and the narration player refresh lastModified on use.)
        val survivors = unreferenced
            .filterNot { now - it.lastModified() > UNREFERENCED_MAX_AGE_MS && it.delete() }
            .sortedBy { it.lastModified() }
        var total = survivors.sumOf { it.length() }
        for (file in survivors) {
            if (total <= UNREFERENCED_CACHE_CAP_BYTES) break
            val size = file.length()
            if (file.delete()) total -= size
        }
    }

    /**
     * Every file name any download manifest refers to, or null if any
     * manifest couldn't be read (then the sweep keeps everything).
     */
    private fun referencedFileNames(context: Context): Set<String>? {
        val names = mutableSetOf<String>()
        val manifests = manifestsDir(context).listFiles() ?: return null
        for (manifest in manifests) {
            if (manifest.extension != "json") continue
            val json = runCatching { JSONObject(manifest.readText()) }.getOrNull() ?: return null
            json.optJSONArray("files")?.let { paths ->
                for (i in 0 until paths.length()) names += File(paths.getString(i)).name
            }
            json.optString("modelPath").takeIf { it.isNotBlank() }?.let { names += File(it).name }
            json.optJSONObject("audio")?.let { audio ->
                audio.keys().forEach { key ->
                    audio.optString(key).takeIf { it.isNotBlank() }?.let { names += File(it).name }
                }
            }
        }
        return names
    }

    /** Human-readable size, for a button or a list row. */
    fun formatBytes(bytes: Long): String = when {
        bytes >= 1_048_576L -> "%.0f MB".format(bytes / 1_048_576.0)
        bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
