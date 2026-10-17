package com.histoury.app.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Exports a Histoury capture to the phone's own Photos app.
 *
 * Deliberately a copy, not a move. The Gallery tab keeps its own copy (in
 * the app's private storage — see LocalGalleryStore), so nothing here
 * removes or alters the original. Because that private copy is removed if
 * the app is uninstalled, this is also how a visitor keeps a photo for good. A visitor asking for a photo on their
 * device is asking for a second copy they can share from Instagram or send
 * to their group chat, and that is all this does.
 *
 * Nothing is exported automatically. Every file this writes is the result of
 * somebody tapping a button, which is why there is no "sync" of any kind:
 * silently filling a stranger's camera roll with dozens of AR captures would
 * be a rude thing for a tourism app to do.
 */
class DeviceGalleryRepository {

    private val storage = FirebaseStorage.getInstance()

    sealed interface Result {
        /**
         * [uri] is the photo in the device's media store. [alreadySaved] is
         * true when an identical copy was already there, so nothing new was
         * written.
         */
        data class Success(val uri: Uri, val alreadySaved: Boolean = false) : Result
        data class Failed(val reason: String) : Result
    }

    /**
     * Whether a copy saved earlier is still on the device. The visitor can
     * delete it from their Photos app at any time, and then the Download
     * button should come back. A row that is still pending or empty counts
     * as gone.
     */
    suspend fun isStillSaved(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                cursor.moveToFirst() && cursor.getLong(1) > 0L
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Downloads the capture behind [imageUrl] and files it under
     * Pictures/Histoury in the device's media store.
     *
     * The bytes are written through EXACTLY as they came down. Decoding to a
     * Bitmap and re-compressing would be the obvious way to do this and it
     * would be wrong: a JPEG re-encode is lossy no matter how high the
     * quality setting, so the saved copy would be measurably worse than the
     * one in the app for no reason at all. Copying the original stream also
     * means a large photo never has to exist as a decoded Bitmap in memory.
     *
     * [siteName] only names the file. It is slugified rather than used raw
     * because a site name can legitimately contain characters the filesystem
     * will not take — "Fort Santiago / Fuerza de Santiago" being exactly the
     * sort of record this app has.
     */
    suspend fun saveToDevice(
        context: Context,
        imageUrl: String,
        siteName: String,
        capturedAtMillis: Long
    ): Result = withContext(Dispatchers.IO) {

        if (imageUrl.isBlank()) {
            return@withContext Result.Failed("That photo has no image to save.")
        }

        val displayName = fileNameFor(siteName, capturedAtMillis)

        // The file name is derived from the capture itself, so the same
        // photo always gets the same name. If it's already in the album
        // (saved earlier, or the saved-state was lost with a data clear),
        // point at that copy instead of filling the camera roll with
        // "histoury-...(1).jpg" duplicates.
        findExisting(context, displayName)?.let { existing ->
            return@withContext Result.Success(existing, alreadySaved = true)
        }

        return@withContext try {

            // Through the Storage SDK rather than a plain HTTPS GET, so the
            // request carries the user's credentials and the same rules that
            // guard the bucket still apply. A download URL alone would work
            // today only because these objects happen to be readable.
            // Photos taken with this version are already on the phone (see
            // LocalGalleryStore): read the file. Older, uploaded ones are
            // downloaded as before.
            val bytes = if (LocalGalleryStore.isLocal(imageUrl)) {
                LocalGalleryStore.readBytes(imageUrl)
                    ?: return@withContext Result.Failed("That photo is no longer on this phone.")
            } else {
                storage.getReferenceFromUrl(imageUrl)
                    .getBytes(MAX_DOWNLOAD_BYTES)
                    .await()
            }

            val values = ContentValues().apply {

                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, MIME_JPEG)
                put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                    // Scoped storage: the app names a folder inside the
                    // shared Pictures collection and the system places it.
                    // No storage permission is involved on these versions —
                    // an app is always allowed to add its own media.
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/" + ALBUM_NAME
                    )

                    // Marks the row incomplete until the bytes are actually
                    // there. Without it the Photos app can index a
                    // zero-length file the instant the row appears and show
                    // the visitor a broken thumbnail.
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver

            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext Result.Failed("Couldn't create the file on your device.")

            // The row exists from here on. If the write can't finish —
            // no stream, storage full, the coroutine cancelled — it is
            // removed, because leaving it would put a permanently broken
            // entry in the visitor's camera roll.
            val written = try {
                resolver.openOutputStream(uri)?.use { output ->
                    output.write(bytes)
                    true
                } ?: false
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }

            if (!written) {
                resolver.delete(uri, null, null)
                return@withContext Result.Failed("Couldn't write the photo to your device.")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                    null,
                    null
                )
            }

            Result.Success(uri)

        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: SecurityException) {
            Log.e("HistouryGallery", "Save to device not permitted", e)
            Result.Failed("Histoury isn't allowed to save photos. Check storage permission in Settings.")
        } catch (e: java.io.IOException) {
            Log.e("HistouryGallery", "Save to device failed to write", e)
            Result.Failed("Couldn't save the photo. Your phone may be out of storage.")
        } catch (e: Exception) {
            Log.e("HistouryGallery", "Save to device failed", e)
            Result.Failed("Couldn't download that photo. Check your connection and try again.")
        }
    }

    /** A completed copy of [displayName] already in the Histoury album, if any. */
    private fun findExisting(context: Context, displayName: String): Uri? = try {

        val (selection, args) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND " +
                "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? AND " +
                "${MediaStore.Images.Media.IS_PENDING} = 0 AND " +
                "${MediaStore.Images.Media.SIZE} > 0" to
                arrayOf(displayName, Environment.DIRECTORY_PICTURES + "/" + ALBUM_NAME + "%")
        } else {
            "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND ${MediaStore.Images.Media.SIZE} > 0" to
                arrayOf(displayName)
        }

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            selection,
            args,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                android.content.ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    cursor.getLong(0)
                )
            } else {
                null
            }
        }
    } catch (e: Exception) {
        // Can't look (e.g. no read access on an old Android): save as usual.
        null
    }

    /**
     * Whether this Android version needs a runtime storage permission before
     * [saveToDevice] can work.
     *
     * Only Android 9 and below. From Android 10 the media store places the
     * file itself and an app writing its own media needs nothing granted —
     * asking anyway would produce a permission dialog that does nothing,
     * and on Android 13+ WRITE_EXTERNAL_STORAGE is not even a permission
     * the system recognises any more.
     */
    fun requiresLegacyStoragePermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    private fun fileNameFor(siteName: String, capturedAtMillis: Long): String {

        val slug = siteName
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(40)
            .ifBlank { "histoury" }

        val stamp = if (capturedAtMillis > 0L) capturedAtMillis else System.currentTimeMillis()

        return "histoury-$slug-$stamp.jpg"
    }

    private companion object {

        const val ALBUM_NAME = "Histoury"

        const val MIME_JPEG = "image/jpeg"

        /**
         * Ceiling on a single download, in bytes.
         *
         * AR captures are screen-resolution JPEGs at quality 85 — a few
         * hundred kilobytes each — so 20 MB is far above anything this will
         * legitimately meet. It exists so a corrupt or unexpected object
         * cannot be pulled entirely into memory.
         */
        const val MAX_DOWNLOAD_BYTES = 20L * 1024 * 1024
    }
}
