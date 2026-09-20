package com.histoury.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * How the app decides which palette to draw with.
 *
 * [SYSTEM] is the default because a phone-wide dark setting is usually a
 * statement about the room the phone is in, not just about one app.
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    /** Label shown next to Appearance in the menu. */
    val label: String
        get() = when (this) {
            SYSTEM -> "Match system"
            LIGHT -> "Light"
            DARK -> "Dark"
        }

    companion object {
        fun fromStoredValue(value: String?): ThemeMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

@Composable
fun HistouryTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {

    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colors = if (useDark) DarkHistouryColors else LightHistouryColors

    // Material's own scheme is kept in step with ours. The app draws almost
    // everything from HistouryColors directly, but Material components it
    // doesn't style by hand — text field cursors, ripples, sheet scrims —
    // read from here, and they'd stay light otherwise.
    val materialScheme = if (useDark) {
        darkColorScheme(
            primary = colors.primary,
            secondary = colors.primaryDark,
            background = colors.background,
            surface = colors.surface,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceRaised,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.outline,
            error = colors.danger
        )
    } else {
        lightColorScheme(
            primary = colors.primary,
            secondary = colors.primaryDark,
            background = colors.background,
            surface = colors.surface,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceSoft,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.outline,
            error = colors.danger
        )
    }

    CompositionLocalProvider(LocalHistouryColors provides colors) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = Typography,
            content = content
        )
    }
}
