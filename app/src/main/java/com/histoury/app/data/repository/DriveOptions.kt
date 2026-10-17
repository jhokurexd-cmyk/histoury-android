package com.histoury.app.data.repository

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The visitor's "Avoid expressways and tolls" choice for car (and, on the
 * Google map, motorcycle) routes. Off by default, so routes are as before.
 *
 * In Metro Manila the expressways (NLEX, SLEX, Skyway, CAVITEX, NAIAX...)
 * are also the toll roads, so one switch covers both: the server is asked
 * to avoid highways AND tollways.
 *
 * Kept in SharedPreferences like [com.histoury.app.theme.ThemePreference]:
 * it's about how this visitor likes to drive, known before any route is
 * asked for. [avoidExpressways] is the source of truth once loaded.
 */
object DriveOptions {

    private const val PREFS_NAME = "histoury_drive_options"
    private const val KEY_AVOID_EXPRESSWAYS = "avoid_expressways"

    private val _avoidExpressways = MutableStateFlow(false)
    val avoidExpressways: StateFlow<Boolean> = _avoidExpressways.asStateFlow()

    fun load(context: Context) {
        _avoidExpressways.value = context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AVOID_EXPRESSWAYS, false)
    }

    fun setAvoidExpressways(context: Context, avoid: Boolean) {
        _avoidExpressways.value = avoid
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AVOID_EXPRESSWAYS, avoid)
            .apply()
    }

    /** Whether the switch applies to [mode]: only vehicles use expressways. */
    fun appliesTo(mode: TravelMode): Boolean =
        mode == TravelMode.DRIVING || mode == TravelMode.MOTORCYCLE

    /** True when a [mode] route should avoid expressways and tolls right now. */
    fun avoidFor(mode: TravelMode): Boolean = appliesTo(mode) && _avoidExpressways.value
}
