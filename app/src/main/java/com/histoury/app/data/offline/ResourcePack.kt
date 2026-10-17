package com.histoury.app.data.offline

import android.content.Context
import android.widget.Toast
import com.histoury.app.data.model.HistoricalSite
import com.histoury.app.data.model.isArReady
import com.histoury.app.data.repository.HistoricalSiteRepository
import com.histoury.app.data.repository.SiteContentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Download All" / "Download for This Trip": several sites at once.
 *
 * Not a second download system — every site is handed to
 * [OfflineSiteManager]'s queue (one at a time, same Wi-Fi/mobile-data
 * rules, same resume and verification), and this only follows the group so
 * Home's bell can show overall progress and one summary message is shown
 * at the end instead of one per site.
 *
 * Also decides when the one-time "Download Resources" pop-up is offered.
 */
object ResourcePack {

    sealed interface State {
        /** Nothing running. */
        object Idle : State

        data class Downloading(
            val done: Int,
            val total: Int,
            val currentName: String,
            val currentFraction: Float
        ) : State {
            val overall: Float
                get() = if (total == 0) 1f else ((done + currentFraction) / total).coerceIn(0f, 1f)
        }

        /** Finished; [failed] is how many sites couldn't be downloaded. */
        data class Finished(val failed: Int) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watcher: Job? = null

    private val _runSiteIds = MutableStateFlow<Set<String>>(emptySet())

    /**
     * Every site in the current group download. Offline Downloads skips its
     * own per-site failure toast for these, since the group ends with one
     * summary message instead.
     */
    val runSiteIds: StateFlow<Set<String>> = _runSiteIds.asStateFlow()

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _missing = MutableStateFlow<List<HistoricalSite>?>(null)

    /**
     * The last known list of AR sites not on the phone yet (null = not
     * checked yet). Drives the "Download Resources" entry in Home's
     * notifications, which stays there after the pop-up is closed.
     */
    val missing: StateFlow<List<HistoricalSite>?> = _missing.asStateFlow()

    /** Every published site with an AR experience, from the last check. */
    @Volatile
    private var arSiteIds: Set<String> = emptySet()

    /**
     * Sites with an AR experience that aren't on the phone yet, or null when
     * the site list couldn't be read — the repository reports a failed
     * fetch as an empty list, and that must not be mistaken for "everything
     * is downloaded".
     */
    suspend fun missingSites(context: Context): List<HistoricalSite>? {
        val all = HistoricalSiteRepository().getHistoricalSites()
        if (all.isEmpty()) return null
        val arSites = all.filter { it.isArReady }
        arSiteIds = arSites.map { it.siteId }.toSet()
        return arSites
            .filterNot { OfflineSiteManager.isDownloaded(context, it.siteId) }
            .also { _missing.value = it }
    }

    // ----- the one-time pop-up -----

    /**
     * Whether to show the big "Download Resources" pop-up now: the first
     * time the visitor meets the feature, and afterwards only when a new AR
     * experience has been published since they last answered it. Closing it
     * is respected — it is not shown on every launch. Offline Downloads
     * (Menu) and the bell on Home stay available the whole time.
     */
    @Volatile
    private var promptCheckedThisLaunch = false

    fun shouldOfferPrompt(context: Context, missing: List<HistoricalSite>): Boolean {
        if (promptCheckedThisLaunch) return false
        promptCheckedThisLaunch = true
        if (missing.isEmpty()) return false
        if (!DownloadPreferences.isResourcePromptHandled(context)) return true
        val known = DownloadPreferences.resourcePromptKnownSites(context)
        return missing.any { it.siteId !in known }
    }

    /** The visitor answered the pop-up (any button, including Close). */
    fun markPromptHandled(context: Context) {
        DownloadPreferences.markResourcePromptHandled(context, arSiteIds)
    }

    /** Total size of [sites], or null when any file's size is unknown. */
    suspend fun estimateBytes(sites: List<HistoricalSite>): Long? = coroutineScope {
        val sizes = sites.map { site ->
            async(Dispatchers.IO) {
                OfflineSiteManager.estimateBytes(site, SiteContentRepository().getSiteContent(site.siteId))
            }
        }.awaitAll()
        if (sizes.any { it == null }) null else sizes.filterNotNull().sum().takeIf { it > 0L }
    }

    /**
     * Queues every site in [sites] that isn't downloaded yet.
     * [allowMobile] is the one-time "Use Mobile Data This Time" answer.
     */
    fun downloadAll(context: Context, sites: List<HistoricalSite>, allowMobile: Boolean = false) {
        val appContext = context.applicationContext
        val toGet = sites.filterNot { OfflineSiteManager.isDownloaded(appContext, it.siteId) }
        if (toGet.isEmpty()) return

        scope.launch {
            toGet.forEach { site ->
                val content = runCatching {
                    SiteContentRepository().getSiteContent(site.siteId)
                }.getOrNull()
                OfflineSiteManager.startDownload(appContext, site, content, allowMobile = allowMobile)
            }
            // Joined to the group only once every site is in the queue, so
            // the group can't look finished before it has started.
            _runSiteIds.value = _runSiteIds.value + toGet.map { it.siteId }
            follow(appContext)
        }
    }

    /** Follows the group in the shared queue until none of it is pending. */
    private fun follow(context: Context) {
        if (watcher?.isActive == true) return
        watcher = scope.launch {
            OfflineSiteManager.tasks.collect { tasks ->
                val group = _runSiteIds.value
                if (group.isEmpty()) return@collect

                val pending = group.mapNotNull { tasks[it] }.filter { it.isPending }
                if (pending.isNotEmpty()) {
                    val current = pending.firstOrNull { it.phase == OfflineSiteManager.Phase.DOWNLOADING }
                        ?: pending.first()
                    _state.value = State.Downloading(
                        done = group.size - pending.size,
                        total = group.size,
                        currentName = current.siteName,
                        currentFraction = current.fraction ?: 0f
                    )
                    return@collect
                }

                // The whole group has ended.
                val failed = group.count { tasks[it]?.phase == OfflineSiteManager.Phase.FAILED }
                val allSaved = group.all { OfflineSiteManager.isDownloaded(context, it) }
                _state.value = State.Finished(failed)
                _runSiteIds.value = emptySet()

                withContext(Dispatchers.Main) {
                    when {
                        allSaved -> Toast.makeText(
                            context,
                            "AR resources downloaded. They'll work even with a weak connection.",
                            Toast.LENGTH_LONG
                        ).show()

                        failed > 0 -> Toast.makeText(
                            context,
                            "$failed site(s) couldn't be downloaded. Try again from Offline Downloads.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                _state.value = State.Idle
                runCatching { missingSites(context) }
            }
        }
    }

    /** Clears a finished result once it has been shown. */
    fun acknowledge() {
        if (_state.value is State.Finished) _state.value = State.Idle
    }
}
