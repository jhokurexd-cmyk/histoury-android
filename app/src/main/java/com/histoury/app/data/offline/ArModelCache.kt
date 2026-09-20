package com.histoury.app.data.offline

import android.content.Context
import android.util.Log
import com.histoury.app.data.model.HistoricalSite
import com.histoury.app.data.model.isArReady
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

    /** Starts downloading [site]'s model in the background, if it has AR. */
    fun prefetch(context: Context, site: HistoricalSite?) {
        if (site == null || !site.isArReady) return
        prefetch(context, site.arModelUrl)
    }

    /** Starts downloading [url] in the background. Safe to call repeatedly. */
    fun prefetch(context: Context, url: String) {
        if (url.isBlank()) return
        val appContext = context.applicationContext
        scope.async {
            runCatching { get(appContext, url) }
                .onFailure { Log.w("HistouryAR", "Background model download failed", it) }
        }
    }

    /** The model file on the phone, downloading it (or joining a download) if needed. */
    suspend fun get(context: Context, url: String): File {

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

    private fun isValidGlb(file: File): Boolean {
        if (!file.isFile || file.length() < 12L || file.length() > 250L * 1024L * 1024L) {
            return false
        }

        return runCatching {
            file.inputStream().buffered().use { input ->
                val header = ByteArray(4)
                input.read(header) == 4 &&
                    header.contentEquals(byteArrayOf(0x67, 0x6C, 0x54, 0x46)) // "glTF"
            }
        }.getOrDefault(false)
    }
}
