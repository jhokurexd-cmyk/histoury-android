package com.histoury.app.data.repository

import android.content.Context

/**
 * Remembers that this device has already been through onboarding.
 *
 * Before this, AuthGate sent everyone who wasn't signed in to Onboarding —
 * so a returning visitor who had simply logged out had to swipe through the
 * three intro pages again on every cold launch before reaching Login.
 */
object OnboardingPreference {

    private const val PREFS_NAME = "histoury_onboarding"
    private const val KEY_SEEN = "seen"

    fun hasSeen(context: Context): Boolean =
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SEEN, false)

    fun markSeen(context: Context) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SEEN, true)
            .apply()
    }
}
