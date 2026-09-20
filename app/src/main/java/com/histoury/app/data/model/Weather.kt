package com.histoury.app.data.model

/**
 * A single "right now" weather snapshot for the Home header.
 *
 * Deliberately minimal — the Home widget only shows temperature,
 * a condition label, and a matching icon.
 */
data class Weather(
    val temperatureC: Int = 0,
    val condition: WeatherCondition = WeatherCondition.CLEAR,
    val isDay: Boolean = true
)

/**
 * Open-Meteo reports conditions as WMO weather codes (0-99). Those are far
 * more granular than a header chip needs, so they're collapsed into a handful
 * of buckets that each map cleanly onto one icon and one short label.
 */
enum class WeatherCondition(private val dayLabel: String, private val nightLabel: String) {

    CLEAR("Sunny", "Clear"),
    MOSTLY_CLEAR("Mostly Sunny", "Mostly Clear"),
    CLOUDY("Cloudy", "Cloudy"),
    OVERCAST("Overcast", "Overcast"),
    FOG("Foggy", "Foggy"),
    DRIZZLE("Light Rain", "Light Rain"),
    RAIN("Rainy", "Rainy"),
    SHOWERS("Showers", "Showers"),
    THUNDERSTORM("Thunderstorm", "Thunderstorm");

    /** "Sunny" reads wrong at 8pm, so the label flips after dark. */
    fun label(isDay: Boolean): String = if (isDay) dayLabel else nightLabel

    companion object {

        fun fromWmoCode(code: Int): WeatherCondition = when (code) {
            0 -> CLEAR
            1, 2 -> MOSTLY_CLEAR
            3 -> OVERCAST
            45, 48 -> FOG
            51, 53, 55, 56, 57 -> DRIZZLE
            61, 63, 65, 66, 67 -> RAIN
            80, 81, 82, 85, 86 -> SHOWERS
            95, 96, 99 -> THUNDERSTORM
            else -> CLOUDY
        }
    }
}
