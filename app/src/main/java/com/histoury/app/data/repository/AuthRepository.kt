package com.histoury.app.data.repository

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.histoury.app.util.FirebaseErrorMessages
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Result of checking a user's `status` (and, if suspended, `suspension`)
 * fields on their `users/{uid}` document.
 *
 * [Suspended.expiresAtMillis] is null for an indefinite suspension.
 * Both [AuthRepository.checkAccountStatus] and [AuthRepository.observeAccountStatus] apply "lazy expiry":
 * if [Suspended.expiresAtMillis] has already passed but the scheduled Cloud Function
 * (autoReactivateSuspendedUsers) hasn't flipped `status` back to "active"
 * yet, the account is still treated as [Active] here.
 */
sealed class AccountStatus {
    object Active : AccountStatus()
    data class Suspended(
        val uid: String,
        val reason: String,
        val note: String?,
        val expiresAtMillis: Long?
    ) : AccountStatus()
}

class AuthRepository {

    private val firebaseAuth = FirebaseAuth.getInstance()

    private val db = FirebaseFirestore.getInstance()

    fun getCurrentUser(): FirebaseUser? = firebaseAuth.currentUser

    /**
     * Emits the current user's uid (or null when signed out) every time
     * Firebase's own auth state changes. Used by MainActivity to know when
     * to start/stop the live suspension listener.
     */
    fun authStateUidFlow(): Flow<String?> = callbackFlow {

        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser?.uid)
        }

        firebaseAuth.addAuthStateListener(listener)

        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    /**
     * One-shot check, used right after login and on the splash screen for
     * an already-persisted session.
     */
    suspend fun checkAccountStatus(uid: String): AccountStatus {

        return try {

            // Source.SERVER, not the default cache-or-server behavior:
            // this check runs at the exact moment MainActivity's live
            // listener (observeAccountStatus) is also attaching to this
            // same document, and Firestore's Android SDK can otherwise
            // satisfy a get() from local cache before that listener's
            // first server snapshot arrives — silently returning a stale
            // "active" status for an account that was suspended moments
            // ago. This is a security-relevant check, so staleness here
            // isn't acceptable; always confirm against the server.
            val snap = db.collection("users").document(uid)
                .get(com.google.firebase.firestore.Source.SERVER)
                .await()

            toAccountStatus(uid, snap.getString("status"), snap.get("suspension") as? Map<*, *>)

        } catch (_: Exception) {
            // Network hiccup or similar: fail open rather than locking a
            // legitimate user out over a transient read error. The
            // real-time listener + rules still protect writes either way.
            AccountStatus.Active
        }
    }

    /**
     * Live listener for the current session: if an admin suspends this
     * account while the app is open, the emitted [AccountStatus.Suspended]
     * lets the caller force a sign-out immediately instead of waiting for
     * the next app restart.
     */
    fun observeAccountStatus(uid: String): Flow<AccountStatus> = callbackFlow {

        val registration = db.collection("users").document(uid)
            .addSnapshotListener { snap, error ->

                if (error != null || snap == null || !snap.exists()) {
                    trySend(AccountStatus.Active)
                    return@addSnapshotListener
                }

                // Snapshot listeners deliver a CACHED snapshot first, then
                // the server one. A stale cached "suspended" (from before an
                // admin unsuspended this account) must not force-logout a
                // legitimately reactivated user — suspension is drastic
                // enough that we only ever act on server-confirmed data.
                if (snap.metadata.isFromCache) {
                    return@addSnapshotListener
                }

                trySend(toAccountStatus(uid, snap.getString("status"), snap.get("suspension") as? Map<*, *>))
            }

        awaitClose { registration.remove() }
    }

    private fun toAccountStatus(uid: String, status: String?, suspension: Map<*, *>?): AccountStatus {

        if (status != "suspended") return AccountStatus.Active

        val expiresAtMillis = (suspension?.get("expiresAt") as? com.google.firebase.Timestamp)
            ?.toDate()?.time

        if (expiresAtMillis != null && expiresAtMillis <= System.currentTimeMillis()) {
            // Duration passed but the hourly Cloud Function hasn't caught
            // up yet — don't block someone who should already be back in.
            return AccountStatus.Active
        }

        return AccountStatus.Suspended(
            uid = uid,
            reason = suspension?.get("reason") as? String ?: "other",
            note = suspension?.get("note") as? String,
            expiresAtMillis = expiresAtMillis
        )
    }

    suspend fun login(email: String, password: String): Result<FirebaseUser> {

        return try {

            val result = firebaseAuth
                .signInWithEmailAndPassword(email, password)
                .await()

            val user = result.user
                ?: return Result.failure(Exception("Login failed. Please try again."))

            Result.success(user)

        } catch (e: Exception) {
            Result.failure(Exception(FirebaseErrorMessages.friendly(e)))
        }
    }

    suspend fun register(
        firstName: String,
        lastName: String,
        email: String,
        password: String
    ): Result<FirebaseUser> {

        return try {

            val result = firebaseAuth
                .createUserWithEmailAndPassword(email, password)
                .await()

            val user = result.user
                ?: return Result.failure(Exception("Registration failed. Please try again."))

            // Sync the name to the Firebase Auth profile so it's available
            // locally (via currentUser.displayName) on future app restarts.
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName("$firstName $lastName".trim())
                .build()
            user.updateProfile(profileUpdates).await()

            val userDoc = hashMapOf(
                "uid" to user.uid,
                "firstName" to firstName,
                "lastName" to lastName,
                "email" to email,
                "role" to "tourist",
                "profileImage" to "",
                "status" to "active",
                "createdAt" to com.google.firebase.Timestamp.now(),
                "updatedAt" to com.google.firebase.Timestamp.now()
            )

            db.collection("users").document(user.uid).set(userDoc).await()

            Result.success(user)

        } catch (e: Exception) {
            Result.failure(Exception(FirebaseErrorMessages.friendly(e)))
        }
    }

    fun logout() {
        firebaseAuth.signOut()
    }

    suspend fun reauthenticate(currentPassword: String): Result<Unit> {

        val user = firebaseAuth.currentUser
            ?: return Result.failure(Exception("You're not signed in."))

        val email = user.email
            ?: return Result.failure(Exception("No email on this account."))

        return try {

            val credential = EmailAuthProvider.getCredential(email, currentPassword)

            user.reauthenticate(credential).await()

            Result.success(Unit)

        } catch (e: Exception) {
            Result.failure(Exception(FirebaseErrorMessages.friendly(e)))
        }
    }
}
