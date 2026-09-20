package com.histoury.app.data.repository

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await

/**
 * Client for the submitSuspensionAppeal Cloud Function.
 *
 * Called with no Firebase Auth session — the app signs a suspended user
 * out immediately upon detecting the suspension, so this intentionally
 * doesn't rely on request.auth. The Cloud Function re-verifies the given
 * uid is genuinely suspended (via the Admin SDK) before accepting it.
 */
class AppealRepository {

    // Same region as the other custom Cloud Functions (sendEmailOtp, etc.)
    private val functions = FirebaseFunctions.getInstance("asia-southeast1")

    suspend fun submitAppeal(uid: String, message: String): Result<Unit> {

        return try {

            functions.getHttpsCallable("submitSuspensionAppeal")
                .call(
                    hashMapOf(
                        "uid" to uid,
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
