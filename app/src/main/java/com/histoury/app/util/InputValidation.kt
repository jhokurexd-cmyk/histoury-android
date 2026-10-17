package com.histoury.app.util

import android.util.Patterns

/**
 * One definition of "looks like an email" for every form in the app.
 *
 * Login, Register, Forgot Password and Change Email each used to do their
 * own `contains("@")` check, which let through things like "juan@" or
 * "@gmail.com" and only failed later, server-side, with a vaguer message.
 */
fun String.isValidEmail(): Boolean {
    val trimmed = trim()
    return trimmed.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(trimmed).matches()
}
