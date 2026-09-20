package com.histoury.app.data.repository

import android.content.Context

/**
 * Remembers that the visitor has already been shown the "Allow background
 * location" explanation dialog on Home, so it only ever interrupts them
 * once.
 *
 * Without this, Home's permission check (LaunchedEffect keyed on Unit)
 * re-runs every time the screen re-enters composition — which is every
 * time the Home tab is revisited, not just app cold starts — and it has
 * no other way to tell "declined" apart from "never asked". SharedPreferences
 * rather than in-memory state because the whole point is for the answer to
 * survive the recomposition that triggers the check in the first place.
 */
object BackgroundLocationPromptPreference {

    private const val PREFS_NAME = "histoury_background_location"
    private const val KEY_ASKED = "asked"

    fun hasAsked(context: Context): Boolean =
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ASKED, false)

    fun markAsked(context: Context) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ASKED, true)
            .apply()
    }
}
