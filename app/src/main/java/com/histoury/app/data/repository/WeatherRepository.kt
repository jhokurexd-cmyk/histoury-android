package com.histoury.app.data.repository

import com.histoury.app.data.model.Weather
import com.histoury.app.data.model.WeatherCondition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/**
 * Current conditions from Open-Meteo.
 *
 * Chosen over OpenWeatherMap/AccuWeather because it needs no API key, no
 * account, and no billing setup — which also means there's no secret to leak
 * in the APK. It's free for non-commercial use, which covers a capstone.
 *
 * Uses HttpURLConnection + org.json (both in the Android SDK) rather than
 * Retrofit/OkHttp so this feature adds zero new Gradle dependencies.
 */
class WeatherRepository {

    companion object {

        // Plaza Roma, Intramuros — the app is location-scoped to the walled
        // city, so a fixed centre point is accurate for every user on site
        // and still correct for users browsing from home.
        const val INTRAMUROS_LAT = 14.5896
        const val INTRAMUROS_LNG = 120.9750

        private const val TIMEOUT_MS = 8000
    }

    /**
     * Returns null on any failure (offline, timeout, malformed body) so the
     * Home header can quietly fall back instead of crashing or blocking.
     */
    suspend fun getCurrentWeather(
        latitude: Double = INTRAMUROS_LAT,
        longitude: Double = INTRAMUROS_LNG
    ): Weather? = withContext(Dispatchers.IO) {

        var connection: HttpURLConnection? = null

        try {

            val url = URL(
                "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$latitude" +
                    "&longitude=$longitude" +
                    "&current=temperature_2m,weather_code,is_day" +
                    "&timezone=Asia%2FManila"
            )

            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext null
            }

            val body = connection.inputStream
                .bufferedReader()
                .use { reader -> reader.readText() }

            val current = JSONObject(body).optJSONObject("current")
                ?: return@withContext null

            Weather(
                temperatureC = current.optDouble("temperature_2m", 0.0).roundToInt(),
                condition = WeatherCondition.fromWmoCode(
                    current.optInt("weather_code", -1)
                ),
                isDay = current.optInt("is_day", 1) == 1
            )

        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
