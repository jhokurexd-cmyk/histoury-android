package com.histoury.app.ui.map

import com.google.android.gms.maps.model.LatLng

/**
 * What a map shows, independent of the SDK that draws it.
 *
 * Screens describe their markers and lines with these plain values; the
 * MapLibre layer ([HistouryMapLibre]) turns them into GeoJSON sources and
 * style layers. Nothing here knows about MapLibre, so business logic and
 * screen state never depend on the renderer.
 *
 * Positions use the app's existing LatLng model class (plain latitude /
 * longitude values), not anything from a map SDK's renderer.
 */
data class MapScene(
    val lines: List<MapLine> = emptyList(),
    val markers: List<MapMarker> = emptyList()
)

/** How a line is drawn. */
enum class LinePattern { SOLID, DASHED, DOTTED }

/**
 * A route or route leg.
 *
 * [widthDp] is in density-independent pixels. [casingColor] draws a wider
 * line underneath (the white edge that lifts a route off the base map).
 * Higher [zIndex] draws on top.
 */
data class MapLine(
    val id: String,
    val points: List<LatLng>,
    val color: Int,
    val widthDp: Float,
    val pattern: LinePattern = LinePattern.SOLID,
    val casingColor: Int? = null,
    val casingWidthDp: Float = 0f,
    val zIndex: Float = 0f
)

/** The picture used for a marker. Equal values share one bitmap. */
sealed interface MarkerIcon {

    /** Teardrop pin, anchored at its tip. */
    data class Pin(val color: Int) : MarkerIcon

    /** Round numbered pin (itinerary stops), anchored at its tip. */
    data class NumberedPin(val number: Int, val color: Int) : MarkerIcon

    /**
     * A historical site's own photo in a circle, with a short pointer tail in
     * [color], anchored at its tip. Shows a plain placeholder in the same
     * shape until the photo has downloaded (or if it can't be).
     */
    data class Photo(val url: String, val color: Int) : MarkerIcon

    /** Heading arrow for the navigating visitor, centred and rotated with the map. */
    data class Arrow(val color: Int) : MarkerIcon
}

/**
 * A tappable point. [title] / [subtitle] are shown in a small callout when
 * the marker is tapped and the screen doesn't handle the tap itself.
 */
data class MapMarker(
    val id: String,
    val position: LatLng,
    val icon: MarkerIcon,
    val title: String? = null,
    val subtitle: String? = null,
    val zIndex: Float = 0f,
    val alpha: Float = 1f
)

/** Camera padding / UI insets in dp (the area the map's own controls must avoid). */
data class MapInsets(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f
)

/**
 * Google zoom levels use 256 px tiles; MapLibre's use 512 px. The same
 * on-screen scale is therefore one level lower in MapLibre. Screens keep
 * thinking in the zoom levels they were tuned with and convert here.
 */
fun googleZoomToMapLibre(googleZoom: Float): Double = (googleZoom - 1f).toDouble()
