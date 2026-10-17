package com.histoury.app.ui.map

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.Disposable
import coil.request.ImageRequest
import coil.size.Scale
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.coroutines.resume
import org.maplibre.android.geometry.LatLng as MlLatLng

/** A renderer-neutral snapshot of where the camera is. Zoom is in MapLibre levels. */
data class MapCamera(
    val target: LatLng,
    val zoom: Double,
    val bearing: Double,
    val tilt: Double
)

/**
 * Everything a screen can do to a MapLibre map: move the camera, fit
 * points, follow a moving position, and hear about the visitor's own
 * gestures. Owned by the screen ([rememberHistouryMapState]) and handed to
 * [HistouryMapLibre], which attaches the real map to it.
 *
 * Calls made before the map and its style have loaded are not lost: the
 * last camera request is kept and applied as soon as the map is ready.
 */
@Stable
class HistouryMapState internal constructor() {

    internal var map: MapLibreMap? by mutableStateOf<MapLibreMap?>(null)
        private set

    internal var mapView: MapView? = null
        private set

    /** True once the current style (light or dark) has finished loading. */
    var isStyleLoaded by mutableStateOf(false)
        internal set

    /** Why the map couldn't load (no connection and nothing cached, usually); null when fine. */
    var loadError by mutableStateOf<String?>(null)
        internal set

    /** Bumped by [retry] to reload the style. */
    internal var reloadCount by mutableIntStateOf(0)
        private set

    /** The marker whose callout is open, if any. */
    internal var callout by mutableStateOf<MapMarker?>(null)

    /** True from the moment a finger moves the map until the camera settles. */
    var isGestureInProgress: Boolean = false
        internal set

    private val _userGestures = MutableSharedFlow<Unit>(extraBufferCapacity = 8)

    /** One event each time the visitor starts panning, zooming or rotating the map. */
    val userGestures: SharedFlow<Unit> = _userGestures.asSharedFlow()

    internal var density: Float = 1f

    // The last scene drawn, so it can be redrawn after a style change.
    internal var lastScene: MapScene = MapScene()
    private val registeredIcons = HashSet<String>()

    // Site-photo pins: finished pictures by icon key (kept across style
    // changes), downloads in progress, and photos that failed this style.
    private var appContext: Context? = null
    private val photoPins = HashMap<String, android.graphics.Bitmap>()
    private val photoLoads = HashMap<String, Disposable>()
    private val photoFailed = HashSet<String>()

    private var pendingCamera: ((MapLibreMap) -> Unit)? = null

    // ----------------------------------------------------------------- wiring

