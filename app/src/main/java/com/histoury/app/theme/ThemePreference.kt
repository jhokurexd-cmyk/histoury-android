package com.histoury.app.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Remembers the visitor's appearance choice across launches.
 *
 * Kept in SharedPreferences rather than Firestore on purpose: the choice
 * has to be known before the first frame is drawn, and it is about this
 * phone in this room, not about the account. Reading it is a synchronous
 * disk hit of a single string, which is why [load] can be called straight
 * from onCreate without a coroutine.
 *
 * The in-memory [mode] is the source of truth once loaded, so the theme
 * switches instantly on selection and the write happens behind it.
 */
object ThemePreference {

    private const val PREFS_NAME = "histoury_appearance"
    private const val KEY_THEME_MODE = "theme_mode"

    private val _mode = MutableStateFlow(ThemeMode.SYSTEM)
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    fun load(context: Context) {

        val stored = context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME_MODE, null)

        _mode.value = ThemeMode.fromStoredValue(stored)
    }

    fun set(context: Context, mode: ThemeMode) {

        _mode.value = mode

        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, mode.name)
            .apply()
    }
}
