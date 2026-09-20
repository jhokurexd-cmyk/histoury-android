package com.histoury.app.data.offline

import com.histoury.app.data.model.referenceImages
import android.content.Context
import android.util.Log
import com.histoury.app.data.model.HistoricalSite
import com.histoury.app.data.model.SiteContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloading a whole site for offline use, and knowing what has been
 * downloaded.
 *
 * This sits on top of [OfflineStore], which already decides where files
 * live. The difference is intent: the AR screen caches a model because
 * somebody happened to open it, whereas this downloads everything for a site
 * because somebody asked for it in advance — and, crucially, records that
 * they did, so it can be listed and deleted later.
 *
 * That record is a small JSON manifest per site rather than a database. The
 * files themselves cannot live in a database anyway — ARCore and MediaPlayer
 * both want a real path on disk — so a database would only be a second copy
 * of what the folder already knows, and the two would eventually disagree.
 * The manifest exists for the one thing a folder cannot answer: which files
 * belong to which site.
 */
object OfflineSiteManager {

    private const val TAG = "OfflineSite"

    /** What the app knows about one site's downloaded files. */
    data class SiteDownload(
        val siteId: String,
        val siteName: String,
        val bytes: Long,
        val fileCount: Int,
        val downloadedAt: Long
    )

    /** Progress of a download in flight, 0f..1f. */
    fun interface ProgressListener {
        fun onProgress(fraction: Float)
    }

    private fun manifestsDir(context: Context): File =
        File(File(context.filesDir, "offline"), "manifests").apply { mkdirs() }

    private fun manifestFor(context: Context, siteId: String): File =
        File(manifestsDir(context), "$siteId.json")

    fun isDownloaded(context: Context, siteId: String): Boolean =
        manifestFor(context, siteId).isFile

    /**
     * Every asset a site needs to work with no connection.
     *
     * The reference photos are included even though they are small: without
     * them ARCore cannot recognise the structure at all. Geospatial-only
     * sites have none and need a connection on site anyway (localisation
     * runs against Google's servers).
     */
    private fun assetsFor(site: HistoricalSite, content: SiteContent?): List<Pair<String, String>> {

        val assets = mutableListOf<Pair<String, String>>()

        if (site.arModelUrl.isNotBlank()) {
            assets += site.arModelUrl to "glb"
        }
        // Every reference photo, not just the primary one — any of them may
        // be the one the camera recognises on the day.
        site.referenceImages.forEach { reference ->
            assets += reference.url to "img"
        }
        content?.audioUrls?.values?.forEach { url ->
            if (url.isNotBlank()) assets += url to "mp3"
        }

        return assets
    }

    private fun destinationFor(context: Context, url: String, extension: String): File {
        val directory = when (extension) {
            "glb" -> OfflineStore.modelsDir(context)
            "img" -> OfflineStore.referencesDir(context)
            else -> OfflineStore.audioDir(context)
        }
        return OfflineStore.fileFor(directory, url, extension)
    }

    /**
     * Asks each server how big its file is, without downloading it.
     *
     * A HEAD request returns the headers only, so the total can be shown
     * before a visitor commits to it. Worth doing: an AR model runs to tens
     * of megabytes, and a download button with no number on it is how an app
     * ends up quietly being the largest thing on someone's phone.
     *
     * Returns null when any size is unknown, so the caller can say "size
     * unknown" rather than print a confident wrong total.
     */
    suspend fun estimateBytes(
        site: HistoricalSite,
        content: SiteContent?
    ): Long? = withContext(Dispatchers.IO) {

        var total = 0L

        assetsFor(site, content).forEach { (url, _) ->
            val length = runCatching {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "HEAD"
                    connectTimeout = 15_000
                    readTimeout = 15_000
                    instanceFollowRedirects = true
                }
                connection.connect()
                val value = connection.contentLengthLong
                connection.disconnect()
                value
            }.getOrDefault(-1L)

            if (length <= 0L) return@withContext null
            total += length
        }

