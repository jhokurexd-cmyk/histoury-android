package com.histoury.app.data.repository

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
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

    /**
     * A login with no users/{uid} profile. Profiles are only created by the
     * registerWithOtp Cloud Function, after the emailed code is verified, so
     * this is a login made by calling Firebase Auth directly (skipping email
     * verification) or a sign-up that never finished. It isn't a usable
     * account: the app signs it out. Registering again with the same email
     * completes it.
     */
    object NoProfile : AccountStatus()

    data class Suspended(
        val uid: String,
        val reason: String,
        val note: String?,
        val expiresAtMillis: Long?,
        // The session's Firebase ID token, captured just before the app
        // signs the suspended user out. submitSuspensionAppeal verifies it
        // to know who is appealing (a bare uid could be anyone's). Valid for
        // up to an hour; null if it couldn't be read.
        val appealToken: String? = null
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

            // A confirmed "no such document" from the server — not a failed
            // read, which lands in the catch below and fails open.
            if (!snap.exists()) return AccountStatus.NoProfile

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

                // A read error says nothing about the account (offline,
                // a transient failure): keep the current state.
                if (error != null || snap == null) {
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

                // The server says the profile is gone: an admin deleted the
                // account. It used to count as Active, leaving the person
                // signed in until their token expired.
                if (!snap.exists()) {
                    trySend(AccountStatus.NoProfile)
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

    /**
     * Creates the account through the registerWithOtp Cloud Function — which
     * checks the emailed [code] and creates the login AND the profile
     * together, all-or-nothing — then signs in.
     *
     * The app used to create the login itself (createUserWithEmailAndPassword)
     * and then write the profile. That let anyone skip email verification by
     * calling Firebase directly, and a connection drop between the two steps
     * left a login with no profile whose email then counted as "already
     * registered", so the visitor could never retry.
     */
    suspend fun register(
        firstName: String,
        lastName: String,
        email: String,
        password: String,
        code: String
    ): Result<FirebaseUser> {

        val created = EmailOtpRepository().registerWithOtp(
            firstName = firstName,
            lastName = lastName,
            email = email,
            password = password,
            code = code
        )

        if (created.isFailure) {
            return Result.failure(created.exceptionOrNull() ?: Exception("Registration failed. Please try again."))
        }

        // The account exists now; if this sign-in fails (e.g. the signal
        // dropped), the visitor can simply log in.
        return login(email, password).fold(
            onSuccess = { Result.success(it) },
            onFailure = {
                Result.failure(Exception("Your account was created. Please log in to continue."))
            }
        )
    }

    /**
     * The signed-in user's ID token for a suspension appeal, or null. Call
     * it BEFORE signing a suspended user out. Uses the cached token when it
     * is still valid; refreshing one fails once the suspension has revoked
     * the session, and then null means "log in again to appeal".
     */
    suspend fun appealTokenOrNull(): String? = try {
        kotlinx.coroutines.withTimeoutOrNull(3_000L) {
            firebaseAuth.currentUser?.getIdToken(false)?.await()?.token
        }
    } catch (_: Exception) {
        null
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
