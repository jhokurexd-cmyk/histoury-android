package com.histoury.app.data.repository

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await

/**
 * Client for the submitSuspensionAppeal Cloud Function.
 *
 * Called with no Firebase Auth session — the app signs a suspended user
 * out immediately upon detecting the suspension. Instead it sends the ID
 * token captured just before that sign-out (AccountStatus.Suspended
 * .appealToken); the Cloud Function verifies it, so nobody can file an
 * appeal in someone else's name, then re-checks the suspension itself.
 */
class AppealRepository {

    // Same region as the other custom Cloud Functions (sendEmailOtp, etc.)
    private val functions = FirebaseFunctions.getInstance("asia-southeast1")

    suspend fun submitAppeal(appealToken: String, message: String): Result<Unit> {

        return try {

            functions.getHttpsCallable("submitSuspensionAppeal")
                .call(
                    hashMapOf(
                        "idToken" to appealToken,
                        "message" to message.trim()
                    )
                )
                .await()

            Result.success(Unit)

        } catch (e: Exception) {
            Result.failure(Exception(friendlyMessage(e)))
        }
    }

    private fun friendlyMessage(e: Exception): String {

        if (e is FirebaseFunctionsException) {

            // See EmailOtpRepository's version of this function for why
            // NOT_FOUND isn't lumped in with "service unavailable" — our
            // Cloud Function legitimately throws NOT_FOUND with a real
            // message, and that should be shown as-is, not hidden.
            val serverMessage = e.message
                ?.takeIf { it.isNotBlank() && it.lowercase() != "internal" }

            if (serverMessage != null) return serverMessage

            return when (e.code) {

                FirebaseFunctionsException.Code.DEADLINE_EXCEEDED ->
                    "The request timed out. Please try again."

                else ->
                    "The appeal service isn't available right now. Please try again later."
            }
        }

        return "Couldn't reach the appeal service. Check your connection and try again."
    }
}
