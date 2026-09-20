package com.histoury.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.google.maps.android.compose.MapProperties
import com.google.android.gms.maps.model.MapStyleOptions
import com.histoury.app.theme.isDarkTheme

/**
 * A night style for the Google Maps views.
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
 * Builds [MapProperties] carrying the night style when the app is dark and
 * no style at all when it isn't — passing null restores the SDK's default,
 * so light mode is untouched.
 *
 * Parsing the JSON allocates, so it's remembered against the theme flag and
 * only rebuilt when the theme actually changes.
 */
@Composable
fun rememberThemedMapProperties(
    isMyLocationEnabled: Boolean = false
): MapProperties {

    val dark = isDarkTheme

    val style = remember(dark) {
        if (dark) MapStyleOptions(NIGHT_STYLE_JSON) else null
    }

    return MapProperties(
        isMyLocationEnabled = isMyLocationEnabled,
        mapStyleOptions = style
    )
}
