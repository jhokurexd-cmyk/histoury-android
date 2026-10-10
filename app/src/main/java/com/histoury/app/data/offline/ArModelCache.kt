package com.histoury.app.data.offline

import android.content.Context
import android.util.Log
import com.histoury.app.data.model.HistoricalSite
import com.histoury.app.data.model.isArReady
import com.histoury.app.util.ConnectivityObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads AR models (.glb) to the phone, once.
 *
 * The AR screen used to start downloading the model only when the visitor
 * tapped AR, so a large model's whole download sat between the tap and the
 * structure appearing. Now Site Details and the arrival screen call
 * [prefetch] as soon as a site with AR is on screen, and the AR screen calls
 * [get]:
 *
 *  - already on the phone  -> returned immediately
 *  - still downloading     -> joins that same download (never starts over)
 *  - not started           -> downloads now
 *
 * Downloads run in an app-wide scope, so leaving Site Details doesn't cancel
 * one that's halfway through.
 *
 * Files go in filesDir (OfflineStore.modelsDir), not cacheDir: Android
 * empties cacheDir when storage runs low, which could delete a model right
 * before the visit it was downloaded for. The download-token URL is part of
 * the key, so replacing a model in the admin panel creates a fresh entry.
 */
object ArModelCache {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val lock = Mutex()

    private val inFlight = mutableMapOf<String, Deferred<File>>()

    /**
     * Starts downloading [site]'s model in the background, if it has AR.
     *
     * Skipped when the site is already in Offline Downloads (its model is on
     * the phone; newer versions come through the update setting instead),
     * and when "Download over" is Wi-Fi only and the phone is on mobile data
     * — merely looking at a site shouldn't spend tens of megabytes of a
     * visitor's data. Opening AR itself still downloads what it needs.
     */
    fun prefetch(context: Context, site: HistoricalSite?) {
        if (site == null || !site.isArReady) return
        if (OfflineSiteManager.isDownloaded(context, site.siteId)) return
        val onMobile = ConnectivityObserver.currentKind(context) != ConnectivityObserver.NetworkKind.WIFI
        if (onMobile && DownloadPreferences.downloadOver(context) == DownloadNetwork.WIFI_ONLY) return
        prefetch(context, site.arModelUrl)
    }

    /** Starts downloading [url] in the background. Safe to call repeatedly. */
    fun prefetch(context: Context, url: String) {
        if (url.isBlank()) return
        val appContext = context.applicationContext
        scope.async {
            runCatching { getByUrl(appContext, url) }
                .onFailure { Log.w("HistouryAR", "Background model download failed", it) }
        }
    }

    /**
     * The model to open in AR, local copy first:
     *
     *  1. the current model already on the phone -> returned immediately
     *  2. the site is in Offline Downloads       -> its downloaded model,
     *     even if a newer version exists (the update is fetched separately,
     *     per "Automatic resource updates"), so AR opens with no connection
     *  3. otherwise                              -> downloaded from Firebase
     *     Storage now (or joins a download already running)
     */
    suspend fun get(context: Context, url: String, siteId: String? = null): File {

        val cachedFile = fileFor(context, url)
        if (withContext(Dispatchers.IO) { OfflineStore.isValidGlb(cachedFile) }) return cachedFile

        val downloaded = withContext(Dispatchers.IO) { OfflineSiteManager.localModelFor(context, siteId) }
        if (downloaded != null) return downloaded

        return getByUrl(context, url)
    }

    /** The model file on the phone, downloading it (or joining a download) if needed. */
    private suspend fun getByUrl(context: Context, url: String): File {

        val cachedFile = fileFor(context, url)
        if (isValidGlb(cachedFile)) return cachedFile

        val download = lock.withLock {
            inFlight[url]?.takeIf { it.isActive }
                ?: scope.async { download(context.applicationContext, url) }
                    .also { inFlight[url] = it }
        }

        return try {
            download.await()
        } finally {
            lock.withLock {
                if (inFlight[url] === download && !download.isActive) inFlight.remove(url)
            }
        }
    }

    private fun fileFor(context: Context, url: String): File =
        File(OfflineStore.modelsDir(context), "${OfflineStore.keyFor(url)}.glb")

    /**
     * SceneView's own remote loading goes through Fuel, which can fail on
     * Firebase download-token URLs served as application/octet-stream even
     * when the GLB is valid. Downloading ourselves and verifying the GLB
     * header avoids that.
     */
    private suspend fun download(context: Context, url: String): File = withContext(Dispatchers.IO) {

        val cachedFile = fileFor(context, url)
        if (isValidGlb(cachedFile)) return@withContext cachedFile

        val temporaryFile = File(cachedFile.parentFile, "${cachedFile.nameWithoutExtension}.download")
        temporaryFile.delete()

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 120_000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }

        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                error("Model download returned HTTP ${connection.responseCode}.")
            }

            connection.inputStream.use { input ->
                temporaryFile.outputStream().buffered().use { output ->
                    input.copyTo(output)
                }
            }

            if (!isValidGlb(temporaryFile)) {
                error("Downloaded file is not a valid GLB.")
            }

            if (cachedFile.exists()) cachedFile.delete()
            if (!temporaryFile.renameTo(cachedFile)) {
                temporaryFile.copyTo(cachedFile, overwrite = true)
                temporaryFile.delete()
            }

            cachedFile
        } finally {
            connection.disconnect()
            if (temporaryFile.exists() && !isValidGlb(temporaryFile)) {
                temporaryFile.delete()
            }
        }
    }

    /** Whole-file check (header and declared length), shared with Offline Downloads. */
    private fun isValidGlb(file: File): Boolean = OfflineStore.isValidGlb(file)
}
