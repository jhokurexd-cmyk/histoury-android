package com.histoury.app.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.histoury.app.data.model.Gallery
import kotlinx.coroutines.tasks.await

class GalleryRepository {

    private val db = FirebaseFirestore.getInstance()

    private val storage = FirebaseStorage.getInstance()

    /**
     * The signed-in user's captured photos, newest first.
     *
     * New captures live on the phone (LocalGalleryStore). Photos taken with
     * earlier versions were uploaded to Firebase Storage; those are still
     * listed (and can still be deleted) so nobody loses what they already
     * have, but nothing new is uploaded any more. If the cloud list can't be
     * loaded (e.g. offline) the local photos are still shown.
     *
     * Sorting happens client-side (same approach as VisitHistoryRepository)
     * so no composite Firestore index is needed for userId + capturedAt.
     *
     * Returns an empty list if no one is signed in.
     */
    suspend fun getGalleryForCurrentUser(context: Context): List<Gallery> {

        val userId = FirebaseAuth.getInstance().currentUser?.uid
            ?: return emptyList()

        val local = try {
            LocalGalleryStore.list(context, userId)
        } catch (e: Exception) {
            Log.e("HistouryGallery", "Couldn't read local gallery", e)
            emptyList()
        }

        val cloud = try {

            val snapshot = db.collection("gallery")
                .whereEqualTo("userId", userId)
                .get()
                .await()

            snapshot.documents
                .mapNotNull { document ->
                    document.toObject(Gallery::class.java)
                        ?.copy(id = document.id)
                }

        } catch (e: Exception) {
            emptyList()
        }

        return (local + cloud).sortedByDescending { entry ->
            (entry.capturedAt as? Timestamp)?.toDate()?.time ?: 0L
        }
    }

    /**
     * Permanently removes [photos] — both the Firestore record and the file
     * behind it.
     *
     * Deleting the document alone would leave the JPEG in the bucket
     * forever: nothing else references it, nothing would ever list it, and
     * it would quietly accumulate storage cost for a photo its owner
     * believes they deleted. So the object goes too, and it goes FIRST:
     * if the record were removed first and the file delete then failed, the
     * app would have lost its only pointer to the orphan.
     *
     * A file that is already gone is not an error. A capture whose upload
     * half-failed, or one deleted from the console, still has a document
     * that its owner is entitled to get rid of, and refusing to clear the
     * record because the file it names is missing would strand it in the
     * grid permanently.
     *
     * Returns the ids actually deleted, so the caller can reconcile its list
     * rather than assuming the whole batch went through.
     */
    suspend fun deletePhotos(context: Context, photos: List<Gallery>): List<String> {

        val userId = FirebaseAuth.getInstance().currentUser?.uid
            ?: return emptyList()

        val deleted = mutableListOf<String>()

        // Photos stored on the phone: just the file and its index entry.
        val (local, cloud) = photos
            .filter { it.userId == userId && it.id.isNotBlank() }
            .partition { LocalGalleryStore.isLocal(it.imageUrl) }

        if (local.isNotEmpty()) {
            try {
                deleted += LocalGalleryStore.delete(context, userId, local.map { it.id }.toSet())
            } catch (e: Exception) {
                Log.e("HistouryGallery", "Couldn't delete local photos", e)
            }
        }

        // Older photos that were uploaded to the cloud: file and record.
        for (photo in cloud) {

            // Belt and braces alongside the Firestore rules: this repository
            // only ever lists the signed-in user's own captures, so a photo
            // owned by somebody else should be unreachable from here — but a
            // delete is not the operation to find that out the hard way.
            if (photo.userId != userId || photo.id.isBlank()) continue

            if (photo.imageUrl.isNotBlank()) {
                try {
                    storage.getReferenceFromUrl(photo.imageUrl).delete().await()
                } catch (e: Exception) {
                    // Already gone, or never arrived. Carry on to the record.
                    Log.w("HistouryGallery", "Storage object not removed for ${photo.id}", e)
                }
            }

            try {
                db.collection("gallery").document(photo.id).delete().await()
                deleted += photo.id
            } catch (e: Exception) {
                Log.e("HistouryGallery", "Could not delete gallery record ${photo.id}", e)
            }
        }

        return deleted
    }
}
