package com.histoury.app.data.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.histoury.app.data.repository.HistoricalSiteRepository
import com.histoury.app.data.repository.PlaceRepository
import com.histoury.app.data.repository.SiteContentRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Progress of the offline warm-up, exposed so a Menu row or a Home banner
 * can show what is available without a connection.
 */
sealed class OfflineCacheState {

    object Idle : OfflineCacheState()

    /** [total] is 0 until the site list comes back and image counting starts. */
    data class Running(val completed: Int, val total: Int) : OfflineCacheState()

    data class Ready(
        val sites: Int,
        val images: Int,
        val completedAt: Long
    ) : OfflineCacheState()

    /** Nothing was fetched this run — offline, or the cache is still fresh. */
    data class Skipped(val reason: String) : OfflineCacheState()
}

/**
 * Fills the offline caches ahead of time.
 *
 * Firestore and Coil both cache whatever they happen to have fetched, which
 * means a tourist only has offline access to sites they already opened —
 * the wrong half of the problem, since the moment you lose signal is the
 * moment you want to read about a site for the first time.
 *
 * This walks every published site and place once, which pulls their
 * documents into Firestore's local cache and their photos into Coil's disk
 * cache. Afterwards the whole of Intramuros reads offline: Home, Explore,
 * Site Details tabs, thumbnails, and featured images.
 *
 * Nothing else in the app needs to know this ran. Repositories keep calling
 * Firestore and screens keep calling AsyncImage; both simply start hitting
 * a warm cache instead of the network.
 */
object OfflineContentCache {

    private const val PREFS_NAME = "histoury_offline_cache"
    private const val KEY_LAST_RUN = "last_run_at"
    private const val KEY_SITE_COUNT = "cached_site_count"
    private const val KEY_IMAGE_COUNT = "cached_image_count"

    /** Skip the walk if the caches were refreshed within this window. */
    private const val REFRESH_INTERVAL_MS = 6L * 60L * 60L * 1000L

    /** Kept low on purpose — this runs behind the user's actual browsing. */
    private const val PARALLEL_DOWNLOADS = 4

    /**
     * Images are fetched at a small decode size. The disk cache is keyed by
     * URL and stores the original bytes, so a later full-size load still
     * hits disk; this only keeps the warm-up from decoding full bitmaps.
     */
    private const val PREFETCH_DECODE_PX = 96

    private val runLock = Mutex()

    private val _state = MutableStateFlow<OfflineCacheState>(OfflineCacheState.Idle)
    val state: StateFlow<OfflineCacheState> = _state.asStateFlow()

    /**
     * Safe to call on every app launch. Returns immediately if a warm-up is
     * already running, if the device is offline, or if the caches were
     * filled recently — pass [force] for a user-triggered "Download for
     * offline" button.
     */
    suspend fun warmUp(context: Context, force: Boolean = false) {

        if (runLock.isLocked) {
            return
        }

        runLock.withLock {
            run(context.applicationContext, force)
        }
    }

    /** Last known cache summary, for display before any warm-up runs. */
    fun lastKnownState(context: Context): OfflineCacheState {

        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val lastRun = prefs.getLong(KEY_LAST_RUN, 0L)

        if (lastRun == 0L) {
            return OfflineCacheState.Idle
        }

        return OfflineCacheState.Ready(
            sites = prefs.getInt(KEY_SITE_COUNT, 0),
            images = prefs.getInt(KEY_IMAGE_COUNT, 0),
            completedAt = lastRun
        )
    }

    private suspend fun run(context: Context, force: Boolean) {

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val lastRun = prefs.getLong(KEY_LAST_RUN, 0L)

        if (!isOnline(context)) {

            // Not a failure. Whatever was cached on the last online run is
            // still there and still being served.
            _state.value = if (lastRun == 0L) {
                OfflineCacheState.Skipped("Offline — no saved content yet")
            } else {
                lastKnownState(context)
            }

            return
        }

        val isFresh = lastRun > 0L &&
            System.currentTimeMillis() - lastRun < REFRESH_INTERVAL_MS

        if (isFresh && !force) {
            _state.value = lastKnownState(context)
            return
        }

        _state.value = OfflineCacheState.Running(completed = 0, total = 0)

        // Reading through the existing repositories rather than querying
        // Firestore directly: same status filters, same slug fallbacks, and
        // the cache is populated with exactly the documents the screens
        // will later ask for.
        val sites = HistoricalSiteRepository().getHistoricalSites()

        val places = PlaceRepository().getPlaces()

        val contentRepository = SiteContentRepository()

        // Pulls the Overview / Timeline / Stories / Sources payload for each
        // site so Site Details opens with full text offline, not just the
        // card blurb from the site list.
        sites.forEach { site ->

            val lookupId = site.siteId.ifBlank { site.documentId }

            if (lookupId.isNotBlank()) {
                contentRepository.getSiteContent(lookupId)
            }
        }

        val imageUrls = collectImageUrls(sites, places)

        _state.value = OfflineCacheState.Running(completed = 0, total = imageUrls.size)

        var completed = 0

        coroutineScope {

            imageUrls.chunked(PARALLEL_DOWNLOADS).forEach { batch ->

                batch.map { url ->
                    async { prefetchImage(context, url) }
                }.awaitAll()

                completed += batch.size

                _state.value = OfflineCacheState.Running(
                    completed = completed,
                    total = imageUrls.size
                )
            }
        }

        val finishedAt = System.currentTimeMillis()

        prefs.edit()
            .putLong(KEY_LAST_RUN, finishedAt)
            .putInt(KEY_SITE_COUNT, sites.size)
            .putInt(KEY_IMAGE_COUNT, imageUrls.size)
            .apply()

        _state.value = OfflineCacheState.Ready(
            sites = sites.size,
            images = imageUrls.size,
            completedAt = finishedAt
        )
    }

    private fun collectImageUrls(
        sites: List<com.histoury.app.data.model.HistoricalSite>,
        places: List<com.histoury.app.data.model.Place>
    ): List<String> {

        val urls = mutableListOf<String>()

        sites.forEach { site ->
            if (site.featuredImage.isNotBlank()) {
                urls.add(site.featuredImage)
            }
        }

        places.forEach { place ->

            if (place.featuredImage.isNotBlank()) {
                urls.add(place.featuredImage)
            }

            place.galleryImages.forEach { url ->
                if (url.isNotBlank()) {
                    urls.add(url)
                }
            }
        }

        return urls.distinct()
    }

    private suspend fun prefetchImage(context: Context, url: String) {

        try {

            val request = ImageRequest.Builder(context)
                .data(url)
                .size(PREFETCH_DECODE_PX)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()

            context.imageLoader.execute(request)

        } catch (e: Exception) {

            // One unreachable image must not abort the rest of the walk.
        }
    }

    private fun isOnline(context: Context): Boolean {

        val manager = context
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false

        val network = manager.activeNetwork ?: return false

        val capabilities = manager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