    internal fun attach(map: MapLibreMap, view: MapView, density: Float) {
        this.map = map
        this.mapView = view
        this.density = density
        this.appContext = view.context.applicationContext
        map.addOnCameraMoveStartedListener { reason ->
            if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                isGestureInProgress = true
                _userGestures.tryEmit(Unit)
            }
        }
        map.addOnCameraIdleListener { isGestureInProgress = false }
    }

    internal fun detach() {
        map = null
        mapView = null
        isStyleLoaded = false
        registeredIcons.clear()
        photoLoads.values.forEach { it.dispose() }
        photoLoads.clear()
    }

    internal fun onStyleLoading() {
        isStyleLoaded = false
        loadError = null
        registeredIcons.clear()
        // A new style (or a retry after going offline) gets another try at
        // photos that failed before.
        photoFailed.clear()
    }

    internal fun onStyleLoaded(style: Style) {
        installLayers(style)
        isStyleLoaded = true
        loadError = null
        render(lastScene)
        map?.let { m -> pendingCamera?.let { op -> pendingCamera = null; runCatching { op(m) } } }
    }

    /** Reloads the style after a failure (e.g. once the connection is back). */
    fun retry() {
        loadError = null
        reloadCount++
    }

    private fun whenReady(op: (MapLibreMap) -> Unit) {
        val m = map
        if (m != null && isStyleLoaded) {
            runCatching { op(m) }.onFailure { Log.w(TAG, "Camera update failed", it) }
        } else {
            pendingCamera = op
        }
    }

    /** Suspends until the map and its style are ready, then returns the map. */
    private suspend fun awaitMap(): MapLibreMap {
        snapshotFlow { map != null && isStyleLoaded }.first { it }
        return map!!
    }

    // ----------------------------------------------------------------- camera

    /** The camera right now, or null before the map exists. */
    fun currentCamera(): MapCamera? {
        val position = map?.cameraPosition ?: return null
        val target = position.target ?: return null
        return MapCamera(LatLng(target.latitude, target.longitude), position.zoom, position.bearing, position.tilt)
    }

    /** Jumps (no animation). [googleZoom] is in the Google zoom levels the screens were tuned with. */
    fun moveTo(target: LatLng, googleZoom: Float) {
        whenReady { it.moveCamera(CameraUpdateFactory.newLatLngZoom(target.ml(), googleZoomToMapLibre(googleZoom))) }
    }

    /**
     * One camera frame for follow mode: called many times a second, so it
     * never queues and never animates. [paddingPx] moves the focal point
     * (e.g. lower on screen, under a banner).
     */
    fun moveFrame(camera: MapCamera, paddingPx: DoubleArray? = null) {
        val m = map ?: return
        if (!isStyleLoaded) return
        val builder = CameraPosition.Builder()
            .target(camera.target.ml())
            .zoom(camera.zoom)
            .bearing(camera.bearing)
            .tilt(camera.tilt)
        if (paddingPx != null) builder.padding(paddingPx)
        runCatching { m.moveCamera(CameraUpdateFactory.newCameraPosition(builder.build())) }
    }

    /** Drops any focal-point padding a follow camera left behind (before fitting bounds). */
    fun resetPadding() {
        val m = map ?: return
        runCatching { m.moveCamera(CameraUpdateFactory.paddingTo(0.0, 0.0, 0.0, 0.0)) }
    }

    /** Glides to [target]. Returns when the animation ends (or is interrupted). */
    suspend fun animateTo(target: LatLng, googleZoom: Float, durationMs: Int = 600) {
        val m = awaitMap()
        animate(m, CameraUpdateFactory.newLatLngZoom(target.ml(), googleZoomToMapLibre(googleZoom)), durationMs)
    }

    /**
     * Frames every point with [paddingDp] around them (plus [extraTopDp] /
     * [extraBottomDp] for overlays). One point, or points all in the same
     * spot, zoom to [singlePointGoogleZoom] instead of the maximum zoom.
     */
    suspend fun fitBounds(
        points: List<LatLng>,
        paddingDp: Int,
        durationMs: Int = 700,
        extraTopDp: Int = 0,
        extraBottomDp: Int = 0,
        singlePointGoogleZoom: Float = 17f
    ) {
        if (points.isEmpty()) return
        val m = awaitMap()
        val distinct = points.distinctBy { it.latitude to it.longitude }
        if (distinct.size < 2) {
            animate(m, CameraUpdateFactory.newLatLngZoom(distinct.first().ml(), googleZoomToMapLibre(singlePointGoogleZoom)), durationMs)
            return
        }
        // The view must have a size before bounds can be fitted.
        // (A View's size isn't snapshot state, so this polls briefly.)
        var waitedMs = 0
        while (waitedMs < 2_000) {
            val view = mapView ?: return
            if (view.width > 0 && view.height > 0) break
            delay(16)
            waitedMs += 16
        }
        val bounds = LatLngBounds.Builder().includes(distinct.map { it.ml() }).build()
        val pad = (paddingDp * density).toInt()
        val update = CameraUpdateFactory.newLatLngBounds(
            bounds,
            pad,
            pad + (extraTopDp * density).toInt(),
            pad,
            pad + (extraBottomDp * density).toInt()
        )
        animate(m, update, durationMs)
    }

    private suspend fun animate(m: MapLibreMap, update: org.maplibre.android.camera.CameraUpdate, durationMs: Int) {
        if (durationMs <= 0) {
            runCatching { m.moveCamera(update) }
            return
        }
        suspendCancellableCoroutine { continuation ->
            val callback = object : MapLibreMap.CancelableCallback {
                override fun onFinish() {
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onCancel() {
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
            try {
                m.animateCamera(update, durationMs, callback)
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resume(Unit)
            }
            continuation.invokeOnCancellation { runCatching { m.cancelTransitions() } }
        }
    }

    /** Glides to the visitor's last known position, if the location dot has one. */
    @SuppressLint("MissingPermission")
    suspend fun animateToUserLocation(googleZoom: Float = 17f): Boolean {
        val m = awaitMap()
        val location = runCatching {
            if (m.locationComponent.isLocationComponentActivated) m.locationComponent.lastKnownLocation else null
        }.getOrNull() ?: return false
        animateTo(LatLng(location.latitude, location.longitude), googleZoom)
        return true
    }

    // --------------------------------------------------------- location dot

    /**
     * Shows or hides the blue "you are here" dot. Uses MapLibre's own
     * location engine and only while the map is on screen; it never asks
     * for permission itself (the screens already do).
     */
    @SuppressLint("MissingPermission")
    internal fun setUserLocationVisible(context: Context, visible: Boolean, accentColor: Int) {
        val m = map ?: return
        val style = m.style ?: return
        if (!style.isFullyLoaded) return
        val granted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val component = m.locationComponent
        try {
            if (visible && granted) {
                if (!component.isLocationComponentActivated) {
                    val options = LocationComponentOptions.builder(context)
                        .foregroundTintColor(accentColor)
                        .bearingTintColor(accentColor)
                        .accuracyColor(accentColor)
                        .accuracyAlpha(0.12f)
                        .build()
                    component.activateLocationComponent(
                        LocationComponentActivationOptions.builder(context, style)
                            .locationComponentOptions(options)
                            .useDefaultLocationEngine(true)
                            .build()
                    )
                }
                component.isLocationComponentEnabled = true
                component.cameraMode = CameraMode.NONE
                component.renderMode = RenderMode.COMPASS
            } else if (component.isLocationComponentActivated) {
                component.isLocationComponentEnabled = false
            }
        } catch (e: Exception) {
            // A revoked permission or a style swap mid-call: no dot this time.
            Log.w(TAG, "Location dot unavailable", e)
        }
    }

    // -------------------------------------------------------------- drawing

    private fun installLayers(style: Style) {

        if (style.getSource(SOURCE_LINES) == null) {
            style.addSource(GeoJsonSource(SOURCE_LINES, FeatureCollection.fromFeatures(emptyList<Feature>())))
        }
        if (style.getSource(SOURCE_MARKERS) == null) {
            style.addSource(GeoJsonSource(SOURCE_MARKERS, FeatureCollection.fromFeatures(emptyList<Feature>())))
        }
        if (style.getSource(SOURCE_PUCK) == null) {
            style.addSource(GeoJsonSource(SOURCE_PUCK, FeatureCollection.fromFeatures(emptyList<Feature>())))
        }

        fun lineLayer(id: String, filter: Expression, dash: Array<Float>?): LineLayer {
            val layer = LineLayer(id, SOURCE_LINES)
                .withFilter(filter)
                .withProperties(
                    PropertyFactory.lineColor(Expression.toColor(Expression.get("color"))),
                    PropertyFactory.lineWidth(Expression.get("width")),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    PropertyFactory.lineSortKey(Expression.get("z"))
                )
            if (dash != null) layer.setProperties(PropertyFactory.lineDasharray(dash))
            return layer
        }

        val kind = Expression.get("kind")
        val pattern = Expression.get("pattern")

        if (style.getLayer(LAYER_CASING) == null) {
            style.addLayer(lineLayer(LAYER_CASING, Expression.eq(kind, "casing"), null))
        }
        if (style.getLayer(LAYER_SOLID) == null) {
            style.addLayer(lineLayer(LAYER_SOLID, Expression.all(Expression.eq(kind, "main"), Expression.eq(pattern, "solid")), null))
        }
        if (style.getLayer(LAYER_DASHED) == null) {
            style.addLayer(
                lineLayer(LAYER_DASHED, Expression.all(Expression.eq(kind, "main"), Expression.eq(pattern, "dashed")), arrayOf(2f, 1.5f))
            )
        }
        if (style.getLayer(LAYER_DOTTED) == null) {
            // With round caps, near-zero dashes draw as dots.
            style.addLayer(
                lineLayer(LAYER_DOTTED, Expression.all(Expression.eq(kind, "main"), Expression.eq(pattern, "dotted")), arrayOf(0.01f, 2f))
            )
        }

        if (style.getLayer(LAYER_PINS) == null) {
            style.addLayer(
                SymbolLayer(LAYER_PINS, SOURCE_MARKERS)
                    .withFilter(Expression.eq(Expression.get("centered"), false))
                    .withProperties(
                        PropertyFactory.iconImage(Expression.get("icon")),
                        PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconIgnorePlacement(true),
                        PropertyFactory.iconOpacity(Expression.get("opacity")),
                        PropertyFactory.symbolSortKey(Expression.get("z")),
                        // Photo pins shrink as the map zooms out so a whole
                        // district of them stays readable; other pins don't.
                        PropertyFactory.iconSize(
                            Expression.interpolate(
                                Expression.linear(),
                                Expression.zoom(),
                                Expression.stop(
                                    11,
                                    Expression.switchCase(
                                        Expression.eq(Expression.get("photo"), Expression.literal(true)),
                                        Expression.literal(0.55f),
                                        Expression.literal(1f)
                                    )
                                ),
                                Expression.stop(14.5, Expression.literal(1f))
                            )
                        )
                    )
            )
        }
        if (style.getLayer(LAYER_CENTERED) == null) {
            style.addLayer(
                SymbolLayer(LAYER_CENTERED, SOURCE_MARKERS)
                    .withFilter(Expression.eq(Expression.get("centered"), true))
                    .withProperties(
                        PropertyFactory.iconImage(Expression.get("icon")),
                        PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconIgnorePlacement(true),
                        PropertyFactory.iconOpacity(Expression.get("opacity")),
                        PropertyFactory.symbolSortKey(Expression.get("z"))
                    )
            )
        }
        if (style.getLayer(LAYER_PUCK) == null) {
            // The navigating visitor: lies flat on the map and turns with it.
            style.addLayer(
                SymbolLayer(LAYER_PUCK, SOURCE_PUCK)
                    .withProperties(
                        PropertyFactory.iconImage(Expression.get("icon")),
                        PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
                        PropertyFactory.iconRotate(Expression.get("bearing")),
                        PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                        PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_MAP),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconIgnorePlacement(true)
                    )
            )
        }
    }

    private fun ensureIcon(style: Style, icon: MarkerIcon): String {
        val key = MarkerIcons.key(icon)
        if (key !in registeredIcons) {
            val picture = if (icon is MarkerIcon.Photo) photoPins[key] else null
            style.addImage(key, picture ?: MarkerIcons.bitmap(icon, density))
            registeredIcons += key
        }
        if (icon is MarkerIcon.Photo) loadPhoto(key, icon)
        return key
    }

    /**
     * Downloads a site photo (through Coil, so it shares the app's image
     * cache with the site cards) and swaps it into the pin. Until then, or
     * if it fails, the pin shows its placeholder. One download per photo.
     */
    private fun loadPhoto(key: String, icon: MarkerIcon.Photo) {
        if (key in photoPins || key in photoLoads || key in photoFailed) return
        val context = appContext ?: return
        val px = (MarkerIcons.PHOTO_PIN_DP * density).toInt().coerceAtLeast(64)
        val request = ImageRequest.Builder(context)
            .data(icon.url)
            .size(px, px)
            .scale(Scale.FILL)
            // The pin is drawn with a software Canvas, which can't read
            // hardware bitmaps.
            .allowHardware(false)
            .target(
                onSuccess = { drawable ->
                    photoLoads.remove(key)
                    val pin = runCatching {
                        MarkerIcons.photoPin(drawable.toBitmap(), icon.color, density)
                    }.getOrNull()
                    if (pin != null) {
                        photoPins[key] = pin
                        // Replace the placeholder in place if this style is using it.
                        val style = map?.style
                        if (style != null && style.isFullyLoaded && key in registeredIcons) {
                            runCatching { style.addImage(key, pin) }
                        }
                    } else {
                        photoFailed += key
                    }
                },
                onError = {
                    photoLoads.remove(key)
                    photoFailed += key
                }
            )
            .build()
        photoLoads[key] = context.imageLoader.enqueue(request)
    }

    /** Draws [scene]; kept and redrawn automatically after a style change. */
    internal fun render(scene: MapScene) {
        lastScene = scene
        val style = map?.style ?: return
        if (!isStyleLoaded && !style.isFullyLoaded) return

        val lineFeatures = ArrayList<Feature>()
        scene.lines.forEach { line ->
            if (line.points.size < 2) return@forEach
            val geometry = LineString.fromLngLats(line.points.map { Point.fromLngLat(it.longitude, it.latitude) })
            val casing = line.casingColor
            if (casing != null && line.casingWidthDp > 0f) {
                lineFeatures += Feature.fromGeometry(geometry).apply {
                    addStringProperty("kind", "casing")
                    addStringProperty("color", cssColor(casing))
                    addNumberProperty("width", line.casingWidthDp)
                    addNumberProperty("z", line.zIndex * 10f)
                }
            }
            lineFeatures += Feature.fromGeometry(geometry).apply {
                addStringProperty("kind", "main")
                addStringProperty("pattern", line.pattern.name.lowercase())
                addStringProperty("color", cssColor(line.color))
                addNumberProperty("width", line.widthDp)
                addNumberProperty("z", line.zIndex * 10f + 1f)
            }
        }

        val markerFeatures = scene.markers.map { marker ->
            Feature.fromGeometry(Point.fromLngLat(marker.position.longitude, marker.position.latitude)).apply {
                addStringProperty("id", marker.id)
                addStringProperty("icon", ensureIcon(style, marker.icon))
                addBooleanProperty("centered", MarkerIcons.isCentered(marker.icon))
                addBooleanProperty("photo", marker.icon is MarkerIcon.Photo)
                addNumberProperty("opacity", marker.alpha)
                addNumberProperty("z", marker.zIndex)
            }
        }

        style.getSourceAs<GeoJsonSource>(SOURCE_LINES)?.setGeoJson(FeatureCollection.fromFeatures(lineFeatures))
        style.getSourceAs<GeoJsonSource>(SOURCE_MARKERS)?.setGeoJson(FeatureCollection.fromFeatures(markerFeatures))

        // A tapped marker that no longer exists closes its callout.
        callout?.let { open -> if (scene.markers.none { it.id == open.id }) callout = null }
    }

    /**
     * Moves the navigation arrow. Called every frame while it glides, so it
     * touches only its own one-point source, never the rest of the scene.
     */
    fun setPuck(position: LatLng?, bearing: Float, color: Int) {
        val style = map?.style ?: return
        if (!isStyleLoaded) return
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_PUCK) ?: return
        if (position == null) {
            source.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()))
            return
        }
        val iconKey = ensureIcon(style, MarkerIcon.Arrow(color))
        source.setGeoJson(
            Feature.fromGeometry(Point.fromLngLat(position.longitude, position.latitude)).apply {
                addStringProperty("icon", iconKey)
                addNumberProperty("bearing", bearing)
            }
        )
    }

    /** The marker under a tap, if any. */
    internal fun markerAt(point: MlLatLng): MapMarker? {
        val m = map ?: return null
        if (!isStyleLoaded) return null
        val screen = m.projection.toScreenLocation(point)
        val slop = 12f * density
        val box = android.graphics.RectF(screen.x - slop, screen.y - slop, screen.x + slop, screen.y + slop)
        val hit = runCatching { m.queryRenderedFeatures(box, LAYER_PINS, LAYER_CENTERED) }.getOrNull()
            ?.firstOrNull() ?: return null
        val id = runCatching { hit.getStringProperty("id") }.getOrNull() ?: return null
        return lastScene.markers.firstOrNull { it.id == id }
    }

    companion object {
        private const val TAG = "HistouryMap"

        internal const val SOURCE_LINES = "histoury-lines"
        internal const val SOURCE_MARKERS = "histoury-markers"
        internal const val SOURCE_PUCK = "histoury-puck"

        internal const val LAYER_CASING = "histoury-line-casing"
        internal const val LAYER_SOLID = "histoury-line-solid"
        internal const val LAYER_DASHED = "histoury-line-dashed"
        internal const val LAYER_DOTTED = "histoury-line-dotted"
        internal const val LAYER_PINS = "histoury-marker-pins"
        internal const val LAYER_CENTERED = "histoury-marker-centered"
        internal const val LAYER_PUCK = "histoury-puck"

        /** ARGB int -> "rgba(r, g, b, a)", which the style's to-color understands. */
        internal fun cssColor(argb: Int): String {
            val a = ((argb ushr 24) and 0xFF) / 255f
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            return "rgba($r, $g, $b, ${"%.3f".format(java.util.Locale.US, a)})"
        }
    }
}

private fun LatLng.ml(): MlLatLng = MlLatLng(latitude, longitude)

/** A map state that lives as long as the screen does. */
@Composable
fun rememberHistouryMapState(): HistouryMapState = remember { HistouryMapState() }
