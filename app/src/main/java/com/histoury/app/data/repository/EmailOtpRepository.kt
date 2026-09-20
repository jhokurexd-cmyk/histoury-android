package com.histoury.app.data.repository

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await

/**
 * Client for the sendEmailOtp / verifyEmailOtp Cloud Functions.
 *
 * Purposes:
 *  - [PURPOSE_REGISTER]     — verify an email before creating an account
 *  - [PURPOSE_CHANGE_EMAIL] — verify a NEW email; the function performs the
 *    actual email change server-side on success (client-side updateEmail()
 *    is blocked on modern Firebase projects)
 *  - [PURPOSE_PASSWORD_RESET] — Forgot Password: the code goes to an EXISTING
 *    account's address; resetPasswordWithOtp then checks it and sets the
 *    new password (see PasswordResetRepository)
 */
class EmailOtpRepository {

    // The OTP functions live in asia-southeast1 (unlike getWalkingDirections,
    // which is legacy us-central1), so this needs the explicit region.
    private val functions = FirebaseFunctions.getInstance("asia-southeast1")

    suspend fun sendOtp(email: String, purpose: String): Result<Unit> {

        return try {

            functions.getHttpsCallable("sendEmailOtp")
                .call(
                    hashMapOf(
                        "email" to email.trim(),
                        "purpose" to purpose
                    )
                )
                .await()

            Result.success(Unit)

        } catch (e: Exception) {
            Result.failure(Exception(friendlyMessage(e)))
        }
    }

    suspend fun verifyOtp(email: String, code: String, purpose: String): Result<Unit> {

        return try {

            functions.getHttpsCallable("verifyEmailOtp")
                .call(
                    hashMapOf(
                        "email" to email.trim(),
                        "code" to code.trim(),
                        "purpose" to purpose
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

            // Our Cloud Functions throw HttpsError with a specific, useful
            // message for every expected case (including NOT_FOUND ones
            // like "No account found with that email address" — that's
            // legitimate business logic, not a broken deployment). Only
            // fall back to the generic dev-facing message when there's no
            // useful text at all, which happens for genuine crashes/wrong
            // region/not-deployed cases where Functions returns a bare
            // "internal" with nothing else attached.
            val serverMessage = e.message
                ?.takeIf { it.isNotBlank() && it.lowercase() != "internal" }

            if (serverMessage != null) return serverMessage

            return when (e.code) {

                FirebaseFunctionsException.Code.DEADLINE_EXCEEDED ->
                    "The verification service timed out. Please try again."

                else ->
                    "The email verification service isn't available. " +
                        "If you're the developer: deploy the sendEmailOtp/verifyEmailOtp " +
                        "Cloud Functions (see OTP_SETUP_GUIDE) and check " +
                        "`firebase functions:log` for crashes."
            }
        }

        return "Couldn't reach the verification service. Check your connection and try again."
    }

    companion object {
        const val PURPOSE_REGISTER = "register"
        const val PURPOSE_CHANGE_EMAIL = "change_email"
        // Must match PURPOSE_RESET_PASSWORD in functions/otp.js exactly.
        // (Was "password_reset", which the backend rejected as an unknown
        // purpose. The backend still accepts that old value from installed
        // builds, but new builds send the canonical name.)
        const val PURPOSE_PASSWORD_RESET = "reset_password"
    }
}
