package com.histoury.app.util

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException

/**
 * Turns raw Firebase exceptions into messages a person can act on.
 *
 * Firebase's own messages leak implementation details ("The supplied auth
 * credential is incorrect, malformed or has expired", raw error codes,
 * etc.) — nothing raw should ever reach the UI.
 */
object FirebaseErrorMessages {

    fun friendly(e: Throwable?): String {

        return when (e) {

            // Wrong password / malformed credential / bad email format.
            // Firebase deliberately doesn't distinguish wrong-password from
            // no-such-account anymore (enumeration protection), so neither
            // should the message.
            is FirebaseAuthInvalidCredentialsException,
            is FirebaseAuthInvalidUserException ->
                "Incorrect email or password. Please try again."

            is FirebaseAuthUserCollisionException ->
                "An account with this email already exists. Try logging in instead."

            is FirebaseAuthWeakPasswordException ->
                "Password is too weak. Use at least 6 characters."

            is FirebaseTooManyRequestsException ->
                "Too many attempts. Please wait a few minutes and try again."

            is FirebaseNetworkException ->
                "No internet connection. Check your network and try again."

            is FirebaseAuthException ->
                // Any other auth error: map the code, never show it raw.
                when (e.errorCode) {
                    "ERROR_USER_DISABLED" ->
                        "This account has been disabled. Contact support for help."
                    "ERROR_OPERATION_NOT_ALLOWED" ->
                        "Sign-in is temporarily unavailable. Please try again later."
                    else ->
                        "Something went wrong signing you in. Please try again."
                }

            is FirebaseFirestoreException ->
                when (e.code) {
                    FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                        "You don't have permission to do that."
                    FirebaseFirestoreException.Code.UNAVAILABLE ->
                        "Couldn't reach the server. Check your connection and try again."
                    else ->
                        "Something went wrong saving your data. Please try again."
                }

            else ->
                "Something went wrong. Please check your connection and try again."
        }
    }
}
