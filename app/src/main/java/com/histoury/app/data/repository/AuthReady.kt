package com.histoury.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * The signed-in user, waiting briefly for Firebase Auth to restore a saved
 * session on a cold start (right after an app update or restart it can
 * report "nobody signed in" for a moment). Returns null only when no one is
 * signed in once that wait is over.
 */
internal suspend fun awaitRestoredUser(timeoutMs: Long = 5_000L): FirebaseUser? {
    val auth = FirebaseAuth.getInstance()
    auth.currentUser?.let { return it }
    return withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { continuation ->
            val listener = object : FirebaseAuth.AuthStateListener {
                override fun onAuthStateChanged(firebaseAuth: FirebaseAuth) {
                    val current = firebaseAuth.currentUser ?: return
                    firebaseAuth.removeAuthStateListener(this)
                    if (continuation.isActive) continuation.resume(current)
                }
            }
            auth.addAuthStateListener(listener)
            continuation.invokeOnCancellation { auth.removeAuthStateListener(listener) }
        }
    }
}
