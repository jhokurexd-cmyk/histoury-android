package com.histoury.app.data.repository

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await

/**
 * Client for the resetPasswordWithOtp Cloud Function.
 *
 * This is a single combined call (code + new password together) rather
 * than a separate verify-then-reset pair — there's no signed-in session in
 * a "forgot password" flow to carry a "verified" state between two calls.
 */
class PasswordResetRepository {

    private val functions = FirebaseFunctions.getInstance("asia-southeast1")

    suspend fun resetPassword(email: String, code: String, newPassword: String): Result<Unit> {

        return try {

            functions.getHttpsCallable("resetPasswordWithOtp")
                .call(
                    hashMapOf(
                        "email" to email.trim(),
                        "code" to code.trim(),
                        "newPassword" to newPassword
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
            // message (e.g. "No account found with that email address"),
            // and that should be shown as-is, not hidden.
            val serverMessage = e.message
                ?.takeIf { it.isNotBlank() && it.lowercase() != "internal" }

            if (serverMessage != null) return serverMessage

            return when (e.code) {

                FirebaseFunctionsException.Code.DEADLINE_EXCEEDED ->
                    "The request timed out. Please try again."

                else ->
                    "The password reset service isn't available right now. Please try again later."
            }
        }

        return "Couldn't reach the password reset service. Check your connection and try again."
    }
}
