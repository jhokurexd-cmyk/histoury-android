package com.histoury.app.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * The app's form rules — ONE definition used for both the live message under
 * a field and the final check when the form is submitted, so the two can
 * never disagree. Each function returns the single most relevant message, or
 * null when the value is fine.
 *
 * Limits match the server:
 * - password: Firebase Authentication / otp.js MIN_PASSWORD_LENGTH = 6
 * - email: the same pattern otp.js uses (and Android's EMAIL_ADDRESS)
 * - review text / report details: reviewModeration.js (500 characters)
 */
object Validators {

    const val NAME_MIN = 2
    const val NAME_MAX = 50
    const val PASSWORD_MIN = 6
    const val PASSWORD_MAX = 128
    const val OTP_LENGTH = 6
    const val REVIEW_MAX = 500
    const val REPORT_DETAILS_MAX = 500

    /**
     * Letters in any script (with their combining accent marks, as the
     * server's name rule allows), plus space, hyphen and straight/curly
     * apostrophe.
     */
    fun isAllowedNameChar(c: Char): Boolean =
        c.isLetter() || isCombiningMark(c) || c.isWhitespace() || c == '-' || c == '\'' || c == '’'

    private fun isCombiningMark(c: Char): Boolean = when (Character.getType(c).toByte()) {
        Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK -> true
        else -> false
    }

    fun name(value: String, label: String): String? {
        val text = value.trim()
        return when {
            text.isEmpty() -> "$label is required."
            text.length < NAME_MIN -> "$label must be at least $NAME_MIN characters."
            text.length > NAME_MAX -> "$label must be $NAME_MAX characters or fewer."
            !text.all(::isAllowedNameChar) -> "$label can only contain letters, spaces, hyphens and apostrophes."
            text.none { it.isLetter() } -> "$label must contain letters."
            else -> null
        }
    }

    fun email(value: String): String? = when {
        value.isBlank() -> "Email is required."
        !value.isValidEmail() -> "Enter a valid email address."
        else -> null
    }

    /** Login only needs something typed; the rules are checked by the server. */
    fun loginPassword(value: String): String? =
        if (value.isEmpty()) "Password is required." else null

    fun newPassword(value: String): String? = when {
        value.isEmpty() -> "Password is required."
        value.length < PASSWORD_MIN -> "Password must be at least $PASSWORD_MIN characters."
        value.length > PASSWORD_MAX -> "Password must be $PASSWORD_MAX characters or fewer."
        else -> null
    }

    fun confirmPassword(password: String, confirm: String): String? = when {
        confirm.isEmpty() -> "Confirm your password."
        confirm != password -> "Passwords do not match."
        else -> null
    }

    fun otp(value: String): String? = when {
        value.isEmpty() -> "Enter the $OTP_LENGTH-digit code."
        value.length != OTP_LENGTH || !value.all(Char::isDigit) -> "The code must be $OTP_LENGTH digits."
        else -> null
    }

    /** General text: optional or required, with min/max once trimmed. */
    fun text(
        value: String,
        label: String,
        min: Int = 0,
        max: Int = Int.MAX_VALUE,
        required: Boolean = true
    ): String? {
        val text = value.trim()
        return when {
            text.isEmpty() -> if (required) "$label is required." else null
            text.length < min -> "$label must be at least $min characters."
            text.length > max -> "$label must be $max characters or fewer."
            else -> null
        }
    }

    /** One line of the live password checklist. */
    data class Requirement(val label: String, val met: Boolean, val required: Boolean = true)

    fun passwordRequirements(password: String): List<Requirement> = listOf(
        Requirement("At least $PASSWORD_MIN characters", password.length >= PASSWORD_MIN),
        // Advice, not a rule — shown so people pick something stronger, but
        // it never blocks the form (the server doesn't require it either).
        Requirement(
            "Mix of letters and numbers (recommended)",
            password.any(Char::isLetter) && password.any(Char::isDigit),
            required = false
        )
    )
}

/**
 * Which fields of one form the user has interacted with, and whether they've
 * tried to submit it. Errors are computed from the current values every
 * time; this only decides WHEN a field's error may be shown:
 *
 * - not before the user has typed into it or left it (no "required" errors
 *   on a form they haven't started), then
 * - live, as the value changes, and
 * - for every field once submit has been attempted.
 */
@Stable
class FormState {

    private val touched = mutableStateMapOf<String, Boolean>()

    var submitted by mutableStateOf(false)
        private set

    /** Call from onValueChange and when the field loses focus. */
    fun touch(field: String) {
        if (touched[field] != true) touched[field] = true
    }

    fun isTouched(field: String): Boolean = touched[field] == true

    /** The error to display for [field], or null if it shouldn't show yet. */
    fun shown(field: String, error: String?): String? =
        if (error != null && (submitted || isTouched(field))) error else null

    /**
     * The final check on submit: reveals every remaining error and returns
     * true only when there are none.
     */
    fun submit(vararg errors: String?): Boolean {
        submitted = true
        return errors.all { it == null }
    }

    fun reset() {
        touched.clear()
        submitted = false
    }
}

@Composable
fun rememberFormState(): FormState = remember { FormState() }

/** Result of a debounced server-side check (e.g. "email already registered"). */
enum class AsyncCheck { IDLE, CHECKING, AVAILABLE, TAKEN, FAILED }
