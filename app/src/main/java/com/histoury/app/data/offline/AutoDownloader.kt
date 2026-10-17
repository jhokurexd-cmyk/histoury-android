package com.histoury.app.data.offline

import android.content.Context
import com.histoury.app.data.repository.SiteContentRepository
import com.histoury.app.util.ConnectivityObserver
import com.histoury.app.util.ConnectivityObserver.NetworkKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * "Auto-download on Wi-Fi": whenever the phone is on Wi-Fi (and the setting
 * is on), every AR site not yet saved is added to the download queue.
 *
 * Not a separate download system — sites go into OfflineSiteManager's
 * queue marked Wi-Fi only, so they never use mobile data: if Wi-Fi drops
 * they show "Waiting for Wi-Fi" and continue when it's back. Sites the
 * visitor cancelled or removed are skipped until they download them again
 * themselves.
 *
 * Checked when the app starts on Wi-Fi, each time the phone joins Wi-Fi,
 * and when the setting is turned on.
 */
object AutoDownloader {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var started = false

    /** Call once when the app starts. */
    fun start(context: Context) {
        if (started) return
        started = true
        val appContext = context.applicationContext
        DownloadPreferences.load(appContext)

        scope.launch {
            combine(
                ConnectivityObserver.observeKind(appContext),
                DownloadPreferences.autoDownloadOnWifi
            ) { kind, enabled -> enabled && kind == NetworkKind.WIFI }
                .distinctUntilChanged()
                .collect { ready ->
                    if (ready) runCatching { queueMissing(appContext) }
                }
        }
    }

    private suspend fun queueMissing(context: Context) {
        val missing = ResourcePack.missingSites(context) ?: return
        val excluded = DownloadPreferences.autoDownloadExcluded(context)

        missing
            .filter { it.siteId !in excluded }
            // Already queued, paused or failed: leave the visitor's choice alone.
            .filter { OfflineSiteManager.tasks.value[it.siteId] == null }
            .forEach { site ->
                val content = runCatching { SiteContentRepository().getSiteContent(site.siteId) }.getOrNull()
                OfflineSiteManager.startDownload(
                    context = context,
                    site = site,
                    content = content,
                    isUpdate = false,
                    wifiOnly = true
                )
            }
    }
}
