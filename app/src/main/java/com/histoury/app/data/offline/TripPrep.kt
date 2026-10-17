package com.histoury.app.data.offline

import android.content.Context
import com.histoury.app.data.model.HistoricalSite
import com.histoury.app.data.model.isArReady
import com.histoury.app.data.repository.HistoricalSiteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * "Prepare for your trip": after a new itinerary is created, offers to
 * download the AR resources of just the sites in that itinerary that aren't
 * on the phone yet — not every site. Shown by TripPrepPrompt (MainActivity).
 */
object TripPrep {

    data class Request(
        val sites: List<HistoricalSite>,
        /** Total download size, null while unknown. */
        val bytes: Long? = null
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _pending = MutableStateFlow<Request?>(null)
    val pending: StateFlow<Request?> = _pending.asStateFlow()

    /**
     * Called when an itinerary is created. [stopRefIds] are the stops'
     * refIds (a site stop's refId is the site's document id); places and
     * sites without AR are ignored.
     */
    fun onItineraryCreated(context: Context, stopRefIds: List<String>) {
        if (stopRefIds.isEmpty()) return
        val appContext = context.applicationContext
        scope.launch {
            val ids = stopRefIds.toSet()
            val sites = runCatching { HistoricalSiteRepository().getHistoricalSites() }
                .getOrDefault(emptyList())
                .filter { (it.documentId in ids || it.siteId in ids) && it.isArReady }
                .filterNot { site ->
                    OfflineSiteManager.isDownloaded(appContext, site.siteId) ||
                        OfflineSiteManager.tasks.value[site.siteId]?.isPending == true
                }
            if (sites.isEmpty()) return@launch

            _pending.value = Request(sites)

            val bytes = runCatching { ResourcePack.estimateBytes(sites) }.getOrNull()
            _pending.update { current ->
                if (current != null && current.sites == sites) current.copy(bytes = bytes) else current
            }
        }
    }

    /** "Maybe Later", or the downloads were queued. */
    fun dismiss() {
        _pending.value = null
    }
}
