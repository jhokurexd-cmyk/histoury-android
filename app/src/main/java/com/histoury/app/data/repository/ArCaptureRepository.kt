package com.histoury.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.auth.FirebaseAuth

/**
 * Saves photos taken inside the AR experience.
 *
 * Captures are stored ON THE PHONE (see LocalGalleryStore), not uploaded.
 * They used to go to Firebase Storage with a Firestore `gallery` record for
 * each one — which, with many visitors taking several photos per site, keeps
 * growing the cloud bucket for pictures only their owner ever sees. Saving
 * locally is also instant and works with no signal, which is common inside
 * Intramuros.
 *
 * The account is still required: photos are filed per signed-in user, so
 * two people sharing a phone each see only their own.
 */
class ArCaptureRepository {

    /**
     * Outcome of a save, rather than a nullable result, so the caller can say
     * something true about what went wrong.
     */
    sealed interface Result {
        /** [imageUri] is the file:// URI of the saved photo. */
        data class Success(val imageUri: String) : Result
        data object NotSignedIn : Result
        data class Failed(val reason: String, val cause: Exception?) : Result
    }

    suspend fun saveCapture(
        context: Context,
        bitmap: Bitmap,
        siteId: String,
        visitId: String = ""
    ): Result {

        val user = FirebaseAuth.getInstance().currentUser
            ?: return Result.NotSignedIn

        return try {
            val saved = LocalGalleryStore.save(
                context = context,
                uid = user.uid,
                bitmap = bitmap,
                siteId = siteId,
                visitId = visitId
            )
            Result.Success(saved.imageUrl)
        } catch (e: java.io.IOException) {
            Log.e("HistouryCapture", "Couldn't write capture", e)
            Result.Failed("Couldn't save that photo. Your phone may be out of storage.", e)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("HistouryCapture", "Capture save failed", e)
            Result.Failed("Couldn't save that photo.", e)
        }
    }
}
