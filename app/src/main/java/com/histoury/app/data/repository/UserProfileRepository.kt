package com.histoury.app.data.repository

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.histoury.app.util.FirebaseErrorMessages
import kotlinx.coroutines.tasks.await

sealed class ProfileUpdateResult {
    object Success : ProfileUpdateResult()
    object RequiresReauthentication : ProfileUpdateResult()
    data class Failure(val message: String) : ProfileUpdateResult()
}

class UserProfileRepository {

    private val firebaseAuth = FirebaseAuth.getInstance()

    private val db = FirebaseFirestore.getInstance()

    private val storage = FirebaseStorage.getInstance()

    suspend fun updateDisplayName(firstName: String, lastName: String): ProfileUpdateResult {

        val user = firebaseAuth.currentUser
            ?: return ProfileUpdateResult.Failure("You're not signed in.")

        return try {

            val docRef = db.collection("users").document(user.uid)

            val snapshot = docRef.get().await()

            val currentFirst = snapshot.getString("firstName") ?: ""
            val currentLast = snapshot.getString("lastName") ?: ""

            val newFullName = "$firstName $lastName".trim()

            // No-op "changes" (same name resubmitted) don't consume the
            // cooldown — only actual name changes do.
            if (currentFirst == firstName && currentLast == lastName) {
                return ProfileUpdateResult.Success
            }

            val lastChange = snapshot.getTimestamp("lastNameChangeAt")

            if (lastChange != null) {

                val elapsedMs = System.currentTimeMillis() - lastChange.toDate().time

                if (elapsedMs < NAME_CHANGE_COOLDOWN_MS) {

                    val daysLeft = ((NAME_CHANGE_COOLDOWN_MS - elapsedMs) / DAY_MS)
                        .toInt()
                        .coerceAtLeast(0) + 1

                    return ProfileUpdateResult.Failure(
                        "You can only change your name once every 30 days. " +
                            "Try again in $daysLeft day${if (daysLeft == 1) "" else "s"}."
                    )
                }
            }

            // Update Firestore
            docRef.update(
                mapOf(
                    "firstName" to firstName,
                    "lastName" to lastName,
                    "lastNameChangeAt" to com.google.firebase.Timestamp.now(),
                    "updatedAt" to com.google.firebase.Timestamp.now()
                )
            ).await()

            // Sync to Firebase Auth profile so it's available immediately on app restarts.
            syncAuthProfile(newFullName, user.photoUrl?.toString())

            ProfileUpdateResult.Success

        } catch (e: Exception) {
            ProfileUpdateResult.Failure(FirebaseErrorMessages.friendly(e))
        }
    }

    /**
     * Updates the local Firebase Auth profile (displayName and photoUrl).
     * This data is persisted in the local session, making it available
     * immediately on app restarts even before Firestore is reached.
     */
    suspend fun syncAuthProfile(displayName: String?, photoUrl: String?) {
        val user = firebaseAuth.currentUser ?: return
        val profileUpdates = UserProfileChangeRequest.Builder()
            .setDisplayName(displayName)
            .setPhotoUri(photoUrl?.let { Uri.parse(it) })
            .build()
        user.updateProfile(profileUpdates).await()
    }

    companion object {
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val NAME_CHANGE_COOLDOWN_MS = 30L * DAY_MS
    }

    /**
     * Verifies the current password by re-authenticating, without changing
     * anything. Used before an email change so the session can be silently
     * re-established (with the new email + this password) afterwards.
     */
    suspend fun verifyCurrentPassword(currentPassword: String): ProfileUpdateResult {

        val user = firebaseAuth.currentUser
            ?: return ProfileUpdateResult.Failure("You're not signed in.")

        val email = user.email
            ?: return ProfileUpdateResult.Failure("Your account has no email set.")

        return try {

            val credential = com.google.firebase.auth.EmailAuthProvider
                .getCredential(email, currentPassword)

            user.reauthenticate(credential).await()

            ProfileUpdateResult.Success

        } catch (e: Exception) {
            ProfileUpdateResult.Failure("Current password is incorrect.")
        }
    }

    /**
     * Changes the password after verifying the current one.
     *
     * Re-authenticating with the current password both confirms the user's
     * identity and satisfies Firebase's recent-login requirement, so the
     * separate reauth-dialog dance is no longer needed for passwords.
     */
    suspend fun changePassword(
        currentPassword: String,
        newPassword: String
    ): ProfileUpdateResult {

        val user = firebaseAuth.currentUser
            ?: return ProfileUpdateResult.Failure("You're not signed in.")

        val email = user.email
            ?: return ProfileUpdateResult.Failure("Your account has no email set.")

        // Step 1: prove the current password is right.
        try {

            val credential = com.google.firebase.auth.EmailAuthProvider
                .getCredential(email, currentPassword)

            user.reauthenticate(credential).await()

        } catch (e: Exception) {
            return ProfileUpdateResult.Failure("Current password is incorrect.")
        }

        // Step 2: set the new one.
        return try {

            user.updatePassword(newPassword).await()

            ProfileUpdateResult.Success

        } catch (e: Exception) {
            ProfileUpdateResult.Failure(FirebaseErrorMessages.friendly(e))
        }
    }

    suspend fun uploadProfilePhoto(localImageUri: Uri): ProfileUpdateResult {

        val user = firebaseAuth.currentUser
            ?: return ProfileUpdateResult.Failure("You're not signed in.")

        return try {

            val photoRef = storage.reference
                .child("profile-images/${user.uid}/profile.jpg")

            photoRef.putFile(localImageUri).await()

            val downloadUrl = photoRef.downloadUrl.await()

            // Update Auth profile
            syncAuthProfile(user.displayName, downloadUrl.toString())

            // Update Firestore
            db.collection("users").document(user.uid)
                .update(
                    mapOf(
                        "profileImage" to downloadUrl.toString(),
                        "updatedAt" to com.google.firebase.Timestamp.now()
                    )
                )
                .await()

            ProfileUpdateResult.Success

        } catch (e: Exception) {
            ProfileUpdateResult.Failure(FirebaseErrorMessages.friendly(e))
        }
    }
}
