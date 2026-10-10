package com.histoury.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.MapProperties
import com.google.android.gms.maps.model.MapStyleOptions
import com.histoury.app.theme.isDarkTheme

/**
 * A night style for the Google Maps views — now only used by lite-mode
 * (static) maps; see [rememberThemedMapProperties].
 *
 * The maps are the one part of the app Compose doesn't paint — they render
 * their own tiles — so switching the palette leaves three glaring white
 * rectangles behind. This feeds the Maps SDK a style that darkens them to
 * match.
 *
 * The JSON is tuned rather than copied wholesale: roads and labels stay
 * legible because navigation still has to work in the dark, and the water
 * around Intramuros keeps enough contrast against land that the walled
 * city's outline is still recognisable at a glance.
 */
private const val NIGHT_STYLE_JSON = """
[
  {"elementType":"geometry","stylers":[{"color":"#1c1a1e"}]},
  {"elementType":"labels.icon","stylers":[{"visibility":"off"}]},
  {"elementType":"labels.text.fill","stylers":[{"color":"#9c96a3"}]},
  {"elementType":"labels.text.stroke","stylers":[{"color":"#121013"}]},
  {"featureType":"administrative","elementType":"geometry","stylers":[{"color":"#35313a"}]},
  {"featureType":"administrative.locality","elementType":"labels.text.fill","stylers":[{"color":"#c9c3d0"}]},
  {"featureType":"poi","elementType":"labels.text.fill","stylers":[{"color":"#8d8794"}]},
  {"featureType":"poi.park","elementType":"geometry","stylers":[{"color":"#20291f"}]},
  {"featureType":"poi.park","elementType":"labels.text.fill","stylers":[{"color":"#6f8a68"}]},
  {"featureType":"road","elementType":"geometry","stylers":[{"color":"#2c2930"}]},
  {"featureType":"road","elementType":"geometry.stroke","stylers":[{"color":"#211f24"}]},
  {"featureType":"road","elementType":"labels.text.fill","stylers":[{"color":"#a49eab"}]},
  {"featureType":"road.arterial","elementType":"geometry","stylers":[{"color":"#332f37"}]},
  {"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#413b45"}]},
  {"featureType":"road.highway","elementType":"labels.text.fill","stylers":[{"color":"#d4ccd9"}]},
  {"featureType":"transit","elementType":"geometry","stylers":[{"color":"#2a262e"}]},
  {"featureType":"transit.station","elementType":"labels.text.fill","stylers":[{"color":"#a99ba0"}]},
  {"featureType":"water","elementType":"geometry","stylers":[{"color":"#0e1418"}]},
  {"featureType":"water","elementType":"labels.text.fill","stylers":[{"color":"#4a5560"}]},
  {"featureType":"water","elementType":"labels.text.stroke","stylers":[{"color":"#0e1418"}]}
]
"""

/**
 * The map's colour scheme, following the APP's light/dark setting (not the
 * phone's, which can differ).
 *
 * Interactive maps use Google's own dark scheme (Maps SDK 19+) instead of
 * the JSON night style above. The JSON style can only recolour whole
 * feature groups — every "road" got one dark grey — so footpaths, sidewalks
 * and pedestrian walkways ended up the same colour as the roads beside
 * them, or vanished into the background. Google's dark scheme keeps all of
 * that detail legible. Pass this as GoogleMap(mapColorScheme = ...).
 */
@Composable
fun rememberThemedMapColorScheme(): ComposeMapColorScheme =
    if (isDarkTheme) ComposeMapColorScheme.DARK else ComposeMapColorScheme.LIGHT

/**
 * Builds [MapProperties] for a map.
 *
 * Interactive maps get NO style JSON at all: the base map then shows every
 * pedestrian path, walkway and sidewalk the Maps SDK has data for, and the
 * dark look comes from [rememberThemedMapColorScheme] instead.
 *
 * [liteMode] maps (a static bitmap, e.g. Place Details) don't support the
 * colour scheme, so only they still get the JSON night style in dark mode.
 *
 * Parsing the JSON allocates, so it's remembered against the theme flag and
 * only rebuilt when the theme actually changes.
 */
@Composable
fun rememberThemedMapProperties(
    isMyLocationEnabled: Boolean = false,
    liteMode: Boolean = false
): MapProperties {

    val dark = isDarkTheme

    val style = remember(dark, liteMode) {
        if (dark && liteMode) MapStyleOptions(NIGHT_STYLE_JSON) else null
    }

    return MapProperties(
        isMyLocationEnabled = isMyLocationEnabled,
        mapStyleOptions = style
    )
}
