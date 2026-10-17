package com.histoury.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.firebase.Timestamp
import com.histoury.app.data.model.Gallery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Date
import java.util.UUID

/**
 * AR captures, kept ON THE PHONE instead of in Firebase Storage.
 *
 * Every capture used to be uploaded to the cloud bucket with a Firestore
 * record pointing at it. With many visitors each taking several photos per
 * site, that grows the bucket (and the bill) without limit, for photos only
 * their owner ever looks at. Now a capture is a JPEG in the app's private
 * storage plus an entry in a small index file beside it — no network, no
 * Storage, no Firestore.
 *
 * Layout (app-private, so no storage permission is involved and other apps
 * can't read it):
 *
 *   files/gallery/{uid}/{id}.jpg
 *   files/gallery/{uid}/index.json     [{ id, siteId, visitId, capturedAtMs, file }]
 *
 * One folder per account, so a second person signing in on the same phone
 * doesn't see the first person's photos.
 *
 * Trade-off to be aware of: private app storage is removed when the app is
 * uninstalled or its data is cleared, and it doesn't move to a new phone. The
 * Gallery's "Save to device" button (DeviceGalleryRepository) copies a photo
 * into the phone's own Photos app for keeping.
 */
object LocalGalleryStore {

    private const val ROOT_DIR = "gallery"
    private const val INDEX_FILE = "index.json"
    private const val JPEG_QUALITY = 85

    /** Serializes index read-modify-writes (a save and a delete at once). */
    private val lock = Mutex()

    private fun userDir(context: Context, uid: String): File =
        File(File(context.applicationContext.filesDir, ROOT_DIR), safe(uid)).apply { mkdirs() }

    /** uids are Firebase ids (letters/digits), but never trust a path segment. */
    private fun safe(segment: String): String = segment.replace(Regex("[^A-Za-z0-9_-]"), "_")

    /** Writes [bitmap] as a new capture. Returns the entry as the Gallery screen shows it. */
    suspend fun save(
        context: Context,
        uid: String,
        bitmap: Bitmap,
        siteId: String,
        visitId: String
    ): Gallery = withContext(Dispatchers.IO) {
        lock.withLock {
            val dir = userDir(context, uid)
            val id = UUID.randomUUID().toString()
            val capturedAtMs = System.currentTimeMillis()
            val file = File(dir, "$id.jpg")
            val partial = File(dir, "$id.part")

            // Written to a temporary name and renamed once complete, so a
            // crash mid-write never leaves a half-written photo in the grid.
            partial.outputStream().buffered().use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                    "Couldn't encode the photo."
                }
            }
            if (!partial.renameTo(file)) {
                partial.copyTo(file, overwrite = true)
                partial.delete()
            }

            val entries = readIndex(dir)
            entries.put(
                JSONObject()
                    .put("id", id)
                    .put("siteId", siteId)
                    .put("visitId", visitId)
                    .put("capturedAtMs", capturedAtMs)
                    .put("file", file.name)
            )
            writeIndex(dir, entries)

            toGallery(uid, file, id, siteId, visitId, capturedAtMs)
        }
    }

    /** This account's captures on this phone, newest first. Entries whose file is gone are dropped. */
    suspend fun list(context: Context, uid: String): List<Gallery> = withContext(Dispatchers.IO) {
        lock.withLock {
            val dir = userDir(context, uid)
            val entries = readIndex(dir)
            val result = mutableListOf<Gallery>()
            val kept = JSONArray()

            for (i in 0 until entries.length()) {
                val entry = entries.optJSONObject(i) ?: continue
                val file = File(dir, entry.optString("file"))
                if (!file.isFile || file.length() == 0L) continue
                kept.put(entry)
                result += toGallery(
                    uid = uid,
                    file = file,
                    id = entry.optString("id"),
                    siteId = entry.optString("siteId"),
                    visitId = entry.optString("visitId"),
                    capturedAtMs = entry.optLong("capturedAtMs")
                )
            }

            if (kept.length() != entries.length()) writeIndex(dir, kept)

            result.sortedByDescending { (it.capturedAt as? Timestamp)?.toDate()?.time ?: 0L }
        }
    }

    /** Deletes the given local captures. Returns the ids actually removed. */
    suspend fun delete(context: Context, uid: String, ids: Set<String>): Set<String> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptySet()
        lock.withLock {
            val dir = userDir(context, uid)
            val entries = readIndex(dir)
            val kept = JSONArray()
            val removed = mutableSetOf<String>()

            for (i in 0 until entries.length()) {
                val entry = entries.optJSONObject(i) ?: continue
                val id = entry.optString("id")
                if (id in ids) {
                    val file = File(dir, entry.optString("file"))
                    if (!file.exists() || file.delete()) removed += id else kept.put(entry)
                } else {
                    kept.put(entry)
                }
            }

            writeIndex(dir, kept)
            removed
        }
    }

    /** True if [imageUrl] points at a capture stored by this class. */
    fun isLocal(imageUrl: String): Boolean = imageUrl.startsWith("file:")

    /** The JPEG bytes of a local capture, for "Save to device". */
    suspend fun readBytes(imageUrl: String): ByteArray? = withContext(Dispatchers.IO) {
        val path = Uri.parse(imageUrl).path ?: return@withContext null
        val file = File(path)
        if (file.isFile) file.readBytes() else null
    }

    private fun toGallery(
        uid: String,
        file: File,
        id: String,
        siteId: String,
        visitId: String,
        capturedAtMs: Long
    ): Gallery {
        val at = Timestamp(Date(capturedAtMs))
        return Gallery(
            id = id,
            siteId = siteId,
            sceneId = "",
            visitId = visitId,
            userId = uid,
            uploadedBy = "",
            // A file:// URI: Coil (the grid and the lightbox) loads it as-is.
            imageUrl = Uri.fromFile(file).toString(),
            capturedAt = at,
            status = "active",
            createdAt = at,
            updatedAt = at
        )
    }

    private fun readIndex(dir: File): JSONArray {
        val file = File(dir, INDEX_FILE)
        if (!file.isFile) return JSONArray()
        return runCatching { JSONArray(file.readText()) }.getOrDefault(JSONArray())
    }

    private fun writeIndex(dir: File, entries: JSONArray) {
        val file = File(dir, INDEX_FILE)
        val tmp = File(dir, "$INDEX_FILE.tmp")
        tmp.writeText(entries.toString())
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }
}
