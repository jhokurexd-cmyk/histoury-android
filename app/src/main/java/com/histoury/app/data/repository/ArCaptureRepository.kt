package com.histoury.app.data.repository

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Saves photos taken inside the AR experience.
 *
 * Two steps that both have to succeed: the JPEG goes to Storage under the
 * user's own folder, then a `gallery` document points at it. The Gallery
 * screen already reads that collection and badges every entry as an AR
 * capture, so nothing there changes.
 *
 * Ownership fields are written from the authenticated uid rather than
 * anything passed in by the caller — the same rule the Storage and Firestore
 * rules enforce after the earlier ownership-spoofing fixes.
 */
class ArCaptureRepository {

    private val db = FirebaseFirestore.getInstance()

    private val storage = FirebaseStorage.getInstance()

    /**
     * Compresses, uploads, and files [bitmap].
     */
    /**
     * Outcome of a save, rather than a nullable URL.
     *
     * The old signature returned null for every kind of failure, so a
     * permissions problem and a dropped connection produced the same
     * "check your connection" message — which is what kept the real cause
     * hidden. The caller can now say something true.
     */
    sealed interface Result {
        data class Success(val downloadUrl: String) : Result
        data object NotSignedIn : Result
        data class Failed(val reason: String, val cause: Exception?) : Result
    }

    suspend fun saveCapture(
        bitmap: Bitmap,
        siteId: String,
        visitId: String = ""
    ): Result = withContext(Dispatchers.IO) {

        val user = FirebaseAuth.getInstance().currentUser
            ?: return@withContext Result.NotSignedIn

        return@withContext try {

            val bytes = ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
                stream.toByteArray()
            }

            val fileName = "${System.currentTimeMillis()}.jpg"

            // gallery-images, NOT gallery.
            //
            // The Storage rules grant a signed-in user write access to
            // gallery-images/{their uid}/**. Nothing grants access to
            // gallery/, so every upload here was landing on the deny-all
            // rule at the bottom of the ruleset and failing with
            // storage/unauthorized — the capture button appeared to do
            // nothing at all.
            //
            // The Firestore collection is separately named "gallery" and is
            // correct; only the bucket path was wrong.
            val photoRef = storage.reference
                .child("gallery-images")
                .child(user.uid)
                .child(fileName)

            photoRef.putBytes(bytes).await()

            val downloadUrl = photoRef.downloadUrl.await().toString()

            db.collection("gallery")
                .add(
                    hashMapOf(
                        "siteId" to siteId,
                        "sceneId" to "",
                        "visitId" to visitId,
                        "userId" to user.uid,
                        "uploadedBy" to (user.displayName ?: ""),
                        "imageUrl" to downloadUrl,
                        "capturedAt" to Timestamp.now(),
                        "status" to "active",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()

            Result.Success(downloadUrl)

        } catch (e: StorageException) {
            Log.e("HistouryCapture", "Storage upload failed", e)
            Result.Failed(
                reason = if (e.errorCode == StorageException.ERROR_NOT_AUTHORIZED) {
                    "This account isn't allowed to save photos."
                } else {
                    "Couldn't upload that photo."
                },
                cause = e
            )

        } catch (e: Exception) {
            Log.e("HistouryCapture", "Capture save failed", e)
            Result.Failed("Couldn't save that photo.", e)
        }
    }

    private companion object {

        // Screen-resolution AR captures compress well; 85 keeps text in the
        // overlay legible without pushing typical files past ~400KB.
        const val JPEG_QUALITY = 85
    }
}