        if (total == 0L) null else total
    }

    /**
     * Downloads everything for a site and writes the manifest.
     *
     * Progress is reported per completed file rather than per byte. Byte
     * progress would need every size up front and a running total across
     * streams; per-file is coarser but honest, and with three or four assets
     * the bar still moves often enough to show the thing is alive.
     */
    suspend fun download(
        context: Context,
        site: HistoricalSite,
        content: SiteContent?,
        onProgress: ProgressListener? = null
    ): Result<SiteDownload> = withContext(Dispatchers.IO) {

        val assets = assetsFor(site, content)

        if (assets.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("This site has nothing to download yet.")
            )
        }

        val stored = mutableListOf<File>()

        assets.forEachIndexed { index, (url, extension) ->

            val destination = destinationFor(context, url, extension)

            // Skipped when already present — a visitor who has opened the AR
            // experience has the model already, and re-downloading tens of
            // megabytes to tell them so would be absurd.
            if (!(destination.isFile && destination.length() > 0L)) {
                val outcome = fetch(url, destination)
                if (outcome.isFailure) {
                    return@withContext Result.failure(
                        outcome.exceptionOrNull()
                            ?: IllegalStateException("Download failed.")
                    )
                }
            }

            stored += destination
            onProgress?.onProgress((index + 1).toFloat() / assets.size)
        }

        val record = SiteDownload(
            siteId = site.siteId,
            siteName = site.siteName,
            bytes = stored.sumOf { it.length() },
            fileCount = stored.size,
            downloadedAt = System.currentTimeMillis()
        )

        writeManifest(context, record, stored)

        Result.success(record)
    }

    private fun fetch(url: String, destination: File): Result<Unit> = runCatching {

        val partial = File(destination.parentFile, "${destination.name}.part")
        partial.delete()

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 30_000
            readTimeout = 120_000
            instanceFollowRedirects = true
        }

        connection.connect()
        check(connection.responseCode in 200..299) {
            "Download returned HTTP ${connection.responseCode}."
        }

        connection.inputStream.use { input ->
            partial.outputStream().buffered().use { output -> input.copyTo(output) }
        }
        connection.disconnect()

        check(partial.length() > 0L) { "Downloaded file was empty." }

        // Renamed only once complete. A half-written file left under the real
        // name would be treated as cached from then on and fail forever.
        if (destination.exists()) destination.delete()
        if (!partial.renameTo(destination)) {
            partial.copyTo(destination, overwrite = true)
            partial.delete()
        }
    }

    private fun writeManifest(context: Context, record: SiteDownload, files: List<File>) {

        val json = JSONObject().apply {
            put("siteId", record.siteId)
            put("siteName", record.siteName)
            put("bytes", record.bytes)
            put("downloadedAt", record.downloadedAt)
            put("files", JSONArray().apply {
                files.forEach { put(it.absolutePath) }
            })
        }

        runCatching { manifestFor(context, record.siteId).writeText(json.toString()) }
            .onFailure { Log.e(TAG, "Could not write manifest", it) }
    }

    /** Everything currently downloaded, newest first. */
    suspend fun listDownloads(context: Context): List<SiteDownload> =
        withContext(Dispatchers.IO) {

            manifestsDir(context).listFiles()
                ?.filter { it.extension == "json" }
                ?.mapNotNull { file ->
                    runCatching {
                        val json = JSONObject(file.readText())
                        val paths = json.optJSONArray("files")

                        // Recomputed from disk rather than trusted from the
                        // manifest. If a file has gone — cleared app data, a
                        // failed write — the listing should say so instead of
                        // reporting megabytes that are not there.
                        var bytes = 0L
                        var present = 0
                        for (i in 0 until (paths?.length() ?: 0)) {
                            val candidate = File(paths!!.getString(i))
                            if (candidate.isFile) {
                                bytes += candidate.length()
                                present++
                            }
                        }

                        SiteDownload(
                            siteId = json.optString("siteId"),
                            siteName = json.optString("siteName"),
                            bytes = bytes,
                            fileCount = present,
                            downloadedAt = json.optLong("downloadedAt")
                        )
                    }.getOrNull()
                }
                ?.sortedByDescending { it.downloadedAt }
                ?: emptyList()
        }

    /**
     * Deletes one site's files.
     *
     * Files shared with another downloaded site are kept. Assets are named
     * by a hash of their URL, so two sites using the same model point at one
     * file — deleting it for one would silently break the other.
     */
    suspend fun delete(context: Context, siteId: String): Boolean =
        withContext(Dispatchers.IO) {

            val manifest = manifestFor(context, siteId)
            if (!manifest.isFile) return@withContext false

            val mine = runCatching {
                val paths = JSONObject(manifest.readText()).optJSONArray("files")
                buildList {
                    for (i in 0 until (paths?.length() ?: 0)) add(paths!!.getString(i))
                }
            }.getOrDefault(emptyList())

            val keep = mutableSetOf<String>()
            manifestsDir(context).listFiles()
                ?.filter { it.extension == "json" && it.nameWithoutExtension != siteId }
                ?.forEach { other ->
                    runCatching {
                        val paths = JSONObject(other.readText()).optJSONArray("files")
                        for (i in 0 until (paths?.length() ?: 0)) keep += paths!!.getString(i)
                    }
                }

            mine.filterNot { it in keep }.forEach { path ->
                runCatching { File(path).delete() }
            }

            manifest.delete()
        }

    /** Human-readable size, for a button or a list row. */
    fun formatBytes(bytes: Long): String = when {
        bytes >= 1_048_576L -> "%.0f MB".format(bytes / 1_048_576.0)
        bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
