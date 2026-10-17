package com.histoury.app.map

import com.google.android.gms.maps.model.LatLng
import com.histoury.app.BuildConfig
import com.histoury.app.data.repository.TravelMode

/** Which SDK draws the app's maps. */
enum class MapRenderer { MAPLIBRE, GOOGLE }

/**
 * Map and routing configuration, read once from BuildConfig.
 *
 * Every value comes from `local.properties` (or Gradle properties / env
 * variables) at build time, so switching renderer or tile provider never
 * needs a code change — see app/build.gradle.kts and MAPLIBRE_MIGRATION.md.
 *
 *   HISTOURY_MAP_RENDERER     maplibre (default) | google
 *   HISTOURY_MAP_STYLE_LIGHT  MapLibre style URL for the light theme
 *   HISTOURY_MAP_STYLE_DARK   MapLibre style URL for the dark theme
 *
 * The Google renderer stays available until MapLibre has been checked on
 * real devices (the brief: replace Google only after feature parity). It is
 * the same app either way; only the map drawing and the routing providers
 * the server may use differ.
 */
object MapConfig {

    val renderer: MapRenderer =
        if (BuildConfig.HISTOURY_MAP_RENDERER.trim().equals("google", ignoreCase = true)) {
            MapRenderer.GOOGLE
        } else {
            MapRenderer.MAPLIBRE
        }

    val useMapLibre: Boolean
        get() = renderer == MapRenderer.MAPLIBRE

    fun styleUrl(dark: Boolean): String =
        if (dark) BuildConfig.HISTOURY_MAP_STYLE_DARK else BuildConfig.HISTOURY_MAP_STYLE_LIGHT

    /**
     * Sent to the getRoute function. Google Maps Platform terms allow Google
     * route geometry only on a Google map, so for "maplibre" the server
     * answers from OpenStreetMap-based providers only (ORS / Valhalla).
     */
    val routingRenderer: String
        get() = if (useMapLibre) "maplibre" else "google"

    /** Starting view when a map has nothing else to show. */
    val INTRAMUROS_CENTER = LatLng(14.5896, 120.9757)
}

/**
 * Features that are switched off on purpose for now. Kept in one place so
 * turning them back on later is a one-line change, and so no screen shows a
 * button that can't work.
 */
object FeatureFlags {

    /**
     * Commute works on both maps. On the Google map it is Google transit, as
     * before. On the MapLibre map it uses the trains, buses and jeepneys
     * mapped in OpenStreetMap, walking from OpenRouteService, and estimated
     * ride/wait times (OSM has no timetables or fares; not every route is
     * mapped). Until the server has its transit data (transit-data.json) the
     * screen says commute isn't set up yet; nothing is invented.
     */
    val commuteDirections: Boolean
        get() = true

    /**
     * Motorcycle directions work on both maps: from Google on the Google map,
     * and from Valhalla's motorcycle profile (OpenStreetMap data) on the
     * MapLibre map. On MapLibre the server needs a Valhalla endpoint
     * (VALHALLA_URL / VALHALLA_API_KEY); until then the screen says
     * motorcycle directions aren't set up yet. It is never routed as a car.
     */
    val motorcycleDirections: Boolean
        get() = true
}

/**
 * Which travel modes the app can route right now.
 *
 * A mode that isn't available is NEVER swapped for another one behind the
 * visitor's back. A trip saved as Commute stays Commute: the screen keeps
 * that mode selected, shows a "Coming Soon" message instead of a route, and
 * the visitor can pick Walk or Drive themselves if they want a route now.
 */
object TravelModes {

    fun isAvailable(mode: TravelMode): Boolean = when (mode) {
        TravelMode.WALKING, TravelMode.DRIVING -> true
        TravelMode.MOTORCYCLE -> FeatureFlags.motorcycleDirections
        TravelMode.COMMUTE -> FeatureFlags.commuteDirections
    }

    /** In display order. */
    val available: List<TravelMode>
        get() = TravelMode.entries.filter(::isAvailable)

    /**
     * The modes a selector should show: every available mode, plus [selected]
     * when it is a saved mode that isn't available (so the visitor can see
     * what the trip was planned with, marked "Soon").
     */
    fun shownWith(selected: TravelMode): List<TravelMode> =
        TravelMode.entries.filter { isAvailable(it) || it == selected }

    /**
     * Why [mode] can't be routed, in words for the visitor, or null when it
     * can. Shown instead of a route; nothing is routed in its place.
     */
    fun unavailableMessage(mode: TravelMode): String? = when {
        isAvailable(mode) -> null
        mode == TravelMode.COMMUTE ->
            "Commute directions are coming soon. Histoury doesn't have reliable " +
                "public transport data for this map yet. Your trip stays saved as Commute. " +
                "Choose Walk, Drive or Motorcycle if you want a route now."
        else -> "Directions for this travel mode are coming soon."
    }

    /**
     * The mode stored as [wire] ("WALK", "DRIVE", "TWO_WHEELER", "TRANSIT").
     * Only a missing or unrecognised value falls back to Walking, the app's
     * long-standing default for trips saved before travel modes existed.
     * A recognised but unavailable mode is returned as-is.
     */
    fun fromWire(wire: String?): TravelMode =
        TravelMode.entries.firstOrNull { it.wireValue == wire } ?: TravelMode.WALKING
}
